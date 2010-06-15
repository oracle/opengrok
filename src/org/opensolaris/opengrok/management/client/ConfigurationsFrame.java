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

package org.opensolaris.opengrok.management.client;

import java.awt.Dimension;
import java.awt.Toolkit;
import java.io.IOException;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.management.Attribute;
import javax.management.AttributeNotFoundException;
import javax.management.InstanceNotFoundException;
import javax.management.InvalidAttributeValueException;
import javax.management.MBeanException;
import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;
import javax.management.ReflectionException;
import javax.swing.table.DefaultTableModel;
import org.opensolaris.opengrok.OpenGrokLogger;
import org.opensolaris.opengrok.configuration.Configuration;
import org.opensolaris.opengrok.configuration.Project;
import org.opensolaris.opengrok.history.RepositoryInfo;
import org.opensolaris.opengrok.index.IgnoredNames;

/**
 *
 * @author Jan Berg
 */
// Many of the generated fields cause this warning
@SuppressWarnings("PMD.SingularField")
public class ConfigurationsFrame extends javax.swing.JFrame {

    AgentConnection con;
    private static final Logger logger = OpenGrokLogger.getLogger();
    private final static Level[] levels = new Level[]{Level.ALL,
    Level.CONFIG, Level.FINE,Level.FINER,Level.FINEST,Level.INFO,Level.OFF,
    Level.SEVERE, Level.WARNING};
    private ObjectName managementObjectName;
    private ObjectName configObjectName;
  
    /* config variables from agent */
    private Level consoleLevel = Level.OFF;
    private Level fileLevel = Level.OFF;
    private String filePath = "";
    private String publishURL = "";
    private Configuration config = null;
    private String configurationFile = null;
    private final static String[] projectTableHeaders = new String[]{"Id","Description","Path","Default"};
    private final static String[] repositoryTableHeaders = new String[]{"Directory","Type","SCM Working"};
    private final static String[] ignoredNamesHeaders = new String[]{"IgnorePatterns"};

    /** Creates new form ConfigurationsFrame
     * @param ac AgentConnection connection to the Opengrok agent.
     * @throws IOException if objectname or configs could not be created
     */
    public ConfigurationsFrame(AgentConnection ac) throws IOException {
        con = ac;
        try {
            managementObjectName = new ObjectName("OGA:name=Management");
            configObjectName = new ObjectName("OGA:name=JMXConfiguration");
        } catch (MalformedObjectNameException ex) {
            logger.log(Level.SEVERE, "MalformedObjectName", ex);
            throw new IOException("Malformedname " + ex);
        }
        initComponents();
        createLogLevelCombos();
        getConfigsFromAgent();

        Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
        Dimension mySize = getPreferredSize();
        setLocation(screenSize.width / 2 - (mySize.width / 2), screenSize.height / 2 - (mySize.height / 2));
    }

    private void showError(Exception exc) {
        ShowErrorForm sf = new ShowErrorForm(this, true, exc.getMessage());
        sf.setVisible(true);
    }

    private void createLogLevelCombos() {
        this.consoleLevelCombo.removeAllItems();
        this.fileLevelCombo.removeAllItems();
        for (int i=0;i<levels.length;i++) {
            this.consoleLevelCombo.addItem(levels[i]);
            this.fileLevelCombo.addItem(levels[i]);
        }
    }

