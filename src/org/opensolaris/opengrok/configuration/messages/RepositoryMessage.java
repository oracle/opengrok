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
package org.opensolaris.opengrok.configuration.messages;

import java.util.Collections;
import java.util.Set;

/**
 * repository specific message
 *
 * @author Vladimir Kotal
 */
public class RepositoryMessage extends Message {

    private static final Set<String> allowedTexts = Collections.singleton("get-repo-type");

    RepositoryMessage() {
    }

    /**
     * Validate the message.
     * Tag is repository path, text is command.
     * @throws ValidationException if message has invalid format
     */
    @Override
    public void validate() throws ValidationException {
        String command = getText();

        // The text field carries the command.
        if (command == null) {
            throw new ValidationException("The message text must contain one of '" + allowedTexts.toString() + "'");
        }
        if (!allowedTexts.contains(command)) {
            throw new ValidationException("The message text must contain one of '" + allowedTexts.toString() + "'");
        }

        if (getTags().isEmpty()) {
            throw new ValidationException("All repository messages must have at least one tag");
        }
        
        super.validate();
    }
}
