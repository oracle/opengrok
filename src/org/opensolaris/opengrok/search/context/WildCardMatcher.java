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
 * ident	"@(#)WildCardMatcher.java 1.1     05/11/11 SMI"
 */

package org.opensolaris.opengrok.search.context;

import org.apache.lucene.search.WildcardTermEnum;

public class WildCardMatcher extends LineMatcher {
    String pattern = "";
    String pre = "";
    int preLen = 0;
    
    public WildCardMatcher(String pattern) {
        this.pattern = pattern;
        int sidx = pattern.indexOf(WildcardTermEnum.WILDCARD_STRING);
        int cidx = pattern.indexOf(WildcardTermEnum.WILDCARD_CHAR);
        int idx = sidx;
        if (idx == -1) {
            idx = cidx;
        } else if (cidx >= 0) {
            idx = Math.min(idx, cidx);
        }
        pre = pattern.substring(0,idx);
        preLen = pre.length();
        this.pattern = pattern.substring(preLen);
    }
    
    public int match(String token) {
        if(token.startsWith(pre) && WildcardTermEnum.wildcardEquals(pattern, 0, token, 0)) {
            return MATCHED;
        }
        return NOT_MATCHED;
    }
}
