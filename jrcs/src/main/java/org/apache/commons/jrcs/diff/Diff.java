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

package org.apache.commons.jrcs.diff;

import java.util.*;

import org.apache.commons.jrcs.diff.myers.MyersDiff;
import org.apache.commons.jrcs.util.ToString;

/**
 * Implements a differencing engine that works on arrays of {@link Object Object}.
 *
 * <p>Within this library, the word <i>text</i> means a unit of information
 * subject to version control.
 *
 * <p>Text is represented as <code>Object[]</code> because
 * the diff engine is capable of handling more than plain ascci. In fact,
 * arrays of any type that implements
 * {@link java.lang.Object#hashCode hashCode()} and
 * {@link java.lang.Object#equals equals()}
 * correctly can be subject to differencing using this
 * library.</p>
 *
 * <p>This library provides a framework in which different differencing
 * algorithms may be used.  If no algorithm is specififed, a default
 * algorithm is used.</p>
 *
 * @version $Revision: 1.16 $ $Date: 2003/10/13 08:00:24 $
 * @author <a href="mailto:juanco@suigeneris.org">Juanco Anez</a>
 * @see     Delta
 * @see     DiffAlgorithm
 *
 * modifications:
 *
 * 27 Apr 2003 bwm
 *
 * Added some comments whilst trying to figure out the algorithm
 *
 * 03 May 2003 bwm
 *
 * Factored out the algorithm implementation into a separate difference
 * algorithm class to allow pluggable algorithms.
 */

public class Diff
    extends ToString
{
    /** The standard line separator. */
    public static final String NL = System.getProperty("line.separator");

    /** The line separator to use in RCS format output. */
    public static final String RCS_EOL = "\n";

    /** The original sequence. */
    protected final Object[] orig;

    /** The differencing algorithm to use. */
    protected DiffAlgorithm algorithm;

    /**
     * Create a differencing object using the default algorithm
     *
     * @param the original text that will be compared
     */
    public Diff(Object[] original)
    {
        this(original, null);
    }

    /**
     * Create a differencing object using the given algorithm
     *
     * @param o the original text which will be compared against
     * @param algorithm the difference algorithm to use.
     */
    public Diff(Object[] original, DiffAlgorithm algorithm)
    {
        if (original == null)
        {
            throw new IllegalArgumentException();
        }

        this.orig = original;
        if (algorithm != null)
            this.algorithm = algorithm;
        else
            this.algorithm = defaultAlgorithm();
    }

    protected DiffAlgorithm defaultAlgorithm()
    {
        return new MyersDiff();
    }

    /**
     * compute the difference between an original and a revision.
     *
     * @param orig the original
     * @param rev the revision to compare with the original.
     * @return a Revision describing the differences
     */
    public static Revision diff(Object[] orig, Object[] rev)
        throws DifferentiationFailedException
    {
        if (orig == null || rev == null)
        {
            throw new IllegalArgumentException();
        }

        return diff(orig, rev, null);
    }

    /**
     * compute the difference between an original and a revision.
     *
     * @param orig the original
     * @param rev the revision to compare with the original.
     * @param algorithm the difference algorithm to use
     * @return a Revision describing the differences
     */
    public static Revision diff(Object[] orig, Object[] rev,
                                DiffAlgorithm algorithm)
        throws DifferentiationFailedException
    {
        if (orig == null || rev == null)
        {
            throw new IllegalArgumentException();
        }

        return new Diff(orig, algorithm).diff(rev);
    }

    /**
     * compute the difference between the original and a revision.
     *
     * @param rev the revision to compare with the original.
     * @return a Revision describing the differences
     */
    public Revision diff(Object[] rev)
        throws DifferentiationFailedException
    {
        if (orig.length == 0 && rev.length == 0)
            return new Revision();
        else
            return algorithm.diff(orig, rev);
    }

    /**
     * Compares the two input sequences.
     * @param orig The original sequence.
     * @param rev The revised sequence.
     * @return true if the sequences are identical. False otherwise.
     */
    public static boolean compare(Object[] orig, Object[] rev)
    {
        if (orig.length != rev.length)
        {
            return false;
        }
        else
        {
            for (int i = 0; i < orig.length; i++)
            {
                if (!orig[i].equals(rev[i]))
                {
                    return false;
                }
            }
            return true;
        }
    }

    /**
     * Converts an array of {@link Object Object} to a string
     * using {@link Diff#NL Diff.NL}
     * as the line separator.
     * @param o the array of objects.
     */
    public static String arrayToString(Object[] o)
    {
        return arrayToString(o, Diff.NL);
    }

    /**
     * Edits all of the items in the input sequence.
     * @param text The input sequence.
     * @return A sequence of the same length with all the lines
     * differing from the corresponding ones in the input.
     */
    public static Object[] editAll(Object[] text)
    {
        Object[] result = new String[text.length];

        for(int i = 0; i < text.length; i++)
            result[i] = text[i] + " <edited>";

        return result;
    }

    /**
     * Performs random edits on the input sequence. Useful for testing.
     * @param text The input sequence.
     * @return The sequence with random edits performed.
     */
    public static Object[] randomEdit(Object[] text)
    {
        return randomEdit(text, text.length);
    }

    /**
     * Performs random edits on the input sequence. Useful for testing.
     * @param text The input sequence.
     * @param seed A seed value for the randomizer.
     * @return The sequence with random edits performed.
     */
    public static Object[] randomEdit(Object[] text, long seed)
    {
        List result = new ArrayList(Arrays.asList(text));
        Random r = new Random(seed);
        int nops = r.nextInt(10);
        for (int i = 0; i < nops; i++)
        {
            boolean del = r.nextBoolean();
            int pos = r.nextInt(result.size() + 1);
            int len = Math.min(result.size() - pos, 1 + r.nextInt(4));
            if (del && result.size() > 0)
            { // delete
                result.subList(pos, pos + len).clear();
            }
            else
            {
                for (int k = 0; k < len; k++, pos++)
                {
                    result.add(pos,
                               "[" + i + "] random edit[" + i + "][" + i + "]");
                }
            }
        }
        return result.toArray();
    }

    /**
     * Shuffles around the items in the input sequence.
     * @param text The input sequence.
     * @return The shuffled sequence.
     */
    public static Object[] shuffle(Object[] text)
    {
        return shuffle(text, text.length);
    }

    /**
    * Shuffles around the items in the input sequence.
    * @param text The input sequence.
    * @param seed A seed value for randomizing the suffle.
    * @return The shuffled sequence.
    */
   public static Object[] shuffle(Object[] text, long seed)
    {
        List result = new ArrayList(Arrays.asList(text));
        Collections.shuffle(result);
        return result.toArray();
    }

    /**
     * Generate a random sequence of the given size.
     * @param The size of the sequence to generate.
     * @return The generated sequence.
     */
    public static Object[] randomSequence(int size)
    {
        return randomSequence(size, size);
    }

    /**
     * Generate a random sequence of the given size.
     * @param The size of the sequence to generate.
     * @param seed A seed value for randomizing the generation.
     * @return The generated sequence.
     */
    public static Object[] randomSequence(int size, long seed)
    {
        Integer[] result = new Integer[size];
        Random r = new Random(seed);
        for(int i = 0; i < result.length; i++)
        {
            result[i] = new Integer(r.nextInt(size));
        }
        return result;
    }

}
