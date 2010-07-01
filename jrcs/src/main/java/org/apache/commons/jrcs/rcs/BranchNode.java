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

/**
 * Represents a branch node in a version control archive.
 * This class is NOT thread safe.
 *
 * <p>A {@link BranchNode BranchNode} stores the deltas between the previous revision
 * and the current revision; that is, when the deltas are applied
 * to the previous revision, the text of the current revision is obtained.
 * The {@link Node#rcsnext rcsnext} field of a BranchNode points to
 * the next revision in the branch.
 * </p>
 *
 * @see Node
 * @see Archive
 *
 * @author <a href="mailto:juanco@suigeneris.org">Juanco Anez</a>
 * @version $Id: BranchNode.java,v 1.4 2003/10/13 07:59:46 rdonkin Exp $
 */
class BranchNode
        extends Node
{
    /**
     * Create a BranchNode with the given version number.
     * The next field in a Branch node points to the next higher
     * revision on the same branch.
     * @param vernum the version number for the node
     * @param next   the next node in the logical RCS hierarchy.
     */
    BranchNode(Version vernum, BranchNode next)
    {
        super(vernum, next);
        if (vernum == null)
        {
            throw new IllegalArgumentException(vernum.toString());
        }
    }


    /**
     * Return the last (leaf) node in the branch this node belongs to.
     * @return The leaf node.
     */
    public BranchNode getLeafNode()
    {
        BranchNode result = this;
        while (result.getRCSNext() != null)
        {
            result = (BranchNode) result.getRCSNext();
        }
        return result;
    }


    /**
     * Set the next node in the RCS logical hierarcy.
     * Update the _parent and _child node accordingly.
     * For BranchNodes, the RCS-next is a child, that is,
     * a node with a larger version number.
     */
    public void setRCSNext(Node node)
    {
        super.setRCSNext(node);
        if (this.getChild() != null)
        {
            this.getChild().parent = null;
        }
        this.child = node;
        if (this.getChild() != null)
        {
            this.getChild().parent = this;
        }
    }

    public Node deltaRevision()
    {
        return this;
    }

    public Node nextInPathTo(Version vernum, boolean soft)
            throws NodeNotFoundException
    {
        Version branchPoint = vernum.getBase(this.version.size());
        Version thisBase = this.version.getBase(branchPoint.size());
        if (thisBase.isGreaterThan(branchPoint) && !soft)
        {
            throw new NodeNotFoundException(vernum);
        } //!!! InternalError, really

        if (this.version.equals(vernum))
        {
            return null;
        }
        else if (this.version.isLessThan(branchPoint))
        {
            return getChild();
        }
        else if (vernum.size() <= this.version.size())
        {
            if (vernum.size() < this.version.size() || branchPoint.last() == 0)
            {
                return getChild();
            } // keep going
            else
            {
                return null;
            }
        }
        else
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
    }
}

