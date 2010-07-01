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

package org.apache.commons.jrcs.tools;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.apache.commons.jrcs.diff.Diff;
import org.apache.commons.jrcs.diff.Revision;


/**
 * A program to compare two files.
 * <p>JDiff produces the deltas between the two given files in Unix diff
 * format.
 * </p>
 * <p>The program was written as a simple test of the
 * {@linkplain org.apache.commons.jrcs.diff diff} package.
 */
public class JDiff
{

    static final String[] loadFile(String name) throws IOException
    {
        BufferedReader data = new BufferedReader(new FileReader(name));
        List lines = new ArrayList();
        String s;
        while ((s = data.readLine()) != null)
        {
            lines.add(s);
        }
        return (String[])lines.toArray(new String[lines.size()]);
    }

    static final void usage(String name)
    {
        System.err.println("Usage: " + name + " file1 file2");
    }

    public static void main(String[] argv) throws Exception
    {
        if (argv.length < 2)
        {
            usage("JDiff");
        }
        else
        {
            Object[] orig = loadFile(argv[0]);
            Object[] rev = loadFile(argv[1]);

            Diff df = new Diff(orig);
            Revision r = df.diff(rev);

            System.err.println("------");
            System.out.print(r.toString());
            System.err.println("------" + new Date());

            try
            {
                Object[] reco = r.patch(orig);
                //String recos = Diff.arrayToString(reco);
                if (!Diff.compare(rev, reco))
                {
                    System.err.println("INTERNAL ERROR:"
                                        + "files differ after patching!");
                }
            }
            catch (Throwable o)
            {
                System.out.println("Patch failed");
            }
        }
    }
}

