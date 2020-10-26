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
 * Copyright (c) 2007, 2018, Oracle and/or its affiliates. All rights reserved.
 * Portions Copyright (c) 2018, 2019, Chris Fraire <cfraire@me.com>.
 */
package org.opengrok.indexer.analysis.sql;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public final class PLSQLConsts {
    private static final Set<String> kwd = new HashSet<>();

    static final Set<String> KEYWORDS = Collections.unmodifiableSet(kwd);

    static {
        kwd.addAll(org.opengrok.indexer.analysis.sql.Consts.KEYWORDS);

        kwd.add("asc"); // plsql2011reserved
        kwd.add("clusters"); // plsql2011reserved
        kwd.add("cluster"); // plsql2011reserved
        kwd.add("colauth"); // plsql2011reserved
        kwd.add("columns"); // plsql2011reserved
        kwd.add("compress"); // plsql2011reserved
        kwd.add("crash"); // plsql2011reserved
        kwd.add("desc"); // plsql2011reserved
        kwd.add("exception"); // plsql2011reserved
        kwd.add("exclusive"); // plsql2011reserved
        kwd.add("goto"); // plsql2011reserved
        kwd.add("identified"); // plsql2011reserved
        kwd.add("if"); // plsql2011reserved
        kwd.add("index"); // plsql2011reserved
        kwd.add("indexes"); // plsql2011reserved
        kwd.add("lock"); // plsql2011reserved
        kwd.add("minus"); // plsql2011reserved
        kwd.add("mode"); // plsql2011reserved
        kwd.add("nocompress"); // plsql2011reserved
        kwd.add("nowait"); // plsql2011reserved
        kwd.add("option"); // plsql2011reserved
        kwd.add("public"); // plsql2011reserved
        kwd.add("resource"); // plsql2011reserved
        kwd.add("share"); // plsql2011reserved
        kwd.add("size"); // plsql2011reserved
        kwd.add("subtype"); // plsql2011reserved
        kwd.add("tabauth"); // plsql2011reserved
        kwd.add("type"); // plsql2011reserved
        kwd.add("view"); // plsql2011reserved
        kwd.add("views"); // plsql2011reserved
    }

    /** Private to enforce static. */
    private PLSQLConsts() {
    }
}
