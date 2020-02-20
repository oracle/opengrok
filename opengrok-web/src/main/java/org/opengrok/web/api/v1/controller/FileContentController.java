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
 * Copyright (c) 2020 Oracle and/or its affiliates. All rights reserved.
 */

package org.opengrok.web.api.v1.controller;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.opengrok.indexer.configuration.RuntimeEnvironment;
import org.opengrok.web.api.v1.filter.CorsEnable;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;

import static org.opengrok.web.util.AuthPathUtil.isPathAuthorized;

@Path(FileContentController.PATH)
public class FileContentController {

    public static final String PATH = "/filecontent";

    private static final RuntimeEnvironment env = RuntimeEnvironment.getInstance();

    class FileContentDTO {
        @JsonProperty
        ArrayList<String> lines;

        FileContentDTO() {
            lines = new ArrayList<>();
        }

        public ArrayList<String> getLines() {
            return lines;
        }

        public void add(String line) {
            lines.add(line);
        }
    }

    @GET
    @CorsEnable
    @Produces(MediaType.APPLICATION_JSON)
    public Object get(@Context HttpServletRequest request,
                      @Context HttpServletResponse response,
                      @QueryParam("path") final String path) throws IOException {

        if (!isPathAuthorized(path, request)) {
            response.sendError(Response.status(Response.Status.FORBIDDEN).build().getStatus(),
                    "not authorized");
            return null;
        }

        // TODO: identify the file type and return only for text files

        File file = new File(env.getSourceRootFile(), path);
        if (!file.isFile()) {
            // TODO: set error
            return null;
        }
        FileContentDTO fileContent = new FileContentDTO();
        try (BufferedReader br = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = br.readLine()) != null) {
               fileContent.add(line);
            }
        }

        // TODO: array of lines with line number
        return fileContent.getLines();
    }
}
