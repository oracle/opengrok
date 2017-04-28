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
 * Copyright (c) 2017, Oracle and/or its affiliates. All rights reserved.
 */
package org.opensolaris.opengrok.authorization.plugins;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import javax.servlet.http.HttpServletRequest;
import org.opensolaris.opengrok.authorization.IAuthorizationPlugin;
import org.opensolaris.opengrok.configuration.Group;
import org.opensolaris.opengrok.configuration.Project;

/**
 *
 * @author Krystof Tulinger
 */
public class GroupPlugin implements IAuthorizationPlugin {

    private List<String> groups = new ArrayList<>();

    @Override
    @SuppressWarnings("unchecked")
    public void load(Map<String, Object> parameters) {
        Object group = parameters.get("groups");
        if (group == null) {
            throw new NullPointerException("No group given in configuration. Use \"groups\" in setup.");
        }

        IllegalArgumentException e = new IllegalArgumentException("Unable to detect the group configuration.");
        Exception last = e;

        try {
            groups.add((String) group);
            return;
        } catch (ClassCastException ex) {
            last.initCause(ex);
            last = ex;
        }

        try {
            groups = (List) group;
            return;
        } catch (ClassCastException ex) {
            last.initCause(ex);
            last = ex;
        }

        try {
            groups = new ArrayList<>(Arrays.asList((String[]) group));
            return;
        } catch (ClassCastException ex) {
            last.initCause(ex);
            last = ex;
        }

        throw e;
    }

    @Override
    public void unload() {
    }

    @Override
    public boolean isAllowed(HttpServletRequest request, Project project) {
        return false;
    }

    @Override
    public boolean isAllowed(HttpServletRequest request, Group group) {
        return this.groups.contains(group.getName());
    }

}
