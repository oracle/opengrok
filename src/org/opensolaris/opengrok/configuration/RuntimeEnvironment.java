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
import java.io.InputStreamReader;
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
import org.opensolaris.opengrok.index.IgnoredNames;

/**
 * The RuntimeEnvironment class is used as a placeholder for the current
 * configuration this execution context (classloader) is using.
 */
public class RuntimeEnvironment {
    private Configuration configuration;
    private Map<Thread, Configuration> threadmap;
    
    private static RuntimeEnvironment instance = new RuntimeEnvironment();
    
    /**
     * Get the one and only instance of the RuntimeEnvironment
     * @return the one and only instance of the RuntimeEnvironment
     */
    public static RuntimeEnvironment getInstance() {
        return instance;
    }
    
    /**
     * Creates a new instance of RuntimeEnvironment. Private to ensure a
     * singleton pattern.
     */
    private RuntimeEnvironment() {
        configuration = new Configuration();
        threadmap = Collections.synchronizedMap(new HashMap<Thread, Configuration>());
    }
    
    private Configuration getConfiguration() {
        Configuration ret = configuration;
        Configuration config = threadmap.get(Thread.currentThread());

        if (config != null) {
            ret = config;
        }
        
        return ret;
    }
    
    /**
     * Get the path to the where the index database is stored
     * @return the path to the index database
     */
    public String getDataRootPath() {
        return getConfiguration().getDataRoot();
    }
    
    /**
     * Get a file representing the index database
     * @return the index database
     */
    public File getDataRootFile() {
        File ret  = null;
        String file = getDataRootPath();
        if (file != null) {
            ret = new File(file);
        }
        
        return ret;
    }
    
    /**
     * Set the path to where the index database is stored
     * @param data the index database
     * @throws IOException if the path cannot be resolved
     */
    public void setDataRoot(File data) throws IOException {
        getConfiguration().setDataRoot(data.getCanonicalPath());
    }
    
    /**
     * Set the path to where the index database is stored
     * @param dataRoot the index database
     */
    public void setDataRoot(String dataRoot) {
        getConfiguration().setDataRoot(dataRoot);
    }
    
    /**
     * Get the path to where the sources are located
     * @return path to where the sources are located
     */
    public String getSourceRootPath() {
        return getConfiguration().getSourceRoot();
    }
    
    /**
     * Get a file representing the directory where the sources are located
     * @return A file representing the directory where the sources are located
     */
    public File getSourceRootFile() {
        File ret  = null;
        String file = getSourceRootPath();
        if (file != null) {
            ret = new File(file);
        }
        
        return ret;
    }
    
    /**
     * Specify the source root
     * @param source the location of the sources
     * @throws IOException if the name cannot be resolved
     */
    public void setSourceRoot(File source) throws IOException {
        getConfiguration().setSourceRoot(source.getCanonicalPath());
    }
    
    /**
     * Specify the source root
     * @param sourceRoot the location of the sources
     */
    public void setSourceRoot(String sourceRoot) {
        getConfiguration().setSourceRoot(sourceRoot);
    }
    
    /**
     * Do we have projects?
     * @return true if we have projects
     */
    public boolean hasProjects() {
        List<Project> proj = getProjects();
        return (proj != null && proj.size() > 0);
    }
    
    /**
     * Get all of the projects
     * @return a list containing all of the projects (may be null)
     */
    public List<Project> getProjects() {
        return getConfiguration().getProjects();
    }
    
    /**
     * Set the list of the projects
     * @param projects the list of projects to use
     */
    public void setProjects(List<Project> projects) {
        getConfiguration().setProjects(projects);
    }
    
    /**
     * Register this thread in the thread/configuration map (so that all
     * subsequent calls to the RuntimeEnvironment from this thread will use
     * the same configuration
     */
    public void register() {
        threadmap.put(Thread.currentThread(), configuration);
    }
    
    /**
     * Get the context name of the web application
     * @return the web applications context name
     */
    public String getUrlPrefix() {
        return getConfiguration().getUrlPrefix();
    }
    
    /**
     * Set the web context name
     * @param urlPrefix the web applications context name
     */
    public void setUrlPrefix(String urlPrefix) {
        getConfiguration().setUrlPrefix(urlPrefix);
    }
    
    /**
     * Get the name of the ctags program in use
     * @return the name of the ctags program in use
     */
    public String getCtags() {
        return getConfiguration().getCtags();
    }
    
    /**
     * Specify the CTags program to use
     * @param ctags the ctags program to use
     */
    public void setCtags(String ctags) {
        getConfiguration().setCtags(ctags);
    }

    /**
     * Validate that I have a Exuberant ctags program I may use
     * @return true if success, false otherwise
     */
    public boolean validateExuberantCtags() {
        String ctags = getCtags();
                
        //Check if exub ctags is available
        Process ctagsProcess = null;
        try {
            ctagsProcess = Runtime.getRuntime().exec(new String[] {ctags, "--version" });
        } catch (Exception e) {
        }
        try {
            BufferedReader cin = new BufferedReader(new InputStreamReader(ctagsProcess.getInputStream()));
            String ctagOut;
            if (!((ctagOut = cin.readLine()) != null && ctagOut.startsWith("Exuberant Ctags"))) {
                System.err.println("Error: No Exuberant Ctags found in PATH!\n" +
                        "(tried running " + ctags + ")\n" +
                        "Please use option -c to specify path to a good Exuberant Ctags program");
                return false;
            }
        } catch (Exception e) {
            System.err.println("Error: executing " + ctags + "! " +e.getLocalizedMessage() +
                    "\nPlease use option -c to specify path to a good Exuberant Ctags program");
            return false;
        }
        
        // reap the child process..
        try {
            int ret;
            if ((ret = ctagsProcess.exitValue()) != 0) {
                System.err.println("Error: ctags returned " + ret);
                return false;
            }            
        } catch (IllegalThreadStateException exp) {
            // the process is still running??? just kill it..
            ctagsProcess.destroy();
            return true;
        }        
        return true;
    }
        
