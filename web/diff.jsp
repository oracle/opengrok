<%--
$Id$

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

Portions Copyright 2011 Jens Elkner.
--%><%@page import="
java.io.BufferedReader,
java.io.FileNotFoundException,
java.io.InputStream,
java.io.InputStreamReader,
java.io.UnsupportedEncodingException,
java.net.URLDecoder,
java.util.ArrayList,

org.apache.commons.jrcs.diff.Chunk,
org.apache.commons.jrcs.diff.Delta,
org.apache.commons.jrcs.diff.Diff,
org.apache.commons.jrcs.diff.Revision,
org.opensolaris.opengrok.analysis.AnalyzerGuru,
org.opensolaris.opengrok.analysis.FileAnalyzer.Genre,
org.opensolaris.opengrok.web.DiffData,
org.opensolaris.opengrok.web.DiffType"
%><%!
private String getAnnotateRevision(DiffData data) {
    if (data.type == DiffType.OLD || data.type == DiffType.NEW) {
        return "<script type=\"text/javascript\">/* <![CDATA[ */ "
            + "document.rev = 'r=" + data.rev[data.type == DiffType.NEW ? 1 : 0]
            + "'; /* ]]> */</script>";
    }
    return "";
}
%><%@

include file="mast.jsp"

%><%
/* ---------------------- diff.jsp start --------------------- */
{
    cfg = PageConfig.get(request);
    DiffData data = cfg.getDiffData();

    if (data.errorMsg != null)  {

%>
<div class="src">
    <h3 class="error">Error:</h3>
    <p><%= data.errorMsg %></p>
</div><%

    } else if (data.genre == Genre.IMAGE) {

        String link = request.getContextPath() + Prefix.RAW_P
            + Util.htmlize(cfg.getPath());
%>
<div id="difftable">
    <table class="image">
        <thead>
        <tr><th><%= data.filename %> (revision <%= data.rev[0] %>)</th>
            <th><%= data.filename %> (revision <%= data.rev[1] %>)</th>
        </tr>
        </thead>
        <tbody>
        <tr><td><img src="<%= link %>?r=<%= data.rev[0] %>"/></td>
            <td><img src="<%= link %>?r=<%= data.rev[1] %>"/></td>
        </tr>
        </tbody>
    </table>
</div><%

    } else if (data.genre != Genre.PLAIN && data.genre != Genre.HTML) {

        String link = request.getContextPath() + Prefix.RAW_P
            + Util.htmlize(cfg.getPath());
%>
<div id="src">Diffs for binary files cannot be displayed! Files are <a
    href="<%= link %>?r=<%= data.rev[0] %>"><%=
        data.filename %>(revision <%= data.rev[0] %>)</a> and <a
    href="<%= link %>?r=<%= data.rev[1] %>"><%=
        data.filename %>(revision <%= data.rev[1] %>)</a>.
</div><%

    } else if (data.revision.size() == 0) {
        %>
        <%= getAnnotateRevision(data) %>
        <b>No differences found!</b><%

    } else {
        //-------- Do THE DIFFS ------------
        int ln1 = 0;
        int ln2 = 0;
        String rp1 = data.param[0];
        String rp2 = data.param[1];
        String reqURI = request.getRequestURI();
        String[] file1 = data.file[0];
        String[] file2 = data.file[1];

        DiffType type = data.type;
        boolean full = data.full;
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
            %>  ( <%= data.rev[0] %> )<%
                } else if (t == DiffType.NEW) {
            %>  ( <%= data.rev[1] %> )<%
                }
            %></span><%
            } else {
        %> <span><a href="<%= reqURI %>?r1=<%= rp1 %>&amp;r2=<%= rp2
            %>&amp;format=<%= t.getAbbrev() %>&amp;full=<%= full ? '1' : '0'
            %>"><%= t.toString() %><%
                if (t == DiffType.OLD) {
            %>  ( <%= data.rev[0] %> )<%
                } else if (t == DiffType.NEW) {
            %>  ( <%= data.rev[1] %> )<%
                }
            %></a></span><%
            }
        }
    %></div>
    <div class="ctype"><%
        if (!full) {
        %>
        <span><a href="<%= reqURI %>?r1=<%= rp1 %>&amp;r2=<%= rp2
            %>&amp;format=<%= type.getAbbrev() %>&amp;full=1">full</a></span>
        <span class="active">compact</span><%
        } else {
        %>
        <span class="active">full</span>
        <span> <a href="<%= reqURI %>?r1=<%= rp1 %>&amp;r2=<%= rp2
            %>&amp;format=<%= type.getAbbrev() %>&amp;full=0">compact</a></span><%
        }
    %></div>
