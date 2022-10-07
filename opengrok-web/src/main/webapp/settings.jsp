<%--
CDDL HEADER START

The contents of this file are subject to the terms of the
Common Development and Distribution License (the "License").
You may not use this file except in compliance with the License.

See LICENSE.txt included in this distribution for the specific
language governing permissions and limitations under the License.

When distributing Covered Code, include this CDDL HEADER in each
file and include the License file at LICENSE.txt.
If applicable, add the following below this CDDL HEADER, with the
fields enclosed by brackets "[]" replaced with your own identifying
information: Portions Copyright [yyyy] [name of copyright owner]

CDDL HEADER END

Copyright (c) 2021, 2022, Oracle and/or its affiliates. All rights reserved.
--%>
<%@page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<%@page session="false" errorPage="error.jsp" import="org.opengrok.web.PageConfig" %>
<%@ page import="org.opengrok.indexer.configuration.RuntimeEnvironment" %>
<%
    {
        PageConfig cfg = PageConfig.get(request);
        cfg.setTitle("OpenGrok Settings");
    }
%>
<%@ include file="/httpheader.jspf" %>
<body>
<div id="page">
    <header id="whole_header">
        <%@include file="/pageheader.jspf" %>
        <div id="Masthead">
            <a href="<%= request.getContextPath() %>/"><span id="home"></span>Home</a>
        </div>
    </header>
    <div id="sbar"></div>
    <div style="padding-left: 1rem;">
        <h1>Settings</h1>
        <h3 class="header-half-bottom-margin">Suggester</h3>
        <%
            boolean suggesterEnabled = RuntimeEnvironment.getInstance().getSuggesterConfig().isEnabled();
        %>
        <label>Enabled
            <input class="local-setting" name="suggester-enabled" type="checkbox" data-checked-value="true"
                   data-unchecked-value="false" data-default-value="<%= suggesterEnabled ? "true" : "false" %>"
                   <%= suggesterEnabled ? "" : "disabled" %>
                   onchange="onSettingsValueChange(this)">
        </label>
        <br>
        <br>
        <input class="submit btn no-margin-left" onclick="resetAllSettings()" type="button" value="Reset to defaults"/>
    </div>
</div>
<script type="text/javascript">
    /* <![CDATA[ */
    document.pageReady.push(() => initSettings());
    /* ]]> */
</script>
<%@include file="/foot.jspf" %>
