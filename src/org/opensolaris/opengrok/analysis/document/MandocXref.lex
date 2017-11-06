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
 * Copyright (c) 2005, 2017, Oracle and/or its affiliates. All rights reserved.
 * Portions Copyright (c) 2017, Chris Fraire <cfraire@me.com>.
 */

package org.opensolaris.opengrok.analysis.document;

import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.io.Writer;
import org.opensolaris.opengrok.analysis.JFlexXref;
import org.opensolaris.opengrok.analysis.plain.PlainXref;
import org.opensolaris.opengrok.configuration.RuntimeEnvironment;

%%
%public
%class MandocXref
%extends JFlexXref
%unicode
%int
%include CommonXref.lexh
%{
    protected boolean didStartTee;
    protected boolean didStartMandoc;
    protected final MandocRunner mandoc = new MandocRunner();
    protected StringWriter plainbuf;

    /**
     * As with {@link TroffXref} we do not want the initial line number
     * written by {@link JFlexXref}.
     */
    @Override
    public void write(Writer out) throws IOException {
        yyline++;
        this.out = out;
        while(yylex() != YYEOF) {
        }
    }

    // TODO move this into an include file when bug #16053 is fixed
    @Override
    protected int getLineNumber() { return yyline; }
    @Override
    protected void setLineNumber(int x) { yyline = x; }

    protected void startTee() throws IOException {
        plainbuf = new StringWriter();

        didStartMandoc = false;
        if (RuntimeEnvironment.getInstance().getMandoc() != null) {
            try {
                mandoc.start();
                didStartMandoc = true;
            } catch (IOException|MandocException e) {
                // nothing we can do here
            }
        }

        didStartTee = true;
    }

    protected void writeTee(String s) throws IOException {
        plainbuf.write(s);
        if (didStartMandoc) mandoc.write(s);
    }
%}

%eof{
    boolean usePlain = true;
    if (didStartMandoc) {
        try {
            String result = mandoc.finish();
            usePlain = false;
            out.write("</pre><div id=\"mandoc\">");
            out.write(result);
            out.write("</div><pre>");
        } catch (IOException|MandocException e) {
            // nothing we can do here
        }
    }
    if (usePlain) {
        try {
            StringReader rdr = new StringReader(plainbuf.toString());
            plainbuf = new StringWriter();

            PlainXref plainxref = new PlainXref(rdr);
            plainxref.project = this.project;
            plainxref.annotation = this.annotation;
            plainxref.write(plainbuf);
            String result = plainbuf.toString();
            out.write(result);
        } catch (IOException e) {
            // nothing we can do here
        }
    }

    didStartTee = false;
    mandoc.destroy();
    plainbuf = null;
%eof}

%%
<YYINITIAL> {
    \w+ |
    [^]    {
        if (!didStartTee) startTee();
        writeTee(yytext());
    }
}
