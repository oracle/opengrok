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
 * Copyright 2008 Sun Microsystems, Inc.  All rights reserved.
 * Use is subject to license terms.
 */
package org.opensolaris.opengrok.management;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.management.JMException;
import javax.management.MBeanServer;
import javax.management.MBeanServerFactory;
import javax.management.NotificationFilter;
import javax.management.ObjectName;
import javax.management.remote.JMXConnectorServer;
import javax.management.remote.JMXServiceURL;
import javax.management.remote.jmxmp.JMXMPConnectorServer;
import javax.management.remote.rmi.RMIConnectorServer;
import javax.management.timer.Timer;
import org.opensolaris.opengrok.Info;
import org.opensolaris.opengrok.OpenGrokLogger;

/**
 * OG Agent main class.
 * Class for starting the basic components:
 * Monitor and JMX and HTTP Connectors.
 * @author Jan S Berg
 */
public class OGAgent {
    Properties props;

    private final static Logger log = Logger.getLogger("org.opensolaris.opengrok");
    private MBeanServer server = null;


    @SuppressWarnings("PMD.SystemPrintln")
    private static boolean loadProperties(File file, InputStream in, Properties props) {
        boolean ret = false;
        try {
            if (file != null) {
                in = new FileInputStream(file);
            }
            props.load(in);
            ret = true;
        } catch (IOException e) {
            System.err.println("Failed to read configuration");
            e.printStackTrace();
            ret = false;
        } finally {
            try {
                in.close();
            } catch (IOException e) {
                System.err.println("Failed to close stream");
                e.printStackTrace();
                ret = false;
            }
        }
        return ret;
    }

    @SuppressWarnings("PMD.SystemPrintln")
    public static void main(final String args[]) {

        Properties props = new Properties();
        // Load default values
        boolean success = loadProperties(null, OGAgent.class.getResourceAsStream("oga.properties"), props);

        File file = new File("/etc/opengrok/opengrok.properties");
        if (file.exists()) {
            success = loadProperties(file, null, props);
        }

        // System properties should override default properties
        props.putAll(System.getProperties());

        // @todo Add support for longopts!!
        for (int i = 0; success && i < args.length; i++) {
            if (args[i].startsWith("--agent=")) {
                props.setProperty("agent", args[i].substring("--agent=".length()));
            } else if (args[i].startsWith("--config=")) {
                file = new File(args[i].substring("--config=".length()));
                if (file.exists()) {
                    success = loadProperties(file, null, props);
                } else {
                    success = false;
                    System.err.println("Cannot load file \"" + file.getAbsolutePath() + "\": No such file");
                }
            }
        }

        URI uri = null;
        if (success) {
            try {
                uri = new URI(props.getProperty("agent"));
            } catch (URISyntaxException ex) {
                success = false;
                System.err.println("Failed to decode agent url");
                ex.printStackTrace();
            }
        }

        if (success) {
            if (props.getProperty("org.opensolaris.opengrok.management.logging.path") == null) {
                props.setProperty("org.opensolaris.opengrok.management.logging.path",
                        uri.getPath());
            }

            if (props.getProperty("org.opensolaris.opengrok.management.connection.host") == null) {
                props.setProperty("org.opensolaris.opengrok.management.connection.host",
                        uri.getHost());
            }

            if (props.getProperty("org.opensolaris.opengrok.management.connection.port") == null) {
                props.setProperty("org.opensolaris.opengrok.management.connection.port",
                        Integer.toString(uri.getPort()));
            }

            success = createLogger(props);
        }

        if (success) {

            OGAgent oga = new OGAgent(props);
            try {
                oga.runOGA();
            } catch (MalformedURLException e) {
                log.log(Level.SEVERE, "Could not create connector server: " + e, e);
                System.exit(1);
            } catch (IOException e) {
                log.log(Level.SEVERE, "Could not start connector server: " + e, e);
                System.exit(2);
            } catch (Exception ex) {
                Logger.getLogger(OGAgent.class.getName()).log(Level.SEVERE, null, ex);
                System.exit(1);
            }
        } else {
            System.exit(1);
        }
    }

    private OGAgent(Properties props) {
        this.props = props;
    }

