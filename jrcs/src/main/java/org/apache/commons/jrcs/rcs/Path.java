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

package org.apache.commons.jrcs.rcs;

import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

import org.apache.commons.jrcs.diff.PatchFailedException;

/**
 * A path from the head revision to a given revision in an Archive.
 * Path collaborates with Node in applying the set of deltas contained
 * in archive nodes to arrive at the text of the revision corresponding
 * to the last node in the path.
 * This class is NOT thread safe.
 *
 * @see Archive
 * @see Node
 *
 * @author <a href="mailto:juanco@suigeneris.org">Juanco Anez</a>
 * @version $Id: Path.java,v 1.4 2003/10/13 07:59:46 rdonkin Exp $
 */
class Path
{
    private List path = new LinkedList();

    /**
     * Creates an empty Path
     */
    public Path()
    {
    }

    /**
     * Add a node to the Path.
     * @param node The Node to add.
     */
    public void add(Node node)
    {
        path.add(node);
    }


    /**
     * The size of the Path.
     * @return The size of the Path
     */
    public int size()
    {
        return path.size();
    }

    /**
     * Return the last node in the path or null if the path is empty.
     * @return the last node in the path or null if the path is empty.
     */
    public Node last()
    {
        if (size() == 0)
        {
            return null;
        }
        else
        {
            return (Node) path.get(size() - 1);
        }
    }


    /**
     * Returns the text that corresponds to applying the patches
     * in the list of nodes in the Path.
     * Assume that the text of the first node is plaintext and not
     * deltatext.
     * @return The resulting text after the patches
     */
    public List patch()
            throws InvalidFileFormatException,
            PatchFailedException,
            NodeNotFoundException
    {
        return patch(false);
    }

    /**
     * Returns the text that corresponds to applying the patches
     * in the list of nodes in the Path.
     * Assume that the text of the first node is plaintext and not
     * deltatext.
     * @param annotate if true, then each text line is a
     * {@link Line Line} with the original text annotated with
     * the revision in which it was last changed or added.
     * @return The resulting text after the patches
     */
    public List patch(boolean annotate)
            throws InvalidFileFormatException,
            PatchFailedException,
            NodeNotFoundException
    {
        return patch(new Lines(), annotate);
    }

    /**
     * Returns the text that corresponds to applying the patches
     * in the list of nodes in the Path.
     * Assume that the text of the first node is plaintext and not
     * deltatext.
     * @param lines The list to where the text must be added and the
     * patches applied.
     * {@link Line Line} with the original text annotated with
     * the revision in which it was last changed or added.
     * @return The resulting text after the patches
     */
    public List patch(List lines)
            throws InvalidFileFormatException,
            PatchFailedException,
            NodeNotFoundException
    {
        return patch(lines, false);
    }

    /**
     * Returns the text that corresponds to applying the patches
     * in the list of nodes in the Path.
     * Assume that the text of the first node is plaintext and not
     * deltatext.
     * @param lines The list to where the text must be added and the
     * patches applied.
     * @param annotate if true, then each text line is a
     * {@link Line Line} with the original text annotated with
     * the revision in which it was last changed or added.
     * @return The resulting text after the patches
     */
    public List patch(List lines, boolean annotate)
            throws InvalidFileFormatException,
            PatchFailedException,
            NodeNotFoundException
    {
        Iterator p = path.iterator();

        // get full text of first node
        TrunkNode head = (TrunkNode) p.next();
        head.patch0(lines, annotate);

        // the rest are patches
        while (p.hasNext())
        {
            Node n = (Node) p.next();
            n.patch(lines, annotate);
        }
        return lines;
    }
}
