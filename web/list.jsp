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

Copyright 2009 Sun Microsystems, Inc.  All rights reserved.
Use is subject to license terms.
--%><%@ page import = "javax.servlet.*,
java.lang.*,
javax.servlet.http.*,
java.util.*,
java.io.*,
java.util.zip.GZIPInputStream,
java.util.logging.Level,
org.opensolaris.opengrok.OpenGrokLogger,
org.opensolaris.opengrok.analysis.*,
org.opensolaris.opengrok.configuration.Project,
org.opensolaris.opengrok.index.*,
org.opensolaris.opengrok.analysis.FileAnalyzer.Genre,
org.opensolaris.opengrok.web.*,
org.opensolaris.opengrok.history.*
"
%><%@include file="mast.jsp"%><script type="text/javascript">/* <![CDATA[ */
function lntoggle() {
   var a = document.getElementsByTagName("a");
   for (var i = 0; i < a.length; i++) {
      var el = a[i];
      if (el.className == 'l' || el.className == 'hl') {
         el.className=el.className+'-hide';
         el.setAttribute("tmp", el.innerHTML);
         el.innerHTML='';
      }
      else if (el.className == 'l-hide' || el.className == 'hl-hide') {
          el.innerHTML=el.getAttribute("tmp");
          el.className=el.className.substr(0,el.className.indexOf('-'));
      }
   }
}
/* ]]> */</script><%
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

        // verify that the current path is part of the selected project
        Project activeProject = Project.getProject(resourceFile);
