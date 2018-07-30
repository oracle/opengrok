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
 * Copyright (c) 2017, Chris Fraire <cfraire@me.com>.
 */
package org.opengrok.indexer.analysis;

import java.io.Reader;
import java.io.Writer;
import org.opengrok.indexer.configuration.Project;
import org.opengrok.indexer.history.Annotation;

/**
 * Represents the arguments for the
 * {@link org.opengrok.indexer.analysis.FileAnalyzer#writeXref(org.opengrok.indexer.analysis.WriteXrefArgs)}
 * method.
 */
public class WriteXrefArgs {
    private final Reader in;
    private final Writer out;
    private Definitions defs;
    private Annotation annotation;
    private Project project;

    /**
     * Initializes an instance of {@link WriteXrefArgs} for the required
     * arguments.
     * @param in a defined instance
     * @param out a defined instance
     * @throws IllegalArgumentException thrown if any argument is null.
     */
    public WriteXrefArgs(Reader in, Writer out) {
        if (in == null) {
            throw new IllegalArgumentException("`in' is null");
        }
        if (out == null) {
            throw new IllegalArgumentException("`out' is null");
        }
        this.in = in;
        this.out = out;
    }

    public Reader getIn() { return in; }
    public Writer getOut() { return out; }

    public Definitions getDefs() { return defs; }
    public void setDefs(Definitions value) { defs = value; }

    public Annotation getAnnotation() { return annotation; }
    public void setAnnotation(Annotation value) { annotation = value; }

    public Project getProject() { return project; }
    public void setProject(Project value) { project = value; }
}
