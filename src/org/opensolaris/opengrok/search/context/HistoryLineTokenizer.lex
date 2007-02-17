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
 * ident      "@(#)HistoryLineTokenizer.lex 1.3     06/02/22 SMI"
 */

package org.opensolaris.opengrok.search.context;

import org.opensolaris.opengrok.web.*;
import org.opensolaris.opengrok.search.Hit;
import java.io.*;
import java.util.*;
import org.apache.lucene.analysis.*;
%%

%public
%class HistoryLineTokenizer
%unicode
%function next
%type String 
%ignorecase

%{
  int markedPos=0;
  int matchStart=-1;
  int rest=0;
  int fld=0;
  boolean wait = false;
  boolean dumpRest = false;
  Writer out;
  List<Hit> hits;
  String filename;
  StringBuilder sb;
  boolean alt;
  public static final HashSet<String> stopset = new HashSet<String>();
  static {
  stopset.add(  "a");
stopset.add( "an");
stopset.add( "and");
stopset.add( "are");
stopset.add( "as");
stopset.add( "at");
stopset.add( "be");
stopset.add( "but");
stopset.add( "by");
stopset.add( "for");
stopset.add( "if");
stopset.add( "in");
stopset.add( "into");
stopset.add( "is");
stopset.add( "it");
stopset.add( "no");
stopset.add( "not");
stopset.add( "of");
stopset.add( "on");
stopset.add( "or");
stopset.add( "s");
stopset.add( "such");
stopset.add( "t");
stopset.add( "that");
stopset.add( "the");
stopset.add( "their");
stopset.add( "then");
stopset.add( "there");
stopset.add( "these");
stopset.add( "they");
stopset.add( "this");
stopset.add( "to");
stopset.add( "was");
stopset.add( "will");
stopset.add( "with");
stopset.add( "/");
stopset.add( "\\");
stopset.add(":");
stopset.add(".");
stopset.add("0.0");
stopset.add( "1.0");
  }

  public void setWriter(Writer out) {
  	this.out = out;
  }
  
  public void setHitList(List<Hit> hits) {
        this.hits = hits;
  }

  /**
   * Set the name of the file we are working on (needed if we would like to
   * generate a list of hits instead of generating html)
   * @param filename the name of the file
   */
  public void setFilename(String filename) {
        this.filename = filename;
  }

    public void setAlt(boolean alt) {
        this.alt = alt;
    }

  public void reInit(char[] buf) {
  	yyreset((Reader) null);
  	zzBuffer = buf;
  	zzEndRead = buf.length;
	zzAtEOF = true;
	zzStartRead = 0;
	wait = false;
	dumpRest = false;
	rest = 0;
	markedPos=0;
	matchStart=-1;
	fld=0;
  }

  public void holdOn() {
     if(!wait) {
  	wait = true;
	matchStart = zzStartRead;
     }
  }
  
  public void neverMind() {
  	wait = false;
	matchStart = -1;
  }

  private void printHTML(char[] buf, int start, int end) throws IOException {
  	for(int i=start;i<end; i++) {
		switch(buf[i]) {
		case '\n':
			out.write("<br/>");
			break;
		case '<':
			out.write("&lt;");
			break;
		case '>':
			out.write("&gt;");
			break;
		case '&':
			out.write("&amp;");
			break;
		default:
			out.write(buf[i]);
		}
	}
  }

  private void formatHTML(char buf[], int start, int end) {
  	for(int i=start;i<end; i++) {
		switch(buf[i]) {
		case '\n':
			sb.append(" ");
			break;
		case '<':
			sb.append("&lt;");
			break;
		case '>':
			sb.append("&gt;");
			break;
		case '&':
			sb.append("&amp;");
			break;
		default:
			sb.append(buf[i]);
		}
	}
  } 

  public void printContext() throws IOException {
        if (sb == null) {
           sb = new StringBuilder();
        } 

  	wait = false;
	if (matchStart == -1) {
		matchStart = zzStartRead;
	}
      
        if (out != null) {
	   //System.err.println("markedPos = " + markedPos + " matchStart= " + matchStart + " zzMarkedPos=" + zzMarkedPos);
	   printHTML(zzBuffer, markedPos, matchStart);
	   out.write("<b>");
	   printHTML(zzBuffer, matchStart, zzMarkedPos);
	   out.write("</b>");
        } else {
           formatHTML(zzBuffer, markedPos, matchStart);
	   sb.append("<b>");
	   formatHTML(zzBuffer, matchStart, zzMarkedPos);
	   sb.append("</b>");
        }
	markedPos = zzMarkedPos;
	matchStart = -1;
	dumpRest = true;
	rest = zzMarkedPos;
  }

  public void dumpRest() throws IOException {
  	//System.err.println("dumpRest = " + dumpRest + " zzEndRead=" + zzEndRead + " zzMarkedPos=" + zzMarkedPos+ " rest = "+rest);
	if(dumpRest) {
           if (out != null) {
		printHTML(zzBuffer, rest, zzEndRead);
           } else {
                formatHTML(zzBuffer, rest, zzEndRead);
                hits.add(new Hit(filename, sb.toString(), "", false, alt));
                sb.setLength(0);
           }
	}
  }
%}

Identifier = [a-zA-Z_] [a-zA-Z0-9_]*
Number = [0-9]+|[0-9]+\.[0-9]+| "0[xX]" [0-9a-fA-F]+
Printable = [\@\$\%\^\&\-+=\?\.\:]

%%


{Identifier}|{Number}|{Printable}	{String m = yytext().toLowerCase();
					if(stopset.contains(m)) { } else { return(m);}}
<<EOF>>   { return null;}
.|\n	{}
