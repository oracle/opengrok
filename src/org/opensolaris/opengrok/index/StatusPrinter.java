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
 * ident      "%Z%%M% %I%     %E% SMI"
 */

package org.opensolaris.opengrok.index;

import java.util.ArrayList;

/**
 *
 * @author Chandan
 */
public class StatusPrinter implements Printer {
    private ArrayList<String> status;
    private StringBuilder sb;
    private boolean lastline;
    public StatusPrinter() {
        sb = new StringBuilder();
        status = new ArrayList<String>(4);
    }
    public String getStatus() {
        sb.setLength(0);
        for(String s: status) {
            sb.append(s);
        }
        return sb.toString();
    }
    public void print(String s) {
        if(lastline) {
            status.clear();
            lastline = false;
        }
        status.add(s);
    }
    public void println(String s) {
        if(lastline)
            status.clear();
        else 
            lastline = true;
        status.add(s);
    }
    public static void main(String[] args) throws Throwable {
        StatusPrinter sp = new StatusPrinter();
        sp.print("ha");
        sp.print(" ha");
        sp.println(" ho!");
        System.out.println(sp.getStatus());
    }
}
