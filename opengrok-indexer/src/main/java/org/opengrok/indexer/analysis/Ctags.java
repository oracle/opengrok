/*
 * CDDL HEADER START
 *
 * The contents of this file are subject to the terms of the
 * Common Development and Distribution License (the "License").
 * You may not use this file except in compliance with the License.
 *
 * See LICENSE.txt included in this distribution for the specific
 * language governing permissions and limitations under the License.
 *
 * When distributing Covered Code, include this CDDL HEADER in each
 * file and include the License file at LICENSE.txt.
 * If applicable, add the following below this CDDL HEADER, with the
 * fields enclosed by brackets "[]" replaced with your own identifying
 * information: Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 */

/*
 * Copyright (c) 2005, 2023, Oracle and/or its affiliates. All rights reserved.
 * Portions Copyright (c) 2017, 2020, Chris Fraire <cfraire@me.com>.
 */
package org.opengrok.indexer.analysis;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.commons.lang3.SystemUtils;
import org.jetbrains.annotations.Nullable;
import org.opengrok.indexer.configuration.OpenGrokThreadFactory;
import org.opengrok.indexer.configuration.RuntimeEnvironment;
import org.opengrok.indexer.index.IndexerParallelizer;
import org.opengrok.indexer.logger.LoggerFactory;
import org.opengrok.indexer.util.CtagsUtil;
import org.opengrok.indexer.util.Executor;
import org.opengrok.indexer.util.IOUtils;
import org.opengrok.indexer.util.SourceSplitter;

/**
 * Provides Ctags by having a running subprocess of ctags.
 *
 * @author Chandan
 */
public class Ctags implements Resettable {

    private static final Logger LOGGER = LoggerFactory.getLogger(Ctags.class);

    private final RuntimeEnvironment env;
    private volatile boolean closing;
    private LangMap langMap;
    private List<String> command;
    private Process ctagsProcess;
    private OutputStreamWriter ctagsIn;
    private BufferedReader ctagsOut;
    private static final String CTAGS_FILTER_TERMINATOR = "__ctags_done_with_file__";
    private String cTagsExtraOptionsFile = null;
    private int tabSize;
    private Duration timeout = Duration.ofSeconds(10);

    private final Set<String> ctagsLanguages = new HashSet<>();

    private boolean junitTesting = false;

    /**
     * Initializes an instance with the current
     * {@link AnalyzerGuru#getLangMap()}.
     */
    public Ctags() {
        env = RuntimeEnvironment.getInstance();
        langMap = AnalyzerGuru.getLangMap();
        cTagsExtraOptionsFile = env.getCTagsExtraOptionsFile();
    }

    /**
     * Gets a value indicating if a subprocess of ctags was started, and it is not alive.
     * @return {@code true} if the instance should be considered closed and no longer usable.
     */
    public boolean isClosed() {
        return ctagsProcess != null && !ctagsProcess.isAlive();
    }

    public void setLangMap(LangMap langMap) {
        this.langMap = langMap;
    }

    public int getTabSize() {
        return tabSize;
    }

    public void setTabSize(int tabSize) {
        this.tabSize = tabSize;
    }

    public void setCTagsExtraOptionsFile(String ctagsExtraOptionsFile) {
        this.cTagsExtraOptionsFile = ctagsExtraOptionsFile;
    }

    public void setTimeout(long timeout) {
        this.timeout = Duration.ofSeconds(timeout);
    }

    public long getTimeout() {
        return this.timeout.getSeconds();
    }

    /**
     * Resets the instance for use for another file but without closing any
     * running ctags instance.
     */
    @Override
    public void reset() {
        setTabSize(0);
    }

    /**
     * {@link #reset()}, and close any running ctags instance.
     */
    public void close() {
        reset();
        IOUtils.close(ctagsIn);
        if (ctagsProcess != null) {
            closing = true;
            LOGGER.log(Level.FINE, "Destroying ctags command");
            ctagsProcess.destroyForcibly();
        }
    }

