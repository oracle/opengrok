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

public final class Consts {
    private static final Set<String> kwd = new HashSet<>();

    static final Set<String> KEYWORDS = Collections.unmodifiableSet(kwd);

    static {
        kwd.add("abs"); // sql2003reserved
        kwd.add("all"); // sql2003reserved
        kwd.add("allocate"); // sql2003reserved
        kwd.add("alter"); // sql2003reserved
        kwd.add("and"); // sql2003reserved
        kwd.add("any"); // sql2003reserved
        kwd.add("are"); // sql2003reserved
        kwd.add("array"); // sql2003reserved
        kwd.add("as"); // sql2003reserved
        kwd.add("asensitive"); // sql2003reserved
        kwd.add("asymmetric"); // sql2003reserved
        kwd.add("at"); // sql2003reserved
        kwd.add("atomic"); // sql2003reserved
        kwd.add("authorization"); // sql2003reserved
        kwd.add("avg"); // sql2003reserved
        kwd.add("begin"); // sql2003reserved
        kwd.add("between"); // sql2003reserved
        kwd.add("bigint"); // sql2003reserved
        kwd.add("binary"); // sql2003reserved
        kwd.add("blob"); // sql2003reserved
        kwd.add("boolean"); // sql2003reserved
        kwd.add("both"); // sql2003reserved
        kwd.add("by"); // sql2003reserved
        kwd.add("call"); // sql2003reserved
        kwd.add("called"); // sql2003reserved
        kwd.add("cardinality"); // sql2003reserved
        kwd.add("cascaded"); // sql2003reserved
        kwd.add("case"); // sql2003reserved
        kwd.add("cast"); // sql2003reserved
        kwd.add("ceil"); // sql2003reserved
        kwd.add("ceiling"); // sql2003reserved
        kwd.add("char"); // sql2003reserved
        kwd.add("char_length"); // sql2003reserved
        kwd.add("character"); // sql2003reserved
        kwd.add("character_length"); // sql2003reserved
        kwd.add("check"); // sql2003reserved
        kwd.add("clob"); // sql2003reserved
        kwd.add("close"); // sql2003reserved
        kwd.add("coalesce"); // sql2003reserved
        kwd.add("collate"); // sql2003reserved
        kwd.add("collect"); // sql2003reserved
        kwd.add("column"); // sql2003reserved
        kwd.add("commit"); // sql2003reserved
        kwd.add("condition"); // sql2003reserved
        kwd.add("connect"); // sql2003reserved
        kwd.add("constraint"); // sql2003reserved
        kwd.add("convert"); // sql2003reserved
        kwd.add("corr"); // sql2003reserved
        kwd.add("corresponding"); // sql2003reserved
        kwd.add("count"); // sql2003reserved
        kwd.add("covar_pop"); // sql2003reserved
        kwd.add("covar_samp"); // sql2003reserved
        kwd.add("create"); // sql2003reserved
        kwd.add("cross"); // sql2003reserved
        kwd.add("cube"); // sql2003reserved
        kwd.add("cume_dist"); // sql2003reserved
        kwd.add("current"); // sql2003reserved
        kwd.add("current_date"); // sql2003reserved
        kwd.add("current_default_transform_group"); // sql2003reserved
        kwd.add("current_path"); // sql2003reserved
        kwd.add("current_role"); // sql2003reserved
        kwd.add("current_time"); // sql2003reserved
        kwd.add("current_timestamp"); // sql2003reserved
        kwd.add("current_transform_group_for_type"); // sql2003reserved
        kwd.add("current_user"); // sql2003reserved
        kwd.add("cursor"); // sql2003reserved
        kwd.add("cycle"); // sql2003reserved
        kwd.add("date"); // sql2003reserved
        kwd.add("day"); // sql2003reserved
        kwd.add("deallocate"); // sql2003reserved
        kwd.add("dec"); // sql2003reserved
        kwd.add("decimal"); // sql2003reserved
        kwd.add("declare"); // sql2003reserved
        kwd.add("default"); // sql2003reserved
        kwd.add("delete"); // sql2003reserved
        kwd.add("dense_rank"); // sql2003reserved
        kwd.add("deref"); // sql2003reserved
        kwd.add("describe"); // sql2003reserved
        kwd.add("deterministic"); // sql2003reserved
        kwd.add("disconnect"); // sql2003reserved
        kwd.add("distinct"); // sql2003reserved
        kwd.add("double"); // sql2003reserved
        kwd.add("drop"); // sql2003reserved
        kwd.add("dynamic"); // sql2003reserved
        kwd.add("each"); // sql2003reserved
        kwd.add("element"); // sql2003reserved
        kwd.add("else"); // sql2003reserved
        kwd.add("end"); // sql2003reserved
        kwd.add("end-exec"); // sql2003reserved
        kwd.add("escape"); // sql2003reserved
        kwd.add("every"); // sql2003reserved
        kwd.add("except"); // sql2003reserved
        kwd.add("exec"); // sql2003reserved
        kwd.add("execute"); // sql2003reserved
        kwd.add("exists"); // sql2003reserved
        kwd.add("exp"); // sql2003reserved
        kwd.add("external"); // sql2003reserved
        kwd.add("extract"); // sql2003reserved
        kwd.add("false"); // sql2003reserved
        kwd.add("fetch"); // sql2003reserved
        kwd.add("filter"); // sql2003reserved
        kwd.add("float"); // sql2003reserved
        kwd.add("floor"); // sql2003reserved
        kwd.add("for"); // sql2003reserved
        kwd.add("foreign"); // sql2003reserved
        kwd.add("free"); // sql2003reserved
        kwd.add("from"); // sql2003reserved
        kwd.add("full"); // sql2003reserved
        kwd.add("function"); // sql2003reserved
        kwd.add("fusion"); // sql2003reserved
        kwd.add("get"); // sql2003reserved
        kwd.add("global"); // sql2003reserved
        kwd.add("grant"); // sql2003reserved
        kwd.add("group"); // sql2003reserved
        kwd.add("grouping"); // sql2003reserved
        kwd.add("having"); // sql2003reserved
        kwd.add("hold"); // sql2003reserved
        kwd.add("hour"); // sql2003reserved
        kwd.add("identity"); // sql2003reserved
        kwd.add("in"); // sql2003reserved
        kwd.add("indicator"); // sql2003reserved
        kwd.add("inner"); // sql2003reserved
        kwd.add("inout"); // sql2003reserved
        kwd.add("insensitive"); // sql2003reserved
        kwd.add("insert"); // sql2003reserved
        kwd.add("int"); // sql2003reserved
        kwd.add("integer"); // sql2003reserved
        kwd.add("intersect"); // sql2003reserved
        kwd.add("intersection"); // sql2003reserved
        kwd.add("interval"); // sql2003reserved
        kwd.add("into"); // sql2003reserved
        kwd.add("is"); // sql2003reserved
        kwd.add("join"); // sql2003reserved
        kwd.add("language"); // sql2003reserved
        kwd.add("large"); // sql2003reserved
        kwd.add("lateral"); // sql2003reserved
        kwd.add("leading"); // sql2003reserved
        kwd.add("left"); // sql2003reserved
        kwd.add("like"); // sql2003reserved
        kwd.add("ln"); // sql2003reserved
        kwd.add("local"); // sql2003reserved
        kwd.add("localtime"); // sql2003reserved
        kwd.add("localtimestamp"); // sql2003reserved
        kwd.add("lower"); // sql2003reserved
        kwd.add("match"); // sql2003reserved
        kwd.add("max"); // sql2003reserved
        kwd.add("member"); // sql2003reserved
        kwd.add("merge"); // sql2003reserved
        kwd.add("method"); // sql2003reserved
        kwd.add("min"); // sql2003reserved
        kwd.add("minute"); // sql2003reserved
        kwd.add("mod"); // sql2003reserved
        kwd.add("modifies"); // sql2003reserved
        kwd.add("module"); // sql2003reserved
        kwd.add("month"); // sql2003reserved
        kwd.add("multiset"); // sql2003reserved
        kwd.add("national"); // sql2003reserved
        kwd.add("natural"); // sql2003reserved
        kwd.add("nchar"); // sql2003reserved
        kwd.add("nclob"); // sql2003reserved
        kwd.add("new"); // sql2003reserved
        kwd.add("no"); // sql2003reserved
        kwd.add("none"); // sql2003reserved
        kwd.add("normalize"); // sql2003reserved
        kwd.add("not"); // sql2003reserved
        kwd.add("null"); // sql2003reserved
        kwd.add("nullif"); // sql2003reserved
        kwd.add("numeric"); // sql2003reserved
        kwd.add("octet_length"); // sql2003reserved
        kwd.add("of"); // sql2003reserved
        kwd.add("old"); // sql2003reserved
        kwd.add("on"); // sql2003reserved
        kwd.add("only"); // sql2003reserved
        kwd.add("open"); // sql2003reserved
        kwd.add("or"); // sql2003reserved
        kwd.add("order"); // sql2003reserved
        kwd.add("out"); // sql2003reserved
        kwd.add("outer"); // sql2003reserved
        kwd.add("over"); // sql2003reserved
        kwd.add("overlaps"); // sql2003reserved
        kwd.add("overlay"); // sql2003reserved
        kwd.add("parameter"); // sql2003reserved
        kwd.add("partition"); // sql2003reserved
        kwd.add("percent_rank"); // sql2003reserved
        kwd.add("percentile_cont"); // sql2003reserved
        kwd.add("percentile_disc"); // sql2003reserved
        kwd.add("position"); // sql2003reserved
        kwd.add("power"); // sql2003reserved
        kwd.add("precision"); // sql2003reserved
        kwd.add("prepare"); // sql2003reserved
        kwd.add("primary"); // sql2003reserved
        kwd.add("procedure"); // sql2003reserved
        kwd.add("range"); // sql2003reserved
        kwd.add("rank"); // sql2003reserved
        kwd.add("reads"); // sql2003reserved
        kwd.add("real"); // sql2003reserved
        kwd.add("recursive"); // sql2003reserved
        kwd.add("ref"); // sql2003reserved
        kwd.add("references"); // sql2003reserved
        kwd.add("referencing"); // sql2003reserved
        kwd.add("regr_avgx"); // sql2003reserved
        kwd.add("regr_avgy"); // sql2003reserved
        kwd.add("regr_count"); // sql2003reserved
        kwd.add("regr_intercept"); // sql2003reserved
        kwd.add("regr_r2"); // sql2003reserved
        kwd.add("regr_slope"); // sql2003reserved
        kwd.add("regr_sxx"); // sql2003reserved
        kwd.add("regr_sxy"); // sql2003reserved
        kwd.add("regr_syy"); // sql2003reserved
        kwd.add("release"); // sql2003reserved
        kwd.add("result"); // sql2003reserved
        kwd.add("return"); // sql2003reserved
        kwd.add("returns"); // sql2003reserved
        kwd.add("revoke"); // sql2003reserved
        kwd.add("right"); // sql2003reserved
        kwd.add("rollback"); // sql2003reserved
        kwd.add("rollup"); // sql2003reserved
        kwd.add("row"); // sql2003reserved
        kwd.add("row_number"); // sql2003reserved
        kwd.add("rows"); // sql2003reserved
        kwd.add("savepoint"); // sql2003reserved
        kwd.add("scope"); // sql2003reserved
        kwd.add("scroll"); // sql2003reserved
        kwd.add("search"); // sql2003reserved
        kwd.add("second"); // sql2003reserved
        kwd.add("select"); // sql2003reserved
        kwd.add("sensitive"); // sql2003reserved
        kwd.add("session_user"); // sql2003reserved
        kwd.add("set"); // sql2003reserved
        kwd.add("similar"); // sql2003reserved
        kwd.add("smallint"); // sql2003reserved
        kwd.add("some"); // sql2003reserved
        kwd.add("specific"); // sql2003reserved
        kwd.add("specifictype"); // sql2003reserved
        kwd.add("sql"); // sql2003reserved
        kwd.add("sqlexception"); // sql2003reserved
        kwd.add("sqlstate"); // sql2003reserved
        kwd.add("sqlwarning"); // sql2003reserved
        kwd.add("sqrt"); // sql2003reserved
        kwd.add("start"); // sql2003reserved
        kwd.add("static"); // sql2003reserved
        kwd.add("stddev_pop"); // sql2003reserved
        kwd.add("stddev_samp"); // sql2003reserved
        kwd.add("submultiset"); // sql2003reserved
        kwd.add("substring"); // sql2003reserved
        kwd.add("sum"); // sql2003reserved
        kwd.add("symmetric"); // sql2003reserved
        kwd.add("system"); // sql2003reserved
        kwd.add("system_user"); // sql2003reserved
        kwd.add("table"); // sql2003reserved
        kwd.add("tablesample"); // sql2003reserved
        kwd.add("then"); // sql2003reserved
        kwd.add("time"); // sql2003reserved
        kwd.add("timestamp"); // sql2003reserved
        kwd.add("timezone_hour"); // sql2003reserved
        kwd.add("timezone_minute"); // sql2003reserved
        kwd.add("to"); // sql2003reserved
        kwd.add("trailing"); // sql2003reserved
        kwd.add("translate"); // sql2003reserved
        kwd.add("translation"); // sql2003reserved
        kwd.add("treat"); // sql2003reserved
        kwd.add("trigger"); // sql2003reserved
        kwd.add("trim"); // sql2003reserved
        kwd.add("true"); // sql2003reserved
        kwd.add("uescape"); // sql2003reserved
        kwd.add("union"); // sql2003reserved
        kwd.add("unique"); // sql2003reserved
        kwd.add("unknown"); // sql2003reserved
        kwd.add("unnest"); // sql2003reserved
        kwd.add("update"); // sql2003reserved
        kwd.add("upper"); // sql2003reserved
        kwd.add("user"); // sql2003reserved
        kwd.add("using"); // sql2003reserved
        kwd.add("value"); // sql2003reserved
        kwd.add("values"); // sql2003reserved
        kwd.add("var_pop"); // sql2003reserved
        kwd.add("var_samp"); // sql2003reserved
        kwd.add("varchar"); // sql2003reserved
        kwd.add("varying"); // sql2003reserved
        kwd.add("when"); // sql2003reserved
        kwd.add("whenever"); // sql2003reserved
        kwd.add("where"); // sql2003reserved
        kwd.add("width_bucket"); // sql2003reserved
        kwd.add("window"); // sql2003reserved
        kwd.add("with"); // sql2003reserved
        kwd.add("within"); // sql2003reserved
        kwd.add("without"); // sql2003reserved
        kwd.add("year"); // sql2003reserved

        kwd.add("array_agg"); // sql2008reserved
        kwd.add("current_catalog"); // sql2008reserved
        kwd.add("current_schema"); // sql2008reserved
        kwd.add("first_value"); // sql2008reserved
        kwd.add("lag"); // sql2008reserved
        kwd.add("last_value"); // sql2008reserved
        kwd.add("lead"); // sql2008reserved
        kwd.add("like_regex"); // sql2008reserved
        kwd.add("max_cardinality"); // sql2008reserved
        kwd.add("nth_value"); // sql2008reserved
        kwd.add("ntile"); // sql2008reserved
        kwd.add("occurrences_regex"); // sql2008reserved
        kwd.add("offset"); // sql2008reserved
        kwd.add("position_regex"); // sql2008reserved
        kwd.add("substring_regex"); // sql2008reserved
        kwd.add("translate_regex"); // sql2008reserved
        kwd.add("truncate"); // sql2008reserved
        kwd.add("trim_array"); // sql2008reserved
        kwd.add("varbinary"); // sql2008reserved

        kwd.add("contains"); // sql2011reserved
        kwd.add("equals"); // sql2011reserved
        kwd.add("period"); // sql2011reserved
        kwd.add("portion"); // sql2011reserved
        kwd.add("precedes"); // sql2011reserved
        kwd.add("succeeds"); // sql2011reserved
        kwd.add("system_time"); // sql2011reserved
    }

    /** Private to enforce static. */
    private Consts() {
    }
}
