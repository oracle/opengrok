package org.opengrok.indexer.configuration;

import java.io.File;

import static org.opengrok.indexer.util.IOUtils.getFileContent;

public class IncludeFiles {
    RuntimeEnvironment env = RuntimeEnvironment.getInstance();

    /**
     * Reload the content of all include files.
     * @param configuration configuration
     */
    public void reloadIncludeFiles(Configuration configuration) {
        getBodyIncludeFileContent(true);
        getHeaderIncludeFileContent(true);
        getFooterIncludeFileContent(true);
        getForbiddenIncludeFileContent(true);
    }

    private transient String footer = null;

    /**
     * Get the contents of the footer include file.
     *
     * @param force if true, reload even if already set
     * @return an empty string if it could not be read successfully, the
     * contents of the file otherwise.
     * @see Configuration#FOOTER_INCLUDE_FILE
     */
    public String getFooterIncludeFileContent(boolean force) {
        if (footer == null || force) {
            footer = getFileContent(new File(env.getIncludeRootPath(), Configuration.FOOTER_INCLUDE_FILE));
        }
        return footer;
    }

    private transient String header = null;

    /**
     * Get the contents of the header include file.
     *
     * @param force if true, reload even if already set
     * @return an empty string if it could not be read successfully, the
     * contents of the file otherwise.
     * @see Configuration#HEADER_INCLUDE_FILE
     */
    public String getHeaderIncludeFileContent(boolean force) {
        if (header == null || force) {
            header = getFileContent(new File(env.getIncludeRootPath(), Configuration.HEADER_INCLUDE_FILE));
        }
        return header;
    }

    private transient String body = null;

    /**
     * Get the contents of the body include file.
     *
     * @param force if true, reload even if already set
     * @return an empty string if it could not be read successfully, the
     * contents of the file otherwise.
     * @see Configuration#BODY_INCLUDE_FILE
     */
    public String getBodyIncludeFileContent(boolean force) {
        if (body == null || force) {
            body = getFileContent(new File(env.getIncludeRootPath(), Configuration.BODY_INCLUDE_FILE));
        }
        return body;
    }

    private transient String eforbidden_content = null;

    /**
     * Get the contents of the page for forbidden error page (403 Forbidden)
     * include file.
     *
     * @param force if true, reload even if already set
     * @return an empty string if it could not be read successfully, the
     * contents of the file otherwise.
     * @see Configuration#E_FORBIDDEN_INCLUDE_FILE
     */
    public String getForbiddenIncludeFileContent(boolean force) {
        if (eforbidden_content == null || force) {
            eforbidden_content = getFileContent(new File(env.getIncludeRootPath(), Configuration.E_FORBIDDEN_INCLUDE_FILE));
        }
        return eforbidden_content;
    }
}
