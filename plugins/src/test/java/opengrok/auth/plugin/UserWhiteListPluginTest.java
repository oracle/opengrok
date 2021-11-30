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
 * Copyright (c) 2020, 2021, Oracle and/or its affiliates. All rights reserved.
 */
package opengrok.auth.plugin;

import opengrok.auth.plugin.entity.User;
import org.apache.commons.lang3.RandomStringUtils;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.opengrok.indexer.configuration.Group;
import org.opengrok.indexer.configuration.Project;
import org.opengrok.indexer.web.DummyHttpServletRequest;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Represents a container for tests of {@link UserWhiteListPlugin}.
 */
public class UserWhiteListPluginTest {

    private static final String OK_USER = "user1321";
    private static final String OK_ID = "id2178";
    private static File tempWhitelistUser;
    private static File tempWhitelistId;
    private static HashMap<String, Object> validPluginParameters;

    private UserWhiteListPlugin plugin;

    public static Collection<String> parameters() {
        return Arrays.asList(UserWhiteListPlugin.ID_FIELD, UserWhiteListPlugin.USERNAME_FIELD);
    }

    @BeforeAll
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

    @AfterAll
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

    public void init(String param) {
        plugin = new UserWhiteListPlugin();
        validPluginParameters.put(UserWhiteListPlugin.FIELD_PARAM, param);
        if (param.equals(UserWhiteListPlugin.USERNAME_FIELD)) {
            validPluginParameters.put(UserWhiteListPlugin.FILE_PARAM, tempWhitelistUser.getPath());
        } else {
            validPluginParameters.put(UserWhiteListPlugin.FILE_PARAM, tempWhitelistId.getPath());
        }
    }

    @ParameterizedTest
    @MethodSource("parameters")
    public void shouldThrowOnLoadIfNullArgument(String param) {
        init(param);
        assertThrows(IllegalArgumentException.class, () -> {
            //noinspection ConstantConditions
            plugin.load(null);
            }, "plugin.load(null)");
    }

    @ParameterizedTest
    @MethodSource("parameters")
    public void shouldThrowOnLoadIfInvalidFieldName(String param) {
        init(param);
        assertThrows(IllegalArgumentException.class, () -> {
            Map<String, Object> map = new HashMap<>();
            map.put(UserWhiteListPlugin.FILE_PARAM, tempWhitelistUser.getPath());
            map.put(UserWhiteListPlugin.FIELD_PARAM, "huh");
            plugin.load(map);
        }, "plugin.load(null)");
    }

    @ParameterizedTest
    @MethodSource("parameters")
    public void shouldThrowOnLoadIfUnreadableFileSpecified(String param) {
        init(param);
        HashMap<String, Object> unreadablePluginParameters = new HashMap<>();
        unreadablePluginParameters.put(UserWhiteListPlugin.FILE_PARAM,
                RandomStringUtils.randomAlphanumeric(24));

        IllegalArgumentException caughtException = null;
        try {
            plugin.load(unreadablePluginParameters);
        } catch (IllegalArgumentException ex) {
            caughtException = ex;
        }

        assertNotNull(caughtException, "caught IllegalArgumentException");
        assertTrue(caughtException.getMessage().contains("Unable to read the file"),
                "caughtException should mention 'Unable to read the file'");
    }

    @ParameterizedTest
    @MethodSource("parameters")
    public void shouldThrowOnLoadIfNoFileSpecified(String param) {
        init(param);
        IllegalArgumentException caughtException = null;
        try {
            plugin.load(new HashMap<>());
        } catch (IllegalArgumentException ex) {
            caughtException = ex;
        }

        assertNotNull(caughtException, "caught IllegalArgumentException");
        assertTrue(caughtException.getMessage().contains("Missing parameter"),
                "caughtException should mention 'Missing parameter'");
    }

    @ParameterizedTest
    @MethodSource("parameters")
    public void shouldStripWhitespaceFromWhitelists(String param) throws IOException {
        plugin = new UserWhiteListPlugin();
        HashMap<String, Object> pluginParameters = new HashMap<>();
        pluginParameters.put(UserWhiteListPlugin.FIELD_PARAM, param);
        Set<String> entries = Set.of("Moomin", " Fillyjonk", "  Snuffkin", "Snork Maiden  ", "Groke ");

        File tmpFile = File.createTempFile("UserWhiteListPluginTestId", "txt");
        try (PrintWriter writer = new PrintWriter(new OutputStreamWriter(
                new FileOutputStream(tmpFile), StandardCharsets.UTF_8))) {
            for (String entity : entries) {
                writer.println(entity);
            }
        }

        // Make sure there as some entries with trailing spaces in the file.
        Stream<String> stream = Files.lines(tmpFile.toPath());
        assertTrue(stream.filter(s -> s.startsWith(" ") || s.endsWith(" ")).
                collect(Collectors.toSet()).size() > 0);

        pluginParameters.put(UserWhiteListPlugin.FILE_PARAM, tmpFile.toString());
        plugin.load(pluginParameters);
        tmpFile.delete();

        Set<String> expected = entries.stream().map(String::trim).collect(Collectors.toSet());
        assertEquals(expected, plugin.getWhitelist());
    }

