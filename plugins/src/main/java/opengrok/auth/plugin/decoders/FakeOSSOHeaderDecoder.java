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
 * Copyright (c) 2017, 2021, Oracle and/or its affiliates. All rights reserved.
 */
package opengrok.auth.plugin.decoders;

/**
 * Almost like @{code OSSOHeaderDecoder} however uses OSSO HTTP headers with
 * prefix which allows for custom header insertion.
 * This class should therefore only be used for debugging.
 *
 * @author Krystof Tulinger
 */
public class FakeOSSOHeaderDecoder extends OSSOHeaderDecoder {

    private static final String PREFIX = "fake-";
    
    public FakeOSSOHeaderDecoder() {
        OSSO_COOKIE_TIMESTAMP_HEADER = PREFIX + OSSOHeaderDecoder.OSSO_COOKIE_TIMESTAMP_HEADER;
        OSSO_TIMEOUT_EXCEEDED_HEADER = PREFIX + OSSOHeaderDecoder.OSSO_TIMEOUT_EXCEEDED_HEADER;
        OSSO_SUBSCRIBER_DN_HEADER = PREFIX + OSSOHeaderDecoder.OSSO_SUBSCRIBER_DN_HEADER;
        OSSO_SUBSCRIBER_HEADER = PREFIX + OSSOHeaderDecoder.OSSO_SUBSCRIBER_HEADER;
        OSSO_USER_DN_HEADER = PREFIX + OSSOHeaderDecoder.OSSO_USER_DN_HEADER;
        OSSO_USER_GUID_HEADER = PREFIX + OSSOHeaderDecoder.OSSO_USER_GUID_HEADER;
    }
}
