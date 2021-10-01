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
 * Portions Copyright (c) 2020, Chris Fraire <cfraire@me.com>.
 */
package org.opengrok.web.api.v1.controller;

import jakarta.ws.rs.core.Application;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.opengrok.indexer.configuration.RuntimeEnvironment;
import org.opengrok.indexer.util.TestRepository;
import org.opengrok.web.api.v1.RestApp;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.opengrok.web.api.v1.filter.CorsFilter.ALLOW_CORS_HEADER;
import static org.opengrok.web.api.v1.filter.CorsFilter.CORS_REQUEST_HEADER;

public class SearchControllerTest extends OGKJerseyTest {

    private static final RuntimeEnvironment env = RuntimeEnvironment.getInstance();

    private static TestRepository repository;

    @Override
    protected Application configure() {
        return new RestApp();
    }

    @BeforeAll
    public static void setUpClass() throws Exception {
        System.setProperty("sun.net.http.allowRestrictedHeaders", "true"); // necessary to test CORS from controllers
        repository = new TestRepository();

        repository.create(SearchControllerTest.class.getClassLoader().getResource("sources"));

        env.setHistoryEnabled(false);
        env.setProjectsEnabled(true);
        env.setDefaultProjectsFromNames(Collections.singleton("__all__"));

        env.getSuggesterConfig().setRebuildCronConfig(null);
    }

    @AfterAll
    public static void tearDownClass() {
        repository.destroy();
    }

    @Test
    void testSearchCors() {
        Response response = target(SearchController.PATH)
                .request()
                .header(CORS_REQUEST_HEADER, "http://example.com")
                .get();
        assertEquals("*", response.getHeaderString(ALLOW_CORS_HEADER));
    }
}
