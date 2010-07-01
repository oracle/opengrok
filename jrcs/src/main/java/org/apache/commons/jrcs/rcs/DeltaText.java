package org.apache.commons.jrcs.rcs;

import java.util.Stack;
import java.util.List;
import java.util.Iterator;

/**
 * RCS DeltaText _text_ container.
 */
class DeltaText
{
    private Stack<DeltaTextLine> deltaStack = new Stack();
    private Node     root;
    private Node     prev;
    private boolean  annotate;

    /**
     * Create a new DeltaTextText set wich may later be applied to some revision.
     * @param root Added lines will be annotated to root revision.
     * @param prev Deleted lines will be annotated to prev revision.
     */
    DeltaText(Node root, Node prev, boolean annotate)
    {
        this.root     = root;
        this.prev     = prev;
        this.annotate = annotate;
    }

    /**
     * Adds a delta to this revision.
     * @param delta the Delta to add.
     */
    void addDeltaText(DeltaTextLine delta)
    {
        deltaStack.push(delta);
    }

    void patch(List<Line> lines)
    {
        if (!annotate)
        {
            while (!deltaStack.empty())
                { deltaStack.pop().patch(root, prev, lines); }
        }
        else
        {
            while (!deltaStack.empty())
                { deltaStack.pop().patchAnnotate(root, prev, lines); }
        }
    }
}
