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
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Get authenticated user principal and use it to create User object.
 * This works e.g. with HTTP Basic authentication headers as per RFC 7617.
 *
 * @author Vladimir Kotal
 */
public class UserPrincipalDecoder implements IUserDecoder {

    private static final Logger LOGGER = Logger.getLogger(UserPrincipalDecoder.class.getName());

    @Override
    public User fromRequest(HttpServletRequest request) {
        if (request.getUserPrincipal() == null) {
            return null;
        }

        String username = request.getUserPrincipal().getName();
        if (username == null || username.isEmpty()) {
            LOGGER.log(Level.WARNING,
                    "Can not construct User object: cannot get user principal from: {0}",
                    request);
            return null;
        }

        return new User(username);
    }
}