package org.opensolaris.opengrok.web.suggester.model;

import org.hibernate.validator.constraints.NotBlank;
import org.opensolaris.opengrok.search.QueryBuilder;

import javax.validation.constraints.Min;
import javax.ws.rs.QueryParam;
import java.util.List;

public final class SuggesterQueryData {

    public static final String PROJECTS_PARAM = "projects[]";

    @QueryParam(PROJECTS_PARAM)
    private List<String> projects;

    @NotBlank
    @QueryParam("field")
    private String field;

    @Min(0)
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
