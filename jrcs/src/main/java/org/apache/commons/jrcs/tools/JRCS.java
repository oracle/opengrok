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

import org.apache.commons.jrcs.diff.Diff;
import org.apache.commons.jrcs.rcs.*;

/**
 * A tool to extract information from CVS/RCS archives.
 * <p>This simple tool was built as a feature test of the
 * {@linkplain org.apache.commons.jrcs.rcs rcs} library.
 * </p>
 */
public class JRCS
{
    static Archive archive;
    static Object[] orig;

    static public void main(String[] args) throws Exception
    {
//     try {
        if (args.length > 2)
        {
            System.err.println("usage: ");
        }
        else
        {
            if (args.length >= 1)
            {
                archive = new Archive(args[0]);
            }
            else
            {
                archive = new Archive("test,v", System.in);
            }
            System.err.println();
            System.err.println("==========================================");
            //System.out.println(archive.toString());
            //System.out.println(archive.getRevision("5.2.7.1"));
            //System.out.println(archive.getRevision(args[1], true));
            //archive.addRevision(new Object[]{}, args[1]);
            orig = archive.getRevision("1.3");
            System.out.println("*-orig-*********");
            System.out.print(Diff.arrayToString(orig));
            System.out.println("**********");
            //!! commented out because of package access error (jvz).
            //Object[] other = archive.getRevision("1.2");
            //System.out.println(Diff.diff(archive.removeKeywords(orig),
            //                   archive.removeKeywords(other)).toRCSString());
            trywith("1.2.3.1");
            trywith("1.2.3.5");
            trywith("1.2.3.1.");
            trywith("1.2");
            trywith("1.2.2");
            /*
            trywith("1.2.2.2");
            trywith("1.2.2.1.1");
            trywith("1.2.2");
            trywith("1.2.3");
            trywith("1.2.3");
            */
        }
/*     }
     catch(java.io.IOException e) {
       System.out.println(e.getClass().toString());
       System.out.println(e.getMessage());
       e.printStackTrace();
     }
*/
    }

    static int n = 1;

    static void trywith(String ver)
    {
        try
        {
            System.out.println();
            System.out.println("-------------");
            System.out.println("Adding " + ver);

            /*
            List editor = new ArrayList(Arrays.asList(orig));
            editor.subList(0,1).clear();
            editor.add(0, "hola!");
            Object[] rev = editor.toArray();
            */
            Object[] rev = Diff.randomEdit(orig, n++);
            //rev = Diff.stringToArray(archive.doKeywords(Diff.arrayToString(rev), null));
            //System.out.print(Archive.arrayToString(rev));

            Version newVer = archive.addRevision(rev, ver);
            System.out.println(newVer + " added");

            if (newVer != null)
            {
                //Object[] rec = archive.getRevision(newVer);
                //System.out.print(Archive.arrayToString(rec));

                /* !! commented out because of package access errors (jvz).
                if (!Diff.compare(archive.removeKeywords(rec),
                    archive.removeKeywords(rev)))
                {
                    System.out.println("revisions differ!");
                    System.out.println("**********");
                    System.out.println(Diff.arrayToString(rec));
                    System.out.println("**********");
                    System.out.println(Diff.arrayToString(rev));
                    System.out.println("**********");
                    System.out.print(Diff.diff(rec, rev).toRCSString());
                    System.out.println("**********");
                }
                */
            }
            System.out.println(ver + " OK");
        }
        catch (Exception e)
        {
            System.err.println(e.getMessage());
            e.printStackTrace();
        }
    }
}
