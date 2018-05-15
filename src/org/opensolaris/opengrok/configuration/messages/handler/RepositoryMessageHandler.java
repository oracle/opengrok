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
package org.opensolaris.opengrok.configuration.messages.handler;

import org.opensolaris.opengrok.configuration.RuntimeEnvironment;
import org.opensolaris.opengrok.configuration.messages.Message;
import org.opensolaris.opengrok.configuration.messages.MessageHandler;
import org.opensolaris.opengrok.configuration.messages.Response;
import org.opensolaris.opengrok.history.RepositoryInfo;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class RepositoryMessageHandler implements MessageHandler {

    private RuntimeEnvironment env;

    public RepositoryMessageHandler(final RuntimeEnvironment env) {
        if (env == null) {
            throw new IllegalArgumentException("Environment cannot be null");
        }
        this.env = env;
    }

    @Override
    public Response handle(final Message message) throws HandleException {
        String command = message.getText();

        if (!"get-repo-type".equals(command)) {
            throw new HandleException("Unknown command " + command);
        }

        List<String> types = new ArrayList<>(message.getTags().size());

        for (String projectName : message.getTags()) {
            boolean found = false;
            for (RepositoryInfo ri : env.getRepositories()) {
                if (ri.getDirectoryNameRelative().equals(projectName)) {
                    types.add(projectName + ":" + ri.getType());
                    found = true;
                    break;
                }
            }
            if (!found) {
                types.add(projectName + ":N/A");
            }
        }
        return Response.of(types.stream().collect(Collectors.joining("\n")));
    }

}
