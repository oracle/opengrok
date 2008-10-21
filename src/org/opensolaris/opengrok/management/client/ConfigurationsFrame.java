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

import java.io.IOException;
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
import org.opensolaris.opengrok.OpenGrokLogger;
import org.opensolaris.opengrok.configuration.Configuration;

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
        } catch (NullPointerException ex) {
            logger.log(Level.SEVERE, "NullPointer", ex);
        }
        initComponents();
        createLogLevelCombos();
        getConfigsFromAgent();
        
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
        
        /*
         this is not documented:
       config.setQuickContextScan(rootPaneCheckingEnabled);
       config.setDefaultProject(defaultProject);
       config.setIgnoredNames(ignoredNames);
       config.setProjects(projects);
       config.setRepositories(repositories);
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
        closeBtn = new javax.swing.JButton();
        updateBtn = new javax.swing.JButton();

        setDefaultCloseOperation(javax.swing.WindowConstants.EXIT_ON_CLOSE);
        setTitle("OpenGrok Configuration");
        setName("configFrame");

        jLabel1.setFont(new java.awt.Font("DejaVu Sans", 1, 13));
        jLabel1.setText("Logging:");

        jLabel2.setText("Console Level");

        consoleLevelCombo.setModel(new javax.swing.DefaultComboBoxModel(new String[] { "Item 1", "Item 2", "Item 3", "Item 4" }));

        jLabel3.setText("File Level");

        fileLevelCombo.setModel(new javax.swing.DefaultComboBoxModel(new String[] { "Item 1", "Item 2", "Item 3", "Item 4" }));

        jLabel4.setText("File Path");

        logFilePathField.setText("jTextField1");

        jLabel5.setFont(new java.awt.Font("DejaVu Sans", 1, 13));
        jLabel5.setText("Publishing:");

        jLabel6.setText("Host URL");

        publishHostField.setText("jTextField2");

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
                        .addComponent(publishHostField, javax.swing.GroupLayout.DEFAULT_SIZE, 439, Short.MAX_VALUE))
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
                        .addComponent(logFilePathField, javax.swing.GroupLayout.DEFAULT_SIZE, 439, Short.MAX_VALUE)))
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
                .addContainerGap(126, Short.MAX_VALUE))
        );

        jTabPane.addTab("General", genSettingsPanel);

        jLabel7.setText("Data Root Path");

        dataRootField.setText("jTextField1");

        jLabel8.setText("Source Root Path");

        sourceRootField.setText("jTextField1");

        luceneLockingCB.setText("Use Lucene Locking");
        luceneLockingCB.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                luceneLockingCBActionPerformed(evt);
            }
        });

        optimizedDatabaseCB.setText("Optimized Database");

        remoteSCMSupportedCB.setText("Remote SCM Supported");

        indexVersionedFilesOnlyCB.setText("Index Versioned Files Only");

        jLabel16.setText("CTags binary");

        cTagsField.setText("jTextField1");

        historyCacheCB.setText("Use History Cache");
        historyCacheCB.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                historyCacheCBActionPerformed(evt);
            }
        });

        jLabel17.setText("History Reader Time Limit");

        historyReaderTimelimitField.setText("jTextField1");

        generateHtmlCB.setText("Generate HTML");

        compressXRefsCB.setText("Compress XRefs");
        compressXRefsCB.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                compressXRefsCBActionPerformed(evt);
            }
        });

        jLabel18.setText("Index Word Limit");

        indexWordLimitField.setText("jTextField1");

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
                            .addComponent(sourceRootField, javax.swing.GroupLayout.DEFAULT_SIZE, 413, Short.MAX_VALUE)
                            .addComponent(dataRootField, javax.swing.GroupLayout.DEFAULT_SIZE, 413, Short.MAX_VALUE)))
                    .addGroup(indexerPanelLayout.createSequentialGroup()
                        .addComponent(jLabel16)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(cTagsField, javax.swing.GroupLayout.DEFAULT_SIZE, 433, Short.MAX_VALUE))
                    .addGroup(indexerPanelLayout.createSequentialGroup()
                        .addGroup(indexerPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING)
                            .addGroup(javax.swing.GroupLayout.Alignment.LEADING, indexerPanelLayout.createSequentialGroup()
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
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
                                .addGroup(indexerPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                                    .addGroup(indexerPanelLayout.createSequentialGroup()
                                        .addComponent(jLabel17)
                                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                        .addComponent(historyReaderTimelimitField, javax.swing.GroupLayout.DEFAULT_SIZE, 215, Short.MAX_VALUE))
                                    .addComponent(optimizedDatabaseCB)
                                    .addComponent(indexVersionedFilesOnlyCB))
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)))))
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
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, 14, Short.MAX_VALUE)
                .addGroup(indexerPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jLabel16)
                    .addComponent(cTagsField, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addContainerGap())
        );

        jTabPane.addTab("Indexer", indexerPanel);

        javax.swing.GroupLayout projectsPanelLayout = new javax.swing.GroupLayout(projectsPanel);
        projectsPanel.setLayout(projectsPanelLayout);
        projectsPanelLayout.setHorizontalGroup(
            projectsPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 523, Short.MAX_VALUE)
        );
        projectsPanelLayout.setVerticalGroup(
            projectsPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 286, Short.MAX_VALUE)
        );

        jTabPane.addTab("Projects", projectsPanel);

        jLabel9.setText("Bug Page");

        bugPageField.setText("jTextField1");

        jLabel10.setText("Bug Pattern");

        bugPatternField.setText("jTextField2");

        jLabel11.setText("Review Page");

        reviewPageField.setText("jTextField3");
        reviewPageField.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                reviewPageFieldActionPerformed(evt);
            }
        });

        jLabel12.setText("Review Pattern");

        reviewPatternField.setText("jTextField4");

        jLabel13.setText("User Page");

        userPageField.setText("jTextField5");

        jLabel14.setText("Web App LAF");

        webAppLAFField.setText("jTextField1");

        jLabel15.setText("URL Prefix");

        urlPrefixField.setText("jTextField1");

        allowLeadingWildCardsCB.setText("Allow Leading Wildcards");

        javax.swing.GroupLayout historyListingPanelLayout = new javax.swing.GroupLayout(historyListingPanel);
        historyListingPanel.setLayout(historyListingPanelLayout);
        historyListingPanelLayout.setHorizontalGroup(
            historyListingPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(historyListingPanelLayout.createSequentialGroup()
                .addContainerGap()
                .addGroup(historyListingPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(historyListingPanelLayout.createSequentialGroup()
                        .addGroup(historyListingPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(jLabel9)
                            .addComponent(jLabel10)
                            .addComponent(jLabel11)
                            .addComponent(jLabel12)
                            .addComponent(jLabel13)
                            .addComponent(jLabel14)
                            .addComponent(jLabel15))
                        .addGap(24, 24, 24)
                        .addGroup(historyListingPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(userPageField, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.DEFAULT_SIZE, 401, Short.MAX_VALUE)
                            .addComponent(reviewPatternField, javax.swing.GroupLayout.DEFAULT_SIZE, 401, Short.MAX_VALUE)
                            .addComponent(reviewPageField, javax.swing.GroupLayout.DEFAULT_SIZE, 401, Short.MAX_VALUE)
                            .addComponent(bugPatternField, javax.swing.GroupLayout.DEFAULT_SIZE, 401, Short.MAX_VALUE)
                            .addComponent(bugPageField, javax.swing.GroupLayout.DEFAULT_SIZE, 401, Short.MAX_VALUE)
                            .addComponent(webAppLAFField, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.DEFAULT_SIZE, 401, Short.MAX_VALUE)
                            .addComponent(urlPrefixField, javax.swing.GroupLayout.DEFAULT_SIZE, 401, Short.MAX_VALUE)))
                    .addComponent(allowLeadingWildCardsCB))
                .addContainerGap())
        );
        historyListingPanelLayout.setVerticalGroup(
            historyListingPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, historyListingPanelLayout.createSequentialGroup()
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
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
                    .addComponent(jLabel11)
                    .addComponent(reviewPageField, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(historyListingPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jLabel12)
                    .addComponent(reviewPatternField, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(historyListingPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jLabel13)
                    .addComponent(userPageField, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(historyListingPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jLabel14)
                    .addComponent(webAppLAFField, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addContainerGap())
        );

        jTabPane.addTab("Web App", historyListingPanel);

        closeBtn.setText("Close");
        closeBtn.setToolTipText("Close without deploying changes");
        closeBtn.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                closeBtnActionPerformed(evt);
            }
        });

        updateBtn.setText("Update");
        updateBtn.setToolTipText("send update to the opengrok agent");
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
                    .addComponent(jTabPane, javax.swing.GroupLayout.Alignment.LEADING, javax.swing.GroupLayout.DEFAULT_SIZE, 531, Short.MAX_VALUE)
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
                .addComponent(jTabPane, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(closeBtn)
                    .addComponent(updateBtn))
                .addContainerGap())
        );

        pack();
    }// </editor-fold>//GEN-END:initComponents
    
    @SuppressWarnings("unused")
    private void closeBtnActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_closeBtnActionPerformed
        // TODO add your handling code here:
        this.dispose();
}//GEN-LAST:event_closeBtnActionPerformed

    @SuppressWarnings("unused")
    private void updateBtnActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_updateBtnActionPerformed
        // TODO add your handling code here:
        
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

            logger.info("updating agent configuration mbean attributes");
            //here we just set the configuration object, as we assume the user
            //knows that he have done a change before pressing update.
            updateConfigurationFromGUIObjects();

            Attribute attribute = new Attribute("Configuration", config.getXMLRepresentationAsString());
            con.getMBeanServerConnection().setAttribute(configObjectName, attribute);
            
        } catch (InstanceNotFoundException ex) {
            Logger.getLogger(ConfigurationsFrame.class.getName()).log(Level.SEVERE, null, ex);
        } catch (AttributeNotFoundException ex) {
            Logger.getLogger(ConfigurationsFrame.class.getName()).log(Level.SEVERE, null, ex);
        } catch (InvalidAttributeValueException ex) {
            Logger.getLogger(ConfigurationsFrame.class.getName()).log(Level.SEVERE, null, ex);
        } catch (MBeanException ex) {
            Logger.getLogger(ConfigurationsFrame.class.getName()).log(Level.SEVERE, null, ex);
        } catch (ReflectionException ex) {
            Logger.getLogger(ConfigurationsFrame.class.getName()).log(Level.SEVERE, null, ex);
        } catch (IOException ex) {
            Logger.getLogger(ConfigurationsFrame.class.getName()).log(Level.SEVERE, null, ex);
        }

    }//GEN-LAST:event_updateBtnActionPerformed

    // Suppress UnusedFormalParameter until the method has been implemented.
    // Also suppress AvoidDuplicateLiterals since PMD doesn't like that we
    // use the same literal in multiple SuppressWarning annotations, and it
    // doesn't understand what we mean if we use a constant instead.
    @SuppressWarnings({"PMD.UnusedFormalParameter", "PMD.AvoidDuplicateLiterals"})
    private void luceneLockingCBActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_luceneLockingCBActionPerformed
        // TODO add your handling code here:
}//GEN-LAST:event_luceneLockingCBActionPerformed

// Avoid UnusedFormalParameter until the method has been implemented
@SuppressWarnings("PMD.UnusedFormalParameter")
private void reviewPageFieldActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_reviewPageFieldActionPerformed
// TODO add your handling code here:
}//GEN-LAST:event_reviewPageFieldActionPerformed

// Avoid UnusedFormalParameter until the method has been implemented
@SuppressWarnings("PMD.UnusedFormalParameter")
private void historyCacheCBActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_historyCacheCBActionPerformed
// TODO add your handling code here:
}//GEN-LAST:event_historyCacheCBActionPerformed

// Avoid UnusedFormalParameter until the method has been implemented
@SuppressWarnings("PMD.UnusedFormalParameter")
private void compressXRefsCBActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_compressXRefsCBActionPerformed
// TODO add your handling code here:
}//GEN-LAST:event_compressXRefsCBActionPerformed



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
    private javax.swing.JComboBox consoleLevelCombo;
    private javax.swing.JTextField dataRootField;
    private javax.swing.JComboBox fileLevelCombo;
    private javax.swing.JPanel genSettingsPanel;
    private javax.swing.JCheckBox generateHtmlCB;
    private javax.swing.JCheckBox historyCacheCB;
    private javax.swing.JPanel historyListingPanel;
    private javax.swing.JTextField historyReaderTimelimitField;
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
    private javax.swing.JLabel jLabel2;
    private javax.swing.JLabel jLabel3;
    private javax.swing.JLabel jLabel4;
    private javax.swing.JLabel jLabel5;
    private javax.swing.JLabel jLabel6;
    private javax.swing.JLabel jLabel7;
    private javax.swing.JLabel jLabel8;
    private javax.swing.JLabel jLabel9;
    private javax.swing.JTabbedPane jTabPane;
    private javax.swing.JTextField logFilePathField;
    private javax.swing.JCheckBox luceneLockingCB;
    private javax.swing.JCheckBox optimizedDatabaseCB;
    private javax.swing.JPanel projectsPanel;
    private javax.swing.JTextField publishHostField;
    private javax.swing.JCheckBox remoteSCMSupportedCB;
    private javax.swing.JTextField reviewPageField;
    private javax.swing.JTextField reviewPatternField;
    private javax.swing.JTextField sourceRootField;
    private javax.swing.JButton updateBtn;
    private javax.swing.JTextField urlPrefixField;
    private javax.swing.JTextField userPageField;
    private javax.swing.JTextField webAppLAFField;
    // End of variables declaration//GEN-END:variables

}
