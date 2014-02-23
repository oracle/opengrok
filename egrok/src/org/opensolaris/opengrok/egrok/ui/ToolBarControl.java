package org.opensolaris.opengrok.egrok.ui;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.swt.SWT;
import org.eclipse.swt.events.FocusEvent;
import org.eclipse.swt.events.FocusListener;
import org.eclipse.swt.events.KeyAdapter;
import org.eclipse.swt.events.KeyEvent;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Text;
import org.eclipse.ui.menus.WorkbenchWindowControlContribution;
import org.opensolaris.opengrok.egrok.query.Query;

public class ToolBarControl extends WorkbenchWindowControlContribution {
  public static final String JSON_SUFFIX = "/json";

  private List<String> history = new ArrayList<String>();
  private int historyIndex = 0;

  public ToolBarControl() {
  }

  public ToolBarControl(String id) {
    super(id);
  }

  @Override
  protected Control createControl(Composite parent) {
    final Text searchBox = new Text(parent, SWT.FILL | SWT.BORDER);

    TextUtils.setToDefault(searchBox, "{OpenGrok");
    searchBox.addFocusListener(new FocusListener() {
      @Override
      public void focusLost(FocusEvent e) {
        TextUtils.setToDefault(searchBox, "{OpenGrok");
      }

      @Override
      public void focusGained(FocusEvent e) {
        TextUtils.makeEditable(searchBox);
      }
    });

    searchBox.addKeyListener(new KeyAdapter() {
      @Override
      public void keyReleased(KeyEvent e) {
        if (e.keyCode == SWT.CR || e.keyCode == SWT.KEYPAD_CR) {
          String text = searchBox.getText();

          if (text != null && !"".equals(text)) {
            history.add(0, text);
            historyIndex = 0;

            Rectangle bounds = searchBox.getBounds();

            Point topLeft = new Point(bounds.x, bounds.y + bounds.height);
            topLeft = searchBox.getShell().toDisplay(topLeft);

            final ResultsDialog dialog = new ResultsDialog(Display.getCurrent()
                .getActiveShell(), text, topLeft);

            Query query = new Query(text);
            query.run(dialog);
          }
        } else if (e.keyCode == SWT.ARROW_UP) {
          historyIndex++;
          if (historyIndex > history.size()) {
            historyIndex = history.size();
          }
          if (historyIndex >= 0 && history.size() >= historyIndex) {
            searchBox.setText(history.get(historyIndex - 1));
          }
        } else if (e.keyCode == SWT.ARROW_DOWN) {
          historyIndex--;

          if (historyIndex < 0) {
            historyIndex = 0;
          } else if (history.size() > historyIndex) {
            searchBox.setText(history.get(historyIndex));
          }
        }
      }
    });

    return parent;
  }
}
