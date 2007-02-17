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
 * ident	"@(#)HitTableModel.java 1.1     06/02/22 SMI"
 */

package org.opensolaris.opengrok.search.scope;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import javax.swing.JProgressBar;
import javax.swing.JTable;
import javax.swing.event.TableModelEvent;
import javax.swing.event.TableModelListener;
import javax.swing.table.TableModel;
import org.apache.lucene.search.Hits;
import org.opensolaris.opengrok.search.Hit;


/**
 * The HitTableModel is a table-model that gives easy access for the hit data.
 *
 * @author Trond Norbye
 */
public final class HitTableModel implements TableModel {
    private static final String DirectoryLabel = "Directory";
    private static final String FileNameLabel = "Filename";
    private static final String HitLabel = "Hit";
    private static final String TagLabel = "Tag";
    private static final String ErrorLabel = "Error";
    private static final String BinaryLabel = "Binary file";
    private static final int COLUMNS = 4;
    private int columnCount;
    private List<TableModelListener> listeners;
    private List<Hit> hitlist;
    private SearchEngine se;
    private int session;
    public void setMaxColumns(int columns) {
        if (columns < 0 || columns > COLUMNS) {
            columns = COLUMNS;
        }
        columnCount = columns;
        
        TableModelEvent e = new TableModelEvent(this, TableModelEvent.HEADER_ROW);
        Iterator<TableModelListener> iter = listeners.iterator();
        
        while (iter.hasNext()) {
            TableModelListener l = iter.next();
            l.tableChanged(e);
        }
    }
    
    /**
     * Creates a new instance of HitTableModel
     */
    public HitTableModel() {
        listeners = new ArrayList<TableModelListener>();
        hitlist = new ArrayList<Hit>(100);
        columnCount = COLUMNS;
    }
    
    /**
     * Remove a client
     * @param l the client to remove
     */
    public void removeTableModelListener(TableModelListener l) {
        listeners.remove(l);
    }
    
    /**
     * Add a client
     * @param l The client to add
     */
    public void addTableModelListener(TableModelListener l) {
        listeners.add(l);
    }
    
    /**
     * Set a value at a given position. This is not possible.
     * @param aValue Not used
     * @param rowIndex Not used
     * @param columnIndex Not used
     */
    public void setValueAt(Object aValue, int rowIndex, int columnIndex) {
        ;
    }
    
    public Hit getHitAt(int row) {
        if (row >= 0 && row < hitlist.size()) {
            return hitlist.get(row);
        }
        return null;
    }
    
    /**
     * Get the textual name of a given column
     * @param columnIndex The column to get the name for
     * @return The textual name for the coulumn, or null if this is an illegal column index
     */
    public String getColumnName(int columnIndex) {
        String ret;
        
        switch (columnIndex) {
            case 0:
                ret = DirectoryLabel;
                break;
                
            case 1:
                ret = FileNameLabel;
                
                break;
                
            case 2:
                ret = HitLabel;
                
                break;
                
            case 3:
                ret = TagLabel;
                break;
                
                
            default:
                ret = ErrorLabel;
                
                break;
        }
        
        return ret;
    }
    
    /**
     * Get the type of class stored in a column
     * @param columnIndex the column to query
     * @return String
     */
    public Class getColumnClass(int columnIndex) {
        return String.class;
    }
    
    /**
     * Is this cell editable or not. None of the cells may be edited
     * @param rowIndex Not used
     * @param columnIndex Not used
     * @return false
     */
    public boolean isCellEditable(int rowIndex, int columnIndex) {
        return false;
    }
    
    /**
     * Get the value stored in a specific location
     * @param rowIndex The row
     * @param columnIndex The column
     * @return The value stored at (columnIndex, rowIndex) or null if an invalid index is specified
     */
    public Object getValueAt(int rowIndex, int columnIndex) {
        String ret;
        
        if ((columnIndex < 0) || (columnIndex >= COLUMNS) || (rowIndex < 0) ||
                (hitlist == null) || (hitlist.size() < rowIndex)) {
            ret = null;
        } else {
            switch (columnIndex) {
                case 0:
                    ret = hitlist.get(rowIndex).getDirectory();
                    if (rowIndex > 0 && hitlist.get(rowIndex - 1).getDirectory().equals(hitlist.get(rowIndex).getDirectory())) {
                        ret = "";
                    }
                    break;
                    
                case 1:
                    ret = hitlist.get(rowIndex).getFilename();
                    if (rowIndex > 0 && hitlist.get(rowIndex - 1).getPath().equals(hitlist.get(rowIndex).getPath())) {
                        ret = "";
                    }
                    break;
                    
                case 2:
                {
                    String no = hitlist.get(rowIndex).getLineno();
                    if (hitlist.get(rowIndex).isBinary() || no == null || no.length() == 0) {
                        ret = "<html>" + hitlist.get(rowIndex).getLine();
                    } else {
                        ret = "<html>" + hitlist.get(rowIndex).getLineno() + ": " + hitlist.get(rowIndex).getLine();
                    }
                }
                break;
                
                case 3:
                    ret = hitlist.get(rowIndex).getTag();
                    if (ret == null) {
                        ret = "";
                    }
                    
                    break;
                    
                default:
                    ret = ErrorLabel;
            }
        }
        
        return ret;
    }
    
    /**
     * Get the number of rows of data
     * @return The number of available rows with data
     */
    public int getRowCount() {
        int ret;
        
        if (hitlist == null) {
            ret = 0;
        } else {
            ret = hitlist.size();
        }
        
        return ret;
    }
    
    /**
     * Get the number of columns of data
     * @return The number of columns of data
     */
    public int getColumnCount() {
        return columnCount;
    }
    
    /**
     * Set the searchEngine this TableModel should use
     * @param se a <code>SearchEngine</code> object
     */
    public void setSearchEngine(SearchEngine se) {
        this.se = se;
    }
    
    public int search(int session) {
        this.session = session;
        int nhits = se.search();
        if (se.onlyFilnameSearch()) {
            setMaxColumns(2);
        } else {
            setMaxColumns(-1);
        }
        return nhits;
    }
    
    public boolean more(int i, int j, int session) {
        //System.err.println("start = " + i + " end " + j);
        if(session != this.session) {
            reset();
            return false;
        }
        se.more(i, j, hitlist);
        if(session != this.session) {
            reset();
            return false;
        }
        TableModelEvent e = new TableModelEvent(this);
        Iterator<TableModelListener> iter = listeners.iterator();
        while (iter.hasNext()) {
            TableModelListener l = iter.next();
            l.tableChanged(e);
        }
        return true;
    }
    
    void reset() {
        this.session = -1;
        hitlist.clear();
        TableModelEvent e = new TableModelEvent(this);
        Iterator<TableModelListener> iter = listeners.iterator();
        while (iter.hasNext()) {
            TableModelListener l = iter.next();
            l.tableChanged(e);
        }
    }
    
    public boolean getAlt(int rowindex) {
        try{
            return hitlist.get(rowindex).getAlt();
        } catch(Exception e) {
        }
        return false;
    }
    
    public boolean getDirsep(int rowindex) {
        try{
            return !hitlist.get(rowindex).getDirectory().equals(hitlist.get(rowindex-1).getDirectory());
        } catch(Exception e) {
        }
        return false;
    }
}
