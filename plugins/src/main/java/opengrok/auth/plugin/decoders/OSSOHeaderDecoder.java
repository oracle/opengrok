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
 * Portions Copyright (c) 2020, Chris Fraire <cfraire@me.com>.
 */
package opengrok.auth.plugin.decoders;

import java.util.Collections;
import java.util.Date;
import java.util.logging.Level;
import java.util.logging.Logger;

import jakarta.servlet.http.HttpServletRequest;
import opengrok.auth.plugin.entity.User;
import opengrok.auth.plugin.util.Timestamp;
import org.opengrok.indexer.web.Laundromat;

/**
 * Decode Oracle SSO specific headers.
 *
 * @author Krystof Tulinger
 */
public class OSSOHeaderDecoder implements IUserDecoder {

    private static final Logger LOGGER = Logger.getLogger(OSSOHeaderDecoder.class.getName());

    protected static String OSSO_COOKIE_TIMESTAMP_HEADER = "osso-cookie-timestamp";
    protected static String OSSO_TIMEOUT_EXCEEDED_HEADER = "osso-idle-timeout-exceeded";
    protected static String OSSO_SUBSCRIBER_DN_HEADER = "osso-subscriber-dn";
    protected static String OSSO_SUBSCRIBER_HEADER = "osso-subscriber";
    protected static String OSSO_USER_DN_HEADER = "osso-user-dn";
    protected static String OSSO_USER_GUID_HEADER = "osso-user-guid";

    @Override
    public User fromRequest(HttpServletRequest request) {
        String username, userguid, timeouted, timestamp;
        Date cookieTimestamp = null;

        // Avoid classification as a taint bug.
        username = Laundromat.launderInput(request.getHeader(OSSO_USER_DN_HEADER));
        timeouted = Laundromat.launderInput(request.getHeader(OSSO_TIMEOUT_EXCEEDED_HEADER));
        timestamp = Laundromat.launderInput(request.getHeader(OSSO_COOKIE_TIMESTAMP_HEADER));
        userguid = Laundromat.launderInput(request.getHeader(OSSO_USER_GUID_HEADER));
        
        if (username == null || username.isEmpty()) {
            LOGGER.log(Level.WARNING,
                    "Can not construct an user: username could not be extracted from headers: {0}",
                    String.join(",", Collections.list(request.getHeaderNames())));
            return null;
        }
        
        if (userguid == null || userguid.isEmpty()) {
            LOGGER.log(Level.WARNING,
                    "Can not construct an user: userguid could not be extracted from headers: {0}",
                    String.join(",", Collections.list(request.getHeaderNames())));
            return null;
        }

        /**
         * The timestamp cookie can be corrupted.
         */
        try {
            cookieTimestamp = Timestamp.decodeTimeCookie(timestamp);
        } catch (NumberFormatException ex) {
            LOGGER.log(Level.WARNING,
                    String.format("Unparseable timestamp cookie \"%s\" for user \"%s\"",
                            timestamp, username), ex);
        }

        /**
         * Creating new user entity with provided information. The entity can be
         * checked if the timeout expired via {@link User#isTimeouted()}.
         */
        User user = new User(username, userguid, cookieTimestamp,
                "true".equalsIgnoreCase(timeouted));

        user.setAttribute("subscriber-dn", request.getHeader(OSSO_SUBSCRIBER_DN_HEADER));
        user.setAttribute("subscriber", request.getHeader(OSSO_SUBSCRIBER_HEADER));

        if (user.isTimeouted()) {
            LOGGER.log(Level.WARNING, "Can not construct an user \"{0}\": header is timeouted",
                    username);
            return null;
        }

        return user;
    }
}
