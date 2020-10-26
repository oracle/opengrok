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
 * Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
 */
package org.opengrok.web.api.v1.suggester.model;

import org.opengrok.indexer.search.QueryBuilder;
import org.opengrok.web.api.v1.suggester.provider.filter.AuthorizationFilter;

import javax.validation.constraints.Min;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Pattern;
import javax.ws.rs.QueryParam;
import java.util.List;

/**
 * Combines multiple query params for suggester into one concise class.
 */
public final class SuggesterQueryData {

    @QueryParam(AuthorizationFilter.PROJECTS_PARAM)
    private List<String> projects;

    @NotBlank(message = "Field param cannot be blank")
    @Pattern(message = "Unknown field", regexp = "(" + QueryBuilder.FULL + "|" + QueryBuilder.DEFS + "|" +
            QueryBuilder.REFS + "|" + QueryBuilder.PATH + "|" + QueryBuilder.HIST + ")")
    @QueryParam("field")
    private String field;

    @Min(message = "Caret position cannot be negative", value = 0)
    @QueryParam("caret")
    private int caretPosition;

    @QueryParam(QueryBuilder.FULL)
    private String full;

    @QueryParam(QueryBuilder.DEFS)
    private String defs;

    @QueryParam(QueryBuilder.REFS)
    private String refs;

    @QueryParam(QueryBuilder.PATH)
    private String path;

    @QueryParam(QueryBuilder.HIST)
    private String hist;

    @QueryParam(QueryBuilder.TYPE)
    private String type;

    public List<String> getProjects() {
        return projects;
    }

    public void setProjects(List<String> projects) {
        this.projects = projects;
    }

    public String getField() {
        return field;
    }

    public void setField(String field) {
        this.field = field;
    }

    public int getCaretPosition() {
        return caretPosition;
    }

    public void setCaretPosition(int caretPosition) {
        this.caretPosition = caretPosition;
    }

    public String getFull() {
        return full;
    }

    public void setFull(String full) {
        this.full = full;
    }

    public String getDefs() {
        return defs;
    }

    public void setDefs(String defs) {
        this.defs = defs;
    }

    public String getRefs() {
        return refs;
    }

    public void setRefs(String refs) {
        this.refs = refs;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public String getHist() {
        return hist;
    }

    public void setHist(String hist) {
        this.hist = hist;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

}
