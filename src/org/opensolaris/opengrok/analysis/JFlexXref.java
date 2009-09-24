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
import org.opensolaris.opengrok.configuration.RuntimeEnvironment;
import org.opensolaris.opengrok.history.Annotation;
import org.opensolaris.opengrok.configuration.Project;

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
          out.write(project.getPath());
      }
  }
  
  protected String getProjectPostfix() {
      return project == null ? "" : ("&amp;project=" + project.getPath());
  }

}
