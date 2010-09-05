package org.apache.commons.jrcs.rcs;

import java.util.List;

class DeltaDelTextLine implements DeltaTextLine
{
	private int          atLine;
	private int          noLines;

	DeltaDelTextLine(int atLine, int noLines)
	{
		this.atLine  = atLine-1; // Delete line offset is off by one
		this.noLines = noLines;
	}

    public void patch(Node root, Node prev, List lines)
    {
        for (int i = 0; i < noLines; i++)
            { lines.remove(atLine); }
    }

	public void patchAnnotate(Node root, Node prev, List lines)
	{
        for ( int i = 0; i < noLines ; i++ )
        {
            Line l = (Line) lines.get(atLine);

            if (null != l)
                { l.revision = prev; }

            lines.remove(atLine);
        }
	}
}