//TODO: somehow integrate below with projects.jspf
        if (activeProject != null) {
            List<String> project = new ArrayList<String>();

            Cookie[] cookies = request.getCookies();
            if (cookies != null) {
             for (Cookie cookie : cookies) {
                if (cookie.getName().equals("OpenGrok/project")) {
                    for (String proj : cookie.getValue().split(",")) {
                        if (proj != "") {
                            if (Project.getByDescription(proj) != null) {
                            project.add(proj);
                            }
                        }
                    }
                }
             }
            }

            boolean set = false;
            if (project != null) {
               boolean found = false;               
               for (Iterator it = project.iterator(); it.hasNext();) {

                   if (activeProject.getDescription().equalsIgnoreCase( (String)it.next() ) ) {
                        found = true;
                        break;
                    }           
               }               
               if (!found) {
                   set = true;
               }
            } else {
                set = true;
            }
// set this in case there is no project selected or current cookie doesn't contain current project from the link, so the rest of search works 100% :)
            if (set) {
             StringBuffer sproject=new StringBuffer(activeProject.getDescription()+",");
             if (project!=null) {
                //only save found projects into cookies
                for (Iterator it = project.iterator(); it.hasNext();) {
                  sproject.append((String)it.next()+",");
                }
             }
             // update the cookie
             Cookie cookie = new Cookie("OpenGrok/project", sproject.toString());
             cookie.setPath(context + "/");
             response.addCookie(cookie);
            }
        }

        // If requesting a Directory listing -------------
        DirectoryListing dl = new DirectoryListing(ef);
        String[] files = resourceFile.list();
        if (files != null) {
            Arrays.sort(files, String.CASE_INSENSITIVE_ORDER);
            List<String> readMes = dl.listTo(resourceFile, out, path, files);
            if(readMes != null && readMes.size() > 0) {
                File xdir = new File(environment.getDataRootPath() + "/xref" + path);
                if (xdir.exists() && xdir.isDirectory()) {
                    char[] buf = new char[8192];
                    for (String readme : readMes) {
                      File readmeFile = new File(xdir, readme + ".gz");
                      Reader br = null;
                      try {
                        if (environment.isCompressXref() && readmeFile.exists()) {
                          br = new InputStreamReader(new GZIPInputStream(new FileInputStream(readmeFile)));
                        } else {
                          readmeFile = new File(xdir, readme);
                          if (readmeFile.exists()) {
                            br = new FileReader(readmeFile);
                          }
                        }

                        if (br != null) {
                          int len = 0;
                          %><h3><%=readme%></h3><div id="src"><pre><%
                          while((len = br.read(buf)) > 0) {
                              out.write(buf, 0, len);
                          }
                          %></pre></div><%
                        }
                      } catch(IOException e) {
                        OpenGrokLogger.getLogger().log(Level.WARNING, "An error occured while reading/writing readme:", e);
                      } finally {
                        if (br != null) {
                          try {
                            br.close();
                          } catch (IOException e) {
                            OpenGrokLogger.getLogger().log(Level.WARNING, "An error occured while closing file:", e);
                          }
                        }
                      }
                    }
                }
            }
        }
    } else if ((rev = request.getParameter("r")) != null && !rev.equals("")) {
        // Else if requesting a previous revision -------------
        if (noHistory) {
            response.sendError(404, "Revision not found");
        } else {
            FileAnalyzerFactory a = AnalyzerGuru.find(basename);
            Genre g = AnalyzerGuru.getGenre(a);
            if (g == Genre.PLAIN|| g == Genre.HTML || g == null) {
                InputStream in = null;
                try {
                    in = HistoryGuru.getInstance().getRevision(resourceFile.getParent(), basename, rev);
                } catch (Exception e) {
                    response.sendError(404, "Revision not found");
                    return;
                }
                if (in != null) {
                    try {
                        if (g == null) {
                            a = AnalyzerGuru.find(in);
                            g = AnalyzerGuru.getGenre(a);
                        }
                        if (g == Genre.DATA || g == Genre.XREFABLE || g == null) {
		            %><div id="src">Binary file [Click <a href="<%=context%>/raw<%=path%>?r=<%=rev%>">here</a> to download] </div><%
                        } else {
		            %><div id="src"><span class="pagetitle"><%=basename%> revision <%=rev%> </span><pre><%
                            if (g == Genre.PLAIN) {
                                Annotation annotation = annotate ? HistoryGuru.getInstance().annotate(resourceFile, rev) : null;
                                //annotation.writeTooltipMap(out); //not needed yet
                                AnalyzerGuru.writeXref(a, in, out, annotation, Project.getProject(resourceFile));
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
        }
    } else {
        // requesting cross referenced file -------------
        File xrefSource = new File(environment.getDataRootFile(), "/xref");
        File xrefFile = new File(xrefSource, path + ".gz");
        Reader fileReader = null;

        if (environment.isCompressXref() ) {
            if  (xrefFile.exists()) {
            fileReader = new InputStreamReader(new GZIPInputStream(new FileInputStream(xrefFile)));
            }
        } else {            
            xrefFile = new File(xrefSource, path);
            if (xrefFile.exists()) {
                fileReader = new FileReader(xrefFile);
            }
        }

        if (fileReader != null && !annotate) {
            char[] buf = new char[8192];
            BufferedReader br = new BufferedReader(fileReader);
            int len = 0;
            %><div id="src"><pre><%
            while((len = br.read(buf)) > 0) {
                out.write(buf, 0, len);
            }
            %></pre></div><%
            br.close();
        } else {
            BufferedInputStream bin = new BufferedInputStream(new FileInputStream(resourceFile));
            FileAnalyzerFactory a = AnalyzerGuru.find(basename);
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
                Annotation annotation = annotate ? HistoryGuru.getInstance().annotate(resourceFile, rev) : null;                
                AnalyzerGuru.writeXref(a, bin, out, annotation, Project.getProject(resourceFile));
                %></pre></div><%
            } else {
	        %> Click <a href="<%=context%>/raw<%=path%>">download <%=basename%></a><%
            }
        }
    }
    %><%@include file="foot.jspf"%><%
}
if (ef != null) {
    try {
        ef.close();
    } catch (IOException e) {
    }
}
%>
