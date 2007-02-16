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

--%><%@ page import = "javax.servlet.*,
java.lang.Integer,
javax.servlet.http.*,
java.util.Hashtable,
java.util.Vector,
java.util.Date,
java.util.ArrayList,
java.util.List,
java.lang.*,
java.io.*,
java.io.StringReader,
org.opensolaris.opengrok.analysis.*,
org.opensolaris.opengrok.search.*,
org.opensolaris.opengrok.web.*,
org.opensolaris.opengrok.web.*,
org.opensolaris.opengrok.search.context.*,
org.opensolaris.opengrok.configuration.*,
org.apache.lucene.spell.*,
org.apache.lucene.analysis.*,
org.apache.lucene.document.*,
org.apache.lucene.index.*,
org.apache.lucene.search.*,
org.apache.lucene.queryParser.*"
%><%@ page session="false" %><%@ page errorPage="error.jsp" %><%
Date starttime = new Date();
String q    = request.getParameter("q");
String defs = request.getParameter("defs");
String refs = request.getParameter("refs");
String hist = request.getParameter("hist");
String path = request.getParameter("path");

%>
<%@ include file="projects.jspf" %>
<%

Hits hits = null;
String errorMsg = null;
String context = request.getContextPath();

if( q!= null && q.equals("")) q = null;
if( defs != null && defs.equals("")) defs = null;
if( refs != null && refs.equals("")) refs = null;
if( hist != null && hist.equals("")) hist = null;
if( path != null && path.equals("")) path = null;
if (project == null) project = null;

