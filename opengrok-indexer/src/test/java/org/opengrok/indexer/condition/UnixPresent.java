package org.opengrok.indexer.condition;

import org.opengrok.indexer.web.Util;

public class UnixPresent implements RunCondition {
    @Override
    public boolean isSatisfied() {
        if (Boolean.getBoolean(forceSystemProperty())) {
            return true;
        }
        return Util.isUnix();
    }

    private String forceSystemProperty() {
        return String.format("junit-force-unix");
    }
}
