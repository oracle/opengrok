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

package org.opengrok.web.api.v1.filter;

import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.container.ContainerResponseFilter;
import jakarta.ws.rs.ext.Provider;

@Provider
@CorsEnable
public class CorsFilter implements ContainerResponseFilter {

    public static final String ALLOW_CORS_HEADER = "Access-Control-Allow-Origin";
    public static final String CORS_REQUEST_HEADER = "Origin";

    /**
     * Method for ContainerResponseFilter.
     */
    @Override
    public void filter(ContainerRequestContext request, ContainerResponseContext response) {
        // if there is no Origin header, then it is not a
        // cross origin request. We don't do anything.
        if (request.getHeaderString(CORS_REQUEST_HEADER) == null) {
            return;
        }

        response.getHeaders().add(ALLOW_CORS_HEADER, "*");
    }
}