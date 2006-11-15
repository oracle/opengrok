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
 * Copyright 2006 Sun Microsystems, Inc.  All rights reserved.
 * Use is subject to license terms.
 */

/*
 * ident	"@(#)AltRenderer.java 1.1     06/02/22 SMI"
 */

package org.opensolaris.opengrok.search.scope;

/**
 *
 * @author Chandan, 2006
 */

import java.awt.Color;
import java.awt.Graphics;
import java.util.*;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.*;
import javax.swing.border.*;
import javax.swing.*;
import java.awt.Component;

public class AltRenderer  extends DefaultTableCellRenderer{
    boolean show;
    boolean alt;
    boolean selected;
    boolean dirsep;
    Color grey = new Color(0xffe8e8e8);
    Color dgrey = new Color(0xff7a8a99);
    Color sel = new Color(0xffbdd0e0);
    public AltRenderer() {
        super();
        setFont(new java.awt.Font("Dialog", 0, 12));
    }
    public Component getTableCellRendererComponent(JTable table,
            Object value,
            boolean isSelected,
            boolean hasFocus,
            int row,
            int column) {
        selected = isSelected;
        if(value != null) {
            setText(value.toString());
            show = true;
            alt = ((HitTableModel)table.getModel()).getAlt(row);
            dirsep =  ((HitTableModel)table.getModel()).getDirsep(row);
        } else {
            setText(null);
            show = false;
        }
        return this;
    }
    public void paint(Graphics g) {
        if(selected) {
            g.setColor(sel);
            g.fillRect(1, 0, getWidth(), getHeight());
        } else if(show) {
            if(alt) {
                g.setColor(grey);
                g.fillRect(1, 0, getWidth(), getHeight());
            } else {
                g.setColor(Color.white);
                g.fillRect(1, 0, getWidth(), getHeight());
            }
        }
        if(dirsep) {
                g.setColor(dgrey);
                g.drawLine(0, 0, getWidth(), 0);
        }
        super.paint(g);
    }
}
