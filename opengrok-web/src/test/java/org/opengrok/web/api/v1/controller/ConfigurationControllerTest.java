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
 * Copyright (c) 2018, 2023, Oracle and/or its affiliates. All rights reserved.
 * Portions Copyright (c) 2020, Chris Fraire <cfraire@me.com>.
 */
package org.opengrok.web.api.v1.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;

import java.util.concurrent.CountDownLatch;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.client.Entity;
import jakarta.ws.rs.core.Response;
import org.glassfish.jersey.internal.inject.AbstractBinder;
import org.glassfish.jersey.server.ResourceConfig;
import org.glassfish.jersey.servlet.ServletContainer;
import org.glassfish.jersey.test.DeploymentContext;
import org.glassfish.jersey.test.ServletDeploymentContext;
import org.glassfish.jersey.test.grizzly.GrizzlyWebTestContainerFactory;
import org.glassfish.jersey.test.spi.TestContainerException;
import org.glassfish.jersey.test.spi.TestContainerFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.opengrok.indexer.configuration.Configuration;
import org.opengrok.indexer.configuration.RuntimeEnvironment;
import org.opengrok.indexer.web.ApiUtils;
import org.opengrok.indexer.web.DummyHttpServletRequest;
import org.opengrok.web.PageConfig;
import org.opengrok.web.api.ApiTaskManager;
import org.opengrok.web.api.v1.suggester.provider.service.SuggesterService;

@ExtendWith(MockitoExtension.class)
class ConfigurationControllerTest extends OGKJerseyTest {

    private final RuntimeEnvironment env = RuntimeEnvironment.getInstance();

    @Mock
    private SuggesterService suggesterService;

    @BeforeAll
    static void setup() {
        ApiTaskManager.getInstance().addPool("configuration", 1);
    }

    @Override
    protected TestContainerFactory getTestContainerFactory() throws TestContainerException {
        return new GrizzlyWebTestContainerFactory();
    }

    @Override
    protected DeploymentContext configureDeployment() {
        return ServletDeploymentContext.
                forServlet(new ServletContainer(
                        new ResourceConfig(ConfigurationController.class, StatusController.class)
                        .register(new AbstractBinder() {
                            @Override
                            protected void configure() {
                                bind(suggesterService).to(SuggesterService.class);
                            }
                        }))).build();
    }

    @Test
    void testApplySetAndGetBasicConfig() throws Exception {
        Configuration config = new Configuration();
        String srcRoot = "/foo";
        config.setSourceRoot(srcRoot);

        String configStr = config.getXMLRepresentationAsString();

        Response response = target("configuration")
                .request()
                .put(Entity.xml(configStr));
        Response finalResponse = ApiUtils.waitForAsyncApi(response);
        assertEquals(Response.Status.CREATED.getStatusCode(), finalResponse.getStatus());

        assertEquals(srcRoot, env.getSourceRootPath());

        String returnedConfig = target("configuration")
                .request()
                .get(String.class);

        assertEquals(configStr, returnedConfig);
    }

    @Test
    void testApplySetInvalidMethod() throws Exception {
        Response r = setValue("noMethodExists", "1000");

        assertEquals(Response.Status.BAD_REQUEST.getStatusCode(), r.getStatus());
    }

    private Response setValue(final String field, final String value) throws InterruptedException {
        Response response = target("configuration")
                .path(field)
                .request()
                .put(Entity.text(value));

        return ApiUtils.waitForAsyncApi(response);
    }

    @Test
    void testApplyGetInvalidMethod() {
        Response r = target("configuration")
                .path("FooBar")
                .request()
                .get();

        assertEquals(Response.Status.BAD_REQUEST.getStatusCode(), r.getStatus());
    }

    @Test
    void testApplySetInvalidMethodParameter() throws Exception {
        Response r = setValue("setDefaultProjects", "1000"); // expecting Set

        assertEquals(Response.Status.BAD_REQUEST.getStatusCode(), r.getStatus());
    }

    @Test
    void testApplySetOptionInteger() throws Exception {
        assertEquals(25, env.getHitsPerPage());

        setValue("hitsPerPage", "1000");

        assertEquals(1000, env.getHitsPerPage());

        env.setHitsPerPage(25);
    }

    @Test
    void testApplySetOptionInvalidInteger() throws Exception {
        Response r = setValue("hitsPerPage", "abcd");

        assertEquals(Response.Status.BAD_REQUEST.getStatusCode(), r.getStatus());
    }

