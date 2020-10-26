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

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import org.junit.Assert;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

public final class JSONUtils {
    private JSONUtils() {
        // private to ensure static
    }

    protected static Set<String> getTopLevelJSONFields(String jsonString) throws IOException {
        Set<String> fields = new HashSet<>();
        JsonFactory jfactory = new JsonFactory();
        JsonParser jParser = jfactory.createParser(jsonString);

        Assert.assertNotNull(jParser);

        JsonToken token;
        jParser.nextToken(); // skip the initial START_OBJECT
        while ((token = jParser.nextToken()) != JsonToken.END_OBJECT) {
            if (token == JsonToken.START_OBJECT) {
                while (jParser.nextToken() != JsonToken.END_OBJECT) {
                }
            }

            if (token != JsonToken.FIELD_NAME) {
                continue;
            }

            fields.add(jParser.getCurrentName());
        }
        jParser.close();

        return fields;
    }
}
