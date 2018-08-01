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
package org.opengrok.indexer.condition;

import org.opengrok.indexer.history.BazaarRepository;
import org.opengrok.indexer.history.BitKeeperRepository;
import org.opengrok.indexer.history.CVSRepository;
import org.opengrok.indexer.history.GitRepository;
import org.opengrok.indexer.history.MercurialRepository;
import org.opengrok.indexer.history.PerforceRepository;
import org.opengrok.indexer.history.RCSRepository;
import org.opengrok.indexer.history.Repository;
import org.opengrok.indexer.history.SubversionRepository;

/**
 * A template {@link org.opengrok.indexer.condition.RunCondition} that will disable certain tests
 * if the repository is not working - generally means not available through the CLI.
 * 
 * Each run condition can be forced on with the system property <b>junit-force-{name}=true</b> or <b>junit-force-all=true</b>
 */
public abstract class RepositoryInstalled implements RunCondition {

    private final String name;
    private final Repository repository;

    public RepositoryInstalled(String name, Repository repository) {
        this.name = name;
        this.repository = repository;
    }

    @Override
    public boolean isSatisfied() {
        if (Boolean.getBoolean(forceSystemProperty())) {
            return true;
        }
        return repository.isWorking();
    }

    private String forceSystemProperty() {
        return String.format("junit-force-%s", name);
    }

    public static class BitKeeperInstalled extends RepositoryInstalled {
        public BitKeeperInstalled() {
            super("bitkeeper", new BitKeeperRepository());
        }
    }

    public static class MercurialInstalled extends RepositoryInstalled {
        public MercurialInstalled() {
            super("mercurial", new MercurialRepository());
        }
    }

    public static class GitInstalled extends RepositoryInstalled {
        public GitInstalled() {
            super("git", new GitRepository());
        }
    }

    public static class RCSInstalled extends RepositoryInstalled {
        public RCSInstalled() {
            super("rcs", new RCSRepository());
        }
    }
    
    public static class BazaarInstalled extends RepositoryInstalled {
        public BazaarInstalled() {
            super("bazaar", new BazaarRepository());
        }
    }

    public static class CvsInstalled extends RepositoryInstalled {
        public CvsInstalled() {
            super("cvs", new CVSRepository());
        }
    }

    public static class PerforceInstalled extends RepositoryInstalled {
        public PerforceInstalled() {
            super("perforce", new PerforceRepository());
        }
    }

    public static class SubvsersionInstalled extends RepositoryInstalled {
        public SubvsersionInstalled() {
            super("svn", new SubversionRepository());
        }
    }

}
