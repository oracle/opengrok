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
package org.opensolaris.opengrok.web.api.controller;

import org.opensolaris.opengrok.configuration.RuntimeEnvironment;
import org.opensolaris.opengrok.web.MessagesContainer;
import org.opensolaris.opengrok.web.MessagesContainer.Message;

import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.QueryParam;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.time.Duration;
import java.time.format.DateTimeParseException;
import java.util.Set;

@Path("/messages")
public class MessagesController {

    private static final String DEFAULT_DURATION = "PT10M";
    private static final String DEFAULT_TAGS = MessagesContainer.MESSAGES_MAIN_PAGE_TAG;
    private static final String DEFAULT_CSS_CLASS = "info";

    private final RuntimeEnvironment env = RuntimeEnvironment.getInstance();

    @PUT
    @Consumes(MediaType.TEXT_PLAIN)
    public void addMessage(
            @QueryParam("duration") @DefaultValue(DEFAULT_DURATION) final String durationStr,
            @QueryParam("tags") @DefaultValue(DEFAULT_TAGS) final Set<String> tags,
            @QueryParam("cssClass") @DefaultValue(DEFAULT_CSS_CLASS) final String cssClass,
            final String text
    ) {
        Duration duration = parseDuration(durationStr);

        Message msg;
        try {
            msg = new Message(text, tags, cssClass, duration);
        } catch (IllegalArgumentException e) {
            throw new WebApplicationException(e, Response.Status.BAD_REQUEST);
        }

        env.addMessage(msg);
    }

    private Duration parseDuration(final String duration) {
        try {
            return Duration.parse(duration);
        } catch (DateTimeParseException e) {
            throw new WebApplicationException(e, Response.Status.BAD_REQUEST);
        }
    }

    @DELETE
    public void removeMessagesWithTag(@QueryParam("tags") final Set<String> tags) {
        env.removeAnyMessage(tags);
    }

}