    /**
     * Gets the command-line arguments used to run ctags.
     * @return a defined (immutable) list
     */
    public List<String> getArgv() {
        initialize();
        return Collections.unmodifiableList(command);
    }

    private void initialize() {
        command = new ArrayList<>();
        String ctagsCommand = env.getCtags();
        command.add(ctagsCommand);

        // Normally, the indexer or the webapp will call validateUniversalCtags()
        // that would set the set of ctags languages returned by env.getCtagsLanguages(),
        // however for tests this might not be always the case so do it here.
        if (env.getCtagsLanguages().isEmpty()) {
            ctagsLanguages.addAll(CtagsUtil.getLanguages(ctagsCommand));
        } else {
            ctagsLanguages.addAll(env.getCtagsLanguages());
        }

        command.add("--kinds-c=+l");

        // Workaround for bug #14924: Don't get local variables in Java
        // code since that creates many false positives.
        // CtagsTest : bug14924 "too many methods" guards for this
        // universal ctags are however safe, so enabling for them
        command.add("--kinds-java=+l");

        command.add("--kinds-sql=+l");
        command.add("--kinds-Fortran=+L");
        command.add("--kinds-C++=+l");
        command.add("--extras=+F"); // Replacement for `--file-scope=yes` since 2017
        command.add("-u"); // Equivalent to `--sort=no` (i.e. "unsorted")
        command.add("--filter=yes");
        command.add("--filter-terminator=" + CTAGS_FILTER_TERMINATOR + "\n");
        command.add("--fields=-af+iKnS");
        command.add("--excmd=pattern");
        command.add("--pattern-length-limit=180"); // Increase from default 96

        //Ideally all below should be in ctags, or in outside config file,
        //we might run out of command line SOON
        //Also note, that below ctags definitions HAVE to be in POSIX
        //otherwise the regexp will not work on some platforms
        //on Solaris regexp.h used is different than on linux (gnu regexp)
        //http://en.wikipedia.org/wiki/Regular_expression#POSIX_basic_and_extended
        addScalaSupport(command);
        addHaskellSupport(command);
        //temporarily use our defs until ctags will fix https://github.com/universal-ctags/ctags/issues/988
        addClojureSupport(command);
        addKotlinSupport(command);
        addSwiftSupport(command);
        addRustSupport(command);
        addPascalSupport(command);
        addPowerShellSupport(command);
        //PLEASE add new languages ONLY with POSIX syntax (see above wiki link)

        if (langMap == null) {
            LOGGER.warning("langMap property is null");
        } else {
            command.addAll(langMap.getCtagsArgs());
        }

        /* Add extra command line options for ctags. */
        if (cTagsExtraOptionsFile != null) {
            LOGGER.log(Level.FINER, "Adding extra options to ctags");
            command.add("--options=" + cTagsExtraOptionsFile);
        }
    }

    private void run() throws IOException {
        String commandStr = Executor.escapeForShell(command, false, SystemUtils.IS_OS_WINDOWS);
        LOGGER.log(Level.FINE, "Executing ctags command [{0}]", commandStr);

        ProcessBuilder processBuilder = new ProcessBuilder(command);

        ctagsProcess = processBuilder.start();
        ctagsIn = new OutputStreamWriter(ctagsProcess.getOutputStream(), StandardCharsets.UTF_8);
        ctagsOut = new BufferedReader(new InputStreamReader(ctagsProcess.getInputStream(),
            StandardCharsets.UTF_8));

        Thread errThread = new OpenGrokThreadFactory("ctags-err").newThread(() -> {
            try (BufferedReader error = new BufferedReader(new InputStreamReader(ctagsProcess.getErrorStream(),
                    StandardCharsets.UTF_8))) {
                String s;
                while ((s = error.readLine()) != null) {
                    if (s.length() > 0) {
                        LOGGER.log(Level.WARNING, "Error from ctags: {0}", s);
                    }
                    if (closing) {
                        break;
                    }
                }
            } catch (IOException exp) {
                LOGGER.log(Level.WARNING, "Got an exception reading ctags error stream: ", exp);
            }
        });
        errThread.setDaemon(true);
        errThread.start();
    }

