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

import java.text.DateFormat;
import java.text.Format;
import java.text.MessageFormat;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.StringTokenizer;
import java.util.TreeMap;
import java.util.Scanner;

import org.apache.commons.jrcs.diff.AddDelta;
import org.apache.commons.jrcs.diff.Chunk;
import org.apache.commons.jrcs.diff.DeleteDelta;
import org.apache.commons.jrcs.diff.Diff;
import org.apache.commons.jrcs.diff.Revision;
import org.apache.commons.jrcs.util.ToString;
import org.apache.commons.jrcs.diff.PatchFailedException;

/**
 * Ancestor to all nodes in a version control Archive.
 * <p>Nodes store the deltas between two revisions of the text.</p>
 *
 * This class is NOT thread safe.
 *
 * @see TrunkNode
 * @see BranchNode
 * @see Archive
 *
 * @author <a href="mailto:juanco@suigeneris.org">Juanco Anez</a>
 * @version $Id: Node.java,v 1.5 2003/10/13 07:59:46 rdonkin Exp $
 */
public abstract class Node
        extends ToString
        implements Comparable
{

    /**
     * The version number for this node.
     */
    protected final Version version;
    protected Date date = new Date();
    protected String author = System.getProperty("user.name");
    protected String state = "Exp";
    protected String log = "";
    protected String locker = "";
    protected Object[] text;
    protected Node rcsnext;
    protected Node parent;
    protected Node child;
    protected TreeMap branches = null;
    protected Phrases phrases = null;
    protected boolean endWithNewLine = true;

    protected static final Format dateFormatter = new MessageFormat(
            "\t{0,number,##00}." +
            "{1,number,00}." +
            "{2,number,00}." +
            "{3,number,00}." +
            "{4,number,00}." +
            "{5,number,00}"
    );
    protected static final DateFormat dateFormat = new SimpleDateFormat("yy.MM.dd.HH.mm.ss");
    protected static final DateFormat dateFormat2K = new SimpleDateFormat("yyyy.MM.dd.HH.mm.ss");


    /**
     * Creates a copy of a node. Only used internally.
     * @param other The node to copy.
     */
    protected Node(Node other)
    {
        this(other.version, null);
        this.date = other.date;
        this.author = other.author;
        this.state = other.state;
        this.log = other.log;
        this.locker = other.locker;
    }

    /**
     * Creates a node with the given version number.
     * @param vernum The version number for the node.
     * @param rcsnext The next node in the RCS logical hierarchy.
     */
    protected Node(Version vernum, Node rcsnext)
    {
        if (vernum == null)
        {
            throw new IllegalArgumentException(vernum.toString());
        }
        this.version = (Version) vernum.clone();
        this.setRCSNext(rcsnext);
    }


    /**
     * Creates a new node of the adequate type for the given version number.
     * @param vernum The version number for the node.
     * @param rcsnext The next node in the RCS logical hierarchy.
     * @return The newly created node.
     */
    static Node newNode(Version vernum, Node rcsnext)
            throws InvalidVersionNumberException
    {
        if (vernum.isTrunk())
        {
            return new TrunkNode(vernum, (TrunkNode) rcsnext);
        }
        else
        {
            return new BranchNode(vernum, (BranchNode) rcsnext);
        }
    }

    /**
     * Creates a new node of the adequate type for the given version number.
     * @param vernum The version number for the node.
     * @return The newly created node.
     */
    static Node newNode(Version vernum)
            throws InvalidVersionNumberException
    {
        return newNode(vernum, null);
    }


    /**
     * Compares the version number of this node to that of another node.
     * @param other The node to compare two.
     * @return 0 if versions are equal, 1 if this version greather than the other,
     * and -1 otherwise.
     */
    public int compareTo(Object other)
    {
        if (other == this)
        {
            return 0;
        }
        else if (!(other instanceof Node))
        {
            return -1;
        }
        else
        {
            return version.compareTo(((Node) other).version);
        }
    }


    /**
     * Returns true if the node is a "ghost" node.
     * Ghost nodes have no associated text ot deltas. CVS uses
     * them to mark certain points in the node hierarchy.
     */
    public boolean isGhost()
    {
        return version.isGhost() || text == null;
    }

    /**
     * Retrieve the branch node identified with
     * the given numer.
     * @param no The branch number.
     * @return The branch node.
     * @see BranchNode
     */
    public BranchNode getBranch(int no)
    {
        if (branches == null)
        {
            return null;
        }
        else if (no == 0)
        {
            Integer branchNo = (Integer) branches.lastKey();
            return (BranchNode) (branchNo == null ? null : branches.get(branchNo));
        }
        else
        {
            return (BranchNode) branches.get(new Integer(no));
        }
    }


    /**
     * Return the root node of the node hierarchy.
     * @return The root node.
     */
    public Node root()
    {
        Node result = this;
        while (result.parent != null)
        {
            result = result.parent;
        }
        return result;
    }

    /**
     * Set the locker.
     * @param user A symbol that identifies the locker.
     */
    public void setLocker(String user)
    {
        locker = user.intern();
    }

    /**
     * Set the author of the node's revision.
     * @param user A symbol that identifies the author.
     */
    public void setAuthor(String user)
    {
        author = user.intern();
    }


    /**
     * Set the date of the node's revision.
     * @param value an array of 6 integers, corresponding to the
     * year, month, day, hour, minute, and second of this revision.<br>
     * If the year has two digits, it is interpreted as belonging to the 20th
     * century.<br>
     * The month is a number from 1 to 12.
     */
    public void setDate(int[] value)
    {
        this.date = new GregorianCalendar(value[0] + (value[0] <= 99 ? 1900 : 0),
                value[1] - 1, value[2],
                value[3], value[4], value[5]).getTime();
    }

    /**
     * Sets the state of the node's revision.
     * @param value A symbol that identifies the state. The most commonly
     * used value is Exp.
     */
    public void setState(String value)
    {
        state = value;
    }

    /**
     * Sets the next node in the RCS logical hierarchy.
     * In the RCS hierarchy, a {@link TrunkNode TrunkNode} points
     * to the previous revision, while a {@link BranchNode BranchNode}
     * points to the next revision.
     * @param node The next node in the RCS logical hierarchy.
     */
    public void setRCSNext(Node node)
    {
        rcsnext = node;
    }

    /**
     * Sets the log message for the node's revision.
     * The log message is usually used to explain why the revision took place.
     * @param value The message.
     */
    public void setLog(String value)
    {
      // the last newline belongs to the file format
      if(value.endsWith(Archive.RCS_NEWLINE))
         log = value.substring(0, value.length()-1);
      else
         log = value;
    }

    /**
     * Sets the text for the node's revision.
     * <p>For archives containing binary information, the text is an image
     * of the revision contents.</p>
     * <p>For ASCII archives, the text contains the delta between the
     * current revision and the next revision in the RCS logical hierarchy.
     * The deltas are codified in a format similar to the one used by Unix diff.</p>
     * <p> The passed string is converted to an array of objects
     * befored being stored as the revision's text</p>
     * @param value The revision's text.
     * @see ArchiveParser
     */
    public void setText(String value)
    {
        this.text = org.apache.commons.jrcs.diff.Diff.stringToArray(value);

	if (false == value.endsWith("\n"))
		endWithNewLine = false;
    }

    /**
     * Sets the text for the node's revision.
     * <p>For archives containing binary information, the text is an image
     * of the revision contents.</p>
     * <p>For ASCII archives, the text contains the delta between the
     * current revision and the next revision in the RCS logical hierarchy.
     * The deltas are codified in a format similar to the one used by Unix diff.
     * @param value The revision's text.
     * @see ArchiveParser
     */
    public void setText(Object[] value)
    {
        this.text = Arrays.asList(value).toArray();
    }

    /**
     * Adds a branch node to the current node.
     * @param node The branch node.
     * @throws InvalidVersionNumberException if the version number
     * is not a valid branch version number for the current node
     */
    public void addBranch(BranchNode node)
            throws InvalidVersionNumberException
    {
        if (node.version.isLessThan(this.version)
            || node.version.size() != (this.version.size()+2))
        {
            throw new InvalidVersionNumberException("version must be grater");
        }

        int branchno = node.version.at(this.version.size());
        if (branches == null)
        {
            branches = new TreeMap();
        }
        branches.put(new Integer(branchno), node);
        node.parent = this;
    }


    /**
     * Returns the version number that should correspond to
     * the revision folowing this node.
     * @return The next version number.
     */
    public Version nextVersion()
    {
        return this.version.next();
    }


    /**
     * Returns the version number that should correspond to a newly
     * created branch of this node.
     * @return the new branch's version number.
     */
    public Version newBranchVersion()
    {
        Version result = new Version(this.version);
        if (branches == null || branches.size() <= 0)
        {
            result.__addBranch(1);
        }
        else
        {
            result.__addBranch(((Integer) branches.lastKey()).intValue());
        }
        result.__addBranch(1);
        return result;
    }


    /**
     * Return the next node in the RCS logical hierarchy.
     * @return the next node
     */
    public Node getRCSNext()
    {
        return rcsnext;
    }


    /**
     * Returns the path from the current node to the node
     * identified by the given version.
     * @param vernum The version number of the last node in the path.
     * @return The path
     * @throws NodeNotFoundException if a node with the given version number
     * doesn't exist, or is not reachable following the RCS-next chain
     * from this node.
     * @see Path
     */
    public Path pathTo(Version vernum)
            throws NodeNotFoundException
    {
        return pathTo(vernum, false);
    }

    /**
     * Returns the path from the current node to the node
     * identified by the given version.
     * @param vernum The version number of the last node in the path.
     * @param soft If true, no error is thrown if a node with the given
     * version doesn't exist. Use soft=true to find a apth to where a new
     * node should be added.
     * @return The path
     * @throws NodeNotFoundException if a node with the given version number
     * is not reachable following the RCS-next chain from this node.
     * If soft=false the exception is also thrown if a node with the given
     * version number doesn't exist.
     * @see Path
     */
    public Path pathTo(Version vernum, boolean soft)
            throws NodeNotFoundException
    {
        Path path = new Path();

        Node target = this;
        do
        {
            path.add(target);
            target = target.nextInPathTo(vernum, soft);
        }
        while (target != null);
        return path;
    }


    /**
     * Returns the next node in the path from the current node to the node
     * identified by the given version.
     * @param vernum The version number of the last node in the path.
     * @param soft If true, no error is thrown if a node with the given
     * version doesn't exist. Use soft=true to find a apth to where a new
     * node should be added.
     * @return The path
     * @throws NodeNotFoundException if a node with the given version number
     * is not reachable following the RCS-next chain from this node.
     * If soft=false the exception is also thrown if a node with the given
     * version number doesn't exist.
     * @see Path
     */
    public abstract Node nextInPathTo(Version vernum, boolean soft)
            throws NodeNotFoundException;


    /**
     * Returns the Node with the version number that corresponds to
     * the revision to be obtained after the deltas in the current node
     * are applied.
     * <p>For a {@link BranchNode BranchNode} the deltaRevision is the
     * current revision; that is, after the deltas are applied, the text for
     * the current revision is obtained.</p>
     * <p>For a {@link TrunkNode TrunkNode} the deltaRevision is the
     * next revision; that is, after the deltas are applied, the text obtained
     * corresponds to the next revision in the chain.</p>
     * @return The node for the delta revision.
     */
    public abstract Node deltaRevision();


    /**
     * Apply the deltas in the current node to the given text.
     * @param original the text to be patched
     * @throws InvalidFileFormatException if the deltas cannot be parsed.
     * @throws PatchFailedException if the diff engine determines that
     * the deltas cannot apply to the given text.
     */
    public void patch(List original)
            throws InvalidFileFormatException,
            PatchFailedException
    {
        patch(original, false);
    }

    /**
     * Apply the deltas in the current node to the given text.
     * @param original the text to be patched
     * @param annotate set to true to have each text line be a
     * {@link Line Line} object that identifies the revision in which
     * the line was changed or added.
     * @throws InvalidFileFormatException if the deltas cannot be parsed.
     * @throws PatchFailedException if the diff engine determines that
     * the deltas cannot apply to the given text.
     */
    public void patch(List original, boolean annotate)
            throws InvalidFileFormatException,
            org.apache.commons.jrcs.diff.PatchFailedException
    {
        Revision revision = new Revision();
        for (int it = 0; it < text.length; it++)
        {
            String cmd = text[it].toString();

            java.util.StringTokenizer t = new StringTokenizer(cmd, "ad ", true);
            char action;
            int n;
            int count;

            try
            {
                action = t.nextToken().charAt(0);
                n = Integer.parseInt(t.nextToken());
                t.nextToken();    // skip the space
                count = Integer.parseInt(t.nextToken());
            }
            catch (Exception e)
            {
                throw new InvalidFileFormatException(version + ":line:" + ":" + e.getMessage());
            }

            if (action == 'd')
            {
                revision.addDelta(new DeleteDelta(new Chunk(n - 1, count)));
            }
            else if (action == 'a')
            {
                revision.addDelta(new AddDelta(n, new Chunk(getTextLines(it + 1, it + 1 + count), 0, count, n - 1)));
                it += count;
            }
            else
            {
                throw new InvalidFileFormatException(version.toString());
            }
        }
        revision.applyTo(original);
    }


    void newpatch(List original, boolean annotate, Node root) throws InvalidFileFormatException
    {
        DeltaText dt = new DeltaText(root, this.child, annotate);

        for (int i = 0; i < text.length; i++)
        {
            try
            {
                char action = ((String)text[i]).charAt(0);

                Scanner s = new Scanner(((String)text[i]).substring(1));

                int atLine  = s.nextInt();
                int noLines = s.nextInt();

                switch (action)
                {
                    case 'a' :
                        dt.addDeltaText(new DeltaAddTextLine(atLine, noLines, text, i+1));
                        i += noLines;
                        break;

                    case 'd' :
                        dt.addDeltaText(new DeltaDelTextLine(atLine, noLines));
                        break;

                    default :
                        throw new InvalidFileFormatException("Expected 'a' or 'd', got: '" + action
                                + "' while parsing deltatext for revision: " + version);
                }
            } // This is not very neat. But it will work.
            catch (Exception e)
            {
                throw new InvalidFileFormatException("While parsing delta text for revision: " + version
                        + ", got: " + e.getMessage());
            }
        }

        dt.patch(original);
    }


    /**
     * Conver the current node and all of its branches
     * to their RCS string representation and
     * add it to the given StringBuffer.
     * @param s The string buffer to add the node's image to.
     */
    public void toString(StringBuffer s)
    {
        toString(s, Archive.RCS_NEWLINE);
    }

    /**
     * Conver the current node and all of its branches
     * to their RCS string representation and
     * add it to the given StringBuffer using the given marker as
     * line separator.
     * @param s The string buffer to add the node's image to.
     * @param EOL The line separator to use.
     */
    public void toString(StringBuffer s, String EOL)
    {
        String EOI = ";" + EOL;
        String NLT = EOL + "\t";

        s.append(EOL);
        s.append(version.toString() + EOL);

        s.append("date");
        if (date != null)
        {
            DateFormat formatter = dateFormat;
            Calendar cal = new GregorianCalendar();
            cal.setTime(date);
            if (cal.get(Calendar.YEAR) > 1999)
            {
                formatter = dateFormat2K;
            }
            s.append("\t" + formatter.format(date));
        }
        s.append(";\tauthor");
        if (author != null)
        {
            s.append(" " + author);
        }
        s.append(";\tstate");
        if (state != null)
        {
            s.append(" ");
            s.append(state);
        }
        s.append(EOI);

        s.append("branches");
        if (branches != null)
        {
            for (Iterator i = branches.values().iterator(); i.hasNext();)
            {
                Node n = (Node) i.next();
                if (n != null)
                {
                    s.append(NLT + n.version);
                }
            }
        }
        s.append(EOI);

        s.append("next\t");
        if (rcsnext != null)
        {
            s.append(rcsnext.version.toString());
        }
        s.append(EOI);
    }


    /**
     * Conver the urrent node to its RCS string representation.
     * @return The string representation
     */
    public String toText()
    {
        final StringBuffer s = new StringBuffer();
        toText(s, Archive.RCS_NEWLINE);
        return s.toString();
    }

    /**
     * Conver the urrent node to its RCS string representation and
     * add it to the given StringBuffer using the given marker as
     * line separator.
     * @param s The string buffer to add the node's image to.
     * @param EOL The line separator to use.
     */
    public void toText(StringBuffer s, String EOL)
    {
        s.append(EOL + EOL);
        s.append(version.toString() + EOL);

        s.append("log" + EOL);
        if (log.length() == 0)
          s.append(Archive.quoteString(""));
        else // add a newline after the comment
          s.append(Archive.quoteString(log + EOL));
        s.append(EOL);

        if (phrases != null)
        {
            s.append(phrases.toString());
        }

        s.append("text" + EOL);

	if (true == endWithNewLine)
		s.append(Archive.quoteString(arrayToString(text, EOL) + EOL));
	else
		s.append(Archive.quoteString(arrayToString(text, EOL)));

        s.append(EOL);

        if (branches != null)
        {
            for (Iterator i = branches.values().iterator(); i.hasNext();)
            {
                Node n = (Node) i.next();
                if (n != null)
                {
                    n.toText(s, EOL);
                }
            }
        }
    }

    /**
     * Return a list with the lines of the node's text.
     * @return The list
     */
    public List getTextLines()
    {
        return getTextLines(new LinkedList());
    }

    /**
     * Return a list with a subset of the lines of the node's text.
     * @param from The offset of the first line to retrieve.
     * @param to The offset of the line after the last one to retrieve.
     * @return The list
     */
    public List getTextLines(int from, int to)
    {
        return getTextLines(new LinkedList(), from, to);
    }

    /**
     * Add a subset of the lines of the node's text to the given list.
     * @return The given list after the additions have been made.
     */
   public List getTextLines(List lines)
    {
        return getTextLines(lines, 0, text.length);
    }

    /**
     * Add a subset of the lines of the node's text to the given list.
     * @param from The offset of the first line to retrieve.
     * @param to The offset of the line after the last one to retrieve.
     * @return The given list after the additions have been made.
     */
    public List getTextLines(List lines, int from, int to)
    {
        for (int i = from; i < to; i++)
        {
            lines.add(new Line(deltaRevision(), text[i]));
        }
        return lines;
    }

    public final Date getDate()
    {
        return date;
    }

    public final String getAuthor()
    {
        return author;
    }

    public final String getState()
    {
        return state;
    }

    public final String getLog()
    {
        return log;
    }

    public final String getLocker()
    {
        return locker;
    }

    public final Object[] getText()
    {
        return text;
    }

    public final Node getChild()
    {
        return child;
    }

    public final TreeMap getBranches()
    {
        return branches;
    }

    public final Node getParent()
    {
        return parent;
    }

    public final Version getVersion()
    {
        return version;
    }

    public Phrases getPhrases()
    {
        return phrases;
    }

}

