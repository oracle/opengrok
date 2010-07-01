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

import java.util.List;

/**
 * Represents a node on the trunk or main branch of a version control Archive.
 *
 * <p>A {@link TrunkNode TrunkNode} stores the deltas between the node's
 * revision and the previous revision;
 * that is, when the deltas are applied to the current revision, the
 * text of the previous revision is obtained.
 * The {@link Node#rcsnext rcsnext} field of a TrunkNode
 * points to the node corresponding to the previous revision.</p>
 * This class is NOT thread safe.
 *
 * @see Node
 * @see Archive
 *
 * @author <a href="mailto:juanco@suigeneris.org">Juanco Anez</a>
 * @version $Id: TrunkNode.java,v 1.4 2003/10/13 07:59:46 rdonkin Exp $
 */
class TrunkNode
        extends Node
{

    /**
     * Create a TrunkNode bu copying another TrunkNode.
     */
    TrunkNode(TrunkNode other)
    {
        super(other);
    }

    /**
     * Create a TrunkNode.
     * The next field in a TrunkNode points to the immediate
     * previos revision or parent.
     */
    TrunkNode(Version vernum, TrunkNode next)
            throws InvalidTrunkVersionNumberException
    {
        super(vernum, next);
        if (vernum.size() > 2)
        {
            throw new InvalidTrunkVersionNumberException(vernum);
        }
    }

    /**
     * Set the next node in the RCS logical hierarcy.
     * Update the _parent and _child node accordingly.
     * For a TrunkNode, the RCS-next is the immediate parent.
     */
    public void setRCSNext(Node node)
    {
        super.setRCSNext(node);
        if (this.getParent() != null)
        {
            this.getParent().child = null;
        }
        this.parent = node;
        if (this.getParent() != null)
        {
            this.getParent().child = this;
        }
    }


    public Node deltaRevision()
    {
        return (getChild() != null ? getChild() : this);
    }

    public Node nextInPathTo(Version vernum, boolean soft)
            throws NodeNotFoundException
    {
        Version branchPoint = vernum.getBase(2);
        if (this.version.isLessThan(branchPoint))
        {
            if (soft)
            {
                return null;
            }
            else
            {
                throw new NodeNotFoundException(vernum);
            }
        }

        Version thisBase = this.version.getBase(branchPoint.size());
        if (thisBase.isGreaterThan(branchPoint))
        {
            return getParent();
        }
        else if (vernum.size() > this.version.size())
        {
            Node branch = getBranch(vernum.at(this.version.size()));
            if (branch != null || soft)
            {
                return branch;
            }
            else
            {
                throw new BranchNotFoundException(vernum.getBase(this.version.size() + 1));
            }
        }
        else
        {
            return null;
        }
    }

    /**
     * Provide the initial patch.
     * Used only for head nodes.
     * @param original Where to add the patch to.
     * @param annotate True if the lines should be annotated with version numbers.
     */
    protected void patch0(List original, boolean annotate)
            throws InvalidFileFormatException,
            NodeNotFoundException,
            org.apache.commons.jrcs.diff.PatchFailedException
    {
        Node root = this.root();
        for (int it = 0; it < getText().length; it++)
        {
            original.add(new Line(root, getText()[it]));
        }
        if (annotate && getParent() != null)
        {
            getParent().pathTo(root.version).patch(original, true);
        }
    }
}