    private void addRustSupport(List<String> command) {
        if (!ctagsLanguages.contains("Rust")) { // Built-in would be capitalized.
            command.add("--langdef=rust"); // Lower-case if user-defined.
        }

        // The following are not supported yet in Universal Ctags 882b6c7
        command.add("--kinddef-rust=I,impl,Trait\\ implementation");
        command.add("--kinddef-rust=r,trait,Traits");
        command.add("--kinddef-rust=V,localVariable,Local\\ variables");
        command.add("--regex-rust=/^[[:space:]]*(pub[[:space:]]+)?(static|const)[[:space:]]+(mut[[:space:]]+)?" +
                "([[:alnum:]_]+)/\\4/C/");
        command.add("--regex-rust=/^[[:space:]]*(pub[[:space:]]+)?(unsafe[[:space:]]+)?impl([[:space:]\n]*<[^>]*>)?" +
                "[[:space:]]+(([[:alnum:]_:]+)[[:space:]]*(<[^>]*>)?[[:space:]]+(for)[[:space:]]+)?" +
                "([[:alnum:]_]+)/\\5 \\7 \\8/I/");
        command.add("--regex-rust=/^[[:space:]]*(pub[[:space:]]+)?(unsafe[[:space:]]+)?trait[[:space:]]+([[:alnum:]_]+)/\\3/r/");
        command.add("--regex-rust=/^[[:space:]]*let([[:space:]]+mut)?[[:space:]]+([[:alnum:]_]+)/\\2/V/");
    }

    private void addPowerShellSupport(List<String> command) {
        if (!ctagsLanguages.contains("PowerShell")) { // Built-in would be capitalized.
            command.add("--langdef=powershell"); // Lower-case if user-defined.
        }

        command.add("--regex-powershell=/\\$(\\{[^}]+\\})/\\1/v/");
        command.add("--regex-powershell=/\\$([[:alnum:]_]+([:.][[:alnum:]_]+)*)/\\1/v/");
        command.add("--regex-powershell=/^[[:space:]]*(:[^[:space:]]+)/\\1/l,label/");

        command.add("--_fielddef-powershell=signature,signatures");
        command.add("--fields-powershell=+{signature}");
        // escaped variable markers
        command.add("--regex-powershell=/`\\$([[:alnum:]_]+([:.][[:alnum:]_]+)*)/\\1//{exclusive}");
        command.add("--regex-powershell=/`\\$(\\{[^}]+\\})/\\1//{exclusive}");
        command.add("--regex-powershell=/#.*\\$([[:alnum:]_]+([:.][[:alnum:]_]+)*)/\\1//{exclusive}");
        command.add("--regex-powershell=/#.*\\$(\\{[^}]+\\})/\\1//{exclusive}");
        command.add("--regex-powershell=/^[[:space:]]*(function|filter)[[:space:]]+([^({[:space:]]+)[[:space:]]*" +
                "(\\(([^)]+)\\))?/\\2/f/{icase}{exclusive}{_field=signature:(\\4)}");
    }

