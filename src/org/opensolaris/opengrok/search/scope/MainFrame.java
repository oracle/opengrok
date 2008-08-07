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

import java.awt.Dimension;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.util.List;
import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.ActionMap;
import javax.swing.InputMap;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPopupMenu;
import javax.swing.KeyStroke;
import org.opensolaris.opengrok.configuration.Configuration;
import org.opensolaris.opengrok.configuration.RuntimeEnvironment;
import org.opensolaris.opengrok.search.Hit;
import org.opensolaris.opengrok.search.scope.editor.InternalEditor;


/**
 * A small GUI frontend to the lucene database.
 *
 * @author Trond Norbye
 */
public class MainFrame extends javax.swing.JFrame {
    static final long serialVersionUID = 1L;
    private Integer searchSession;
    private Object searchSessionLock = new Object();
    private Boolean searching;
    private Object searchingLock = new Object();
    private int nhits;
    private int shownhits;
    
    /** Creates new form MainFrame */
    public MainFrame() {
        searchSession = 0;
        searching = false;
        initComponents();
        Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
        Dimension mySize = getPreferredSize();
        setLocation(screenSize.width/2 - (mySize.width/2), screenSize.height/2 - (mySize.height/2));
        if (indexDatabaseCombo.getItemCount() > 0) {
            indexDatabaseCombo.setSelectedIndex(0);
        } else {
            searchButton.setEnabled(false);
        }
        searchButton.setEnabled(true);

        // Search should be the default button, esc should be cancel
        getRootPane().setDefaultButton(searchButton);
        registerEscapeHandler(definitionField);
        registerEscapeHandler(fileField);
        registerEscapeHandler(fullField);
        registerEscapeHandler(historyField);
        registerEscapeHandler(symbolField);
        registerEscapeHandler(symbolField);
        registerEscapeHandler(searchButton);
        
        // Create the editor list..
        List<Editor> list = Config.getInstance().getAvailableEditors();
        final JPopupMenu menu = new JPopupMenu();
        
        // I would like the InternalEditor to be the first element in the menu if it exists
        for (Editor ed : list) {
            if (ed instanceof InternalEditor) {
                JMenuItem item = new JMenuItem("View");
                menu.add(item);
                item.putClientProperty("editor", ed);
                item.addActionListener(new ActionListener() {
                    public void actionPerformed(ActionEvent e) {
                        JMenuItem item = (JMenuItem)e.getSource();
                        displayFile((Editor)item.getClientProperty("editor"));
                    }
                });
                break;
            }
        }
        
        // Add the other editors
        for (Editor ed : list) {
            if (!(ed instanceof InternalEditor) && !ed.getName().equalsIgnoreCase("Custom")) {
                JMenuItem item = new JMenuItem("Open in " + ed.getName());
                item.putClientProperty("editor", ed);
                item.addActionListener(new ActionListener() {
                    public void actionPerformed(ActionEvent e) {
                        JMenuItem item = (JMenuItem)e.getSource();
                        displayFile((Editor)item.getClientProperty("editor"));
                    }
                });
                menu.add(item);
            }
        }
        
        hitsTable.addMouseListener(new MouseAdapter() {
            public void mousePressed(MouseEvent evt) {
                if (evt.isPopupTrigger() && hitsTable.getSelectedRow() != -1) {
                    menu.show(evt.getComponent(), evt.getX(), evt.getY());
                }
            }
            public void mouseReleased(MouseEvent evt) {
                if (evt.isPopupTrigger() && hitsTable.getSelectedRow() != -1) {
                    menu.show(evt.getComponent(), evt.getX(), evt.getY());
                }
            }
        });
        setDefaultColumnWidth();
    }
    
    private void registerEscapeHandler(JComponent field) {
        Action escapeKeyAction = new AbstractAction() {
            public void actionPerformed(ActionEvent e) {
                clearButtonActionPerformed(null);
            }
        };
        
        KeyStroke escapeKeyStroke = KeyStroke.getKeyStroke((char)KeyEvent.VK_ESCAPE);
        InputMap inputMap = field.getInputMap();
        ActionMap actionMap = field.getActionMap();
        if (inputMap != null && actionMap != null) {
            inputMap.put(escapeKeyStroke, "escape");
            actionMap.put("escape", escapeKeyAction);
        }
    }
    
