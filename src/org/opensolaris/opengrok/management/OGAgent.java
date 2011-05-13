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
 * Copyright (c) 2008, 2010, Oracle and/or its affiliates. All rights reserved.
 */

package org.opensolaris.opengrok.management;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.rmi.registry.LocateRegistry;
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
import javax.management.remote.JMXConnectorServerFactory;
import javax.management.remote.JMXServiceURL;
import javax.management.timer.Timer;
import org.opensolaris.opengrok.Info;
import org.opensolaris.opengrok.OpenGrokLogger;

// PMD thinks this import is unused (confused because it's static?)
import org.opensolaris.opengrok.util.IOUtils;
import static org.opensolaris.opengrok.management.Constants.*; // NOPMD

/**
 * OG Agent main class.
 * Class for starting the basic components:
 * Monitor and JMX and HTTP Connectors.
 * @author Jan S Berg
 */
final public class OGAgent {
    Properties props;

    private final static Logger log = Logger.getLogger("org.opensolaris.opengrok");
    private MBeanServer server = null;


    @SuppressWarnings("PMD.SystemPrintln")
    private static boolean loadProperties(File file, InputStream in, Properties props) {
        boolean ret = false;
        InputStream stream = in;
        try {
            if (file != null) {
                stream = new FileInputStream(file);
            }
            props.load(stream);
            ret = true;
        } catch (IOException e) {
            System.err.println("Failed to read configuration");
            e.printStackTrace(System.err);
            ret = false;
        } finally {
            IOUtils.close(stream);
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
                ex.printStackTrace(System.err);
            }
        }

        if (success) {
            setIfNotSet(props, LOG_PATH, uri.getPath() + "/log");

            setIfNotSet(props, CONFIG_FILE,
                    uri.getPath() + "/etc/configuration.xml");

            setIfNotSet(props, JMX_HOST, uri.getHost());

            setIfNotSet(props, JMX_PORT, String.valueOf(uri.getPort()));

            setIfNotSet(props, RMI_PORT, String.valueOf(uri.getPort() + 1));

            setIfNotSet(props, JMX_URL,
                    "service:jmx:rmi://" + props.getProperty(JMX_HOST) + ":" +
                    props.getProperty(JMX_PORT) + "/jndi/rmi://" +
                    props.getProperty(JMX_HOST) + ":" +
                    props.getProperty(RMI_PORT) + "/opengrok");

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

    /**
     * Set a property if it is not already set to some value.
     */
    private static void setIfNotSet(Properties props, String key, String val) {
        if (!props.keySet().contains(key)) {
            props.setProperty(key, val);
        }
    }

    public void runOGA() throws MalformedURLException, IOException, JMException {
        String javaver = System.getProperty("java.version");


        log.info("Starting " + Info.getFullVersion() +
                " JMX Agent, with java version " + javaver);
        //create mbeanserver

        ArrayList<MBeanServer> mbservs = MBeanServerFactory.findMBeanServer(null);
        log.fine("Finding MBeanservers, size " + mbservs.size());
        if (mbservs.isEmpty()) {
            server = MBeanServerFactory.createMBeanServer();
        } else {
            server = mbservs.get(0);
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
        String urlString = props.getProperty(JMX_URL);
        HashMap<String, Object> env = new HashMap<String, Object>();
        JMXServiceURL url = new JMXServiceURL(urlString);

        // If the protocol is RMI we need to have an RMI registry running.
        // Start an embedded registry if so requested.
        if (url.getProtocol().equals(RMI_PROTOCOL) &&
                Boolean.parseBoolean(props.getProperty(RMI_START))) {
            int rmiport = Integer.parseInt(props.getProperty(RMI_PORT));
            log.log(Level.FINE, "Starting RMI registry on port {0}", rmiport);
            LocateRegistry.createRegistry(rmiport);
        }

        log.log(Level.FINE, "Starting JMX connector on {0}", urlString);
        JMXConnectorServer connectorServer =
                JMXConnectorServerFactory.newJMXConnectorServer(url, env, server);

        connectorServer.start();

        log.info("OGA is ready and running...");
    }

    private void createIndexTimer(Properties properties) throws JMException {

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
