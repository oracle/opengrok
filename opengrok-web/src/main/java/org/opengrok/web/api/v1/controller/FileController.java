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
 * Portions Copyright (c) 2020, Chris Fraire <cfraire@me.com>.
 */
package org.opengrok.web.api.v1.controller;

import org.apache.lucene.document.Document;
import org.apache.lucene.queryparser.classic.ParseException;
import org.opengrok.indexer.analysis.AbstractAnalyzer;
import org.opengrok.indexer.configuration.RuntimeEnvironment;
import org.opengrok.indexer.search.QueryBuilder;
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
import javax.ws.rs.core.StreamingOutput;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;

import static org.opengrok.indexer.index.IndexDatabase.getDocument;
import static org.opengrok.web.util.FileUtil.toFile;

@Path(FileController.PATH)
public class FileController {

    public static final String PATH = "/file";

    private static final RuntimeEnvironment env = RuntimeEnvironment.getInstance();

    private StreamingOutput transfer(File file) throws FileNotFoundException {
        InputStream in = new FileInputStream(file);
        return out -> {
            try {
                byte[] buffer = new byte[1024];
                int len = in.read(buffer);
                while (len != -1) {
                    out.write(buffer, 0, len);
                    len = in.read(buffer);
                }
            } finally {
                in.close();
            }
        };
    }

    @GET
    @CorsEnable
    @PathAuthorized
    @Path("/content")
    @Produces(MediaType.TEXT_PLAIN)
    public StreamingOutput getContentPlain(@Context HttpServletRequest request,
                             @Context HttpServletResponse response,
                             @QueryParam("path") final String path) throws IOException, ParseException, NoPathParameterException {

        File file = toFile(path);
        if (file == null) {
            // error already set in the response
            return null;
        }

        Document doc;
        if ((doc = getDocument(file)) == null) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND, "Cannot get document for file");
            return null;
        }

        String fileType = doc.get(QueryBuilder.T);
        if (!AbstractAnalyzer.Genre.PLAIN.typeName().equals(fileType)) {
            response.sendError(HttpServletResponse.SC_NOT_ACCEPTABLE, "Not a text file");
            return null;
        }

        return transfer(file);
    }

    @GET
    @CorsEnable
    @PathAuthorized
    @Path("/content")
    @Produces(MediaType.APPLICATION_OCTET_STREAM)
    public StreamingOutput getContentOctets(@Context HttpServletRequest request,
                                           @Context HttpServletResponse response,
                                           @QueryParam("path") final String path) throws IOException, ParseException, NoPathParameterException {

        File file = toFile(path);
        if (file == null) {
            return null;
        }

        Document doc;
        if ((doc = getDocument(file)) == null) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND, "Cannot get document for file");
            return null;
        }

        try {
            return transfer(file);
        } catch (FileNotFoundException e) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND, "Cannot find file");
            return null;
        }
    }

    @GET
    @CorsEnable
    @PathAuthorized
    @Path("/genre")
    @Produces(MediaType.TEXT_PLAIN)
    public String getGenre(@Context HttpServletRequest request,
                           @Context HttpServletResponse response,
                           @QueryParam("path") final String path) throws IOException, ParseException, NoPathParameterException {

        File file = toFile(path);
        if (file == null) {
            // error already set in the response
            return null;
        }

        Document doc;
        if ((doc = getDocument(file)) == null) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND, "Cannot get document for file");
            return null;
        }

        AbstractAnalyzer.Genre genre = AbstractAnalyzer.Genre.get(doc.get(QueryBuilder.T));
        if (genre == null) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND, "Cannot get genre from the document");
            return null;
        }

        return genre.toString();
    }
}
