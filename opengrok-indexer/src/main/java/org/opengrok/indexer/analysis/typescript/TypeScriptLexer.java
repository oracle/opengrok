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
 * Copyright (c) 2017, 2019, Chris Fraire <cfraire@me.com>.
 */

package org.opengrok.indexer.analysis.typescript;

import org.opengrok.indexer.analysis.JFlexJointLexer;
import org.opengrok.indexer.analysis.JFlexSymbolMatcher;
import org.opengrok.indexer.analysis.Resettable;
import org.opengrok.indexer.web.HtmlConsts;

import java.io.IOException;
import java.util.Stack;

/**
 * Represents an abstract base class for TypeScript lexers.
 */
@SuppressWarnings("Duplicates")
abstract class TypeScriptLexer extends JFlexSymbolMatcher implements JFlexJointLexer, Resettable {

    private TypeScriptLexerData data;

    /**
     * Represents the stack of data if interpolation is nested.
     */
    private Stack<TypeScriptLexerData> dataStack;

    TypeScriptLexer() {
        data = new TypeScriptLexerData();
        // dataStack is null to begin.
    }

    /**
     * Resets the instance to an initial state.
     */
    @Override
    public void reset() {
        super.reset();
        data = new TypeScriptLexerData();
        if (dataStack != null) {
            dataStack.clear();
        }
    }

    /**
     * Calls {@link #phLOC()} if the yystate is not COMMENT or SCOMMENT.
     */
    public void chkLOC() {
        if (yystate() != COMMENT() && yystate() != SCOMMENT()) {
            phLOC();
        }
    }

    /**
     * Resets the substitution brace counter to 1.
     */
    void substitutionOp() {
        data.nEndBrace = 1;
    }

    /**
     * Determine if template substitution should end based on the first
     * character of {@code capture}, and also recognizing tokens that increase
     * the nesting level alternatively.
     * <p>
     * Calling this method has side effects to possibly modify
     * {@code nEndBrace}.
     * @return {@code true} if the substitution state does not end
     */
    boolean notInTemplateOrSubstitutionDoesNotEnd(String capture) throws IOException {
        if (data.nEndBrace <= 0) {
            return true;
        }
        if (capture.startsWith("}")) {
            if (--data.nEndBrace <= 0) {
                int nRemaining = capture.length() - 1;
                String opener = capture.substring(0, 1);
                popData();
                yypop();
                disjointSpan(HtmlConsts.STRING_CLASS);
                offer(opener);
                if (nRemaining > 0) {
                    yypushback(nRemaining);
                }
                return false;
            }
        }
        if (capture.startsWith("{")) {
            ++data.nEndBrace;
        }
        return true;
    }

    void pushData() {
        if (dataStack == null) {
            dataStack = new Stack<>();
        }
        dataStack.push(data);
        data = new TypeScriptLexerData();
    }

    private void popData() {
        data = dataStack.pop();
    }

    /**
     * Subclasses must override to get the constant value created by JFlex to
     * represent COMMENT.
     */
    abstract int COMMENT();

    /**
     * Subclasses must override to get the constant value created by JFlex to
     * represent SCOMMENT.
     */
    abstract int SCOMMENT();

    private static class TypeScriptLexerData {
        /**
         * When interpolating inside `` with ${, the number of remaining '}'
         * characters is stored. It starts at 1, and any nesting increases the
         * value.
         */
        int nEndBrace;
    }
}
