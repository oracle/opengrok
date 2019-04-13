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

<%@ attribute name="projects" required="true" type="java.util.Set<org.opengrok.indexer.configuration.Project>" %>

<c:if test="${projects.size() > 0}">
    <thead>
    <tr>
        <td colspan="3"><b>Project</b></td>
    </tr>
    </thead>
    <tbody>
    <c:forEach var="project" items="${projects}">
        <c:if test="${project.indexed}">
            <opengrok:project
                    project="${project}"
            />
        </c:if>
    </c:forEach>
    </tbody>
</c:if>