    private void getConfigsFromAgent() {
        if (con != null && con.isConnected()) {
            try {
               /* Get Management MBean attributes */
               consoleLevel = (Level)con.getMBeanServerConnection().getAttribute(this.managementObjectName, "ConsoleLogLevel");
               this.consoleLevelCombo.setSelectedItem(consoleLevel);
               fileLevel = (Level)con.getMBeanServerConnection().getAttribute(this.managementObjectName, "FileLogLevel");
               this.fileLevelCombo.setSelectedItem(fileLevel);
               filePath = (String)con.getMBeanServerConnection().getAttribute(this.managementObjectName, "FileLogPath");
               this.logFilePathField.setText(filePath);
               publishURL = (String)con.getMBeanServerConnection().getAttribute(this.managementObjectName, "PublishServerURL");
               if (publishURL == null) {
                   publishURL = "";
               }
               this.publishHostField.setText(publishURL);
               configurationFile = (String)con.getMBeanServerConnection().getAttribute(this.managementObjectName, "ConfigurationFile");
               this.configFileField.setText(configurationFile);
               
               /* Get Configuration MBean attributes */
               String xmlconfig = (String)con.getMBeanServerConnection().getAttribute(this.configObjectName, "Configuration");
               config = Configuration.makeXMLStringAsConfiguration(xmlconfig);
               this.updateConfigFieldsFromConfig();
            } catch (MBeanException ex) {
                logger.log(Level.SEVERE, "", ex);
            } catch (AttributeNotFoundException ex) {
                Logger.getLogger(ConfigurationsFrame.class.getName()).log(Level.SEVERE, null, ex);
            } catch (InstanceNotFoundException ex) {
                Logger.getLogger(ConfigurationsFrame.class.getName()).log(Level.SEVERE, null, ex);
            } catch (ReflectionException ex) {
                Logger.getLogger(ConfigurationsFrame.class.getName()).log(Level.SEVERE, null, ex);
            } catch (IOException ex) {
                Logger.getLogger(ConfigurationsFrame.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
    }
    
    private void updateConfigFieldsFromConfig() {
        this.dataRootField.setText(config.getDataRoot());
        this.sourceRootField.setText(config.getSourceRoot());
        this.indexVersionedFilesOnlyCB.setSelected(config.isIndexVersionedFilesOnly());
        this.luceneLockingCB.setSelected(config.isUsingLuceneLocking());
        this.remoteSCMSupportedCB.setSelected(config.isRemoteScmSupported());
        this.bugPageField.setText(config.getBugPage());
        this.bugPatternField.setText(config.getBugPattern());
        this.reviewPageField.setText(config.getReviewPage());
        this.reviewPatternField.setText(config.getReviewPattern());
        this.userPageField.setText(config.getUserPage());
        this.webAppLAFField.setText(config.getWebappLAF());
        this.compressXRefsCB.setSelected(config.isCompressXref());
        this.cTagsField.setText(config.getCtags());
        this.generateHtmlCB.setSelected(config.isGenerateHtml());
        this.historyCacheCB.setSelected(config.isHistoryCache());
        this.indexWordLimitField.setText(Integer.toString(config.getIndexWordLimit()));
        this.allowLeadingWildCardsCB.setSelected(config.isAllowLeadingWildcard());
        this.urlPrefixField.setText(config.getUrlPrefix());
        this.historyReaderTimelimitField.setText(Integer.toString(config.getHistoryCacheTime()));

        List<Project> projects =  config.getProjects();
        DefaultTableModel model = new DefaultTableModel(projectTableHeaders,projects.size());
        
        Project defaultproj = config.getDefaultProject();
        int row = 0;
        for (Project proj:projects) {
           model.setValueAt(proj.getId(), row, 0);
           model.setValueAt(proj.getDescription(), row, 1);
           model.setValueAt(proj.getPath(), row, 2);
           String defaultval = "";
           if (proj.equals(defaultproj)) {
               defaultval = "Yes";
           } else {
               defaultval = "";
           }
           model.setValueAt(defaultval, row, 3);
           row++;
        }
        this.projectsTable.setModel(model);

        IgnoredNames in = config.getIgnoredNames();
        List<String> ignoredpatternlist = in.getItems();
        DefaultTableModel ignoredModel = new DefaultTableModel(ignoredNamesHeaders,ignoredpatternlist.size());
        row = 0;
        for (String item: ignoredpatternlist) {
            ignoredModel.setValueAt(item, row, 0);
            row++;
        }
        this.ignoredNamesTable.setModel(ignoredModel);

        List<RepositoryInfo> repos = config.getRepositories();
        DefaultTableModel reposModel = new DefaultTableModel(repositoryTableHeaders,repos.size());
        row = 0;
        for (RepositoryInfo info : repos) {
            reposModel.setValueAt(info.getDirectoryName(), row, 0);
            reposModel.setValueAt(info.getType(), row, 1);
            reposModel.setValueAt(Boolean.valueOf(info.isWorking()), row, 2);
            row++;
        }
        this.repositoryTable.setModel(reposModel);
        /*
         this is not documented:
       config.setQuickContextScan(rootPaneCheckingEnabled);
         * is this necessary with new logging?:
       config.setVerbose(rootPaneCheckingEnabled);
       */
        
    }

    /** This method is called from within the constructor to
     * initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is
     * always regenerated by the Form Editor.
     */
    @SuppressWarnings({"unchecked", "PMD.AvoidDuplicateLiterals"})
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        jTabPane = new javax.swing.JTabbedPane();
        genSettingsPanel = new javax.swing.JPanel();
        jLabel1 = new javax.swing.JLabel();
        jLabel2 = new javax.swing.JLabel();
        consoleLevelCombo = new javax.swing.JComboBox();
        jLabel3 = new javax.swing.JLabel();
        fileLevelCombo = new javax.swing.JComboBox();
        jLabel4 = new javax.swing.JLabel();
        logFilePathField = new javax.swing.JTextField();
        jLabel5 = new javax.swing.JLabel();
        jLabel6 = new javax.swing.JLabel();
        publishHostField = new javax.swing.JTextField();
        jLabel19 = new javax.swing.JLabel();
        jLabel20 = new javax.swing.JLabel();
        configFileField = new javax.swing.JTextField();
        indexerPanel = new javax.swing.JPanel();
        jLabel7 = new javax.swing.JLabel();
        dataRootField = new javax.swing.JTextField();
        jLabel8 = new javax.swing.JLabel();
        sourceRootField = new javax.swing.JTextField();
        luceneLockingCB = new javax.swing.JCheckBox();
        optimizedDatabaseCB = new javax.swing.JCheckBox();
        remoteSCMSupportedCB = new javax.swing.JCheckBox();
        indexVersionedFilesOnlyCB = new javax.swing.JCheckBox();
        jLabel16 = new javax.swing.JLabel();
        cTagsField = new javax.swing.JTextField();
        historyCacheCB = new javax.swing.JCheckBox();
        jLabel17 = new javax.swing.JLabel();
        historyReaderTimelimitField = new javax.swing.JTextField();
        generateHtmlCB = new javax.swing.JCheckBox();
        compressXRefsCB = new javax.swing.JCheckBox();
        jLabel18 = new javax.swing.JLabel();
        indexWordLimitField = new javax.swing.JTextField();
        projectsPanel = new javax.swing.JPanel();
        jScrollPane1 = new javax.swing.JScrollPane();
        projectsTable = new javax.swing.JTable();
        repositoryPanel = new javax.swing.JPanel();
        jScrollPane2 = new javax.swing.JScrollPane();
        repositoryTable = new javax.swing.JTable();
        historyListingPanel = new javax.swing.JPanel();
        jLabel9 = new javax.swing.JLabel();
        bugPageField = new javax.swing.JTextField();
        jLabel10 = new javax.swing.JLabel();
        bugPatternField = new javax.swing.JTextField();
        jLabel11 = new javax.swing.JLabel();
        reviewPageField = new javax.swing.JTextField();
        jLabel12 = new javax.swing.JLabel();
        reviewPatternField = new javax.swing.JTextField();
        jLabel13 = new javax.swing.JLabel();
        userPageField = new javax.swing.JTextField();
        jLabel14 = new javax.swing.JLabel();
        webAppLAFField = new javax.swing.JTextField();
        jLabel15 = new javax.swing.JLabel();
        urlPrefixField = new javax.swing.JTextField();
        allowLeadingWildCardsCB = new javax.swing.JCheckBox();
        jPanel1 = new javax.swing.JPanel();
        jScrollPane3 = new javax.swing.JScrollPane();
        ignoredNamesTable = new javax.swing.JTable();
        closeBtn = new javax.swing.JButton();
        updateBtn = new javax.swing.JButton();

        setDefaultCloseOperation(javax.swing.WindowConstants.EXIT_ON_CLOSE);
        setTitle("OpenGrok Configuration"); // NOI18N
        setName("configFrame"); // NOI18N

        jLabel1.setFont(new java.awt.Font("DejaVu Sans", 1, 13));
        jLabel1.setText("Logging:"); // NOI18N

        jLabel2.setText("Console Level"); // NOI18N

        consoleLevelCombo.setModel(new javax.swing.DefaultComboBoxModel(new String[] { "Item 1", "Item 2", "Item 3", "Item 4" }));

        jLabel3.setText("File Level"); // NOI18N

        fileLevelCombo.setModel(new javax.swing.DefaultComboBoxModel(new String[] { "Item 1", "Item 2", "Item 3", "Item 4" }));

        jLabel4.setText("File Path"); // NOI18N

        logFilePathField.setText("jTextField1"); // NOI18N

        jLabel5.setFont(new java.awt.Font("DejaVu Sans", 1, 13));
        jLabel5.setText("Publishing:"); // NOI18N

        jLabel6.setText("Host URL"); // NOI18N

        publishHostField.setText("jTextField2"); // NOI18N

        jLabel19.setFont(new java.awt.Font("DejaVu Sans", 1, 13));
        jLabel19.setText("Configuration");

        jLabel20.setText("Configuration file: ");

        configFileField.setText("jTextField1");

        javax.swing.GroupLayout genSettingsPanelLayout = new javax.swing.GroupLayout(genSettingsPanel);
        genSettingsPanel.setLayout(genSettingsPanelLayout);
        genSettingsPanelLayout.setHorizontalGroup(
            genSettingsPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(genSettingsPanelLayout.createSequentialGroup()
                .addContainerGap()
                .addGroup(genSettingsPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(jLabel5)
                    .addGroup(genSettingsPanelLayout.createSequentialGroup()
                        .addComponent(jLabel6)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(publishHostField, javax.swing.GroupLayout.DEFAULT_SIZE, 410, Short.MAX_VALUE))
                    .addComponent(jLabel1)
                    .addGroup(genSettingsPanelLayout.createSequentialGroup()
                        .addComponent(jLabel2)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(consoleLevelCombo, javax.swing.GroupLayout.PREFERRED_SIZE, 90, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(jLabel3)
                        .addGap(18, 18, 18)
                        .addComponent(fileLevelCombo, javax.swing.GroupLayout.PREFERRED_SIZE, 93, javax.swing.GroupLayout.PREFERRED_SIZE))
                    .addGroup(genSettingsPanelLayout.createSequentialGroup()
                        .addComponent(jLabel4)
                        .addGap(18, 18, 18)
                        .addComponent(logFilePathField, javax.swing.GroupLayout.DEFAULT_SIZE, 407, Short.MAX_VALUE))
                    .addComponent(jLabel19)
                    .addGroup(genSettingsPanelLayout.createSequentialGroup()
                        .addComponent(jLabel20)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(configFileField, javax.swing.GroupLayout.DEFAULT_SIZE, 349, Short.MAX_VALUE)))
                .addContainerGap())
        );
        genSettingsPanelLayout.setVerticalGroup(
            genSettingsPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(genSettingsPanelLayout.createSequentialGroup()
                .addContainerGap()
                .addComponent(jLabel1)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(genSettingsPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jLabel2)
                    .addComponent(jLabel3)
                    .addComponent(consoleLevelCombo, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(fileLevelCombo, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(genSettingsPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jLabel4)
                    .addComponent(logFilePathField, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addComponent(jLabel5)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(genSettingsPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jLabel6)
                    .addComponent(publishHostField, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addComponent(jLabel19)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(genSettingsPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jLabel20)
                    .addComponent(configFileField, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addContainerGap(75, Short.MAX_VALUE))
        );

        jTabPane.addTab("General", genSettingsPanel);

        jLabel7.setText("Data Root Path"); // NOI18N

        dataRootField.setText("jTextField1"); // NOI18N

        jLabel8.setText("Source Root Path"); // NOI18N

        sourceRootField.setText("jTextField1"); // NOI18N

        luceneLockingCB.setText("Use Lucene Locking"); // NOI18N

        optimizedDatabaseCB.setText("Optimized Database"); // NOI18N

        remoteSCMSupportedCB.setText("Remote SCM Supported"); // NOI18N

        indexVersionedFilesOnlyCB.setText("Index Versioned Files Only"); // NOI18N

        jLabel16.setText("CTags binary"); // NOI18N

        cTagsField.setText("jTextField1"); // NOI18N

        historyCacheCB.setText("Use History Cache"); // NOI18N

        jLabel17.setText("History Reader Time Limit"); // NOI18N

        historyReaderTimelimitField.setText("jTextField1"); // NOI18N

        generateHtmlCB.setText("Generate HTML"); // NOI18N

        compressXRefsCB.setText("Compress XRefs"); // NOI18N

        jLabel18.setText("Index Word Limit"); // NOI18N

        indexWordLimitField.setText("jTextField1"); // NOI18N

        javax.swing.GroupLayout indexerPanelLayout = new javax.swing.GroupLayout(indexerPanel);
        indexerPanel.setLayout(indexerPanelLayout);
        indexerPanelLayout.setHorizontalGroup(
            indexerPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(indexerPanelLayout.createSequentialGroup()
                .addContainerGap()
                .addGroup(indexerPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(indexerPanelLayout.createSequentialGroup()
                        .addGroup(indexerPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(jLabel7)
                            .addComponent(jLabel8))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addGroup(indexerPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(sourceRootField, javax.swing.GroupLayout.DEFAULT_SIZE, 362, Short.MAX_VALUE)
                            .addComponent(dataRootField, javax.swing.GroupLayout.DEFAULT_SIZE, 362, Short.MAX_VALUE)))
                    .addGroup(indexerPanelLayout.createSequentialGroup()
                        .addGroup(indexerPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING)
                            .addGroup(javax.swing.GroupLayout.Alignment.LEADING, indexerPanelLayout.createSequentialGroup()
                                .addComponent(jLabel18)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(indexWordLimitField, 0, 0, Short.MAX_VALUE))
                            .addComponent(remoteSCMSupportedCB, javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(luceneLockingCB, javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(historyCacheCB, javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(generateHtmlCB, javax.swing.GroupLayout.Alignment.LEADING))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addGroup(indexerPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(compressXRefsCB)
                            .addGroup(indexerPanelLayout.createSequentialGroup()
                                .addComponent(jLabel17)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(historyReaderTimelimitField, javax.swing.GroupLayout.DEFAULT_SIZE, 117, Short.MAX_VALUE))
                            .addComponent(optimizedDatabaseCB)
                            .addComponent(indexVersionedFilesOnlyCB)))
                    .addGroup(indexerPanelLayout.createSequentialGroup()
                        .addComponent(jLabel16)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(cTagsField, javax.swing.GroupLayout.DEFAULT_SIZE, 386, Short.MAX_VALUE)))
                .addContainerGap())
        );
        indexerPanelLayout.setVerticalGroup(
            indexerPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(indexerPanelLayout.createSequentialGroup()
                .addContainerGap()
                .addGroup(indexerPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jLabel7)
                    .addComponent(dataRootField, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(indexerPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jLabel8)
                    .addComponent(sourceRootField, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(indexerPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(luceneLockingCB)
                    .addComponent(optimizedDatabaseCB))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(indexerPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(remoteSCMSupportedCB)
                    .addComponent(indexVersionedFilesOnlyCB))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(indexerPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(historyCacheCB)
                    .addComponent(jLabel17)
                    .addComponent(historyReaderTimelimitField, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(indexerPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(generateHtmlCB)
                    .addComponent(compressXRefsCB))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(indexerPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jLabel18)
                    .addComponent(indexWordLimitField, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(indexerPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jLabel16)
                    .addComponent(cTagsField, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addContainerGap(49, Short.MAX_VALUE))
        );

        jTabPane.addTab("Indexer", indexerPanel);

        projectsTable.setModel(new javax.swing.table.DefaultTableModel(
            new Object [][] {
                {null, null, null, null},
                {null, null, null, null},
                {null, null, null, null},
                {null, null, null, null}
            },
            new String [] {
                "Title 1", "Title 2", "Title 3", "Title 4"
            }
        ));
        jScrollPane1.setViewportView(projectsTable);

        javax.swing.GroupLayout projectsPanelLayout = new javax.swing.GroupLayout(projectsPanel);
        projectsPanel.setLayout(projectsPanelLayout);
        projectsPanelLayout.setHorizontalGroup(
            projectsPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(projectsPanelLayout.createSequentialGroup()
                .addContainerGap()
                .addComponent(jScrollPane1, javax.swing.GroupLayout.DEFAULT_SIZE, 478, Short.MAX_VALUE)
                .addContainerGap())
        );
        projectsPanelLayout.setVerticalGroup(
            projectsPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(projectsPanelLayout.createSequentialGroup()
                .addComponent(jScrollPane1, javax.swing.GroupLayout.DEFAULT_SIZE, 292, Short.MAX_VALUE)
                .addContainerGap())
        );

        jTabPane.addTab("Projects", projectsPanel);

        repositoryTable.setModel(new javax.swing.table.DefaultTableModel(
            new Object [][] {
                {null, null, null, null},
                {null, null, null, null},
                {null, null, null, null},
                {null, null, null, null}
            },
            new String [] {
                "Title 1", "Title 2", "Title 3", "Title 4"
            }
        ));
        jScrollPane2.setViewportView(repositoryTable);

        javax.swing.GroupLayout repositoryPanelLayout = new javax.swing.GroupLayout(repositoryPanel);
        repositoryPanel.setLayout(repositoryPanelLayout);
        repositoryPanelLayout.setHorizontalGroup(
            repositoryPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(repositoryPanelLayout.createSequentialGroup()
                .addContainerGap()
                .addComponent(jScrollPane2, javax.swing.GroupLayout.DEFAULT_SIZE, 478, Short.MAX_VALUE)
                .addContainerGap())
        );
        repositoryPanelLayout.setVerticalGroup(
            repositoryPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(repositoryPanelLayout.createSequentialGroup()
                .addContainerGap()
                .addComponent(jScrollPane2, javax.swing.GroupLayout.DEFAULT_SIZE, 272, Short.MAX_VALUE)
                .addContainerGap())
        );

        jTabPane.addTab("Repositories", repositoryPanel);

        jLabel9.setText("Bug Page"); // NOI18N

        bugPageField.setText("jTextField1"); // NOI18N

        jLabel10.setText("Bug Pattern"); // NOI18N

        bugPatternField.setText("jTextField2"); // NOI18N

        jLabel11.setText("Review Page"); // NOI18N

        reviewPageField.setText("jTextField3"); // NOI18N

        jLabel12.setText("Review Pattern"); // NOI18N

        reviewPatternField.setText("jTextField4"); // NOI18N

        jLabel13.setText("User Page"); // NOI18N

        userPageField.setText("jTextField5"); // NOI18N

        jLabel14.setText("Web App LAF"); // NOI18N

        webAppLAFField.setText("jTextField1"); // NOI18N

        jLabel15.setText("URL Prefix"); // NOI18N

        urlPrefixField.setText("jTextField1"); // NOI18N

        allowLeadingWildCardsCB.setText("Allow Leading Wildcards"); // NOI18N

        javax.swing.GroupLayout historyListingPanelLayout = new javax.swing.GroupLayout(historyListingPanel);
        historyListingPanel.setLayout(historyListingPanelLayout);
        historyListingPanelLayout.setHorizontalGroup(
            historyListingPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(historyListingPanelLayout.createSequentialGroup()
                .addContainerGap()
                .addGroup(historyListingPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(allowLeadingWildCardsCB)
                    .addGroup(historyListingPanelLayout.createSequentialGroup()
                        .addGroup(historyListingPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addGroup(historyListingPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                                .addComponent(jLabel10)
                                .addComponent(jLabel15)
                                .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, historyListingPanelLayout.createSequentialGroup()
                                    .addComponent(jLabel9, javax.swing.GroupLayout.PREFERRED_SIZE, 69, javax.swing.GroupLayout.PREFERRED_SIZE)
                                    .addGap(19, 19, 19)))
                            .addComponent(jLabel12)
                            .addComponent(jLabel11)
                            .addComponent(jLabel13))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addGroup(historyListingPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(userPageField, javax.swing.GroupLayout.DEFAULT_SIZE, 377, Short.MAX_VALUE)
                            .addComponent(urlPrefixField, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.DEFAULT_SIZE, 377, Short.MAX_VALUE)
                            .addComponent(bugPageField, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.DEFAULT_SIZE, 377, Short.MAX_VALUE)
                            .addComponent(bugPatternField, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.DEFAULT_SIZE, 377, Short.MAX_VALUE)
                            .addComponent(reviewPageField, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.DEFAULT_SIZE, 377, Short.MAX_VALUE)
                            .addComponent(reviewPatternField, javax.swing.GroupLayout.DEFAULT_SIZE, 377, Short.MAX_VALUE)))
                    .addGroup(historyListingPanelLayout.createSequentialGroup()
                        .addComponent(jLabel14)
                        .addGap(18, 18, 18)
                        .addComponent(webAppLAFField, javax.swing.GroupLayout.DEFAULT_SIZE, 378, Short.MAX_VALUE)))
                .addContainerGap())
        );
        historyListingPanelLayout.setVerticalGroup(
            historyListingPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(historyListingPanelLayout.createSequentialGroup()
                .addContainerGap()
                .addComponent(allowLeadingWildCardsCB)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(historyListingPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jLabel15)
                    .addComponent(urlPrefixField, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(historyListingPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jLabel9)
                    .addComponent(bugPageField, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(historyListingPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jLabel10)
                    .addComponent(bugPatternField, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(historyListingPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(reviewPageField, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(jLabel11))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(historyListingPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(reviewPatternField, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(jLabel12))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(historyListingPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jLabel13)
                    .addComponent(userPageField, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(historyListingPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jLabel14)
                    .addComponent(webAppLAFField, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addContainerGap(23, Short.MAX_VALUE))
        );

        jTabPane.addTab("Web App", historyListingPanel);

        ignoredNamesTable.setModel(new javax.swing.table.DefaultTableModel(
            new Object [][] {
                {null, null, null, null},
                {null, null, null, null},
                {null, null, null, null},
                {null, null, null, null}
            },
            new String [] {
                "Title 1", "Title 2", "Title 3", "Title 4"
            }
        ));
        jScrollPane3.setViewportView(ignoredNamesTable);

        javax.swing.GroupLayout jPanel1Layout = new javax.swing.GroupLayout(jPanel1);
        jPanel1.setLayout(jPanel1Layout);
        jPanel1Layout.setHorizontalGroup(
            jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel1Layout.createSequentialGroup()
                .addContainerGap()
                .addComponent(jScrollPane3, javax.swing.GroupLayout.DEFAULT_SIZE, 478, Short.MAX_VALUE)
                .addContainerGap())
        );
        jPanel1Layout.setVerticalGroup(
            jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel1Layout.createSequentialGroup()
                .addContainerGap()
                .addComponent(jScrollPane3, javax.swing.GroupLayout.DEFAULT_SIZE, 272, Short.MAX_VALUE)
                .addContainerGap())
        );

        jTabPane.addTab("IgnoredNames", jPanel1);

        closeBtn.setText("Close"); // NOI18N
        closeBtn.setToolTipText("Close without deploying changes"); // NOI18N
        closeBtn.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                closeBtnActionPerformed(evt);
            }
        });

        updateBtn.setText("Update"); // NOI18N
        updateBtn.setToolTipText("send update to the opengrok agent"); // NOI18N
        updateBtn.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                updateBtnActionPerformed(evt);
            }
        });

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(getContentPane());
        getContentPane().setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING)
                    .addComponent(jTabPane, javax.swing.GroupLayout.DEFAULT_SIZE, 539, Short.MAX_VALUE)
                    .addGroup(layout.createSequentialGroup()
                        .addComponent(updateBtn)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(closeBtn)))
                .addContainerGap())
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, layout.createSequentialGroup()
                .addContainerGap()
                .addComponent(jTabPane, javax.swing.GroupLayout.DEFAULT_SIZE, 358, Short.MAX_VALUE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(closeBtn)
                    .addComponent(updateBtn))
                .addContainerGap())
        );

        pack();
    }// </editor-fold>//GEN-END:initComponents

    @SuppressWarnings("unused")
    private void closeBtnActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_closeBtnActionPerformed
        this.dispose();
}//GEN-LAST:event_closeBtnActionPerformed

    @SuppressWarnings("unused")
    private void updateBtnActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_updateBtnActionPerformed
        try {
            logger.info("updating to agent management mbean attributes");
            if (!filePath.equals(this.logFilePathField.getText())) {
                Attribute attribute = new Attribute("FileLogPath", this.logFilePathField.getText());
                con.getMBeanServerConnection().setAttribute(managementObjectName, attribute);
                filePath = this.logFilePathField.getText();
            }
            if (!fileLevel.equals((Level)this.fileLevelCombo.getSelectedItem())) {
                Attribute attribute = new Attribute("FileLogLevel", this.fileLevelCombo.getSelectedItem());
                con.getMBeanServerConnection().setAttribute(managementObjectName, attribute);
                fileLevel = (Level)this.fileLevelCombo.getSelectedItem();
            }
            if (!consoleLevel.equals((Level)this.consoleLevelCombo.getSelectedItem())) {
                Attribute attribute = new Attribute("ConsoleLogLevel", this.consoleLevelCombo.getSelectedItem());         
                con.getMBeanServerConnection().setAttribute(managementObjectName, attribute);
                consoleLevel = (Level)this.consoleLevelCombo.getSelectedItem();
            }
            if (!publishURL.equals(this.publishHostField.getText())) {
                Attribute attribute = new Attribute("PublishServerURL", this.publishHostField.getText());
                con.getMBeanServerConnection().setAttribute(managementObjectName, attribute);
                publishURL = this.publishHostField.getText();
            }
            if (!configurationFile.equals(this.configFileField.getText())) {
                Attribute attribute = new Attribute("ConfigurationFile",this.configFileField.getText());
                con.getMBeanServerConnection().setAttribute(managementObjectName, attribute);
                configurationFile = this.configFileField.getText();
            }

            logger.info("updating agent configuration mbean attributes");
            //here we just set the configuration object, as we assume the user
            //knows that he have done a change before pressing update.
            updateConfigurationFromGUIObjects();

            Attribute attribute = new Attribute("Configuration", config.getXMLRepresentationAsString());
            con.getMBeanServerConnection().setAttribute(configObjectName, attribute);
            
        } catch (InstanceNotFoundException ex) {
            Logger.getLogger(ConfigurationsFrame.class.getName()).log(Level.SEVERE, null, ex);
            this.showError(ex);
        } catch (AttributeNotFoundException ex) {
            Logger.getLogger(ConfigurationsFrame.class.getName()).log(Level.SEVERE, null, ex);
            this.showError(ex);
        } catch (InvalidAttributeValueException ex) {
            Logger.getLogger(ConfigurationsFrame.class.getName()).log(Level.SEVERE, null, ex);
            this.showError(ex);
        } catch (MBeanException ex) {
            Logger.getLogger(ConfigurationsFrame.class.getName()).log(Level.SEVERE, null, ex);
            this.showError(ex);
        } catch (ReflectionException ex) {
            Logger.getLogger(ConfigurationsFrame.class.getName()).log(Level.SEVERE, null, ex);
            this.showError(ex);
        } catch (IOException ex) {
            Logger.getLogger(ConfigurationsFrame.class.getName()).log(Level.SEVERE, null, ex);
            this.showError(ex);
        }

    }//GEN-LAST:event_updateBtnActionPerformed

    // Suppress UnusedFormalParameter until the method has been implemented.
    // Also suppress AvoidDuplicateLiterals since PMD doesn't like that we
    // use the same literal in multiple SuppressWarning annotations, and it
    // doesn't understand what we mean if we use a constant instead.
// Avoid UnusedFormalParameter until the method has been implemented
// Avoid UnusedFormalParameter until the method has been implemented
// Avoid UnusedFormalParameter until the method has been implemented


    /**
     * put the GUI fields and objects into the OpenGrok Configuration object
     */
   private void updateConfigurationFromGUIObjects() {
       //index tab settings
       config.setSourceRoot(this.sourceRootField.getText());
       config.setDataRoot(this.dataRootField.getText());
       config.setIndexVersionedFilesOnly(this.indexVersionedFilesOnlyCB.isSelected());
       config.setUsingLuceneLocking(luceneLockingCB.isSelected());
       config.setRemoteScmSupported(remoteSCMSupportedCB.isSelected());
       config.setOptimizeDatabase(this.optimizedDatabaseCB.isSelected());
       config.setAllowLeadingWildcard(this.allowLeadingWildCardsCB.isSelected());
       config.setCompressXref(this.compressXRefsCB.isSelected());
       config.setCtags(this.cTagsField.getText());
       config.setGenerateHtml(this.generateHtmlCB.isSelected());
       config.setHistoryCache(this.historyCacheCB.isSelected());
       config.setHistoryCacheTime(Integer.parseInt(this.historyReaderTimelimitField.getText()));
       config.setIndexWordLimit(Integer.parseInt(this.indexWordLimitField.getText()));
       config.setUrlPrefix(this.urlPrefixField.getText());
       /*
       config.setQuickContextScan(rootPaneCheckingEnabled);
       config.setDefaultProject(defaultProject);
       config.setIgnoredNames(ignoredNames);
       config.setProjects(projects);
       config.setRepositories(repositories);
       config.setVerbose(rootPaneCheckingEnabled);
       */
       //web app tab settings
       config.setBugPage(bugPageField.getText());
       config.setBugPattern(bugPatternField.getText());
       config.setReviewPage(reviewPageField.getText());
       config.setReviewPattern(reviewPatternField.getText());
       config.setUserPage(userPageField.getText());
       config.setWebappLAF(this.webAppLAFField.getText());
   }

    /**
    * @param args the command line arguments
    */
    public static void main(String args[]) {
        java.awt.EventQueue.invokeLater(new Runnable() {
            public void run() {
                try {
                    new ConfigurationsFrame(null).setVisible(true);
                } catch (IOException ex) {
                    Logger.getLogger(ConfigurationsFrame.class.getName()).log(Level.SEVERE, null, ex);
                }
            }
        });
    }

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JCheckBox allowLeadingWildCardsCB;
    private javax.swing.JTextField bugPageField;
    private javax.swing.JTextField bugPatternField;
    private javax.swing.JTextField cTagsField;
    private javax.swing.JButton closeBtn;
    private javax.swing.JCheckBox compressXRefsCB;
    private javax.swing.JTextField configFileField;
    private javax.swing.JComboBox consoleLevelCombo;
    private javax.swing.JTextField dataRootField;
    private javax.swing.JComboBox fileLevelCombo;
    private javax.swing.JPanel genSettingsPanel;
    private javax.swing.JCheckBox generateHtmlCB;
    private javax.swing.JCheckBox historyCacheCB;
    private javax.swing.JPanel historyListingPanel;
    private javax.swing.JTextField historyReaderTimelimitField;
    private javax.swing.JTable ignoredNamesTable;
    private javax.swing.JCheckBox indexVersionedFilesOnlyCB;
    private javax.swing.JTextField indexWordLimitField;
    private javax.swing.JPanel indexerPanel;
    private javax.swing.JLabel jLabel1;
    private javax.swing.JLabel jLabel10;
    private javax.swing.JLabel jLabel11;
    private javax.swing.JLabel jLabel12;
    private javax.swing.JLabel jLabel13;
    private javax.swing.JLabel jLabel14;
    private javax.swing.JLabel jLabel15;
    private javax.swing.JLabel jLabel16;
    private javax.swing.JLabel jLabel17;
    private javax.swing.JLabel jLabel18;
    private javax.swing.JLabel jLabel19;
    private javax.swing.JLabel jLabel2;
    private javax.swing.JLabel jLabel20;
    private javax.swing.JLabel jLabel3;
    private javax.swing.JLabel jLabel4;
    private javax.swing.JLabel jLabel5;
    private javax.swing.JLabel jLabel6;
    private javax.swing.JLabel jLabel7;
    private javax.swing.JLabel jLabel8;
    private javax.swing.JLabel jLabel9;
    private javax.swing.JPanel jPanel1;
    private javax.swing.JScrollPane jScrollPane1;
    private javax.swing.JScrollPane jScrollPane2;
    private javax.swing.JScrollPane jScrollPane3;
    private javax.swing.JTabbedPane jTabPane;
    private javax.swing.JTextField logFilePathField;
    private javax.swing.JCheckBox luceneLockingCB;
    private javax.swing.JCheckBox optimizedDatabaseCB;
    private javax.swing.JPanel projectsPanel;
    private javax.swing.JTable projectsTable;
    private javax.swing.JTextField publishHostField;
    private javax.swing.JCheckBox remoteSCMSupportedCB;
    private javax.swing.JPanel repositoryPanel;
    private javax.swing.JTable repositoryTable;
    private javax.swing.JTextField reviewPageField;
    private javax.swing.JTextField reviewPatternField;
    private javax.swing.JTextField sourceRootField;
    private javax.swing.JButton updateBtn;
    private javax.swing.JTextField urlPrefixField;
    private javax.swing.JTextField userPageField;
    private javax.swing.JTextField webAppLAFField;
    // End of variables declaration//GEN-END:variables

}
