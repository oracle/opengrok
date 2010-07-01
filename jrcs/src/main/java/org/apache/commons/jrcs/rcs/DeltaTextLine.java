package org.apache.commons.jrcs.rcs;

import java.util.List;

interface DeltaTextLine
{
    public void patch(Node root, Node prev, List<Line> lines);

    public void patchAnnotate(Node root, Node prev, List<Line> lines);
}
