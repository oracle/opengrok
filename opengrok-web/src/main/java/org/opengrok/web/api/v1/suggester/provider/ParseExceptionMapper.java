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
package org.opengrok.web.api.v1.suggester.provider;

import org.apache.lucene.queryparser.classic.ParseException;
import org.opengrok.web.api.error.ExceptionMapperUtils;

import javax.ws.rs.core.Response;
import javax.ws.rs.ext.ExceptionMapper;
import javax.ws.rs.ext.Provider;

/**
 * Maps the {@link ParseException} to a {@link javax.ws.rs.core.Response.Status#BAD_REQUEST} status.
 */
@Provider
public class ParseExceptionMapper implements ExceptionMapper<ParseException> {

    @Override
    public Response toResponse(final ParseException e) {
        return ExceptionMapperUtils.toResponse(Response.Status.BAD_REQUEST, e);
    }

}
