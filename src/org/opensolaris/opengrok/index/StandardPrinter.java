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

import java.io.PrintStream;


/**
 *
 * @author Chandan
 */
public class StandardPrinter implements Printer {
    private PrintStream out;
    public StandardPrinter(PrintStream out) {
        this.out = out;
    }
    public void print(String s) {
        out.print(s);
    }
    public void println(String s) {
        out.println(s);
    }
}
