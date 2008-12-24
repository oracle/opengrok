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
import java.io.IOException;
import java.util.logging.Level;
import org.opensolaris.opengrok.OpenGrokLogger;
import org.opensolaris.opengrok.configuration.Configuration;
import org.opensolaris.opengrok.configuration.RuntimeEnvironment;

/**
 *
 * @author Jan Berg
 */
public class JMXConfiguration implements JMXConfigurationMBean {

    public String getConfiguration() {
        return RuntimeEnvironment.getInstance().getConfiguration().getXMLRepresentationAsString();
    }

    @SuppressWarnings({"PMD.CollapsibleIfStatements", "PMD.PreserveStackTrace"})
    public void setConfiguration(String config) throws IOException {
        Configuration configuration = Configuration.makeXMLStringAsConfiguration(config);
        RuntimeEnvironment.getInstance().setConfiguration(configuration);
        //write it to file as well
        String configfile = Management.getInstance().getConfigurationFile();
        try  {
            File file = new File(configfile);
            if (!file.exists()) {
                if (!file.createNewFile()) {
                    throw new IOException("Could not create configuration file: '" + configfile + "'");
                }
            }
            RuntimeEnvironment.getInstance().writeConfiguration(file);
        } catch (IOException orig) {
            IOException ioex = new IOException("Could not create configuration file " + configfile);
            ioex.initCause(orig);
            OpenGrokLogger.getLogger().log(Level.SEVERE,"Could not create configfile " + configfile, ioex);
            throw ioex;
        }
    }
}
