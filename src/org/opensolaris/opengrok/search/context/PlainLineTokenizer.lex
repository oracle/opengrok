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
 * ident     "@(#)PlainLineTokenizer.lex 1.2     06/02/22 SMI"
 */

/**
 * for plain text tokenizers
 */

package org.opensolaris.opengrok.search.context;

import org.opensolaris.opengrok.web.*;
import java.util.*;
import java.io.*;
import org.apache.lucene.analysis.*;
import org.opensolaris.opengrok.search.Hit;
%%

%public
%class PlainLineTokenizer
%line
%unicode
%function next
%type String 
%ignorecase
%switch

%{
  int markedPos=0;
  int curLinePos=0;
  int matchStart=-1;
  int markedLine=0;
  int rest=0;
  boolean wait = false;
  boolean dumpRest = false;
  Writer out;
  String url;
  TreeMap<Integer, String[]> tags;
  boolean prevHi = false;
  Integer prevLn = null;
  List<Hit> hits;
  Hit hit;
  StringBuilder sb;
  boolean alt;

  /**
   * Set the writer that should receive all output
   * @param out The new writer to write to
   */
  public void setWriter(Writer out) {
  	yyline = 1;
  	this.out = out;
  }

    /*
     * This would be faster than yytext().tolowercase()
     */
    public String loweryytext() {
        char[] lcs = new char[zzMarkedPos-zzStartRead];
        int k = 0;
        for(int i = zzStartRead; i < zzMarkedPos; i++) {
            char s = zzBuffer[i];
            if(s >= 'A' && s <= 'Z') {
                lcs[k++] =(char)(s + 32);
            } else {
                lcs[k++]= s;
            }
        }
        return new String(lcs);
    }

  /**
   * Set the name of the file we are working on (needed if we would like to
   * generate a list of hits instead of generating html)
   * @param filename the name of the file
   */
  public void setFilename(String filename) {
     this.url = filename;
     hit = new Hit(filename, null, null, false, alt);
  }
  
  /**
   * Set the list we should create Hit objects for
   * @param hits the hits we should add Hit objects
   */
  public void setHitList(List<Hit> hits) {
     this.hits = hits;
  }

    public void setAlt(boolean alt) {
        this.alt = alt;
    }


  public void reInit(char[] buf, int len, Writer out, String url, TreeMap<Integer, String[]> tags) {
  	yyreset((Reader) null);
  	zzBuffer = buf;
	zzStartRead = 0;
  	zzEndRead = len;
	zzAtEOF = true;

	wait = false;
	dumpRest = false;
	rest = 0;
	markedPos=0;
	curLinePos=0;
	matchStart=-1;
	markedLine=0;
	yyline = 1;
  	this.out = out;
	this.url = url;
	this.tags = tags;
	if(this.tags == null) {
		this.tags = new TreeMap<Integer, String[]>();
	}
	prevHi = false;
  }

  public void reInit(Reader in, Writer out, String url, TreeMap<Integer, String[]> tags) {
  	yyreset(in);
	zzStartRead = 0;

	wait = false;
	dumpRest = false;
	rest = 0;
	markedPos=0;
	curLinePos=0;
	matchStart=-1;
	markedLine=0;
	yyline = 1;
  	this.out = out;
	this.url = url;
	this.tags = tags;
	if(this.tags == null) {
		this.tags = new TreeMap<Integer, String[]>();
	}
	prevHi = false;
  }

  public void holdOn() {
     if(!wait) {
  	wait = true;
	matchStart = zzStartRead;
     }
  }
  
  public void neverMind() {
  	wait = false;
	if(!dumpRest) {
		markedPos = curLinePos;
		markedLine = yyline;
	}
	matchStart = -1;
  }

  
  private int printWithNum(char[] buf, int start, int end, int lineNo) throws IOException {
  	for(int i=start;i<end; i++) {
		switch(buf[i]) {
		case '\n':
			++lineNo;
			Integer ln = Integer.valueOf(lineNo);
			boolean hi = tags.containsKey(ln);

			out.write("</a>");
			if(prevHi){
				out.write(" <i> ");
				String[] desc = tags.remove(prevLn);
				out.write(desc[2]);
				out.write(" </i>");
			}
			out.write("<br/>");
			
			prevHi = hi;
			prevLn = ln;
			if(hi) out.write("<spans class=\"h\">"); 
			out.write("<a href=\"");
			out.write(url);
			String num = String.valueOf(lineNo);
			out.write(num);
			out.write("\"><span class=\"l\">");
			out.write(num);
			out.write("</span> ");
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
	return lineNo;
  }

  private int formatWithNum(char[] buf, int start, int end, int lineNo) {
  	for(int i=start;i<end; i++) {
		switch(buf[i]) {
		case '\n':
			++lineNo;
			Integer ln = Integer.valueOf(lineNo);
			boolean hi = tags.containsKey(ln);
			if(prevHi){
			   String[] desc = tags.remove(prevLn);
                           hit.setTag(desc[2]);
			}
			prevHi = hi;
			prevLn = ln;
                        sb.append(' ');
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
	return lineNo;
  }


  public void printContext() throws IOException {
        if (sb == null) {
            sb = new StringBuilder();
        }

        if (hit == null) {
           hit = new Hit(url, null, null, false, alt);
        }

  	wait = false;
	if (matchStart == -1) {
		matchStart = zzStartRead;
	}

	if(curLinePos == markedPos) {
			Integer ln = Integer.valueOf(markedLine);
			prevHi = tags.containsKey(ln);
			prevLn = ln;
			if (prevHi) {
				prevLn = ln;
			}

                        if (out != null) {
			   out.write("<a class=\"s\" href=\"");
			   out.write(url);
			   String num = String.valueOf(markedLine);
			   out.write(num);
			   out.write("\"><span class=\"l\">");
			   out.write(num);
		           out.write("</span> ");
                        }
	}

        if (out != null) {
           markedLine = printWithNum(zzBuffer, markedPos, matchStart, markedLine);
	   out.write("<b>");
	   markedLine = printWithNum(zzBuffer, matchStart, zzMarkedPos, markedLine);
	   out.write("</b>");
        } else {
           markedLine = formatWithNum(zzBuffer, markedPos, matchStart, markedLine);
           hit.setLineno(String.valueOf(markedLine));
	   sb.append("<b>");
	   markedLine = formatWithNum(zzBuffer, matchStart, zzMarkedPos, markedLine);
	   sb.append("</b>");
        }
	markedPos = zzMarkedPos;
	matchStart = -1;
	dumpRest = true;
	rest = zzMarkedPos;
  }
  public void dumpRest() throws IOException {
	if(dumpRest) {
		for(int i=0; rest+i<zzEndRead && i<100; i++) {
			if(zzBuffer[rest+i] == '\n') {
                           if (out != null) {
				printWithNum(zzBuffer, rest, rest+i-1, markedLine);
				//out.write(zzBuffer, rest, i);
				out.write("</a>");
				if (prevHi) {
					out.write(" <i> ");
					String[] desc = tags.remove(prevLn);
					out.write(desc[2]);
					out.write(" </i>");
				}
				out.write("<br/>");
                           } else {
                               formatWithNum(zzBuffer, rest, rest+i-1, markedLine);
                               hit.setLine(sb.toString());
			       if (prevHi) {
                                  String[] desc = tags.remove(prevLn);
                                  hit.setTag(desc[2]);
			       }
                               hits.add(hit);
                           }
                           break;
			}
		}
	}
	if (tags.size() > 0) {
        if (out != null) {
	   for(Integer rem : tags.keySet()) {
		String[] desc = tags.get(rem);
		out.write("<a class=\"s\" href=\"");
		out.write(url);
		out.write(desc[1]);
		out.write("\"><span class=\"l\">");
		out.write(desc[1]);
		out.write("</span> ");
		out.write(Util.htmlize(desc[3]).replaceAll(desc[0], "<b>" + desc[0] + "</b>"));
		out.write("</a> <i> ");
		out.write(desc[2]);
		out.write(" </i><br/>");
	   }
        } else {
	   for(Integer rem : tags.keySet()) {
		String[] desc = tags.get(rem);
                hit = new Hit(url, "<html>" + Util.htmlize(desc[3]).replaceAll(desc[0], "<b>" + desc[0] + "</b>"), 
                              desc[1], false, alt);
                hit.setTag(desc[2]);
                hits.add(hit);
	   }
        }
	}
  }
%}

//WhiteSpace     = [ \t\f\r]+|\n
Identifier = [a-zA-Z_] [a-zA-Z0-9_]*
Number = [0-9]+|[0-9]+\.[0-9]+| "0[xX]" [0-9a-fA-F]+
Printable = [\@\$\%\^\&\-+=\?\.\:]


%%
{Identifier}|{Number}|{Printable}	{return loweryytext();}
<<EOF>>   { return null;}

\n	{
		if(!wait) {
			markedPos = zzMarkedPos;
			markedLine = yyline+1;
			matchStart = -1;
			curLinePos=zzMarkedPos;
		}
		if(dumpRest) {
                        if (out != null) {
			   printWithNum(zzBuffer, rest, zzMarkedPos-1, markedLine);
			   out.write("</a>");
			   if(prevHi){
		   		out.write(" <i> ");
				String[] desc = tags.remove(prevLn);
				out.write(desc[2]);
				out.write("</i> ");
			   }
			   out.write("<br/>");
                        } else {
                           formatWithNum(zzBuffer, rest, zzMarkedPos-1, markedLine);
                           hit.setLine(sb.toString());
			   if(prevHi){
				String[] desc = tags.remove(prevLn);
				hit.setTag(desc[2]);
			   }
                           hits.add(hit);
                           sb.setLength(0);
                           hit = new Hit(url, null, null, false, alt);
                     }
			dumpRest = false;

		}
	}

.	{}
