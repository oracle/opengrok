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
 * Copyright 2009 Sun Microsystems, Inc.  All rights reserved.
 * Use is subject to license terms.
 */

package org.opensolaris.opengrok.analysis;

import java.io.IOException;
import java.io.Writer;
import java.util.Set;
import org.opensolaris.opengrok.configuration.Project;
import org.opensolaris.opengrok.configuration.RuntimeEnvironment;
import org.opensolaris.opengrok.history.Annotation;

/**
 *
 * @author Lubos Kosco
 */
public class JFlexXref {
  public Writer out;
  public String urlPrefix = RuntimeEnvironment.getInstance().getUrlPrefix();
  public Annotation annotation;
  public Project project;
  protected Definitions defs;
  
  public void setDefs(Definitions defs) {
  	this.defs = defs;
  }
  
  protected void appendProject() throws IOException {
      if (project != null) {
          out.write("&amp;project=");
          out.write(project.getDescription());
      }
  }
  
  protected String getProjectPostfix() {
      return project == null ? "" : ("&amp;project=" + project.getDescription());
  }

  /**
   * Write a symbol and generate links as appropriate.
   *
   * @param symbol the symbol to write
   * @param keywords a set of keywords recognized by this analyzer (no links
   * will be generated if the symbol is a keyword)
   * @param line the line number on which the symbol appears
   * @throws IOException if an error occurs while writing to the stream
   */
  protected void writeSymbol(String symbol, Set<String> keywords, int line)
          throws IOException {
      if (keywords.contains(symbol)) {
          // This is a keyword, so we don't create a link.
          out.append("<b>").append(symbol).append("</b>");

      } else if (defs != null && defs.hasDefinitionAt(symbol, line)) {
          // This is the definition of the symbol.

          // 1) Create an anchor for direct links. (Perhaps, we should only
          //    do this when there's exactly one definition of the symbol in
          //    this file? Otherwise, we may end up with multiple anchors with
          //    the same name.)
          out.append("<a class=\"d\" name=\"").append(symbol).append("\"/>");

          // 2) Create a link that searches for all references to this symbol.
          out.append("<a href=\"").append(urlPrefix).append("refs=");
          out.append(symbol);
          appendProject();
          out.append("\" class=\"d\">").append(symbol).append("</a>");

      } else if (defs != null && defs.occurrences(symbol) == 1) {
          // This is a reference to a symbol defined exactly once in this file.

          // Generate a direct link to the symbol definition.
          out.append("<a class=\"f\" href=\"#").append(symbol).append("\">")
                  .append(symbol).append("</a>");

      } else {
          // This is a symbol that is not defined in this file, or a symbol
          // that is defined more than once in this file. In either case, we
          // can't generate a direct link to the definition, so generate a
          // link to search for all definitions of that symbol instead.
          out.append("<a href=\"").append(urlPrefix).append("defs=");
          out.append(symbol);
          appendProject();
          out.append("\">").append(symbol).append("</a>");
      }
  }

}
