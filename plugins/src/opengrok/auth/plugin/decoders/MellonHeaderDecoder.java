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
 * Decode basic headers coming from the
 * <a href="https://github.com/Uninett/mod_auth_mellon">mod_auth_mellon</a> module
 * for Apache web server.
 *
 * This decoder assumes that the SAML Service Provider metadata was setup with
 * {@code <NameIDFormat>urn:oasis:names:tc:SAML:1.1:nameid-format:emailAddress</NameIDFormat>}
 * i.e. that Identity Provider will send back e-mail address of the authenticated user
 * and that the {@code mod_auth_mellon} is setup to create Apache environment variable
 * containing the e-mail address and the {@code mod_headers} Apache module is set to
 * pass the value of this variable in HTTP header called {@code MELLON_email}, i.e.:
 * {@code RequestHeader set email "%{MELLON_email}e" env=MELLON_email}
 */
public class MellonHeaderDecoder implements IUserDecoder {

    private static final Logger LOGGER = Logger.getLogger(MellonHeaderDecoder.class.getName());

    static String MELLON_EMAIL_HEADER = "MELLON_email";

    @Override
    public User fromRequest(HttpServletRequest request) {
        String username = request.getHeader(MELLON_EMAIL_HEADER);
        if (username == null || username.isEmpty()) {
            LOGGER.log(Level.WARNING,
                    "Can not construct an user: username could not be extracted");
            return null;
        }

        return new User(username);
    }
}
