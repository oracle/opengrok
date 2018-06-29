package org.opensolaris.opengrok.configuration;

import java.util.Set;

public class SuggesterConfig {

    public static final boolean ENABLED_DEFAULT = true;
    public static final int MAX_RESULTS_DEFAULT = 10;
    public static final int MIN_CHARS_DEFAULT = 0;
    public static final int MAX_PROJECTS_DEFAULT = Integer.MAX_VALUE;
    public static final boolean ALLOW_COMPLEX_QUERIES_DEFAULT = true;
    public static final boolean ALLOW_MOST_POPULAR_DEFAULT = true;
    public static final boolean SHOW_SCORES_DEFAULT = false;
    public static final boolean SHOW_INDEXES_DEFAULT = true;
    public static final boolean SHOW_SPEED_DEFAULT = false;

    public static final Set<String> allowedProjectsDefault = null;
    public static final Set<String> allowedFieldsDefault = null;

    private boolean enabled;

    private int maxResults;

    private int minChars;

    private Set<String> allowedProjects;

    private int maxProjects;

    private Set<String> allowedFields;

    private boolean allowComplexQueries;

    private boolean allowMostPopular;

    private boolean showScores;

    private boolean showProjects;

    private boolean showSpeed;

    private SuggesterConfig() {
    }

    public static SuggesterConfig getDefault() {
        SuggesterConfig config = new SuggesterConfig();
        config.setEnabled(ENABLED_DEFAULT);
        config.setMaxResults(MAX_RESULTS_DEFAULT);
        config.setMinChars(MIN_CHARS_DEFAULT);
        config.setAllowedProjects(allowedProjectsDefault);
        config.setMaxProjects(MAX_PROJECTS_DEFAULT);
        config.setAllowedFields(allowedFieldsDefault);
        config.setAllowComplexQueries(ALLOW_COMPLEX_QUERIES_DEFAULT);
        config.setAllowMostPopular(ALLOW_MOST_POPULAR_DEFAULT);
        config.setShowScores(SHOW_SCORES_DEFAULT);
        config.setShowProjects(SHOW_INDEXES_DEFAULT);
        config.setShowSpeed(SHOW_SPEED_DEFAULT);
        return config;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public int getMaxResults() {
        return maxResults;
    }

    public void setMaxResults(int maxResults) {
        this.maxResults = maxResults;
    }

    public int getMinChars() {
        return minChars;
    }

    public void setMinChars(int minChars) {
        this.minChars = minChars;
    }

    public Set<String> getAllowedProjects() {
        return allowedProjects;
    }

    public void setAllowedProjects(Set<String> allowedProjects) {
        this.allowedProjects = allowedProjects;
    }

    public int getMaxProjects() {
        return maxProjects;
    }

    public void setMaxProjects(int maxProjects) {
        this.maxProjects = maxProjects;
    }

    public Set<String> getAllowedFields() {
        return allowedFields;
    }

    public void setAllowedFields(Set<String> allowedFields) {
        this.allowedFields = allowedFields;
    }

    public boolean isAllowComplexQueries() {
        return allowComplexQueries;
    }

    public void setAllowComplexQueries(boolean allowComplexQueries) {
        this.allowComplexQueries = allowComplexQueries;
    }

    public boolean isAllowMostPopular() {
        return allowMostPopular;
    }

    public void setAllowMostPopular(boolean allowMostPopular) {
        this.allowMostPopular = allowMostPopular;
    }

    public boolean isShowScores() {
        return showScores;
    }

    public void setShowScores(boolean showScores) {
        this.showScores = showScores;
    }

    public boolean isShowProjects() {
        return showProjects;
    }

    public void setShowProjects(boolean showProjects) {
        this.showProjects = showProjects;
    }

    public boolean isShowSpeed() {
        return showSpeed;
    }

    public void setShowSpeed(boolean showSpeed) {
        this.showSpeed = showSpeed;
    }
}
