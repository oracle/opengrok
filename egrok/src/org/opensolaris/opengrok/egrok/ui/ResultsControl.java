package org.opensolaris.opengrok.egrok.ui;

import java.awt.Desktop;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import org.eclipse.core.resources.IFile;
import org.eclipse.jface.preference.JFacePreferences;
import org.eclipse.jface.resource.JFaceResources;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.IRegion;
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
import org.eclipse.swt.graphics.Font;
import org.eclipse.swt.graphics.FontData;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.TextStyle;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.ui.IEditorDescriptor;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.part.FileEditorInput;
import org.eclipse.ui.texteditor.ITextEditor;
import org.opensolaris.opengrok.egrok.Activator;
import org.opensolaris.opengrok.egrok.model.Hit;
import org.opensolaris.opengrok.egrok.model.HitContainer;
import org.opensolaris.opengrok.egrok.preferences.EGrokPreferencePage;

public class ResultsControl extends TreeViewer {
  public interface IFilterProvider {
    String getCurrentFilterText();
  }

  private class ResultsContentProvider implements ITreeContentProvider {
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

      List<HitContainer> rootElements = new ArrayList<HitContainer>(
          map.values());

      Collections.sort(rootElements, new Comparator<HitContainer>() {
        @Override
        public int compare(HitContainer o1, HitContainer o2) {
          if (o1.getCorrespondingFile() != null
              && o2.getCorrespondingFile() == null) {
            return -1;
          } else if (o1.getCorrespondingFile() == null
              && o2.getCorrespondingFile() != null) {
            return 1;
          }
          return o2.getNumberOfHits() - o1.getNumberOfHits();
        }
      });

      return rootElements.toArray(new Object[rootElements.size()]);
    }

    public void setHits(Map<String, HitContainer> map) {
      this.map = map;
    }

    public Map<String, HitContainer> getHits() {
      return map;
    }

    @Override
    public Object[] getChildren(Object parentElement) {
      return getElements(parentElement);
    }

    @Override
    public Object getParent(Object element) {
      return null;
    }

