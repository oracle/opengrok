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
 * Copyright (c) 2005, 2021, Oracle and/or its affiliates. All rights reserved.
 * Portions Copyright (c) 2017, Chris Fraire <cfraire@me.com>.
 */

package org.opengrok.indexer.analysis.document;

import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.io.Writer;
import org.opengrok.indexer.analysis.JFlexNonXref;
import org.opengrok.indexer.analysis.JFlexXref;
import org.opengrok.indexer.analysis.plain.PlainXref;
import org.opengrok.indexer.configuration.RuntimeEnvironment;
%%
%public
%class MandocXref
%extends JFlexNonXref
%unicode
%int
%char
%init{
    /*
     * Keep this antiquated management of yyline for a JFlexNonXref subclass.
     * Hopefully JFlexNonXref will be retired before too long.
     */
    yyline = 1;
%init}
%include ../CommonLexer.lexh
// do not include CommonXref.lexh in JFlexNonXref subclasses
%{
    protected boolean didStartTee;
    protected boolean didStartMandoc;
    protected final MandocRunner mandoc = new MandocRunner();
    protected StringWriter plainbuf;

    /**
     * As with {@link TroffXref} we do not want the initial line number
     * written by {@link JFlexNonXref}.
     */
    @Override
    public void write(Writer out) throws IOException {
        this.out = out;
        while(yylex() != YYEOF) {
        }
    }

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

    protected void writeTee() throws IOException {
        if (!didStartTee) startTee();
        String s = yytext();
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
    try {
        StringReader rdr = new StringReader(plainbuf.toString());
        plainbuf = new StringWriter();

        JFlexXref plainxref = new JFlexXref(new PlainXref(rdr));
        plainxref.setProject(this.project);
        plainxref.setAnnotation(this.annotation);
        plainxref.write(plainbuf);

        loc = plainxref.getLOC();

        if (usePlain) {
            String result = plainbuf.toString();
            out.write(result);
        }
    } catch (IOException e) {
        // nothing we can do here
    }

    didStartTee = false;
    mandoc.destroy();
    plainbuf = null;
%eof}

%include ../Common.lexh
%%
<YYINITIAL> {
    {EOL}    {
        writeTee();
        setLineNumber(++yyline);
    }

    {WhspChar}+ |
    \w+ |
    [^]    {
        writeTee();
    }
}
