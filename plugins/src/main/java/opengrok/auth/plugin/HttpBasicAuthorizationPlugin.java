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
 * Copyright (c) 2016, 2018, Oracle and/or its affiliates. All rights reserved.
 */

package opengrok.auth.plugin;

import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Collectors;
import javax.servlet.http.HttpServletRequest;
import org.opengrok.indexer.authorization.IAuthorizationPlugin;
import org.opengrok.indexer.configuration.Group;
import org.opengrok.indexer.configuration.Project;

/**
 * This class is a full example of a working plugin from HTTP Basic tutorial on
 * https://github.com/OpenGrok/OpenGrok/wiki/OpenGrok-Authorization#configuration
 *
 * @author Krystof Tulinger
 */
public class HttpBasicAuthorizationPlugin implements IAuthorizationPlugin {

    private static final Map<String, Set<String>> USER_PROJECTS = new TreeMap<>();
    private static final Map<String, Set<String>> USER_GROUPS = new TreeMap<>();

    static {
        // all have access to "test-project-11" and some to other "test-project-5" or "test-project-8"
        USER_PROJECTS.put("007", new TreeSet<>(Arrays.asList(new String[]{"test-project-11", "test-project-5"})));
        USER_PROJECTS.put("008", new TreeSet<>(Arrays.asList(new String[]{"test-project-11", "test-project-8"})));
        USER_PROJECTS.put("009", new TreeSet<>(Arrays.asList(new String[]{"test-project-11"})));
        USER_PROJECTS.put("00A", new TreeSet<>(Arrays.asList(new String[]{"test-project-11"})));
        USER_PROJECTS.put("00B", new TreeSet<>(Arrays.asList(new String[]{"test-project-11"})));
    }

    static {
        USER_GROUPS.put("007", new TreeSet<>(Arrays.asList(new String[]{})));
        USER_GROUPS.put("008", new TreeSet<>(Arrays.asList(new String[]{})));
        USER_GROUPS.put("009", new TreeSet<>(Arrays.asList(new String[]{})));
        USER_GROUPS.put("00A", new TreeSet<>(Arrays.asList(new String[]{})));
        USER_GROUPS.put("00B", new TreeSet<>(Arrays.asList(new String[]{})));
    }

    @Override
    public void load(Map<String, Object> parameters) {
    }

    @Override
    public void unload() {
    }

    private void init(HttpServletRequest request) {
        Set<String> projects = new TreeSet<>();
        Set<String> groups = new TreeSet<>();
        Group g;

        for (String group : Arrays.asList(new String[]{"admins", "users", "plugins", "ghost"})) {
            if (!request.isUserInRole(group)) {
                continue;
            }

            discoverGroup(group, request);
        }
    }

    /**
     * Add this group, all parent groups, all subgroups, all projects in this
     * group, all repositories in this group, all projects in the subgroups and
     * all repositories in the subgroups among the allowed entities for the
     * authorization.
     *
     * <p>
     * The purpose of this is when user allows a particular group then the
     * expectation is to allow all included groups/projects/repositories.
     * </p>
     *
     * @param group string name of the group to be discovered
     * @param request the requests containing the user information
     */
    private void discoverGroup(String group, HttpServletRequest request) {
        Group g;
        if ((g = Group.getByName(group)) != null) {
            USER_GROUPS.get(request.getUserPrincipal().getName()).addAll(g.getRelatedGroups().stream().map((t) -> {
                return t.getName();
            }).collect(Collectors.toSet()));

            USER_PROJECTS.get(request.getUserPrincipal().getName()).addAll(g.getAllProjects().stream().map((t) -> {
                return t.getName();
            }).collect(Collectors.toSet()));
        }
    }

    @Override
    public boolean isAllowed(HttpServletRequest request, Project project) {
        if (request.getUserPrincipal() == null) {
            return false;
        }

        init(request);

        return USER_PROJECTS.get(request.getUserPrincipal().getName()).contains(project.getName());
    }

    @Override
    public boolean isAllowed(HttpServletRequest request, Group group) {
        if (request.getUserPrincipal() == null) {
            return false;
        }

        init(request);

        return USER_GROUPS.get(request.getUserPrincipal().getName()).contains(group.getName());
    }

}
