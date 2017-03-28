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

Copyright (c) 2005, 2017, Oracle and/or its affiliates. All rights reserved.

Portions Copyright 2011 Jens Elkner.

--%><%@ page session="false" errorPage="error.jsp" import="
org.opensolaris.opengrok.web.PageConfig,
org.opensolaris.opengrok.search.SearchEngine"
%><%
/* ---------------------- help.jsp start --------------------- */
{
    PageConfig cfg = PageConfig.get(request);
    cfg.checkSourceRootExistence();
    cfg.setTitle("OpenGrok Help");
}
%><%@

include file="httpheader.jspf"

%>
<body>
    <div id="page">
        <div id="whole_header">
            <div id="header"><%@

include file="pageheader.jspf"

            %></div>
            <div id="Masthead">Help page</div>
        </div>
        <div id="sbar">
            <div id="menu"><%@

include file="menu.jspf"

%>
            </div>
        </div>
        <div id="help">

<h4>Examples:</h4>
<pre class="example">

To find where setResourceMonitors is defined:
<a href="search?q=&amp;defs=setResourceMonitors">defs:setResourceMonitors</a>

To find files that use sprintf in usr/src/cmd/cmd-inet/usr.sbin/:
<a href="search?refs=sprintf&amp;path=usr%2Fsrc%2Fcmd%2Fcmd-inet%2Fusr.sbin%2F"
>refs:sprintf path:usr/src/cmd/cmd-inet/usr.sbin</a>

To find assignments to variable foo:
<a href="search?q=%22foo+%3D%22">"foo ="</a>

To find Makefiles where pstack binary is being built:
<a href="search?q=pstack&amp;path=Makefile">pstack path:Makefile</a>

to search for phrase "Bill Joy":
<a href="search?q=%22Bill+Joy%22">"Bill Joy"</a>

To find perl files that do not use /usr/bin/perl but something else:
<a href="search?q=-%22%2Fusr%2Fbin%2Fperl%22+%2B%22%2Fbin%2Fperl%22"
>-"/usr/bin/perl" +"/bin/perl"</a>

To find all strings beginning with foo use the wildcard:
<a href="search?q=foo*">foo*</a>

To find all files which have . c in their name (dot is a token!):
<a href="search?path=%22. c%22">". c"</a>

To find all files which start with "ma" and then have only alphabet characters do:
<a href="search?path=/ma[a-zA-Z]*/">path:/ma[a-zA-Z]*/</a>

To find all main methods in all files analyzed by C analyzer (so .c, .h, ...) do:
<a href="search?q=main&type=c">main type:c</a>
</pre>

<h4>More info:</h4>
A <dfn>Query</dfn> is a series of clauses. A clause may be prefixed by:
<ul>
    <li>a plus "<b>+</b>" or a minus "<b>-</b>" sign, indicating that the clause
        is required or prohibited respectively; or</li>
    <li>a <dfn>term</dfn> followed by a colon "<b>:</b>", indicating the
        <dfn>field</dfn> to be searched. This enables one to construct queries
        which search multiple <dfn>fields</dfn>.</li>
