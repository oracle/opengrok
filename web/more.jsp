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

Copyright 2010 Sun Microsystems, Inc.  All rights reserved.
Use is subject to license terms.

--%><%@ page import = "javax.servlet.*,
java.lang.*,
javax.servlet.http.*,
java.util.*,
java.io.*,
java.text.*,  
org.opensolaris.opengrok.analysis.*,
org.opensolaris.opengrok.history.*,
org.opensolaris.opengrok.web.*,
org.opensolaris.opengrok.search.context.*,
org.opensolaris.opengrok.search.QueryBuilder,
org.opensolaris.opengrok.search.SearchEngine,
java.util.regex.*,
org.apache.lucene.queryParser.*,
org.apache.lucene.search.*"
%><%@include file="mast.jsp"%><%

if (valid) {
    QueryBuilder qbuilder = new QueryBuilder()
            .setFreetext(request.getParameter("full"))
            .setDefs(request.getParameter("defs"))
            .setRefs(request.getParameter("refs"))
            .setPath(request.getParameter("path"))
            .setHist(request.getParameter("hist"));

    // This is for backward compatibility with links created by OpenGrok 0.8.x
    // and earlier. We used to concatenate the entire query into a single
    // string and send it in the t parameter. If we get such a link, just add
    // it to the freetext field, and we'll get the old behaviour. We can
    // probably remove this code in the first feature release after 0.9.
    String t = request.getParameter("t");
    if (t != null) {
        qbuilder.setFreetext(t);
    }

    try {
        Query tquery = qbuilder.build();
        if (tquery != null) {
            Context sourceContext = new Context(tquery, qbuilder.getQueries());
            %><p><span class="pagetitle">Lines Matching <b><%=tquery%></b></span></p><div id="more" style="line-height:1.5em;"><pre><%
            sourceContext.getContext(new FileReader(resourceFile), out, context+"/xref", null, path, null, false, null);
            %></pre></div><%
        }
    } catch (Exception e) {
	
    }
}

%><%@include file="foot.jspf"%>
