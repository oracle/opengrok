package org.opensolaris.opengrok.egrok.ui;

import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.Font;
import org.eclipse.swt.graphics.FontData;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Text;

public class TextUtils {
  public static void setToDefault(Text text, String defaultText) {
    text.setForeground(new Color(null, 0x8b, 0x89, 0x98));
    text.setText(defaultText);
    FontData fontData = text.getFont().getFontData()[0];
    Font font = new Font(Display.getCurrent(), new FontData(fontData.getName(),
        fontData.getHeight(), SWT.ITALIC));
    text.setFont(font);
  }

  public static void makeEditable(Text text) {
    text.setForeground(new Color(null, 0x00, 0x00, 0x00));
    text.setText("");
    FontData fontData = text.getFont().getFontData()[0];
    Font font = new Font(Display.getCurrent(), new FontData(fontData.getName(),
        fontData.getHeight(), SWT.NORMAL));
    text.setFont(font);

  }

}
