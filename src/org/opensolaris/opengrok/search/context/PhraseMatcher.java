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
package org.opensolaris.opengrok.search.context;

/**
 * Matches a term against a set of tokens
 *
 */
public class PhraseMatcher extends LineMatcher {
    private String[] phraseTerms;
    private int cur;
    
    public PhraseMatcher(String[] phraseTerms) {
        this.phraseTerms  = phraseTerms;
        cur = 0;
    }
    
    public int match(String token) {
        if (token.equals(phraseTerms[cur])) {
            //System.out.println(" PhraseMatcher matched " + token);
            if ( cur < phraseTerms.length-1) {
                cur ++;
                return WAIT; //matching.
            } else {
                //System.out.println(" PhraseMatcher match complete with " + token);
                cur = 0;
                return MATCHED; //matched!
            }
        } else if (cur > 0) {
            cur = 0;
            if (token.equals(phraseTerms[cur])) {
                cur ++;
                return WAIT; //matching.
            }
        }
        return NOT_MATCHED;
    }
}
