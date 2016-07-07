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
import javax.servlet.http.HttpServletRequest;
import org.opensolaris.opengrok.authorization.IAuthorizationPlugin;
import org.opensolaris.opengrok.configuration.Group;
import org.opensolaris.opengrok.configuration.Project;
/**
 * Sample authorization plugin.
 * 
 * Always just bypass all authorization requests.
 */
public class SampleAuthorizationPlugin implements IAuthorizationPlugin {

    @Override
    public void load() {
    }

    @Override
    public void unload() {
    }

    
    @Override
    public boolean isAllowed(HttpServletRequest request, Project project) {
        return true;
    }

    @Override
    public boolean isAllowed(HttpServletRequest request, Group group) {
        return true;
    }
}

