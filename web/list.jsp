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
java.lang.*,
javax.servlet.http.*,
java.util.*,
java.io.*,
org.opensolaris.opengrok.analysis.*,
org.opensolaris.opengrok.index.*,
org.opensolaris.opengrok.analysis.FileAnalyzer.Genre,
org.opensolaris.opengrok.web.*,
org.opensolaris.opengrok.history.*
"
%><%@include file="mast.jsp"%><%
String rev = null;
if(!isDir && ef != null) {
        try {
            ef.close();
        } catch (IOException e) {
        }
        ef = null;
}

if (valid) {
    if (isDir) {
// If requesting a Directory listing -------------
        DirectoryListing dl = new DirectoryListing(ef);
        String[] files = resourceFile.list();
        if (files != null) {
            Arrays.sort(files, String.CASE_INSENSITIVE_ORDER);
            ArrayList readMes = dl.listTo(resourceFile, out, path, files);
            if(readMes != null && readMes.size() > 0) {
                File xdir = new File(environment.getDataRootPath() + "/xref" + path);
                if(xdir.exists() && xdir.isDirectory()) {
                    char[] buf = new char[8192];
                    for(int i = 0; i< readMes.size(); i++) {
                        try {
                        BufferedReader br = new BufferedReader(new FileReader(new File(xdir, (String)readMes.get(i))));
                        int len = 0;
		    %><h3><%=(String)readMes.get(i)%></h3><div id="src"><pre><%
                    while((len = br.read(buf)) > 0) {
                            out.write(buf, 0, len);
                    }
		    %></pre></div><%
                    br.close();
                        } catch(IOException e) {
                        }
                    }
                }
            }
        }
    } else if ((rev = request.getParameter("r")) != null && !rev.equals("")) {
// Else if requesting a previous revision -------------
        if(noHistory) {
                    response.sendError(404, "Revision not found");
        } else if (rev.matches("^[0-9]+(\\.[0-9]+)*$")) {
            Class a = AnalyzerGuru.find(basename);
            Genre g = AnalyzerGuru.getGenre(a);
            if (g == Genre.PLAIN|| g == Genre.HTML || g == null) {
                InputStream in = null;
                try{
                    in = HistoryGuru.getInstance().getRevision(resourceFile.getParent(), basename, rev);
                } catch (Exception e) {
                    response.sendError(404, "Revision not found");
                    return;
                }
                if(in != null) {
                    try {
                        if (g == null) {
                            a = AnalyzerGuru.find(in);
                            g = AnalyzerGuru.getGenre(a);
                        }
                        if (g == Genre.DATA || g == Genre.XREFABLE || g == null) {
		%> <div id="src">Binary file [Click <a href="<%=context%>/raw<%=path%>?r=<%=rev%>">here</a> to download] </div><%
                        } else {
		%><div id="src"><span class="pagetitle"><%=basename%> revision <%=rev%> </span><pre>
<%
if (g == Genre.PLAIN) {
    Annotation annotation = annotate ?
        HistoryGuru.getInstance().annotate(resourceFile, rev) : null;
    AnalyzerGuru.writeXref(a, in, out, annotation);
} else if (g == Genre.IMAGE) {
			%><img src="<%=context%>/raw<%=path%>?r=<%=rev%>"/><%
} else if (g == Genre.HTML) {
    char[] buf = new char[8192];
    Reader br = new InputStreamReader(in);
    int len = 0;
    while((len = br.read(buf)) > 0) {
        out.write(buf, 0, len);
    }
} else {
		    %> Click <a href="<%=context%>/raw<%=path%>?r=<%=rev%>">download <%=basename%></a><%
}
                        }
                    } catch (IOException e) {
        %> <h3 class="error">IO Error</h3> <p> <%=e.getMessage() %> </p> <%
           
                    }
      %></pre></div><%
      in.close();
                } else {
    	%> <h3 class="error">Error reading file</h3> <%
                }
                
            } else if(g == Genre.IMAGE) {
	%><div id="src"><img src="<%=context%>/raw<%=path%>?r=<%=rev%>"/></div><%
            } else {
    %><div id="src"> Binary file [Click <a href="<%=context%>/raw<%=path%>?r=<%=rev%>">here</a> to download] </div><%
            }
            
        } else {
	%><h3 class="error">Error: Invalid Revision Number!</h3><%
        }
    } else {
// requesting cross referenced file -------------
        
        String xrefSource = environment.getDataRootPath() + "/xref";
        String resourceXFile = xrefSource + path;
        File xrefFile = new File(resourceXFile);
        if(xrefFile.exists() && !annotate) {
            char[] buf = new char[8192];
            BufferedReader br = new BufferedReader(new FileReader(resourceXFile));
            int len = 0;
        %><div id="src"><pre><%
        while((len = br.read(buf)) > 0) {
                out.write(buf, 0, len);
        }
        %></pre></div><%
        br.close();
        } else {
            BufferedInputStream bin = new BufferedInputStream(new FileInputStream(resourceFile));
            Class a = AnalyzerGuru.find(basename);
            Genre g = AnalyzerGuru.getGenre(a);
            if(g == null) {
                a = AnalyzerGuru.find(bin);
                g = AnalyzerGuru.getGenre(a);
            }
            if (g == Genre.IMAGE) {
        	%><div id="src"><img src="<%=context%>/raw<%=path%>"/></div><%
            } else if( g == Genre.HTML) {
                char[] buf = new char[8192];
                Reader br = new InputStreamReader(bin);
                int len = 0;
                while((len = br.read(buf)) > 0) {
                    out.write(buf, 0, len);
                }
            } else if(g == Genre.PLAIN) {
            %><div id="src"><pre><%
            Annotation annotation = annotate ?
                HistoryGuru.getInstance().annotate(resourceFile, rev) : null;
            AnalyzerGuru.writeXref(a, bin, out, annotation);
            %></pre></div><%
            } else {
	    %> Click <a href="<%=context%>/raw<%=path%>">download <%=basename%></a><%
        }
        }
    }
%><%@include file="foot.jspf"%><%
}
if(ef != null) {
    try {
        ef.close();
    } catch (IOException e) {
    }
}
%>
