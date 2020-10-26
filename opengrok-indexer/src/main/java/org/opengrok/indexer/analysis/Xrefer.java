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
 * Copyright (c) 2009, 2018, Oracle and/or its affiliates. All rights reserved.
 * Portions Copyright (c) 2011, Jens Elkner.
 * Portions Copyright (c) 2017, Chris Fraire <cfraire@me.com>.
 */
package org.opengrok.indexer.analysis;

import java.io.IOException;
import java.io.Writer;
import org.opengrok.indexer.configuration.Project;
import org.opengrok.indexer.history.Annotation;

/**
 * Represents an API for lexers that write a cross-reference document.
 */
public interface Xrefer extends Resettable {

    /**
     * Get generated scopes.
     * @return scopes for current line
     */
    Scopes getScopes();

    /**
     * Gets the document number of lines.
     * @return a number greater than or equal to 1
     */
    int getLineNumber();

    /**
     * Gets the document physical lines-of-code count.
     * @return a number greater than or equal to 0
     */
    int getLOC();

    void setAnnotation(Annotation annotation);

    /**
     * Set definitions.
     * @param defs definitions
     */
    void setDefs(Definitions defs);

    void setProject(Project project);

    /**
     * Set folding of code.
     * @param foldingEnabled whether to fold or not
     */
    void setFoldingEnabled(boolean foldingEnabled);

    /**
     * Set scopes.
     * @param scopesEnabled if they should be enabled or disabled
     */
    void setScopesEnabled(boolean scopesEnabled);

    /**
     * Write xref to the specified {@code Writer}.
     *
     * @param out xref destination
     * @throws IOException on error when writing the xref
     */
    void write(Writer out) throws IOException;
}