    /**
     * Get the max time a SMC operation may use to avoid beeing cached
     * @return the max time
     */
    public int getHistoryReaderTimeLimit() {
        return getConfiguration().getHistoryCacheTime();
    }
    
    /**
     * Specify the maximum time a SCM operation should take before it will
     * be cached (in ms)
     * @param historyReaderTimeLimit the max time in ms before it is cached
     */
    public void setHistoryReaderTimeLimit(int historyReaderTimeLimit) {
        getConfiguration().setHistoryCacheTime(historyReaderTimeLimit);
    }
    
    /**
     * Is history cache currently enabled?
     * @return true if history cache is enabled
     */
    public boolean useHistoryCache() {
        return getConfiguration().isHistoryCache();
    }
    
    /**
     * Specify if we should use history cache or not
     * @param useHistoryCache set false if you do not want to use history cache
     */
    public void setUseHistoryCache(boolean useHistoryCache) {
        getConfiguration().setHistoryCache(useHistoryCache);
    }
    
    /**
     * Should we generate HTML or not during the indexing phase
     * @return true if HTML should be generated during the indexing phase
     */
    public boolean isGenerateHtml() {
        return getConfiguration().isGenerateHtml();
    }
    
    /**
     * Specify if we should generate HTML or not during the indexing phase
     * @param generateHtml set this to true to pregenerate HTML
     */
    public void setGenerateHtml(boolean generateHtml) {
        getConfiguration().setGenerateHtml(generateHtml);
    }
    
    public boolean isQuickContextScan() {
        return getConfiguration().isQuickContextScan();
    }

    public void setQuickContextScan(boolean quickContextScan) {
        getConfiguration().setQuickContextScan(quickContextScan);
    }

    /**
     * Get the map of external SCM repositories available
     * @return A map containing all available SCMs
     */
    public Map<String, ExternalRepository> getRepositories() {
        return getConfiguration().getRepositories();
    }
    
    /**
     * Set the map of external SCM repositories
     * @param repositories the repositories to use
     */
    public void setRepositories(Map<String, ExternalRepository> repositories) {
        getConfiguration().setRepositories(repositories);
    }
    
    /**
     * Set the project that is specified to be the default project to use. The
     * default project is the project you will search (from the web application)
     * if the page request didn't contain the cookie..
     * @param defaultProject The default project to use
     */
    public void setDefaultProject(Project defaultProject) {
        getConfiguration().setDefaultProject(defaultProject);
    }
    
    /**
     * Get the project that is specified to be the default project to use. The
     * default project is the project you will search (from the web application)
     * if the page request didn't contain the cookie..
     * @return the default project (may be null if not specified)
     */
    public Project getDefaultProject() {
        return getConfiguration().getDefaultProject();
    }
    
    /**
     * Chandan wrote the following answer on the opengrok-discuss list:
     * "Traditionally search engines (specially spiders) think that large files
     * are junk. Large files tend to be multimedia files etc., which text
     * search spiders do not want to chew. So they ignore the contents of 
     * the file after a cutoff length. Lucene does this by number of words,
     * which is by default is 10,000."
     * By default OpenGrok will increase this limit to 60000, but it may be
     * overridden in the configuration file
     * @return The maximum words to index
     */
    public int getIndexWordLimit() {
        return getConfiguration().getIndexWordLimit();
    }

    public void setIndexWordLimit(int indexWordLimit) {
        getConfiguration().setIndexWordLimit(indexWordLimit);
    }
    
    public boolean isVerbose() {
        return getConfiguration().isVerbose();
    }

    public void setVerbose(boolean verbose) {
        getConfiguration().setVerbose(verbose);
    }
    
    public IgnoredNames getIgnoredNames() {
        return getConfiguration().getIgnoredNames();
    }

    public void setIgnoredNames(IgnoredNames ignoredNames) {
        getConfiguration().setIgnoredNames(ignoredNames);
    }
    
    /**
     * Read an configuration file and set it as the current configuration.
     * @param file the file to read
     * @throws IOException if an error occurs
     */
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
    
    /**
     * Write the current configuration to a file
     * @param file the file to write the configuration into
     * @throws IOException if an error occurs
     */
    public void writeConfiguration(File file) throws IOException {
        XMLEncoder e = new XMLEncoder(
                new BufferedOutputStream(new FileOutputStream(file)));
        e.writeObject(getConfiguration());
        e.close();
    }
    
    /**
     * Write the current configuration to a socket
     * @param host the host address to receive the configuration
     * @param port the port to use on the host
     * @throws IOException if an error occurs
     */
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
    
    /**
     * Try to stop the configuration listener thread
     */
    public void stopConfigurationListenerThread() {
        try {
            configServerSocket.close();
        } catch (Exception e) {}
    }
    
    /**
     * Start a thread to listen on a socket to receive new configurations
     * to use.
     * @param endpoint The socket address to listen on
     * @return true if the endpoint was available (and the thread was started)
     */
    public boolean startConfigurationListenerThread(SocketAddress endpoint) {
        boolean ret = false;
        
        try {
            configServerSocket = new ServerSocket();
            configServerSocket.bind(endpoint);
            ret = true;
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