    @Test
    void testApplySetOptionBooleanTrue() throws Exception {
        testSetChattyStatusPageTrue("true");
        testSetChattyStatusPageTrue("on");
        testSetChattyStatusPageTrue("1");
    }

    private void testSetChattyStatusPageTrue(final String value) throws Exception {
        env.setChattyStatusPage(false);

        setValue("chattyStatusPage", value);

        assertTrue(env.isChattyStatusPage());
    }

    @Test
    void testApplySetOptionBooleanFalse() throws Exception {
        testSetChattyStatusPageFalse("false");
        testSetChattyStatusPageFalse("off");
        testSetChattyStatusPageFalse("0");
    }

    private void testSetChattyStatusPageFalse(final String value) throws Exception {
        env.setChattyStatusPage(true);

        setValue("chattyStatusPage", value);

        assertFalse(env.isChattyStatusPage());
    }

    @Test
    void testApplySetOptionInvalidBoolean1() throws Exception {
        Response r = setValue("chattyStatusPage", "1000"); // only 1 is accepted as true

        assertEquals(Response.Status.BAD_REQUEST.getStatusCode(), r.getStatus());
    }

    @Test
    void testApplySetOptionInvalidBoolean2() throws Exception {
        Response r = setValue("chattyStatusPage", "anything");

        assertEquals(Response.Status.BAD_REQUEST.getStatusCode(), r.getStatus());
    }

    @Test
    void testApplySetOptionString() throws Exception {
        String old = env.getUserPage();

        setValue("userPage", "http://users.portal.com?user=");

        assertEquals("http://users.portal.com?user=", env.getUserPage());

        setValue("userPage", "some complicated \"string\" with &#~Đ`[đ\\ characters");

        assertEquals("some complicated \"string\" with &#~Đ`[đ\\ characters", env.getUserPage());

        env.setUserPage(old);
    }

    @Test
    void testApplyGetOptionString() {
        env.setSourceRoot("/foo/bar");
        String response = target("configuration")
                .path("sourceRoot")
                .request()
                .get(String.class);

        assertEquals(response, env.getSourceRootPath());
    }

    @Test
    void testApplyGetOptionInteger() {
        int hitsPerPage = target("configuration")
                .path("hitsPerPage")
                .request()
                .get(int.class);

        assertEquals(env.getHitsPerPage(), hitsPerPage);
    }

    @Test
    void testApplyGetOptionBoolean() {
        boolean response = target("configuration")
                .path("historyCache")
                .request()
                .get(boolean.class);

        assertEquals(env.isHistoryCache(), response);
    }

    @Test
    void testSuggesterServiceNotifiedOnConfigurationFieldChange() throws Exception {
        reset(suggesterService);
        setValue("sourceRoot", "test");
        verify(suggesterService).refresh();
    }

    @Test
    void testSuggesterServiceNotifiedOnConfigurationChange() throws InterruptedException {
        reset(suggesterService);
        Response response = target("configuration")
                .request()
                .put(Entity.xml(new Configuration().getXMLRepresentationAsString()));
        ApiUtils.waitForAsyncApi(response);
        verify(suggesterService).refresh();
    }

    @Test
    void testConfigValueSetVsThread() throws InterruptedException {
        int origValue = env.getHitsPerPage();
        final int[] threadValue = new int[1];
        final CountDownLatch startLatch = new CountDownLatch(1);
        final CountDownLatch endLatch = new CountDownLatch(1);

        Thread thread = new Thread(() -> {
            HttpServletRequest req = new DummyHttpServletRequest();
            PageConfig pageConfig = PageConfig.get(req);
            RuntimeEnvironment e = pageConfig.getEnv();
            startLatch.countDown();
            // Wait for hint of termination, save the value and exit.
            try {
                endLatch.await();
            } catch (InterruptedException ex) {
                ex.printStackTrace();
            }
            threadValue[0] = e.getHitsPerPage();
        });

        thread.start();
        startLatch.await();

        // Set brand-new configuration.
        int newValue = origValue + 42;
        Configuration config = new Configuration();
        config.setHitsPerPage(newValue);
        String configStr = config.getXMLRepresentationAsString();
        Response response = target("configuration")
                .request()
                .put(Entity.xml(configStr));
        ApiUtils.waitForAsyncApi(response);

        // Unblock the thread.
        endLatch.countDown();
        thread.join();

        // Check thread's view of the variable.
        assertEquals(newValue, threadValue[0]);

        // Revert the value back to the default.
        env.setHitsPerPage(origValue);
    }
}
