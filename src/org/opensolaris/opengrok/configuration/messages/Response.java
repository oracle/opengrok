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
package org.opensolaris.opengrok.configuration.messages;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class Response {

    private final List<String> data;

    private Response(final List<String> data) {
        this.data = data;
    }

    public static Response of(final String item) {
        if (item == null) {
            throw new IllegalArgumentException("Cannot instantiate response of null");
        }

        return new Response(Collections.singletonList(item));
    }

    public static Response empty() {
        return new Response(Collections.emptyList());
    }

    public Response combine(final Response other) {
        if (other == null) {
            throw new IllegalArgumentException("Cannot combine with null response");
        }

        List<String> combinedMessages = new ArrayList<>(data.size() + other.data.size());
        combinedMessages.addAll(data);
        combinedMessages.addAll(other.data);
        return new Response(combinedMessages);
    }

    public List<String> getData() {
        return new ArrayList<>(data);
    }

}
