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
 * Copyright (c) 2017, 2019 Oracle and/or its affiliates. All rights reserved.
 * Portions Copyright (c) 2017, Chris Fraire <cfraire@me.com>.
 */
package org.opengrok.indexer.web;

import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NavigableSet;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * A list-like container for javascripts in the page.
 *
 * @author Krystof Tulinger
 */
public class Scripts implements Iterable<Scripts.Script> {

    private static final String DEBUG_SUFFIX = "-debug";

    enum Type {
        MINIFIED, DEBUG
    }

    /**
     * A script wrapper.
     */
    public abstract static class Script {

        /**
         * Represents the script information. Either
         * <ul>
         * <li>script HTML src attribute for remote scripts</li>
         * <li>inline javascript code for inline scripts</li>
         * </ul>
         */
        protected String scriptData;
        protected int priority;

        public Script(String scriptData, int priority) {
            this.scriptData = scriptData;
            this.priority = priority;
        }

        public abstract String toHtml();

        public String getScriptData() {
            return scriptData;
        }

        public int getPriority() {
            return priority;
        }
    }

    /**
     * Script implementing the toHtml() method as an external script resource.
     */
    public static class FileScript extends Script {

        public FileScript(String script, int priority) {
            super(script, priority);
        }

        @Override
        public String toHtml() {
            StringBuilder builder = new StringBuilder();
            builder.append("\t<script type=\"text/javascript\" src=\"");
            builder.append(this.getScriptData());
            builder.append("\" data-priority=\"");
            builder.append(this.getPriority());
            builder.append("\"></script>\n");
            return builder.toString();
        }

    }

    protected static final Map<String, Script> SCRIPTS = new TreeMap<>();

    /**
     * Aliases for the page scripts. The path in the FileScript is relatively to
     * the request's context path.
     *
     * @see HttpServletRequest#getContextPath()
     */
    static {
        SCRIPTS.put("jquery", new FileScript("js/jquery-3.4.1.min.js", 10));
        SCRIPTS.put("jquery-ui", new FileScript("js/jquery-ui-1.12.1-custom.min.js", 11));
        SCRIPTS.put("jquery-tablesorter", new FileScript("js/jquery-tablesorter-2.26.6.min.js", 12));
        SCRIPTS.put("tablesorter-parsers", new FileScript("js/tablesorter-parsers-0.0.2.min.js", 13));
        SCRIPTS.put("tablesorter-parsers" + DEBUG_SUFFIX, new FileScript("js/tablesorter-parsers-0.0.2.js", 13));
        SCRIPTS.put("searchable-option-list", new FileScript("js/searchable-option-list-2.0.7.min.js", 14));
        SCRIPTS.put("utils", new FileScript("js/utils-0.0.32.min.js", 15));
        SCRIPTS.put("utils" + DEBUG_SUFFIX, new FileScript("js/utils-0.0.32.js", 15));
        SCRIPTS.put("repos", new FileScript("js/repos-0.0.2.min.js", 20));
        SCRIPTS.put("repos" + DEBUG_SUFFIX, new FileScript("js/repos-0.0.2.js", 20));
        SCRIPTS.put("diff", new FileScript("js/diff-0.0.4.min.js", 20));
        SCRIPTS.put("diff" + DEBUG_SUFFIX, new FileScript("js/diff-0.0.4.js", 20));
        SCRIPTS.put("jquery-caret", new FileScript("js/jquery.caret-1.5.2.min.js", 25));
    }

    private static final Comparator<Script> SCRIPTS_COMPARATOR = Comparator
            .comparingInt(Script::getPriority)
            .thenComparing(Script::getScriptData);

    /**
     * Scripts which will be written to the page.
     */
    private final NavigableSet<Script> outputScripts = new TreeSet<>(SCRIPTS_COMPARATOR);

    /**
     * Convert the page scripts into HTML.
     *
     * @return the HTML
     */
    public String toHtml() {
        StringBuilder builder = new StringBuilder();
        for (Script entry : this) {
            builder.append(entry.toHtml());
        }
        return builder.toString();
    }

    /**
     * Return the HTML representation of the page scripts.
     *
     * @return the HTML
     * @see #toHtml()
     */
    @Override
    public String toString() {
        return toHtml();
    }

    /**
     * Return the size of the page scripts.
     *
     * @return the size
     * @see List#size()
     */
    public int size() {
        return outputScripts.size();
    }

    /**
     * Check if there is any script for this page.
     *
     * @return true if there is not; false otherwise
     * @see List#isEmpty()
     */
    public boolean isEmpty() {
        return outputScripts.isEmpty();
    }

    /**
     * Iterator over the page scripts.
     *
     * @return the iterator
     * @see List#iterator()
     */
    @Override
    public Iterator<Script> iterator() {
        return outputScripts.iterator();
    }

    /**
     * Add a script which is identified by the name.
     *
     * @param contextPath given context path for the used URL
     * @param scriptName  name of the script
     * @param type type of the script to add
     * @return true if script was added; false otherwise
     */
    public boolean addScript(String contextPath, String scriptName, Type type) {
        contextPath = contextPath == null || contextPath.isEmpty() ? "/" : contextPath + "/";
        if (type == Type.DEBUG && SCRIPTS.containsKey(scriptName + DEBUG_SUFFIX)) {
            addScript(contextPath, scriptName + DEBUG_SUFFIX);
            return true;
        } else if (SCRIPTS.containsKey(scriptName)) {
            addScript(contextPath, scriptName);
            return true;
        }
        return false;
    }

    private void addScript(String contextPath, String scriptName) {
        this.addScript(new FileScript(contextPath + SCRIPTS.get(scriptName).getScriptData(),
                SCRIPTS.get(scriptName).getPriority()));
    }

    /**
     * Add a script to the page, taking the script priority into account.
     *
     * @param script the script
     */
    public void addScript(Script script) {
        this.outputScripts.add(script);
    }
}
