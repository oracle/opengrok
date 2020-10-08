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
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/functions" prefix="fn" %>
<%@ taglib prefix="opengrok" tagdir="/WEB-INF/tags" %>

<%@ attribute name="pageConfig" required="true" type="org.opengrok.web.PageConfig" %>
<%@ attribute name="repositories" required="true" type="java.util.Set<org.opengrok.indexer.configuration.Project>" %>

<c:if test="${repositories.size() > 0}">
    <thead>
    <tr>
        <td><b>Repository</b></td>
        <td><b>SCM Type: Parent (branch)</b></td>
        <td><b>Current version</b></td>
    </tr>
    </thead>
    <tbody>
    <c:forEach var="project" items="${repositories}">
        <c:if test="${project.indexed}">
            <c:forEach var="repositoryInfo"
                       items="${pageConfig.projectHelper.getSortedRepositoryInfo(project)}"
                       varStatus="iterator">
                <c:if test="${iterator.first || repositoryInfo.parent != null}">
                    <opengrok:repository
                            pageConfig="${pageConfig}"
                            project="${project}"
                            repositoryInfo="${repositoryInfo}"
                            isFirst="${iterator.first}"
                    />
                </c:if>
            </c:forEach>
        </c:if>
    </c:forEach>
    </tbody>
</c:if>
