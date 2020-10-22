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
 * Copyright (c) 2008, 2020, Oracle and/or its affiliates. All rights reserved.
 * Portions Copyright (c) 2017, 2020, Chris Fraire <cfraire@me.com>.
 * Portions Copyright (c) 2019, Chris Ross <cross@distal.com>.
 */
package org.opengrok.indexer.history;

import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.opengrok.indexer.condition.ConditionalRun;
import org.opengrok.indexer.condition.ConditionalRunRule;
import org.opengrok.indexer.condition.RepositoryInstalled;
import org.opengrok.indexer.configuration.RuntimeEnvironment;
import org.opengrok.indexer.util.FileUtilities;

import java.io.File;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.AbstractMap.SimpleImmutableEntry;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertEquals;
import static org.opengrok.indexer.history.PerforceRepository.protectPerforceFilename;
import static org.opengrok.indexer.history.PerforceRepository.unprotectPerforceFilename;

/**
 * Do basic testing of the Perforce support.
 * @author Trond Norbye
 */
public class PerforceRepositoryTest {

    @Rule
    public ConditionalRunRule rule = new ConditionalRunRule();

    private static boolean skip;
    private static List<File> files;
    private static final File root = new File("/var/opengrok/src/p4foo");

    @BeforeClass
    public static void setUpClass() {
        if (!root.exists()) {
            skip = true;
        } else {
            files = new ArrayList<>();
            RuntimeEnvironment env = RuntimeEnvironment.getInstance();
            RepositoryFactory.initializeIgnoredNames(env);
            FileUtilities.getAllFiles(root, files, false);
            env.setSourceRoot(root.getAbsolutePath());
        }
    }

    /**
     * Following are steps to set up for testing:
     * <p><ul>
     * <li>Install a Perforce server instance. I elected to install the
     * helix-p4d package on Ubuntu by following the instructions at
     * <a href="https://www.perforce.com/manuals/p4sag/Content/P4SAG/install.linux.packages.install.html">
     * Helix Core Server Administrator Guide &gt; Installing the server &gt; Linux
     * package-based installation &gt; Installation</a>.
     * <li>Configure the Perforce server. Follow the instructions at
     * <a href="https://www.perforce.com/manuals/p4sag/Content/P4SAG/install.linux.packages.configure.html">
     * Helix Core Server Administrator Guide &gt; Installing the server &gt; Linux
     * package-based installation &gt; Post-installation configuration</a>.
     * <li>Secure the Perforce server transport layer. I deployed a private key
     * and certificate following the instructions at
     * <a href="https://www.perforce.com/manuals/p4sag/Content/P4SAG/DB5-16618.html">
     * Helix Core Server Administrator Guide &gt; Securing the server &gt; Using SSL
     * to encrypt connections to a Helix server &gt; Key and certificate
     * management</a>.
     * <li>Define an authentication method for the Perforce server. I elected to
     * authenticate against my home Active Directory following the instructions
     * at <a href="https://www.perforce.com/manuals/p4sag/Content/P4SAG/security.ldap.auth.html">
     * Helix Core Server Administrator Guide &gt; Securing the server &gt; LDAP
     * authentication &gt; Authenticating against Active Directory and LDAP
     * servers</a> and then testing the LDAP configuration per
     * <a href="https://www.perforce.com/manuals/p4sag/Content/P4SAG/security.ldap.testing.html">
     * Helix Core Server Administrator Guide &gt; Securing the server &gt; LDAP
     * authentication &gt; Testing and enabling LDAP configurations</a>.
     * <li>Install Perforce on the development workstation. I used Homebrew to
     * install: {@code admin$ brew cask install perforce}
     * <li>Set environment to connect to the Perforce server. My server is named
     * p4: {@code export P4PORT=ssl:p4.localdomain:1666}
     * <li>Define a Perforce client view on the workstation. For a workstation
     * named workstation1: {@code cd /var/opengrok/src && p4 client workstation1}
     * <li>Add sample code and submit: {@code p4 add *.h && p4 submit}
     * <li>Add more sample code and submit: {@code p4 add *.c && p4 submit}
     * <li>Add more sample code and submit: {@code p4 add *.txt && p4 submit}
     * <li>Code, Index, Test, and Debug.
     * </ul><p>
     */
    @Test
    @ConditionalRun(RepositoryInstalled.PerforceInstalled.class)
    public void testHistoryAndAnnotations() throws Exception {
        if (skip) {
            return;
        }

        PerforceRepository instance = new PerforceRepository();
        instance.setDirectoryName(new File(root.getAbsolutePath()));

        for (File f : files) {
            if (instance.fileHasHistory(f)) {
                History history = instance.getHistory(f);
                assertNotNull("Failed to get history for: " + f.getAbsolutePath(), history);

                for (HistoryEntry entry : history.getHistoryEntries()) {
                    String revision = entry.getRevision();
                    InputStream in = instance.getHistoryGet(
                            f.getParent(), f.getName(), revision);
                    assertNotNull("Failed to get revision " + revision +
                            " of " + f.getAbsolutePath(), in);

                    if (instance.fileHasAnnotation(f)) {
                        assertNotNull(
                                "Failed to annotate: " + f.getAbsolutePath(),
                                instance.annotate(f, revision));
                    }
                }
            }
        }
    }

