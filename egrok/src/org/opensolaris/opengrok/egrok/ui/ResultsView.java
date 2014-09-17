package org.opensolaris.opengrok.egrok.ui;

import java.util.Map;

import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.ui.part.ViewPart;
import org.opensolaris.opengrok.egrok.model.HitContainer;

public class ResultsView extends ViewPart {
  public static final String ID = "org.opensolaris.opengrok.egrok.resultsView";

  private ResultsControl viewer;

  public ResultsView() {
  }

  @Override
  public void createPartControl(Composite parent) {
    viewer = new ResultsControl(parent, null);

  }

  @Override
  public void setFocus() {

  }

  public void setHits(Map<String, HitContainer> map) {
    viewer.setHits(map);

    Display.getDefault().asyncExec(new Runnable() {
      @Override
      public void run() {
        viewer.refresh();
      }
    });
  }

  public void refresh() {
    viewer.refresh();
  }

  public void setQuery(String query) {
    setPartName("{OpenGrok search results for '" + query + "'");
  }

}