    public final void runOGA() throws MalformedURLException, IOException, JMException {
        String machinename = java.net.InetAddress.getLocalHost().getHostName();
        String javaver = System.getProperty("java.version");


        log.info("Starting " + Info.getFullVersion() +
                " JMX Agent, with java version " + javaver);
        //create mbeanserver

        String connprotocol = props.getProperty("org.opensolaris.opengrok.management.connection.protocol", "jmxmp");
        int connectorport = Integer.parseInt(props.getProperty("org.opensolaris.opengrok.management.connection.port"));
        log.fine("Using protocol " + connprotocol + ", port: " + connectorport);

        ArrayList mbservs = MBeanServerFactory.findMBeanServer(null);
        log.fine("Finding MBeanservers, size " + mbservs.size());
        if (mbservs.isEmpty()) {
            server = MBeanServerFactory.createMBeanServer();
        } else {
            server = (MBeanServer) mbservs.get(0);
        }

        //instantiate and register OGAManagement
        ObjectName manager = new ObjectName("OGA:name=Management");
        server.registerMBean(Management.getInstance(props), manager);

        //instantiate and register OGA:JMXConfiguration
        ObjectName config = new ObjectName("OGA:name=JMXConfiguration");
        JMXConfiguration jc = new JMXConfiguration();
        server.registerMBean(jc, config);

        //instantiate and register Timer service and resource purger
        createIndexTimer(props);
        log.info("MBeans registered");

        // Create and start connector server
        log.fine("Starting JMX connector");
        HashMap<String, Object> env = new HashMap<String, Object>();
        JMXServiceURL url = new JMXServiceURL(connprotocol, machinename, connectorport);
        JMXConnectorServer connectorServer = null;

        if ("jmxmp".equals(connprotocol)) {
            connectorServer = new JMXMPConnectorServer(url, env, server);
        } else if ("rmi".equals(connprotocol) || "iiop".equals(connprotocol)) {
            connectorServer = new RMIConnectorServer(url, env, server);
        } else {
            throw new IOException("Unknown connector protocol");
        }
        connectorServer.start();

        log.info("OGA is ready and running...");
    }

    private void createIndexTimer(Properties properties) throws IOException, JMException {

        //instantiate, register and start the Timer service
        ObjectName timer = new ObjectName("service:name=timer");
        server.registerMBean(new Timer(), timer);
        server.invoke(timer, "start", null, null);
        log.info("Started timer service");

        boolean enabled = Boolean.parseBoolean(properties.getProperty("org.opensolaris.opengrok.management.indexer.enabled"));
        int period = Integer.parseInt(properties.getProperty("org.opensolaris.opengrok.management.indexer.sleeptime"));
        log.fine("Indexer enabled: " + enabled);
        log.fine("Indexer period: " + period + " seconds");
        //instantiate and register resource purger
        ObjectName indexRunner = new ObjectName("OGA:name=AgentIndexRunner," + "source=timer");
        server.registerMBean(AgentIndexRunner.getInstance(enabled), indexRunner);
        // Add index notification to timer (read from org.opensolaris.opengrok.management.indexer.sleeptime property).
        Date date = new Date(System.currentTimeMillis() + Timer.ONE_SECOND * 5);
        Long longPeriod = Long.valueOf(period * Timer.ONE_SECOND);
        Integer id = (Integer) server.invoke(timer, "addNotification",
                new Object[]{"timer.notification", // Type
                    "Time to index again", // Message
                    null, // user data
                    date, // Start time
                    longPeriod, // Period
                },
                new String[]{String.class.getName(),
                    String.class.getName(),
                    Object.class.getName(),
                    Date.class.getName(),
                    "long",});

        // Add indexer as listener to index notifications
        NotificationFilter filter = new TimerFilter(id);
        server.addNotificationListener(timer, indexRunner, filter, null);
    }

    @SuppressWarnings("PMD.SystemPrintln")
    private static boolean createLogger(Properties props) {
        boolean ret = true;
        String OGAlogpath = props.getProperty("org.opensolaris.opengrok.management.logging.path");

        Level loglevel = null;
        try {
            loglevel = Level.parse(props.getProperty("org.opensolaris.opengrok.management.logging.filelevel"));
        } catch (Exception exll) {
            loglevel = Level.FINE;
        }
        Level consoleloglevel = null;
        try {
            consoleloglevel = Level.parse(props.getProperty("org.opensolaris.opengrok.management.logging.consolelevel"));
        } catch (Exception excl) {
            consoleloglevel = Level.INFO;
        }
        try {
            OpenGrokLogger.setupLogger(OGAlogpath, loglevel, consoleloglevel);
        } catch (IOException ex) {
            System.err.println("OGAgent failed set up logging: " + ex);
            ret = false;
        }

        return ret;
    }
}
