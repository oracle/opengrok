package org.opensolaris.opengrok.egrok.ui;

import java.awt.Desktop;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import org.eclipse.jface.dialogs.PopupDialog;
import org.eclipse.jface.preference.JFacePreferences;
import org.eclipse.jface.resource.JFaceResources;
import org.eclipse.jface.viewers.ColumnLabelProvider;
import org.eclipse.jface.viewers.DelegatingStyledCellLabelProvider;
import org.eclipse.jface.viewers.DelegatingStyledCellLabelProvider.IStyledLabelProvider;
import org.eclipse.jface.viewers.IOpenListener;
import org.eclipse.jface.viewers.ITreeContentProvider;
import org.eclipse.jface.viewers.OpenEvent;
import org.eclipse.jface.viewers.StructuredSelection;
import org.eclipse.jface.viewers.StyledString;
import org.eclipse.jface.viewers.TreeViewer;
import org.eclipse.jface.viewers.Viewer;
import org.eclipse.jface.viewers.ViewerFilter;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.FocusEvent;
import org.eclipse.swt.events.FocusListener;
import org.eclipse.swt.events.KeyAdapter;
import org.eclipse.swt.events.KeyEvent;
import org.eclipse.swt.graphics.Font;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.graphics.TextStyle;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Text;
import org.opensolaris.opengrok.egrok.Activator;
import org.opensolaris.opengrok.egrok.model.Hit;
import org.opensolaris.opengrok.egrok.model.HitContainer;
import org.opensolaris.opengrok.egrok.preferences.EGrokPreferencePage;

public class ResultsDialog extends PopupDialog {
  private static final String FILTER_DEFAULT = "Filter...";

  private class ListContentPvider implements ITreeContentProvider {
    private Map<String, HitContainer> map;

    @Override
    public void dispose() {
    }

    @Override
    public void inputChanged(Viewer viewer, Object oldInput, Object newInput) {
    }

    @Override
    public Object[] getElements(Object inputElement) {
      if (map == null) {
        return new Object[] { "Searching..." };
      } else if (map.isEmpty()) {
        return new Object[] { "No results found!" };
      }

      if (inputElement instanceof HitContainer) {
        String key = ((HitContainer) inputElement).getName();

        return map.get(key).getHits();
      } else if (inputElement instanceof Hit) {
        return new Object[] {};
      }

      return map.values().toArray(new Object[map.values().size()]);
    }

    public void setHits(Map<String, HitContainer> map) {
      this.map = map;
    }

    @Override
    public Object[] getChildren(Object parentElement) {
      return getElements(parentElement);
    }

    @Override
    public Object getParent(Object element) {
      // TODO Auto-generated method stub
      return null;
    }

    @Override
    public boolean hasChildren(Object element) {
      return element instanceof HitContainer;
    }
  }

  private class ListLabelProvider extends ColumnLabelProvider implements
      IStyledLabelProvider {

    @Override
    public String getText(Object element) {
      return getStyledText(element).toString();
    }

    @Override
    public StyledString getStyledText(Object element) {
      if (element instanceof String) {
        return new StyledString((String) element);
      }

      if (element instanceof Hit) {
        Hit hit = (Hit) element;
        StyledString result = new StyledString();

        StyledString.Styler courierNew = new StyledString.Styler() {
          @Override
          public void applyStyles(TextStyle textStyle) {
            textStyle.font = new Font(null, "Courier New", viewer.getControl()
                .getFont().getFontData()[0].getHeight(), SWT.NORMAL);
          }
        };
        StyledString.Styler courierNewHighlight = new StyledString.Styler() {
          @Override
          public void applyStyles(TextStyle textStyle) {
            textStyle.font = new Font(null, "Courier New", viewer.getControl()
                .getFont().getFontData()[0].getHeight(), SWT.NORMAL);
            textStyle.foreground = JFaceResources.getColorRegistry().get(
                JFacePreferences.QUALIFIER_COLOR);
          }
        };

        result.append(hit.getLineno() + " ", courierNew);

        String line = hit.getLine();
        int startidx = line.indexOf("<b>");
        int endidx = line.indexOf("</b>");
        String before = line.substring(0, startidx);
        String term = line.substring(startidx + "<b>".length(), endidx);
        String after = line.substring(endidx + "</b>".length());

        result.append(before, courierNewHighlight);
        result.append(term, courierNew);
        result.append(after, courierNewHighlight);
        return result;
      } else if (element instanceof HitContainer) {
        HitContainer container = (HitContainer) element;

        StyledString result = new StyledString();

        String name = container.getName();
        String fileName = name.substring(name.lastIndexOf('/') + 1);
        result.append(name.substring(0, name.lastIndexOf('/') + 1),
            StyledString.COUNTER_STYLER);
        result.append(fileName);
        result.append(" (" + container.getNumberOfHits() + ")",
            StyledString.COUNTER_STYLER);

        return result;

      }

      return null;

    }
  }

