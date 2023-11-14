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
 * Copyright (c) 2017, Oracle and/or its affiliates. All rights reserved.
 */
package opengrok.auth.plugin.util;

import java.util.Date;

/**
 * Implementation of timestamp decoding.
 *
 * @author Krystof Tulinger
 * @see <a href="https://docs.oracle.com/cd/B28196_01/idmanage.1014/b15997/mod_osso.htm">mod_osso documentation</a>
 * chapter 9.5
 *
 */
public class Timestamp {

    private Timestamp() {
    }

    /**
     * Converts OSSO timestamp cookie into java Date.
     *
     * @param cookie string representing the timestamp cookie
     * @return java date object
     * @throws NumberFormatException number format exception
     */
    public static Date decodeTimeCookie(String cookie) throws NumberFormatException {
        return new Date(Long.parseLong(cookie, 16) * 1000);
    }

    /**
     * Converts Date into OSSO cookie.
     *
     * @param date date
     * @return string with the encoded value
     */
    public static String encodeTimeCookie(Date date) {
        return Long.toHexString(date.getTime() / 1000);
    }
}
