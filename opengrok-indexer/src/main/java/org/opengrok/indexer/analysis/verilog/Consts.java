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
 * Copyright (c) 2019, Chris Fraire <cfraire@me.com>.
 */
package org.opengrok.indexer.analysis.verilog;

import java.util.HashSet;
import java.util.Set;

/**
 * Represents a container for a set of SystemVerilog keywords.
 */
public class Consts {

    public static final Set<String> kwd = new HashSet<>();

    static {
        kwd.add("accept_on"); // IEEE 1800-2017
        kwd.add("alias"); // IEEE 1800-2017
        kwd.add("always"); // IEEE 1800-2017
        kwd.add("always_comb"); // IEEE 1800-2017
        kwd.add("always_ff"); // IEEE 1800-2017
        kwd.add("always_latch"); // IEEE 1800-2017
        kwd.add("and"); // IEEE 1800-2017
        kwd.add("assert"); // IEEE 1800-2017
        kwd.add("assign"); // IEEE 1800-2017
        kwd.add("assume"); // IEEE 1800-2017
        kwd.add("automatic"); // IEEE 1800-2017
        kwd.add("before"); // IEEE 1800-2017
        kwd.add("begin"); // IEEE 1800-2017
        kwd.add("bind"); // IEEE 1800-2017
        kwd.add("bins"); // IEEE 1800-2017
        kwd.add("binsof"); // IEEE 1800-2017
        kwd.add("bit"); // IEEE 1800-2017
        kwd.add("break"); // IEEE 1800-2017
        kwd.add("buf"); // IEEE 1800-2017
        kwd.add("bufif0"); // IEEE 1800-2017
        kwd.add("bufif1"); // IEEE 1800-2017
        kwd.add("byte"); // IEEE 1800-2017
        kwd.add("case"); // IEEE 1800-2017
        kwd.add("casex"); // IEEE 1800-2017
        kwd.add("casez"); // IEEE 1800-2017
        kwd.add("cell"); // IEEE 1800-2017
        kwd.add("chandle"); // IEEE 1800-2017
        kwd.add("checker"); // IEEE 1800-2017
        kwd.add("class"); // IEEE 1800-2017
        kwd.add("clocking"); // IEEE 1800-2017
        kwd.add("cmos"); // IEEE 1800-2017
        kwd.add("config"); // IEEE 1800-2017
        kwd.add("const"); // IEEE 1800-2017
        kwd.add("constraint"); // IEEE 1800-2017
        kwd.add("context"); // IEEE 1800-2017
        kwd.add("continue"); // IEEE 1800-2017
        kwd.add("cover"); // IEEE 1800-2017
        kwd.add("covergroup"); // IEEE 1800-2017
        kwd.add("coverpoint"); // IEEE 1800-2017
        kwd.add("cross"); // IEEE 1800-2017
        kwd.add("deassign"); // IEEE 1800-2017
        kwd.add("default"); // IEEE 1800-2017
        kwd.add("defparam"); // IEEE 1800-2017
        kwd.add("design"); // IEEE 1800-2017
        kwd.add("disable"); // IEEE 1800-2017
        kwd.add("dist"); // IEEE 1800-2017
        kwd.add("do"); // IEEE 1800-2017
        kwd.add("edge"); // IEEE 1800-2017
        kwd.add("else"); // IEEE 1800-2017
        kwd.add("end"); // IEEE 1800-2017
        kwd.add("endcase"); // IEEE 1800-2017
        kwd.add("endchecker"); // IEEE 1800-2017
        kwd.add("endclass"); // IEEE 1800-2017
        kwd.add("endclocking"); // IEEE 1800-2017
        kwd.add("endconfig"); // IEEE 1800-2017
        kwd.add("endfunction"); // IEEE 1800-2017
        kwd.add("endgenerate"); // IEEE 1800-2017
        kwd.add("endgroup"); // IEEE 1800-2017
        kwd.add("endinterface"); // IEEE 1800-2017
        kwd.add("endmodule"); // IEEE 1800-2017
        kwd.add("endpackage"); // IEEE 1800-2017
        kwd.add("endprimitive"); // IEEE 1800-2017
        kwd.add("endprogram"); // IEEE 1800-2017
        kwd.add("endproperty"); // IEEE 1800-2017
        kwd.add("endspecify"); // IEEE 1800-2017
        kwd.add("endsequence"); // IEEE 1800-2017
        kwd.add("endtable"); // IEEE 1800-2017
        kwd.add("endtask"); // IEEE 1800-2017
        kwd.add("enum"); // IEEE 1800-2017
        kwd.add("event"); // IEEE 1800-2017
        kwd.add("eventually"); // IEEE 1800-2017
        kwd.add("expect"); // IEEE 1800-2017
        kwd.add("export"); // IEEE 1800-2017
        kwd.add("extends"); // IEEE 1800-2017
        kwd.add("extern"); // IEEE 1800-2017
        kwd.add("final"); // IEEE 1800-2017
        kwd.add("first_match"); // IEEE 1800-2017
        kwd.add("for"); // IEEE 1800-2017
        kwd.add("force"); // IEEE 1800-2017
        kwd.add("foreach"); // IEEE 1800-2017
        kwd.add("forever"); // IEEE 1800-2017
        kwd.add("fork"); // IEEE 1800-2017
        kwd.add("forkjoin"); // IEEE 1800-2017
        kwd.add("function"); // IEEE 1800-2017
        kwd.add("generate"); // IEEE 1800-2017
        kwd.add("genvar"); // IEEE 1800-2017
        kwd.add("global"); // IEEE 1800-2017
        kwd.add("highz0"); // IEEE 1800-2017
        kwd.add("highz1"); // IEEE 1800-2017
        kwd.add("if"); // IEEE 1800-2017
        kwd.add("iff"); // IEEE 1800-2017
        kwd.add("ifnone"); // IEEE 1800-2017
        kwd.add("ignore_bins"); // IEEE 1800-2017
        kwd.add("illegal_bins"); // IEEE 1800-2017
        kwd.add("implements"); // IEEE 1800-2017
        kwd.add("implies"); // IEEE 1800-2017
        kwd.add("import"); // IEEE 1800-2017
        kwd.add("incdir"); // IEEE 1800-2017
        kwd.add("include"); // IEEE 1800-2017
        kwd.add("initial"); // IEEE 1800-2017
        kwd.add("inout"); // IEEE 1800-2017
        kwd.add("input"); // IEEE 1800-2017
        kwd.add("inside"); // IEEE 1800-2017
        kwd.add("instance"); // IEEE 1800-2017
        kwd.add("int"); // IEEE 1800-2017
        kwd.add("integer"); // IEEE 1800-2017
        kwd.add("interconnect"); // IEEE 1800-2017
        kwd.add("interface"); // IEEE 1800-2017
        kwd.add("intersect"); // IEEE 1800-2017
        kwd.add("join"); // IEEE 1800-2017
        kwd.add("join_any"); // IEEE 1800-2017
        kwd.add("join_none"); // IEEE 1800-2017
        kwd.add("large"); // IEEE 1800-2017
        kwd.add("let"); // IEEE 1800-2017
        kwd.add("liblist"); // IEEE 1800-2017
        kwd.add("library"); // IEEE 1800-2017
        kwd.add("local"); // IEEE 1800-2017
        kwd.add("localparam"); // IEEE 1800-2017
        kwd.add("logic"); // IEEE 1800-2017
        kwd.add("longint"); // IEEE 1800-2017
        kwd.add("macromodule"); // IEEE 1800-2017
        kwd.add("matches"); // IEEE 1800-2017
        kwd.add("medium"); // IEEE 1800-2017
        kwd.add("modport"); // IEEE 1800-2017
        kwd.add("module"); // IEEE 1800-2017
        kwd.add("nand"); // IEEE 1800-2017
        kwd.add("negedge"); // IEEE 1800-2017
        kwd.add("nettype"); // IEEE 1800-2017
        kwd.add("new"); // IEEE 1800-2017
        kwd.add("nexttime"); // IEEE 1800-2017
        kwd.add("nmos"); // IEEE 1800-2017
        kwd.add("nor"); // IEEE 1800-2017
        kwd.add("noshowcancelled"); // IEEE 1800-2017
        kwd.add("not"); // IEEE 1800-2017
        kwd.add("notif0"); // IEEE 1800-2017
        kwd.add("notif1"); // IEEE 1800-2017
        kwd.add("null"); // IEEE 1800-2017
        kwd.add("or"); // IEEE 1800-2017
        kwd.add("output"); // IEEE 1800-2017
        kwd.add("package"); // IEEE 1800-2017
        kwd.add("packed"); // IEEE 1800-2017
        kwd.add("parameter"); // IEEE 1800-2017
        kwd.add("pmos"); // IEEE 1800-2017
        kwd.add("posedge"); // IEEE 1800-2017
        kwd.add("primitive"); // IEEE 1800-2017
        kwd.add("priority"); // IEEE 1800-2017
        kwd.add("program"); // IEEE 1800-2017
        kwd.add("property"); // IEEE 1800-2017
        kwd.add("protected"); // IEEE 1800-2017
        kwd.add("pull0"); // IEEE 1800-2017
        kwd.add("pull1"); // IEEE 1800-2017
        kwd.add("pulldown"); // IEEE 1800-2017
        kwd.add("pullup"); // IEEE 1800-2017
        kwd.add("pulsestyle_ondetect"); // IEEE 1800-2017
        kwd.add("pulsestyle_onevent"); // IEEE 1800-2017
        kwd.add("pure"); // IEEE 1800-2017
        kwd.add("rand"); // IEEE 1800-2017
        kwd.add("randc"); // IEEE 1800-2017
        kwd.add("randcase"); // IEEE 1800-2017
        kwd.add("randsequence"); // IEEE 1800-2017
        kwd.add("rcmos"); // IEEE 1800-2017
        kwd.add("real"); // IEEE 1800-2017
        kwd.add("realtime"); // IEEE 1800-2017
        kwd.add("ref"); // IEEE 1800-2017
        kwd.add("reg"); // IEEE 1800-2017
        kwd.add("reject_on"); // IEEE 1800-2017
        kwd.add("release"); // IEEE 1800-2017
        kwd.add("repeat"); // IEEE 1800-2017
        kwd.add("restrict"); // IEEE 1800-2017
        kwd.add("return"); // IEEE 1800-2017
        kwd.add("rnmos"); // IEEE 1800-2017
        kwd.add("rpmos"); // IEEE 1800-2017
        kwd.add("rtran"); // IEEE 1800-2017
        kwd.add("rtranif0"); // IEEE 1800-2017
        kwd.add("rtranif1"); // IEEE 1800-2017
        kwd.add("s_always"); // IEEE 1800-2017
        kwd.add("s_eventually"); // IEEE 1800-2017
        kwd.add("s_nexttime"); // IEEE 1800-2017
        kwd.add("s_until"); // IEEE 1800-2017
        kwd.add("s_until_with"); // IEEE 1800-2017
        kwd.add("scalared"); // IEEE 1800-2017
        kwd.add("sequence"); // IEEE 1800-2017
        kwd.add("shortint"); // IEEE 1800-2017
        kwd.add("shortreal"); // IEEE 1800-2017
        kwd.add("showcancelled"); // IEEE 1800-2017
        kwd.add("signed"); // IEEE 1800-2017
        kwd.add("small"); // IEEE 1800-2017
        kwd.add("soft"); // IEEE 1800-2017
        kwd.add("solve"); // IEEE 1800-2017
        kwd.add("specify"); // IEEE 1800-2017
        kwd.add("specparam"); // IEEE 1800-2017
        kwd.add("static"); // IEEE 1800-2017
        kwd.add("string"); // IEEE 1800-2017
        kwd.add("strong"); // IEEE 1800-2017
        kwd.add("strong0"); // IEEE 1800-2017
        kwd.add("strong1"); // IEEE 1800-2017
        kwd.add("struct"); // IEEE 1800-2017
        kwd.add("super"); // IEEE 1800-2017
        kwd.add("supply0"); // IEEE 1800-2017
        kwd.add("supply1"); // IEEE 1800-2017
        kwd.add("sync_accept_on"); // IEEE 1800-2017
        kwd.add("sync_reject_on"); // IEEE 1800-2017
        kwd.add("table"); // IEEE 1800-2017
        kwd.add("tagged"); // IEEE 1800-2017
        kwd.add("task"); // IEEE 1800-2017
        kwd.add("this"); // IEEE 1800-2017
        kwd.add("throughout"); // IEEE 1800-2017
        kwd.add("time"); // IEEE 1800-2017
        kwd.add("timeprecision"); // IEEE 1800-2017
        kwd.add("timeunit"); // IEEE 1800-2017
        kwd.add("tran"); // IEEE 1800-2017
        kwd.add("tranif0"); // IEEE 1800-2017
        kwd.add("tranif1"); // IEEE 1800-2017
        kwd.add("tri"); // IEEE 1800-2017
        kwd.add("tri0"); // IEEE 1800-2017
        kwd.add("tri1"); // IEEE 1800-2017
        kwd.add("triand"); // IEEE 1800-2017
        kwd.add("trior"); // IEEE 1800-2017
        kwd.add("trireg"); // IEEE 1800-2017
        kwd.add("type"); // IEEE 1800-2017
        kwd.add("typedef"); // IEEE 1800-2017
        kwd.add("union"); // IEEE 1800-2017
        kwd.add("unique"); // IEEE 1800-2017
        kwd.add("unique0"); // IEEE 1800-2017
        kwd.add("unsigned"); // IEEE 1800-2017
        kwd.add("until"); // IEEE 1800-2017
        kwd.add("until_with"); // IEEE 1800-2017
        kwd.add("untyped"); // IEEE 1800-2017
        kwd.add("use"); // IEEE 1800-2017
        kwd.add("uwire"); // IEEE 1800-2017
        kwd.add("var"); // IEEE 1800-2017
        kwd.add("vectored"); // IEEE 1800-2017
        kwd.add("virtual"); // IEEE 1800-2017
        kwd.add("void"); // IEEE 1800-2017
        kwd.add("wait"); // IEEE 1800-2017
        kwd.add("wait_order"); // IEEE 1800-2017
        kwd.add("wand"); // IEEE 1800-2017
        kwd.add("weak"); // IEEE 1800-2017
        kwd.add("weak0"); // IEEE 1800-2017
        kwd.add("weak1"); // IEEE 1800-2017
        kwd.add("while"); // IEEE 1800-2017
        kwd.add("wildcard"); // IEEE 1800-2017
        kwd.add("wire"); // IEEE 1800-2017
        kwd.add("with"); // IEEE 1800-2017
        kwd.add("within"); // IEEE 1800-2017
        kwd.add("wor"); // IEEE 1800-2017
        kwd.add("xnor"); // IEEE 1800-2017
        kwd.add("xor"); // IEEE 1800-2017
        kwd.add("`__FILE__"); // IEEE 1800-2017
        kwd.add("`__LINE__"); // IEEE 1800-2017
        kwd.add("`begin_keywords"); // IEEE 1800-2017
        kwd.add("`celldefine"); // IEEE 1800-2017
        kwd.add("`default_decay_time"); // IEEE 1800-2017
        kwd.add("`default_nettype"); // IEEE 1800-2017
        kwd.add("`default_trireg_strength"); // IEEE 1800-2017
        kwd.add("`define"); // IEEE 1800-2017
        kwd.add("`delay_mode_distributed"); // IEEE 1800-2017
        kwd.add("`delay_mode_path"); // IEEE 1800-2017
        kwd.add("`delay_mode_unit"); // IEEE 1800-2017
        kwd.add("`delay_mode_zero"); // IEEE 1800-2017
        kwd.add("`else"); // IEEE 1800-2017
        kwd.add("`elsif"); // IEEE 1800-2017
        kwd.add("`end_keywords"); // IEEE 1800-2017
        kwd.add("`endcelldefine"); // IEEE 1800-2017
        kwd.add("`endif"); // IEEE 1800-2017
        kwd.add("`ifdef"); // IEEE 1800-2017
        kwd.add("`ifndef"); // IEEE 1800-2017
        kwd.add("`include"); // IEEE 1800-2017
        kwd.add("`line"); // IEEE 1800-2017
        kwd.add("`nounconnected_drive"); // IEEE 1800-2017
        kwd.add("`pragma"); // IEEE 1800-2017
        kwd.add("`resetall"); // IEEE 1800-2017
        kwd.add("`timescale"); // IEEE 1800-2017
        kwd.add("`unconnected_drive"); // IEEE 1800-2017
        kwd.add("`undef"); // IEEE 1800-2017
        kwd.add("`undefineall"); // IEEE 1800-2017
//        kwd.add("$setup"); // IEEE 1800-2017
//        kwd.add("$fullskew"); // IEEE 1800-2017
//        kwd.add("$hold"); // IEEE 1800-2017
//        kwd.add("$nochange"); // IEEE 1800-2017
//        kwd.add("$period"); // IEEE 1800-2017
//        kwd.add("$recovery"); // IEEE 1800-2017
//        kwd.add("$recrem"); // IEEE 1800-2017
//        kwd.add("$removal"); // IEEE 1800-2017
//        kwd.add("$setuphold"); // IEEE 1800-2017
//        kwd.add("$skew"); // IEEE 1800-2017
//        kwd.add("$timeskew"); // IEEE 1800-2017
//        kwd.add("$width"); // IEEE 1800-2017
    }

    /** Private to enforce static. */
    private Consts() {
    }
}
