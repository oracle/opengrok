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
 * Copyright (c) 2020, Chris Fraire <cfraire@me.com>.
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
 */
package opengrok.auth.plugin;

import opengrok.auth.plugin.entity.User;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.opengrok.indexer.configuration.Group;
import org.opengrok.indexer.configuration.Project;
import org.opengrok.indexer.util.RandomString;
import org.opengrok.indexer.web.DummyHttpServletRequest;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Represents a container for tests of {@link UserWhiteListPlugin}.
 */
@RunWith(Parameterized.class)
public class UserWhiteListPluginTest {

    private static final String OK_USER = "user1321";
    private static final String OK_ID = "id2178";
    private static File tempWhitelistUser;
    private static File tempWhitelistId;
    private static HashMap<String, Object> validPluginParameters;

    private UserWhiteListPlugin plugin;
    private final String param;

    @Parameterized.Parameters
    public static Collection<String> parameters() {
        return Arrays.asList(UserWhiteListPlugin.ID_FIELD, UserWhiteListPlugin.USERNAME_FIELD);
    }

    public UserWhiteListPluginTest(String param) {
        this.param = param;
    }

    @BeforeClass
    public static void beforeClass() throws Exception {
        tempWhitelistUser = File.createTempFile("UserWhiteListPluginTestUser", "txt");
        try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(
                new FileOutputStream(tempWhitelistUser), StandardCharsets.UTF_8))) {
            writer.write(OK_USER);
            // Don't bother with trailing LF.
        }

        tempWhitelistId = File.createTempFile("UserWhiteListPluginTestId", "txt");
        try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(
                new FileOutputStream(tempWhitelistId), StandardCharsets.UTF_8))) {
            writer.write(OK_ID);
            // Don't bother with trailing LF.
        }

        validPluginParameters = new HashMap<>();
    }

    @AfterClass
    public static void afterClass() {
        if (tempWhitelistUser != null) {
            //noinspection ResultOfMethodCallIgnored
            tempWhitelistUser.delete();
        }
        if (tempWhitelistId != null) {
            //noinspection ResultOfMethodCallIgnored
            tempWhitelistId.delete();
        }
    }

    @Before
    public void setUp() {
        plugin = new UserWhiteListPlugin();
        validPluginParameters.put(UserWhiteListPlugin.FIELD_PARAM, this.param);
        if (this.param.equals(UserWhiteListPlugin.USERNAME_FIELD)) {
            validPluginParameters.put(UserWhiteListPlugin.FILE_PARAM, tempWhitelistUser.getPath());
        } else {
            validPluginParameters.put(UserWhiteListPlugin.FILE_PARAM, tempWhitelistId.getPath());
        }
    }

    @Test
    public void shouldThrowOnLoadIfNullArgument() {
        assertThrows(NullPointerException.class, () -> {
            //noinspection ConstantConditions
            plugin.load(null);
            }, "plugin.load(null)");
    }

    @Test
    public void shouldThrowOnLoadIfInvalidFieldName() {
        assertThrows(IllegalArgumentException.class, () -> {
            HashMap<String, Object> map = new HashMap<>();
            map.put(UserWhiteListPlugin.FILE_PARAM, tempWhitelistUser.getPath());
            map.put(UserWhiteListPlugin.FIELD_PARAM, "huh");
            plugin.load(map);
        }, "plugin.load(null)");
    }

    @Test
    public void shouldThrowOnLoadIfUnreadableFileSpecified() {
        HashMap<String, Object> unreadablePluginParameters = new HashMap<>();
        unreadablePluginParameters.put(UserWhiteListPlugin.FILE_PARAM,
                RandomString.generateLower(24));

        IllegalArgumentException caughtException = null;
        try {
            plugin.load(unreadablePluginParameters);
        } catch (IllegalArgumentException ex) {
            caughtException = ex;
        }

        assertNotNull("caught IllegalArgumentException", caughtException);
        assertTrue("caughtException should mention 'Unable to read the file'",
                caughtException.getMessage().contains("Unable to read the file"));
    }

    @Test
    public void shouldThrowOnLoadIfNoFileSpecified() {
        IllegalArgumentException caughtException = null;
        try {
            plugin.load(new HashMap<>());
        } catch (IllegalArgumentException ex) {
            caughtException = ex;
        }

        assertNotNull("caught IllegalArgumentException", caughtException);
        assertTrue("caughtException should mention 'Missing parameter'",
                caughtException.getMessage().contains("Missing parameter"));
    }

    @Test
    public void shouldUnload() {
        plugin.unload();
    }

    @Test
    public void shouldAllowWhitelistedUserForAnyProject() {
        plugin.load(validPluginParameters);

        DummyHttpServletRequest req = new DummyHttpServletRequest();
        User user;
        if (this.param.equals(UserWhiteListPlugin.USERNAME_FIELD)) {
            user = new User(OK_USER);
        } else {
            user = new User("blurb", OK_ID);
        }
        req.setAttribute(UserPlugin.REQUEST_ATTR, user);

        Project randomProject = new Project(RandomString.generateUpper(10));
        boolean projectAllowed = plugin.isAllowed(req, randomProject);
        assertTrue("should allow OK entity for random project 1", projectAllowed);

        randomProject = new Project(RandomString.generateUpper(10));
        projectAllowed = plugin.isAllowed(req, randomProject);
        assertTrue("should allow OK entity for random project 2", projectAllowed);
    }

    @Test
    public void shouldNotAllowRandomUserForAnyProject() {
        plugin.load(validPluginParameters);

        DummyHttpServletRequest req = new DummyHttpServletRequest();
        req.setAttribute(UserPlugin.REQUEST_ATTR, new User(RandomString.generateUpper(8)));

        Project randomProject = new Project(RandomString.generateUpper(10));
        boolean projectAllowed = plugin.isAllowed(req, randomProject);
        assertFalse("should not allow random user for random project 1", projectAllowed);

        randomProject = new Project(RandomString.generateUpper(10));
        projectAllowed = plugin.isAllowed(req, randomProject);
        assertFalse("should not allow random user for random project 2", projectAllowed);
    }

    @Test
    public void shouldAllowWhitelistedUserForAnyGroup() {
        plugin.load(validPluginParameters);

        DummyHttpServletRequest req = new DummyHttpServletRequest();
        User user;
        if (this.param.equals(UserWhiteListPlugin.USERNAME_FIELD)) {
            user = new User(OK_USER);
        } else {
            user = new User("blurb", OK_ID);
        }
        req.setAttribute(UserPlugin.REQUEST_ATTR, user);

        Group randomGroup = new Group(RandomString.generateUpper(10));
        boolean groupAllowed = plugin.isAllowed(req, randomGroup);
        assertTrue("should allow OK entity for random group 1", groupAllowed);

        randomGroup = new Group(RandomString.generateUpper(10));
        groupAllowed = plugin.isAllowed(req, randomGroup);
        assertTrue("should allow OK entity for random group 2", groupAllowed);
    }

    @Test
    public void shouldNotAllowRandomUserForAnyGroup() {
        plugin.load(validPluginParameters);

        DummyHttpServletRequest req = new DummyHttpServletRequest();
        req.setAttribute(UserPlugin.REQUEST_ATTR, new User(RandomString.generateUpper(8)));

        Group randomGroup = new Group(RandomString.generateUpper(10));
        boolean projectAllowed = plugin.isAllowed(req, randomGroup);
        assertFalse("should not allow random group 1", projectAllowed);

        randomGroup = new Group(RandomString.generateUpper(10));
        projectAllowed = plugin.isAllowed(req, randomGroup);
        assertFalse("should not allow random group 2", projectAllowed);
    }
}
