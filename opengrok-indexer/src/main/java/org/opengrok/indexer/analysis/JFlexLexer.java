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
 * Copyright (c) 2017, Chris Fraire <cfraire@me.com>.
 */
package org.opengrok.indexer.analysis;

import java.io.IOException;
import java.io.Reader;

/**
 * Represents an API for lexers created by JFlex for {@code %type int}.
 * <p>http://jflex.de
 */
public interface JFlexLexer {

    /**
     * Gets the matched input text as documented by JFlex.
     * @return "the matched input text region"
     */
    String yytext();

    /**
     * Gets the matched input text length as documented by JFlex.
     * @return "the length of the matched input text region (does not require a
     * String object to be created)"
     */
    int yylength();

    /**
     * Gets a character from the matched input text as documented by JFlex.
     * @param pos "a value from 0 to {@link #yylength()}-1"
     * @return "the character at position {@code pos} from the matched text. It
     * is equivalent to {@link #yytext()} then {@link String#charAt(int)} --
     * but faster."
     */
    char yycharat(int pos);

    /**
     * Closes the input stream [as documented by JFlex]. All subsequent calls
     * to the scanning method will return the end of file value.
     * @throws IOException if an error occurs while closing
     */
    void yyclose() throws IOException;

    /**
     * Closes the current input stream [as documented by JFlex], and resets
     * the scanner to read from a new Reader.
     * @param reader the new reader
     */
    void yyreset(Reader reader);

    /**
     * Gets the current lexical state as documented by JFlex.
     * @return "the current lexical state of the scanner."
     */
    int yystate();

    /**
     * Enters the lexical state {@code lexicalState} [as documented by JFlex].
     * @param lexicalState the new state
     */
    void yybegin(int lexicalState);

    /**
     * "Pushes {@code number} characters of the matched text back into the
     * input stream [as documented by JFlex].
     * <p>[The characters] will be read again in the next call of the scanning
     * method.
     * <p>The number of characters to be read again must not be greater than
     * the length of the matched text. The pushed back characters will not be
     * included in {@link #yylength()} and {@link #yytext()}."
     * @param number the [constrained] number of characters
     */
    void yypushback(int number);

    /**
     * "Runs the scanner [as documented by JFlex].
     * <p>[The method] can be used to get the next token from the input."
     * <p>"Consume[s] input until one of the expressions in the specification
     * is matched or an error occurs."
     * @return a value returned by the lexer specification if defined or the
     * {@code EOF} value upon reading end-of-file
     * @throws IOException if an error occurs reading the input
     */
    int yylex() throws IOException;
}
