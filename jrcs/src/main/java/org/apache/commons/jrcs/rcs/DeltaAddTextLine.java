package org.apache.commons.jrcs.rcs;

import java.util.List;

class DeltaAddTextLine implements DeltaTextLine
{
	private int          atLine;
	private int          noLines;
	private Object[]     rcsText;
	private int          rcsTextOffset;

	DeltaAddTextLine(int atLine, int noLines, Object[] rcsText, int rcsTextOffset)
	{
		this.atLine  = atLine;
		this.noLines = noLines;
		this.rcsText = rcsText;
		this.rcsTextOffset = rcsTextOffset;
	}

    /* This builds up a revision */
	public void patch(Node root, Node prev, List lines)
	{
        for ( int i = 0; i < noLines ; i++ )
        {
            lines.add(atLine+i, new Line(root, rcsText[rcsTextOffset+i]));
        }
	}

    /* This annotates a revision, you have to do this on a *copy* of what you want annotated.. */
    public void patchAnnotate(Node root, Node prev, List lines)
    {
        for (int i = 0; i < noLines ; i++)
            { lines.add(atLine, null); }
    }

}

