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
 * Copyright (c) 2005, 2015, Oracle and/or its affiliates. All rights reserved.
 */

package org.opensolaris.opengrok.analysis;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.opensolaris.opengrok.configuration.RuntimeEnvironment;
import org.opensolaris.opengrok.logger.LoggerFactory;
import org.opensolaris.opengrok.util.IOUtils;
import org.opensolaris.opengrok.util.Interner;

/**
 * Provides Ctags by having a running instance of ctags
 *
 * @author Chandan
 */
public class Ctags {

    private static final Logger LOGGER = LoggerFactory.getLogger(Ctags.class);

    private Process ctags;
    private OutputStreamWriter ctagsIn;
    private BufferedReader ctagsOut;
    private static final String CTAGS_FILTER_TERMINATOR = "__ctags_done_with_file__";
    //default: setCtags(System.getProperty("org.opensolaris.opengrok.analysis.Ctags", "ctags"));
    private String binary;
    private String CTagsExtraOptionsFile = null;
    private ProcessBuilder processBuilder;
    
    private final int MIN_METHOD_LINE_LENGTH=6; //this means basically empty method body in tags, so skip it
    private final int MAX_METHOD_LINE_LENGTH=1030; //96 is used by universal ctags for some lines, but it's too low, OpenGrok can theoretically handle 50000 with 8G heap    
    // also this might break scopes functionality, if set too low

    public void setBinary(String binary) {
        this.binary = binary;
    }

    public void setCTagsExtraOptionsFile(String CTagsExtraOptionsFile) {
        this.CTagsExtraOptionsFile = CTagsExtraOptionsFile;
    }

    public void close() throws IOException {
        IOUtils.close(ctagsIn);
        if (ctags != null) {
            ctags.destroy();
        }
    }

