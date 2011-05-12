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

Copyright (c) 2009, 2010, Oracle and/or its affiliates. All rights reserved.
Portions Copyright 2011 Jens Elkner.

--%><%@page session="false" errorPage="error.jsp" import="
org.opensolaris.opengrok.configuration.RuntimeEnvironment,
org.opensolaris.opengrok.web.Util"
%><%@

include file="projects.jspf"

%><%
/* ---------------------- status.jsp start --------------------- */
{
	cfg = PageConfig.get(request);
	cfg.setTitle("Status");
%><%@

include file="httpheader.jspf" 

%>
<body>
	<div id="page">
		<div id="whole_header">
			<div id="header"><%@

include file="pageheader.jspf"

			%>
			</div>
			<div id="Masthead"></div>
		</div>
		<div id="status">
			<h1>OpenGrok status page</h1>
			<p>
This page is only used for testing purposes to dump some of the
internal settings on your OpenGrok server.</p><%
		if (cfg.getEnv().isChattyStatusPage()) {
			Util.dumpConfiguration(out);
		} else {
		%><p>
For security reasons, printing of internal settings is not enabled by
default. To enable, set the property <tt>chattyStatusPage</tt> to
<tt>true</tt> in <tt>configuration.xml</tt>.</p><%
		}
		%>
		</div>
<%
}
/* ---------------------- status.jsp start --------------------- */
%><%@

include file="foot.jspf"

%>