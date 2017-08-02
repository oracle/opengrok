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
 * Copyright (c) 2014, 2017 Oracle and/or its affiliates. All rights reserved.
 */
package org.opensolaris.opengrok.web;

import java.io.IOException;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.List;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.apache.tools.ant.util.Base64Converter;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.opensolaris.opengrok.search.Hit;
import org.opensolaris.opengrok.search.SearchEngine;

public class JSONSearchServlet extends HttpServlet {

    private static final long serialVersionUID = -1675062445999680962L;
    private static final Base64Converter conv = new Base64Converter();
    private static final int MAX_RESULTS = 1000; // hard coded limit
    private static final String PARAM_FREETEXT = "freetext";
    private static final String PARAM_DEF = "def";
    private static final String PARAM_SYMBOL = "symbol";
    private static final String PARAM_PATH = "path";
    private static final String PARAM_HIST = "hist";
    private static final String PARAM_START = "start";
    private static final String PARAM_MAXRESULTS = "maxresults";
    private static final String PARAM_PROJECT = "project";
    private static final String ATTRIBUTE_DIRECTORY = "directory";
    private static final String ATTRIBUTE_FILENAME = "filename";
    private static final String ATTRIBUTE_LINENO = "lineno";
    private static final String ATTRIBUTE_LINE = "line";
    private static final String ATTRIBUTE_PATH = "path";
    private static final String ATTRIBUTE_RESULTS = "results";
    private static final String ATTRIBUTE_DURATION = "duration";
    private static final String ATTRIBUTE_RESULT_COUNT = "resultcount";

    @SuppressWarnings({"unchecked", "deprecation"})
    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        JSONObject result = new JSONObject();
        SearchEngine engine = new SearchEngine();

        boolean valid = false;

        String freetext = req.getParameter(PARAM_FREETEXT);
        String def = req.getParameter(PARAM_DEF);
        String symbol = req.getParameter(PARAM_SYMBOL);
        String path = req.getParameter(PARAM_PATH);
        String hist = req.getParameter(PARAM_HIST);
        String projects[] = req.getParameterValues(PARAM_PROJECT);

        if (freetext != null) {
            freetext = URLDecoder.decode(freetext);
            engine.setFreetext(freetext);
            valid = true;
            result.put(PARAM_FREETEXT, freetext);
        }

        if (def != null) {
            def = URLDecoder.decode(def);
            engine.setDefinition(def);
            valid = true;
            result.put(PARAM_DEF, def);
        }

        if (symbol != null) {
            symbol = URLDecoder.decode(symbol);
            engine.setSymbol(symbol);
            valid = true;
            result.put(PARAM_SYMBOL, symbol);
        }

        if (path != null) {
            path = URLDecoder.decode(path);
            engine.setFile(path);
            valid = true;
            result.put(PARAM_PATH, path);
        }

        if (hist != null) {
            hist = URLDecoder.decode(hist);
            engine.setHistory(hist);
            valid = true;
            result.put(PARAM_HIST, hist);
        }

        if (!valid) {
            return;
        }

        try {
            long start = System.currentTimeMillis();
            int numResults;
            if(projects == null || projects.length == 0) {
                numResults = engine.search(req);
            } else {
                numResults = engine.search(req, projects);
            }

            int pageStart = getIntParameter(req, PARAM_START, 0);

            Integer maxResultsParam = getIntParameter(req, PARAM_MAXRESULTS, null);
            int maxResults = maxResultsParam == null ? MAX_RESULTS : maxResultsParam;
            if (maxResultsParam != null) {
                result.put(PARAM_MAXRESULTS, maxResults);
            }

            List<Hit> results = new ArrayList<>(maxResults);
            engine.results(pageStart,
                    numResults > maxResults ? maxResults : numResults, results);
            JSONArray resultsArray = new JSONArray();
            for (Hit hit : results) {
                JSONObject hitJson = new JSONObject();
                hitJson.put(ATTRIBUTE_DIRECTORY,
                        JSONObject.escape(hit.getDirectory()));
                hitJson.put(ATTRIBUTE_FILENAME,
                        JSONObject.escape(hit.getFilename()));
                hitJson.put(ATTRIBUTE_LINENO, hit.getLineno());
                hitJson.put(ATTRIBUTE_LINE, conv.encode(hit.getLine()));
                hitJson.put(ATTRIBUTE_PATH, hit.getPath());
                resultsArray.add(hitJson);
            }

            long duration = System.currentTimeMillis() - start;

            result.put(ATTRIBUTE_DURATION, duration);
            result.put(ATTRIBUTE_RESULT_COUNT, results.size());

            result.put(ATTRIBUTE_RESULTS, resultsArray);


            resp.setContentType("application/json");
            resp.getWriter().write(result.toString());
        } finally {
            engine.destroy();
        }
    }

    /**
     * Convenience utility for consistently getting and parsing an integer
     * parameter from the provided {@link HttpServletRequest}.
     *
     * @param request The request to extract the parameter from
     * @param paramName The name of the parameter on the request.
     * @param defaultValue The default value to use when no value is
     *                     provided or parsing fails.
     * @return The integer value of the request param if present or the
     *         defaultValue if none is present.
     */
    private static Integer getIntParameter(final HttpServletRequest request, final String paramName, final Integer defaultValue) {
        final String paramValue = request.getParameter(paramName);
        if (paramValue == null) {
            return defaultValue;
        }

        try {
            return Integer.valueOf(paramValue);
        } catch (final NumberFormatException ignored) {}

        return defaultValue;
    }
}
