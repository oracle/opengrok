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
 * ident	"@(#)SCCSget.java 1.1     05/11/11 SMI"
 */

package org.opensolaris.opengrok.history;

import java.io.*;
import java.util.*;

/**
 * Opens a s.file and reads out a required revision
 */
public class SCCSget extends FilterInputStream {
    boolean pass;
    int field;
    boolean sep;
    String rev;
    int version = 1;
    boolean binary;
    HashMap<String, Integer> deltaTable;
    
    private byte[] dBuf;	// buffer to put decoded bytes
    private int dSize = 0;	// size of dBuf
    private int dIndex = 0;	// index into dBuf
    
    /**
     *
     * @param in
     * @param rev
     */
    public SCCSget(InputStream in, String rev) {
        super(in);
        pass = sep = binary = false;
        field = 0;
        deltaTable = new HashMap<String, Integer>();
        this.rev = rev;
        dBuf = new byte[45];
    }
    
    /**
     *
     * @return if the s-dot-file contains ascii encoded binary files
     */
    public boolean isBinary() {
        return binary;
    }
 /*
    public String readRecord() throws java.io.IOException {
        sep = true;
        StringBuilder record = new StringBuilder(128);
        int c;
        while ((c = read()) > 01) {
            record.append((char)c);
        }
        if (record.length() > 1) {
            return record.toString();
        } else {
            return null;
        }
    }
  */
    /**
     *
     * @throws java.io.IOException
     */
    public final int read() throws java.io.IOException {
        int c, d, delta, d1;
        int till = 0;
        if(binary && dIndex < dSize) {
            return dBuf[dIndex++] & 0xff;
        }
        
        while((c = in.read()) != -1) {
            if(c == 01) {
                d = in.read();
                switch (d) {
                    case 'I':
                        delta = readEol();
                        //System.out.println("I = " + delta);
                        if (pass && delta > version) {
                            //System.out.println("---- IGNORING --- ");
                            till = delta;
                            pass = false;
                        }
                        break;
                    case 'D':
                        delta = readEol();
                        //System.out.println("D = " + delta);
                        if (pass && delta <= version) {
                            //System.out.println("---- IGNORING --- ");
                            till = delta;
                            pass = false;
                        }
                        break;
                    case 'E':
                        delta = readEol();
                        //System.out.println("E = " + delta);
                        if(!pass && delta == till) {
                            pass = true;
                            //System.out.println("---- starting --- ");
                        }
                        break;
                    case 'd':
                        readDelta();
                        break;
                    case 'f':
                        in.read();
                        d1 = in.read();
                        switch (d1) {
                            case 'e':
                                if(readEol() == 1) {
                                    binary = true;
                                }
                                break;
                            default:
                                readEol();
                        }
                        break;
                    case 'T':
                        //System.out.println(deltaTable);
                        Integer delVersion;
                        if ((delVersion =  deltaTable.get(rev)) != null) {
                            version = delVersion.intValue();
                        } else {
                            throw new IOException("No such verson");
                        }
                        in.read();	// ignore the newline after <soh>T
                        pass = true;
                        break;
                    default:
                        pass = false;
                }
            } else {
                if (pass) {
                    if (binary) {
                        //System.out.println("dIndex = " + dIndex + " dSize=" + dSize);
                        if (dIndex >= dSize && !uuDecode(c)) {
                            return -1;
                        }
                        //System.out.println("XXX: returning [" + dIndex + "] = " + dBuf[dIndex]);
                        return dBuf[dIndex++] & 0xff;
                    } else {
                        return(c);
                    }
                }
            }
        }
        return(-1);
    }
    
    private int readEol() throws java.io.IOException {
        int c;
        int val = 0;
        do {
            c = in.read();
            if (c > 0x29 && c < 0x40) {
                val = val*10 + c - 0x30;
            }
        } while( c != -1 && c != '\n');
        return val;
    }
    