    private void displayFile(Editor editor) {
        int rows[] = hitsTable.getSelectedRows();
        if (rows != null) {
            HitTableModel model = (HitTableModel)hitsTable.getModel();
            for (int ii = 0; ii < rows.length; ++ii) {
                Hit hit = model.getHitAt(rows[ii]);
                
                if (hit.isBinary()) {
                    if (JOptionPane.showConfirmDialog(this, hit.getFilename() + " is a binary file. Really open?", "Really open file?", JOptionPane.YES_NO_OPTION) == JOptionPane.NO_OPTION) {
                        continue;
                    }
                }
                
                StringBuilder sb = new StringBuilder();
                sb.append(RuntimeEnvironment.getInstance().getSourceRootPath());
                sb.append(hit.getDirectory());
                sb.append(File.separator);
                sb.append(hit.getFilename());
                
                try {
                    Integer lineno = null;
                    try {
                        lineno = Integer.parseInt(hit.getLineno());
                    } catch (Exception e) {
                        ;
                    }
                    editor.displayFile(sb.toString(), lineno);
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(null, "Failed to display file\n" + ex.getMessage(), "Failed to display file", JOptionPane.ERROR_MESSAGE);
                }
            }
        }
    }
    
    
    /** This method is called from within the constructor to
     * initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is
     * always regenerated by the Form Editor.
     */
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        javax.swing.JLabel jLabel1 = new javax.swing.JLabel();
        fullField = new javax.swing.JTextField();
        javax.swing.JLabel jLabel2 = new javax.swing.JLabel();
        definitionField = new javax.swing.JTextField();
        javax.swing.JLabel jLabel3 = new javax.swing.JLabel();
        symbolField = new javax.swing.JTextField();
        javax.swing.JLabel jLabel4 = new javax.swing.JLabel();
        fileField = new javax.swing.JTextField();
        javax.swing.JLabel jLabel5 = new javax.swing.JLabel();
        historyField = new javax.swing.JTextField();
        searchButton = new javax.swing.JButton();
        javax.swing.JButton clearButton = new javax.swing.JButton();
        javax.swing.JScrollPane jScrollPane1 = new javax.swing.JScrollPane();
        hitsTable = new javax.swing.JTable();
        hitsTable.setDefaultRenderer(String.class, new AltRenderer());
        summaryLabel = new javax.swing.JLabel();
        javax.swing.JLabel jLabel6 = new javax.swing.JLabel();
        indexDatabaseCombo = new javax.swing.JComboBox();
        javax.swing.JButton browseIndexBtn = new javax.swing.JButton();
        lbar = new javax.swing.JProgressBar();
        moreButton = new javax.swing.JButton();
        holdPath = new javax.swing.JToggleButton();
        javax.swing.JMenuBar jMenuBar1 = new javax.swing.JMenuBar();
        javax.swing.JMenu jMenu1 = new javax.swing.JMenu();
        javax.swing.JMenuItem exitMenuItem = new javax.swing.JMenuItem();
        javax.swing.JMenu jMenu2 = new javax.swing.JMenu();
        javax.swing.JMenuItem editorMenuItem = new javax.swing.JMenuItem();

        setDefaultCloseOperation(javax.swing.WindowConstants.EXIT_ON_CLOSE);
        setTitle("OpenGrok");

        jLabel1.setHorizontalAlignment(javax.swing.SwingConstants.RIGHT);
        jLabel1.setText("Full Search");

        fullField.setFont(new java.awt.Font("DialogInput", 0, 12));
        fullField.setBorder(javax.swing.BorderFactory.createLineBorder(new java.awt.Color(204, 204, 204)));

        jLabel2.setHorizontalAlignment(javax.swing.SwingConstants.RIGHT);
        jLabel2.setText("Definition");

        definitionField.setFont(new java.awt.Font("DialogInput", 0, 12));
        definitionField.setBorder(javax.swing.BorderFactory.createLineBorder(new java.awt.Color(204, 204, 204)));

        jLabel3.setHorizontalAlignment(javax.swing.SwingConstants.RIGHT);
        jLabel3.setText("Symbol");

        symbolField.setFont(new java.awt.Font("DialogInput", 0, 12));
        symbolField.setBorder(javax.swing.BorderFactory.createLineBorder(new java.awt.Color(204, 204, 204)));

        jLabel4.setHorizontalAlignment(javax.swing.SwingConstants.RIGHT);
        jLabel4.setText("File Path");