    private void addPascalSupport(List<String> command) {
        if (!ctagsLanguages.contains("Pascal")) { // Built-in would be capitalized.
            command.add("--langdef=pascal"); // Lower-case if user-defined.
        }

        command.add("--kinddef-pascal=t,type,Types");
        command.add("--kinddef-pascal=c,class,Classes");
        command.add("--kinddef-pascal=i,interface,Interfaces");
        command.add("--kinddef-pascal=n,constructor,Constructors");
        command.add("--kinddef-pascal=d,destructor,Destructors");
        command.add("--kinddef-pascal=o,property,Properties");
        command.add("--kinddef-pascal=s,section,Sections");
        command.add("--kinddef-pascal=u,unit,Units");
        command.add("--regex-pascal=/([[:alnum:]_]+)[[:space:]]*=[[:space:]]*\\([[:space:]]*[[:alnum:]_][[:space:]]*\\)/\\1/t/");
        command.add("--regex-pascal=/([[:alnum:]_]+)[[:space:]]*=[[:space:]]*class[[:space:]]*[^;]*$/\\1/c/");
        command.add("--regex-pascal=/([[:alnum:]_]+)[[:space:]]*=[[:space:]]*interface[[:space:]]*[^;]*$/\\1/i/");
        command.add("--regex-pascal=/^constructor[[:space:]]+(T[a-zA-Z0-9_]+(<[a-zA-Z0-9_, ]+>)?\\.)([a-zA-Z0-9_<>, ]+)(.*)+/\\1\\3/n/");
        command.add("--regex-pascal=/^destructor[[:space:]]+(T[a-zA-Z0-9_]+(<[a-zA-Z0-9_, ]+>)?\\.)([a-zA-Z0-9_<>, ]+)(.*)+/\\1\\3/d/");
        command.add("--regex-pascal=/^(procedure)[[:space:]]+T[a-zA-Z0-9_<>, ]+\\.([a-zA-Z0-9_<>, ]+)(.*)/\\2/p/");
        command.add("--regex-pascal=/^(function)[[:space:]]+T[a-zA-Z0-9_<>, ]+\\.([a-zA-Z0-9_<>, ]+)(.*)/\\2/f/");
        command.add("--regex-pascal=/^[[:space:]]*property[[:space:]]+([a-zA-Z0-9_<>, ]+)[[:space:]]*\\:(.*)/\\1/o/");
        command.add("--regex-pascal=/^(uses|interface|implementation)$/\\1/s/");
        command.add("--regex-pascal=/^unit[[:space:]]+([a-zA-Z0-9_<>, ]+)[;(]/\\1/u/");
    }

    private void addSwiftSupport(List<String> command) {
        if (!ctagsLanguages.contains("Swift")) { // Built-in would be capitalized.
            command.add("--langdef=swift"); // Lower-case if user-defined.
        }
        command.add("--kinddef-swift=n,enum,Enums");
        command.add("--kinddef-swift=t,typealias,Type\\ aliases");
        command.add("--kinddef-swift=p,protocol,Protocols");
        command.add("--kinddef-swift=s,struct,Structs");
        command.add("--kinddef-swift=c,class,Classes");
        command.add("--kinddef-swift=f,function,Functions");
        command.add("--kinddef-swift=v,variable,Variables");
        command.add("--kinddef-swift=e,extension,Extensions");

        command.add("--regex-swift=/enum[[:space:]]+([^\\{\\}]+).*$/\\1/n/");
        command.add("--regex-swift=/typealias[[:space:]]+([^:=]+).*$/\\1/t/");
        command.add("--regex-swift=/protocol[[:space:]]+([^:\\{]+).*$/\\1/p/");
        command.add("--regex-swift=/struct[[:space:]]+([^:\\{]+).*$/\\1/s/");
        command.add("--regex-swift=/class[[:space:]]+([^:\\{]+).*$/\\1/c/");
        command.add("--regex-swift=/func[[:space:]]+([^\\(\\)]+)\\([^\\(\\)]*\\)/\\1/f/");
        command.add("--regex-swift=/(var|let)[[:space:]]+([^:=]+).*$/\\2/v/");
        command.add("--regex-swift=/^[[:space:]]*extension[[:space:]]+([^:\\{]+).*$/\\1/e/");
    }

