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

ident	"%Z%%M% %I%     %E% SMI"

--%><%@ page import = "java.util.List,
javax.servlet.*,
javax.servlet.http.*,
org.opensolaris.opengrok.configuration.RuntimeEnvironment,
org.opensolaris.opengrok.configuration.Project,
org.opensolaris.opengrok.web.*"
 session="false" errorPage="error.jsp" %><%@ include file="projects.jspf" %><%
String q = null;
String defs = null;
String refs = null;
String hist = null;
String path = null;
 %><?xml version="1.0" encoding="iso-8859-1"?>
<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Strict//EN" "http://www.w3.org/TR/xhtml1/DTD/xhtml1-strict.dtd">
<html xmlns="http://www.w3.org/1999/xhtml" xml:lang="en-US" lang="en-US">
<head>
    <meta name="robots" content="noindex,nofollow">
    <link rel="icon" href="img/icon.png" type="image/png"/>
    <link rel="stylesheet" type="text/css" href="style.css"/>
    <link rel="stylesheet" type="text/css" href="print.css" media="print" />
    <link rel="alternate stylesheet" type="text/css" media="all" title="Paper White" href="print.css"/>
    <title>Search</title>
</head>
<body>
<div id="page">
<div id="header">
    <%= getServletContext().getInitParameter("HEADER") %>
</div>
<div id="Masthead"></div>
<div id="bar">
<%@ include file="menu.jspf" %>
</div>
<div id="results" style="font-size:100%">
    <!-- CONFIG: Page Content -->
    <p>
        <b>OpenGrok - a "wicked fast" source browser!</b>
    </p>
    <p>
        This is the front page of of your <a href="xref/">browsable and searchable
    source tree</a> (<a href="xref/">xref/</a>).</p>
    <p>
        To setup and configure OpenGrok please refer to 
        <a href="http://www.opensolaris.org/os/project/opengrok/">http://www.opensolaris.org/os/project/opengrok/</a>
        or README.txt in the distribution.
    </p>
    <p>
        You can replace this block of text with some more useful 
        information about your source tree and its organization, with direct 
        links to key parts of code base.
    </p>
    <br/>
    <br/>
    <br/>
    <br/>
    <br/>
</div>
<%@include file="foot.jspf"%>
