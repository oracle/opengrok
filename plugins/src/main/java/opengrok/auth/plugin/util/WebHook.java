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
 * Copyright (c) 2019, 2021, Oracle and/or its affiliates. All rights reserved.
 */
package opengrok.auth.plugin.util;

import org.opengrok.indexer.configuration.OpenGrokThreadFactory;

import java.io.Serializable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class WebHook implements Serializable {
    private static final long serialVersionUID = -1;

    private String uri;
    private String content;

    public WebHook() {
    }

    WebHook(String uri, String content) {
        this.uri = uri;
        this.content = content;
    }

    public void setURI(String uri) {
        this.uri = uri;
    }
    public String getURI() {
        return uri;
    }

    public void setContent(String content) {
        this.content = content;
    }
    public String getContent() {
        return content;
    }

    public Future<String> post() {
        CompletableFuture<String> completableFuture = new CompletableFuture<>();

        Executors.newCachedThreadPool(new OpenGrokThreadFactory("webhook-")).submit(() -> {
            int status = RestfulClient.postIt(getURI(), getContent());
            completableFuture.complete(String.valueOf(status));
            return null;
        });

        return completableFuture;
    }
}
