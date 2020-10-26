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
 * Portions Copyright (c) 2020, Chris Fraire <cfraire@me.com>.
 */
package opengrok.auth.plugin;

import opengrok.auth.plugin.entity.User;
import org.opengrok.indexer.authorization.IAuthorizationPlugin;
import org.opengrok.indexer.configuration.Group;
import org.opengrok.indexer.configuration.Project;

import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Stream;

public class UserWhiteListPlugin implements IAuthorizationPlugin {
    private static final String className = UserWhiteListPlugin.class.getName();
    private static final Logger LOGGER = Logger.getLogger(className);

    static final String FILE_PARAM = "file";

    private final Set<String> whitelist = new TreeSet<>();

    @Override
    public void load(Map<String, Object> parameters) {
        String filePath;

        if ((filePath = (String) parameters.get(FILE_PARAM)) == null) {
            throw new IllegalArgumentException("Missing parameter [" + FILE_PARAM + "] in the configuration");
        }

        // Load whitelist from file to memory.
        try (Stream<String> stream = Files.lines(Paths.get(filePath))) {
            stream.forEach(whitelist::add);
        } catch (IOException e) {
            throw new IllegalArgumentException(String.format("Unable to read the file \"%s\"", filePath), e);
        }
    }

    @Override
    public void unload() {
        whitelist.clear();
    }

    private boolean checkWhitelist(HttpServletRequest request) {
        User user;
        String attrName = UserPlugin.REQUEST_ATTR;
        if ((user = (User) request.getAttribute(attrName)) == null) {
            LOGGER.log(Level.WARNING, "cannot get {0} attribute", attrName);
            return false;
        }

        return whitelist.contains(user.getUsername());
    }

    @Override
    public boolean isAllowed(HttpServletRequest request, Project project) {
        return checkWhitelist(request);
    }

    @Override
    public boolean isAllowed(HttpServletRequest request, Group group) {
        return checkWhitelist(request);
    }
}
