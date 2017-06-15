<%--
$Id$

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

Copyright (c) 2010, 2017, Oracle and/or its affiliates. All rights reserved.

Portions Copyright 2011 Jens Elkner.

--%><%@page errorPage="error.jsp" import="
java.io.FileInputStream,
java.io.Reader,
java.util.logging.Level,
java.util.logging.Logger,

org.apache.lucene.search.Query,
org.opensolaris.opengrok.search.QueryBuilder,
org.opensolaris.opengrok.search.context.Context,
org.opensolaris.opengrok.logger.LoggerFactory,
org.opensolaris.opengrok.util.IOUtils"
%>
<%
{
    PageConfig cfg = PageConfig.get(request);
    cfg.checkSourceRootExistence();
}
%><%@include

file="mast.jsp"

%><%
/* ---------------------- more.jsp start --------------------- */
{
    PageConfig cfg = PageConfig.get(request);
    QueryBuilder qbuilder = cfg.getQueryBuilder();

    try {
        Query tquery = qbuilder.build();
        if (tquery != null) {
            Context sourceContext = new Context(tquery, qbuilder.getQueries());
%><p><span class="pagetitle">Lines Matching <b><%= tquery %></b></span></p>
<div id="more" style="line-height:1.5em;">
    <pre><%
            Reader r = IOUtils.createBOMStrippedReader(
                    new FileInputStream(cfg.getResourceFile()));
            sourceContext.getContext(r, out,
                request.getContextPath() + Prefix.XREF_P, null, cfg.getPath(),
                null, false, false, null, null);
    %></pre>
</div><%
        }
    } catch (Exception e) {
        LoggerFactory.getLogger(more_jsp.class).log(Level.WARNING, e.getMessage());
    }
}
/* ---------------------- more.jsp end --------------------- */
%><%@

include file="foot.jspf"

%>