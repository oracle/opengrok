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

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.IOException;
import java.net.MalformedURLException;
import java.util.HashMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.management.InstanceNotFoundException;
import javax.management.MBeanServerConnection;
import javax.management.MalformedObjectNameException;
import javax.management.Notification;
import javax.management.NotificationListener;
import javax.management.ObjectName;
import javax.management.remote.JMXConnector;
import javax.management.remote.JMXConnectorFactory;
import javax.management.remote.JMXServiceURL;

/**
 * Class to handle the connection and methods to the OpenGrok Agent
 * @author Jan S Berg
 */
public class AgentConnection implements NotificationListener {

    private MBeanServerConnection server = null;
    private ObjectName objName = null;
    private String objStrName = "OGA:name=AgentIndexRunner,source=timer";
    private static final Logger logger = Logger.getLogger("org.opensolaris.opengrok");
    private String agenturl = "";
    private JMXConnector jmxconn = null; // TODO: Set to private
    private boolean connected = false;
    private ActionListener actionListener = null;
    private boolean listenerRegistered = false;
    private final static long RECONNECT_SLEEPTIME = 5000;
    public final static String JAVA_LANG_STRING = "java.lang.String";
    public final static String JAVA_LANG_OBJECT = "java.lang.Object";
    private StringBuilder filesInfo = new StringBuilder();
    /** MAX size of the filesInfo buffer */
    private final static int FILESINFOMAX = 50000;
    private long starttime = 0;
    private long endtime = 0;

    /**
     * Constructor for AgentConnection to OpenGrok JMX Agent
     * @param url The url for the agent to connect to (format host:port)
     * @throws java.net.MalformedURLException if url is not in correct format
     * @throws java.io.IOException if a connection error occurs
     * @throws javax.management.MalformedObjectNameException if the JMX object name is not correct
     */
    public AgentConnection(String url) throws MalformedURLException, IOException, MalformedObjectNameException {
        agenturl = url;
        objName = new ObjectName(objStrName);
    }

    public MBeanServerConnection getMBeanServerConnection() {
        return server;
    }

    public String getAgentURL() {
        return agenturl;
    }

    public void setActionListener(ActionListener listener) {
        actionListener = listener;
    }

    public String getFilesInfo() {
        return filesInfo.toString();
    }

    public void clearFilesInfo() {
        filesInfo = new StringBuilder();
    }

    public long getStartTime() {
        return starttime;
    }

    public long getEndTime() {
        return endtime;
    }

    void setUrl(String property) {
        agenturl = property;
    }

    public void handleNotification(Notification notification, java.lang.Object handback) {
        if (notification.getType().equals("ogaaction")) {
            if (handback instanceof String) {
                logger.fine("Notification type: '" + notification.getType() +
                        "', seq nr: '" + notification.getSequenceNumber() +
                        "', message: '" + notification.getMessage() +
                        "', user data: '" + notification.getUserData() + "'");
                logger.finest("Notification: '" + notification + "'");
                logger.finest("Notification: source: '" + notification.getSource() + "'");
            } else if (handback instanceof ObjectName) {
                ObjectName pingBean = (ObjectName) handback;
                logger.fine("Received notification from '" + pingBean + "'" +
                        " type: '" + notification.getType() +
                        "', seq nr: '" + notification.getSequenceNumber() +
                        "', message: '" + notification.getMessage() +
                        "', user data: '" + notification.getUserData() + "'");
                if (filesInfo.length() < FILESINFOMAX) {
                    //filesInfo.append(notification.getMessage());
                    filesInfo.append(notification.getUserData().toString());
                    if (actionListener != null) {
                        actionListener.actionPerformed(new ActionEvent(this,
                                ActionEvent.ACTION_PERFORMED, "OpenGrok fileevent"));
                    }
                }
            }
        } else if (notification.getType().equals("ogainfostring")) {
            if (handback instanceof String) {
                logger.finest("Notification type: '" + notification.getType() +
                        "', seq nr: '" + notification.getSequenceNumber() +
                        "', message: '" + notification.getMessage() +
                        "', user data: '" + notification.getUserData() + "'");
                logger.finest("Notification: '" + notification + "'");
                logger.finest("Notification: source: '" + notification.getSource() + "'");
            } else if (handback instanceof ObjectName) {
                ObjectName pingBean = (ObjectName) handback;
                logger.finest("Received notification from '" + pingBean + "'" +
                        " type: '" + notification.getType() +
                        "', seq nr: '" + notification.getSequenceNumber() +
                        "', message: '" + notification.getMessage() +
                        "', user data: '" + notification.getUserData() + "'");
            }
        } else if (notification.getType().equals("ogainfolong")) {
            if (handback instanceof String) {
                logger.finest("Notification type: '" + notification.getType() +
                        "', seq nr: '" + notification.getSequenceNumber() +
                        "', message: '" + notification.getMessage() +
                        "', user data: '" + notification.getUserData() + "'");
                logger.finest("Notification: '" + notification + "'");
                logger.finest("Notification: source: '" + notification.getSource() + "'");
            } else if (handback instanceof ObjectName) {
                ObjectName pingBean = (ObjectName) handback;
                logger.info("Received notification from '" + pingBean + "'" +
                        " type: '" + notification.getType() +
                        "', seq nr: '" + notification.getSequenceNumber() +
                        "', message: '" + notification.getMessage() +
                        "', user data: '" + notification.getUserData() + "'");
                if ("StartIndexing".equals(notification.getMessage())) {
                    starttime = ((Long) notification.getUserData()).longValue();
                } else if ("FinishedIndexing".equals(notification.getMessage())) {
                    endtime = ((Long) notification.getUserData()).longValue();
                } else {
                    logger.warning("Unknown message " + notification.getMessage());
                }
            }
        } else {
            logger.info("Notification: type: '" + notification.getType() + "', seq nr: '" + notification.getSequenceNumber() + "', message: '" + notification.getMessage() + "', user data: '" + notification.getUserData() + "'");
            logger.finest("Notification: '" + notification + "'");
            logger.finest("Notification: source: '" + notification.getSource() + "'");
        }
    }