if (q != null || defs != null || refs != null || hist != null || path != null) {
    Searcher searcher = null;		    //the searcher used to open/search the index
    IndexReader ireader = null; 	    //the reader used to open/search the index
    Query query = null, defQuery = null; 		    //the Query created by the QueryParser
    
    int start = 0;		       //the first index displayed on this page
    int max    = 25;			//the maximum items displayed on this page
    int thispage = 0;			    //used for the for/next either max or
    String moreUrl = null;
    CompatibleAnalyser analyzer = new CompatibleAnalyser();
    String qstr = "";
    String result = "";
    try {
        String DATA_ROOT = env.getDataRootPath();
        if(DATA_ROOT.equals("")) {
            throw new Exception("DATA_ROOT parameter is not configured in web.xml!");
        }
        File data_root = new File(DATA_ROOT);
        if(!data_root.isDirectory()) {
            throw new Exception("DATA_ROOT parameter in web.xml does not exist or is not a directory!");
        }
        ireader = IndexReader.open(DATA_ROOT + "/index");
        searcher = new IndexSearcher(ireader);
        //String date = request.getParameter("date");
        try {
            start = Integer.parseInt(request.getParameter("start"));	//parse the max results first
            max = Integer.parseInt(request.getParameter("n"));      //then the start index
            if(max < 0 || (max % 10 != 0) || max > 50) max = 25;
            if(start < 0 ) start = 0;
        } catch (Exception e) {  }
        
        StringBuilder sb = new StringBuilder();
        if (q != null) {
            sb.append(q);
        }

        if (defs != null) {
            sb.append(" defs:(");
            sb.append(defs);
            sb.append(")");
        }
        
        if (refs != null) {
            sb.append(" refs:(");
            sb.append(refs);
            sb.append(")");
        }

        if (path != null) {
            sb.append(" path:(");
            sb.append(path);
            sb.append(")");
        }

        if (hist != null) {
            sb.append(" hist:(");
            sb.append(hist);
            sb.append(")");
        }
        
        if (project != null) {
            sb.append(" (");

            boolean first = true;
            for (String s : project.split(" ")) {
                if (first) {
                    first = false;
                } else {
                    sb.append(" OR ");
                }
                sb.append("project:(");
                sb.append(s);
                sb.append(")");
            }

            sb.append(")");
        }
        
        qstr = sb.toString();
                                
        QueryParser qparser = new QueryParser("full", analyzer);
        qparser.setOperator(QueryParser.DEFAULT_OPERATOR_AND);
        query = qparser.parse(qstr); //parse the
        hits = searcher.search(query);
        thispage = max;
    } catch (BooleanQuery.TooManyClauses e) {
        errorMsg = "<b>Error:</b> Too many results for wildcard!";
    } catch (ParseException e) {
        errorMsg = "<b>Error:</b><br/>" + Util.Htmlize(qstr) + "<br/>" + Util.Htmlize(e.getMessage());
    } catch (FileNotFoundException e) {
        errorMsg = "<b>Error:</b> Index database not found";
    } catch (Exception e) {
        errorMsg = "<b>Error:</b> " + Util.Htmlize(e.getMessage());
    }
    if (hits != null && hits.length() == 1 && request.getServletPath().equals("/s")) {
        if (query != null && query instanceof TermQuery) {
            response.sendRedirect(context + "/xref" + hits.doc(0).get("path")
            + "#" + ((TermQuery)query).getTerm().text());
        } else {
            response.sendRedirect(context + "/xref" + hits.doc(0).get("path"));
        }
    } else {
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
    <%@ include file="menu.jspf"%>
<!-- table cellpadding="0" cellspacing="0" border="0" width="100%">
    <tr>
    <td valign="top"><br /> &nbsp;</td>
    <td align="left" valign="middle">
        <br/><form action="search" name="sbox">
                <table cellpadding="2" border="0" cellspacing="0">
                    <tr valign="top">
                        <td>
                            <table cellpadding="2" border="0" cellspacing="0">
                                <tr><td align="right"> Full&nbsp;Search </td><td><input class="q" name="q" size="45" style="width: 300px" value="<%=Util.formQuoteEscape(q)%>"/></td></tr>
                                <tr><td align="right"> Definition </td><td><input class="q" name="defs" size="25" style="width: 300px" value="<%=Util.formQuoteEscape(defs)%>"/></td></tr>
                                <tr><td align="right"> Symbol </td><td><input class="q" name="refs" size="25" style="width: 300px" value="<%=Util.formQuoteEscape(refs)%>"/></td></tr>
                                <tr><td align="right"> File&nbsp;Path </td><td><input class="q" name="path" size="25" style="width: 300px" value="<%=Util.formQuoteEscape(path)%>"/></td></tr>
                                <tr><td align="right"> History </td><td><input class="q" name="hist" size="25" style="width: 300px" value="<%=Util.formQuoteEscape(hist)%>"/></td></tr>
                            </table>
                        </td>
                        <%         if (hasProjects) { %>
                        <td>
                            Project:<br>
                            <select class="q" name="project" size="6" multiple="true" style="width: 300px">
                                <%                for (Project p : env.getProjects()) {
                                %><option value="<%=Util.formQuoteEscape(p.getPath())%>"<%=p.getPath().equals(project) ? " selected" : ""%>><%=Util.formQuoteEscape(p.getDescription())%></option><%
                                } %>
                            </select>
                        </td>
                        <%         } %>                                    
                    </tr>
            <tr><td></td><td>  &nbsp; <input class="submit" type="submit" value="Search"/> | <input class="submit"
            onClick="document.sbox.q.value='';document.sbox.defs.value='';document.sbox.refs.value='';document.sbox.path.value='';document.sbox.hist.value='';" type="button" value=" Clear "
            />  | <a href="help.html">Help</a></td></tr>
        </table></form>
    </td>
    <td valign="top" align="right"></td></tr>
</table -->
</div>
<div id="results">
<%
if( hits == null || errorMsg != null) {
	    	%><%=errorMsg%><%
            } else if (hits.length() == 0) {
                
                String ngramIndex = env.getDataRootPath() + "/spellIndex";
                if (ngramIndex != null && (new
                        File(ngramIndex+"/segments")).exists()) {
                    Date sstart = new Date();
                    IndexReader spellReader = IndexReader.open(ngramIndex);
                    IndexSearcher spellSearcher = new IndexSearcher(spellReader);

                    %><p><font color="#cc0000">Did you mean</font>:<%
                        String[] toks;
                        if(q != null) {
                            toks = q.split("[\t ]+");
                            if(toks != null){
                                for(int j=0; j<toks.length; j++) {
                                    if(toks[j].length() > 3) {
                                        String[] ret = NGramSpeller.suggestUsingNGrams(spellSearcher,toks[j].toLowerCase(), 3, 4, 3, 5, 3, 2, 0, null, false);
                                        for(int i = 0;i < ret.length; i++) {
					%> <a href=search?q=<%=ret[i]%>><%=ret[i]%></a> &nbsp; <%
                                        }
                                    }
                                }
                            }
                        }
                        if(refs != null) {
                            toks = refs.split("[\t ]+");
                            if(toks != null){
                                for(int j=0; j<toks.length; j++) {
                                    if(toks[j].length() > 3) {
                                        String[] ret = NGramSpeller.suggestUsingNGrams(spellSearcher,toks[j].toLowerCase(),3, 4, 3, 5, 3, 2, 0, null, false);
                                        for(int i = 0;i < ret.length; i++) {
					%> <a href=search?q=<%=ret[i]%>><%=ret[i]%></a> &nbsp;  <%
                                }
                                }
                        }
                        }
                        }
                        if(defs != null) {
                            toks = defs.split("[\t ]+");
                            if(toks != null){
                                for(int j=0; j<toks.length; j++) {
                                    if(toks[j].length() > 3) {
                                        String[] ret = NGramSpeller.suggestUsingNGrams(spellSearcher,toks[j].toLowerCase(),3, 4, 3, 5, 3, 2, 0, null, false);
                                        for(int i = 0;i < ret.length; i++) {
					%> <a href=search?q=<%=ret[i]%>><%=ret[i]%></a> &nbsp;  <%
                                        }
                                    }
                                }
                            }
                        }
                        spellSearcher.close();
                        spellReader.close();
                        %></p><%
                }
		%><p> Your search  <b><%=query.toString()%></b> did not match any files.
                    <br />
                    Suggestions:<br/><blockquote>- Make sure all terms are spelled correctly.<br/>
                        - Try different keywords.<br/>
                        - Try more general keywords.<br/>
                        - Use 'wil*' cards if you are looking for partial match.
                    </blockquote>
		</p><%
            } else { // We have a lots of results to show
                StringBuilder slider = null;
                if ( max < hits.length()) {
                    if((start + max) < hits.length()) {
                        thispage = max;
                    } else {
                        thispage = hits.length() - start;
                    }
                    String url =   (q == null ? "" : "&amp;q=" + Util.URIEncode(q) ) +
                            (defs == null ? "" : "&amp;defs=" + Util.URIEncode(defs)) +
                            (refs == null ? "" : "&amp;refs=" + Util.URIEncode(refs)) +
                            (path == null ? "" : "&amp;path=" + Util.URIEncode(path)) +
                            (hist == null ? "" : "&amp;hist=" + Util.URIEncode(hist));
                    
                    slider = new StringBuilder();
                    int labelStart =1;
                    int sstart = start - max* (start / max % 10 + 1) ;
                    if(sstart < 0) {
                        sstart = 0;
                        labelStart = 1;
                    } else {
                        labelStart = sstart/max + 1;
                    }
                    int label = labelStart;
                    int labelEnd = label + 11;
                    String arr;
                    for(int i=sstart; i<hits.length() && label <= labelEnd; i+= max) {
                        if (i <= start && start < i+ max) {
                            slider.append("<span class=\"sel\">" + label + "</span>");
                        } else {
                            if(label == labelStart && label != 1) {
                                arr = "&lt;&lt";
                            } else if(label == labelEnd && i < hits.length()) {
                                arr = "&gt;&gt;";
                            } else {
                                arr = label < 10 ? " " + label : String.valueOf(label);
                            }
                            slider.append("<a class=\"more\" href=\"search?n=" + max + "&amp;start=" + i + url + "\">"+
                                    arr + "</a>");
                        }
                        label++;
                    }
                } else {
                    thispage = hits.length() - start;      // set the max index to max or last
                }
		%>&nbsp; &nbsp; Searched <b><%=query.toString()%></b> (Results <b><%=start+1%> -
		<%=thispage+start%></b> of <b><%=hits.length()%></b>) <p><%=slider != null ?
                    slider.toString(): ""%></p>
		<table width="100%" cellpadding="3" cellspacing="0" border="0"><%
                
                Context sourceContext = null;
                Summarizer summer = null;
                if (query != null) {
                    try{
                        sourceContext = new Context(query);
                        if(sourceContext != null)
                            summer = new Summarizer(query, analyzer);
                    } catch (Exception e) {
                        
                    }
                }
                
                HistoryContext historyContext = null;
                try {
                    historyContext = new HistoryContext(query);
                } catch (Exception e) {
                }
                EftarFileReader ef = null;
                try{
                    ef = new EftarFileReader(env.getDataRootPath() + "/index/dtags.eftar");
                } catch (Exception e) {
                }
                Results.prettyPrintHTML(hits, start, start+thispage,
                        out,
                        sourceContext, historyContext, summer,
                        context + "/xref",
                        context + "/more",
                        env.getSourceRootPath(),
                        env.getDataRootPath(),
                        ef);
                if(ef != null) {
                    try{
                    ef.close();
                    } catch (IOException e) {
                    }
                }
		%></table><br/>
		<b> Completed in <%=(new Date()).getTime() - starttime.getTime()%> milliseconds </b> <br/>
		<%=slider != null ? "<p>" + slider + "</p>" : ""%>
		<%
            }
	    %><br/></div><%@include file="foot.jsp"%><%
    }
    if (ireader != null)
        ireader.close();
} else { // Entry page show the map
    response.sendRedirect(context + "/index.jsp");
}
%>