    private void addKotlinSupport(List<String> command) {
        if (!ctagsLanguages.contains("Kotlin")) { // Built-in would be capitalized.
            command.add("--langdef=kotlin"); // Lower-case if user-defined.
        }
        command.add("--kinddef-kotlin=d,dataClass,Data\\ classes");
        command.add("--kinddef-kotlin=I,import,Imports");

        command.add("--regex-kotlin=/^[[:space:]]*((abstract|final|sealed|implicit|lazy)[[:space:]]*)*" +
                "(private[^ ]*|protected)?[[:space:]]*class[[:space:]]+([[:alnum:]_:]+)/\\4/c/");
        command.add("--regex-kotlin=/^[[:space:]]*((abstract|final|sealed|implicit|lazy)[[:space:]]*)*" +
                "(private[^ ]*|protected)?[[:space:]]*object[[:space:]]+([[:alnum:]_:]+)/\\4/o/");
        command.add("--regex-kotlin=/^[[:space:]]*((abstract|final|sealed|implicit|lazy)[[:space:]]*)*" +
                "(private[^ ]*|protected)?[[:space:]]*((abstract|final|sealed|implicit|lazy)[[:space:]]*)*" +
                "data class[[:space:]]+([[:alnum:]_:]+)/\\6/d/");
        command.add("--regex-kotlin=/^[[:space:]]*((abstract|final|sealed|implicit|lazy)[[:space:]]*)*" +
                "(private[^ ]*|protected)?[[:space:]]*interface[[:space:]]+([[:alnum:]_:]+)/\\4/i/");
        command.add("--regex-kotlin=/^[[:space:]]*type[[:space:]]+([[:alnum:]_:]+)/\\1/T/");
        command.add("--regex-kotlin=/^[[:space:]]*((abstract|final|sealed|implicit|lazy|private[^ ]*" +
                "(\\[[a-z]*\\])*|protected)[[:space:]]*)*fun[[:space:]]+([[:alnum:]_:]+)/\\4/m/");
        command.add("--regex-kotlin=/^[[:space:]]*((abstract|final|sealed|implicit|lazy|private[^ ]*" +
                "|protected)[[:space:]]*)*val[[:space:]]+([[:alnum:]_:]+)/\\3/C/");
        command.add("--regex-kotlin=/^[[:space:]]*((abstract|final|sealed|implicit|lazy|private[^ ]*" +
                "|protected)[[:space:]]*)*var[[:space:]]+([[:alnum:]_:]+)/\\3/v/");
        command.add("--regex-kotlin=/^[[:space:]]*package[[:space:]]+([[:alnum:]_.:]+)/\\1/p/");
        command.add("--regex-kotlin=/^[[:space:]]*import[[:space:]]+([[:alnum:]_.:]+)/\\1/I/");
    }

    /**
     * Override Clojure support with patterns from https://gist.github.com/kul/8704283.
     */
    private void addClojureSupport(List<String> command) {
        if (!ctagsLanguages.contains("Clojure")) { // Built-in would be capitalized.
            command.add("--langdef=clojure"); // Lower-case if user-defined.
        }
        command.add("--kinddef-clojure=d,definition,Definitions");
        command.add("--kinddef-clojure=p,privateFunction,Private\\ functions");
        command.add("--kinddef-clojure=m,macro,Macros");
        command.add("--kinddef-clojure=i,inline,Inlines");
        command.add("--kinddef-clojure=a,multimethodDefinition,Multimethod\\ definitions");
        command.add("--kinddef-clojure=b,multimethodInstance,Multimethod\\ instances");
        command.add("--kinddef-clojure=c,definitionOnce,Definition\\ once");
        command.add("--kinddef-clojure=s,struct,Structs");
        command.add("--kinddef-clojure=v,intern,Interns");

        command.add("--regex-clojure=/\\([[:space:]]*create-ns[[:space:]]+([-[:alnum:]*+!_:\\/.?]+)/\\1/n/");
        command.add("--regex-clojure=/\\([[:space:]]*def[[:space:]]+([-[:alnum:]*+!_:\\/.?]+)/\\1/d/");
        command.add("--regex-clojure=/\\([[:space:]]*defn-[[:space:]]+([-[:alnum:]*+!_:\\/.?]+)/\\1/p/");
        command.add("--regex-clojure=/\\([[:space:]]*defmacro[[:space:]]+([-[:alnum:]*+!_:\\/.?]+)/\\1/m/");
        command.add("--regex-clojure=/\\([[:space:]]*definline[[:space:]]+([-[:alnum:]*+!_:\\/.?]+)/\\1/i/");
        command.add("--regex-clojure=/\\([[:space:]]*defmulti[[:space:]]+([-[:alnum:]*+!_:\\/.?]+)/\\1/a/");
        command.add("--regex-clojure=/\\([[:space:]]*defmethod[[:space:]]+([-[:alnum:]*+!_:\\/.?]+)/\\1/b/");
        command.add("--regex-clojure=/\\([[:space:]]*defonce[[:space:]]+([-[:alnum:]*+!_:\\/.?]+)/\\1/c/");
        command.add("--regex-clojure=/\\([[:space:]]*defstruct[[:space:]]+([-[:alnum:]*+!_:\\/.?]+)/\\1/s/");
        command.add("--regex-clojure=/\\([[:space:]]*intern[[:space:]]+([-[:alnum:]*+!_:\\/.?]+)/\\1/v/");
    }