    private void initialize() throws IOException {
        RuntimeEnvironment env = RuntimeEnvironment.getInstance();
        if (processBuilder == null) {
            List<String> command = new ArrayList<>();            

            command.add(binary);
            command.add("--c-kinds=+l");

            // Workaround for bug #14924: Don't get local variables in Java
            // code since that creates many false positives. Uncomment the next
            // line when the bug has been fixed.
            //only disable if old ctags, enable for universal ctags
            if (env.isUniversalCtags()) {
             command.add("--java-kinds=+l");
            }
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

            //Ideally all below should be in ctags, or in outside config file,
            //we might run out of command line SOON

            //Also note, that below ctags definitions HAVE to be in POSIX
            //otherwise the regexp will not work on some platforms
            //on Solaris regexp.h used is different than on linux (gnu regexp)
            //http://en.wikipedia.org/wiki/Regular_expression#POSIX_basic_and_extended

            command.add("--langdef=scala"); // below is bug 61 to get full scala support
            command.add("--langmap=scala:.scala");
            command.add("--regex-scala=/^[[:space:]]*((abstract|final|sealed|implicit|lazy)[[:space:]]*)*(private|protected)?[[:space:]]*class[[:space:]]+([a-zA-Z0-9_]+)/\\4/c,classes/");
            command.add("--regex-scala=/^[[:space:]]*((abstract|final|sealed|implicit|lazy)[[:space:]]*)*(private|protected)?[[:space:]]*object[[:space:]]+([a-zA-Z0-9_]+)/\\4/c,objects/");
            command.add("--regex-scala=/^[[:space:]]*((abstract|final|sealed|implicit|lazy)[[:space:]]*)*(private|protected)?[[:space:]]*case class[[:space:]]+([a-zA-Z0-9_]+)/\\4/c,case classes/");
            command.add("--regex-scala=/^[[:space:]]*((abstract|final|sealed|implicit|lazy)[[:space:]]*)*(private|protected)?[[:space:]]*case object[[:space:]]+([a-zA-Z0-9_]+)/\\4/c,case objects/");
            command.add("--regex-scala=/^[[:space:]]*((abstract|final|sealed|implicit|lazy)[[:space:]]*)*(private|protected)?[[:space:]]*trait[[:space:]]+([a-zA-Z0-9_]+)/\\4/t,traits/");
            command.add("--regex-scala=/^[[:space:]]*type[[:space:]]+([a-zA-Z0-9_]+)/\\1/T,types/");
            command.add("--regex-scala=/^[[:space:]]*((abstract|final|sealed|implicit|lazy)[[:space:]]*)*def[[:space:]]+([a-zA-Z0-9_]+)/\\3/m,methods/");
            command.add("--regex-scala=/^[[:space:]]*((abstract|final|sealed|implicit|lazy)[[:space:]]*)*val[[:space:]]+([a-zA-Z0-9_]+)/\\3/l,constants/");
            command.add("--regex-scala=/^[[:space:]]*((abstract|final|sealed|implicit|lazy)[[:space:]]*)*var[[:space:]]+([a-zA-Z0-9_]+)/\\3/l,variables/");
            command.add("--regex-scala=/^[[:space:]]*package[[:space:]]+([a-zA-Z0-9_.]+)/\\1/p,packages/");

            command.add("--langdef=haskell"); // below was added with #912
            command.add("--langmap=haskell:.hs.hsc");
            command.add("--regex-haskell=/^[[:space:]]*class[[:space:]]+([a-zA-Z0-9_]+)/\\1/c,classes/");
            command.add("--regex-haskell=/^[[:space:]]*data[[:space:]]+([a-zA-Z0-9_]+)/\\1/t,types/");
            command.add("--regex-haskell=/^[[:space:]]*newtype[[:space:]]+([a-zA-Z0-9_]+)/\\1/t,types/");
            command.add("--regex-haskell=/^[[:space:]]*type[[:space:]]+([a-zA-Z0-9_]+)/\\1/t,types/");
            command.add("--regex-haskell=/^([a-zA-Z0-9_]+).*[[:space:]]+={1}[[:space:]]+/\\1/f,functions/");
            command.add("--regex-haskell=/[[:space:]]+([a-zA-Z0-9_]+).*[[:space:]]+={1}[[:space:]]+/\\1/f,functions/");
            command.add("--regex-haskell=/^(let|where)[[:space:]]+([a-zA-Z0-9_]+).*[[:space:]]+={1}[[:space:]]+/\\2/f,functions/");
            command.add("--regex-haskell=/[[:space:]]+(let|where)[[:space:]]+([a-zA-Z0-9_]+).*[[:space:]]+={1}[[:space:]]+/\\2/f,functions/");

            command.add("--langmap=clojure:+.cljs");
            command.add("--langmap=clojure:+.cljx");
            
            if (!env.isUniversalCtags()) {
                command.add("--langdef=golang");
                command.add("--langmap=golang:.go");
                command.add("--regex-golang=/func([[:space:]]+([^)]+))?[[:space:]]+([a-zA-Z0-9_]+)/\\2/f,func/");
                command.add("--regex-golang=/var[[:space:]]+([a-zA-Z_][a-zA-Z0-9_]+)/\\1/v,var/");
                command.add("--regex-golang=/type[[:space:]]+([a-zA-Z_][a-zA-Z0-9_]+)/\\1/t,type/");

                command.add("--langdef=clojure"); // clojure support (patterns are from https://gist.github.com/xzj/1518834)
                command.add("--langmap=clojure:.clj");                
                command.add("--regex-clojure=/\\([[:space:]]*create-ns[[:space:]]+([-[[:alnum:]]*+!_:\\/.?]+)/\\1/n,namespace/");
                command.add("--regex-clojure=/\\([[:space:]]*def[[:space:]]+([-[[:alnum:]]*+!_:\\/.?]+)/\\1/d,definition/");
                command.add("--regex-clojure=/\\([[:space:]]*defn[[:space:]]+([-[[:alnum:]]*+!_:\\/.?]+)/\\1/f,function/");
                command.add("--regex-clojure=/\\([[:space:]]*defn-[[:space:]]+([-[[:alnum:]]*+!_:\\/.?]+)/\\1/p,private function/");
                command.add("--regex-clojure=/\\([[:space:]]*defmacro[[:space:]]+([-[[:alnum:]]*+!_:\\/.?]+)/\\1/m,macro/");
                command.add("--regex-clojure=/\\([[:space:]]*definline[[:space:]]+([-[[:alnum:]]*+!_:\\/.?]+)/\\1/i,inline/");
                command.add("--regex-clojure=/\\([[:space:]]*defmulti[[:space:]]+([-[[:alnum:]]*+!_:\\/.?]+)/\\1/a,multimethod definition/");
                command.add("--regex-clojure=/\\([[:space:]]*defmethod[[:space:]]+([-[[:alnum:]]*+!_:\\/.?]+)/\\1/b,multimethod instance/");
                command.add("--regex-clojure=/\\([[:space:]]*defonce[[:space:]]+([-[[:alnum:]]*+!_:\\/.?]+)/\\1/c,definition (once)/");
                command.add("--regex-clojure=/\\([[:space:]]*defstruct[[:space:]]+([-[[:alnum:]]*+!_:\\/.?]+)/\\1/s,struct/");
                command.add("--regex-clojure=/\\([[:space:]]*intern[[:space:]]+([-[[:alnum:]]*+!_:\\/.?]+)/\\1/v,intern/");
                command.add("--regex-clojure=/\\([[:space:]]*ns[[:space:]]+([-[[:alnum:]]*+!_:\\/.?]+)/\\1/n,namespace/");
            }

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
        }

        ctags = processBuilder.start();
        ctagsIn = new OutputStreamWriter(ctags.getOutputStream());
        ctagsOut = new BufferedReader(new InputStreamReader(ctags.getInputStream()));

        Thread errThread = new Thread(new Runnable() {

            @Override
            public void run() {
                StringBuilder sb = new StringBuilder();
                try (BufferedReader error = new BufferedReader(
                        new InputStreamReader(ctags.getErrorStream()))) {
                    String s;
                    while ((s = error.readLine()) != null) {
                        sb.append(s);
                        sb.append('\n');
                    }
                } catch (IOException exp) {
                     LOGGER.log(Level.WARNING, "Got an exception reading ctags error stream: ", exp);
                }
                if (sb.length() > 0) {
                    LOGGER.log(Level.WARNING, "Error from ctags: {0}", sb.toString());
                }
            }
        });
        errThread.setDaemon(true);
        errThread.start();
    }

