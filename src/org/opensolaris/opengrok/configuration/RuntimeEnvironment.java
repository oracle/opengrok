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
package org.opensolaris.opengrok.configuration;

import java.beans.XMLDecoder;
import java.beans.XMLEncoder;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.UnknownHostException;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.opensolaris.opengrok.history.ExternalRepository;

/**
 * The RuntimeEnvironment class is used as a placeholder for the current
 * configuration this execution context (classloader) is using.
 */
public class RuntimeEnvironment {
    private boolean threadLocalConfig;
    
    private Configuration configuration;
    private Map<Thread, Configuration> threadmap;
    
    private static RuntimeEnvironment instance = new RuntimeEnvironment();
    
    public static RuntimeEnvironment getInstance() {
        return instance;
    }
    
    /**
     * Creates a new instance of RuntimeEnvironment
     */
    private RuntimeEnvironment() {
        threadLocalConfig = false;
        configuration = new Configuration();
        threadmap = Collections.synchronizedMap(new HashMap<Thread, Configuration>());
    }
    
    private Configuration getConfiguration() {
        Configuration ret = configuration;
        
        if (threadLocalConfig) {
            Configuration config = threadmap.get(Thread.currentThread());
            
            if (config != null) {
                ret = config;
            }
        }
        
        return ret;
    }
    
    public String getDataRootPath() {
        return getConfiguration().getDataRoot();
    }
    
    public String getSourceRootPath() {
        return getConfiguration().getSourceRoot();
    }
    
    public File getDataRootFile() {
        File ret  = null;
        String file = getDataRootPath();
        if (file != null) {
            ret = new File(file);
        }
        
        return ret;
    }
    
    public File getSourceRootFile() {
        File ret  = null;
        String file = getSourceRootPath();
        if (file != null) {
            ret = new File(file);
        }
        
        return ret;
    }
    
    public void setDataRoot(File data) throws IOException {
        getConfiguration().setDataRoot(data.getCanonicalPath());
    }
    
    public void setDataRoot(String dataRoot) {
        getConfiguration().setDataRoot(dataRoot);
    }
    
    public void setSourceRoot(File source) throws IOException {
        getConfiguration().setSourceRoot(source.getCanonicalPath());
    }
    
    public void setSourceRoot(String sourceRoot) {
        getConfiguration().setSourceRoot(sourceRoot);
    }
    
    public boolean hasProjects() {
        List<Project> proj = getProjects();
        return (proj != null && proj.size() > 0);
    }
    
    public List<Project> getProjects() {
        return getConfiguration().getProjects();
    }
    
    public void setProjects(List<Project> projects) {
        getConfiguration().setProjects(projects);
    }
    
    public void register() {
        threadmap.put(Thread.currentThread(), configuration);
    }
    
    public String getUrlPrefix() {
        return getConfiguration().getUrlPrefix();
    }
    
    public void setUrlPrefix(String urlPrefix) {
        getConfiguration().setUrlPrefix(urlPrefix);
    }
    
    public String getCtags() {
        return getConfiguration().getCtags();
    }
    
    public void setCtags(String ctags) {
        getConfiguration().setCtags(ctags);
    }
    
    public int getHistoryReaderTimeLimit() {
        return getConfiguration().getHistoryCacheTime();
    }
    
    public void setHistoryReaderTimeLimit(int historyReaderTimeLimit) {
        getConfiguration().setHistoryCacheTime(historyReaderTimeLimit);
    }
    
    public boolean useHistoryCache() {
        return getConfiguration().isHistoryCache();
    }
    
    public void setUseHistoryCache(boolean useHistoryCache) {
        getConfiguration().setHistoryCache(useHistoryCache);
    }
    
    public void setThreadLocalConfiguration(boolean tls) {
        threadLocalConfig = tls;
    }
    
    public Map<String, ExternalRepository> getRepositories() {
        return getConfiguration().getRepositories();
    }
    
    public void setRepositories(Map<String, ExternalRepository> repositories) {
        getConfiguration().setRepositories(repositories);
    }
    
    public Map<Thread, Configuration> getThreadmap() {
        return threadmap;
    }
    
    public void readConfiguration(File file) throws IOException {
        XMLDecoder d = new XMLDecoder(
                new BufferedInputStream(new FileInputStream(file)));
        Object obj = d.readObject();
        d.close();
        
        if (obj instanceof Configuration) {
            configuration = (Configuration)obj;
            System.out.println("Config file " + file.getName() + " successfully read");
        } else {
            throw new IOException("Not a valid config file");
        }
    }
    
    public void writeConfiguration(File file) throws IOException {
        XMLEncoder e = new XMLEncoder(
                new BufferedOutputStream(new FileOutputStream(file)));
        e.writeObject(getConfiguration());
        e.close();
    }
    
    public void writeConfiguration(InetAddress host, int port) throws IOException {
        Socket sock = new Socket(host, port);
        XMLEncoder e = new XMLEncoder(sock.getOutputStream());
        e.writeObject(getConfiguration());
        e.close();
        try {
            sock.close();
        } catch (Exception ex) {
            ;
        }
    }
    
    private Thread configurationListenerThread;
    private ServerSocket configServerSocket;
    
    public void stopConfigurationListenerThread() {
        try {
            configServerSocket.close();
        } catch (Exception e) {}        
    }

    public boolean startConfigurationListenerThread(SocketAddress endpoint) {
        boolean ret = false;
        
        try {
            configServerSocket = new ServerSocket();
            configServerSocket.bind(endpoint);
            ret = true;
            threadLocalConfig = true;
            final ServerSocket sock = configServerSocket;
            Thread t = new Thread(new Runnable() {
                public void run() {
                    Socket s = null;
                    while (!sock.isClosed()) {
                        try {
                            System.out.flush();
                            s = sock.accept();
                            System.out.println((new Date()).toString() + " OpenGrok: Got request from " + s.getInetAddress().getHostAddress());
                            String line;
                            BufferedInputStream in = new BufferedInputStream(s.getInputStream());
                            
                            XMLDecoder d = new XMLDecoder(new BufferedInputStream(in));
                            Object obj = d.readObject();
                            d.close();
                            
                            if (obj instanceof Configuration) {
                                configuration = (Configuration)obj;
                                System.out.println("Configuration updated: " + configuration.getSourceRoot());
                                System.out.flush();
                            }
                        } catch (IOException e) {
                            e.printStackTrace();
                        } finally {
                            try { s.close(); } catch (Exception ex) { }
                        }
                    }
                }
            });
            t.start();
        } catch (UnknownHostException ex) {
            ex.printStackTrace();
        } catch (IOException ex) {
            ex.printStackTrace();
        }
        
        if (!ret && configServerSocket != null) {
            try {
                configServerSocket.close();
            } catch (IOException ex) {
                ;
            }
        }
        
        return ret;
    }
}
