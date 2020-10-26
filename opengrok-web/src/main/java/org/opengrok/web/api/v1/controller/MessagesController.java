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
 * Copyright (c) 2018, 2019, Oracle and/or its affiliates. All rights reserved.
 */
package org.opengrok.web.api.v1.controller;

import org.opengrok.indexer.configuration.RuntimeEnvironment;
import org.opengrok.indexer.web.messages.Message;
import org.opengrok.indexer.web.messages.MessagesContainer.AcceptedMessage;

import javax.validation.Valid;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.Collections;
import java.util.Set;

@Path("/messages")
public class MessagesController {

    private final RuntimeEnvironment env = RuntimeEnvironment.getInstance();

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    public Response addMessage(@Valid final Message message) {
        env.addMessage(message);

        return Response.status(Response.Status.CREATED).build();
    }

    @DELETE
    @Consumes(MediaType.TEXT_PLAIN)
    public void removeMessagesWithTag(@QueryParam("tag") final String tag, final String text) {
        if (tag == null) {
            throw new WebApplicationException("Message tag has to be specified", Response.Status.BAD_REQUEST);
        }
        env.removeAnyMessage(Collections.singleton(tag), text);
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Set<AcceptedMessage> getMessages(@QueryParam("tag") final String tag) {
        if (tag != null) {
            return env.getMessages(tag);
        } else {
            return env.getAllMessages();
        }
    }

}
