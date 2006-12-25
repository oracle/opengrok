/*
 * CDDL HEADER START
 *
 * The contents of this file are subject to the terms of the
 * Common Development and Distribution License (the "License").
 * You may not use this file except in compliance with the License.
 *
 * See LICENSE.txt included in this distribution for the specific
 * language governing permissions and limitations under the License.
 *
 * When distributing Covered Code, include this CDDL HEADER in each
 * file and include the License file at LICENSE.txt.
 * If applicable, add the following below this CDDL HEADER, with the
 * fields enclosed by brackets "[]" replaced with your own identifying
 * information: Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 */

/*
 * Copyright 2005 Trond Norbye.  All rights reserved.
 * Use is subject to license terms.
 */
 
/*
 * ident	"@(#)Config.java 1.1     06/02/22 SMI"
 */

package org.opensolaris.opengrok.search.scope;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.List;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;
import org.opensolaris.opengrok.search.scope.editor.ExternalEditor;
import org.opensolaris.opengrok.search.scope.editor.InternalEditor;


/**
 * The Config class is a Singleton class that provides a single storage towards
 * all of the user configurable settings.
 *
 * @author Trond Norbye
 *
 * @todo I should refactor the editor part. see comments in Editor.java
 */
public class Config {
    /**
     * This is the one and only instance of the Config class
     */
    private static Config instance = new Config();

    /**
     * The label used in the Preferences to store the location of the index
     * database
     */
    private static final String DATA_ROOT = "DATA_ROOT";

    /**
     * The label used to store the display-name of an editor
     */
    private static final String NAME_LABEL = "name";

    /**
     * The label used to store the command of an editor
     *
     * @todo Specify the exact format (%s will be substituted with the filename
     *       etc)
     */
    private static final String COMMAND_LABEL = "command";

    /**
     * The label used to store the name of the class this editor will use
     */
    private static final String CLASS_LABEL = "class";

    /**
     * The preferences backend
     */
    private Preferences prefs;

    /**
     * A list of the available editors
     */
    private List<Editor> editors;

    /**
     * The location of the sources
     */
    private String sourceRoot;

    /**
     * The location of the repositories
     */
    private List<IndexDatabase> databases;

    /**
     * Create a new instance of Config. This constructor is private in order to
     * enforce a singleton pattern
     */
    private Config() {
        prefs = Preferences.userNodeForPackage(this.getClass());
        editors = null;
        sourceRoot = null;

        try {
            String[] names = prefs.childrenNames();

            if ((names == null) || (names.length == 0)) {
                // First time execution.. Create initial preferences
                Preferences epref = prefs.node("editors");
                Preferences editor = epref.node("xemacs");

                editor.put(NAME_LABEL, "XEmacs");
                editor.put(COMMAND_LABEL, "xemacs +%d %s");
                editor.put(CLASS_LABEL, ExternalEditor.class.getName());
                editor.sync();

                editor = epref.node("gvim");
                editor.put(NAME_LABEL, "gvim");
                editor.put(COMMAND_LABEL, "gvim --remote-silent +%d %s");
                editor.put(CLASS_LABEL, ExternalEditor.class.getName());
                editor.sync();

                editor = epref.node("custom");
                editor.put(NAME_LABEL, "Custom");
                editor.put(COMMAND_LABEL, "");
                editor.put(CLASS_LABEL, ExternalEditor.class.getName());
                editor.sync();

                editor = epref.node("internal");
                editor.put(NAME_LABEL, "Internal");
                editor.put(COMMAND_LABEL, "");
                editor.put(CLASS_LABEL, InternalEditor.class.getName());
                editor.sync();

                epref.sync();
                prefs.sync();
            }
        } catch (BackingStoreException e) {
            ;
        }

        String editor = prefs.get("editor", null);
        if(editor == null) {
            editor = System.getenv("EDITOR");
        }

        if ((editor != null) && (editor.length() > 0)) {
            // Try to see if I have this editor configured
            String name = editor;
            int index = editor.lastIndexOf(File.separator);

            if (index != -1) {
                name = editor.substring(index + File.separator.length());
            }

            boolean found = false;

            for (Editor ed : getAvailableEditors()) {
                if (ed.getName().equalsIgnoreCase(name)) {
                    found = true;

                    break;
                }
            }

            if (!found) {
                ExternalEditor ed = new ExternalEditor();
                ed.setName(name);
                ed.setCommand(editor + " %s");
                editors.add(ed);
            }

            prefs.put("editor", name);
        }
    }

    /**
     * Get the one and only instance of the Config class
     *
     * @return A Config object
     */
    public static Config getInstance() {
        return instance;
    }

    /**
     * Get the directory where the sources are located. The location of the
     * sources is stored in a file named SRC_ROOT in the root of the index
     * database. This function will read that file and return the content.
     *
     * @return The name of the directory containing the sources
     */
    public String getSourceRoot() {
        if (sourceRoot == null) {
            StringBuilder sb = new StringBuilder();
            sb.append(getIndexDatabase());
            sb.append(File.separator);
            sb.append("SRC_ROOT");

            try {
                BufferedReader r = new BufferedReader(new FileReader(
                            sb.toString()));
                sourceRoot = r.readLine();
                r.close();
            } catch (Exception e) {
                ;
            }
        }

        return sourceRoot;
    }

