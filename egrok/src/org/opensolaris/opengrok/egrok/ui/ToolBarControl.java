package org.opensolaris.opengrok.egrok.ui;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import org.eclipse.equinox.security.storage.EncodingUtils;
import org.eclipse.jface.dialogs.MessageDialog;
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
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.opensolaris.opengrok.egrok.Activator;
import org.opensolaris.opengrok.egrok.model.Hit;
import org.opensolaris.opengrok.egrok.preferences.EGrokPreferencePage;

public class ToolBarControl extends WorkbenchWindowControlContribution {
	public static final String JSON_SUFFIX = "/json";

	private List<String> history = new ArrayList<String>();
	private int historyIndex = 0;
	private Text searchBox;

	public ToolBarControl() {
	}

	public ToolBarControl(String id) {
		super(id);
	}

	@Override
	protected Control createControl(Composite parent) {
		searchBox = new Text(parent, SWT.FILL | SWT.BORDER);

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
						doSearch(text);
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

	private void doSearch(String text) {
		String baseUrl = Activator.getDefault().getPreferenceStore()
				.getString(EGrokPreferencePage.BASE_URL);
		String userName = Activator.getDefault().getPreferenceStore()
				.getString(EGrokPreferencePage.USERNAME);
		String password = Activator.getDefault().getPreferenceStore()
				.getString(EGrokPreferencePage.PASSWORD);

		try {
			URL url = new URL(baseUrl + JSON_SUFFIX + "?freetext=" + text);
			final HttpURLConnection conn = (HttpURLConnection) (baseUrl
					.startsWith("https") ? createHttpsUrlConnection(url) : url
					.openConnection());

			if (userName != null && password != null && !"".equals(userName)
					&& !"".equals(password)) {
				String base64 = EncodingUtils
						.encodeBase64((userName + ":" + password).getBytes());
				conn.setRequestProperty("Authorization", "Basic " + base64);
			}

			final ResultsDialog dialog = createResultsDialog();

			Runnable runnable = new Runnable() {

				@Override
				public void run() {
					try {
						conn.connect();
						BufferedReader br = new BufferedReader(
								new InputStreamReader(conn.getInputStream()));

						String tmp = null;
						StringBuffer buffer = new StringBuffer();
						while ((tmp = br.readLine()) != null) {
							buffer.append(tmp);
						}

						JSONParser parser = new JSONParser();
						JSONObject results = (JSONObject) parser.parse(buffer
								.toString());

						JSONArray array = (JSONArray) results.get("results");

						List<Hit> resultList = new ArrayList<>();
						for (Object obj : array) {
							JSONObject result = (JSONObject) obj;
							Hit hit = new Hit(result);
							if (hit.isValid()) {
								resultList.add(hit);
							}
						}

						dialog.setResults(resultList);

					} catch (Exception e) {
						handleException(e);
					}

				}
			};

			new Thread(runnable).start();
			dialog.open();

		} catch (Exception e) {
			handleException(e);
		}

	}

	private ResultsDialog createResultsDialog() {
		Rectangle bounds = searchBox.getBounds();

		Point topLeft = new Point(bounds.x, bounds.y + bounds.height);
		topLeft = searchBox.getShell().toDisplay(topLeft);

		final ResultsDialog dialog = new ResultsDialog(Display.getCurrent()
				.getActiveShell(), topLeft);
		return dialog;
	}

	private void handleException(final Exception e) {
		e.printStackTrace();

		Display.getDefault().asyncExec(new Runnable() {
			@Override
			public void run() {
				MessageDialog dialog = new MessageDialog(Display.getDefault()
						.getActiveShell(), "Error", null, e.toString(),
						MessageDialog.ERROR, new String[] { "OK" }, 0);
				dialog.open();
			}
		});

	}

	private HttpsURLConnection createHttpsUrlConnection(URL url) {
		try {
			System.setProperty("jsse.enableSNIExtension", "false");

			TrustManager[] trustAllCerts = new TrustManager[] { new X509TrustManager() {
				@Override
				public java.security.cert.X509Certificate[] getAcceptedIssuers() {
					return null;
				}

				@Override
				public void checkClientTrusted(
						java.security.cert.X509Certificate[] certs,
						String authType) {
				}

				@Override
				public void checkServerTrusted(
						java.security.cert.X509Certificate[] certs,
						String authType) {
				}
			} };
			SSLContext sc = SSLContext.getInstance("SSL");
			sc.init(null, trustAllCerts, new java.security.SecureRandom());
			HttpsURLConnection
					.setDefaultSSLSocketFactory(sc.getSocketFactory());
			HttpsURLConnection
					.setDefaultHostnameVerifier(new HostnameVerifier() {
						@Override
						public boolean verify(String arg0, SSLSession arg1) {
							return true;
						}
					});

			return (HttpsURLConnection) url.openConnection();
		} catch (Exception ex) {
			handleException(ex);
		}
		return null;
	}

}
