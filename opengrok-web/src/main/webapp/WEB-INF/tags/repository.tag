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

Copyright (c) 2019, Oracle and/or its affiliates. All rights reserved.
Portions Copyright (c) 2019, Krystof Tulinger <k.tulinger@seznam.cz>.
--%>
<%@ tag import="org.apache.commons.lang3.ObjectUtils" %>
<%@ tag import="org.opengrok.indexer.web.Prefix" %>
<%@ tag import="org.opengrok.indexer.web.Util" %>
<%@ tag import="org.opengrok.indexer.web.messages.MessagesUtils" %>

<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/functions" prefix="fn" %>

<%@ attribute name="pageConfig" required="true" type="org.opengrok.web.PageConfig" %>
<%@ attribute name="project" required="true" type="org.opengrok.indexer.configuration.Project" %>
<%@ attribute name="repositoryInfo" required="true" type="org.opengrok.indexer.history.RepositoryInfo" %>
<%@ attribute name="isFirst" required="true" type="java.lang.Boolean" %>

<c:set var="isSubrepository" value="${!Util.fixPathIfWindows(repositoryInfo.getDirectoryNameRelative()).equals(project.getPath())}"/>
<c:set var="name" value="${project.name}"/>
<c:set var="defaultLength" value="${Integer.valueOf(10)}"/>
<c:set var="NA" value="N/A"/>
<c:set var="summary" value="${ObjectUtils.defaultIfNull(repositoryInfo.currentVersion, NA)}"/>
<c:set var="maxLength" value="${Math.max(defaultLength, pageConfig.currentIndexedCollapseThreshold)}"/>

<c:if test="${isSubrepository}">
    <c:set var="name"
           value="${pageConfig.getRelativePath(pageConfig.sourceRootPath, repositoryInfo.directoryName)}"/>
</c:if>

<c:if test="${isSubrepository && isFirst}">
    <tr>
        <td class="name repository" colspan="3">
            <a href="${pageContext.request.getContextPath()}${Prefix.XREF_P.toString()}/${project.name}"
               title="Xref for project ${Util.htmlize(project.name)}">
                    ${Util.htmlize(project.name)}
            </a>
        </td>
    </tr>
</c:if>

<tr>
    <td class="name ${isSubrepository ? "subrepository" : "repository"}">
        <a href="${pageContext.request.getContextPath()}${Prefix.XREF_P.toString()}/${name}"
           title="Xref for project ${Util.htmlize(name)}">
            ${Util.htmlize(name)}
        </a>

        <c:set var="messages" value="${MessagesUtils.messagesToJson(project)}"/>
        <c:if test="${not empty messages}">
            <span class="note-${MessagesUtils.getMessageLevel(project.getName())} important-note important-note-rounded"
                  data-messages='${messages}'>!</span>
        </c:if>
    </td>
    <td>${Util.htmlize(ObjectUtils.defaultIfNull(repositoryInfo.type, "N/A"))}:
        ${Util.linkify(ObjectUtils.defaultIfNull(Util.redactUrl(repositoryInfo.parent), "N/A"))}
        (${Util.htmlize(ObjectUtils.defaultIfNull(repositoryInfo.branch, "N/A"))})
    </td>
    <td>
        <c:choose>
            <c:when test="${summary.length() > maxLength}">
                <span class="rev-message-summary">${Util.htmlize(summary.substring(0, maxLength))}</span>
                <span class="rev-message-full rev-message-hidden">${Util.htmlize(summary)}</span>
                <span data-toggle-state="less"><a class="rev-toggle-a rev-message-toggle"
                                                  href="#">show more ... </a></span>
            </c:when>
            <c:otherwise>
                <span class="rev-message-full">${Util.htmlize(summary)}</span>
            </c:otherwise>
        </c:choose>
    </td>
</tr>
