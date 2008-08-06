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
 * Copyright 2005 Sun Microsystems, Inc.  All rights reserved.
 * Use is subject to license terms.
 */

/*
 * ident	"@(#)PrefixMatcher.java 1.1     05/11/11 SMI"
 */
/*
 * Matches a term against a prefix
 *
 */

package org.opensolaris.opengrok.search.context;

public class PrefixMatcher extends LineMatcher {
    private String prefix;
    public PrefixMatcher(String prefix) {
        this.prefix  = prefix;
    }
    
    public int match(String token) {
        if (token.startsWith(prefix))
            return MATCHED;
        return NOT_MATCHED;
    }
}
