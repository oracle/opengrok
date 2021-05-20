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
 * Copyright (c) 2016, 2021, Oracle and/or its affiliates. All rights reserved.
 */
package org.opengrok.indexer.condition;

import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;
import org.opengrok.indexer.history.BazaarRepository;
import org.opengrok.indexer.history.BitKeeperRepository;
import org.opengrok.indexer.history.CVSRepository;
import org.opengrok.indexer.history.MercurialRepository;
import org.opengrok.indexer.history.PerforceRepository;
import org.opengrok.indexer.history.RCSRepository;
import org.opengrok.indexer.history.Repository;
import org.opengrok.indexer.history.SCCSRepository;
import org.opengrok.indexer.history.SubversionRepository;

public class RepositoryInstalled {

    private static final String FORCE_ALL_PROPERTY = "junit-force-all";

    public enum Type {
        BITKEEPER(new BitKeeperRepository()),
        MERCURIAL(new MercurialRepository()),
        RCS(new RCSRepository()),
        BAZAAR(new BazaarRepository()),
        CVS(new CVSRepository()),
        PERFORCE(new PerforceRepository()),
        SUBVERSION(new SubversionRepository()),
        SCCS(new SCCSRepository());

        private final Supplier<Boolean> satisfied;

        Type(Repository repository) {
            satisfied = Suppliers.memoize(() -> Boolean.getBoolean(FORCE_ALL_PROPERTY) || repository.isWorking());
        }

        public boolean isSatisfied() {
            return satisfied.get();
        }
    }

    private RepositoryInstalled() {
    }

}
