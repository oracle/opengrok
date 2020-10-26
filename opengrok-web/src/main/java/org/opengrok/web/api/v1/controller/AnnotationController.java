/*
 * CDDL HEADER START
 *
 * The contents of this file are subject to the terms of the
 * Common Development and Distribution License (the "License").
 * You may not use this file except in compliance with the License.
 *
 * See LICENSE.txt included in this distribution for the specific
 * language governing permissions and limitations under the License.
 *
 * When distributing Covered Code, include this CDDL HEADER in each
 * file and include the License file at LICENSE.txt.
 * If applicable, add the following below this CDDL HEADER, with the
 * fields enclosed by brackets "[]" replaced with your own identifying
 * information: Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 */

/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
 */
package org.opengrok.web.api.v1.controller;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.opengrok.indexer.history.Annotation;
import org.opengrok.indexer.history.HistoryGuru;
import org.opengrok.web.api.v1.filter.CorsEnable;
import org.opengrok.web.api.v1.filter.PathAuthorized;
import org.opengrok.web.util.NoPathParameterException;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.opengrok.web.util.FileUtil.toFile;

@Path(AnnotationController.PATH)
public class AnnotationController {

    public static final String PATH = "/annotation";

    static class AnnotationDTO {
        @JsonProperty
        private String revision;
        @JsonProperty
        private String author;
        @JsonProperty
        private String description;
        @JsonProperty
        private String version;

        // for testing
        AnnotationDTO() {
        }

        AnnotationDTO(String revision, String author, String description, String version) {
            this.revision = revision;
            this.author = author;
            this.description = description;
            this.version = version;
        }

        // for testing
        public String getAuthor() {
            return this.author;
        }

        // for testing
        public String getRevision() {
            return this.revision;
        }

        // for testing
        public String getDescription() {
            return this.description;
        }

        // for testing
        public String getVersion() {
            return this.version;
        }
    }

    @GET
    @CorsEnable
    @PathAuthorized
    @Produces(MediaType.APPLICATION_JSON)
    public List<AnnotationDTO> getContent(@Context HttpServletRequest request,
                                          @Context HttpServletResponse response,
                                          @QueryParam("path") final String path,
                                          @QueryParam("revision") final String revision)
            throws IOException, NoPathParameterException {

        File file = toFile(path);

        Annotation annotation = HistoryGuru.getInstance().annotate(file,
                revision == null || revision.isEmpty() ? null : revision);

        ArrayList<AnnotationDTO> annotationList = new ArrayList<>();
        for (int i = 1; i <= annotation.size(); i++) {
            annotationList.add(new AnnotationDTO(annotation.getRevision(i),
                    annotation.getAuthor(i),
                    annotation.getDesc(annotation.getRevision(i)),
                    annotation.getFileVersion(annotation.getRevision(i)) + "/" + annotation.getRevisions().size()));
        }

        return annotationList;
    }
}
