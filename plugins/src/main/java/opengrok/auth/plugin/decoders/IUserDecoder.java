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
 * Copyright (c) 2017, 2019, Oracle and/or its affiliates. All rights reserved.
 */
package opengrok.auth.plugin.decoders;

import javax.servlet.http.HttpServletRequest;
import opengrok.auth.plugin.entity.User;

/**
 *
 * @author Krystof Tulinger
 */
public interface IUserDecoder {

    /**
     * Creates the User from the http request.
     *
     * Fills all parameters given in the request into the user.
     *
     * @param request the request
     * @return the user object or null (if it could not be constructed)
     */
    User fromRequest(HttpServletRequest request);
}
