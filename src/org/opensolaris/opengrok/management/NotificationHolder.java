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
package org.opensolaris.opengrok.management;

import javax.management.NotificationListener;
import javax.management.NotificationFilter;

/**
 * @author Jan S Berg
 */
public class NotificationHolder {

    private NotificationListener notl = null;
    private Object obNL = null;
    private NotificationFilter nfilt = null;

    public NotificationHolder(NotificationListener nl, NotificationFilter nf, Object obj) {
        notl = nl;
        obNL = obj;
        nfilt = nf;
    }

    public NotificationListener getNL() {
        return notl;
    }

    public Object getObj() {
        return obNL;
    }

    public NotificationFilter getFilter() {
        return nfilt;
    }
}