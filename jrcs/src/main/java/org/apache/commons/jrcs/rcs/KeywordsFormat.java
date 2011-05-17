/*
 * ====================================================================
 *
 * The Apache Software License, Version 1.1
 *
 * Copyright (c) 1999-2003 The Apache Software Foundation.
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 * 1. Redistributions of source code must retain the above copyright
 *    notice, this list of conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright
 *    notice, this list of conditions and the following disclaimer in
 *    the documentation and/or other materials provided with the
 *    distribution.
 *
 * 3. The end-user documentation included with the redistribution, if
 *    any, must include the following acknowledgement:
 *       "This product includes software developed by the
 *        Apache Software Foundation (http://www.apache.org/)."
 *    Alternately, this acknowledgement may appear in the software itself,
 *    if and wherever such third-party acknowledgements normally appear.
 *
 * 4. The names "The Jakarta Project", "Commons", and "Apache Software
 *    Foundation" must not be used to endorse or promote products derived
 *    from this software without prior written permission. For written
 *    permission, please contact apache@apache.org.
 *
 * 5. Products derived from this software may not be called "Apache"
 *    nor may "Apache" appear in their names without prior written
 *    permission of the Apache Software Foundation.
 *
 * THIS SOFTWARE IS PROVIDED ``AS IS'' AND ANY EXPRESSED OR IMPLIED
 * WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES
 * OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED.  IN NO EVENT SHALL THE APACHE SOFTWARE FOUNDATION OR
 * ITS CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
 * LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF
 * USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
 * OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT
 * OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF
 * SUCH DAMAGE.
 * ====================================================================
 *
 * This software consists of voluntary contributions made by many
 * individuals on behalf of the Apache Software Foundation.  For more
 * information on the Apache Software Foundation, please see
 * <http://www.apache.org/>.
 *
 */

/*
 * Copyright (c) 2010, Oracle and/or its affiliates. All rights reserved.
 */

package org.apache.commons.jrcs.rcs;

import java.text.Format;
import java.text.MessageFormat;

/**
 * Formatter for the RCS keywords. It is intended as an helper class to
 * replace the use of gnu.regexp. This class is NOT threadsafe.
 *
 * @author <a href="mailto:sbailliez@apache.org">Stephane Bailliez</a>
 */
final class KeywordsFormat
{
    final Format Header_FORMAT =
            new MessageFormat("\\$Header: {1} {2} {3, date,yyyy/MM/dd HH:mm:ss} {4} {5} \\$");
    final Format Id_FORMAT =
            new MessageFormat("\\$Id: {1} {2} {3, date,yyyy/MM/dd HH:mm:ss} {4} {5} \\$");
    final Format RCSFile_FORMAT =
            new MessageFormat("\\$RCSfile: {1} \\$");
    final Format Revision_FORMAT =
            new MessageFormat("\\$Revision: {2} \\$");
    final Format Date_FORMAT =
            new MessageFormat("\\$Date: {3, date,yyyy/MM/dd HH:mm:ss} \\$");
    final Format Author_FORMAT =
            new MessageFormat("\\$Author: {4} \\$");
    final Format State_FORMAT =
            new MessageFormat("\\$State: {5} \\$");
    final Format Locker_FORMAT =
            new MessageFormat("\\$Locker: {6} \\$");
    final Format Source_FORMAT =
            new MessageFormat("\\$Source: {0} \\$");

    private static final String ID_RE = "\\$Id(:[^\\$]*)?\\$";
    private static final String HEADER_RE = "\\$Header(:[^\\$]*)?\\$";
    private static final String SOURCE_RE = "\\$Source(:[^\\$]*)?\\$";
    private static final String RCSFILE_RE = "\\$RCSfile(:[^\\$]*)?\\$";
    private static final String REVISION_RE = "\\$Revision(:[^\\$]*)?\\$";
    private static final String DATE_RE = "\\$Date(:[^\\$]*)?\\$";
    private static final String AUTHOR_RE = "\\$Author(:[^\\$]*)?\\$";
    private static final String STATE_RE = "\\$State(:[^\\$]*)?\\$";
    private static final String LOCKER_RE = "\\$Locker(:[^\\$]*)?\\$";

    /**
     * update the given text made of RCS keywords with the appropriate
     * revision info.
     * @param text the input text containing the RCS keywords.
     * @param revisionInfo the revision information.
     * @return the formatted text with the RCS keywords.
     */
    String update(String text, Object[] revisionInfo)
    {
        String data = text
                .replaceAll(ID_RE, Id_FORMAT.format(revisionInfo))
                .replaceAll(HEADER_RE, Header_FORMAT.format(revisionInfo))
                .replaceAll(SOURCE_RE, Source_FORMAT.format(revisionInfo))
                .replaceAll(RCSFILE_RE, RCSFile_FORMAT.format(revisionInfo))
                .replaceAll(REVISION_RE, Revision_FORMAT.format(revisionInfo))
                .replaceAll(DATE_RE, Date_FORMAT.format(revisionInfo))
                .replaceAll(AUTHOR_RE, Author_FORMAT.format(revisionInfo))
                .replaceAll(STATE_RE, State_FORMAT.format(revisionInfo))
                .replaceAll(LOCKER_RE, Locker_FORMAT.format(revisionInfo));
        //@TODO: should do something about Name and Log
        return data;
    }

    /**
     * Reinitialize all RCS keywords match.
     * @param text the text to look for RCS keywords.
     * @return the text with initialized RCS keywords.
     */
    String reset(String text)
    {
        String data = text
                .replaceAll(ID_RE, "\\$Id\\$")
                .replaceAll(HEADER_RE, "\\$Header\\$")
                .replaceAll(SOURCE_RE, "\\$Source\\$")
                .replaceAll(RCSFILE_RE, "\\$RCSfile\\$")
                .replaceAll(REVISION_RE, "\\$Revision\\$")
                .replaceAll(DATE_RE, "\\$Date\\$")
                .replaceAll(AUTHOR_RE, "\\$Author\\$")
                .replaceAll(STATE_RE, "\\$State\\$")
                .replaceAll(LOCKER_RE, "\\$Locker\\$");
        //@TODO: should do something about Name and Log
        return data;
    }
}
