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
 * Copyright 2008 Sun Microsystems, Inc.  All rights reserved.
 * Use is subject to license terms.
 * 
 * @author Jorgen Austvik
 */
package org.opensolaris.opengrok.history;

import java.io.File;
import java.io.IOException;
import java.util.logging.Level;
import org.opensolaris.opengrok.OpenGrokLogger;
import org.opensolaris.opengrok.configuration.RuntimeEnvironment;

/**
 * This is a factory class for the different repositories.
 * 
 * @author austvik
 */
public final class RepositoryFactory {

    private static Repository repositories[] = {
        new MercurialRepository(),
        new BazaarRepository(),
        new GitRepository(),
        new SubversionRepository(),
        new SCCSRepository(),
        new RazorRepository(),
        new ClearCaseRepository(),
        new PerforceRepository(),
        new RCSRepository(),
        new CVSRepository(),};

    private RepositoryFactory() {
        // Factory class, should not be constructed
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
                if (!rep.isWorking()) {
                    OpenGrokLogger.getLogger().log(Level.WARNING, res.getClass().getSimpleName() + " not working (missing binaries?): " + file.getPath());
                }
                try {
                    res.setDirectoryName(file.getCanonicalPath());
                } catch (IOException e) {
                    OpenGrokLogger.getLogger().log(Level.SEVERE, "Failed to get canonical path name for " + file.getAbsolutePath(), e);
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
     * @param file File that might contain a repository
     * @return Correct repository for the given file
     */
    public static Repository getRepository(RepositoryInfo info) throws InstantiationException, IllegalAccessException {
        return getRepository(new File(info.getDirectoryName()));
    }
}
