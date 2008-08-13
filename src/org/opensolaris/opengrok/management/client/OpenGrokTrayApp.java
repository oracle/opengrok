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
package org.opensolaris.opengrok.management.client;

import java.awt.AWTException;
import java.awt.Image;
import java.awt.MenuItem;
import java.awt.PopupMenu;
import java.awt.SystemTray;
import java.awt.Toolkit;
import java.awt.TrayIcon;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.WindowEvent;
import java.awt.event.WindowListener;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.opensolaris.opengrok.OpenGrokLogger;

/**
 *
 * @author Jan S Berg
 */
public class OpenGrokTrayApp {

    private static TrayIcon trayIcon;
    private static String cfgfile;
    private SettingsPersistence settings;
    private final static Logger log = OpenGrokLogger.getLogger();
    public static final String OPENGROKICONURL = "opengrok.gif";
    public static final String INDEXWARNINGICONURL = "opengrok_indexwarning.gif";
    public static final String NOCONNECTIONICONURL = "opengrok_noconnection.gif";
    private AgentConnection agent = null;
    private boolean agentConnected = false;
    private Image opengrokImage;
    private Image indexWarningImage;
    private Image noConnectionImage;
    private Image currentIcon;

    /**
     * @param args the command line arguments
     */
    public static void main(String[] args) {
        try {
            for (int i = 0; i < args.length; i++) {
                if (args[i].equals("-config")) {
                    if (++i < args.length) {
                        cfgfile = args[i];
                    } else {
                        System.err.println("-config, argument missing: config file");
                        System.exit(1);
                    }
                }
            }
            OpenGrokTrayApp ogta = new OpenGrokTrayApp(cfgfile);
            ogta.enableSystemTray();
        } catch (IOException ex) {
            log.log(Level.SEVERE, "Exception starting ", ex);
        }
    }

    public OpenGrokTrayApp(String cfgfile) throws IOException {
        settings = new SettingsPersistence(cfgfile);
        loadImages();
        OpenGrokLogger.setupLogger(settings.getProperty(SettingsPersistence.LOGGINGPATHKEY),
                settings.getFileLogLevel(),
                settings.getConsoleLogLevel());
        if (tryAgentConnect()) {
            currentIcon = opengrokImage;
        } else {
            currentIcon = noConnectionImage;
        }

    }

    private void loadImages() {
        java.net.URL imageUrl;
        imageUrl = OpenGrokTrayApp.class.getResource(OPENGROKICONURL);
        opengrokImage = Toolkit.getDefaultToolkit().getImage(imageUrl);
        imageUrl = OpenGrokTrayApp.class.getResource(INDEXWARNINGICONURL);
        indexWarningImage = Toolkit.getDefaultToolkit().getImage(imageUrl);
        imageUrl = OpenGrokTrayApp.class.getResource(NOCONNECTIONICONURL);
        noConnectionImage = Toolkit.getDefaultToolkit().getImage(imageUrl);
    }

    private boolean tryAgentConnect() {
        boolean retval = false;
        try {
            if (agent == null) {
                agent = new AgentConnection(settings.getAgentUrl());
            }
            agent.setUrl(settings.getAgentUrl());

            agent.connect();
            agent.registerListener();
            retval = true;
            agentConnected = true;
        } catch (Exception ex) {
            log.log(Level.WARNING, "Could not connect ", ex);
        }
        return retval;
    }

