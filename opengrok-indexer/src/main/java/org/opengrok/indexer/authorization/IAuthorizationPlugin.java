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
package org.opengrok.indexer.authorization;

import java.util.Map;

import jakarta.servlet.http.HttpServletRequest;
import org.opengrok.indexer.configuration.Group;
import org.opengrok.indexer.configuration.Project;

/**
 * Interface for authorization plug-ins.
 *
 * All plug-ins considered for authorization must implement this interface
 *
 * @author Krystof Tulinger
 */
public interface IAuthorizationPlugin {

    /**
     * Called when the plug-in is loaded into memory. With the parameters from
     * the configuration.
     *
     * This can be used for establishing database/LDAP connection or perform
     * initialization.
     *
     * @param parameters parameters specified in the configuration
     */
    void load(Map<String, Object> parameters);

    /**
     * Called when the plug-in is about to be deleted from the memory.
     *
     * This can be used for releasing connections and/or other release stuff.
     */
    void unload();

    /**
     * This method should decide if given request should be allowed to view or
     * display the project.
     *
     * It is up to the implementor if the standard request attributes like
     * session, user principal and others are used or not.
     *
     * @param request servlet request
     * @param project project to test
     * @return true if request is allowed to see this project
     */
    boolean isAllowed(HttpServletRequest request, Project project);

    /**
     * This method should decide if given request should be allowed to view or
     * display the group.
     *
     * It is up to the implementor if the standard request attributes like
     * session, user principal and others are used or not.
     *
     * VERY IMPORTANT NOTE: Allowing particular group does not allow its
     * projects, repositories. You must include those in the isAllowed method
     * for project if you want to display content of the group.
     *
     * @param request servlet request
     * @param group group to test
     * @return true if request is allowed to see this group of projects
     */
    boolean isAllowed(HttpServletRequest request, Group group);
}
