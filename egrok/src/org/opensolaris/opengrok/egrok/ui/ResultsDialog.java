package org.opensolaris.opengrok.egrok.ui;

import java.awt.Desktop;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.regex.Pattern;

import org.eclipse.jface.dialogs.PopupDialog;
import org.eclipse.jface.viewers.ILabelProvider;
import org.eclipse.jface.viewers.ILabelProviderListener;
import org.eclipse.jface.viewers.IOpenListener;
import org.eclipse.jface.viewers.IStructuredContentProvider;
import org.eclipse.jface.viewers.ListViewer;
import org.eclipse.jface.viewers.OpenEvent;
import org.eclipse.jface.viewers.StructuredSelection;
import org.eclipse.jface.viewers.Viewer;
import org.eclipse.jface.viewers.ViewerFilter;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.FocusEvent;
import org.eclipse.swt.events.FocusListener;
import org.eclipse.swt.events.KeyAdapter;
import org.eclipse.swt.events.KeyEvent;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Text;
import org.opensolaris.opengrok.egrok.Activator;
import org.opensolaris.opengrok.egrok.model.Hit;
import org.opensolaris.opengrok.egrok.preferences.EGrokPreferencePage;

public class ResultsDialog extends PopupDialog {
	private static final String FILTER_DEFAULT = "Filter...";

	private class ListContentPvider implements IStructuredContentProvider {
		private List<Hit> hits;

		@Override
		public void dispose() {
		}

		@Override
		public void inputChanged(Viewer viewer, Object oldInput, Object newInput) {
		}

		@Override
		public Object[] getElements(Object inputElement) {
			if (hits == null) {
				return new Object[] { "Searching..." };
			} else if (hits.isEmpty()) {
				return new Object[] { "No results found!" };
			}
			return hits.toArray();
		}

		public void setHits(List<Hit> hits) {
			this.hits = hits;
		}
	}

	private class ListLabelProvider implements ILabelProvider {
		@Override
		public void removeListener(ILabelProviderListener listener) {
		}

		@Override
		public boolean isLabelProperty(Object element, String property) {
			return false;
		}

		@Override
		public void dispose() {
		}

		@Override
		public void addListener(ILabelProviderListener listener) {
		}

		@Override
		public String getText(Object element) {
			if (element instanceof String) {
				return (String) element;
			}

			Hit hit = (Hit) element;
			return hit.getFilename() + ": " + hit.getLineno();
		}

		@Override
		public Image getImage(Object element) {
			return null;
		}
	}

	public ResultsDialog(Shell parent, Point location) {
		super(parent, SWT.BORDER, true, true, true, true, true,
				"{OpenGrok Search Results", "");

		this.location = location;
	}

	private ListContentPvider contentProvider = new ListContentPvider();
	private ListViewer viewer;
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

		viewer = new ListViewer(composite, SWT.FILL | SWT.V_SCROLL);

		viewer.setContentProvider(contentProvider);

		viewer.setLabelProvider(new ListLabelProvider());

		viewer.addFilter(new ViewerFilter() {
			@Override
			public boolean select(Viewer viewer, Object parentElement,
					Object element) {

				if (!(element instanceof Hit)) {
					return true;
				}
				String filterText = filter.getText();

				if (filterText != null && !"".equals(filterText)
						&& !FILTER_DEFAULT.equals(filterText)) {

					Hit hit = (Hit) element;

					if (Activator.getDefault().getPreferenceStore()
							.getBoolean(EGrokPreferencePage.FILTER_REGEX)) {
						return Pattern.matches(filterText, hit.getFilename());
					} else {
						String text = hit.getFilename();

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
						String baseurl = Activator.getDefault()
								.getPreferenceStore()
								.getString(EGrokPreferencePage.BASE_URL);
						baseurl += "/xref"
								+ hit.getDirectory().replaceAll("\\\\", "")
								+ "/" + hit.getFilename() + "#"
								+ hit.getLineno();
						Desktop.getDesktop().browse(new URI(baseurl));
					} catch (IOException | URISyntaxException e) {
						e.printStackTrace();
					}
				}

			}
		});

		viewer.setInput(new Object());

		viewer.getList().setLayoutData(
				new GridData(SWT.FILL, SWT.FILL, true, true));

		viewer.getList().setBackground(composite.getBackground());

		viewer.refresh();

		return composite;
	}

	@Override
	public int open() {

		return super.open();

	}

	@Override
	protected void adjustBounds() {
		getShell().setLocation(location);
		super.adjustBounds();
	}

	public void setResults(List<Hit> results) {
		contentProvider.setHits(results);

		Display.getDefault().asyncExec(new Runnable() {
			@Override
			public void run() {
				viewer.refresh();
			}
		});
	}

}
