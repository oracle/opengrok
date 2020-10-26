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
 * Copyright (c) 2019, Oracle and/or its affiliates. All rights reserved.
 */
package org.opengrok.indexer.web.messages;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.opengrok.indexer.logger.LoggerFactory;

import java.util.logging.Level;

public interface JSONable {

    String EMPTY = "";

    /**
     * Convert object to JSON.
     * @return JSON string or empty string on error
     */
    default String toJSON() {
        ObjectMapper mapper = new ObjectMapper();
        try {
            return mapper.writeValueAsString(this);
        } catch (JsonProcessingException e) {
            LoggerFactory.getLogger(JSONable.class).log(Level.WARNING,
                    String.format("failed to convert object of class %s to JSON: %s",
                    this.getClass().toString(), this), e);
            return EMPTY;
        }
    }
}