    private void addHaskellSupport(List<String> command) {
        if (!ctagsLanguages.contains("Haskell")) { // Built-in would be capitalized.
            command.add("--langdef=haskell"); // below added with #912. Lowercase if user-defined.
        }

        command.add("--regex-haskell=/^[[:space:]]*class[[:space:]]+([a-zA-Z0-9_]+)/\\1/c/");
        command.add("--regex-haskell=/^[[:space:]]*data[[:space:]]+([a-zA-Z0-9_]+)/\\1/t/");
        command.add("--regex-haskell=/^[[:space:]]*newtype[[:space:]]+([a-zA-Z0-9_]+)/\\1/t/");
        command.add("--regex-haskell=/^[[:space:]]*type[[:space:]]+([a-zA-Z0-9_]+)/\\1/t/");
        command.add("--regex-haskell=/^([a-zA-Z0-9_]+).*[[:space:]]+={1}[[:space:]]+/\\1/f/");
        command.add("--regex-haskell=/[[:space:]]+([a-zA-Z0-9_]+).*[[:space:]]+={1}[[:space:]]+/\\1/f/");
        command.add("--regex-haskell=/^(let|where)[[:space:]]+([a-zA-Z0-9_]+).*[[:space:]]+={1}[[:space:]]+/\\2/f/");
        command.add("--regex-haskell=/[[:space:]]+(let|where)[[:space:]]+([a-zA-Z0-9_]+).*[[:space:]]+={1}[[:space:]]+/\\2/f/");
    }

