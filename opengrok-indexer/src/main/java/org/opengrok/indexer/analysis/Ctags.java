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
 * Copyright (c) 2005, 2018, Oracle and/or its affiliates. All rights reserved.
 * Portions Copyright (c) 2017-2018, Chris Fraire <cfraire@me.com>.
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
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.opengrok.indexer.configuration.RuntimeEnvironment;
import org.opengrok.indexer.logger.LoggerFactory;
import org.opengrok.indexer.util.IOUtils;
import org.opengrok.indexer.util.SourceSplitter;

/**
 * Provides Ctags by having a running subprocess of ctags.
 *
 * @author Chandan
 */
public class Ctags implements Resettable {

    private static final Logger LOGGER = LoggerFactory.getLogger(Ctags.class);

    private volatile boolean closing;
    private Process ctags;
    private Thread errThread;
    private OutputStreamWriter ctagsIn;
    private BufferedReader ctagsOut;
    private static final String CTAGS_FILTER_TERMINATOR = "__ctags_done_with_file__";
    private String binary;
    private String CTagsExtraOptionsFile = null;
    private int tabSize;

    private boolean junit_testing = false;

    /**
     * Gets a value indicating if a subprocess of ctags was started and it is
     * not alive.
     * @return {@code true} if the instance should be considered closed and no
     * longer usable.
     */
    public boolean isClosed() {
        return ctags != null && !ctags.isAlive();
    }

    public String getBinary() {
        return binary;
    }

    public void setBinary(String binary) {
        this.binary = binary;
    }

    public int getTabSize() {
        return tabSize;
    }

    public void setTabSize(int tabSize) {
        this.tabSize = tabSize;
    }

