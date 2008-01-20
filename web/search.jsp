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
org.opensolaris.opengrok.index.IndexDatabase,
org.opensolaris.opengrok.search.*,
org.opensolaris.opengrok.web.*,
org.opensolaris.opengrok.search.context.*,
org.opensolaris.opengrok.configuration.*,
org.apache.lucene.search.spell.LuceneDictionary,
org.apache.lucene.search.spell.SpellChecker,
org.apache.lucene.store.FSDirectory,
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

%><%@ include file="projects.jspf" %><%
String sort = null;

final String LASTMODTIME = "lastmodtime";
final String RELEVANCY = "relevancy";

Cookie[] cookies = request.getCookies();
if (cookies != null) {
    for (Cookie cookie : cookies) {
        if (cookie.getName().equals("OpenGrok/sorting")) {
            sort = cookie.getValue();
            if (!LASTMODTIME.equals(sort) && !RELEVANCY.equals(sort)) {
                sort = null;
            }
            break;
        }
    }
}

String sortParam = request.getParameter("sort");
if (sortParam != null) {
    if (LASTMODTIME.equals(sortParam)) {
        sort = LASTMODTIME;
    } else if (RELEVANCY.equals(sortParam)) {
        sort = RELEVANCY;
    }
    if (sort != null) {
        Cookie cookie = new Cookie("OpenGrok/sorting", sort);
        response.addCookie(cookie);
    }
}

Hits hits = null;
String errorMsg = null;

if( q!= null && q.equals("")) q = null;
if( defs != null && defs.equals("")) defs = null;
if( refs != null && refs.equals("")) refs = null;
if( hist != null && hist.equals("")) hist = null;
if( path != null && path.equals("")) path = null;
if (project != null && project.equals("")) project = null;

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
        //String date = request.getParameter("date");
        try {
            start = Integer.parseInt(request.getParameter("start"));	//parse the max results first
            max = Integer.parseInt(request.getParameter("n"));      //then the start index
            if(max < 0 || (max % 10 != 0) || max > 50) max = 25;
            if(start < 0 ) start = 0;
        } catch (Exception e) {  }
        
        qstr = Util.buildQueryString(q, defs, refs, path, hist);
                                
        QueryParser qparser = new QueryParser("full", analyzer);
        qparser.setDefaultOperator(QueryParser.AND_OPERATOR);
        qparser.setAllowLeadingWildcard(env.isAllowLeadingWildcard());

        query = qparser.parse(qstr); //parse the

        File root = new File(RuntimeEnvironment.getInstance().getDataRootFile(),
                "index");

        if (RuntimeEnvironment.getInstance().hasProjects()) {
            if (project == null) {
                errorMsg = "<b>Error:</b> You must select a project!";
            } else {
                root = new File(root, project);
            }
        }

        if (errorMsg == null) {
            ireader = IndexReader.open(root);
            searcher = new IndexSearcher(ireader);

            if ("lastmodtime".equals(sort)) {
                hits = searcher.search(query, new Sort("date", true));
            } else {
                hits = searcher.search(query);
            }
        }
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
    // @TODO fix me. I should try to figure out where the exact hit is instead
    // of returning a page with just _one_ entry in....
    if (hits != null && hits.length() == 1 && request.getServletPath().equals("/s") && (query != null && query instanceof TermQuery)) {
        String preFragmentPath = Util.URIEncodePath(context + "/xref" + hits.doc(0).get("path"));
        String fragment = Util.URIEncode(((TermQuery)query).getTerm().text());
        
        StringBuilder url = new StringBuilder(preFragmentPath);
        url.append("#");
        url.append(fragment);

        response.sendRedirect(url.toString());
    } else {
         String pageTitle = "Search";
         RuntimeEnvironment environment = RuntimeEnvironment.getInstance();
         environment.register();
	    %><%@ include file="httpheader.jspf" %>
<body>
<div id="page">
    <div id="header"><%@ include file="pageheader.jspf" %></div>
<div id="Masthead"></div>
<div id="bar">
    <table border="0" width="100%"><tr><td><a href="<%=context%>" id="home">Home</a></td><td align="right"><%                
     {
        StringBuffer url = request.getRequestURL();
        url.append('?');
        String querys = request.getQueryString();
        if (querys != null) {
            int idx = querys.indexOf("sort=");
            if (idx == -1) {
                url.append(querys);
                url.append('&');
            } else {
                url.append(querys.substring(0, idx));
            }
        }
        url.append("sort=");
        
        if (sort == null || RELEVANCY.equals(sort)) {
           url.append(LASTMODTIME);
           %><b>Sort by relevance</b> <a href="<%=url.toString()%>">Sort by last modified time</a><%
        } else {
           url.append(RELEVANCY);
           %><a href="<%=url.toString()%>">Sort by relevance</a> <b>Sort by last modified time</b><%
        }
      } %></td></tr></table>
</div>
<div id="menu">
   <%@ include file="menu.jspf"%>
</div>
<div id="results">
<%
if( hits == null || errorMsg != null) {
	    	%><%=errorMsg%><%
            } else if (hits.length() == 0) {
                File spellIndex = new File(env.getDataRootPath(), "spellIndex");

                if (RuntimeEnvironment.getInstance().hasProjects()) {
                    spellIndex = new File(spellIndex, project);
                }
                                              
                if (spellIndex.exists()) {
                    FSDirectory spellDirectory = FSDirectory.getDirectory(spellIndex);
                    SpellChecker checker = new SpellChecker(spellDirectory);

                    Date sstart = new Date();

                    %><p><font color="#cc0000">Did you mean</font>:<%
                        String[] toks;
                        if(q != null) {
                            toks = q.split("[\t ]+");
                            if(toks != null){
                                for(int j=0; j<toks.length; j++) {
                                    if(toks[j].length() > 3) {
                                        String[] ret = checker.suggestSimilar(toks[j].toLowerCase(), 3);
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
                                        String[] ret = checker.suggestSimilar(toks[j].toLowerCase(), 3);
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
                                        String[] ret = checker.suggestSimilar(toks[j].toLowerCase(), 3);
                                        for(int i = 0;i < ret.length; i++) {
					%> <a href=search?q=<%=ret[i]%>><%=ret[i]%></a> &nbsp;  <%
                                        }
                                    }
                                }
                            }
                        }
                        spellDirectory.close();
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
                            (hist == null ? "" : "&amp;hist=" + Util.URIEncode(hist)) +
                            (sort == null ? "" : "&amp;sort=" + Util.URIEncode(sort));
                    
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
		<%=thispage+start%></b> of <b><%=hits.length()%></b>) sorted by <%=sort%> <p><%=slider != null ?
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
	    %><br/></div><%@include file="foot.jspf"%><%
    }
    if (ireader != null)
        ireader.close();
} else { // Entry page show the map
    response.sendRedirect(context + "/index.jsp");
}
%>
