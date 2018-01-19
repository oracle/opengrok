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
 * Copyright (c) 2017, 2018, Oracle and/or its affiliates. All rights reserved.
 */
package org.opensolaris.opengrok.configuration.messages;

import java.io.IOException;
import java.util.Arrays;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.opensolaris.opengrok.configuration.RuntimeEnvironment;
import org.opensolaris.opengrok.util.BooleanUtil;
import org.opensolaris.opengrok.util.ClassUtil;


/**
 * A message to retrieve/change the configuration.
 *
 * @author Vladimir Kotal
 * @author Krystof Tulinger
 */
public class ConfigMessage extends Message {

    /**
     * Pattern describes the java variable name and the assigned value.
     * Examples:
     * <ul>
     * <li>variable = true</li>
     * <li>stopOnClose = 10</li>
     * </ul>
     */
    private static final Pattern VARIABLE_PATTERN = Pattern.compile("([a-z_]\\w*) = (.*)");

    @Override
    protected byte[] applyMessage(RuntimeEnvironment env) throws IOException {
        if (hasTag("getconf")) {
            return env.getConfiguration().getXMLRepresentationAsString().getBytes();
        } else if (hasTag("auth") && "reload".equalsIgnoreCase(getText())) {
            env.getAuthorizationFramework().reload();
        } else if (hasTag("set")) {
            Matcher matcher = VARIABLE_PATTERN.matcher(getText());
            if (matcher.find()) {
                // set the property
                ClassUtil.invokeSetter(
                        env.getConfiguration(),
                        matcher.group(1), // field
                        matcher.group(2) // value
                );
                // apply the configuration - let the environment reload the configuration if necessary
                env.applyConfig(env.getConfiguration(), false);
                return String.format("Variable \"%s\" set to \"%s\".", matcher.group(1), matcher.group(2)).getBytes();
            } else {
                // invalid pattern
                throw new IOException(
                        String.format("The pattern \"%s\" does not match \"%s\".",
                                VARIABLE_PATTERN.toString(),
                                getText()));
            }
        } else if (hasTag("get")) {
            return ClassUtil.invokeGetter(env.getConfiguration(), getText()).getBytes();
        } else if (hasTag("setconf")) {
            env.applyConfig(this, hasTag("reindex"));
        }

        return null;
    }

    @Override
    public void validate() throws Exception {
        Set<String> allowedTags = new TreeSet<>(Arrays.asList("setconf",
                "getconf", "auth", "get", "set"));

        Set<String> tagCopy = new TreeSet<>(allowedTags);
        tagCopy.retainAll(getTags());
        if (tagCopy.size() > 1) {
            throw new Exception("The message tag must be one of '" + allowedTags.toString() + "'");
        }

        if (hasTag("setconf")) {
            if (getText() == null) {
                throw new Exception("The setconf message must contain a text.");
            }
        } else if (hasTag("getconf")) {
            if (getText() != null) {
                throw new Exception("The getconf message should not contain a text.");
            }
            if (getTags().size() != 1) {
                throw new Exception("The getconf message should be the only tag.");
            }
        } else if (hasTag("set") || hasTag("get")) {
            if (getText() == null) {
                throw new Exception("The get/set message must contain a text.");
            }
            if (getTags().size() != 1) {
                throw new Exception("The get/set message should be the only tag.");
            }
        } else if (hasTag("auth")) {
            if (!"reload".equalsIgnoreCase(getText())) {
                throw new Exception("The auth message can only accept a text \"reload\".");
            }
            if (getTags().size() != 1) {
                throw new Exception("The auth message should be the only tag.");
            }
        } else {
            throw new Exception("The message tag must be either setconf, getconf, auth or set");
        }

        super.validate();
    }
}