    private void enableSystemTray() {

        if (SystemTray.isSupported()) {

            SystemTray tray = SystemTray.getSystemTray();
            MouseListener mouseListener = new MouseListener() {

                @Override
                public void mouseClicked(MouseEvent e) {
                    log.finest("Tray Icon - Mouse clicked!");
                }

                @Override
                public void mouseEntered(MouseEvent e) {
                    log.finest("Tray Icon - Mouse entered!");
                }

                @Override
                public void mouseExited(MouseEvent e) {
                    log.finest("Tray Icon - Mouse exited!");
                }

                @Override
                public void mousePressed(MouseEvent e) {
                    log.finest("Tray Icon - Mouse pressed!");
                }

                @Override
                public void mouseReleased(MouseEvent e) {
                    log.finest("Tray Icon - Mouse released!");
                }
            };

            log.info("Creating listeners");

            ActionListener exitListener = new ActionListener() {

                @Override
                public void actionPerformed(ActionEvent e) {
                    log.info("Exiting...");
                    if (agent != null && agent.isConnected()) {
                        agent.unregister();
                    }
                    System.exit(0);
                }
            };


            final WindowListener settingsWindowListener = new WindowListener() {

                @Override
                public void windowOpened(WindowEvent arg0) {
                    log.finest("Received window opened");
                }

                @Override
                public void windowClosing(WindowEvent arg0) {
                    log.finest("Received window closing " + arg0);
                }

                @Override
                public void windowClosed(WindowEvent arg0) {
                    log.info("Received window closing");
                    if (!agentConnected) {
                        tryAgentConnect();
                    }
                    if (!agentConnected) {
                        currentIcon = noConnectionImage;
                    } else {
                        currentIcon = opengrokImage;
                    }
                    trayIcon.setImage(currentIcon);
                }

                @Override
                public void windowIconified(WindowEvent arg0) {
                    log.finest("Received window iconified");
                }

                public void windowDeiconified(WindowEvent arg0) {
                    log.finest("Received window deiconified");
                }

                public void windowActivated(WindowEvent arg0) {
                    log.finest("Received window activated");
                }

                public void windowDeactivated(WindowEvent arg0) {
                    log.finest("Received window deactivated");
                }
            };

            final WindowListener notificationWindowListener = new WindowListener() {

                public void windowOpened(WindowEvent arg0) {
                    log.finest("Received window opened");
                }

                public void windowClosing(WindowEvent arg0) {
                    log.finest("Received window closing");
                }

                public void windowClosed(WindowEvent arg0) {
                    log.info("Received window closing");
                    if (agent != null && agent.isConnected()) {
                        currentIcon = opengrokImage;
                    } else {
                        currentIcon = noConnectionImage;
                    }
                    trayIcon.setImage(currentIcon);
                }

                public void windowIconified(WindowEvent arg0) {
                    log.finest("Received window iconified");
                }

                public void windowDeiconified(WindowEvent arg0) {
                    log.finest("Received window deiconified");
                }

                public void windowActivated(WindowEvent arg0) {
                    log.finest("Received window activated");
                }

                public void windowDeactivated(WindowEvent arg0) {
                    log.finest("Received window deactivated");
                }
            };

            ActionListener settingsListener = new ActionListener() {

                public void actionPerformed(ActionEvent e) {
                    log.finer("Settings...");
                    SettingsFrame sf = new SettingsFrame(settings);
                    sf.addWindowListener(settingsWindowListener);
                    sf.setVisible(true);
                    log.finer("Done settings");

                }
            };

            ActionListener showNotificationsListener = new ActionListener() {

                public void actionPerformed(ActionEvent e) {
                    log.info("Notifications...");
                    String notifs = "";
                    long starttime = 0;
                    long endtime = 0;
                    if (agentConnected && agent != null) {
                        notifs = agent.getFilesInfo();
                        agent.clearFilesInfo();
                        starttime = agent.getStartTime();
                        endtime = agent.getEndTime();
                    }
                    NotificationsFrame sf = new NotificationsFrame(notifs, starttime, endtime);
                    sf.addWindowListener(notificationWindowListener);
                    sf.setVisible(true);

                    log.finest("Done Notifications");

                }
            };

            log.info("Creating popup and tray icon ");

            PopupMenu popup = new PopupMenu();
            MenuItem exitItem = new MenuItem("Exit");
            exitItem.addActionListener(exitListener);
            MenuItem settingsItem = new MenuItem("Settings");
            settingsItem.addActionListener(settingsListener);
            MenuItem notifyItem = new MenuItem("Show notifications");
            notifyItem.addActionListener(showNotificationsListener);

            popup.add(settingsItem);
            popup.add(notifyItem);
            popup.addSeparator();
            popup.add(exitItem);



            trayIcon = new TrayIcon(opengrokImage, "OpenGrok Agent Client", popup);

            ActionListener actionListener = new ActionListener() {

                public void actionPerformed(ActionEvent e) {
                    log.info("Got Event " + e.getActionCommand());
                    trayIcon.displayMessage("OpenGrok Indexer",
                            "Files have been deleted/added",
                            TrayIcon.MessageType.INFO);
                    trayIcon.setImage(indexWarningImage);
                }
            };

            if (agent == null || !agent.isConnected()) {
                trayIcon.setImage(noConnectionImage);
            } else {
                agent.setActionListener(actionListener);
            }

            trayIcon.setImageAutoSize(true);
            trayIcon.addActionListener(actionListener);
            trayIcon.addMouseListener(mouseListener);

            try {
                tray.add(trayIcon);
            } catch (AWTException e) {
                System.err.println("TrayIcon could not be added.");
            }
            log.info("Created, ready for action");

        } else {

            //  System Tray is not supported
            System.err.println("System Tray is NOT supported");

        }
    }
}
