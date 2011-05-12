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
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.management.ListenerNotFoundException;
import javax.management.MBeanNotificationInfo;
import javax.management.MBeanRegistration;
import javax.management.MBeanServer;
import javax.management.Notification;
import javax.management.NotificationEmitter;
import javax.management.NotificationFilter;
import javax.management.NotificationListener;
import javax.management.ObjectName;
import org.opensolaris.opengrok.configuration.RuntimeEnvironment;
import org.opensolaris.opengrok.history.HistoryGuru;
import org.opensolaris.opengrok.index.IndexChangedListener;
import org.opensolaris.opengrok.index.Indexer;

/**
 * AgentIndexRunner.
 * @author Jan S Berg
 */
public final class AgentIndexRunner implements AgentIndexRunnerMBean, NotificationListener,
        MBeanRegistration, Runnable, IndexChangedListener, NotificationEmitter {

    private transient static AgentIndexRunner indexerInstance = null;
    private final static String NOTIFICATIONACTIONTYPE = "ogaaction";
    private final static String NOTIFICATIONEXCEPTIONTYPE = "ogaexception";
    private final static String NOTIFICATIONINFOSTRINGTYPE = "ogainfostring";
    private final static String NOTIFICATIONINFOLONGTYPE = "ogainfolong";
    private boolean enabled;
    private transient Thread indexThread = null;
    private final static Logger log = Logger.getLogger("org.opensolaris.opengrok");
    private RuntimeEnvironment env = null;
    private long lastIndexStart = 0;
    private long lastIndexFinish = 0;
    private long lastIndexUsedTime = 0;
    private Exception lastException = null;
    private final Set<NotificationHolder> notifListeners =
            new HashSet<NotificationHolder>();
    private static long sequenceNo = 0;
    private final StringBuilder notifications = new StringBuilder();
    private final static int MAXMESSAGELENGTH = 50000;

    /**
     * The only constructor is private, so other classes will only get an
     * instance through the static factory method getInstance().
     */
    private AgentIndexRunner(boolean enabledParam) {
        enabled = enabledParam;
    }

    /**
     * Static factory method to get an instance of AgentIndexRunner.
     * @param enabledParam if true, the initial instance should be running or not
     */
    @SuppressWarnings("PMD.AvoidSynchronizedAtMethodLevel")
    public static synchronized AgentIndexRunner getInstance(boolean enabledParam) {
        if (indexerInstance == null) {
            indexerInstance = new AgentIndexRunner(enabledParam);
        }
        return indexerInstance;
    }

    @Override
    public ObjectName preRegister(MBeanServer serverParam, ObjectName name) {
        return name;
    }

    @Override
    public void postRegister(Boolean registrationDone) {
        // not used
    }

    @Override
    public void preDeregister() {
        // not used
    }

    @Override
    public void postDeregister() {
        // not used
    }

    @Override
    public void run() {
        try {
            //Indexer ind = new Indexer();
            log.info("Running...");
            lastIndexStart = System.currentTimeMillis();
            lastException = null;
            doNotify(NOTIFICATIONINFOLONGTYPE, "StartIndexing", Long.valueOf(lastIndexStart));
            String configfile = Management.getInstance().getConfigurationFile();
            if (configfile == null) {
                doNotify(NOTIFICATIONEXCEPTIONTYPE, "Missing Configuration file", "");
            }
            File cfgFile = new File(configfile);
            if (cfgFile.exists()) {
                env = RuntimeEnvironment.getInstance();
                log.log(Level.INFO, "Running indexer with configuration {0}", configfile);
                env.readConfiguration(cfgFile);

                Indexer index = Indexer.getInstance();
                int noThreads = Management.getInstance().getNumberOfThreads().intValue();
                boolean update = Management.getInstance().getUpdateIndexDatabase().booleanValue();
                String[] sublist = Management.getInstance().getSubFiles();
                log.info("Update source repositories");
                HistoryGuru.getInstance().updateRepositories();
                List<String> subFiles = Arrays.asList(sublist);
                log.log(Level.INFO, "Starting index, update {0} noThreads {1} subfiles {2}", new Object[]{String.valueOf(update), String.valueOf(noThreads), String.valueOf(subFiles.size())});
                index.doIndexerExecution(update, noThreads, subFiles, this);
                log.info("Finished indexing");
                lastIndexFinish = System.currentTimeMillis();
                sendNotifications();
                doNotify(NOTIFICATIONINFOLONGTYPE, "FinishedIndexing", Long.valueOf(lastIndexFinish));
                lastIndexUsedTime = lastIndexFinish - lastIndexStart;
                String publishhost = Management.getInstance().getPublishServerURL();
                if ((publishhost == null) || (publishhost.equals(""))) {
                    log.warning("No publishhost given, not sending updates");
                } else {
                    index.sendToConfigHost(env, publishhost);
                    doNotify(NOTIFICATIONINFOSTRINGTYPE, "Published index", publishhost);
                }


            } else {
                log.log(Level.WARNING, "Cannot Run indexing without proper configuration file {0}", configfile);
                doNotify(NOTIFICATIONEXCEPTIONTYPE, "Configuration file not valid", configfile);
            }
        } catch (Exception e) {
            log.log(Level.SEVERE,
                    "Exception running indexing ", e);
            lastException = e;
        }
    }

    /**
     * Disables indexer
     */
    @Override
    public void disable() {
        enabled = false;
    }

    /**
     * Enables the indexer
     */
    @Override
    public void enable() {
        enabled = true;
    }

    /**
     * Handle timer notifications to the purgatory.
     * Will start the purger if it is enabled and return immediately.
     */
    @Override
    public void handleNotification(Notification n, Object hb) {
        if (n.getType().equals("timer.notification")) {
            log.finer("Received timer notification");
            if (enabled) {
                index(false);
            } else {
                log.info("Indexing is disabled, doing nothing");
            }
        } else {
            log.log(Level.WARNING, "Received unknown notification type: {0}", n.getType());
        }
    }

    /**
     * The index method starts a thread that will 
     * start indexing part of the opengrok agent.
     * @param waitForFinished if false the command returns immediately, if true
     * it will return when the indexing is done.
     */
    @Override
    public void index(boolean waitForFinished) {
        log.info("Starting indexing.");
        /*
         * Synchronize here to make sure that you never get more than one
         * indexing thread trying to start at the same time.
         */
        synchronized (this) {
            if (indexThread != null) {
                if (indexThread.isAlive()) {
                    log.warning("Previous indexer is still alive, will not start another.");
                    return;
                }
                log.fine("Previous indexer is no longer alive, starting a new one.");
            }
            indexThread = new Thread(this);
            try {
                indexThread.start();
                if (!waitForFinished) {
                    return;
                }
                log.fine("Waiting for indexer to finish...");
                indexThread.join();
                log.fine("indexer finished.");
            } catch (Exception e) {
                log.log(Level.SEVERE,
                        "Caught Exception while waiting for indexing to finish.", e);
            }
            return;
        }
    }

    @Override
    public void fileAdd(String path, String analyzer) {
        log.log(Level.INFO, "Add {0} analyzer {1}", new Object[]{path, analyzer});        
    }

    @Override
    public void fileRemove(String path) {
        log.log(Level.INFO, "File remove {0}", path);        
    }

    @Override
    public void fileUpdate(String path) {
        log.log(Level.INFO, "File updated {0}", path);
        addFileAction("U:", path);
    }


    @Override
    public void fileAdded(String path, String analyzer) {
        log.log(Level.INFO, "Added {0} analyzer {1}", new Object[]{path, analyzer});
        addFileAction("A:", path);
    }

    @Override
    public void fileRemoved(String path) {
        log.log(Level.INFO, "File removed {0}", path);
        addFileAction("R:", path);
    }

    private void addFileAction(String type, String path) {
        notifications.append('\n');
        notifications.append(type);
        notifications.append(path);
        if (notifications.length() > MAXMESSAGELENGTH) {
            sendNotifications();
        }
    }

    private void sendNotifications() {
        if (notifications.length() > 0) {
            doNotify(NOTIFICATIONACTIONTYPE, "FilesInfo", notifications.toString());
            notifications.delete(0, notifications.length());
        }
    }

    @Override
    public long lastIndexTimeFinished() {
        return lastIndexFinish;
    }

    @Override
    public long lastIndexTimeStarted() {
        return lastIndexStart;
    }

    @Override
    public long lastIndexTimeUsed() {
        return lastIndexUsedTime;
    }

    @Override
    public Exception getExceptions() {
        return lastException;
    }

    @Override
    public void addNotificationListener(NotificationListener notiflistener, NotificationFilter notfilt, Object obj) throws IllegalArgumentException {
        log.log(Level.CONFIG, "Adds a notiflistner, with obj {0}", obj.toString());
        if (notiflistener == null) {
            throw new IllegalArgumentException("Must have legal NotificationListener");
        }
        synchronized (notifListeners) {
            notifListeners.add(new NotificationHolder(notiflistener, notfilt, obj));
        }
    }

    @Override
    public void removeNotificationListener(NotificationListener notiflistener) throws ListenerNotFoundException {
        log.info("removes a notiflistener, no obj");
        boolean removed = false;
        synchronized (notifListeners) {
            Iterator<NotificationHolder> it = notifListeners.iterator();
            while (it.hasNext()) {
                NotificationHolder mnf = it.next();
                if (mnf.getNL().equals(notiflistener)) {
                    it.remove();
                    removed = true;
                }
            }
        }
        if (!removed) {
            throw new ListenerNotFoundException("Didn't remove the given NotificationListener");
        }
    }

    @Override
    public void removeNotificationListener(NotificationListener notiflistener, NotificationFilter filt, Object obj) throws ListenerNotFoundException {
        log.log(Level.CONFIG, "removes a notiflistener obj {0}", obj);
        boolean removed = false;
        synchronized (notifListeners) {
            Iterator<NotificationHolder> it = notifListeners.iterator();
            while (it.hasNext()) {
                NotificationHolder mnf = it.next();
                if (mnf.getNL().equals(notiflistener) 
                       && ((mnf.getFilter() == null) || mnf.getFilter().equals(filt)) 
                       && ((mnf.getFilter() == null) || mnf.getObj().equals(obj))) {
                            it.remove();
                            removed = true;
                }
            }
        }
        if (!removed) {
            throw new ListenerNotFoundException("Didn't remove the given NotificationListener");
        }
    }

    /**
     * Method that the subclass can override, but doesn't have to
     * @return MBeanNotificationInfo array of notification (and types) this class can emitt.
     */
    @Override
    public MBeanNotificationInfo[] getNotificationInfo() {
        MBeanNotificationInfo[] info = new MBeanNotificationInfo[1];
        String[] supptypes = {NOTIFICATIONACTIONTYPE, NOTIFICATIONINFOLONGTYPE, NOTIFICATIONINFOSTRINGTYPE};
        String name = "AgentIndexRunner";
        String descr = "OpenGrok Indexer Notifications";
        MBeanNotificationInfo minfo = new MBeanNotificationInfo(supptypes, name,
                descr);
        info[0] = minfo;
        return info;
    }

    private void doNotify(String type, String msg, Object userdata) {
        try {
            log.log(Level.CONFIG, "start notifying {0} listeners", notifListeners.size());
            long ts = System.currentTimeMillis();
            sequenceNo++;
            Notification notif = new Notification(type, this, sequenceNo, ts, msg);
            notif.setUserData(userdata);
            synchronized (notifListeners) {
                for (NotificationHolder nl : notifListeners) {
                    log.log(Level.FINE, "having one with obj {0}", nl.getObj());
                    try {
                        if ((nl.getFilter() == null) ||
                                nl.getFilter().isNotificationEnabled(notif)) {
                            nl.getNL().handleNotification(notif, nl.getObj());
                        }
                    } catch (Exception exnot) {
                        log.log(Level.WARNING, "Ex " + exnot, exnot);
                    }
                }
            }
        } catch (Exception ex) {
            log.log(Level.SEVERE,
                    "Exception during notification sending: " + ex.getMessage(),
                    ex);
        }
    }
} 
