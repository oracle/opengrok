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
 * Copyright 2005 Trond Norbye.  All rights reserved.
 * Use is subject to license terms.
 */

/*
 * ident	"@(#)Main.java 1.1     06/02/22 SMI"
 */

package org.opensolaris.opengrok.search.scope;

import java.awt.GraphicsEnvironment;


/**
 * This is the entry point of the CodeSearch. Print out an error message
 * if the application is started without an associated display.
 *
 * @author Trond Norbye
 */
public class Main {
    /**
     * This is the program entry point. It will start the graphical version if there is an 
     * associated display. Otherwise a CLI version will be started (if I implement one ;))
     * @param args the command line arguments
     */
    public static void main(String[] args) {
        if (GraphicsEnvironment.isHeadless()) {
            System.err.println(
                "The current version of CodeSearch cannot be started without a display");
        } else {
            MainFrame.main(args);
        }
    }
}
