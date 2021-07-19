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
 * Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
 */
package org.opengrok.web.api.v1;

import jakarta.ws.rs.ApplicationPath;
import org.glassfish.jersey.server.ResourceConfig;
import org.opengrok.web.api.v1.suggester.SuggesterAppBinder;

@ApplicationPath(RestApp.API_PATH)
public class RestApp extends ResourceConfig {

    public static final String API_PATH = "/api/v1";

    public RestApp() {
        register(new SuggesterAppBinder());
        packages("org.opengrok.web.api.constraints", "org.opengrok.web.api.error");
        packages(true, "org.opengrok.web.api.v1");
    }

}