    @ParameterizedTest
    @MethodSource("parameters")
    public void shouldUnload(String param) {
        init(param);
        plugin.unload();
    }

    @ParameterizedTest
    @MethodSource("parameters")
    public void shouldAllowWhitelistedUserForAnyProject(String param) {
        init(param);
        plugin.load(validPluginParameters);

        DummyHttpServletRequest req = new DummyHttpServletRequest();
        User user;
        if (param.equals(UserWhiteListPlugin.USERNAME_FIELD)) {
            user = new User(OK_USER);
        } else {
            user = new User("blurb", OK_ID);
        }
        req.setAttribute(UserPlugin.REQUEST_ATTR, user);

        Project randomProject = new Project(RandomStringUtils.randomAlphanumeric(10));
        boolean projectAllowed = plugin.isAllowed(req, randomProject);
        assertTrue(projectAllowed, "should allow OK entity for random project 1");

        randomProject = new Project(RandomStringUtils.randomAlphanumeric(10));
        projectAllowed = plugin.isAllowed(req, randomProject);
        assertTrue(projectAllowed, "should allow OK entity for random project 2");
    }

    @ParameterizedTest
    @MethodSource("parameters")
    public void shouldNotAllowRandomUserForAnyProject(String param) {
        init(param);
        plugin.load(validPluginParameters);

        DummyHttpServletRequest req = new DummyHttpServletRequest();
        req.setAttribute(UserPlugin.REQUEST_ATTR, new User(RandomStringUtils.randomAlphanumeric(8)));

        Project randomProject = new Project(RandomStringUtils.randomAlphanumeric(10));
        boolean projectAllowed = plugin.isAllowed(req, randomProject);
        assertFalse(projectAllowed, "should not allow random user for random project 1");

        randomProject = new Project(RandomStringUtils.randomAlphanumeric(10));
        projectAllowed = plugin.isAllowed(req, randomProject);
        assertFalse(projectAllowed, "should not allow random user for random project 2");
    }

    @ParameterizedTest
    @MethodSource("parameters")
    public void shouldAllowWhitelistedUserForAnyGroup(String param) {
        init(param);
        plugin.load(validPluginParameters);

        DummyHttpServletRequest req = new DummyHttpServletRequest();
        User user;
        if (param.equals(UserWhiteListPlugin.USERNAME_FIELD)) {
            user = new User(OK_USER);
        } else {
            user = new User("blurb", OK_ID);
        }
        req.setAttribute(UserPlugin.REQUEST_ATTR, user);

        Group randomGroup = new Group(RandomStringUtils.randomAlphanumeric(10));
        boolean groupAllowed = plugin.isAllowed(req, randomGroup);
        assertTrue(groupAllowed, "should allow OK entity for random group 1");

        randomGroup = new Group(RandomStringUtils.randomAlphanumeric(10));
        groupAllowed = plugin.isAllowed(req, randomGroup);
        assertTrue(groupAllowed, "should allow OK entity for random group 2");
    }

    @ParameterizedTest
    @MethodSource("parameters")
    public void shouldNotAllowRandomUserForAnyGroup(String param) {
        init(param);
        plugin.load(validPluginParameters);

        DummyHttpServletRequest req = new DummyHttpServletRequest();
        req.setAttribute(UserPlugin.REQUEST_ATTR, new User(RandomStringUtils.randomAlphanumeric(8)));

        Group randomGroup = new Group(RandomStringUtils.randomAlphanumeric(10));
        boolean projectAllowed = plugin.isAllowed(req, randomGroup);
        assertFalse(projectAllowed, "should not allow random group 1");

        randomGroup = new Group(RandomStringUtils.randomAlphanumeric(10));
        projectAllowed = plugin.isAllowed(req, randomGroup);
        assertFalse(projectAllowed, "should not allow random group 2");
    }
}
