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
 * Copyright (c) 2018 Oracle and/or its affiliates. All rights reserved.
 */
package org.opensolaris.opengrok.web.api.error;

import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

public class ExceptionMapperUtils {

    private ExceptionMapperUtils() {
    }

    public static Response toResponse(final Response.Status status, final Exception e) {
        return Response.status(status)
                .entity(new ExceptionModel(e.getMessage()))
                .type(MediaType.APPLICATION_JSON)
                .build();
    }

    private static class ExceptionModel {

        private String message;

        public ExceptionModel(final String message) {
            this.message = message;
        }

        public String getMessage() {
            return message;
        }

        public void setMessage(final String message) {
            this.message = message;
        }
    }

}
