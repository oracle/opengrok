package org.opensolaris.opengrok.egrok.model;

import java.util.HashMap;
import java.util.Map.Entry;
import java.util.Set;

import org.eclipse.core.resources.IProject;

public class WorkspaceMatches {
  private HashMap<IProject, Integer> projects = new HashMap<IProject, Integer>();

  public void add(IProject project, int segmentOffset) {
    projects.put(project, segmentOffset);
  }

  public Set<Entry<IProject, Integer>> get() {
    return projects.entrySet();
  }

}
