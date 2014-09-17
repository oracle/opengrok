package org.opensolaris.opengrok.egrok.model;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.core.resources.IFile;

public class HitContainer {
  private String name;
  private List<Hit> hits = new ArrayList<Hit>();
  private IFile correspondingFile;

  public HitContainer(String name) {
    this.name = name;
  }

  public void add(Hit hit) {
    hits.add(hit);
    hit.setContainer(this);
  }

  public String getName() {
    return name;
  }

  public int getNumberOfHits() {
    return hits.size();
  }

  public Hit[] getHits() {
    return hits.toArray(new Hit[hits.size()]);
  }

  public void setCorrespondingFile(IFile file) {
    this.correspondingFile = file;
  }

  public IFile getCorrespondingFile() {
    return correspondingFile;
  }
}
