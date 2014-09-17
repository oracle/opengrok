package org.opensolaris.opengrok.egrok.model;

import org.eclipse.equinox.internal.security.storage.Base64;
import org.json.simple.JSONObject;

@SuppressWarnings("restriction")
public class Hit {
  private static final String ATTRIBUTE_DIRECTORY = "directory";
  private static final String ATTRIBUTE_FILENAME = "filename";
  private static final String ATTRIBUTE_LINENO = "lineno";
  private static final String ATTRIBUTE_LINE = "line";
  private static final String ATTRIBUTE_PATH = "path";

  private String directory;
  private String filename;
  private int lineno = -1;
  private String line;
  private String path;
  private HitContainer container;

  public Hit(JSONObject jsonobj) {
    String directory = (String) jsonobj.get(ATTRIBUTE_DIRECTORY);

    setDirectory(directory.replaceAll("\\\\", ""));

    setFilename((String) jsonobj.get(ATTRIBUTE_FILENAME));

    String base64 = (String) jsonobj.get(ATTRIBUTE_LINE);
    setLine(new String(Base64.decode(base64)));

    String lineno = (String) jsonobj.get(ATTRIBUTE_LINENO);
    if (!lineno.isEmpty()) {
      setLineno(Integer.parseInt(lineno));
    }
    setPath((String) jsonobj.get(ATTRIBUTE_PATH));
  }

  public Hit(String directory, String filename, int lineno, String line,
      String path) {
    super();
    this.directory = directory;
    this.filename = filename;
    this.lineno = lineno;
    this.line = line;
    this.path = path;
  }

  public String getDirectory() {
    return directory;
  }

  public void setDirectory(String directory) {
    this.directory = directory;
  }

  public String getFilename() {
    return filename;
  }

  public void setFilename(String filename) {
    this.filename = filename;
  }

  public int getLineno() {
    return lineno;
  }

  public void setLineno(int lineno) {
    this.lineno = lineno;
  }

  public String getLine() {
    return line;
  }

  public void setLine(String line) {
    this.line = line;
  }

  public String getPath() {
    return path;
  }

  public void setPath(String path) {
    this.path = path;
  }

  public boolean isValid() {
    return lineno > -1;
  }

  public HitContainer getContainer() {
    return container;
  }

  public void setContainer(HitContainer container) {
    this.container = container;
  }
}
