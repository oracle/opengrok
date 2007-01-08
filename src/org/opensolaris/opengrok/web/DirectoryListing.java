/*
 * CDDL HEADER START
 *
 * The contents of this file are subject to the terms of the
 * Common Development and Distribution License (the "License").  
 * You may not use this file except in compliance with the License.
 *
 * See LICENSE.txt included in this distribution for the specific
 * language governing permissions and limitations under the License.
 *
 * When distributing Covered Code, include this CDDL HEADER in each
 * file and include the License file at LICENSE.txt.
 * If applicable, add the following below this CDDL HEADER, with the
 * fields enclosed by brackets "[]" replaced with your own identifying
 * information: Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 */

/*
 * Copyright 2005 Sun Microsystems, Inc.  All rights reserved.
 * Use is subject to license terms.
 */

/*
 * ident	"@(#)DirectoryListing.java 1.2     05/12/01 SMI"
 */

package org.opensolaris.opengrok.web;

import java.io.*;
import java.util.*;
import java.text.*;
import java.util.HashSet;
import org.opensolaris.opengrok.index.IgnoredNames;

/**
 * Generates HTML listing of a Directory
 */
public class DirectoryListing {
    private EftarFileReader desc;
    private long now;
    
    public DirectoryListing() {
	desc = null;
	now =  (new Date()).getTime();
    }
    
    public DirectoryListing(EftarFileReader desc) {
	this.desc = desc;
	now =  (new Date()).getTime();
    }
    
    public void listTo(File dir, Writer out) throws IOException {
	String[] files = dir.list();
	if (files != null) {
	    Arrays.sort(files, String.CASE_INSENSITIVE_ORDER);
	    listTo(dir, out, dir.getPath(), files);
	}
    }
    
    /**
     * Write a listing of "dir" to "out"
     *
     * @param dir to be Listed
     * @param out writer to write
     * @param path Virtual Path of the directory
     * @param files childred of dir
     * @throws java.io.IOException
     * Returns a list of READMEs
     *
     */
    public ArrayList listTo(File dir, Writer out, String path, String[] files) throws IOException {
	Arrays.sort(files, String.CASE_INSENSITIVE_ORDER);
	boolean alt = true;
	NumberFormat formatter = new DecimalFormat("#,###,###,###");
	Format dateFormatter = new SimpleDateFormat("dd-MMM-yyyy");
	out.write("<table cellspacing=\"0\" border=\"0\" id=\"dirlist\">");
	EftarFileReader.FNode parentFNode = null;
	if (!path.equals("")) {
	    out.write("<tr><td colspan=\"4\"><a href=\"..\"><i>Up to higher level directory</i></a></td></tr>");
	}
	if(desc != null)
	    parentFNode =  desc.getNode(path);
	out.write("<tr class=\"thead\"><th><tt>Name</tt></th><th><tt>Date</tt></th><th><tt>Size</tt></th>");
/*        if (fstats != null) {
	    out.write("<th><tt>Lines</tt></th><th><tt>Files</tt></th>");
	}
 */
	if(parentFNode!= null && parentFNode.childOffset > 0) {
	    out.write("<th><tt>Description</tt></th>");
	}
	out.write("</tr>");
	ArrayList<String> readMes = new ArrayList<String>();
	for (int i = 0; i < files.length; i++) {
	    if(!IgnoredNames.ignore(files[i])) {
		File child = new File(dir, files[i]);
		String count = "";
		String size = null;
		String lines = "-";
		if (files[i].startsWith("README") || files[i].endsWith("README") || files[i].startsWith("readme")){
		    readMes.add(files[i]);
		}
		alt ^= true;
		out.write("<tr align=\"right\"");
		if(alt)
		    out.write(" class=\"alt\"");
		
		boolean isDir = child.isDirectory();
		out.write("><td align=\"left\"><tt> <a href=\"" + files[i] + (isDir ? "/\" class=\"r\"" : "\" class=\"p\"") + ">");
		if(isDir) {
		    out.write("<b>" + files[i] + "</b></a>/");
		} else {
		    out.write(        files[i] +     "</a>");
		}
		Date lastm = new Date(child.lastModified());
		out.write("</tt></td><td>" + ((now - lastm.getTime()) < 86400000 ? "Today" : dateFormatter.format(lastm))
		+ "</td>");
/*                if (fstats != null) {
		    SizeandLines snl = fstats.get(path + "/" +files[i]);
		    //System.out.println("Fstats for = " + path + "/" +files[i] + " = " + snl);
		    if(snl != null) {
			out.write("<td><tt>" + Util.redableSize(snl.size) + "</tt></td>");
			out.write("<td><tt>" + formatter.format(snl.lines) + "</tt></td>");
			out.write("<td><tt>" + (isDir ? formatter.format(snl.count) : "") + "</tt></td>");
		    } else {
			out.write("<td>-</td><td>-</td><td>-</td>");
 
		    }
		} else
 */
		out.write("<td><tt>" + (isDir ? "" : Util.redableSize(child.length())) + "</tt></td>");
		
		if(parentFNode != null  && parentFNode.childOffset > 0) {
		    String briefDesc = desc.getChildTag(parentFNode, files[i]);
		    if (briefDesc != null) {
			out.write("<td align=\"left\">");
			out.write(briefDesc);
			out.write("</td>");
		    } else {
			out.write("<td></td>");
		    }
		}
		out.write("</tr>");
	    }
	}
	out.write("</table>");
	return (readMes);
    }
    
    public static void main(String args[]) { try {
	DirectoryListing dl = new DirectoryListing();
	File tolist = new File(args[0]);
	File outFile = new File(args[1]);
	BufferedWriter out = new BufferedWriter(new FileWriter(outFile));
	//Page.mast(args[0], out, true);
	dl.listTo(tolist, out);
	//Page.footer(out);
	out.close();
    } catch ( Exception e) {
	System.out.println(" ERROR " + e + "\n Usage DirListing <dir> <output.html>");
    }
    }
}
