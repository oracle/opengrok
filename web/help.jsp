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
                                <tr><td></td><td>  &nbsp; <input class="submit" type="submit" value=" Search "/> | <input class="submit" onclick="document.sbox.q.value='';document.sbox.defs.value='';document.sbox.refs.value='';document.sbox.path.value='';document.sbox.hist.value='';" type="button" value=" Clear "/></td></tr>
                            </table>
                        </form>
            </td></tr></table>
        </div>
        <div id="results"><p>
                A <u>Query</u> is a series of clauses. A clause may be prefixed by:
                <ul>
                    <li>a plus "<b>+</b>" or a minus "<b>-</b>" sign, indicating that the clause is required or prohibited respectively; or</li>
                    <li>a <u>term</u> followed by a colon "<b>:</b>", indicating the <u>field</u> to be searched. This enables one to construct queries which search multiple <u>fields</u>.</li>
                </ul>
                A clause may be either:
                <ul>
                    <li> a <u>term</u>, indicating all the documents that contain this term; or</li>
                    <li> a <u>phrase</u> - group of words surrounded by double quotes <b>" "</b>, e.g. "hello dolly"  </li>
                    <li> a nested query, enclosed in parentheses "<b>(</b>" "<b>)</b>" (also called query/field <u>grouping</u>) . Note that this may be used with a +/- prefix to require any of a set of terms. </li>
                    <li> boolean <u>operators</u> which allow terms to be combined through logic operators. Supported are <b>AND</b>(<b>&&</b>), "<b>+</b>", <b>OR</b>(<b>||</b>), <b>NOT</b>(<b>!</b>) and "<b>-</b>" (Note: they must be ALL CAPS).</li>
                </ul>
                Wildcard, Fuzzy, Proximity & Range Searches:
                <ul>
                    <li> to perform a single character wildcard search use the "<b>?</b>" symbol, e.g.  te?t</li>
                    <li> to perform a multiple character wildcard search use the "<b>*</b>" symbol, e.g. test* or te*t</li>
                    <li> you cannot use a * or ? symbol as the first character of a search (unless enabled using indexer option -a).</li>
                    <li> to do a fuzzy search(find words similar in spelling, based on the Levenshtein Distance, or Edit Distance algorithm) use the tilde, "<b>~</b>", e.g. rcs~ </li>
                    <li> to do a proximity search use the tilde, "~", symbol at the end of a Phrase. For example to search for a "opengrok" and "help" within 10 words of each other enter: "opengrok help"~10 </li>
                    <li> range queries allow one to match documents whose field(s) values are between the lower and upper bound specified by the Range Query. Range Queries can be inclusive or exclusive of the upper and lower bounds. Sorting is done lexicographically. Inclusive queries are denoted by square brackets <b>[ ]</b> , exclusive by curly brackets <b>{ }</b>. For example: title:{Aida TO Carmen} - will find all documents between Aida to Carmen, exclusive of Aida and Carmen. </li>
                </ul>
                    
                <a name="escaping"><u>Escaping special characters:</u></a>
                <p>Opengrok supports escaping special characters that are part of the query syntax. The current list special characters are:<br/>
                    <b>+ - &amp;&amp; || ! ( ) { } [ ] ^ " ~ * ? : \ </b><br/>
                To escape these character use the \ before the character. For example to search for (1+1):2 use the query: \(1\+1\)\:2</p> 
                    
                valid <u>FIELDs</u> are
                <pre>
                            <b>full:</b> Full text search.
                            <b>defs:</b> Only finds symbol definitions.
                            <b>refs:</b> Only finds symbols.
                            <b>path:</b> path of the source file.
                            <b>hist:</b> History log comments
                </pre>
                <p>
                    the term(phrases) can be boosted(making it more relevant) using a caret <b>^</b> , e.g. help^4 opengrok - will make term help boosted
                </p>
                <u><b>Examples:</b></u>
                <pre>
                    
                            To find where setResourceMonitors is defined:
                            <a href="search?q=&defs=setResourceMonitors">defs:setResourceMonitors</a>
                                
                            To find files that use sprintf in usr/src/cmd/cmd-inet/usr.sbin/:
                            <a href="search?refs=sprintf&path=usr%2Fsrc%2Fcmd%2Fcmd-inet%2Fusr.sbin%2F">refs:sprintf path:usr/src/cmd/cmd-inet/usr.sbin</a>
                                
                            To find assignments to variable Asign:
                            <a href="search?q=%22asign+%3D+%22">"Asign="</a>
                                
                            To find Makefiles where pstack binary is being built:
                            <a href="search?q=pstack&path=Makefile">pstack path:Makefile</a>
                                
                            to search for phrase "Bill Joy":
                            <a href="search?q=%22Bill+Joy%22">"Bill Joy"</a>
                                
                            To find perl files that do not use /usr/bin/perl but something else:
                            <a href="search?q=-%22%2Fusr%2Fbin%2Fperl%22+%2B%22%2Fbin%2Fperl%22">-"/usr/bin/perl" +"/bin/perl"</a>

                            To find all strings begining with foo use the wildcard:
                            <a href="search?q=foo*">foo*</a>
                </pre>
                    
                <p>Opengrok search is powered by <a href="http://lucene.apache.org/">lucene</a>, for more detail on query syntax refer to lucene docs.</p>
            </p>
        </div>
        <%@include file="foot.jspf"%>
