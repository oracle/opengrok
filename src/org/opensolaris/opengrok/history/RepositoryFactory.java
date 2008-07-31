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

/**
 * This is a factory class for the different repositories.
 * 
 * @author austvik
 */
public class RepositoryFactory {

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
        new CVSRepository(),
    };
    
    /**
     * Returns a repository for the given file, or null if no repository was found.
     * 
     * @param file File that might contain a repository
     * @return Correct repository for the given file
     */
    static Repository getRepository(File file) throws InstantiationException, IllegalAccessException {
        Repository res = null;
        for (Repository rep : repositories) {
            if (rep.isRepositoryFor(file)) {
                res = rep.getClass().newInstance();
                break;
            }
        }
        return res;
    }
    
}
