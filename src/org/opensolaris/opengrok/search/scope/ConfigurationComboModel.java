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

package org.opensolaris.opengrok.search.scope;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import javax.swing.ComboBoxModel;
import javax.swing.event.ListDataEvent;
import javax.swing.event.ListDataListener;

/**
 *
 * @author Trond Norbye
 */
public class ConfigurationComboModel implements ComboBoxModel {
    private List<ListDataListener> clients;
    private Object selected;
    private List<File> data;
    
    /** Creates a new instance of ConfigurationComboModel */
    public ConfigurationComboModel() {
        clients = new ArrayList<ListDataListener>();
        selected = null;
        data = Config.getInstance().getRecentConfigurations();
    }

    public void refresh() {
        ListDataEvent evt = new ListDataEvent(this, ListDataEvent.CONTENTS_CHANGED, 0, Integer.MAX_VALUE);
        for (ListDataListener l : clients) {
            l.contentsChanged(evt);
        }
    }
    
    /**
     * 
     * Set the selected item. The implementation of this  method should notify 
     * all registered <code>ListDataListener</code>s that the contents 
     * have changed. 
     * 
     * 
     * @param anItem the list object to select or <code>null</code> 
     *        to clear the selection
     */
    public void setSelectedItem(Object anItem) {
        selected = anItem;
    }

    /**
     * Returns the value at the specified index.  
     * @param index the requested index
     * @return the value at <code>index</code>
     */
    public Object getElementAt(int index) {
        Object ret = null;
        if (index >=0 && index < data.size()) {
            ret = data.get(index);
        }
        return ret;
    }

    /**
     * Removes a listener from the list that's notified each time a 
     * change to the data model occurs.
     * 
     * @param l the <code>ListDataListener</code> to be removed
     */
    public void removeListDataListener(ListDataListener l) {
        clients.remove(l);
    }

    /**
     * Adds a listener to the list that's notified each time a change
     * to the data model occurs.
     * 
     * @param l the <code>ListDataListener</code> to be added
     */
    public void addListDataListener(ListDataListener l) {
        clients.add(l);
    }

    /**
     * 
     * Returns the length of the list.
     * @return the length of the list
     */
    public int getSize() {
        return data.size();
    }

    /**
     * 
     * Returns the selected item 
     * @return The selected item or <code>null</code> if there is no selection
     */
    public Object getSelectedItem() {
        return selected;
    }
    
}
