package org.opensolaris.opengrok.egrok.ui;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Random;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IResourceVisitor;
import org.eclipse.core.resources.IWorkspaceRoot;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.jface.action.Action;
import org.eclipse.jface.action.IMenuManager;
import org.eclipse.jface.dialogs.PopupDialog;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.FocusEvent;
import org.eclipse.swt.events.FocusListener;
import org.eclipse.swt.events.KeyAdapter;
import org.eclipse.swt.events.KeyEvent;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Text;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.PlatformUI;
import org.opensolaris.opengrok.egrok.Activator;
import org.opensolaris.opengrok.egrok.model.Hit;
import org.opensolaris.opengrok.egrok.model.HitContainer;
import org.opensolaris.opengrok.egrok.preferences.EGrokPreferencePage;

public class ResultsDialog extends PopupDialog {
  private static final String FILTER_DEFAULT = "Filter...";

  private ResultsControl viewer;
  private Text filter;
  private Point location;
  private String query;

  private ResultsView resultsView;

  public ResultsDialog(Shell parent, String query, Point location) {
    super(parent, SWT.BORDER | SWT.RESIZE, true, true, true, true, false,
        "{OpenGrok search results for '" + query + "'", "");

    this.query = query;
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
  protected Control createTitleControl(Composite parent) {
    Control result = super.createTitleControl(parent);

    return result;
  }

  @Override
  protected void fillDialogMenu(IMenuManager dialogMenu) {
    super.fillDialogMenu(dialogMenu);

    dialogMenu.add(new Action() {
      @Override
      public String getText() {
        return "Pin this result...";
      }

      @Override
      public void run() {
        IWorkbenchPage page = PlatformUI.getWorkbench()
            .getActiveWorkbenchWindow().getActivePage();

        try {
          resultsView = (ResultsView) page.showView(ResultsView.ID,
              String.valueOf(new Random().nextInt()),
              IWorkbenchPage.VIEW_ACTIVATE);

          resultsView.setQuery(query);
          resultsView.setHits(viewer.getHits());
        } catch (PartInitException ex) {

        }
      }
    });
  }

  @Override
  protected Control createContents(Composite parent) {
    Composite composite = (Composite) super.createContents(parent);

    composite.setLayout(new GridLayout(1, true));

    viewer = new ResultsControl(composite,
        new ResultsControl.IFilterProvider() {
          @Override
          public String getCurrentFilterText() {
            return filter.getText();
          }
        });

    viewer.getControl().setLayoutData(
        new GridData(SWT.FILL, SWT.FILL, true, true));

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

    viewer.setHits(roots);

    if (resultsView != null) {
      resultsView.setHits(roots);
    }

    Display.getDefault().asyncExec(new Runnable() {
      @Override
      public void run() {
        if (!viewer.getControl().isDisposed()) {
          viewer.refresh();
        }
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
              if (!viewer.getControl().isDisposed()) {
                viewer.refresh();
              }
              if (resultsView != null) {
                resultsView.refresh();
              }
            }
          });

        }
      };

      workspaceLocator.run();

    }
  }
}
