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

Copyright (c) 2006, 2025, Oracle and/or its affiliates. All rights reserved.
Portions Copyright 2011 Jens Elkner.
Portions Copyright (c) 2020, Chris Fraire <cfraire@me.com>.
--%>
<%@page errorPage="error.jsp" import="
java.io.ByteArrayInputStream,
java.io.OutputStream,
java.io.InputStream,
java.nio.charset.StandardCharsets,

org.suigeneris.jrcs.diff.delta.Chunk,
org.suigeneris.jrcs.diff.delta.Delta,
org.opengrok.indexer.analysis.AbstractAnalyzer,
org.opengrok.web.DiffData,
org.opengrok.web.DiffType"
%>
<%!
private String getAnnotateRevision(DiffData data) {
    if (data.getType() == DiffType.OLD || data.getType() == DiffType.NEW) {
        String rev = data.getRev(data.getType() == DiffType.NEW ? 1 : 0);
        return "<script type=\"text/javascript\">/* <![CDATA[ */ "
            + "document.rev = function() { return " + Util.htmlize(Util.jsStringLiteral(rev))
            + "; } /* ]]> */</script>";
    }
    return "";
}
%>
<%
{
    PageConfig cfg = PageConfig.get(request);
    cfg.addScript("diff");
    cfg.checkSourceRootExistence();
    /*
     * This block must be the first block before any other output in the response.
     * If there is already any output written into the response, and we
     * use the same response and reset the content and the headers then we have
     * a collision with the response streams and the "getOutputStream() has
     * already been called" exception occurs.
     */
    DiffData data = cfg.getDiffData();
    request.setAttribute("diff.jsp-data", data);
    if (data.getType() == DiffType.TEXT
            && request.getParameter("action") != null
            && request.getParameter("action").equals("download")) {
        try (OutputStream o = response.getOutputStream()) {
            for (int i = 0; i < data.getRevision().size(); i++) {
                Delta delta = data.getRevision().getDelta(i);
                try (InputStream in = new ByteArrayInputStream(delta.toString().getBytes(StandardCharsets.UTF_8))) {
                    response.setHeader("content-disposition", "attachment; filename="
                            + cfg.getResourceFile().getName() + "@" + data.getRev(0)
                            + "-" + data.getRev(1) + ".diff");
                    byte[] buffer = new byte[8192];
                    int nr;
                    while ((nr = in.read(buffer)) > 0) {
                        o.write(buffer, 0, nr);
                    }
                }
            }
            o.flush();
            o.close();
            return;
        }
    }
}
%><%@

include file="/mast.jsp"