    public Definitions doCtags(String file) throws IOException {
        boolean ctagsRunning = false;
        if (ctags != null) {
            try {
                ctags.exitValue();
                ctagsRunning = false;
                // ctags is dead! we must restart!!!
            } catch (IllegalThreadStateException exp) {
                ctagsRunning = true;
                // ctags is still running :)
            }
        }

        if (!ctagsRunning) {
            initialize();
        }

        Definitions ret = null;
        if (file.length() > 0 && !"\n".equals(file)) {
            //log.fine("doing >" + file + "<");
            ctagsIn.write(file);
            ctagsIn.flush();
            ret = new Definitions();
            readTags(ret);
        }

        return ret;
    }

    private void readTags(Definitions defs) {
        try {
            do {
                String tagLine = ctagsOut.readLine();
                //log.fine("Tagline:-->" + tagLine+"<----ONELINE");
                if (tagLine == null) {
                    LOGGER.warning("Unexpected end of file!");
                    try {
                        int val = ctags.exitValue();
                        LOGGER.log(Level.WARNING, "ctags exited with code: {0}", val);
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

                int p = tagLine.indexOf('\t');
                if (p <= 0) {
                    //log.fine("SKIPPING LINE - NO TAB");
                    continue;
                }
                String def = tagLine.substring(0, p);
                int mstart = tagLine.indexOf('\t', p + 1);
                String lnum = "-1";
                String signature = null;
                String kind = null;
                String inher = null;

                int lp = tagLine.length();
                while ((p = tagLine.lastIndexOf('\t', lp - 1)) > 0) {
                    //log.fine(" p = " + p + " lp = " + lp);
                    String fld = tagLine.substring(p + 1, lp);
                    //log.fine("FIELD===" + fld);
                    lp = p;
                    if (fld.startsWith("line:")) {
                        int sep = fld.indexOf(':');
                        lnum = fld.substring(sep + 1);
                    } else if (fld.startsWith("signature:")) {
                        int sep = fld.indexOf(':');
                        signature = fld.substring(sep + 1);
                    } else if (fld.indexOf(':') < 0) {
                        kind = fld;
                        break;
                    } else {
                        inher = fld;
                    }
                }

                final String match;
                int mlength = p - mstart;
                if ((p > 0) && (mlength > MIN_METHOD_LINE_LENGTH)) {
                    if (mlength < MAX_METHOD_LINE_LENGTH) {
                    match = tagLine.substring(mstart + 3, p - 4).
                            replace("\\/", "/").replaceAll("[ \t]+", " ");
                    } else {
                        LOGGER.log(Level.FINEST, "Ctags: stripping method body for def {0} line {1}(scopes might break)", new Object[]{def, lnum});
                        match = tagLine.substring(mstart + 3, mstart + MAX_METHOD_LINE_LENGTH - 1). // +3 - 4 = -1
                            replace("\\/", "/").replaceAll("[ \t]+", " ");
                    }                    
                } else {                    
                    continue;
                }

                // Bug #809: Keep track of which symbols have already been
                // seen to prevent duplicating them in memory.
                final Interner<String> seenSymbols = new Interner<>();

                final String type =
                        inher == null ? kind : kind + " in " + inher;
                addTag(defs, seenSymbols, lnum, def, type, match, inher);
                if (signature != null) {
                    //TODO if some languages use different character for separating arguments, below needs to be adjusted
                    String[] args = signature.split(",");
                    for (String arg : args) {
                        //log.fine("Param = "+ arg);
                        int space = arg.lastIndexOf(' ');//TODO this is not the best way, but works to find the last string(name) in the argument, hence skipping type
                        if (space > 0 && space < arg.length()) {
                            String afters=arg.substring(space+1);
                            //FIXME this will not work for typeless languages such as python or assignments inside signature ... but since ctags doesn't provide signatures for python yet and assigning stuff in signature is not the case for c or java, we don't care ...
                            String[] names=afters.split("[\\W]"); //this should just parse out variables, we assume first non empty text is the argument name
                            for (String name : names) {
                             if (name.length()>0) {
                              //log.fine("Param Def = "+ string);
                              addTag(defs, seenSymbols, lnum, name, "argument",
                                     def.trim() + signature.trim(), null);
                              break;
                             }
                            }
                        }
                    }
                }
            //log.fine("Read = " + def + " : " + lnum + " = " + kind + " IS " + inher + " M " + match);
            } while (true);
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "CTags parsing problem: ", e);
        }
        LOGGER.severe("CTag reader cycle was interrupted!");
    }

    /**
     * Add a tag to a {@code Definitions} instance.
     */
    private void addTag(Definitions defs, Interner<String> seenSymbols,
            String lnum, String symbol, String type, String text, String scope) {
        // The strings are frequently repeated (a symbol can be used in
        // multiple definitions, multiple definitions can have the same type,
        // one line can contain multiple definitions). Intern them to minimize
        // the space consumed by them (see bug #809).
        defs.addTag(Integer.parseInt(lnum), seenSymbols.intern(symbol.trim()),
            seenSymbols.intern(type.trim()), seenSymbols.intern(text.trim()),
            scope == null ? null : seenSymbols.intern(scope.trim()));
    }
}