</div>

<div id="difftable">
    <div class="pre"><%
        if (type == DiffType.SIDEBYSIDE || type == DiffType.UNIFIED) {
        %><table class="plain"><%
            if (type == DiffType.SIDEBYSIDE) {
            %>
            <thead><tr>
                <th><%= data.filename %> (<%= data.rev[0] %>)</th>
                <th><%= data.filename %> (<%= data.rev[1] %>)</th>
            </tr></thead><%
            }
            %>
            <tbody><%
        }

        for (int i=0; i < data.revision.size(); i++) {
            Delta d = data.revision.getDelta(i);
            if (type == DiffType.TEXT) {
        %><%= Util.htmlize(d.toString()) %><%
            } else {
                Chunk c1 = d.getOriginal();
                Chunk c2 = d.getRevised();
                int cn1 = c1.first();
                int cl1 = c1.last();
                int cn2 = c2.first();
                int cl2 = c2.last();

                int i1 = cn1, i2 = cn2;
                StringBuilder bl1 = new StringBuilder(80);
                StringBuilder bl2 = new StringBuilder(80);
                for (; i1 <= cl1 && i2 <= cl2; i1++, i2++) {
                    Util.htmlize(file1[i1], bl1);
                    Util.htmlize(file2[i2], bl2);
                    String[] ss = Util.diffline(bl1, bl2);
                    file1[i1] = ss[0];
                    file2[i2] = ss[1];
                    bl1.setLength(0);
                    bl2.setLength(0);
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
                %><i><%= ++ln2 %></i><%=
                    Util.htmlize(file2[j]) %><br/><%
                            }
                        } else {
                            for (int j = ln2; j < ln2 + 8; j++) {
                %><i><%= j+1 %></i><%=
                    Util.htmlize(file2[j]) %><br/><%
                            }
                %><br/>--- <b><%= cn2 - ln2 - 16
                    %> unchanged lines hidden</b> (<a href="<%= reqURI
                    %>?r1=<%= rp1 %>&amp;r2=<%= rp2
                    %>&amp;format=<%= type.getAbbrev()
                    %>&amp;full=1#<%= ln2 %>">view full</a>) --- <br/><br/><%
                            ln2 = cn2 - 8;
                            for (int j = ln2; j < cn2; j++) {
                %><i><%= ++ln2 %></i><%= Util.htmlize(file2[j]) %><br/><%
                            }
                        }
                %></td>
            </tr><%
                        ln1 = cn1;
                    }
                    if (cn1 <= cl1) {
            %>
            <tr><td><%
                        for (int j = cn1; j  <= cl1 ; j++) {
                %><del class="d"><%= ++ln1 %></del><%= file1[j]
                %><br/><%
                        }
                %></td>
            </tr><%
                    }
                    if (cn2 <= cl2) {
            %>
            <tr class="k"><td><%
                        for (int j = cn2; j  < cl2; j++) {
                %><i class="a"><%= ++ln2 %></i><%= file2[j]
                %><br/><%
                        }
                %><i class="a"><%= ++ln2 %></i><%= file2[cl2] %><%
                        if(full) {
                %><a name="<%= ln2 %>" /><%
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
                %><i><%= ++ln1 %></i><%=
                    Util.htmlize(file1[j]) %><br/><%
                            }
                %></td><td><%
                            for (int j = ln2; j  < cn2 ; j++) {
                %><i><%= ++ln2 %></i><%=
                    Util.htmlize(file2[j]) %><br/><%
                            }
                        } else {
                            for (int j = ln1; j < ln1 + 8; j++) {
                %><i><%= j+1 %></i><%=
                    Util.htmlize(file1[j]) %><br/><%
                            }
                %><br/>--- <b><%= cn1 - ln1 - 16
                    %> unchanged lines hidden</b> (<a href="<%= reqURI
                    %>?r1=<%= rp1 %>&amp;r2=<%= rp2
                    %>&amp;format=<%= type.getAbbrev()
                    %>&amp;full=1#<%= ln2 %>">view full</a>) --- <br/><br/><%
                            ln1 = cn1 - 8;
                            for (int j = ln1; j < cn1; j++) {
                %><i><%= ++ln1 %></i><%=
                    Util.htmlize(file1[j]) %><br/><%
                            }
                %></td><td><%
                            for (int j = ln2; j < ln2 + 8; j++) {
                %><i><%= j+1 %></i><%=
                    Util.htmlize(file2[j]) %><br/><%
                            }
                %><br/>--- <b><%= cn2 - ln2 - 16
                    %> unchanged lines hidden</b> (<a href="<%= reqURI
                    %>?r1=<%= rp1 %>&amp;r2=<%= rp2
                    %>&amp;format=<%= type.getAbbrev()
                    %>&amp;full=1#<%= ln2 %>">view full</a>) --- <br/><br/><%
                            ln2 = cn2 - 8;
                            for (int j = ln2; j < cn2; j++) {
                %><i><%= ++ln2 %></i><%=
                    Util.htmlize(file2[j]) %><br/><%
                            }
                        }
                %></td>
            </tr><%
                    }
            %>
            <tr class="k"><td><%
                    for (int j = cn1; j  <= cl1; j++) {
                %><i><%= ++ln1 %></i><%= file1[j] %><br/><%
                    }
                %></td><td><%
                    for (int j = cn2; j  <= cl2; j++) {
                %><i><%= ++ln2 %></i><a name="<%= ln2 %>"></a><%=
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
        %><i><%= ++ln1 %></i><%=
            Util.htmlize(file1[j]) %><br/><%
                            }
                        } else {
                            for (int j = ln1; j < ln1 + 8; j++) {
        %><i><%= j+1 %></i><%=
            Util.htmlize(file1[j]) %><br/><%
                            }
        %><br/>--- <b><%= cn1 - ln1 - 16
            %> unchanged lines hidden</b> (<a href="<%= reqURI
            %>?r1=<%= rp1 %>&amp;r2=<%= rp2
            %>&amp;format=<%= type.getAbbrev()
            %>&amp;full=1#<%=ln1%>">view full</a>) --- <br/><br/><%
                            ln1 = cn1 - 8;
                            for (int j = ln1; j < cn1; j++) {
        %><i><%= ++ln1 %></i><%=
            Util.htmlize(file1[j]) %><br/><%
                            }
                        }
                    }
                    for (int j = cn1; j  <= cl1 ; j++) {
        %><i><%= ++ln1 %></i><%= file1[j] %><br/><%
                    }
                    if (full) {
        %><a name="<%=ln1%>" ></a><%
                    }
// NEW
                } else if (type == DiffType.NEW) {
                    if (cn2 > ln2) {
                        if (full || cn2 - ln2 < 20) {
                            for (int j = ln2; j  < cn2 ; j++) {
        %><i><%= ++ln2 %></i><%=
            Util.htmlize(file2[j]) %><br/><%
                            }
                        } else {
                            for (int j = ln2; j < ln2 + 8; j++) {
        %><i><%= j+1 %></i><%=
            Util.htmlize(file2[j]) %><br/><%
                            }
        %><br/>--- <b><%= cn2 - ln2 - 16
            %> unchanged lines hidden</b> (<a href="<%= reqURI
            %>?r1=<%= rp1 %>&amp;r2=<%= rp2
            %>&amp;format=<%= type.getAbbrev()
            %>&amp;full=1#<%= ln2 %>">view full</a>) --- <br/><br/><%
                            ln2 = cn2 - 8;
                            for (int j = ln2; j < cn2; j++) {
            %><i><%= ++ln2 %></i><%=
                Util.htmlize(file2[j]) %><br/><%
                            }
                        }
                    }
                    for (int j = cn2; j  <= cl2 ; j++) {
        %><i><%= ++ln2 %></i><%= file2[j] %><br/><%
                    }
                    if (full) {
        %><a name="<%= ln2 %>"></a><%
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
                    for (int j = ln1; j < file1.length ; j++) {
                %><i><%= j+1 %></i><%= Util.htmlize(file1[j]) %><br/><%
                    }
                %></td><td><%
                    for (int j = ln2; j < file2.length ; j++) {
                %><i><%= j+1 %></i><%= Util.htmlize(file2[j]) %><br/><%
                    }
                %></td>
            </tr>
            </tbody>
        </table><%
                } else {
            %>
            <tr><td><%
                    for (int j = ln1; j < ln1 + 8 ; j++) {
                %><i><%= j+1 %></i><%= Util.htmlize(file1[j]) %><br/><%
                    }
                %><br/> --- <b><%= file1.length - ln1 - 8
                %> unchanged lines hidden</b> --- </td><td><%
                    for (int j = ln2; j < ln2 + 8 ; j++) {
                %><i><%= j+1 %></i><%= Util.htmlize(file2[j]) %><br/><%
                    }
                %><br/>--- <b><%= file1.length - ln1 - 8
                %> unchanged lines hidden</b> ---</td>
            </tr>
            </tbody>
        </table><%
                }
            } else if (type == DiffType.UNIFIED) {
                if (full || file2.length - ln2 < 20) {
            %>
            <tr><td><%
                    for (int j = ln2; j < file2.length ; j++) {
                %><i><%= j+1 %></i><%= Util.htmlize(file2[j]) %><br/><%
                    }
                %></td>
            </tr>
            </tbody>
        </table><%
                } else {
            %>
            <tr><td><%
                    for (int j = ln2; j < ln2 + 8 ; j++) {
                %><i><%= j+1 %></i><%= Util.htmlize(file2[j]) %><br/><%
                    }
                %><br/>--- <b><%= file2.length - ln2 - 8
                %> unchanged lines hidden</b> ---</td>
            </tr>
            </tbody>
        </table><%
                }
            } else if (type == DiffType.OLD) {
                if (full || file1.length - ln1 < 20) {
                    for (int j = ln1; j < file1.length ; j++) {
        %><i><%= j+1 %></i><%= Util.htmlize(file1[j]) %><br/><%
                    }
                } else {
                    for (int j = ln1; j < ln1 + 8 ; j++) {
        %><i><%= j+1 %></i><%= Util.htmlize(file1[j]) %><br/><%
                    }
        %><br/> --- <b><%= file1.length - ln1 - 8
        %> unchanged lines hidden</b> ---<br/><%
                }
            } else if (type == DiffType.NEW) {
                if (full || file2.length - ln2 < 20) {
                    for (int j = ln2; j < file2.length ; j++) {
        %><i><%= j+1 %></i><%=Util.htmlize(file2[j])%><br/><%
                    }
                } else {
                    for (int j = ln2; j < ln2 + 8 ; j++) {
        %><i><%= j+1 %></i><%= Util.htmlize(file2[j]) %><br/><%
                    }
        %><br/> --- <b><%= file2.length - ln2 - 8
        %> unchanged lines hidden</b> ---<br/><%
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

include file="foot.jspf"

%>