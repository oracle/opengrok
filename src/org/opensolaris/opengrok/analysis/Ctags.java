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
 * Copyright 2008 Sun Microsystems, Inc.  All rights reserved.
 * Use is subject to license terms.
 */
package org.opensolaris.opengrok.analysis;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.util.HashMap;
import org.opensolaris.opengrok.configuration.RuntimeEnvironment;

/**
 * Provides Ctags by having a running instance of ctags
 *
 * @author Chandan
 */
public class Ctags {

    private StringBuilder tagFile;
    private HashMap<String, HashMap<Integer, String>> defs;
    private Process ctags;
    private OutputStreamWriter ctagsIn;
    private BufferedReader ctagsOut;

    public Ctags() throws IOException {
        initialize();
    }

    public void close() throws IOException {
        ctagsIn.close();
        ctags.destroy();
    }

    private void initialize() throws IOException {
        ctags = Runtime.getRuntime().exec(new String[]{
            RuntimeEnvironment.getInstance().getCtags(),
            "--c-kinds=+l",
            "--java-kinds=+l",
            "--sql-kinds=+l",
            "--Fortran-kinds=+L",
            "--C++-kinds=+l",
            "--file-scope=yes",
            "-u",
            "--filter=yes",
            "--filter-terminator=__ctags_done_with_file__" + '\n',
            "--fields=-anf+iKnS",
            "--excmd=pattern",
            "--regex-Asm=/^[ \\t]*(ENTRY|ENTRY2|ALTENTRY)[ \\t]*\\(([a-zA-Z0-9_]+)/\\2/f,function/"  // for assmebly definitions
        });
        ctagsIn = new OutputStreamWriter(ctags.getOutputStream());
        ctagsOut = new BufferedReader(new InputStreamReader(ctags.getInputStream()));
        tagFile = new StringBuilder(512);
    }

    public HashMap<String, HashMap<Integer, String>> doCtags(String file) throws IOException {
        boolean ctagsRunning = true;
        try {
            ctags.exitValue();
            ctagsRunning = false;
        // ctags is dead! we must restart!!!
        } catch (IllegalThreadStateException exp) {
        // ctags is still running :)
        }

        if (!ctagsRunning) {
            initialize();
        }

        defs = null;
        if (file.length() > 0 && !file.equals("\n")) {
            //System.out.println("doing >" + file + "<");
            ctagsIn.write(file);
            ctagsIn.flush();
            defs = new HashMap<String, HashMap<Integer, String>>();
            tagFile.setLength(0);
            readTags();
        //System.out.println("DONE >" + file + "<");
        }
        return defs;
    }

    public String tagString() {
        return tagFile.toString();
    }

    public void readTags() {
        try {
            do {
                String tagLine = ctagsOut.readLine();
                //System.out.println("Tagline:-->" + tagLine+"<----ONELINE");
                if (tagLine == null) {
                    System.err.println("Unexpected end of file!");
                    try {
                        int val = ctags.exitValue();
                        System.err.println("ctags exited with code: " + val);
                    } catch (Exception e) {}
                    Throwable t = new Throwable();
                    t.printStackTrace();
                    return ;
                }
                
                if (tagLine.equals("__ctags_done_with_file__")) {
                    return;
                }
                int p = tagLine.indexOf('\t');
                if (p <= 0) {
                    //System.out.println("SKIPPING LINE - NO TAB");
                    continue;
                }
                String def = tagLine.substring(0, p);
                int mstart = tagLine.indexOf('\t', p + 1);
                String lnum = "-1";
                String signature = null;
                String match = null;
                String kind = null;
                String inher = null;

                int lp = tagLine.length();
                while ((p = tagLine.lastIndexOf('\t', lp - 1)) > 0) {
                    //System.out.println(" p = " + p + " lp = " + lp);
                    String fld = tagLine.substring(p + 1, lp);
                    //System.out.println("FIELD===" + fld);
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
                if (p > 0) {
                    if (p - mstart > 6) {
                        match = tagLine.substring(mstart + 3, p - 4);
                        match = match.replaceAll("\\/", "/");
                        match = match.replaceAll("[ \t]+", " ");
                    } else {
                        continue;
                    }
                }
                HashMap<Integer, String> taglist = defs.get(def);
                if (taglist == null) {
                    taglist = new HashMap<Integer, String>();
                    defs.put(def, taglist);
                }
                taglist.put(new Integer(lnum), kind);
                tagFile.append(def + '\t' + lnum + '\t' + kind + (inher != null ? (" in " + inher) : "") + '\t' + match + '\n');
                if (signature != null) {
                    String[] args = signature.split("[ ]*[^a-z0-9_]+[ ]*");
                    for (String arg : args) {
                        //System.out.println("Param = "+ arg);
                        int space = arg.lastIndexOf(' ');
                        if (space > 0 && space < arg.length()) {
                            if (arg.charAt(space + 1) == '*') {
                                int ptr = arg.lastIndexOf('*');
                                if (ptr > 0) {
                                    space = ptr;
                                }
                            }
                            String argDef = arg.substring(space + 1);
                            //System.out.println("Param Def = "+ argDef);
                            HashMap<Integer, String> itaglist = defs.get(argDef);
                            if (itaglist == null) {
                                itaglist = new HashMap<Integer, String>();
                                defs.put(argDef, itaglist);
                            }
                            itaglist.put(new Integer(lnum), "a");
                            tagFile.append(argDef + '\t' + lnum + "\targument\t" + def + signature + '\n');
                        }
                    }
                }
            //System.out.println("Read = " + def + " : " + lnum + " = " + kind + " IS " + inher + " M " + match);
            } while (true);
        } catch (Exception e) {
            e.printStackTrace();
        }
        System.err.println("SHOULD NOT HAVE COME HERE");
    }
}
