package org.opensolaris.opengrok.egrok.query;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
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
import org.eclipse.swt.widgets.Display;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;
import org.opensolaris.opengrok.egrok.Activator;
import org.opensolaris.opengrok.egrok.model.Hit;
import org.opensolaris.opengrok.egrok.preferences.EGrokPreferencePage;
import org.opensolaris.opengrok.egrok.ui.ResultsDialog;

public class Query {
  public static final String JSON_SUFFIX = "/json";

  private String freetext;

  public Query(String freetext) {
    this.freetext = freetext;
  }

  public void run(final ResultsDialog dialog) {
    try {
      String baseUrl = Activator.getDefault().getPreferenceStore()
          .getString(EGrokPreferencePage.BASE_URL);
      String userName = Activator.getDefault().getPreferenceStore()
          .getString(EGrokPreferencePage.USERNAME);
      String password = Activator.getDefault().getPreferenceStore()
          .getString(EGrokPreferencePage.PASSWORD);

      if (baseUrl == null || baseUrl.isEmpty()) {
        throw new RuntimeException(
            "Invalid base url, check your configuration!");
      }

      URL url = new URL(baseUrl + JSON_SUFFIX + "?freetext=" + URLEncoder.encode(freetext, "UTF-8"));

      final HttpURLConnection conn = (HttpURLConnection) (baseUrl
          .startsWith("https") ? createHttpsUrlConnection(url) : url
          .openConnection());

      if (userName != null && password != null && !userName.isEmpty()
          && !password.isEmpty()) {
        String base64 = EncodingUtils.encodeBase64((userName + ":" + password)
            .getBytes());
        conn.setRequestProperty("Authorization", "Basic " + base64);
      }

      Runnable runnable = new Runnable() {

        @Override
        public void run() {
          try {
            conn.connect();
            BufferedReader br = new BufferedReader(new InputStreamReader(
                conn.getInputStream()));

            String tmp = null;
            StringBuffer buffer = new StringBuffer();
            while ((tmp = br.readLine()) != null) {
              buffer.append(tmp);
            }

            List<Hit> resultList = parseJSON(buffer.toString());

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
            java.security.cert.X509Certificate[] certs, String authType) {
        }

        @Override
        public void checkServerTrusted(
            java.security.cert.X509Certificate[] certs, String authType) {
        }
      } };
      SSLContext sc = SSLContext.getInstance("SSL");
      sc.init(null, trustAllCerts, new java.security.SecureRandom());
      HttpsURLConnection.setDefaultSSLSocketFactory(sc.getSocketFactory());
      HttpsURLConnection.setDefaultHostnameVerifier(new HostnameVerifier() {
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

  private List<Hit> parseJSON(String json) throws ParseException {
    JSONObject results = (JSONObject) new JSONParser().parse(json.toString());

    JSONArray array = (JSONArray) results.get("results");

    List<Hit> resultList = new ArrayList<>();
    for (Object obj : array) {
      JSONObject result = (JSONObject) obj;
      Hit hit = new Hit(result);
      if (hit.isValid()) {
        resultList.add(hit);
      }
    }
    return resultList;
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

}
