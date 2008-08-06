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

import javax.management.Notification;
import javax.management.NotificationFilter;
import javax.management.timer.TimerNotification;

/**
 *
 * @author  Jan S Berg
 */
public class TimerFilter implements NotificationFilter {

    private Integer id = null;

    /** Creates a new instance of TimerFilter */
    public TimerFilter(Integer id) {
        this.id = id;
    }

    public boolean isNotificationEnabled(Notification n) {

        if (n.getType().equals("timer.notification")) {
            TimerNotification timerNotif = (TimerNotification) n;
            if (timerNotif.getNotificationID().equals(id)) {
                return true;
            }
        }
        return false;
    }
}
