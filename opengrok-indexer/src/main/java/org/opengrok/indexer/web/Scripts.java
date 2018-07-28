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
 * Copyright (c) 2017, 2018 Oracle and/or its affiliates. All rights reserved.
 * Portions Copyright (c) 2017, Chris Fraire <cfraire@me.com>.
 */
package org.opengrok.indexer.web;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * A list-like container for javascripts in the page.
 *
 * @author Krystof Tulinger
 */
public class Scripts implements Iterable<Scripts.Script> {

    /**
     * A script wrapper.
     */
    static abstract public class Script {

        /**
         * Represents the script information, either
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

        abstract public String toHtml();

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
    static public class FileScript extends Script {

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

    /**
     * Script implementing the toHtml() method as an inline script resource.
     */
    static public class InlineScript extends Script {

        public InlineScript(String script, int priority) {
            super(script, priority);
        }

        @Override
        public String toHtml() {
            StringBuilder builder = new StringBuilder();
            builder.append("\t<script type=\"text/javascript\" data-priority=\"");
            builder.append(this.getPriority());
            builder.append("\">/* <![CDATA[ */");
            builder.append(this.getScriptData());
            builder.append("\n/* ]]> */</script>\n");
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
        SCRIPTS.put("jquery", new FileScript("js/jquery-3.2.0.min.js", 10));
        SCRIPTS.put("jquery-ui", new FileScript("js/jquery-ui-1.12.1-custom.min.js", 11));
        SCRIPTS.put("jquery-tablesorter", new FileScript("js/jquery-tablesorter-2.26.6.min.js", 12));
        SCRIPTS.put("tablesorter-parsers", new FileScript("js/tablesorter-parsers-0.0.1.js", 13));
        SCRIPTS.put("searchable-option-list", new FileScript("js/searchable-option-list-2.0.5.min.js", 14));
        SCRIPTS.put("utils", new FileScript("js/utils-0.0.24.js", 15));
        SCRIPTS.put("repos", new FileScript("js/repos-0.0.1.js", 20));
        SCRIPTS.put("diff", new FileScript("js/diff-0.0.3.js", 20));
        SCRIPTS.put("jquery-caret", new FileScript("js/jquery.caret-1.5.2.min.js", 25));
    }

    /**
     * Scripts which will be written to the page. We assume that the length
     * could be the same as for {@link #SCRIPTS}.
     */
    private final List<Script> outputScripts = new ArrayList<>(SCRIPTS.size());

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
     *
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
     *
     * @see List#size()
     */
    public int size() {
        return outputScripts.size();
    }

    /**
     * Check if there is any script for this page.
     *
     * @return true if there is not; false otherwise
     *
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
     * @param scriptName name of the script
     * @return true if script was added; false otherwise
     */
    public boolean addScript(String contextPath, String scriptName) {
        contextPath = contextPath == null || contextPath.isEmpty() ? "/" : contextPath + "/";
        if (SCRIPTS.containsKey(scriptName)) {
            this.addScript(
                    // put the context path end append the script path
                    new FileScript(contextPath + SCRIPTS.get(scriptName).getScriptData(),
                            SCRIPTS.get(scriptName).getPriority()));
            return true;
        }
        return false;
    }

    /**
     * Add a script to the page, taking the script priority into account. The
     * position is determined as the upper bound for the given priority.
     *
     * @param script the script
     */
    public void addScript(Script script) {
        int index = Collections.binarySearch(outputScripts, script, new Comparator<Script>() {
            @Override
            public int compare(Script a, Script b) {
                return a.getPriority() - b.getPriority();
            }
        });
        if (index < 0) {
            /**
             * Key is not found in the list the index<br>
             * equals to -(insertion index) - 1.
             */
            this.outputScripts.add(Math.abs(index + 1), script);
        } else {
            /**
             * Key found in the list, append it after the last element with the
             * same priority => insert it at the upper bound index.
             */
            while (index < this.outputScripts.size()
                    && this.outputScripts.get(index).getPriority() == script.getPriority()) {
                index++;
            }
            this.outputScripts.add(index, script);
        }
    }

    /**
     * Get the page script by the index
     *
     * @param index index of the script
     * @return the script
     *
     * @see List#get(int)
     */
    public Script get(int index) {
        return outputScripts.get(index);
    }
}
