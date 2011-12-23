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

Copyright 2010 Sun Microsystems, Inc.  All rights reserved.
Use is subject to license terms.

Portions Copyright 2011 Jens Elkner.

--%><%@page import="
java.io.FileReader,
java.util.logging.Level,

org.opensolaris.opengrok.OpenGrokLogger,
org.apache.lucene.search.Query,
org.opensolaris.opengrok.search.QueryBuilder,
org.opensolaris.opengrok.search.context.Context"
%><%@include

file="mast.jsp"

%><%
/* ---------------------- more.jsp start --------------------- */
{
    cfg = PageConfig.get(request);
    QueryBuilder qbuilder = cfg.getQueryBuilder();

    try {
        Query tquery = qbuilder.build();
        if (tquery != null) {
            Context sourceContext = new Context(tquery, qbuilder.getQueries());
%><p><span class="pagetitle">Lines Matching <b><%= tquery %></b></span></p>
<div id="more" style="line-height:1.5em;">
    <pre><%
            sourceContext.getContext(new FileReader(cfg.getResourceFile()), out,
                request.getContextPath() + Prefix.XREF_P, null, cfg.getPath(),
                null, false, null);
    %></pre>
</div><%
        }
    } catch (Exception e) {
        OpenGrokLogger.getLogger().log(Level.WARNING, e.getMessage());
    }
}
/* ---------------------- more.jsp end --------------------- */
%><%@

include file="foot.jspf"

%>