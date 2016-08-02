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
 * Copyright (c) 2016, Oracle and/or its affiliates. All rights reserved.
 */
import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import javax.servlet.http.HttpServletRequest;
import org.opensolaris.opengrok.authorization.IAuthorizationPlugin;
import org.opensolaris.opengrok.configuration.Group;
import org.opensolaris.opengrok.configuration.Project;


/**
 * This class is a full example of a working plugin from HTTP Basic tutorial on
 * https://github.com/OpenGrok/OpenGrok/wiki/OpenGrok-Authorization#configuration
 *
 * @author Krystof Tulinger
 */
public class HttpBasicAuthorizationPlugin implements IAuthorizationPlugin {

    private static final Map<String, Set<String>> userProjects = new TreeMap<>();
    private static final Map<String, Set<String>> userGroups = new TreeMap<>();

    static {
        // all have access to "test-project-11" and some to other "test-project-5" or "test-project-8"
        userProjects.put("007", new TreeSet<>(Arrays.asList(new String[]{"test-project-11", "test-project-5"})));
        userProjects.put("008", new TreeSet<>(Arrays.asList(new String[]{"test-project-11", "test-project-8"})));
        userProjects.put("009", new TreeSet<>(Arrays.asList(new String[]{"test-project-11"})));
        userProjects.put("00A", new TreeSet<>(Arrays.asList(new String[]{"test-project-11"})));
        userProjects.put("00B", new TreeSet<>(Arrays.asList(new String[]{"test-project-11"})));
    }

    static {
        userGroups.put("007", new TreeSet<>(Arrays.asList(new String[]{})));
        userGroups.put("008", new TreeSet<>(Arrays.asList(new String[]{})));
        userGroups.put("009", new TreeSet<>(Arrays.asList(new String[]{})));
        userGroups.put("00A", new TreeSet<>(Arrays.asList(new String[]{})));
        userGroups.put("00B", new TreeSet<>(Arrays.asList(new String[]{})));
    }

    @Override
    public void load() {
    }

    @Override
    public void unload() {
    }

    private void init(HttpServletRequest request) {
        Set<String> projects = new TreeSet<>();
        Set<String> groups = new TreeSet<>();
        Group g;

        Set<String> descendants = new TreeSet<>();
        for (String group : Arrays.asList(new String[]{"admins", "users", "plugins", "ghost"})) {
            if (!request.isUserInRole(group)) {
                continue;
            }

            discoverGroup(group, request, descendants);
        }

        userGroups.get(request.getUserPrincipal().getName()).addAll(descendants);
    }

    private void discoverGroup(String group, HttpServletRequest request, Set<String> descendants) {
        Group g;
        if ((g = Group.getByName(group)) != null) {
            // group discovery
            for (Project p : g.getRepositories()) {
                userProjects.get(request.getUserPrincipal().getName()).add(p.getDescription());
            }
            for (Project p : g.getProjects()) {
                userProjects.get(request.getUserPrincipal().getName()).add(p.getDescription());
            }
            for (Group grp : g.getDescendants()) {
                for (Project p : grp.getRepositories()) {
                    userProjects.get(request.getUserPrincipal().getName()).add(p.getDescription());
                }
                for (Project p : grp.getProjects()) {
                    userProjects.get(request.getUserPrincipal().getName()).add(p.getDescription());
                }
                descendants.add(grp.getName());
            }
            while (g != null) {
                descendants.add(g.getName());
                g = g.getParent();
            }

        }
    }

    @Override
    public boolean isAllowed(HttpServletRequest request, Project project) {
        if (request.getUserPrincipal() == null) {
            return false;
        }

        init(request);

        return userProjects.get(request.getUserPrincipal().getName()).contains(project.getDescription());
    }

    @Override
    public boolean isAllowed(HttpServletRequest request, Group group) {
        if (request.getUserPrincipal() == null) {
            return false;
        }

        init(request);

        return userGroups.get(request.getUserPrincipal().getName()).contains(group.getName());
    }

}
