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
 * Copyright (c) 2008, 2018, Oracle and/or its affiliates. All rights reserved.
 * Portions Copyright (c) 2020, Aleksandr Kirillov <alexkirillovsamara@gmail.com>.
 */
package org.opengrok.indexer.configuration;

import java.io.File;

import static org.opengrok.indexer.util.IOUtils.getFileContent;

public class IncludeFiles {
    /**
     * Reload the content of all include files.
     */
    public void reloadIncludeFiles() {
        getBodyIncludeFileContent(true);
        getHeaderIncludeFileContent(true);
        getFooterIncludeFileContent(true);
        getForbiddenIncludeFileContent(true);
        getHttpHeaderIncludeFileContent(true);
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
            footer = getFileContent(new File(RuntimeEnvironment.getInstance().getIncludeRootPath(),
                    Configuration.FOOTER_INCLUDE_FILE));
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
            header = getFileContent(new File(RuntimeEnvironment.getInstance().getIncludeRootPath(),
                    Configuration.HEADER_INCLUDE_FILE));
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
            body = getFileContent(new File(RuntimeEnvironment.getInstance().getIncludeRootPath(),
                    Configuration.BODY_INCLUDE_FILE));
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
            eforbidden_content = getFileContent(new File(RuntimeEnvironment.getInstance().getIncludeRootPath(),
                    Configuration.E_FORBIDDEN_INCLUDE_FILE));
        }
        return eforbidden_content;
    }

    private transient String http_header = null;

    /**
     * Get the contents of the HTTP header include file.
     *
     * @param force if true, reload even if already set
     * @return an empty string if it could not be read successfully, the
     * contents of the file otherwise.
     * @see Configuration#HTTP_HEADER_INCLUDE_FILE
     */
    public String getHttpHeaderIncludeFileContent(boolean force) {
        if (http_header == null || force) {
            http_header = getFileContent(new File(RuntimeEnvironment.getInstance().getIncludeRootPath(),
                    Configuration.HTTP_HEADER_INCLUDE_FILE));
        }
        return http_header;
    }
}