    @Test
    public void testProtectFilename() throws Exception {
        List<SimpleImmutableEntry<String, String>> testmap = new ArrayList<>();
        testmap.add(new SimpleImmutableEntry<>("Testfile 34", "Testfile 34"));
        testmap.add(new SimpleImmutableEntry<>("Test%52", "Test%2552"));
        testmap.add(new SimpleImmutableEntry<>("Test*4+2", "Test%2A4+2"));
        testmap.add(new SimpleImmutableEntry<>("Test@", "Test%40"));
        testmap.add(new SimpleImmutableEntry<>("@seventeen", "%40seventeen"));
        testmap.add(new SimpleImmutableEntry<>("upNdown(", "upNdown("));
        testmap.add(new SimpleImmutableEntry<>("tst#99", "tst%2399"));
        testmap.add(new SimpleImmutableEntry<>("#File*Three%trig", "%23File%2AThree%25trig"));
        testmap.add(new SimpleImmutableEntry<>("Two%and5#3#4", "Two%25and5%233%234"));

        for (SimpleImmutableEntry<String, String> ent : testmap) {
            String prot = protectPerforceFilename(ent.getKey());
            assertEquals("Improper protected filename, " + prot + " != " + ent.getValue(), ent.getValue(), prot);
        }
    }

    @Test
    public void testUnprotectFilename() throws Exception {
        List<SimpleImmutableEntry<String, String>> testmap = new ArrayList<>();
        testmap.add(new SimpleImmutableEntry<>("Testfile 34", "Testfile 34"));
        testmap.add(new SimpleImmutableEntry<>("Test%52", "Test%2552"));
        testmap.add(new SimpleImmutableEntry<>("Test*4+2", "Test%2A4+2"));
        testmap.add(new SimpleImmutableEntry<>("Test@", "Test%40"));
        testmap.add(new SimpleImmutableEntry<>("@seventeen", "%40seventeen"));
        testmap.add(new SimpleImmutableEntry<>("upNdown(", "upNdown("));
        testmap.add(new SimpleImmutableEntry<>("tst#99", "tst%2399"));
        testmap.add(new SimpleImmutableEntry<>("#File*Three%trig", "%23File%2AThree%25trig"));
        testmap.add(new SimpleImmutableEntry<>("Two%and5#3#4", "Two%25and5%233%234"));

        for (SimpleImmutableEntry<String, String> ent : testmap) {
            String u = unprotectPerforceFilename(ent.getValue());
            assertEquals("Bad unprotected filename for " + ent.getValue(), ent.getKey(), u);
        }
    }
}
