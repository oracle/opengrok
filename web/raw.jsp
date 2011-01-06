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

Copyright 2008 Sun Microsystems, Inc.  All rights reserved.
Use is subject to license terms.
--%><%@page import="java.io.File,
java.io.InputStream,
java.io.OutputStream,
java.io.FileInputStream,
java.io.FileNotFoundException,
org.opensolaris.opengrok.configuration.RuntimeEnvironment,
org.opensolaris.opengrok.history.HistoryGuru"%><%
String path = request.getPathInfo();
if (path == null) {
  path = "";
}
RuntimeEnvironment env = RuntimeEnvironment.getInstance();
File file = new File(env.getSourceRootPath(), path);
try {
  path = env.getPathRelativeToSourceRoot(file, 0);
} catch (FileNotFoundException e) {
  response.sendError(response.SC_NOT_FOUND);
  return;
}

if (!file.exists() || !file.canRead() || RuntimeEnvironment.getInstance().getIgnoredNames().ignore(file)) {
  response.sendError(response.SC_NOT_FOUND);
  return;
} else if (file.isDirectory()) {
  response.sendError(response.SC_NOT_FOUND, "Can't download a directory");
  return;
}

String mimeType = getServletContext().getMimeType(file.getAbsolutePath());
response.setContentType(mimeType);

String revision = request.getParameter("r");
if (revision != null && revision.length() == 0) {
  revision = null;
}

InputStream in = null;

if (revision != null) {
  try{
    in = HistoryGuru.getInstance().getRevision(file.getParent(), file.getName(), revision);
  } catch (Exception e) {
    response.sendError(404, "Revision not found");
    return ;
  }
} else {
  response.setContentLength((int)file.length());
  response.setDateHeader("Last-Modified", file.lastModified());
  in = new FileInputStream(file);
}

try {
  response.setHeader("content-disposition", "attachment; filename=" + file.getName());

  OutputStream o = response.getOutputStream();
  byte[] buffer = new byte[8192];
  int nr;
  while ((nr = in.read(buffer)) > 0) {
    o.write(buffer, 0, nr);
  }
  o.flush();
  o.close();
} finally {
  in.close();
}
%>
