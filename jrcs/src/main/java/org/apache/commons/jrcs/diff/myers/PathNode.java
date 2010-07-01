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

package org.apache.commons.jrcs.diff.myers;

/**
 * A node in a diffpath.
 *
 * @version $Revision: 1.7 $ $Date: 2003/10/13 08:00:24 $
 * @author <a href="mailto:juanco@suigeneris.org">Juanco Anez</a>
 *
 * @see DiffNode
 * @see Snake
 *
 */
public abstract class PathNode
{
    /** Position in the original sequence. */
    public final int i;
    /** Position in the revised sequence. */
    public final int j;
    /** The previous node in the path. */
    public final PathNode prev;

    /**
     * Concatenates a new path node with an existing diffpath.
     * @param i The position in the original sequence for the new node.
     * @param j The position in the revised sequence for the new node.
     * @param prev The previous node in the path.
     */
    public PathNode(int i, int j, PathNode prev)
    {
        this.i = i;
        this.j = j;
        this.prev = prev;
    }

    /**
     * Is this node a {@link Snake Snake node}?
     * @return true if this is a {@link Snake Snake node}
     */
    public abstract boolean isSnake();

    /**
     * Is this a bootstrap node?
     * <p>
     * In bottstrap nodes one of the two corrdinates is
     * less than zero.
     * @return tru if this is a bootstrap node.
     */
    public boolean isBootstrap()
    {
        return i < 0 || j < 0;
    }

    /**
     * Skips sequences of {@link DiffNode DiffNodes} until a
     * {@link Snake} or bootstrap node is found, or the end
     * of the path is reached.
     * @return The next first {@link Snake} or bootstrap node in the path, or
     * <code>null</code>
     * if none found.
     */
    public final PathNode previousSnake()
    {
        if (isBootstrap())
            return null;
        if (!isSnake() && prev != null)
            return prev.previousSnake();
        return this;
    }

    /**
     * {@inheritDoc}
     */
    public String toString()
    {
        StringBuffer buf = new StringBuffer("[");
        PathNode node = this;
        while (node != null)
        {
            buf.append("(");
            buf.append(Integer.toString(node.i));
            buf.append(",");
            buf.append(Integer.toString(node.j));
            buf.append(")");
            node = node.prev;
        }
        buf.append("]");
        return buf.toString();
    }
}