    private void addScalaSupport(List<String> command) {
        if (!ctagsLanguages.contains("Scala")) { // Built-in would be capitalized.
            command.add("--langdef=scala"); // below is bug 61 to get full scala support. Lower-case
        }
        command.add("--kinddef-scala=c,class,Classes");
        command.add("--kinddef-scala=o,object,Objects");
        command.add("--kinddef-scala=C,caseClass,Case\\ classes");
        command.add("--kinddef-scala=O,caseObject,Case\\ objects");
        command.add("--kinddef-scala=t,trait,Traits");
        command.add("--kinddef-scala=m,method,Methods");
        command.add("--kinddef-scala=l,constant,Constants");
        command.add("--kinddef-scala=v,variable,Variables");
        command.add("--kinddef-scala=T,type,Types");
        command.add("--kinddef-scala=p,package,Packages");

        command.add("--regex-scala=/^[[:space:]]*((abstract|final|sealed|implicit|lazy)[[:space:]]*)*" +
                "(private|protected)?[[:space:]]*class[[:space:]]+([a-zA-Z0-9_]+)/\\4/c/");
        command.add("--regex-scala=/^[[:space:]]*((abstract|final|sealed|implicit|lazy)[[:space:]]*)*" +
                "(private|protected)?[[:space:]]*object[[:space:]]+([a-zA-Z0-9_]+)/\\4/o/");
        command.add("--regex-scala=/^[[:space:]]*((abstract|final|sealed|implicit|lazy)[[:space:]]*)*" +
                "(private|protected)?[[:space:]]*case class[[:space:]]+([a-zA-Z0-9_]+)/\\4/C/");
        command.add("--regex-scala=/^[[:space:]]*((abstract|final|sealed|implicit|lazy)[[:space:]]*)*" +
                "(private|protected)?[[:space:]]*case object[[:space:]]+([a-zA-Z0-9_]+)/\\4/O/");
        command.add("--regex-scala=/^[[:space:]]*((abstract|final|sealed|implicit|lazy)[[:space:]]*)*" +
                "(private|protected)?[[:space:]]*trait[[:space:]]+([a-zA-Z0-9_]+)/\\4/t/");
        command.add("--regex-scala=/^[[:space:]]*type[[:space:]]+([a-zA-Z0-9_]+)/\\1/T/");
        command.add("--regex-scala=/^[[:space:]]*((abstract|final|sealed|implicit|lazy|private|protected)" +
                "[[:space:]]*)*def[[:space:]]+([a-zA-Z0-9_]+)/\\3/m/");
        command.add("--regex-scala=/^[[:space:]]*((abstract|final|sealed|implicit|lazy)[[:space:]]*)*" +
                "val[[:space:]]+([a-zA-Z0-9_]+)/\\3/l/");
        command.add("--regex-scala=/^[[:space:]]*((abstract|final|sealed|implicit|lazy)[[:space:]]*)*" +
                "var[[:space:]]+([a-zA-Z0-9_]+)/\\3/v/");
        command.add("--regex-scala=/^[[:space:]]*package[[:space:]]+([a-zA-Z0-9_.]+)/\\1/p/");
    }

    /**
     * Run ctags on a file.
     * @param file file path to process
     * @return valid instance of {@link Definitions} or {@code null} on error
     * @throws IOException I/O exception
     * @throws InterruptedException interrupted command
     */
    @Nullable
    public Definitions doCtags(String file) throws IOException, InterruptedException {

        if (file.length() < 1 || "\n".equals(file)) {
            return null;
        }

        if (ctagsProcess != null) {
            try {
                int exitValue = ctagsProcess.exitValue();
                // If it is possible to retrieve exit value without exception
                // this means the ctags process is dead.
                LOGGER.log(Level.WARNING, "Ctags process exited with exit value {0}",
                        exitValue);
                // Throw the following to indicate non-I/O error for retry.
                throw new InterruptedException("ctags process died");
            } catch (IllegalThreadStateException exp) {
                // The ctags process is still running.
            }
        } else {
            initialize();
            run();
        }

        CtagsReader rdr = new CtagsReader();
        rdr.setSplitterSupplier(() -> trySplitSource(file));
        rdr.setTabSize(tabSize);
        Definitions ret = null;
        try {
            ctagsIn.write(file + "\n");
            if (Thread.interrupted()) {
                throw new InterruptedException("write()");
            }
            ctagsIn.flush();
            if (Thread.interrupted()) {
                throw new InterruptedException("flush()");
            }

            /*
             * Run the ctags reader in a time bound thread to make sure
             * the ctags process completes so that the indexer can
             * make progress instead of hanging the whole operation.
             */
            IndexerParallelizer parallelizer = env.getIndexerParallelizer();
            ExecutorService executor = parallelizer.getCtagsWatcherExecutor();
            Future<Definitions> future = executor.submit(() -> {
                readTags(rdr);
                return rdr.getDefinitions();
            });

            try {
                ret = future.get(getTimeout(), TimeUnit.SECONDS);
            } catch (ExecutionException ex) {
                LOGGER.log(Level.WARNING, "execution exception", ex);
            } catch (TimeoutException ex) {
                LOGGER.log(Level.WARNING,
                        String.format("Terminating ctags process for file '%s' " +
                                "due to timeout %d seconds", file, getTimeout()));
                close();
                // Allow for retry in IndexDatabase.
                throw new InterruptedException("ctags timeout");
            }
        } catch (IOException ex) {
            /*
             * In case the ctags process had to be destroyed, possibly pre-empt
             * the IOException with an InterruptedException.
             */
            if (Thread.interrupted()) {
                throw new InterruptedException("I/O");
            }
            throw ex;
        }

        return ret;
    }