    private void readDelta() throws java.io.IOException {
        int c;
        int val = 0;
        StringBuilder rev = new StringBuilder(8);
        c = in.read();
        c = in.read();
        if(c == 'D') {
            in.read();
            /* read revision */
            while((c = in.read()) != ' ') {
                if(c == '\n' || c == -1) {
                    return;
                }
                rev.append((char)c);
            }
            //System.out.print("REv = "+ rev);
            /* skip date time uid */
            int skip = 3;
            while(skip > 0) {
                c = in.read();
                if(c == '\n' || c == -1)
                    return;
                if(c == ' ')
                    skip--;
            }
            
            /* read delta */
            do {
                c = in.read();
                if (c > 0x29 && c < 0x40) {
                    val = val*10 + c - 0x30;
                }
                if(c == '\n' || c == -1)
                    return;
            } while(c!= ' ');
            //System.out.println(" VAL = "+ val);
        }
        do {
            c = in.read();
        } while (c != -1 && c != '\n');
        if (val != 0) {
            deltaTable.put(rev.toString(), new Integer(val));
        }
        return;
    }
    
    public int read(byte b[], int off, int len) throws IOException {
        if (b == null) {
            throw new NullPointerException();
        } else if ((off < 0) || (off > b.length) || (len < 0) ||
                ((off + len) > b.length) || ((off + len) < 0)) {
            throw new IndexOutOfBoundsException();
        } else if (len == 0) {
            return 0;
        }
        
        int c = read();
        if (c == -1) {
            return -1;
        }
        b[off] = (byte)c;
        
        int i = 1;
        try {
            for (; i < len ; i++) {
                c = read();
                if (c == -1) {
                    break;
                }
                if (b != null) {
                    b[off + i] = (byte)c;
                }
            }
        } catch (IOException ee) {
        }
        return i;
    }
    
    private boolean uuDecode(int count) throws IOException {
        if (count == -1 || count == '\n') {
            return false;
        }
        count = (count - ' ') & 0x3f;
        if (count == 0) {
            return false;
        }
        if (count > 45) {
            throw new IOException("UUdecode: format excpetion: More than 45 encoded chars in a line");
        }
        byte a, b;
        dSize = 0;
        dIndex = 0;
        int x, y;
        
        while (dSize < count) {
            x = in.read();
            y = in.read();
            if(x == -1 || y == -1) {
                return false;
            }
            if(x == '\n' || y == '\n') {
                throw new IOException("UUdecode: Format Excpetion: Unexpected EOL!");
            }
            a = (byte)((x - ' ') & 0x3f);
            b = (byte)((y - ' ') & 0x3f);
            dBuf[dSize++] = (byte)(((a << 2) & 0xfc) | ((b >>> 4) & 3));
            
            if (dSize < count) {
                a = b;
                x = in.read();
                if(x == -1) {
                    return false;
                }
                if(x == '\n') {
                    throw new IOException("UUdecode: Format Excpetion: Unexpected EOL!");
                }
                b = (byte)((x - ' ') & 0x3f);
                dBuf[dSize++] = (byte)(((a << 4) & 0xf0) | ((b >>> 2) & 0xf));
            }
            
            if (dSize < count) {
                a = b;
                x = in.read();
                if(x == -1) {
                    return false;
                }
                if(x == '\n') {
                    throw new IOException("UUdecode: Format Excpetion: Unexpected EOL!");
                }
                b = (byte)((x - ' ') & 0x3f);
                dBuf[dSize++] = (byte)(((a << 6) & 0xc0) | (b & 0x3f));
            }
        }
        do {
            x = in.read();
        } while (x != '\n' && x != -1);
        return true;
    }
    
    /**
     *
     * @param args
     */
    public static void main(String[] args) {
        try {
            SCCSget get = new SCCSget(new BufferedInputStream(new FileInputStream(args[0])),
                    args[1]);
//            OutputStreamWriter out = new OutputStreamWriter(System.out);
            int c;
            while ((c = get.read()) != -1) {
                System.out.write(c);
            }
            // out.close();
        } catch (Exception e) {
            System.err.println(e+"\n USAGE: s.file rev");
        }
    }
}
