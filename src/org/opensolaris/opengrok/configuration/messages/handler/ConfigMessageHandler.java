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
import org.opensolaris.opengrok.util.ClassUtil;

import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ConfigMessageHandler implements MessageHandler {

    /**
     * Pattern describes the java variable name and the assigned value.
     * Examples:
     * <ul>
     * <li>variable = true</li>
     * <li>stopOnClose = 10</li>
     * </ul>
     */
    private static final Pattern VARIABLE_PATTERN = Pattern.compile("([a-z_]\\w*) = (.*)");

    private RuntimeEnvironment env;

    public ConfigMessageHandler(final RuntimeEnvironment env) {
        if (env == null) {
            throw new IllegalArgumentException("Environment cannot be null");
        }
        this.env = env;
    }

    @Override
    public Response handle(final Message message) throws HandleException {
        if (message.hasTag("getconf")) {
            return Response.of(env.getConfiguration().getXMLRepresentationAsString());
        } else if (message.hasTag("auth") && "reload".equalsIgnoreCase(message.getText())) {
            env.getAuthorizationFramework().reload();
        } else if (message.hasTag("set")) {
            Matcher matcher = VARIABLE_PATTERN.matcher(message.getText());
            if (matcher.find()) {
                // set the property
                try {
                    ClassUtil.invokeSetter(
                            env.getConfiguration(),
                            matcher.group(1), // field
                            matcher.group(2) // value
                    );
                } catch (IOException e) {
                    throw new HandleException(e);
                }
                // apply the configuration - let the environment reload the configuration if necessary
                env.applyConfig(env.getConfiguration(), false);
                return Response.of(String.format("Variable \"%s\" set to \"%s\".", matcher.group(1), matcher.group(2)));
            } else {
                // invalid pattern
                throw new HandleException(
                        String.format("The pattern \"%s\" does not match \"%s\".",
                                VARIABLE_PATTERN.toString(),
                                message.getText()));
            }
        } else if (message.hasTag("get")) {
            try {
                return Response.of(ClassUtil.invokeGetter(env.getConfiguration(), message.getText()));
            } catch (IOException e) {
                throw new HandleException(e);
            }
        } else if (message.hasTag("setconf")) {
            env.applyConfig(message, message.hasTag("reindex"));
        }
        return Response.empty();
    }

}