    /**
     * Produce definitions for the text in the buffer String. ctags process is
     * mocked, not real mostly used for junit testing
     *
     * @param bufferTags tags file output
     * @return definitions parsed from buffer
     * @throws java.lang.InterruptedException interrupted
     */
    Definitions testCtagsParser(String bufferTags)
            throws InterruptedException {

        // Ensure output is magic-terminated as expected.
        StringBuilder tagsBuilder = new StringBuilder(bufferTags);
        if (!bufferTags.endsWith("\n")) {
            tagsBuilder.append("\n");
        }
        tagsBuilder.append(CTAGS_FILTER_TERMINATOR);
        tagsBuilder.append("\n");
        bufferTags = tagsBuilder.toString();

        junitTesting = true;
        ctagsOut = new BufferedReader(new StringReader(bufferTags));
        ctagsProcess = new Process() {
            @Override
            public OutputStream getOutputStream() {
                return null;
            }

            @Override
            public InputStream getInputStream() {
                return null;
            }

            @Override
            public InputStream getErrorStream() {
                return null;
            }

            @Override
            public int waitFor() {
                return 0;
            }

            @Override
            public int exitValue() {
                return 0;
            }

            @Override
            public void destroy() {
                //Empty Method
            }
        };

        CtagsReader rdr = new CtagsReader();
        rdr.setTabSize(tabSize);
        readTags(rdr);
        return rdr.getDefinitions();
    }

    private void readTags(CtagsReader reader) throws InterruptedException {
        try {
            do {
                String tagLine = ctagsOut.readLine();
                if (Thread.interrupted()) {
                    throw new InterruptedException("readLine()");
                }

                if (tagLine == null) {
                    if (!junitTesting) {
                        LOGGER.warning("ctags: Unexpected end of file!");
                    }
                    try {
                        int val = ctagsProcess.exitValue();
                        if (!junitTesting) {
                            LOGGER.log(Level.WARNING, "ctags exited with code: {0}", val);
                        }
                    } catch (IllegalThreadStateException e) {
                        LOGGER.log(Level.WARNING, "ctags EOF but did not exit");
                        ctagsProcess.destroyForcibly();
                    } catch (Exception e) {
                        LOGGER.log(Level.WARNING, "ctags problem:", e);
                        ctagsProcess.destroyForcibly();
                    }
                    // Throw the following to indicate non-I/O error for retry.
                    throw new InterruptedException("tagLine == null");
                }

                if (CTAGS_FILTER_TERMINATOR.equals(tagLine)) {
                    return;
                }

                //fix for bug #16334
                if (tagLine.endsWith(CTAGS_FILTER_TERMINATOR)) {
                    LOGGER.log(Level.WARNING, "ctags encountered a problem while generating tags for the file. The index will be incomplete.");
                    return;
                }

                reader.readLine(tagLine);
            } while (true);
        } catch (InterruptedException e) {
            throw e;
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "CTags parsing problem: ", e);
        }
        LOGGER.severe("CTag reader cycle was interrupted!");
    }

    /**
     * Attempts to create a {@link SourceSplitter} instance with content from
     * the specified file.
     * @return a defined instance or {@code null} on failure (without exception)
     */
    private static SourceSplitter trySplitSource(String filename) {
        SourceSplitter splitter = new SourceSplitter();
        try {
            StreamSource src = StreamSource.fromFile(new File(filename));
            splitter.reset(src);
        } catch (NullPointerException | IOException ex) {
            LOGGER.log(Level.WARNING, "Failed to re-read ''{0}''", filename);
            return null;
        }
        LOGGER.log(Level.FINEST, "Re-read ''{0}''", filename);
        return splitter;
    }
}