        fileField.setFont(new java.awt.Font("DialogInput", 0, 12));
        fileField.setText(Config.getInstance().getPath());
        fileField.setBorder(javax.swing.BorderFactory.createLineBorder(new java.awt.Color(204, 204, 204)));

        jLabel5.setHorizontalAlignment(javax.swing.SwingConstants.RIGHT);
        jLabel5.setText("History");

        historyField.setFont(new java.awt.Font("DialogInput", 0, 12));
        historyField.setBorder(javax.swing.BorderFactory.createLineBorder(new java.awt.Color(204, 204, 204)));

        searchButton.setIcon(new javax.swing.ImageIcon(getClass().getResource("/org/opensolaris/opengrok/search/scope/q.gif"))); // NOI18N
        searchButton.setText("Search");
        searchButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                searchButtonActionPerformed(evt);
            }
        });

        clearButton.setIcon(new javax.swing.ImageIcon(getClass().getResource("/org/opensolaris/opengrok/search/scope/stp.gif"))); // NOI18N
        clearButton.setText("Cancel");
        clearButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                clearButtonActionPerformed(evt);
            }
        });

        hitsTable.setModel(new HitTableModel());
        hitsTable.setIntercellSpacing(new java.awt.Dimension(0, 0));
        hitsTable.setMinimumSize(new java.awt.Dimension(10, 0));
        hitsTable.setShowHorizontalLines(false);
        hitsTable.setShowVerticalLines(false);
        hitsTable.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseClicked(java.awt.event.MouseEvent evt) {
                hitsTableMouseClicked(evt);
            }
        });
        jScrollPane1.setViewportView(hitsTable);

        summaryLabel.setFont(new java.awt.Font("Dialog", 0, 12));
        summaryLabel.setText(" ");

        jLabel6.setHorizontalAlignment(javax.swing.SwingConstants.RIGHT);
        jLabel6.setText("Configuration");

        indexDatabaseCombo.setModel(new org.opensolaris.opengrok.search.scope.ConfigurationComboModel());
        indexDatabaseCombo.addItemListener(new java.awt.event.ItemListener() {
            public void itemStateChanged(java.awt.event.ItemEvent evt) {
                indexDatabaseComboItemStateChanged(evt);
            }
        });

        browseIndexBtn.setIcon(new javax.swing.ImageIcon(getClass().getResource("/org/opensolaris/opengrok/search/scope/do.gif"))); // NOI18N
        browseIndexBtn.setToolTipText("Open a index database");
        browseIndexBtn.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                browseIndexBtnActionPerformed(evt);
            }
        });

        lbar.setForeground(new java.awt.Color(130, 153, 175));
        lbar.setString("");
        lbar.setStringPainted(true);

        moreButton.setIcon(new javax.swing.ImageIcon(getClass().getResource("/org/opensolaris/opengrok/search/scope/more.gif"))); // NOI18N
        moreButton.setToolTipText("More");
        moreButton.setEnabled(false);
        moreButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                moreButtonActionPerformed(evt);
            }
        });

        holdPath.setFont(new java.awt.Font("Dialog", 0, 10));
        holdPath.setSelected(Config.getInstance().getPathHold());
        holdPath.setToolTipText("Remember the path");
        holdPath.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                holdPathActionPerformed(evt);
            }
        });

        jMenu1.setText("File");

        exitMenuItem.setText("Exit");
        exitMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                exitMenuItemActionPerformed(evt);
            }
        });
        jMenu1.add(exitMenuItem);

        jMenuBar1.add(jMenu1);

        jMenu2.setText("Options");

        editorMenuItem.setIcon(new javax.swing.ImageIcon(getClass().getResource("/org/opensolaris/opengrok/search/scope/p.gif"))); // NOI18N
        editorMenuItem.setText("Editor...");
        editorMenuItem.setIconTextGap(9);
        editorMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                editorMenuItemActionPerformed(evt);
            }
        });
        jMenu2.add(editorMenuItem);

        jMenuBar1.add(jMenu2);

        setJMenuBar(jMenuBar1);

        org.jdesktop.layout.GroupLayout layout = new org.jdesktop.layout.GroupLayout(getContentPane());
        getContentPane().setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(org.jdesktop.layout.GroupLayout.TRAILING, layout.createSequentialGroup()
                .addContainerGap()
                .add(layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING, false)
                    .add(org.jdesktop.layout.GroupLayout.TRAILING, jLabel1, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .add(jLabel6, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .add(jLabel2, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .add(jLabel3, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .add(jLabel4, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .add(jLabel5, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                .add(layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
                    .add(org.jdesktop.layout.GroupLayout.TRAILING, layout.createSequentialGroup()
                        .add(indexDatabaseCombo, 0, 516, Short.MAX_VALUE)
                        .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                        .add(browseIndexBtn, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 32, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED))
                    .add(layout.createSequentialGroup()
                        .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                        .add(searchButton)
                        .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                        .add(clearButton)
                        .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED, 199, Short.MAX_VALUE)
                        .add(lbar, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 167, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                        .add(moreButton, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 29, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE))
                    .add(historyField, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, 554, Short.MAX_VALUE)
                    .add(definitionField, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, 554, Short.MAX_VALUE)
                    .add(symbolField, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, 554, Short.MAX_VALUE)
                    .add(org.jdesktop.layout.GroupLayout.TRAILING, layout.createSequentialGroup()
                        .add(fileField, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, 522, Short.MAX_VALUE)
                        .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                        .add(holdPath, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 26, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE))
                    .add(fullField, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, 554, Short.MAX_VALUE))
                .addContainerGap())
            .add(layout.createSequentialGroup()
                .addContainerGap()
                .add(summaryLabel, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, 629, Short.MAX_VALUE)
                .addContainerGap())
            .add(jScrollPane1, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, 653, Short.MAX_VALUE)
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(layout.createSequentialGroup()
                .addContainerGap()
                .add(layout.createParallelGroup(org.jdesktop.layout.GroupLayout.BASELINE)
                    .add(jLabel6)
                    .add(browseIndexBtn)
                    .add(indexDatabaseCombo, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                .add(layout.createParallelGroup(org.jdesktop.layout.GroupLayout.BASELINE)
                    .add(jLabel1)
                    .add(fullField, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE))
                .add(2, 2, 2)
                .add(layout.createParallelGroup(org.jdesktop.layout.GroupLayout.BASELINE)
                    .add(jLabel2)
                    .add(definitionField, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE))
                .add(2, 2, 2)
                .add(layout.createParallelGroup(org.jdesktop.layout.GroupLayout.BASELINE)
                    .add(jLabel3)
                    .add(symbolField, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE))
                .add(2, 2, 2)
                .add(layout.createParallelGroup(org.jdesktop.layout.GroupLayout.BASELINE)
                    .add(jLabel4)
                    .add(holdPath, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 18, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                    .add(fileField, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE))
                .add(2, 2, 2)
                .add(layout.createParallelGroup(org.jdesktop.layout.GroupLayout.BASELINE)
                    .add(jLabel5)
                    .add(historyField, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                .add(layout.createParallelGroup(org.jdesktop.layout.GroupLayout.BASELINE)
                    .add(clearButton, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 28, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                    .add(searchButton, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 28, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                    .add(moreButton, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 27, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                    .add(lbar, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 27, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                .add(jScrollPane1, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, 500, Short.MAX_VALUE)
                .add(0, 0, 0)
                .add(summaryLabel))
        );

        pack();
    }// </editor-fold>//GEN-END:initComponents
    
    private void moreButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_moreButtonActionPerformed
        if(!searching && shownhits < nhits) {
            searching = true;
            searchButton.setEnabled(false);
            moreButton.setEnabled(false);
        }
        Thread t = new Thread() {
            public void run() {
                long start = System.currentTimeMillis();
                int mySession = searchSession;
                int inc = 7;
                int i = shownhits;
                int e = shownhits;
                long fstart;
                HitTableModel m = (HitTableModel)hitsTable.getModel();
                for(; searching && i < nhits && (fstart = System.currentTimeMillis()) - start < 5000; i+=inc) {
                    e = i+inc;
                    if(e > nhits) {
                        e = nhits;
                    }
                    lbar.setValue((e*100)/nhits+1);
                    lbar.setString(i + "/" + nhits);
                    if(!m.more(i, e, searchSession)) {
                        summaryLabel.setText(" ");
                        lbar.setValue(0);
                        lbar.setString("");
                        nhits = 0;
                        searchButton.setEnabled(true);
                        moreButton.setEnabled(false);
                        return;
                    }
                    lbar.setString(e + "/" + nhits);
                    if(System.currentTimeMillis()-fstart < 300) {
                        inc = inc + inc/2;
                    }
                }
                long stop = System.currentTimeMillis();
                if(nhits == 0) {
                    lbar.setString("0 found!");
                    shownhits = 0;
                } else {
                    summaryLabel.setText("Took " + ((stop - start)/1000) + " sec");
                    shownhits = e;
                    if (i >= nhits) {
                        lbar.setValue(100);
                        lbar.setString(nhits + " found");
                        moreButton.setEnabled(false);
                    } else {
                        lbar.setString("First " + i + "/" + nhits);
                        moreButton.setEnabled(true);
                    }
                }
                synchronized(searchingLock) {
                    searching = false;
                    searchButton.setEnabled(true);
                }
            }
        };
        t.start();
        
    }//GEN-LAST:event_moreButtonActionPerformed
    
    private void holdPathActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_holdPathActionPerformed
        if(holdPath.isSelected()) {
            Config.getInstance().setPath(fileField.getText().trim());
        }
        Config.getInstance().setPathHold(holdPath.isSelected());
    }//GEN-LAST:event_holdPathActionPerformed
    
    private void indexDatabaseComboItemStateChanged(java.awt.event.ItemEvent evt) {//GEN-FIRST:event_indexDatabaseComboItemStateChanged
        File file = (File) evt.getItem();
        try {
            Configuration config = Configuration.read(file);
            RuntimeEnvironment.getInstance().setConfiguration(config);
            searchButton.setEnabled(true);
        } catch (Exception exp) {
            JOptionPane.showMessageDialog(this, "Failed to read configuration: " + exp.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            searchButton.setEnabled(false);
        }
    }//GEN-LAST:event_indexDatabaseComboItemStateChanged
    
    private void browseIndexBtnActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_browseIndexBtnActionPerformed
        JFileChooser jfc = new JFileChooser();
        if (jfc.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            try {
                Configuration config = Configuration.read(jfc.getSelectedFile());
                Config.getInstance().getRecentConfigurations().add(jfc.getSelectedFile());
                RuntimeEnvironment.getInstance().setConfiguration(config);
                ((ConfigurationComboModel)indexDatabaseCombo.getModel()).refresh();
                indexDatabaseCombo.getModel().setSelectedItem(jfc.getSelectedFile());
                Config.getInstance().setCurrentConfiguration(jfc.getSelectedFile());
            } catch (Exception exp) {
                JOptionPane.showMessageDialog(this, "Failed to read configuration: " + exp.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }//GEN-LAST:event_browseIndexBtnActionPerformed
    
        
    private void editorMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_editorMenuItemActionPerformed
        EditorConfigPanel pnl = new EditorConfigPanel();
        pnl.setEditor(Config.getInstance().getEditor());
        if (JOptionPane.showConfirmDialog(this, pnl, "Editor settings", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE, null) == JOptionPane.OK_OPTION) {
            pnl.updateEditor();
            Config.getInstance().setEditor(pnl.getEditor());
        }
    }//GEN-LAST:event_editorMenuItemActionPerformed
    
    private void exitMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_exitMenuItemActionPerformed
        System.exit(0);
    }//GEN-LAST:event_exitMenuItemActionPerformed
    
    private void updateSelectedFile() {
        int rows[] = hitsTable.getSelectedRows();
        if(rows != null && rows.length > 0) {
            summaryLabel.setText(
                    ((HitTableModel)hitsTable.getModel()).getHitAt(rows[0]).getPath());
        }
    }
    private void hitsTableMouseClicked(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_hitsTableMouseClicked
        if (evt.getClickCount() == 2) {
            displayFile(Config.getInstance().getEditor());
        } else if (evt.getClickCount() == 1) {
            updateSelectedFile();
        }
    }//GEN-LAST:event_hitsTableMouseClicked
    
    private void searchButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_searchButtonActionPerformed
        synchronized(searchingLock) {
            searching = true;
        };
        searchButton.setEnabled(false);
        moreButton.setEnabled(false);
        SearchEngine se = new SearchEngine();
        se.setDefinition(definitionField.getText().trim());
        se.setFile(fileField.getText().trim());
        se.setFreetext(fullField.getText().trim());
        se.setHistory(historyField.getText().trim());
        se.setSymbol(symbolField.getText().trim());
        synchronized(searchSessionLock) {
            searchSession++;
        };
        final HitTableModel m = (HitTableModel)hitsTable.getModel();
        m.reset();
        m.setSearchEngine(se);
        Thread t = new Thread() {
            public void run() {
                long start = System.currentTimeMillis();
                int mySession;
                nhits = m.search(mySession = searchSession);
                setDefaultColumnWidth();
                int inc = 7;
                int i = 0;
                int e = 0;
                long fstart;
                while(searching && i < nhits && (fstart = System.currentTimeMillis()) - start < 5000) {
                    i = e;
                    e = i+inc;
                    if(e > nhits) {
                        e = nhits;
                    }
                    lbar.setValue((e*100)/nhits+1);
                    lbar.setString(i + "/" + nhits);
                    if(!m.more(i, e, searchSession)) {
                        summaryLabel.setText(" ");
                        lbar.setValue(0);
                        lbar.setString("");
                        nhits = 0;
                        searchButton.setEnabled(true);
                        moreButton.setEnabled(false);
                        return;
                    }
                    lbar.setString(e + "/" + nhits);
                    if(System.currentTimeMillis()-fstart < 300) {
                        inc = inc + inc/2;
                    }
                }
                long stop = System.currentTimeMillis();
                if(nhits == 0) {
                    lbar.setString("0 found!");
                    shownhits = 0;
                } else {
                    summaryLabel.setText("Took " + ((stop - start)/1000) + " sec");
                    shownhits = e;
                    if (i >= nhits) {
                        lbar.setValue(100);
                        lbar.setString(nhits + " found");
                        moreButton.setEnabled(false);
                    } else {
                        lbar.setString("First " + i + "/" + nhits);
                        moreButton.setEnabled(true);
                    }
                }
                synchronized(searchingLock) {
                    searching = false;
                    searchButton.setEnabled(true);
                }
            }
        };
        t.start();
    }//GEN-LAST:event_searchButtonActionPerformed
    
    private void clearButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_clearButtonActionPerformed
        synchronized(searchingLock) {
            if(searching) {
                searching = false;
                return;
            }
        }
        synchronized(searchSessionLock) {
            searchSession++;
        };
        definitionField.setText("");
        if(!holdPath.isSelected())  fileField.setText("");
        fullField.setText("");
        historyField.setText("");
        symbolField.setText("");
        ((HitTableModel)hitsTable.getModel()).reset();
        summaryLabel.setText(" ");
        lbar.setValue(0);
        lbar.setString("");
    }//GEN-LAST:event_clearButtonActionPerformed
    
    private void setDefaultColumnWidth() {
        int width = hitsTable.getWidth();
        if (hitsTable.getColumnCount() == 2) {
            hitsTable.getColumnModel().getColumn(0).setPreferredWidth((int)(width * 0.5));
            hitsTable.getColumnModel().getColumn(1).setPreferredWidth((int)(width * 0.5));
        } else {
            hitsTable.getColumnModel().getColumn(0).setPreferredWidth((int)(width * 0.1));
            hitsTable.getColumnModel().getColumn(1).setPreferredWidth((int)(width * 0.15));
            hitsTable.getColumnModel().getColumn(2).setPreferredWidth((int)(width * 0.65));
            hitsTable.getColumnModel().getColumn(3).setPreferredWidth((int)(width * 0.1));
        }
    }
    
    /**
     * Program entry point for the graphical interface version of the seach program
     * @param args the command line arguments
     */
    public static void main(String[] args) {
        java.awt.EventQueue.invokeLater(new Runnable() {
            public void run() {
                new MainFrame().setVisible(true);
            }
        });
    }
    
    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JTextField definitionField;
    private javax.swing.JTextField fileField;
    private javax.swing.JTextField fullField;
    private javax.swing.JTextField historyField;
    private javax.swing.JTable hitsTable;
    javax.swing.JToggleButton holdPath;
    private javax.swing.JComboBox indexDatabaseCombo;
    javax.swing.JProgressBar lbar;
    javax.swing.JButton moreButton;
    private javax.swing.JButton searchButton;
    private javax.swing.JLabel summaryLabel;
    private javax.swing.JTextField symbolField;
    // End of variables declaration//GEN-END:variables
}