    @Override
    public boolean hasChildren(Object element) {
      return element instanceof HitContainer;
    }
  }

  private class ResultsLabelProvider extends ColumnLabelProvider implements
      IStyledLabelProvider {

    private FontData defaultFont;

    public ResultsLabelProvider(FontData defaultFont) {
      this.defaultFont = defaultFont;
    }

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

        StyledString.Styler courierNewSmall = new StyledString.Styler() {
          @Override
          public void applyStyles(TextStyle textStyle) {
            textStyle.font = new Font(null, "Courier New",
                defaultFont.getHeight() - 1, SWT.NORMAL);
          }
        };
        StyledString.Styler courierNew = new StyledString.Styler() {
          @Override
          public void applyStyles(TextStyle textStyle) {
            textStyle.font = new Font(null, "Courier New",
                defaultFont.getHeight(), SWT.NORMAL);
          }
        };
        StyledString.Styler courierNewGrey = new StyledString.Styler() {
          @Override
          public void applyStyles(TextStyle textStyle) {
            textStyle.font = new Font(null, "Courier New",
                defaultFont.getHeight(), SWT.NORMAL);
            textStyle.foreground = JFaceResources.getColorRegistry().get(
                JFacePreferences.QUALIFIER_COLOR);
          }
        };

        result.append(hit.getLineno() + " ", courierNewSmall);

        String line = hit.getLine();

        int startidx = line.indexOf("<b>");
        int endidx = line.indexOf("</b>");
        String before = line.substring(0, startidx);
        String term = line.substring(startidx + "<b>".length(), endidx);
        String after = line.substring(endidx + "</b>".length());

        result.append(before, courierNewGrey);
        result.append(term, courierNew);
        result.append(after, courierNewGrey);
        return result;
      } else if (element instanceof HitContainer) {
        HitContainer container = (HitContainer) element;
        StyledString result = new StyledString();

        IFile file = container.getCorrespondingFile();
        if (file != null) {
          String project = file.getProject().getName();

          result.append(project + "/");
          result.append(file.getProjectRelativePath().toString());

        } else {

          String name = container.getName();
          String fileName = name.substring(name.lastIndexOf('/') + 1);
          result.append(name.substring(0, name.lastIndexOf('/') + 1),
              StyledString.COUNTER_STYLER);
          result.append(fileName);
        }
        result.append(" (" + container.getNumberOfHits() + ")",
            StyledString.DECORATIONS_STYLER);
        return result;

      }

      return null;
    }

    @Override
    public Image getImage(Object element) {
      if (element instanceof HitContainer) {
        HitContainer container = (HitContainer) element;
        return findEditor(container).getImageDescriptor().createImage();
      }
      return super.getImage(element);
    }
  }

  private ResultsContentProvider contentProvider;
  private ResultsLabelProvider labelProvider;

  public ResultsControl(Composite parent, final IFilterProvider filterProvider) {
    super(parent, SWT.FILL | SWT.V_SCROLL);

    contentProvider = new ResultsContentProvider();
    labelProvider = new ResultsLabelProvider(getControl().getFont()
        .getFontData()[0]);

    setContentProvider(contentProvider);
    setLabelProvider(new DelegatingStyledCellLabelProvider(labelProvider));

    if (filterProvider != null) {
      addFilter(new ViewerFilter() {
        @Override
        public boolean select(Viewer viewer, Object parentElement,
            Object element) {

          String text = labelProvider.getText(element);

          String filterText = filterProvider.getCurrentFilterText();

          if (element instanceof Hit && parentElement instanceof HitContainer
              && select(viewer, null, parentElement)) {
            return true;
          }

          if (filterText != null && !"".equals(filterText)
              && !"Filter...".equals(filterText)) {

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
    }

    addOpenListener(new IOpenListener() {
      @Override
      public void open(OpenEvent event) {
        StructuredSelection selection = (StructuredSelection) event
            .getSelection();

        Object selected = selection.getFirstElement();

        if (selected instanceof Hit) {
          ResultsControl.this.open((Hit) selected);
        } else if (selected instanceof HitContainer) {
          ResultsControl.this.open((HitContainer) selected);
        }
      }
    });

    setInput(new Object());

    getControl().setBackground(parent.getBackground());
  }

  public void setHits(Map<String, HitContainer> map) {
    contentProvider.setHits(map);
  }

  public Map<String, HitContainer> getHits() {
    return contentProvider.getHits();
  }

  private IEditorPart open(Hit hit) {
    HitContainer container = hit.getContainer();
    IFile correspondingFile = container.getCorrespondingFile();

    if (correspondingFile != null) {
      IEditorPart editor = openInEditor(correspondingFile,
          findEditor(hit.getContainer()));

      if (editor instanceof ITextEditor) {
        ITextEditor textEditor = (ITextEditor) editor;

        IDocument document = textEditor.getDocumentProvider().getDocument(
            editor.getEditorInput());
        if (document != null) {
          IRegion lineInfo = null;
          try {
            lineInfo = document.getLineInformation(hit.getLineno() - 1);
          } catch (BadLocationException e) {
            e.printStackTrace();
          }
          if (lineInfo != null) {
            textEditor.selectAndReveal(lineInfo.getOffset(),
                lineInfo.getLength());
          }
        }
      }
    } else {
      openInBrowser(hit.getDirectory() + "/" + hit.getFilename(),
          hit.getLineno());
    }

    return null;
  }

  private IEditorPart open(HitContainer container) {
    IFile file = container.getCorrespondingFile();

    if (file != null) {
      openInEditor(file, findEditor(container));
    } else {
      openInBrowser(container.getName(), -1);
    }

    return null;
  }

  private void openInBrowser(String location, int lineNo) {
    try {
      String baseUrl = Activator.getDefault().getPreferenceStore()
          .getString(EGrokPreferencePage.BASE_URL);
      baseUrl += "/xref" + location;
      if (lineNo > -1) {
        baseUrl += "#" + lineNo;
      }
      Desktop.getDesktop().browse(new URI(baseUrl));
    } catch (IOException | URISyntaxException e) {
      e.printStackTrace();
    }

  }

  private IEditorPart openInEditor(IFile file, IEditorDescriptor desc) {
    IWorkbenchPage page = PlatformUI.getWorkbench().getActiveWorkbenchWindow()
        .getActivePage();

    try {
      return page.openEditor(new FileEditorInput(file), desc.getId());
    } catch (PartInitException e) {
      e.printStackTrace();
    }

    return null;
  }

  private IEditorDescriptor findEditor(HitContainer container) {
    String fileName = container.getName().substring(
        container.getName().lastIndexOf('/') + 1);
    return findEditor(fileName);
  }

  private IEditorDescriptor findEditor(String fileName) {
    IEditorDescriptor desc = PlatformUI.getWorkbench().getEditorRegistry()
        .getDefaultEditor(fileName);
    if (desc == null) {
      desc = PlatformUI.getWorkbench().getEditorRegistry()
          .findEditor("org.eclipse.ui.DefaultTextEditor");
    }
    return desc;
  }

}
