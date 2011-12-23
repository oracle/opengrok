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

Copyright 2005 Sun Microsystems, Inc.  All rights reserved.
Use is subject to license terms.

Portions Copyright 2011 Jens Elkner.
--%><%@ page session="false" errorPage="error.jsp" %><%@

include file="projects.jspf"

%><%
/* ---------------------- index.jsp start --------------------- */
{
    cfg = PageConfig.get(request);
    cfg.setTitle("Search");
%><%@

include file="httpheader.jspf"

%><body>
    <div id="page">
        <div id="whole_header">
            <div id="header"><%@

include file="pageheader.jspf"

            %></div>
            <div id="Masthead"></div>
            <div id="sbar"><%@

include file="menu.jspf"

            %></div>
        </div>
        <div id="results"><%@

include file="index_body.html"

        %></div>
<%
}
/* ---------------------- index.jsp end --------------------- */
%><%@

include file="foot.jspf"

%>