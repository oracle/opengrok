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
 * Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
 */
package org.opensolaris.opengrok.analysis;

import java.io.IOException;
import java.io.Reader;
import java.lang.reflect.Field;
import org.opensolaris.opengrok.analysis.Definitions.Tag;
import org.opensolaris.opengrok.analysis.Scopes.Scope;

/**
 *
 * @author kotal
 */
public abstract class JFlexScopeParser {
    
    protected Scopes scopes = new Scopes();
    protected Scope scope;
    
    protected final int yyeof;
    
    public JFlexScopeParser() {
        try {
            Field f = getClass().getField("YYEOF");
            yyeof = f.getInt(null);
        }catch(Exception e) {
            AssertionError ae = new AssertionError("Couldn't initialize yyeof");
            ae.initCause(e);
            throw ae;
        }    
    }
    
    public final void reInit(Reader reader) {
        this.yyreset(reader);
    }      
    
    public Scopes getScopes() {
        return scopes;
    }
    
    public void parse(Tag tag, Reader reader) throws IOException {
        try {
            int lineNo = 0;
            while (lineNo < tag.line) {
                if (reader.read() == '\n') {
                    lineNo++;
                }
            }
        } catch(IOException e) {
            System.out.print(e.getMessage());
        }

        reInit(reader);
        setLineNumber(tag.line+1);
        start(tag.text);

        scope = new Scope(tag.line, tag.line, tag.symbol, tag.scope);
        while (yylex() != yyeof) { // NOPMD while statement intentionally empty
            // nothing to do here, yylex() will do the work
        }
        scopes.addScope(scope);
    }
    
    protected abstract void start(String defText);
    
    /**
     * Get the next token from the scanner.
     */
    public abstract int yylex() throws IOException;

    /**
     * Reset the scanner.
     */
    public abstract void yyreset(Reader reader);

    /**
     * Get the value of {@code yyline}.
     */
    protected abstract int getLineNumber();

    /**
     * Set the value of {@code yyline}.
     */
    protected abstract void setLineNumber(int x);

    public abstract void yybegin(int newState);

    public abstract int yystate();
    
}
