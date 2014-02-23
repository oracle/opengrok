package org.opensolaris.opengrok.egrok.ui;

import java.awt.Desktop;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.regex.Pattern;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IResourceVisitor;
import org.eclipse.core.resources.IWorkspaceRoot;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.jface.dialogs.PopupDialog;
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
import org.eclipse.swt.events.FocusEvent;
import org.eclipse.swt.events.FocusListener;
import org.eclipse.swt.events.KeyAdapter;
import org.eclipse.swt.events.KeyEvent;
import org.eclipse.swt.graphics.Font;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.graphics.TextStyle;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Text;
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

        StyledString.Styler courierNewSmall = new StyledString.Styler() {
          @Override
          public void applyStyles(TextStyle textStyle) {
            textStyle.font = new Font(null, "Courier New", viewer.getControl()
                .getFont().getFontData()[0].getHeight() - 1, SWT.NORMAL);
          }
        };
        StyledString.Styler courierNew = new StyledString.Styler() {
          @Override
          public void applyStyles(TextStyle textStyle) {
            textStyle.font = new Font(null, "Courier New", viewer.getControl()
                .getFont().getFontData()[0].getHeight(), SWT.NORMAL);
          }
        };
        StyledString.Styler courierNewGrey = new StyledString.Styler() {
          @Override
          public void applyStyles(TextStyle textStyle) {
            textStyle.font = new Font(null, "Courier New", viewer.getControl()
                .getFont().getFontData()[0].getHeight(), SWT.NORMAL);
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

  private ListContentPvider contentProvider = new ListContentPvider();
  private TreeViewer viewer;
  private Text filter;
  private Point location;

  public ResultsDialog(Shell parent, String query, Point location) {
    super(parent, SWT.BORDER | SWT.RESIZE, true, true, true, true, false,
        "{OpenGrok search results for '" + query + "'", "");

    this.location = location;
  }

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

        if (element instanceof Hit && parentElement instanceof HitContainer
            && select(viewer, null, parentElement)) {
          return true;
        }

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
          ResultsDialog.this.open((Hit) selected);
        } else if (selected instanceof HitContainer) {
          ResultsDialog.this.open((HitContainer) selected);
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
    final Map<String, HitContainer> roots = new HashMap<String, HitContainer>();
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

    if (Activator.getDefault().getPreferenceStore()
        .getBoolean(EGrokPreferencePage.WORKSPACE_MATCHES)) {

      Runnable workspaceLocator = new Runnable() {
        @Override
        public void run() {
          IWorkspaceRoot workspaceRoot = ResourcesPlugin.getWorkspace()
              .getRoot();
          final Map<HitContainer, HashMap<IProject, Integer>> potentialProjects = new HashMap<HitContainer, HashMap<IProject, Integer>>();

          final Map<IProject, ArrayList<String>> locationSegments = new HashMap<IProject, ArrayList<String>>();

          try {
            workspaceRoot.accept(new IResourceVisitor() {
              @Override
              public boolean visit(IResource resource) throws CoreException {
                if (resource instanceof IWorkspaceRoot) {
                  return true;
                }
                if (resource instanceof IProject) {
                  IProject project = (IProject) resource;

                  IPath location = project.getLocation();

                  for (String segment : location.segments()) {
                    ArrayList<String> segments = locationSegments.get(project);
                    if (segments == null) {
                      segments = new ArrayList<String>();
                      locationSegments.put(project, segments);
                    }
                    segments.add(segment);
                  }
                }
                return false;
              }

            });
          } catch (CoreException e) {
            e.printStackTrace();
          }

          Map<HitContainer, ArrayList<String>> hitcontainerSegments = new HashMap<HitContainer, ArrayList<String>>();

          for (HitContainer hitcontainer : roots.values()) {
            ArrayList<String> segments = new ArrayList<String>();

            for (String segment : hitcontainer.getName().split("/")) {
              segments.add(segment);
            }

            hitcontainerSegments.put(hitcontainer, segments);
          }

          for (IProject project : locationSegments.keySet()) {
            ArrayList<String> segments = locationSegments.get(project);
            int idx = 0;
            for (String segment : segments) {
              for (HitContainer container : hitcontainerSegments.keySet()) {

                for (String containerPathSegment : hitcontainerSegments
                    .get(container)) {
                  if (segment.equals(containerPathSegment)) {
                    HashMap<IProject, Integer> matches = potentialProjects
                        .get(container);

                    if (matches == null) {
                      matches = new HashMap<IProject, Integer>();
                      potentialProjects.put(container, matches);
                    }

                    matches.put(project, idx);
                  }
                }
              }
              idx++;
            }
          }

          for (HitContainer container : potentialProjects.keySet()) {
            String fullLocation = container.getName();
            HashMap<IProject, Integer> matches = potentialProjects
                .get(container);

            System.out.println(container.getName());
            for (Entry<IProject, Integer> match : matches.entrySet()) {
              IProject project = match.getKey();
              Integer matchingLocation = match.getValue();
              String matchingString = project.getLocation().segment(
                  matchingLocation);
              System.out.println("match: " + matchingString);

              String local = fullLocation.substring(fullLocation
                  .indexOf(matchingString) + matchingString.length());

              System.out.println("local: " + local);

              IResource member = project.findMember(local);
              System.out.println("member: " + member);

              if (member instanceof IFile) {
                IFile file = (IFile) member;

                container.setCorrespondingFile(file);
              }
            }
          }

          Display.getDefault().asyncExec(new Runnable() {
            @Override
            public void run() {
              viewer.refresh();
            }
          });

        }
      };

      workspaceLocator.run();

    }
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