%><%
/* ---------------------- diff.jsp start --------------------- */
{
    PageConfig cfg = PageConfig.get(request);
    DiffData data = (DiffData) request.getAttribute("diff.jsp-data");

    // the data is never null as the getDiffData always return valid object
    if (data.getErrorMsg() != null)  {

%>
<div class="src">
    <h3 class="error">Error:</h3>
    <p><%= Util.htmlize(data.getErrorMsg()) %></p>
</div><%
    } else if (data.getGenre() == AbstractAnalyzer.Genre.IMAGE) {

        String link = request.getContextPath() + Prefix.DOWNLOAD_P + Util.htmlize(cfg.getPath());
%>
<div id="difftable">
    <table class="image" aria-label="table with old and new image">
        <thead>
        <tr>
            <th><%= Util.htmlize(data.getFilename()) %> (revision <%= Util.htmlize(data.getRev(0)) %>)</th>
            <th><%= Util.htmlize(data.getFilename()) %> (revision <%= Util.htmlize(data.getRev(1)) %>)</th>
        </tr>
        </thead>
        <tbody>
        <tr>
            <td><img src="<%= link %>?<%= QueryParameters.REVISION_PARAM_EQ %><%= Util.uriEncode(data.getRev(0)) %>" alt="previous image"/>
            </td>
            <td><img src="<%= link %>?<%= QueryParameters.REVISION_PARAM_EQ %><%= Util.uriEncode(data.getRev(1)) %>" alt="new image"/>
            </td>
        </tr>
        </tbody>
    </table>
</div><%

    } else if (data.getGenre() != AbstractAnalyzer.Genre.PLAIN && data.getGenre() != AbstractAnalyzer.Genre.HTML) {

        String link = request.getContextPath() + Prefix.DOWNLOAD_P + Util.uriEncodePath(cfg.getPath());
%>
<div id="src">Diffs for binary files cannot be displayed! Files are
    <a href="<%= link %>?<%= QueryParameters.REVISION_PARAM_EQ %><%= Util.uriEncode(data.getRev(0)) %>">
        <%= Util.htmlize(data.getFilename()) %>(revision <%= Util.htmlize(data.getRev(0)) %>)
    </a> and
    <a href="<%= link %>?<%= QueryParameters.REVISION_PARAM_EQ %><%= Util.uriEncode(data.getRev(1)) %>">
        <%= Util.htmlize(data.getFilename()) %>(revision <%= Util.htmlize(data.getRev(1)) %>)
    </a>.
</div><%

    } else if (data.getRevision().size() == 0) {
        %>
        <%= getAnnotateRevision(data) %>
        <strong>No differences found!</strong><%

    } else {
        //-------- Do THE DIFFS ------------
        int ln1 = 0;
        int ln2 = 0;
        String rp1 = data.getParam(0);
        String rp2 = data.getParam(1);
        String reqURI = request.getRequestURI();
        String[] file1 = data.getFile(0);
        String[] file2 = data.getFile(1);

        DiffType type = data.getType();
        boolean full = data.isFull();
%>
<%= getAnnotateRevision(data) %>
<div id="diffbar">
    <div class="legend">
        <span class="d">Deleted</span>
        <span class="a">Added</span>
    </div>
    <div class="tabs"><%
        for (DiffType t : DiffType.values()) {
            if (type == t) {
        %> <span class="active"><%= t.toString() %><%
                if (t == DiffType.OLD) {
            %>  (<%= Util.htmlize(data.getRev(0)) %>)<%
                } else if (t == DiffType.NEW) {
            %>  (<%= Util.htmlize(data.getRev(1)) %>)<%
                }
            %></span><%
            } else {
        %> <span><a href="<%= reqURI %>?<%= QueryParameters.REVISION_1_PARAM_EQ %><%= rp1 %>&amp;
<%= QueryParameters.REVISION_2_PARAM_EQ %><%= rp2 %>&amp;
<%= QueryParameters.FORMAT_PARAM_EQ %><%= t.getAbbrev() %>&amp;
<%= QueryParameters.DIFF_LEVEL_PARAM_EQ %><%= full ? '1' : '0'%>"><%= t.toString() %>
            <%
                if (t == DiffType.OLD) {
            %>  (<%= Util.htmlize(data.getShortRev(0)) %>)<%
                } else if (t == DiffType.NEW) {
            %>  (<%= Util.htmlize(data.getShortRev(1)) %>)<%
                }
            %></a></span><%
            }
        }
    %></div>
    <div class="ctype"><%
        if (!full) {
        %>
        <span><a href="<%= reqURI %>?<%= QueryParameters.REVISION_1_PARAM_EQ %><%= Util.uriEncode(rp1) %>&amp;
<%= QueryParameters.REVISION_2_PARAM_EQ %><%= Util.uriEncode(rp2) %>&amp;
<%= QueryParameters.FORMAT_PARAM_EQ %><%= type.getAbbrev() %>&amp;
<%= QueryParameters.DIFF_LEVEL_PARAM_EQ %>1">full</a></span>
        <span class="active">compact</span><%
        } else {
        %>
        <span class="active">full</span>
        <span> <a href="<%= reqURI %>?<%= QueryParameters.REVISION_1_PARAM_EQ %><%= Util.uriEncode(rp1) %>&amp;
<%= QueryParameters.REVISION_2_PARAM_EQ %><%= Util.uriEncode(rp2) %>&amp;
<%= QueryParameters.FORMAT_PARAM_EQ %><%= type.getAbbrev() %>&amp;
<%= QueryParameters.DIFF_LEVEL_PARAM_EQ %>0">compact</a></span><%
        }
        %><span><a href="#" id="toggle-jumper">jumper</a></span>
        <span><a href="<%= reqURI %>?<%= QueryParameters.REVISION_1_PARAM_EQ %><%= Util.uriEncode(rp1) %>&amp;
<%= QueryParameters.REVISION_2_PARAM_EQ %><%= Util.uriEncode(rp2) %>&amp;
<%= QueryParameters.FORMAT_PARAM_EQ %><%= DiffType.TEXT %>&amp;
action=download">download diff</a></span><%
    %></div>
</div>

<div id="difftable">
    <div class="pre"><%
        if (type == DiffType.SIDEBYSIDE || type == DiffType.UNIFIED) {
        %><table class="plain" aria-label="table with old and new content"><%
            if (type == DiffType.SIDEBYSIDE) {
                String linkPrefix = request.getContextPath() + Prefix.XREF_P + Util.uriEncodePath(cfg.getPath()) +
                        "?" + QueryParameters.REVISION_PARAM_EQ;
            %>
            <thead><tr>
                <th><a href="<%= linkPrefix %><%= Util.uriEncode(data.getRev(0)) %>"><%= Util.htmlize(data.getFilename()) %> (<%= Util.htmlize(data.getRev(0)) %>)</a></th>
                <th><a href="<%= linkPrefix %><%= Util.uriEncode(data.getRev(1)) %>"><%= Util.htmlize(data.getFilename()) %> (<%= Util.htmlize(data.getRev(1)) %>)</a></th>
            </tr></thead><%
            }
            %>
            <tbody><%
        }

        for (int i = 0; i < data.getRevision().size(); i++) {
            Delta delta = data.getRevision().getDelta(i);
            if (type == DiffType.TEXT) {
        %><%= Util.htmlize(delta.toString()) %><%
            } else {
                Chunk c1 = delta.getOriginal();
                Chunk c2 = delta.getRevised();
                int cn1 = c1.first();
                int cl1 = c1.last();
                int cn2 = c2.first();
                int cl2 = c2.last();

                int i1 = cn1, i2 = cn2;
                StringBuilder bl1 = new StringBuilder(80);
                StringBuilder bl2 = new StringBuilder(80);
                for (; i1 <= cl1 && i2 <= cl2; i1++, i2++) {
                    String[] ss = Util.diffline(
                            new StringBuilder(file1[i1]),
                            new StringBuilder(file2[i2]));
                    file1[i1] = ss[0];
                    file2[i2] = ss[1];
                }
                // deleted
                for (; i1 <= cl1; i1++) {
                    bl1.setLength(0);
                    bl1.append("<span class=\"d\">");
                    Util.htmlize(file1[i1], bl1);
                    file1[i1] = bl1.append("</span>").toString();
                }
                // added
                for (; i2 <= cl2; i2++) {
                    bl2.setLength(0);
                    bl2.append("<span class=\"a\">");
                    Util.htmlize(file2[i2], bl2);
                    file2[i2] = bl2.append("</span>").toString();
                }

                if (type == DiffType.UNIFIED) {
// UDIFF
                    if (cn1 > ln1 || cn2 > ln2) {
            %>
            <tr class="k"><td><%
                        if (full || (cn2 - ln2 < 20)) {
                            for (int j = ln2; j < cn2; j++) {
                %><span class="it"><%= ++ln2 %></span><%=
                    Util.htmlize(file2[j]) %><br/><%
                            }
                        } else {
                            for (int j = ln2; j < ln2 + 8; j++) {
                %><span class="it"><%= j + 1 %></span><%=
                    Util.htmlize(file2[j]) %><br/><%
                            }
                %><br/>--- <strong><%= cn2 - ln2 - 16
                    %> unchanged lines hidden</strong> (<a href="<%= reqURI
%>?<%= QueryParameters.REVISION_1_PARAM_EQ %><%= rp1 %>&amp;
<%= QueryParameters.REVISION_2_PARAM_EQ %><%= rp2 %>&amp;
<%= QueryParameters.FORMAT_PARAM_EQ %><%= type.getAbbrev() %>&amp;
<%= QueryParameters.DIFF_LEVEL_PARAM_EQ %>1#<%= ln2 %>">view full</a>)
                    --- <br/><br/><%
                            ln2 = cn2 - 8;
                            for (int j = ln2; j < cn2; j++) {
                %><span class="it"><%= ++ln2 %></span><%= Util.htmlize(file2[j]) %><br/><%
                            }
                        }
                %></td>
            </tr><%
                        ln1 = cn1;
                    }
                    if (cn1 <= cl1) {
            %>
            <tr class="chunk"><td><%
                        for (int j = cn1; j <= cl1 ; j++) {
                %><del class="d"><%= ++ln1 %></del><%= file1[j]
                %><br/><%
                        }
                %></td>
            </tr><%
                    }
                    if (cn2 <= cl2) {
            %>
            <tr class="k<%
                    if (cn1 > cl1) {
                        %> chunk<%
                    }
                %>"><td><%
                        for (int j = cn2; j < cl2; j++) {
                %><span class="a it"><%= ++ln2 %></span><%= file2[j]
                %><br/><%
                        }
                %><span class="a it"><%= ++ln2 %></span><%= file2[cl2] %><%
                        if (full) {
                %><a id="<%= ln2 %>" /><%
                        }
                %></td>
            </tr><%
                    }
                } else if (type == DiffType.SIDEBYSIDE) {
// SDIFF
                    if (cn1 > ln1 || cn2 > ln2) {
            %>
            <tr class="k"><td><%
                        if (full || cn2 - ln2 < 20) {
                            for (int j = ln1; j < cn1; j++) {
                %><span class="it"><%= ++ln1 %></span><%=
                    Util.htmlize(file1[j]) %><br/><%
                            }
                %></td><td><%
                            for (int j = ln2; j < cn2 ; j++) {
                %><span class="it"><%= ++ln2 %></span><%=
                    Util.htmlize(file2[j]) %><br/><%
                            }
                        } else {
                            for (int j = ln1; j < ln1 + 8; j++) {
                %><span class="it"><%= j + 1 %></span><%=
                    Util.htmlize(file1[j]) %><br/><%
                            }
                %><br/>--- <strong><%= cn1 - ln1 - 16
                    %> unchanged lines hidden</strong> (<a href="<%= reqURI
%>?<%= QueryParameters.REVISION_1_PARAM_EQ %><%= rp1 %>&amp;
<%= QueryParameters.REVISION_2_PARAM_EQ %><%= rp2 %>&amp;
<%= QueryParameters.FORMAT_PARAM_EQ %><%= type.getAbbrev() %>&amp;
<%= QueryParameters.DIFF_LEVEL_PARAM_EQ %>1#<%= ln2 %>">view full</a>)
                    --- <br/><br/><%
                            ln1 = cn1 - 8;
                            for (int j = ln1; j < cn1; j++) {
                %><span class="it"><%= ++ln1 %></span><%=
                    Util.htmlize(file1[j]) %><br/><%
                            }
                %></td><td><%
                            for (int j = ln2; j < ln2 + 8; j++) {
                %><span class="it"><%= j + 1 %></span><%=
                    Util.htmlize(file2[j]) %><br/><%
                            }
                %><br/>--- <strong><%= cn2 - ln2 - 16
                    %> unchanged lines hidden</strong> (<a href="<%= reqURI
%>?<%= QueryParameters.REVISION_1_PARAM_EQ %><%= rp1 %>&amp;
<%= QueryParameters.REVISION_2_PARAM_EQ %><%= rp2 %>&amp;
<%= QueryParameters.FORMAT_PARAM_EQ %><%= type.getAbbrev() %>&amp;
<%= QueryParameters.DIFF_LEVEL_PARAM_EQ %>1#<%= ln2 %>">view full</a>)
                    --- <br/><br/><%
                            ln2 = cn2 - 8;
                            for (int j = ln2; j < cn2; j++) {
                %><span class="it"><%= ++ln2 %></span><%=
                    Util.htmlize(file2[j]) %><br/><%
                            }
                        }
                %></td>
            </tr><%
                    }
            %>
            <tr class="k chunk"><td><%
                    for (int j = cn1; j <= cl1; j++) {
                %><span class="it"><%= ++ln1 %></span><%= file1[j] %><br/><%
                    }
                %></td><td><%
                    for (int j = cn2; j <= cl2; j++) {
                %><span class="it"><%= ++ln2 %></span><a id="<%= ln2 %>"></a><%=
                    file2[j] %><br/><%
                    }
                %></td>
            </tr><%
// OLD
                } else if (type == DiffType.OLD) {
                    // OLD
                    if (cn1 > ln1) {
                        if (full || cn1 - ln1 < 20) {
                            for (int j = ln1; j < cn1; j++) {
        %><span class="it"><%= ++ln1 %></span><%=
            Util.htmlize(file1[j]) %><br/><%
                            }
                        } else {
                            for (int j = ln1; j < ln1 + 8; j++) {
        %><span class="it"><%= j + 1 %></span><%=
            Util.htmlize(file1[j]) %><br/><%
                            }
        %><br/>--- <strong><%= cn1 - ln1 - 16
            %> unchanged lines hidden</strong> (<a href="<%= reqURI
%>?<%= QueryParameters.REVISION_1_PARAM_EQ %><%= rp1 %>&amp;
<%= QueryParameters.REVISION_2_PARAM_EQ %><%= rp2 %>&amp;
<%= QueryParameters.FORMAT_PARAM_EQ %><%= type.getAbbrev() %>&amp;
<%= QueryParameters.DIFF_LEVEL_PARAM_EQ %>1#<%=ln1%>">view full</a>) --- <br/><br/><%
                            ln1 = cn1 - 8;
                            for (int j = ln1; j < cn1; j++) {
        %><span class="it"><%= ++ln1 %></span><%=
            Util.htmlize(file1[j]) %><br/><%
                            }
                        }
                    }
                    for (int j = cn1; j <= cl1 ; j++) {
        %><span class="it"><%= ++ln1 %></span><%= file1[j] %><br/><%
                    }
                    if (full) {
        %><a id="<%=ln1%>" ></a><%
                    }
// NEW
                } else if (type == DiffType.NEW) {
                    if (cn2 > ln2) {
                        if (full || cn2 - ln2 < 20) {
                            for (int j = ln2; j < cn2 ; j++) {
        %><span class="it"><%= ++ln2 %></span><%=
            Util.htmlize(file2[j]) %><br/><%
                            }
                        } else {
                            for (int j = ln2; j < ln2 + 8; j++) {
        %><span class="it"><%= j + 1 %></span><%=
            Util.htmlize(file2[j]) %><br/><%
                            }
        %><br/>--- <strong><%= cn2 - ln2 - 16
            %> unchanged lines hidden</strong> (<a href="<%= reqURI
%>?<%= QueryParameters.REVISION_1_PARAM_EQ %><%= rp1 %>&amp;
<%= QueryParameters.REVISION_2_PARAM_EQ %><%= rp2 %>&amp;
<%= QueryParameters.FORMAT_PARAM_EQ %><%= type.getAbbrev() %>&amp;
<%= QueryParameters.DIFF_LEVEL_PARAM_EQ %>1#<%= ln2 %>">view full</a>) --- <br/><br/><%
                            ln2 = cn2 - 8;
                            for (int j = ln2; j < cn2; j++) {
            %><span class="it"><%= ++ln2 %></span><%=
                Util.htmlize(file2[j]) %><br/><%
                            }
                        }
                    }
                    for (int j = cn2; j <= cl2 ; j++) {
        %><span class="it"><%= ++ln2 %></span><%= file2[j] %><br/><%
                    }
                    if (full) {
        %><a id="<%= ln2 %>"></a><%
                    }
                }
            } // else
        } // for
// deltas done, dump the remaining
        if (file1.length >= ln1) {
            if (type == DiffType.SIDEBYSIDE) {
                if (full || file1.length - ln1 < 20) {
            %>
            <tr><td><%
                    for (int j = ln1; j < file1.length; j++) {
                %><span class="it"><%= j + 1 %></span><%= Util.htmlize(file1[j]) %><br/><%
                    }
                %></td><td><%
                    for (int j = ln2; j < file2.length; j++) {
                %><span class="it"><%= j + 1 %></span><%= Util.htmlize(file2[j]) %><br/><%
                    }
                %></td>
            </tr>
            </tbody>
        </table><%
                } else {
            %>
            <tr><td><%
                    for (int j = ln1; j < ln1 + 8; j++) {
                %><span class="it"><%= j + 1 %></span><%= Util.htmlize(file1[j]) %><br/><%
                    }
                %><br/> --- <strong><%= file1.length - ln1 - 8
                %> unchanged lines hidden</strong> --- </td><td><%
                    for (int j = ln2; j < ln2 + 8; j++) {
                %><span class="it"><%= j + 1 %></span><%= Util.htmlize(file2[j]) %><br/><%
                    }
                %><br/>--- <strong><%= file1.length - ln1 - 8
                %> unchanged lines hidden</strong> ---</td>
            </tr>
            </tbody>
        </table><%
                }
            } else if (type == DiffType.UNIFIED) {
                if (full || file2.length - ln2 < 20) {
            %>
            <tr><td><%
                    for (int j = ln2; j < file2.length; j++) {
                %><span class="it"><%= j + 1 %></span><%= Util.htmlize(file2[j]) %><br/><%
                    }
                %></td>
            </tr>
            </tbody>
        </table><%
                } else {
            %>
            <tr><td><%
                    for (int j = ln2; j < ln2 + 8; j++) {
                %><span class="it"><%= j + 1 %></span><%= Util.htmlize(file2[j]) %><br/><%
                    }
                %><br/>--- <strong><%= file2.length - ln2 - 8
                %> unchanged lines hidden</strong> ---</td>
            </tr>
            </tbody>
        </table><%
                }
            } else if (type == DiffType.OLD) {
                if (full || file1.length - ln1 < 20) {
                    for (int j = ln1; j < file1.length; j++) {
        %><span class="it"><%= j + 1 %></span><%= Util.htmlize(file1[j]) %><br/><%
                    }
                } else {
                    for (int j = ln1; j < ln1 + 8; j++) {
        %><span class="it"><%= j + 1 %></span><%= Util.htmlize(file1[j]) %><br/><%
                    }
        %><br/> --- <strong><%= file1.length - ln1 - 8
        %> unchanged lines hidden</strong> ---<br/><%
                }
            } else if (type == DiffType.NEW) {
                if (full || file2.length - ln2 < 20) {
                    for (int j = ln2; j < file2.length; j++) {
        %><span class="it"><%= j + 1 %></span><%=Util.htmlize(file2[j])%><br/><%
                    }
                } else {
                    for (int j = ln2; j < ln2 + 8; j++) {
        %><span class="it"><%= j + 1 %></span><%= Util.htmlize(file2[j]) %><br/><%
                    }
        %><br/> --- <strong><%= file2.length - ln2 - 8
        %> unchanged lines hidden</strong> ---<br/><%
                }
            }
        }

//----DIFFS Done--------
    %></div>
</div><%
    }
}
/* ---------------------- diff.jsp end --------------------- */
%><%@

include file="/foot.jspf"

%>