    public void registerListener() throws IOException,
            InstanceNotFoundException {
        logger.fine("Registering listener: " + this.getClass().getName() + " for ObjectName: " + objName);
        server.addNotificationListener(objName, this, null, objName);
        logger.fine("Listener Registered");
        listenerRegistered = true;
    }

    public void reconnect(int retrytimes) throws MalformedURLException, IOException {
        logger.fine("Doing reconnect on '" + agenturl + "'");
        boolean notconnected = true;
        int triednumtimes = 0;
        while (notconnected) {
            triednumtimes++;
            if (jmxconn != null) {
                try {
                    jmxconn.close();
                } catch (Exception ex) {
                    logger.warning("Exception during close " + ex);
                }
            }
            try {
                connect(); 
                notconnected = false;
            } catch (MalformedURLException ex) {
                logger.log(Level.SEVERE, null, ex);
                throw ex;
            } catch (IOException ex) {
                logger.log(Level.SEVERE, null, ex);

                notconnected = true;

                if (triednumtimes <= retrytimes) {
                    logger.info("retry connect did try " + triednumtimes + ", retrying " +
                            retrytimes + " times, waiting " + RECONNECT_SLEEPTIME + " before" +
                            " next try.");
                    try {
                        Thread.sleep(RECONNECT_SLEEPTIME);
                    } catch (Exception sleepex) {
                        logger.finest("Thread.sleep exception " + sleepex);
                    }

                } else {
                    throw ex;
                }
            }

        }
    }

    protected void connect() throws MalformedURLException, IOException {
        String[] hostport = agenturl.split(":");
        int port = Integer.parseInt(hostport[1]);
        JMXServiceURL url = new JMXServiceURL("jmxmp", hostport[0], port);
        logger.info("jmx url " + url);
        HashMap<String, Object> env = new HashMap<String, Object>();
        jmxconn = JMXConnectorFactory.connect(url, env);
        logger.finer("jmx connect ok");
        server = jmxconn.getMBeanServerConnection();
        logger.info("JMX connection ok");
        connected = true;
    }

    public boolean isConnected() {
        return connected;
    }

    public void unregister() {
        if ((server != null) && (objName != null) && listenerRegistered) {
            try {
                logger.fine("Unregistering listener: " + this.getClass().getName() + " for ObjectName: " + objName);
                server.removeNotificationListener(objName, this, null, objName);
                listenerRegistered = false;
            } catch (Exception remnlex) {
                logger.severe("Exception unregister notif listener: '" + this.getClass().getName() + "' for ObjectName: " + objName + "'");
            }
        }

        try {

            if (jmxconn != null) {
                logger.fine("Closing connection");
                jmxconn.close();
                jmxconn = null;
                server = null;
            }
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Exception disconnecting " + e.getMessage(), e);
        }

    }
}

