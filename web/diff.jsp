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

ident	"@(#)diff.jsp 1.2     05/12/01 SMI"

--%><%@ page import = "javax.servlet.*,
java.lang.*,
javax.servlet.http.*,
java.util.*,
java.io.*,
java.text.*,
java.net.URLDecoder,
org.opensolaris.opengrok.analysis.*,
org.opensolaris.opengrok.analysis.FileAnalyzer.Genre,
org.opensolaris.opengrok.web.*,
org.opensolaris.opengrok.history.*,
org.apache.commons.jrcs.diff.*"
%><%@include file="mast.jsp"%><%!

String readableLine(int n) {
    if (n < 10) {
        return " " + n;
    } else {
        return String.valueOf(n);
    }
}

String[] diffline(String line1, String line2) {
    int i=0, j=1;
    int l1 =  line1.length();
    int l2 =  line2.length();
    String[] ret = new String[2];
    while (i < l1 && i < l2 && (line1.charAt(i) == line2.charAt(i)))
        i++;
    
    while (j < l1 && j < l2 && (line1.charAt(l1 - j) == line2.charAt(l2 - j)))
        j++;
    
    StringBuilder sb = new StringBuilder(line1);
    if(i <= l1 - j) {
        sb.insert(i, "<span class=\"d\">");
        sb.insert(sb.length()-j+1, "</span>");
        ret[0] = sb.toString();
    } else {
        ret[0] = line1;
    }
    
    if(i <= l2 - j) {
        sb = new StringBuilder(line2);
        sb.insert(i, "<span class=\"a\">");
        sb.insert(sb.length()-j+1, "</span>");
        ret[1] = sb.toString();
    } else {
        ret[1] = line2;
    }
    return ret;
}

%><%

