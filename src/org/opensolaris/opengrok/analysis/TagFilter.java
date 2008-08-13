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
package org.opensolaris.opengrok.analysis;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.FilterReader;
import java.io.Reader;
import java.util.HashMap;

/**
 * A reader that reads only plain text from a HTML or XML file
 *
 * @author Chandan
 */
public class TagFilter extends FilterReader {
    private boolean pass;
    private int esc;
    private boolean p;
    private char[] esctag;
    private HashMap<String, Character> escs;
    public TagFilter(Reader in) {
        super(in);
        pass = true;
        esc = 0;
        p = false;
        esctag = new char[10];
        escs = new HashMap<String, Character>();
        escs.put("&gt", Character.valueOf('>'));
        escs.put("&lt",  Character.valueOf('<'));
        escs.put("&amp",  Character.valueOf('&'));
    }
    
    public final int read(char[] buf, int start, int len) throws java.io.IOException {
        int n=0;
        int c;
        while((c = this.read()) > -1 && n <= len && start < buf.length) {
            buf[start++] = (char) c;
            n++;
        }
        if (c == -1 && n == 0 && len != 0) {
            return -1;
        }
        return n;
    }
    
    public final int read() throws java.io.IOException {
        int c;
        while ((c = in.read()) != -1) {
            if (c == '<') {
                pass = false;
            } else if (c == '>') {
                pass = true;
                c = ' ';
            } else if(pass && c == '&') {
                esc = 0;
            }
            boolean sp =  isSpace(c);
            if(esc >= 0) {
                if(c == ';') {
                    Character ec = escs.get(new String(esctag,0,esc));
                    esc = -1;
                    if(ec != null) {
                        p = false;
                        return ec.charValue();
                    } else {
                        p = true;
                        return ' ';
                    }
                } else if (sp) {
                    esc = -1;
                } else {
                    if(esc < 10) {
                        esctag[esc++] = (char)c;
                    } else {
                        esc = -1;
                    }
                }
            } else if (pass && (!p || !sp)) {
                p = sp;
                return c;
            }
        }
        return -1;
    }
    
    public static boolean isSpace(int ch) {
        return (ch <= 0x0020) &&
                (((((1L << 0x0009) |
                (1L << 0x000A) |
                (1L << 0x000C) |
                (1L << 0x000D) |
                (1L << 0x0020)) >> ch) & 1L) != 0);
    }
    
    public static void main(String[] args) throws Throwable {
        BufferedReader r = new BufferedReader(new TagFilter(new FileReader(args[0])));
        String l;
        while ((l = r.readLine())!= null) {
            System.out.println(l);
        }
    }
}
