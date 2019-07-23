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
 * Copyright (c) 2019, Oracle and/or its affiliates. All rights reserved.
 */

package opengrok.auth.plugin.decoders;

import opengrok.auth.plugin.entity.User;

import javax.servlet.http.HttpServletRequest;
import java.util.Base64;
import java.util.Collections;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Decode HTTP Basic authentication headers to form a User object.
 *
 * @author Vladimir Kotal
 */
public class HttpBasicAuthHeaderDecoder implements IUserDecoder {

    private static final Logger LOGGER = Logger.getLogger(MellonHeaderDecoder.class.getName());

    static final String AUTHORIZATION_HEADER = "authorization";
    static final String BASIC = "Basic";

    @Override
    public User fromRequest(HttpServletRequest request) {
        String authHeader = request.getHeader(AUTHORIZATION_HEADER);
        if (authHeader == null) {
            LOGGER.log(Level.FINE, "no {0} header in request {1}",
                    new Object[]{AUTHORIZATION_HEADER, request});
            return null;
        }

        if (!authHeader.startsWith(BASIC)) {
            LOGGER.log(Level.WARNING, "{0} header does not start with {1}: {2}",
                    new Object[]{AUTHORIZATION_HEADER, BASIC, authHeader});
            return null;
        }

        String encodedValue = authHeader.split(" ")[1];
        Base64.Decoder decoder = Base64.getDecoder();
        String username = new String(decoder.decode(encodedValue)).split(":")[0];
        if (username == null || username.isEmpty()) {
            LOGGER.log(Level.WARNING,
                    "Can not construct User object: header ''{1}'' not found in request headers: {0}",
                    new Object[]{String.join(",", Collections.list(request.getHeaderNames())),
                            AUTHORIZATION_HEADER});
            return null;
        }

        return new User(username);
    }
}