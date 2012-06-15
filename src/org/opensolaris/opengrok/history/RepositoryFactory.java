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
 * Copyright (c) 2008, 2010, Oracle and/or its affiliates. All rights reserved.
 */

package org.opensolaris.opengrok.history;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;

import org.opensolaris.opengrok.OpenGrokLogger;

/**
 * This is a factory class for the different repositories.
 *
 * @author austvik
 */
public final class RepositoryFactory {

    private static Repository repositories[] = {
        new MercurialRepository(),
        new AccuRevRepository(),
        new BazaarRepository(),
        new GitRepository(),
        new MonotoneRepository(),
        new SubversionRepository(),
        new SCCSRepository(),
        new RazorRepository(),
        new ClearCaseRepository(),
        new PerforceRepository(),
        new RCSRepository(),
        new CVSRepository(),
        new RepoRepository(),
    };

    private RepositoryFactory() {
        // Factory class, should not be constructed
    }

    /**
     * Get a list of all available repository handlers.
     * @return a list which contains none-{@code null} values, only.
     */
    public static List<Class<? extends Repository>> getRepositoryClasses() {
        ArrayList<Class<? extends Repository>> list =
            new ArrayList<Class<? extends Repository>>(repositories.length);
        for (int i=repositories.length-1; i >= 0; i--) {
            list.add(repositories[i].getClass());
        }
        return list;
    }

    /**
     * Returns a repository for the given file, or null if no repository was found.
     *
     * @param file File that might contain a repository
     * @return Correct repository for the given file
     */
    public static Repository getRepository(File file) throws InstantiationException, IllegalAccessException {
        Repository res = null;
        for (Repository rep : repositories) {
            if (rep.isRepositoryFor(file)) {
                res = rep.getClass().newInstance();
                try {
                    res.setDirectoryName(file.getCanonicalPath());
                } catch (IOException e) {
                    OpenGrokLogger.getLogger().log(Level.SEVERE, "Failed to get canonical path name for " + file.getAbsolutePath(), e);
                }

                if (!res.isWorking()) {
                    OpenGrokLogger.getLogger().log(
                            Level.WARNING,
                            "{0} not working (missing binaries?): {1}",
                            new Object[] {
                                res.getClass().getSimpleName(),
                                file.getPath()
                            });
                }

                if (res.getType() == null || res.getType().length() == 0) {
                    res.setType(res.getClass().getSimpleName());
                }
                break;
            }
        }
        return res;
    }

    /**
     * Returns a repository for the given file, or null if no repository was found.
     *
     * @param info Information about the repository
     * @return Correct repository for the given file
     */
    public static Repository getRepository(RepositoryInfo info) throws InstantiationException, IllegalAccessException {
        return getRepository(new File(info.getDirectoryName()));
    }
}
