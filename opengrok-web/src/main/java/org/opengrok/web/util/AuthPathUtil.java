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
 * Copyright (c) 2020 Oracle and/or its affiliates. All rights reserved.
 */

package org.opengrok.web.util;

import org.opengrok.indexer.authorization.AuthorizationFramework;
import org.opengrok.indexer.configuration.Project;
import org.opengrok.indexer.configuration.RuntimeEnvironment;

import javax.servlet.http.HttpServletRequest;

public class AuthPathUtil {
    private static final RuntimeEnvironment env = RuntimeEnvironment.getInstance();

    public static boolean isPathAuthorized(String path, HttpServletRequest request) {
        if (request != null) {
            AuthorizationFramework auth = env.getAuthorizationFramework();
            if (auth != null) {
                Project p = Project.getProject(path.startsWith("/") ? path : "/" + path);
                return p == null || auth.isAllowed(request, p);
            }
        }

        return false;
    }
}
