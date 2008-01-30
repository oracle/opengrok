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
package org.opensolaris.opengrok;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * Utility class to get information of the OpenGrok version.
 * 
 * @author Trond Norbye
 */
public class Info {
    private static final Properties properties = new Properties();

    static {
        try {
            InputStream in = Info.class.getResourceAsStream("info.properties");
            if (in != null) {
                properties.load(in);
            }
        } catch (IOException ioe) {
            throw new RuntimeException(ioe);
        }
    }

    public static String getVersion() {
        return "OpenGrok v" + properties.getProperty("version", "unknown");
    }
    
    public static String getRevision() {
        return properties.getProperty("changeset", "unknown");        
    }
    
    private Info() {
    }
}
