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
 * Copyright (c) 2018 Oracle and/or its affiliates. All rights reserved.
 */
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
    public static final boolean SHOW_PROJECTS_DEFAULT = true;
    public static final boolean SHOW_TIME_DEFAULT = false;
    public static final String REBUILD_CRON_CONFIG_DEFAULT = "0 0 * * *"; // every day at midnight
    public static final int SUGGESTER_BUILD_TERMINATION_TIME_DEFAULT = 1800; // half an hour should be enough

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

    private boolean showTime;

    private String rebuildCronConfig;

    private int suggesterBuildTerminationTimeSec;

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
        config.setShowScores(true); // TODO: change after implementation complete
        config.setShowProjects(SHOW_PROJECTS_DEFAULT);
        config.setShowTime(true); // TODO: change after implementation complete
        config.setRebuildCronConfig(REBUILD_CRON_CONFIG_DEFAULT);
        config.setSuggesterBuildTerminationTimeSec(SUGGESTER_BUILD_TERMINATION_TIME_DEFAULT);
        return config;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(final boolean enabled) {
        this.enabled = enabled;
    }

    public int getMaxResults() {
        return maxResults;
    }

    public void setMaxResults(final int maxResults) {
        this.maxResults = maxResults;
    }

    public int getMinChars() {
        return minChars;
    }

    public void setMinChars(final int minChars) {
        this.minChars = minChars;
    }

    public Set<String> getAllowedProjects() {
        return allowedProjects;
    }

    public void setAllowedProjects(final Set<String> allowedProjects) {
        this.allowedProjects = allowedProjects;
    }

    public int getMaxProjects() {
        return maxProjects;
    }

    public void setMaxProjects(final int maxProjects) {
        this.maxProjects = maxProjects;
    }

    public Set<String> getAllowedFields() {
        return allowedFields;
    }

    public void setAllowedFields(final Set<String> allowedFields) {
        this.allowedFields = allowedFields;
    }

    public boolean isAllowComplexQueries() {
        return allowComplexQueries;
    }

    public void setAllowComplexQueries(final boolean allowComplexQueries) {
        this.allowComplexQueries = allowComplexQueries;
    }

    public boolean isAllowMostPopular() {
        return allowMostPopular;
    }

    public void setAllowMostPopular(final boolean allowMostPopular) {
        this.allowMostPopular = allowMostPopular;
    }

    public boolean isShowScores() {
        return showScores;
    }

    public void setShowScores(final boolean showScores) {
        this.showScores = showScores;
    }

    public boolean isShowProjects() {
        return showProjects;
    }

    public void setShowProjects(final boolean showProjects) {
        this.showProjects = showProjects;
    }

    public boolean isShowTime() {
        return showTime;
    }

    public void setShowTime(final boolean showTime) {
        this.showTime = showTime;
    }

    public String getRebuildCronConfig() {
        return rebuildCronConfig;
    }

    public void setRebuildCronConfig(final String rebuildCronConfig) {
        this.rebuildCronConfig = rebuildCronConfig;
    }

    public int getSuggesterBuildTerminationTimeSec() {
        return suggesterBuildTerminationTimeSec;
    }

    public void setSuggesterBuildTerminationTimeSec(final int suggesterBuildTerminationTimeSec) {
        this.suggesterBuildTerminationTimeSec = suggesterBuildTerminationTimeSec;
    }
}