  public ResultsDialog(Shell parent, Point location) {
    super(parent, SWT.BORDER, true, true, true, true, false,
        "{OpenGrok Search Results", "");

    this.location = location;
  }

  private ListContentPvider contentProvider = new ListContentPvider();
  private TreeViewer viewer;
  private Text filter;
  private Point location;

  @Override
  protected Point getInitialSize() {
    return new Point(500, 300);
  }

  @Override
  protected Control createDialogArea(Composite parent) {
    parent.setLayout(new GridLayout(1, true));

    filter = new Text(parent, SWT.FILL);
    filter.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, false));

    TextUtils.setToDefault(filter, FILTER_DEFAULT);
    filter.addFocusListener(new FocusListener() {
      @Override
      public void focusLost(FocusEvent e) {
        if ("".equals(filter.getText())) {
          TextUtils.setToDefault(filter, FILTER_DEFAULT);
        }
      }

      @Override
      public void focusGained(FocusEvent e) {
        TextUtils.makeEditable(filter);
      }
    });

    filter.addKeyListener(new KeyAdapter() {
      @Override
      public void keyReleased(KeyEvent e) {
        viewer.refresh();
      }
    });

    return parent;
  }

  @Override
  protected Control createInfoTextArea(Composite parent) {
    return parent;
  }

  @Override
  protected Control createContents(Composite parent) {
    Composite composite = (Composite) super.createContents(parent);

    composite.setLayout(new GridLayout(1, true));

    viewer = new TreeViewer(composite, SWT.FILL | SWT.V_SCROLL);

    viewer.setContentProvider(contentProvider);

    final ListLabelProvider labelProvider = new ListLabelProvider();

    viewer
        .setLabelProvider(new DelegatingStyledCellLabelProvider(labelProvider) {
          @Override
          public String getToolTipText(Object element) {
            return "";
          }
        });

    viewer.addFilter(new ViewerFilter() {
      @Override
      public boolean select(Viewer viewer, Object parentElement, Object element) {

        String text = labelProvider.getText(element);

        String filterText = filter.getText();

        if (filterText != null && !"".equals(filterText)
            && !FILTER_DEFAULT.equals(filterText)) {

          if (Activator.getDefault().getPreferenceStore()
              .getBoolean(EGrokPreferencePage.FILTER_REGEX)) {
            return Pattern.matches(filterText, text);
          } else {

            String[] cards = filterText.split("\\*");

            for (String card : cards) {
              int idx = text.indexOf(card);

              if (idx == -1) {
                return false;
              }

              text = text.substring(idx + card.length());
            }
            return true;
          }

        }

        return true;
      }
    });

    viewer.addOpenListener(new IOpenListener() {

      @Override
      public void open(OpenEvent event) {
        StructuredSelection selection = (StructuredSelection) event
            .getSelection();

        Object selected = selection.getFirstElement();

        if (selected instanceof Hit) {
          Hit hit = (Hit) selected;

          try {
            String baseurl = Activator.getDefault().getPreferenceStore()
                .getString(EGrokPreferencePage.BASE_URL);
            baseurl += "/xref" + hit.getDirectory() + "/" + hit.getFilename()
                + "#" + hit.getLineno();
            Desktop.getDesktop().browse(new URI(baseurl));
          } catch (IOException | URISyntaxException e) {
            e.printStackTrace();
          }
        } else if (selected instanceof HitContainer) {
          HitContainer container = (HitContainer) selected;

          try {
            String baseurl = Activator.getDefault().getPreferenceStore()
                .getString(EGrokPreferencePage.BASE_URL);
            baseurl += "/xref" + container.getName();
            Desktop.getDesktop().browse(new URI(baseurl));
          } catch (IOException | URISyntaxException e) {
            e.printStackTrace();
          }

        }

      }
    });

    viewer.setInput(new Object());

    viewer.getControl().setLayoutData(
        new GridData(SWT.FILL, SWT.FILL, true, true));

    viewer.getControl().setBackground(composite.getBackground());

    viewer.refresh();

    return composite;
  }

  @Override
  protected void adjustBounds() {
    getShell().setLocation(location);
    super.adjustBounds();
  }

  public void setResults(List<Hit> results) {
    Map<String, HitContainer> roots = new HashMap<String, HitContainer>();
    for (Hit hit : results) {
      String key = hit.getDirectory() + "/" + hit.getFilename();

      HitContainer container = roots.get(key);
      if (container == null) {
        container = new HitContainer(key);
        roots.put(key, container);
      }
      container.add(hit);
    }

    contentProvider.setHits(roots);

    Display.getDefault().asyncExec(new Runnable() {
      @Override
      public void run() {
        viewer.refresh();
      }
    });
  }

}
