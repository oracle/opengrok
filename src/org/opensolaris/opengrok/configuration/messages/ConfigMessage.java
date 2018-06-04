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

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.TreeSet;

/**
 * A message to retrieve/change the configuration.
 *
 * @author Vladimir Kotal
 * @author Krystof Tulinger
 */
public class ConfigMessage extends Message {

    private static final Set<String> allowedTags = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
            "setconf",
            "getconf",
            "auth",
            "get",
            "set"
    )));

    ConfigMessage() {
    }

    @Override
    public void validate() throws ValidationException {

        Set<String> tagCopy = new TreeSet<>(allowedTags);
        tagCopy.retainAll(getTags());
        if (tagCopy.size() > 1) {
            throw new ValidationException("The message tag must be one of '" + allowedTags.toString() + "'");
        }

        if (hasTag("setconf")) {
            if (getText() == null) {
                throw new ValidationException("The setconf message must contain a text.");
            }
        } else if (hasTag("getconf")) {
            if (getText() != null) {
                throw new ValidationException("The getconf message should not contain a text.");
            }
            if (getTags().size() != 1) {
                throw new ValidationException("The getconf message should be the only tag.");
            }
        } else if (hasTag("set") || hasTag("get")) {
            if (getText() == null) {
                throw new ValidationException("The get/set message must contain a text.");
            }
            if (getTags().size() != 1) {
                throw new ValidationException("The get/set message should be the only tag.");
            }
        } else if (hasTag("auth")) {
            if (!"reload".equalsIgnoreCase(getText())) {
                throw new ValidationException("The auth message can only accept a text \"reload\".");
            }
            if (getTags().size() != 1) {
                throw new ValidationException("The auth message should be the only tag.");
            }
        } else {
            throw new ValidationException("The message tag must be either setconf, getconf, auth or set");
        }

        super.validate();
    }
}
