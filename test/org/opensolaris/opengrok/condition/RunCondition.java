package org.opensolaris.opengrok.condition;

import org.opensolaris.opengrok.history.GitRepository;
import org.opensolaris.opengrok.history.MercurialRepository;
import org.opensolaris.opengrok.history.Repository;

public interface RunCondition {

    boolean isSatisfied();

    class MercurialInstalled implements RunCondition {
        private final Repository REPO = new MercurialRepository();
        @Override
        public boolean isSatisfied() {
            return REPO.isWorking();
        }
    }

    class GitInstalled implements RunCondition {
        private final Repository REPO = new GitRepository();
        @Override
        public boolean isSatisfied() {
            return REPO.isWorking();
        }
    }

}