</ul>
<p>A <dfn>clause</dfn> may be either:</p>
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
<p>Regular Expression, Wildcard, Fuzzy, Proximity &amp; Range Searches:</p>
<ul>
    <li>to perform a regular expression search use the "<b>/</b>" enclosure,
        e.g.  /[mb]an/ - will search for man or for ban;<br/>
        NOTE: path field search escapes "/" by default, so it only supports
        regexps when the search string <u>starts and ends</u> with "/".<br/>
        More info can be found on <a href="http://lucene.apache.org/core/<%=SearchEngine.LUCENE_VERSION_HELP%>/core/org/apache/lucene/util/automaton/RegExp.html?is-external=true">Lucene regexp page</a>.
    </li>
    <li>to perform a single character wildcard search use the "<b>?</b>" symbol,
        e.g.  te?t</li>
    <li>to perform a multiple character wildcard search use the "<b>*</b>"
        symbol, e.g. test* or te*t</li>
    <li>you can use a * or ? symbol as the first character of a search
        (unless not enabled using indexer option -a).</li>
    <li>to do a fuzzy search (find words similar in spelling, based on the
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
<p>
    Opengrok supports escaping special characters that are part of the query
    syntax. Current special characters are:<br/>
    <b>+ - &amp;&amp; || ! ( ) { } [ ] ^ " ~ * ? : \ / </b><br/>
    To escape these character use the \ before the character. For example to
    search for <b>(1+1):2</b> use the query: <b>\(1\+1\)\:2</b>
</p>
<p>
    NOTE on analyzers: Indexed words are made up of Alpha-Numeric and Underscore
    characters. One letter words are usually not indexed as symbols!<br/>
    Most other characters (including single and double quotes) are treated as
    "spaces/whitespace" (so even if you escape them, they will not be found, since
    most analyzers ignore them). <br/>
    The exceptions are: <b>@ $ % ^ &amp; = ? . :</b> which are mostly indexed as
    separate words.<br/>
    Because some of them are part of the query syntax, they must be escaped with a
    reverse slash as noted above.<br/>
    So searching for <b>\+1</b> or <b>\+ 1</b> will both find <b>+1</b> and <b>+ 1</b>.
</p>

<p>Valid <dfn>FIELDs</dfn> are</p>
<dl class="fields">
    <dt>full</dt>
    <dd>Search through all text tokens (words,strings,identifiers,numbers) in index.</dd>

    <dt>defs</dt>
    <dd>Only finds symbol definitions (where e.g. a variable (function, ...) is defined).</dd>

    <dt>refs</dt>
    <dd>Only finds symbols (e.g. methods, classes, functions, variables).</dd>

    <dt>path</dt>
    <dd>path of the source file (no need to use dividers, or if, then use "/" - Windows users, "\" is an escape key in Lucene query syntax! <br/>Please don't use "\", or replace it with "/").<br/>Also note that if you want just exact path, enclose it in "", e.g. "src/mypath", otherwise dividers will be removed and you get more hits.</dd>

    <dt>hist</dt>
    <dd>History log comments.</dd>

    <dt>type</dt>
    <dd>Type of analyzer used to scope down to certain file types (e.g. just C sources).<br/>Current mappings: <%=SearchHelper.getFileTypeDescriptions().toString()%></dd>
</dl>

<p>
    The term (phrases) can be boosted (making it more relevant) using a caret
    <b>^</b> , e.g. help^4 opengrok - will make term help boosted
</p>

<p>Opengrok search is powered by <a href="http://lucene.apache.org/">Lucene</a>,
for more detail on query syntax refer to <a href="http://lucene.apache.org/core/<%=SearchEngine.LUCENE_VERSION_HELP%>/queryparser/org/apache/lucene/queryparser/classic/package-summary.html#package.description">Lucene docs</a>.
</p>

<h4>Intelligence Window</h4>
<p>
Key "1" toggles <dfn>Intelligence Window</dfn>.  It gives the user many helper actions on the last symbol pointed by the mouse cursor.
</p>
<img src="<%= PageConfig.get(request).getCssDir() %>/img/intelli-window.png"/>

<h5>Symbol Highlighting</h5>
<p>
Key "2" toggles highlighting of the last symbol pointed by the mouse cursor.  This functionality is also accessible via the <dfn>Intelligence Window</dfn>.
</p>
<p>
Key "3" toggles unhighlighting all symbols. This functionality is also accessible via the <dfn>Intelligence Window</dfn>.
</p>
<img src="<%= PageConfig.get(request).getCssDir() %>/img/symbol-highlighting.png"/>

<p>
    You can close the intelligence window either by mouse in the right upper corner or by keyboard with "Esc" key.
</p>

<h5>Symbol jumping</h5>
<p>
By 'n' for next and 'b' for back you can jump between the symbols easily only with keyboard. When there is no symbol highlighted then the jump
is made to the next symbol in the file from the current one. If you have highlighted a specific symbol then the jump is done only among the highlighted symbols.
</p>

<h4>Diff jumper</h4>

<p>
The OpenGrok also provides an easy way how to jump through the large diffs finding the interesting pieces of code. In the diff mode you can enable diff jumper by hitting the "jumper" button.
</p>
<img src="<%= PageConfig.get(request).getCssDir() %>/img/diff-jumper.png"/>

<h5>Mouse and keyboard navigation</h5>
<p>
You can then use your mouse to intuitively navigate yourself through the diff. Also there is a convenient shortcut for moving on your keyboard,
you can use 'n' for next and 'b' for back to jump over to the next chunk. This is available even when the jumper window is not opened.
</p>
<img src="<%= PageConfig.get(request).getCssDir() %>/img/diff-jumping.png"/>

        </div>
<%
/* ---------------------- help.jsp end --------------------- */
%><%@

include file="foot.jspf"

%>