    public void setCTagsExtraOptionsFile(String CTagsExtraOptionsFile) {
        this.CTagsExtraOptionsFile = CTagsExtraOptionsFile;
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
     * @throws IOException not really thrown -- but logged
     */
    public void close() throws IOException {
        reset();
        IOUtils.close(ctagsIn);
        if (ctags != null) {
            closing = true;
            LOGGER.log(Level.FINE, "Destroying ctags command");
            ctags.destroyForcibly();
        }
    }

    private void initialize() throws IOException {
        RuntimeEnvironment env = RuntimeEnvironment.getInstance();
        ProcessBuilder processBuilder;
        List<String> command = new ArrayList<>();

        command.add(binary);
        command.add("--c-kinds=+l");

        command.add("--langmap=clojure:+.cljs");
        command.add("--langmap=clojure:+.cljx");

        // Workaround for bug #14924: Don't get local variables in Java
        // code since that creates many false positives.
        // CtagsTest : bug14924 "too many methods" guards for this
        // universal ctags are however safe, so enabling for them
        command.add("--java-kinds=+l");

        command.add("--sql-kinds=+l");
        command.add("--Fortran-kinds=+L");
        command.add("--C++-kinds=+l");
        command.add("--file-scope=yes");
        command.add("-u");
        command.add("--filter=yes");
        command.add("--filter-terminator=" + CTAGS_FILTER_TERMINATOR + "\n");
        command.add("--fields=-anf+iKnS");
        command.add("--excmd=pattern");
        command.add("--langmap=sh:+.kshlib"); // RFE #17849
        command.add("--langmap=sql:+.plb"); // RFE #19208
        command.add("--langmap=sql:+.pls"); // RFE #19208
        command.add("--langmap=sql:+.pld"); // RFE #19208
        command.add("--langmap=sql:+.pks"); // RFE #19208 ?
        command.add("--langmap=sql:+.pkb"); // # 1763
        command.add("--langmap=sql:+.pck"); // # 1763

        command.add("--langmap=javascript:+.ts");

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

        /* Add extra command line options for ctags. */
        if (CTagsExtraOptionsFile != null) {
            LOGGER.log(Level.INFO, "Adding extra options to ctags");
            command.add("--options=" + CTagsExtraOptionsFile);
        }

        StringBuilder sb = new StringBuilder();
        for (String s : command) {
            sb.append(s).append(" ");
        }
        String commandStr = sb.toString();
        LOGGER.log(Level.FINE, "Executing ctags command [{0}]", commandStr);

        processBuilder = new ProcessBuilder(command);

        ctags = processBuilder.start();
        ctagsIn = new OutputStreamWriter(
            ctags.getOutputStream(), StandardCharsets.UTF_8);
        ctagsOut = new BufferedReader(new InputStreamReader(ctags.getInputStream(),
            StandardCharsets.UTF_8));

        errThread = new Thread(() -> {
            try (BufferedReader error = new BufferedReader(new InputStreamReader(ctags.getErrorStream(),
                    StandardCharsets.UTF_8))) {
                String s;
                while ((s = error.readLine()) != null) {
                    if (s.length() > 0) {
                        LOGGER.log(Level.WARNING, "Error from ctags: {0}",
                            s);
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
        command.add("--langdef=rust");
        command.add("--langmap=rust:+.rs");

        // The following are not supported yet in Universal Ctags b13cb551
        command.add("--regex-rust=/^[[:space:]]*(pub[[:space:]]+)?(static|const)[[:space:]]+(mut[[:space:]]+)?([[:alnum:]_]+)/\\4/C,consts,static constants/");
        command.add("--regex-rust=/^[[:space:]]*(pub[[:space:]]+)?(unsafe[[:space:]]+)?impl([[:space:]\n]*<[^>]*>)?[[:space:]]+(([[:alnum:]_:]+)[[:space:]]*(<[^>]*>)?[[:space:]]+(for)[[:space:]]+)?([[:alnum:]_]+)/\\5 \\7 \\8/I,impls,trait implementations/");
        command.add("--regex-rust=/^[[:space:]]*(pub[[:space:]]+)?(unsafe[[:space:]]+)?trait[[:space:]]+([[:alnum:]_]+)/\\3/r,traits,traits/");
        command.add("--regex-rust=/^[[:space:]]*let[[:space:]]+(mut)?[[:space:]]+([[:alnum:]_]+)/\\2/V,variables/");
    }

    private void addPowerShellSupport(List<String> command) {
        command.add("--langdef=Posh");
        command.add("--langmap=Posh:+.ps1,Posh:+.psm1");
        command.add("--regex-Posh=/\\$(\\{[^}]+\\})/\\1/v,variable/");
        command.add("--regex-Posh=/\\$([[:alnum:]_]+([:.][[:alnum:]_]+)*)/\\1/v,variable/");
        command.add("--regex-Posh=/^[[:space:]]*(:[^[:space:]]+)/\\1/l,label/");

        command.add("--_fielddef-Posh=signature,signatures");
        command.add("--fields-Posh=+{signature}");
        // escaped variable markers
        command.add("--regex-Posh=/`\\$([[:alnum:]_]+([:.][[:alnum:]_]+)*)/\\1//{exclusive}");
        command.add("--regex-Posh=/`\\$(\\{[^}]+\\})/\\1//{exclusive}");
        command.add("--regex-Posh=/#.*\\$([[:alnum:]_]+([:.][[:alnum:]_]+)*)/\\1//{exclusive}");
        command.add("--regex-Posh=/#.*\\$(\\{[^}]+\\})/\\1//{exclusive}");
        command.add("--regex-Posh=/^[[:space:]]*(function|filter)[[:space:]]+([^({[:space:]]+)[[:space:]]*(\\(([^)]+)\\))?/\\2/f,function,functions/{icase}{exclusive}{_field=signature:(\\4)}");
    }

    private void addPascalSupport(List<String> command) {
        command.add("--langdef=pascal");
        command.add("--langmap=pascal:+.pas");
        command.add("--regex-pascal=/([[:alnum:]_]+)[[:space:]]*=[[:space:]]*\\([[:space:]]*[[:alnum:]_][[:space:]]*\\)/\\1/t,Type/");
        command.add("--regex-pascal=/([[:alnum:]_]+)[[:space:]]*=[[:space:]]*class[[:space:]]*[^;]*$/\\1/c,Class/");
        command.add("--regex-pascal=/([[:alnum:]_]+)[[:space:]]*=[[:space:]]*interface[[:space:]]*[^;]*$/\\1/i,interface/");
        command.add("--regex-pascal=/^constructor[[:space:]]+(T[a-zA-Z0-9_]+(<[a-zA-Z0-9_, ]+>)?\\.)([a-zA-Z0-9_<>, ]+)(.*)+/\\1\\3/n,Constructor/");
        command.add("--regex-pascal=/^destructor[[:space:]]+(T[a-zA-Z0-9_]+(<[a-zA-Z0-9_, ]+>)?\\.)([a-zA-Z0-9_<>, ]+)(.*)+/\\1\\3/d,Destructor/");
        command.add("--regex-pascal=/^(procedure)[[:space:]]+T[a-zA-Z0-9_<>, ]+\\.([a-zA-Z0-9_<>, ]+)(.*)/\\2/p,procedure/");
        command.add("--regex-pascal=/^(function)[[:space:]]+T[a-zA-Z0-9_<>, ]+\\.([a-zA-Z0-9_<>, ]+)(.*)/\\2/f,function/");
        command.add("--regex-pascal=/^[[:space:]]*property[[:space:]]+([a-zA-Z0-9_<>, ]+)[[:space:]]*\\:(.*)/\\1/o,property/");
        command.add("--regex-pascal=/^(uses|interface|implementation)$/\\1/s,Section/");
        command.add("--regex-pascal=/^unit[[:space:]]+([a-zA-Z0-9_<>, ]+)[;(]/\\1/u,unit/");
    }

    private void addSwiftSupport(List<String> command) {
        command.add("--langdef=swift");
        command.add("--langmap=swift:+.swift");
        command.add("--regex-swift=/enum[[:space:]]+([^\\{\\}]+).*$/\\1/n,enum,enums/");
        command.add("--regex-swift=/typealias[[:space:]]+([^:=]+).*$/\\1/t,typealias,typealiases/");
        command.add("--regex-swift=/protocol[[:space:]]+([^:\\{]+).*$/\\1/p,protocol,protocols/");
        command.add("--regex-swift=/struct[[:space:]]+([^:\\{]+).*$/\\1/s,struct,structs/");
        command.add("--regex-swift=/class[[:space:]]+([^:\\{]+).*$/\\1/c,class,classes/");
        command.add("--regex-swift=/func[[:space:]]+([^\\(\\)]+)\\([^\\(\\)]*\\)/\\1/f,function,functions/");
        command.add("--regex-swift=/(var|let)[[:space:]]+([^:=]+).*$/\\2/v,variable,variables/");
        command.add("--regex-swift=/^[[:space:]]*extension[[:space:]]+([^:\\{]+).*$/\\1/e,extension,extensions/");
    }

    private void addKotlinSupport(List<String> command) {
        command.add("--langdef=kotlin");
        command.add("--langmap=kotlin:+.kt");
        command.add("--langmap=kotlin:+.kts");
        command.add("--regex-kotlin=/^[[:space:]]*((abstract|final|sealed|implicit|lazy)[[:space:]]*)*(private[^ ]*|protected)?[[:space:]]*class[[:space:]]+([[:alnum:]_:]+)/\\4/c,classes/");
        command.add("--regex-kotlin=/^[[:space:]]*((abstract|final|sealed|implicit|lazy)[[:space:]]*)*(private[^ ]*|protected)?[[:space:]]*object[[:space:]]+([[:alnum:]_:]+)/\\4/o,objects/");
        command.add("--regex-kotlin=/^[[:space:]]*((abstract|final|sealed|implicit|lazy)[[:space:]]*)*(private[^ ]*|protected)?[[:space:]]*((abstract|final|sealed|implicit|lazy)[[:space:]]*)*data class[[:space:]]+([[:alnum:]_:]+)/\\6/d,data classes/");
        command.add("--regex-kotlin=/^[[:space:]]*((abstract|final|sealed|implicit|lazy)[[:space:]]*)*(private[^ ]*|protected)?[[:space:]]*interface[[:space:]]+([[:alnum:]_:]+)/\\4/i,interfaces/");
        command.add("--regex-kotlin=/^[[:space:]]*type[[:space:]]+([[:alnum:]_:]+)/\\1/T,types/");
        command.add("--regex-kotlin=/^[[:space:]]*((abstract|final|sealed|implicit|lazy|private[^ ]*(\\[[a-z]*\\])*|protected)[[:space:]]*)*fun[[:space:]]+([[:alnum:]_:]+)/\\4/m,methods/");
        command.add("--regex-kotlin=/^[[:space:]]*((abstract|final|sealed|implicit|lazy|private[^ ]*|protected)[[:space:]]*)*val[[:space:]]+([[:alnum:]_:]+)/\\3/C,constants/");
        command.add("--regex-kotlin=/^[[:space:]]*((abstract|final|sealed|implicit|lazy|private[^ ]*|protected)[[:space:]]*)*var[[:space:]]+([[:alnum:]_:]+)/\\3/v,variables/");
        command.add("--regex-kotlin=/^[[:space:]]*package[[:space:]]+([[:alnum:]_.:]+)/\\1/p,packages/");
        command.add("--regex-kotlin=/^[[:space:]]*import[[:space:]]+([[:alnum:]_.:]+)/\\1/I,imports/");
    }

    private void addClojureSupport(List<String> command) {
        command.add("--langdef=clojure"); // clojure support (patterns are from https://gist.github.com/kul/8704283)
        command.add("--langmap=clojure:+.clj");

        command.add("--regex-clojure=/\\([[:space:]]*create-ns[[:space:]]+([-[:alnum:]*+!_:\\/.?]+)/\\1/n,namespace/");
        command.add("--regex-clojure=/\\([[:space:]]*def[[:space:]]+([-[:alnum:]*+!_:\\/.?]+)/\\1/d,definition/");
        command.add("--regex-clojure=/\\([[:space:]]*defn-[[:space:]]+([-[:alnum:]*+!_:\\/.?]+)/\\1/p,private function/");
        command.add("--regex-clojure=/\\([[:space:]]*defmacro[[:space:]]+([-[:alnum:]*+!_:\\/.?]+)/\\1/m,macro/");
        command.add("--regex-clojure=/\\([[:space:]]*definline[[:space:]]+([-[:alnum:]*+!_:\\/.?]+)/\\1/i,inline/");
        command.add("--regex-clojure=/\\([[:space:]]*defmulti[[:space:]]+([-[:alnum:]*+!_:\\/.?]+)/\\1/a,multimethod definition/");
        command.add("--regex-clojure=/\\([[:space:]]*defmethod[[:space:]]+([-[:alnum:]*+!_:\\/.?]+)/\\1/b,multimethod instance/");
        command.add("--regex-clojure=/\\([[:space:]]*defonce[[:space:]]+([-[:alnum:]*+!_:\\/.?]+)/\\1/c,definition (once)/");
        command.add("--regex-clojure=/\\([[:space:]]*defstruct[[:space:]]+([-[:alnum:]*+!_:\\/.?]+)/\\1/s,struct/");
        command.add("--regex-clojure=/\\([[:space:]]*intern[[:space:]]+([-[:alnum:]*+!_:\\/.?]+)/\\1/v,intern/");
    }

    private void addHaskellSupport(List<String> command) {
        command.add("--langdef=haskell"); // below was added with #912
        command.add("--langmap=haskell:+.hs");
        command.add("--langmap=haskell:+.hsc");
        command.add("--regex-haskell=/^[[:space:]]*class[[:space:]]+([a-zA-Z0-9_]+)/\\1/c,classes/");
        command.add("--regex-haskell=/^[[:space:]]*data[[:space:]]+([a-zA-Z0-9_]+)/\\1/t,types/");
        command.add("--regex-haskell=/^[[:space:]]*newtype[[:space:]]+([a-zA-Z0-9_]+)/\\1/t,types/");
        command.add("--regex-haskell=/^[[:space:]]*type[[:space:]]+([a-zA-Z0-9_]+)/\\1/t,types/");
        command.add("--regex-haskell=/^([a-zA-Z0-9_]+).*[[:space:]]+={1}[[:space:]]+/\\1/f,functions/");
        command.add("--regex-haskell=/[[:space:]]+([a-zA-Z0-9_]+).*[[:space:]]+={1}[[:space:]]+/\\1/f,functions/");
        command.add("--regex-haskell=/^(let|where)[[:space:]]+([a-zA-Z0-9_]+).*[[:space:]]+={1}[[:space:]]+/\\2/f,functions/");
        command.add("--regex-haskell=/[[:space:]]+(let|where)[[:space:]]+([a-zA-Z0-9_]+).*[[:space:]]+={1}[[:space:]]+/\\2/f,functions/");
    }

    private void addScalaSupport(List<String> command) {
        command.add("--langdef=scala"); // below is bug 61 to get full scala support
        command.add("--langmap=scala:+.scala");
        command.add("--regex-scala=/^[[:space:]]*((abstract|final|sealed|implicit|lazy)[[:space:]]*)*(private|protected)?[[:space:]]*class[[:space:]]+([a-zA-Z0-9_]+)/\\4/c,classes/");
        command.add("--regex-scala=/^[[:space:]]*((abstract|final|sealed|implicit|lazy)[[:space:]]*)*(private|protected)?[[:space:]]*object[[:space:]]+([a-zA-Z0-9_]+)/\\4/o,objects/");
        command.add("--regex-scala=/^[[:space:]]*((abstract|final|sealed|implicit|lazy)[[:space:]]*)*(private|protected)?[[:space:]]*case class[[:space:]]+([a-zA-Z0-9_]+)/\\4/C,case classes/");
        command.add("--regex-scala=/^[[:space:]]*((abstract|final|sealed|implicit|lazy)[[:space:]]*)*(private|protected)?[[:space:]]*case object[[:space:]]+([a-zA-Z0-9_]+)/\\4/O,case objects/");
        command.add("--regex-scala=/^[[:space:]]*((abstract|final|sealed|implicit|lazy)[[:space:]]*)*(private|protected)?[[:space:]]*trait[[:space:]]+([a-zA-Z0-9_]+)/\\4/t,traits/");
        command.add("--regex-scala=/^[[:space:]]*type[[:space:]]+([a-zA-Z0-9_]+)/\\1/T,types/");
        command.add("--regex-scala=/^[[:space:]]*((abstract|final|sealed|implicit|lazy)[[:space:]]*)*def[[:space:]]+([a-zA-Z0-9_]+)/\\3/m,methods/");
        command.add("--regex-scala=/^[[:space:]]*((abstract|final|sealed|implicit|lazy)[[:space:]]*)*val[[:space:]]+([a-zA-Z0-9_]+)/\\3/l,constants/");
        command.add("--regex-scala=/^[[:space:]]*((abstract|final|sealed|implicit|lazy)[[:space:]]*)*var[[:space:]]+([a-zA-Z0-9_]+)/\\3/v,variables/");
        command.add("--regex-scala=/^[[:space:]]*package[[:space:]]+([a-zA-Z0-9_.]+)/\\1/p,packages/");
    }

    public Definitions doCtags(String file) throws IOException,
            InterruptedException {
        if (file.length() < 1 || "\n".equals(file)) {
            return null;
        }

        if (ctags != null) {
            try {
                int exitValue = ctags.exitValue();
                // If it is possible to retrieve exit value without exception
                // this means the ctags process is dead.
                LOGGER.log(Level.WARNING, "Ctags process exited with exit value {0}",
                        exitValue);
                // Throw the following to indicate non-I/O error for retry.
                throw new InterruptedException("ctags died");
            } catch (IllegalThreadStateException exp) {
                // The ctags process is still running.
            }
        } else {
            initialize();
        }

        CtagsReader rdr = new CtagsReader();
        rdr.setSplitterSupplier(() -> { return trySplitSource(file); });
        rdr.setTabSize(tabSize);
        Definitions ret;
        try {
            ctagsIn.write(file + "\n");
            if (Thread.interrupted()) {
                throw new InterruptedException("write()");
            }
            ctagsIn.flush();
            if (Thread.interrupted()) {
                throw new InterruptedException("flush()");
            }
            readTags(rdr);
            ret = rdr.getDefinitions();
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
     * produce definitions for the text in the buffer String ctags process is
     * mocked, not real mostly used for junit testing
     *
     * @param bufferTags tags file output
     * @return definitions parsed from buffer
     */
    public Definitions testCtagsParser(String bufferTags) {
        junit_testing = true;
        ctagsOut = new BufferedReader(new StringReader(bufferTags));
        ctags = new Process() {
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
            public int waitFor() throws InterruptedException {
                return 0;
            }

            @Override
            public int exitValue() {
                return 0;
            }

            @Override
            public void destroy() {
            }
        };

        CtagsReader rdr = new CtagsReader();
        rdr.setTabSize(tabSize);
        try {
            readTags(rdr);
        } catch (InterruptedException ex) {
            LOGGER.log(Level.SEVERE, "readTags() test", ex);
        }
        Definitions ret = rdr.getDefinitions();
        return ret;
    }

    private void readTags(CtagsReader reader) throws InterruptedException {
        try {
            do {
                String tagLine = ctagsOut.readLine();
                if (Thread.interrupted()) {
                    throw new InterruptedException("readLine()");
                }

                //log.fine("Tagline:-->" + tagLine+"<----ONELINE");
                if (tagLine == null) {
                    if (!junit_testing) {
                        LOGGER.warning("Unexpected end of file!");
                    }
                    try {
                        int val = ctags.exitValue();
                        if (!junit_testing) {
                            LOGGER.log(Level.WARNING, "ctags exited with code: {0}", val);
                        }
                    } catch (Exception e) {
                        LOGGER.log(Level.WARNING, "Ctags problem: ", e);
                    }
                    LOGGER.fine("Ctag read");
                    return;
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
        } catch (NullPointerException|IOException ex) {
            LOGGER.log(Level.WARNING, "Failed to re-read {0}", filename);
            return null;
        }
        LOGGER.log(Level.FINEST, "Re-read {0}", filename);
        return splitter;
    }
}
