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

Portions Copyright 2011 Jens Elkner.

--%><%@ page session="false" errorPage="error.jsp" import="
org.opensolaris.opengrok.web.PageConfig"
%><%
/* ---------------------- help.jsp start --------------------- */
{
    cfg = PageConfig.get(request);
    cfg.setTitle("OpenGrok Help");
%><%@

include file="httpheader.jspf"

%>
<body>
    <div id="page">
        <div id="whole_header">
            <div id="header"><%@

include file="pageheader.jspf"

            %></div>
            <div id="Masthead"></div>
        </div>
        <div id="sbar">
            <div id="menu"><%@

include file="menu.jspf"

%>
            </div>
        </div>
        <div id="help">
<p>
A <dfn>Query</dfn> is a series of clauses. A clause may be prefixed by:</p>
<ul>
    <li>a plus "<b>+</b>" or a minus "<b>-</b>" sign, indicating that the clause
        is required or prohibited respectively; or</li>
    <li>a <dfn>term</dfn> followed by a colon "<b>:</b>", indicating the
        <dfn>field</dfn> to be searched. This enables one to construct queries
        which search multiple <dfn>fields</dfn>.</li>
</ul>
<p>A clause may be either:</p>
<ul>
    <li>a <dfn>term</dfn>, indicating all the documents that contain this term;
        or</li>
    <li>a <dfn>phrase</dfn> - group of words surrounded by double quotes
        <b>" "</b>, e.g. "hello dolly"  </li>
    <li>a nested query, enclosed in parentheses "<b>(</b>" "<b>)</b>" (also
        called query/field <dfn>grouping</dfn>) . Note that this may be used
        with a +/- prefix to require any of a set of terms. </li>
    <li>boolean <dfn>operators</dfn> which allow terms to be combined through
        logic operators. Supported are <b>AND</b>(<b>&amp;&amp;</b>), "<b>+</b>",
        <b>OR</b>(<b>||</b>), <b>NOT</b>(<b>!</b>) and "<b>-</b>" (Note: they
        must be ALL CAPS).</li>
</ul>
<p>Wildcard, Fuzzy, Proximity &amp; Range Searches:</p>
<ul>
    <li>to perform a single character wildcard search use the "<b>?</b>" symbol,
        e.g.  te?t</li>
    <li>to perform a multiple character wildcard search use the "<b>*</b>"
        symbol, e.g. test* or te*t</li>
    <li>you cannot use a * or ? symbol as the first character of a search
        (unless enabled using indexer option -a).</li>
    <li>to do a fuzzy search(find words similar in spelling, based on the
        Levenshtein Distance, or Edit Distance algorithm) use the tilde,
        "<b>~</b>", e.g. rcs~ </li>
    <li>to do a proximity search use the tilde, "~", symbol at the end of a
        Phrase. For example to search for a "opengrok" and "help" within 10
        words of each other enter: "opengrok help"~10 </li>
    <li>range queries allow one to match documents whose field(s) values are
        between the lower and upper bound specified by the Range Query. Range
        Queries can be inclusive or exclusive of the upper and lower bounds.
        Sorting is done lexicographically. Inclusive queries are denoted by
        square brackets <b>[ ]</b> , exclusive by curly brackets <b>{ }</b>.
        For example: title:{Aida TO Carmen} - will find all documents between
        Aida to Carmen, exclusive of Aida and Carmen. </li>
</ul>

<a id="escaping"><dfn>Escaping special characters:</dfn></a>
<p>Opengrok supports escaping special characters that are part of the query
    syntax. Current special characters are:<br/>
    <b>+ - &amp;&amp; || ! ( ) { } [ ] ^ " ~ * ? : \ </b><br/>
To escape these character use the \ before the character. For example to search
for <b>(1+1):2</b> use the query: <b>\(1\+1\)\:2</b>
</p>
<p>NOTE on analyzers: Indexed words are made up of Alpha-Numeric and Underscore
characters. One letter words are usually not indexed as symbols!<br/>
Most other characters(including single and double quotes) are treated as
"spaces/whitespace"(so even if you escape them, they will not be found, since
most analyzers ignore them). <br/>
The exceptions are: <b>@ $ % ^ &amp; = ? . :</b> which are mostly indexed as
separate words.<br/>
Because some of them are part of the query syntax, they must be escaped with a
reverse slash as noted above.<br/>
So searching for <b>\+1</b> or <b>\+ 1</b> will both find <b>+1</b> and <b>+ 1</b>.
</p>

<p>valid <dfn>FIELDs</dfn> are</p>
    <dl class="fields">
<dt>full</dt>
<dd>Search through all text tokens(words,strings,identifiers,numbers) in index.</dd>

<dt>defs</dt>
<dd>Only finds symbol definitions.</dd>

<dt>refs</dt>
<dd>Only finds symbols.</dd>

<dt>path</dt>
<dd>path of the source file.</dd>

<dt>hist</dt>
<dd>History log comments.</dd>
    </dl>

<p>
the term(phrases) can be boosted (making it more relevant) using a caret
<b>^</b> , e.g. help^4 opengrok - will make term help boosted
</p>

<dfn><b>Examples:</b></dfn>
<pre class="example">

To find where setResourceMonitors is defined: <a
href="search?q=&amp;defs=setResourceMonitors">defs:setResourceMonitors</a>

To find files that use sprintf in usr/src/cmd/cmd-inet/usr.sbin/:
<a href="search?refs=sprintf&amp;path=usr%2Fsrc%2Fcmd%2Fcmd-inet%2Fusr.sbin%2F"
>refs:sprintf path:usr/src/cmd/cmd-inet/usr.sbin</a>

To find assignments to variable Asign:
<a href="search?q=%22asign+%3D+%22">"Asign="</a>

To find Makefiles where pstack binary is being built:
<a href="search?q=pstack&amp;path=Makefile">pstack path:Makefile</a>

to search for phrase "Bill Joy":
<a href="search?q=%22Bill+Joy%22">"Bill Joy"</a>

To find perl files that do not use /usr/bin/perl but something else:
<a href="search?q=-%22%2Fusr%2Fbin%2Fperl%22+%2B%22%2Fbin%2Fperl%22"
>-"/usr/bin/perl" +"/bin/perl"</a>

To find all strings begining with foo use the wildcard:
<a href="search?q=foo*">foo*</a>

To find all files which have . c in their name(dot is a token!):
<a href="search?path=%22. c%22">". c"</a>

</pre>

<p>Opengrok search is powered by <a href="http://lucene.apache.org/"
>lucene</a>, for more detail on query syntax refer to lucene docs.</p>
        </div>
<%
}
/* ---------------------- help.jsp end --------------------- */
%><%@

include file="foot.jspf"

%>