if (valid) {
    String rp1 = request.getParameter("r1");
    String rp2 = request.getParameter("r2");
    String srcRoot = environment.getSourceRootFile().getAbsolutePath();

    String r1 = null;
    String r2 = null;
    File rpath1 = null;
    File rpath2 = null;
    String[] tmp;
    try {
        tmp = rp1.split("@");	
        if (tmp != null && tmp.length == 2) {
	    rpath1 = new File(srcRoot+URLDecoder.decode(tmp[0], "ISO-8859-1"));
	    r1 = URLDecoder.decode(tmp[1], "ISO-8859-1");
	}
    } catch (UnsupportedEncodingException e) {
    }

    try {
        tmp = rp2.split("@");
        if (tmp != null && tmp.length == 2) {
	    if (tmp != null && tmp.length == 2) {
		rpath2 = new File(srcRoot+URLDecoder.decode(tmp[0], "ISO-8859-1"));
		r2 = URLDecoder.decode(tmp[1], "ISO-8859-1");
	    }
	}
    } catch (UnsupportedEncodingException e) {
    }

    if (r1 == null || r2 == null || r1.equals("") || r2.equals("") || r1.equals(r2) || !r1.matches("^[0-9]+(\\.[0-9]+)*$") || !r2.matches("^[0-9]+(\\.[0-9]+)*$")) {
%><div class="src"><h3 class="error">Error:</h3>
    Please pick two revisions to compare the changed from the <a href="<%=context%>/history<%=path%>">history</a>
</div><%
// Error message ask to choose two versions from History log page with link to it
    } else {
        Genre g = AnalyzerGuru.getGenre(basename);
        if (g == Genre.PLAIN || g == null || g == Genre.DATA || g == Genre.HTML) {
            InputStream in1 = null;
            InputStream in2 = null;
            try{
                in1 = HistoryGuru.getInstance().getRevision(rpath1.getParent(), rpath1.getName(), r1);
                in2 = HistoryGuru.getInstance().getRevision(rpath2.getParent(), rpath2.getName(), r2);
            } catch (Exception e) {
		%> <h3 class="error">Error opening revisions!</h3> <%
            }
            try {
                if (in1 != null && in2 != null) {
                    g = AnalyzerGuru.getGenre(basename);
                    if (g == null) {
                        g = AnalyzerGuru.getGenre(in1);
                    }
                    if (g == Genre.IMAGE) {
				%> <div id="difftable">
				<table rules="cols" cellpadding="5"><tr><th><%=basename%> (revision <%=r1%>)</th><th><%=basename%> (revision <%=r2%>)</th></tr>
				<tr><td><img src="<%=context%>/raw<%=path%>?r=<%=r1%>"/></td><td><img src="<%=context%>/raw<%=path%>?r=<%=r2%>"/></td></tr></table></div><%
                    } else if (g == Genre.PLAIN || g == Genre.HTML) {
//--------Do THE DIFFS------------
                        ArrayList l1 = new ArrayList();
                        String line;
                        BufferedReader reader1 = new BufferedReader(new InputStreamReader(in1));
                        BufferedReader reader2 = new BufferedReader(new InputStreamReader(in2));
                        
                        while ((line = reader1.readLine()) != null) {
                            l1.add(line);
                        }
                        
                        ArrayList l2 = new ArrayList();
                        while ((line = reader2.readLine()) != null) {
                            l2.add(line);
                        }
                        Object[] file1 = l1.toArray();
                        Object[] file2 = l2.toArray();
                        
                        Revision rev = Diff.diff(file1, file2);
                        
                        if(rev.size() == 0) {
	%><b>No differences found!</b><%
                        } else {
                            
                            int ln1 = 0;
                            int ln2 = 0;
                            
                            String format = request.getParameter("format");
                            if(format == null || (!format.equals("o") && !format.equals("n") && !format.equals("u") && !format.equals("t")))
                                format = "s";
                            String pfull = request.getParameter("full");
                            boolean full = pfull != null && pfull.equals("1");
                            pfull = full ? "1" : "0";
                            

%><div id="difftable"><div id="diffbar"><span class="tabsel">&nbsp;<span class="d"> Deleted </span>&nbsp;<span class="a">&nbsp;Added&nbsp;</span>&nbsp;</span> | <%

if(format.equals("s")) {
	%><span class="tabsel"><b>sdiff</b></span> <%
} else {
	%><span class="tab"><a href="<%=reqURI%>?r1=<%=r1%>&r2=<%=r2%>&format=s&full=<%=pfull%>">sdiff</a></span> <%
}
                            
                            if(format.equals("u")) {
	%><span class="tabsel"><b>udiff</b></span> <%
                                } else {
	%><span class="tab"><a href="<%=reqURI%>?r1=<%=r1%>&r2=<%=r2%>&format=u&full=<%=pfull%>">udiff</a></span> <%
                                }
                            
                            if(format.equals("t")) {
	%><span class="tabsel"><b>text</b></span> <%
                                } else {
	%><span class="tab"><a href="<%=reqURI%>?r1=<%=r1%>&r2=<%=r2%>&format=t&full=<%=pfull%>">text</a></span> <%
                                }
                            
                            if(format.equals("o")) {
	%><span class="tabsel"><b>old (<%=r1%>)</b></span> <%
                                } else {
	%><span class="tab"><a href="<%=reqURI%>?r1=<%=r1%>&r2=<%=r2%>&format=o&full=<%=pfull%>">old (<%=r1%>)</a></span> <%
                                }
                            
                            if(format.equals("n")) {
	%><span class="tabsel"><b>new (<%=r2%>)</b></span>&nbsp;|&nbsp;<%
                                } else {
	%><span class="tab"><a href="<%=reqURI%>?r1=<%=r1%>&r2=<%=r2%>&format=n&full=<%=pfull%>">new (<%=r2%>)</a></span>&nbsp;|&nbsp;<%
                                }
                            
                            if(!full) {
	%><span class="tab"><a href="<%=reqURI%>?r1=<%=r1%>&r2=<%=r2%>&format=<%=format%>&full=1">&nbsp; &nbsp; full &nbsp; &nbsp;</a></span> <span class="tabsel"><b>compact</b></span><%
                                } else {
	%><span class="tabsel"><b>&nbsp; &nbsp; full &nbsp; &nbsp;</b> </span> <span class="tab"> <a href="<%=reqURI%>?r1=<%=r1%>&r2=<%=r2%>&format=<%=format%>&full=0">compact</a></span><%
                                }
                            
                            if(format.equals("s") || format.equals("u")) {
	%></div><pre wrap><table cellpadding="2" cellspacing="1" border="0"  rules="cols"><%
        if(format.equals("s")) {
		%><tr><th><%=basename%> (<%=r1%>)</th><th><%=basename%> (<%=r2%>)</th></tr><%
        }
                                } else {
	%></div><pre wrap><%
                                }
                            
                            for (int i=0; i < rev.size(); i++) {
                                Delta d = rev.getDelta(i);
                                if(format.equals("t")) {
	%><%=Util.Htmlize(d.toString())%><%
                                    } else {
                                        Chunk c1 = d.getOriginal();
                                        Chunk c2 = d.getRevised();
                                        int cn1 = c1.first();
                                        int cl1 = c1.last();
                                        int cn2 = c2.first();
                                        int cl2 = c2.last();
                                        
                                        int i1 = cn1, i2 = cn2;
                                        for (; i1 <= cl1 && i2 <= cl2; i1++, i2++) {
                                            String[] ss = diffline(Util.Htmlize((String)file1[i1]), Util.Htmlize((String)file2[i2]));
                                            file1[i1] = ss[0];
                                            file2[i2] = ss[1];
                                        }
                                        if(i1 <= cl1) {
                                            for(int h=i1; h<= cl1; h++) {
                                                file1[h] = Util.Htmlize((String)file1[h]);
                                            }
                                            file1[i1] = "<span class=\"d\">" + file1[i1];
                                            file1[cl1] = file1[cl1] + "</span>";
                                        }
                                        if(i2 <= cl2) {
                                            for(int h=i2; h<= cl2; h++) {
                                                file2[h] = Util.Htmlize((String)file2[h]);
                                            }
                                            file2[i2] = "<span class=\"a\">" + file2[i2];
                                            file2[cl2] = file2[cl2] + "</span>";
                                        }
                                        
                                        if (format.equals("u")) {
// UDIFF
                                            if (cn1 > ln1 || cn2 > ln2) {
	  %><tr class="k"><td><%
          if (full || (cn2 - ln2 < 20)) {
              for (int j = ln2; j < cn2; j++) {
		 	%><i><%=readableLine(++ln2)%></i><%=Util.Htmlize((String)file2[j])%><br/><%
              }
          } else {
              for (int j = ln2; j < ln2+8; j++) {
			%><i><%=readableLine(j+1)%></i><%=Util.Htmlize((String)file2[j])%><br/><%
              }
		%><br/>--- <b><%=cn2 - ln2 - 16%> unchanged lines hidden</b> (<a href="<%=reqURI%>?r1=<%=r1%>&r2=<%=r2%>&format=<%=format%>&full=1#<%=ln2%>">view full</a>) --- <br/><br/><%
                ln2 = cn2-8;
                for (int j = cn2 - 8; j < cn2; j++) {
			%><i><%=readableLine(++ln2)%></i><%=Util.Htmlize((String)file2[j])%><br/><%
                }
          }
	  %></td></tr><%
          ln1 = cn1;
                                            }
                                            if(cn1 <= cl1) {
		%><tr><td class="d"><%
                for(int j = cn1; j  <= cl1 ; j++) {
			%><strike class="d"><%=readableLine(++ln1)%></strike><%=file1[j]%><br/><%
                }
		%></td></tr><%
                                            }
                                            if(cn2 <= cl2) {
		%><tr class="k"><td><%
                for(int j = cn2; j  < cl2; j++) {
			%><i class="a"><%=readableLine(++ln2)%></i><%=file2[j]%><br/><%
                }
		%><i class="a"><%=readableLine(++ln2)%></i><%=file2[cl2]%><%
                if(full) {
			%><a name="<%=ln2%>" /><%
                }
		%></td></tr><%
                                            }
// SDIFF by default
                                        } else if(format.equals("s")) {
                                            
                                            if (cn1 > ln1 || cn2 > ln2) {
	    %><tr class="k"><td><%
            if(full || cn2 - ln2 < 20) {
                for(int j = ln1; j < cn1; j++) {
			%><i><%=readableLine(++ln1)%></i><%=Util.Htmlize((String)file1[j])%><br/><%
                }
		%></td><td><%
                for(int j = ln2; j  < cn2 ; j++) {
			%><i><%=readableLine(++ln2)%></i><%=Util.Htmlize((String)file2[j])%><br/><%
                }
            } else {
                for(int j = ln1; j < ln1+8; j++) {
			%><i><%=readableLine(j+1)%></i><%=Util.Htmlize((String)file1[j])%><br/><%
                }
		%><br/>--- <b><%=cn1 - ln1 - 16%> unchanged lines hidden</b> (<a href="<%=reqURI%>?r1=<%=r1%>&r2=<%=r2%>&format=<%=format%>&full=1#<%=ln2%>">view full</a>) --- <br/><br/><%
                ln1 = cn1-8;
                for (int j = cn1 - 8; j < cn1; j++) {
			%><i><%=readableLine(++ln1)%></i><%=Util.Htmlize((String)file1[j])%><br/><%
                }
	     %></td><td><%
             for (int j = ln2; j < ln2+8; j++) {
			%><i><%=readableLine(j+1)%></i><%=Util.Htmlize((String)file2[j])%><br/><%
             }
		%><br/>--- <b><%=cn2 - ln2 - 16%> unchanged lines hidden</b> (<a href="<%=reqURI%>?r1=<%=r1%>&r2=<%=r2%>&format=<%=format%>&full=1#<%=ln2%>">view full</a>) --- <br/><br/><%
                ln2 = cn2-8;
                for (int j = cn2 - 8; j < cn2; j++) {
			%><i><%=readableLine(++ln2)%></i><%=Util.Htmlize((String)file2[j])%><br/><%
                }
            }
	    %></td></tr><%    
                                            }

	%><tr  valign="top" class="k"><td><%
        for(int j = cn1; j  <= cl1; j++) {
		%><i><%=readableLine(++ln1)%></i><%=file1[j]%><br/><%
        }
	%></td><td><%
        for(int j = cn2; j  <= cl2; j++) {
		%><i><%=readableLine(++ln2)%></i><a name="<%=ln2%>" /></a><%=file2[j]%><br/><%
        }
	%></td></tr><%
        
// OLD -----
                                        } else if ( format.equals("o")) {
                                            if (cn1 > ln1) {
                                                if(full || cn1 - ln1 < 20) {
                                                    for(int j = ln1; j < cn1; j++) {
			%><i><%=readableLine(++ln1)%></i><%=Util.Htmlize((String)file1[j])%><br/><%
                                                    }
                                                } else {
                                                    for(int j = ln1; j < ln1+8; j++) {
			%><i><%=readableLine(j+1)%></i><%=Util.Htmlize((String)file1[j])%><br/><%
                                                    }
		%><br/>--- <b><%=cn1 - ln1 - 16%> unchanged lines hidden</b> (<a href="<%=reqURI%>?r1=<%=r1%>&r2=<%=r2%>&format=<%=format%>&full=1#<%=ln1%>">view full</a>) --- <br/><br/><%
                ln1 = cn1-8;
                for (int j = cn1 - 8; j < cn1; j++) {
			%><i><%=readableLine(++ln1)%></i><%=Util.Htmlize((String)file1[j])%><br/><%
                }
                                                }
                                            }
                                            for(int j = cn1; j  <= cl1 ; j++) {
		%><i><%=readableLine(++ln1)%></i><%=file1[j]%><br/><%
                                            }
                                            if(full) {
			%><a name="<%=ln1%>" ></a><%
                                            }
                                            
// NEW -----------
                                        } else if ( format.equals("n")) {
                                            
                                            if (cn2 > ln2) {
                                                if(full || cn2 - ln2 < 20) {
                                                    for(int j = ln2; j  < cn2 ; j++) {
			%><i><%=readableLine(++ln2)%></i><%=Util.Htmlize((String)file2[j])%><br/><%
                                                    }
                                                } else {
                                                    for (int j = ln2; j < ln2+8; j++) {
				%><i><%=readableLine(j+1)%></i><%=Util.Htmlize((String)file2[j])%><br/><%
                                                    }
			%><br/>--- <b><%=cn2 - ln2 - 16%> unchanged lines hidden</b> (<a href="<%=reqURI%>?r1=<%=r1%>&r2=<%=r2%>&format=<%=format%>&full=1#<%=ln2%>">view full</a>) --- <br/><br/><%
                        ln2 = cn2-8;
                        for (int j = cn2 - 8; j < cn2; j++) {
				%><i><%=readableLine(++ln2)%></i><%=Util.Htmlize((String)file2[j])%><br/><%
                        }
                                                }
                                            }
                                            for(int j = cn2; j  <= cl2 ; j++) {
		%><i><%=readableLine(++ln2)%></i><%=file2[j]%><br/><%
                                            }
                                            if(full) {
			%><a name="<%=ln2%>"></a><%
                                            }
                                            
            }
                                    }
                            }
                            
                            
                            if (file1.length >= ln1) {
// dump the remaining
                                if (format.equals("s")) {
                                    if (full || file1.length - ln1 < 20) {
		%><tr><td><%
                for (int j = ln1; j < file1.length ; j++) {
		 	%><i><%=(j+1)%></i><%=Util.Htmlize((String)file1[j])%><br/><%
                }
		%></td><td><%
                for (int j = ln2; j < file2.length ; j++) {
		 	%><i><%=(j+1)%></i><%=Util.Htmlize((String)file2[j])%><br/><%
                }
		%></td></tr></table><%
                                        } else {
		%><tr><td><%
                for (int j = ln1; j < ln1 + 8 ; j++) {
		 	%><i><%=(j+1)%></i><%=Util.Htmlize((String)file1[j])%><br/><%
                }

		%><br/> --- <b><%=file1.length - ln1 - 8%> unchanged lines hidden</b> --- </td><td><%
                for (int j = ln2; j < ln2 + 8 ; j++) {
		 	%><i><%=(j+1)%></i><%=Util.Htmlize((String)file2[j])%><br/><%
                }
		%><br/>--- <b><%=file1.length - ln1 - 8%> unchanged lines hidden</b> ---</td></tr></table><%
                                        }
                                    } else if (format.equals("u")) {
                                        if (full || file2.length - ln2 < 20) {
		%><tr><td><%
                for (int j = ln2; j < file2.length ; j++) {
		 	%><i><%=(j+1)%></i><%=Util.Htmlize((String)file2[j])%><br/><%
                }
		%></td></tr></table><%
                                        } else {
		%><tr><td><%
                for (int j = ln2; j < ln2 + 8 ; j++) {
		 	%><i><%=(j+1)%></i><%=Util.Htmlize((String)file2[j])%><br/><%
                }
		%><br/>--- <b><%=file2.length - ln2 - 8%> unchanged lines hidden</b> ---</td></tr></table><%
                                        }
                                    } else if (format.equals("o")) {
                                    if (full || file1.length - ln1 < 20) {
                                        for (int j = ln1; j < file1.length ; j++) {
		 	%><i><%=(j+1)%></i><%=Util.Htmlize((String)file1[j])%><br/><%
                                            }
                                        } else {
                                            for (int j = ln1; j < ln1 + 8 ; j++) {
		 	%><i><%=(j+1)%></i><%=Util.Htmlize((String)file1[j])%><br/><%
                                            }

		%><br/> --- <b><%=file1.length - ln1 - 8%> unchanged lines hidden</b> ---<br/><%
                                        }
                                    } else if (format.equals("n")) {
                                    if (full || file2.length - ln2 < 20) {
                                        for (int j = ln2; j < file2.length ; j++) {
		 	%><i><%=(j+1)%></i><%=Util.Htmlize((String)file2[j])%><br/><%
                                            }
                                        } else {
                                            for (int j = ln2; j < ln2 + 8 ; j++) {
		 	%><i><%=(j+1)%></i><%=Util.Htmlize((String)file2[j])%><br/><%
                                            }
		%><br/> --- <b><%=file2.length - ln2 - 8%> unchanged lines hidden</b> ---<br/><%
                                        }
                                    }
                                
                            }
                            
//----DIFFS Done--------
%></pre></div><%
                        }
                    } else {
				%> <div id="src">Diffs for binary files cannot be displayed! Files are <a href="<%=context%>/raw<%=path%>?r=<%=r1%>"><%=basename%>(revision <%=r1%>)</a> and
                                    <a href="<%=context%>/raw<%=path%>?r=<%=r2%>"><%=basename%>(revision <%=r2%>)</a>.  
				</div><%
                    }
                }
            } catch (FileNotFoundException e) {
		 %><div class="src"><h3 class="error">Error Opening files! <%=Util.Htmlize(e.getMessage())%></h3></div><%
            }
            if(in1 != null)
                in1.close();
            if(in2 != null)
                in2.close();
        } else if (g == Genre.IMAGE) {
				%> <div class="src">
				<table rules="cols" cellpadding="5"><tr><th><%=basename%> (revision <%=r1%>)</th><th><%=basename%> (revision <%=r2%>)</th></tr>
				<tr><td><img src="<%=context%>/raw<%=path%>?r=<%=r1%>"/></td><td><img src="<%=context%>/raw<%=path%>?r=<%=r2%>"/></td></tr></table></div><%
                                
        } else {
				%> <div class="src">Diffs for binary files cannot be displayed. Files are <a href="<%=context%>/raw<%=path%>?r=<%=r1%>"><%=basename%>(revision <%=r1%>)</a> and
                                    <a href="<%=context%>/raw<%=path%>?r=<%=r2%>"><%=basename%>(revision <%=r2%>)</a>.  
				</div><%
        }
    }
%><%@include file="foot.jspf"%><%
}
%>