    /**
     * Get the name of the directory containing the index database.
     *
     * @return The name of the directory containing the index database.
     */
    public String getIndexDatabase() {
        getAvailableIndexDatabases();

        String ret = null;

        if (databases.size() > 0) {
            ret = databases.get(0).getDatabase();
        }

        return ret;
    }

    /**
     * Store the name of the directory containing the index database.
     *
     * @param dataRoot The directory containing the index database
     */
    public void setIndexDatabase(String dataRoot) {
        sourceRoot = null;
        databases.add(0, new IndexDatabase(dataRoot));

        // remove any "duplicates"
        int len = databases.size();

        for (int ii = 1; ii < len; ++ii) {
            if (databases.get(ii).getDatabase().equals(dataRoot)) {
                databases.remove(ii);

                break;
            }
        }

        if (databases.size() > 9) {
            databases.remove(10);
        }

        int ii = 0;

        for (IndexDatabase db : databases) {
            prefs.put("database" + ii, db.getDatabase());
            ++ii;
        }
    }

    /**
     * Get a list of the available editors to use to display files. You may add
     * your own editor by subclassing Editor, and add an entry in the
     * preferences under <em>editors</em>. Create a new "subnode" for your
     * editor, and add the following key's:
     * <pre>
     *    name - this key contains the display-name of the editor
     *    command - this key contains the command to display in the command field
     *    class - this key contains the name of the class that implements the editor.
     * </pre>
     *
     * @return The list of available editors
     */
    public List<Editor> getAvailableEditors() {
        if (editors == null) {
            Preferences epref = prefs.node("editors");
            editors = new ArrayList<Editor>();

            String[] names = null;

            try {
                names = epref.childrenNames();
            } catch (Exception e) {
                e.printStackTrace();
            }

            if (names != null) {
                for (int ii = 0; ii < names.length; ++ii) {
                    Preferences editor = epref.node(names[ii]);
                    String name = editor.get(NAME_LABEL, null);
                    String command = editor.get(COMMAND_LABEL, null);
                    String clazz = editor.get(CLASS_LABEL, null);

                    if ((name != null) && (command != null) && (clazz != null)) {
                        try {
                            Class c = Class.forName(clazz);
                            Editor ed = (Editor)c.newInstance();
                            ed.setName(name);
                            ed.setCommand(command);
                            editors.add(ed);
                        } catch (Exception e) {
                            System.err.println("Failed to add editor: " +
                                e.getMessage());
                            e.printStackTrace(System.err);
                        }
                    }
                }
            }
        }

        return editors;
    }

    /**
     * Get the selected editor.
     *
     * @return The editor to use
     */
    public Editor getEditor() {
        String editor = prefs.get("editor", null);

        if (editor == null) {
            editor = "Internal";
        }

        List<Editor> eds = getAvailableEditors();
        Editor ret = null;

        for (Editor ed : eds) {
            if (ed.getName().equalsIgnoreCase(editor)) {
                ret = ed;
                break;
            }
        }

        return ret;
    }
   
    /**
     * Set the editor to use
     *
     * @param editor The editor to use when new files shall be displayed
     */
    public void setEditor(Editor editor) {
        prefs.put("editor", editor.getName());
        if (editor.isEditable()) {
            Preferences epref = prefs.node("editors")
                                     .node(editor.getName().toLowerCase());
            epref.put(NAME_LABEL, editor.getName());
            epref.put(COMMAND_LABEL, editor.getCommand());
            epref.put(CLASS_LABEL, editor.getClass().getName());
        }

        try {
            prefs.sync();
        } catch (Exception e) {
            ;
        }
    }

    public List<IndexDatabase> getAvailableIndexDatabases() {
        if (databases == null) {
            databases = new ArrayList<IndexDatabase>();

            for (int ii = 0; ii < 10; ++ii) {
                String data = prefs.get("database" + ii, null);

                if ((data != null) && (data.length() > 0)) {
                    databases.add(new IndexDatabase(data));
                }
            }
        }

        return databases;
    }
    /**
     * Get the preferred subtree path.
     *
     * @return preferred subtree path.
     */
    public String getPath() {
        return prefs.get("path", "");
    }
   
    /**
     * Set the preferred subtree path.
     *
     * @param path preferred subtree path.
     */
    public void setPath(String path) {
        prefs.put("path", path);
        try {
            prefs.sync();
        } catch (Exception e) {
            ;
        }
    }

    /**
     * Get the preferred subtree path button status
     *
     * @return preferred subtree path on?
     */
    public boolean getPathHold() {
        return prefs.getBoolean("pathhold", false);
    }
   
   /**
     * Set if the preferred subtree path hold button is ON
     *
     * @param b is preferred subtree path on?
     */

    void setPathHold(boolean b) {
        prefs.putBoolean("pathhold", b);
        try {
            prefs.sync();
        } catch (Exception e) {
            ;
        }
    }
}
