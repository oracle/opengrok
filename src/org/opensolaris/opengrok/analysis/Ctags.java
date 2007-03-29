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
 * Copyright 2005 Sun Microsystems, Inc.  All rights reserved.
 * Use is subject to license terms.
 */

/*
 * ident	"%Z%%M% %I%     %E% SMI"
 */

package org.opensolaris.opengrok.analysis;

import java.io.*;
import java.util.*;

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
    public Ctags(String ctagprog) throws IOException {
        ctags = Runtime.getRuntime().exec(new String[] {ctagprog, 
                "--c-kinds=+l",
                "--java-kinds=+l",
                "--sql-kinds=+l",
                "--Fortran-kinds=+L",
                "--C++-kinds=+l",
                "--file-scope=yes",
                "-u",
                "--filter=yes",
                "--filter-terminator=" + '\n',
                "--fields=-anf+iKnS",
                "--excmd=pattern",
		"--regex-Asm=/^[ \\t]*(ENTRY|ENTRY2|ALTENTRY)[ \\t]*\\(([a-zA-Z0-9_]+)/\\2/f,function/"  // for assmebly definitions
        }, new String[] {"LD_LIBRARY_PATH=/usr/lib"});
        ctagsIn = new OutputStreamWriter(ctags.getOutputStream());
        ctagsOut = new BufferedReader(new InputStreamReader(ctags.getInputStream()));
        tagFile = new StringBuilder(512);
    }
    
    public void close() throws IOException {
        ctagsIn.close();
        ctags.destroy();
    }
    
    public HashMap<String, HashMap<Integer, String>> doCtags(String file) throws IOException {
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
   /* 
    public boolean writeTagFile(HashMap<String, HashMap<Integer, String>> defs, String tagPath) {
        if(defs == null)
            return false;
        try {
            BufferedWriter out = new BufferedWriter(new FileWriter(tagPath));
            for(String def : defs.keySet()) {
                HashMap<Integer, String>tags = defs.get(def);
                if(tags != null) {
                    for(Integer line : tags.keySet()) {
                        out.write(def+"\t"+line+"\t"+tags.get(line)+"\n");
                    }
                }
            }
            out.close();
            return true;
        } catch (IOException e) {
            return false;
        }
    }
    */
    public  String tagString() {
        return tagFile.toString();
    }
    
    public void readTags() {
        try {
            do {
                String tagLine = ctagsOut.readLine();
                //System.out.println("Tagline:-->" + tagLine+"<----ONELINE");
                if (tagLine.length()==0) {
                    //System.out.println("RETURNING - EMPTY LINE");
                    return;
                }
                int p = tagLine.indexOf('\t');
                if (p <= 0) {
                    //System.out.println("SKIPPING LINE - NO TAB");
                    continue;
                }
                String def = tagLine.substring(0, p);
                int mstart = tagLine.indexOf('\t', p+1);
                String lnum = "-1";
                String signature = null;
                String match = null;
                String kind = null;
                String inher = null;
                
                int lp = tagLine.length();
                while((p = tagLine.lastIndexOf('\t', lp-1)) > 0) {
                    //System.out.println(" p = " + p + " lp = " + lp);
                    String fld = tagLine.substring(p+1,lp);
                    //System.out.println("FIELD===" + fld);
                    lp = p;
                    if(fld.startsWith("line:")) {
                        int sep = fld.indexOf(':');
                        lnum = fld.substring(sep+1);
                    } else if (fld.startsWith("signature:")) {
                        int sep = fld.indexOf(':');
                        signature = fld.substring(sep+1);
                    } else if (fld.indexOf(':') < 0) {
                        kind = fld;
                        break;
                    } else {
                        inher = fld;
                    }
                }
                if (p > 0 )
                    if(p - mstart > 6) {
                    match = tagLine.substring(mstart+3, p-4);
                    match = match.replaceAll("\\/", "/");
                    match = match.replaceAll("[ \t]+", " ");
                    } else {
                    //System.out.println("SKIPPING LINE - NO SECOND TAB");
                    continue;
                    }
                HashMap<Integer, String> taglist = defs.get(def);
                if (taglist == null) {
                    taglist = new HashMap<Integer, String>();
                    defs.put(def, taglist);
                }
                taglist.put(new Integer(lnum),kind);
                tagFile.append(def+'\t'+lnum+ '\t'+ kind+(inher != null ? (" in " + inher) : "")+ '\t'+ match+'\n');
                if(signature != null) {
                    String[] args = signature.split("[ ]*[^a-z0-9_]+[ ]*");
                    for(String arg : args) {
                        //System.out.println("Param = "+ arg);
                        int space = arg.lastIndexOf(' ');
                        if (space > 0 && space < arg.length()) {
                            if(arg.charAt(space+1) == '*') {
                                int ptr = arg.lastIndexOf('*');
                                if(ptr > 0) {
                                    space = ptr;
                                }
                            }
                            String argDef = arg.substring(space+1);
                            //System.out.println("Param Def = "+ argDef);
                            HashMap<Integer, String> itaglist = defs.get(argDef);
                            if(itaglist == null) {
                                itaglist = new HashMap<Integer, String>();
                                defs.put(argDef, itaglist);
                            }
                            itaglist.put(new Integer(lnum), "a");
                            tagFile.append(argDef + '\t' + lnum + "\targument\t" + def+signature+'\n');
                        }
                    }
                }
                //System.out.println("Read = " + def + " : " + lnum + " = " + kind + " IS " + inher + " M " + match);
            } while(true);
        } catch (Exception e) {
            e.printStackTrace();
        }
        System.err.println("SHOULD NOT HAVE COME HERE");
    }
    
    public static void main(String[] args) {
        try{
            Ctags ct = new Ctags(args[0]);
            BufferedReader inr = new BufferedReader(new InputStreamReader(System.in));
            String file;
            while((file = inr.readLine()) != null) {
                System.out.println("file: " + file);
                HashMap<String, HashMap<Integer, String>> tdefs = ct.doCtags(file + "\n");
                System.out.println("Tags = " + tdefs);
                System.out.println(ct.tagString());
                //ct.writeTagFile(tdefs, "/somefile");
            }
        } catch ( Exception e) {
            e.printStackTrace();
        }
    }
}
