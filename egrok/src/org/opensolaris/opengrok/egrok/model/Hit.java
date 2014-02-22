package org.opensolaris.opengrok.egrok.model;

import org.eclipse.equinox.internal.security.storage.Base64;
import org.json.simple.JSONObject;

public class Hit {
  public Hit(JSONObject jsonobj) {
    String directory = (String) jsonobj.get("directory");
    setDirectory(directory.replaceAll("\\\\", ""));
    setFilename((String) jsonobj.get("filename"));

    String base64 = (String) jsonobj.get("line");
    setLine(new String(Base64.decode(base64)));

    String lineno = (String) jsonobj.get("lineno");
    if (!lineno.isEmpty()) {
      setLineno(Integer.parseInt(lineno));
    }
    setPath((String) jsonobj.get("path"));
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

  private String directory;
  private String filename;
  private int lineno = -1;
  private String line;
  private String path;

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
}
