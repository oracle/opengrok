package org.opensolaris.opengrok.egrok.preferences;

import org.eclipse.jface.preference.BooleanFieldEditor;
import org.eclipse.jface.preference.FieldEditorPreferencePage;
import org.eclipse.jface.preference.StringFieldEditor;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.IWorkbenchPreferencePage;
import org.opensolaris.opengrok.egrok.Activator;

public class EGrokPreferencePage extends FieldEditorPreferencePage implements
    IWorkbenchPreferencePage {

  public static final String BASE_URL = "baseUrl";
  public static final String USERNAME = "userName";
  public static final String PASSWORD = "password";
  public static final String FILTER_REGEX = "filterRegex";
  public static final String WORKSPACE_MATCHES = "workspaceMatches";

  @Override
  protected void createFieldEditors() {
    addField(new StringFieldEditor(BASE_URL, "{OpenGrok base URL",
        getFieldEditorParent()));

    addField(new StringFieldEditor(USERNAME,
        "Username for Basic Authentication (optional)", getFieldEditorParent()));

    addField(new StringFieldEditor(PASSWORD,
        "Password for Basic Authentication (optional)", getFieldEditorParent()) {
      @Override
      protected void doFillIntoGrid(Composite parent, int numColumns) {
        super.doFillIntoGrid(parent, numColumns);
        getTextControl().setEchoChar('*');
      }
    });

    addField(new BooleanFieldEditor(FILTER_REGEX,
        "Filter with Regular Expressions", getFieldEditorParent()));

    addField(new BooleanFieldEditor(WORKSPACE_MATCHES,
        "Corelate matches with files in workspace (experimental)",
        getFieldEditorParent()));
  }

  @Override
  public void init(IWorkbench workbench) {
    setPreferenceStore(Activator.getDefault().getPreferenceStore());
  }

}
