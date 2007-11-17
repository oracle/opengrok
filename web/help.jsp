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

Copyright 2007 Sun Microsystems, Inc.  All rights reserved.
Use is subject to license terms.

--%><%@ page import = "org.opensolaris.opengrok.configuration.RuntimeEnvironment"
             session="false" errorPage="error.jsp" %><%
RuntimeEnvironment environment = RuntimeEnvironment.getInstance();
environment.register();
String pageTitle = "OpenGrok Help";
%><%@ include file="httpheader.jspf" %>
<body>
    <div id="page">
        <div id="header"><%@ include file="pageheader.jspf" %> </div>
        <div id="Masthead"></div>
        <div id="bar">
            <table cellpadding="0" cellspacing="0" border="0" width="100%">
                <tr><td valign="top"><br /> &nbsp;</td><td align="left" valign="middle"><br/>
                        <form action="search" name="sbox">
                            <table cellpadding="2" border="0" cellspacing="0">
                                <tr><td align="right"> Full&nbsp;Search (q) </td><td><input class="q" name="q" size="45" value=""/></td></tr>
                                <tr><td align="right"> Definition (defs) </td><td><input class="q" name="defs" size="25" value=""/></td></tr>
                                <tr><td align="right"> Symbol (refs) </td><td><input class="q" name="refs" size="25" value=""/></td></tr>
                                <tr><td align="right"> File&nbsp;Path (path) </td><td><input class="q" name="path" size="25" value=""/></td></tr>
                                <tr><td align="right"> History (hist) </td><td><input class="q" name="hist" size="25" value=""/></td></tr>
                                <tr><td></td><td>  &nbsp; <input class="submit" type="submit" value=" Search "/> | <input class="submit" onClick="document.sbox.q.value='';document.sbox.defs.value='';document.sbox.refs.value='';document.sbox.path.value='';document.sbox.hist.value='';" type="button" value=" Clear "/></td></tr>
                            </table>
                        </form>
            </td></tr></table>
        </div>
        <div id="results"><p>
                A Query is a series of clauses. A clause may be prefixed by:
                <ul>
                    <li>a plus (+) or a minus (-) sign, indicating that the clause is required or prohibited respectively; or
                    <li>a term followed by a colon, indicating the field to be searched. This enables one to construct queries which search multiple fields. 
                </ul>
                A clause may be either:
                <ul>
                    <li> a term, indicating all the documents that contain this term; or
                    <li> a nested query, enclosed in parentheses (). Note that this may be used with a +/- prefix to require any of a set of terms. 
                </ul>
                
                valid FIELDs are
                <pre>
                    full: Full text search.
                    defs: Only finds symbol definitions.
                    refs: Only finds symbols.
                    path: path of the source file.
                    hist: History log comments
                </pre>
                
                <b>Examples</b>
                <pre>
                    
                    To find where setResourceMonitors is defined
                    <a href="search?q=&defs=setResourceMonitors">defs:setResourceMonitors</a>
                    
                    To find files that use sprintf in usr/src/cmd/cmd-inet/usr.sbin/
                    <a href="search?refs=sprintf&path=usr%2Fsrc%2Fcmd%2Fcmd-inet%2Fusr.sbin%2F">refs:sprintf path:usr/src/cmd/cmd-inet/usr.sbin</a>
                    
                    To find assignments to variable Asign
                    <a href="search?q=%22asign+%3D+%22">"Asign="</a>
                    
                    To find Makefiles where pstack binary is being built
                    <a href="search?q=pstack&path=Makefile">pstack path:Makefile</a>
                    
                    to search for phrase "Bill Joy":
                    <a href="search?q=%22Bill+Joy%22">"Bill Joy"</a>
                    
                    To find perl files that do not use /usr/bin/perl but something else, 
                    <a href="search?q=-%22%2Fusr%2Fbin%2Fperl%22+%2B%22%2Fbin%2Fperl%22">-"/usr/bin/perl" +"/bin/perl"</a>
            </pre></p>
        </div>
        <%@include file="foot.jspf"%>
