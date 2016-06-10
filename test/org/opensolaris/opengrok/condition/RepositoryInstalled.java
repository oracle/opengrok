package org.opensolaris.opengrok.condition;

import org.opensolaris.opengrok.history.BazaarRepository;
import org.opensolaris.opengrok.history.CVSRepository;
import org.opensolaris.opengrok.history.GitRepository;
import org.opensolaris.opengrok.history.MercurialRepository;
import org.opensolaris.opengrok.history.PerforceRepository;
import org.opensolaris.opengrok.history.Repository;
import org.opensolaris.opengrok.history.SubversionRepository;

/**
 * A template {@link org.opensolaris.opengrok.condition.RunCondition} that will disable certain tests
 * if the repository is not working - generally means not available through the CLI.
 * 
 * Each run condition can be forced on with the system property <b>junit-force-{name}=true</b> or <b>junit-force-all=true</b>
 */
public abstract class RepositoryInstalled implements RunCondition {

    static final String FORCE_ALL_PROPERTY = "junit-force-all";

    private final String name;
    private final Repository repository;

    public RepositoryInstalled(String name, Repository repository) {
        this.name = name;
        this.repository = repository;
    }

    @Override
    public boolean isSatisfied() {
        if (Boolean.getBoolean(FORCE_ALL_PROPERTY)) {
            return true;
        }
        if (Boolean.getBoolean(forceSystemProperty())) {
            return true;
        }
        return repository.isWorking();
    }

    private String forceSystemProperty() {
        return String.format("junit-force-%s", name);
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
