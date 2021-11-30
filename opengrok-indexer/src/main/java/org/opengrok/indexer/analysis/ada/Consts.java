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
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2017, Chris Fraire <cfraire@me.com>.
 */
package org.opengrok.indexer.analysis.ada;

import java.util.HashSet;
import java.util.Set;

/**
 * Represents a container for Ada keywords and other string constants.
 */
public class Consts {

    static final Set<String> kwd = new HashSet<>();
    static {
        kwd.add("abort");
        kwd.add("abs");
        kwd.add("abstract");
        kwd.add("accept");
        kwd.add("access");
        kwd.add("aliased");
        kwd.add("all");
        kwd.add("and");
        kwd.add("array");
        kwd.add("at");
        kwd.add("begin");
        kwd.add("body");
        kwd.add("case");
        kwd.add("constant");
        kwd.add("declare");
        kwd.add("delay");
        kwd.add("delta");
        kwd.add("digits");
        kwd.add("do");
        kwd.add("else");
        kwd.add("elsif");
        kwd.add("end");
        kwd.add("entry");
        kwd.add("exception");
        kwd.add("exit");
        kwd.add("for");
        kwd.add("function");
        kwd.add("generic");
        kwd.add("goto");
        kwd.add("if");
        kwd.add("in");
        kwd.add("interface");
        kwd.add("is");
        kwd.add("limited");
        kwd.add("loop");
        kwd.add("mod");
        kwd.add("new");
        kwd.add("not");
        kwd.add("null");
        kwd.add("of");
        kwd.add("or");
        kwd.add("others");
        kwd.add("out");
        kwd.add("overriding");
        kwd.add("package");
        kwd.add("pragma");
        kwd.add("private");
        kwd.add("procedure");
        kwd.add("protected");
        kwd.add("raise");
        kwd.add("range");
        kwd.add("record");
        kwd.add("rem");
        kwd.add("renames");
        kwd.add("requeue");
        kwd.add("return");
        kwd.add("reverse");
        kwd.add("select");
        kwd.add("separate");
        kwd.add("subtype");
        kwd.add("synchronized");
        kwd.add("tagged");
        kwd.add("task");
        kwd.add("terminate");
        kwd.add("then");
        kwd.add("type");
        kwd.add("until");
        kwd.add("use");
        kwd.add("when");
        kwd.add("while");
        kwd.add("with");
        kwd.add("xor");

        kwd.add("ascii"); // A.1 The Package Standard
        kwd.add("base"); // A.1 The Package Standard
        kwd.add("boolean"); // A.1 The Package Standard
        kwd.add("character"); // A.1 The Package Standard
        kwd.add("false"); // A.1 The Package Standard
        kwd.add("float"); // A.1 The Package Standard
        kwd.add("integer"); // A.1 The Package Standard
        kwd.add("last"); // A.1 The Package Standard
        kwd.add("left"); // A.1 The Package Standard
        kwd.add("natural"); // A.1 The Package Standard
        kwd.add("nul");  // A.1 The Package Standard
        kwd.add("pack"); // A.1 The Package Standard
        kwd.add("positive"); // A.1 The Package Standard
        kwd.add("right"); // A.1 The Package Standard
        kwd.add("root_real"); // A.1 The Package Standard
        kwd.add("size"); // A.1 The Package Standard
        kwd.add("soh");  // A.1 The Package Standard
        kwd.add("standard"); // A.1 The Package Standard
        kwd.add("string"); // A.1 The Package Standard
        kwd.add("true"); // A.1 The Package Standard
        kwd.add("universal_fixed"); // A.1 The Package Standard
        kwd.add("wide_character"); // A.1 The Package Standard
        kwd.add("wide_string"); // A.1 The Package Standard
        kwd.add("wide_wide_character"); // A.1 The Package Standard
        kwd.add("wide_wide_string"); // A.1 The Package Standard

        kwd.add("ada");  // A.2 The Package Ada
        kwd.add("pure"); // A.2 The Package Ada

        kwd.add("characters"); // A.3.1 The Packages Characters...
        kwd.add("wide_characters"); // A.3.1 The Packages Characters...
        kwd.add("wide_wide_characters"); // A.3.1 The Packages Chara...

        kwd.add("is_alphanumeric"); // A.3.2 Characters.Handling
        kwd.add("is_basic"); // A.3.2 Characters.Handling
        kwd.add("is_control"); // A.3.2 Characters.Handling
        kwd.add("is_decimal_digit"); // A.3.2 Characters.Handling
        kwd.add("is_digit"); // A.3.2 Characters.Handling
        kwd.add("is_graphic"); // A.3.2 Characters.Handling
        kwd.add("is_hexadecimal_digit"); // A.3.2 Characters.Handling
        kwd.add("is_iso_646"); // A.3.2 Characters.Handling
        kwd.add("is_letter"); // A.3.2 Characters.Handling
        kwd.add("is_lower"); // A.3.2 Characters.Handling
        kwd.add("is_special"); // A.3.2 Characters.Handling
        kwd.add("is_upper"); // A.3.2 Characters.Handling
        kwd.add("iso_646"); // A.3.2 Characters.Handling
        kwd.add("to_basic"); // A.3.2 Characters.Handling
        kwd.add("to_iso_646"); // A.3.2 Characters.Handling
        kwd.add("to_lower"); // A.3.2 Characters.Handling
        kwd.add("to_upper"); // A.3.2 Characters.Handling
        kwd.add("val"); // A.3.2 Characters.Handling

        kwd.add("ack"); // A.3.3 Characters.Latin_1
        kwd.add("acute"); // A.3.3 Characters.Latin_1
        kwd.add("ampersand"); // A.3.3 Characters.Latin_1
        kwd.add("apc"); // A.3.3 Characters.Latin_1
        kwd.add("apostrophe"); // A.3.3 Characters.Latin_1
        kwd.add("asterisk"); // A.3.3 Characters.Latin_1
        kwd.add("bel"); // A.3.3 Characters.Latin_1
        kwd.add("bph"); // A.3.3 Characters.Latin_1
        kwd.add("broken_bar"); // A.3.3 Characters.Latin_1
        kwd.add("bs"); // A.3.3 Characters.Latin_1
        kwd.add("can"); // A.3.3 Characters.Latin_1
        kwd.add("cch"); // A.3.3 Characters.Latin_1
        kwd.add("cedilla"); // A.3.3 Characters.Latin_1
        kwd.add("cent_sign"); // A.3.3 Characters.Latin_1
        kwd.add("circumflex"); // A.3.3 Characters.Latin_1
        kwd.add("colon"); // A.3.3 Characters.Latin_1
        kwd.add("comma"); // A.3.3 Characters.Latin_1
        kwd.add("commercial_at"); // A.3.3 Characters.Latin_1
        kwd.add("copyright_sign"); // A.3.3 Characters.Latin_1
        kwd.add("cr"); // A.3.3 Characters.Latin_1
        kwd.add("csi"); // A.3.3 Characters.Latin_1
        kwd.add("currency_sign"); // A.3.3 Characters.Latin_1
        kwd.add("dc1"); // A.3.3 Characters.Latin_1
        kwd.add("dc2"); // A.3.3 Characters.Latin_1
        kwd.add("dc3"); // A.3.3 Characters.Latin_1
        kwd.add("dc4"); // A.3.3 Characters.Latin_1
        kwd.add("dcs"); // A.3.3 Characters.Latin_1
        kwd.add("degree_sign"); // A.3.3 Characters.Latin_1
        kwd.add("del"); // A.3.3 Characters.Latin_1
        kwd.add("diaeresis"); // A.3.3 Characters.Latin_1
        kwd.add("division_sign"); // A.3.3 Characters.Latin_1
        kwd.add("dle"); // A.3.3 Characters.Latin_1
        kwd.add("dollar_sign"); // A.3.3 Characters.Latin_1
        kwd.add("em"); // A.3.3 Characters.Latin_1
        kwd.add("enq"); // A.3.3 Characters.Latin_1
        kwd.add("eot"); // A.3.3 Characters.Latin_1
        kwd.add("epa"); // A.3.3 Characters.Latin_1
        kwd.add("equals_sign"); // A.3.3 Characters.Latin_1
        kwd.add("esa"); // A.3.3 Characters.Latin_1
        kwd.add("esc"); // A.3.3 Characters.Latin_1
        kwd.add("etb"); // A.3.3 Characters.Latin_1
        kwd.add("etx"); // A.3.3 Characters.Latin_1
        kwd.add("exclamation"); // A.3.3 Characters.Latin_1
        kwd.add("feminine_ordinal_indicator"); // A.3.3 Characters.Latin_1
        kwd.add("ff"); // A.3.3 Characters.Latin_1
        kwd.add("fraction_one_half"); // A.3.3 Characters.Latin_1
        kwd.add("fraction_one_quarter"); // A.3.3 Characters.Latin_1
        kwd.add("fraction_three_quarters"); // A.3.3 Characters.Latin_1
        kwd.add("fs"); // A.3.3 Characters.Latin_1
        kwd.add("full_stop"); // A.3.3 Characters.Latin_1
        kwd.add("grave"); // A.3.3 Characters.Latin_1
        kwd.add("greater_than_sign"); // A.3.3 Characters.Latin_1
        kwd.add("gs"); // A.3.3 Characters.Latin_1
        kwd.add("ht"); // A.3.3 Characters.Latin_1
        kwd.add("htj"); // A.3.3 Characters.Latin_1
        kwd.add("hts"); // A.3.3 Characters.Latin_1
        kwd.add("hyphen"); // A.3.3 Characters.Latin_1
        kwd.add("inverted_exclamation"); // A.3.3 Characters.Latin_1
        kwd.add("inverted_question"); // A.3.3 Characters.Latin_1
        kwd.add("is1"); // A.3.3 Characters.Latin_1
        kwd.add("is2"); // A.3.3 Characters.Latin_1
        kwd.add("is3"); // A.3.3 Characters.Latin_1
        kwd.add("is4"); // A.3.3 Characters.Latin_1
        kwd.add("latin_1"); // A.3.3 Characters.Latin_1
        kwd.add("lc_a_acute"); // A.3.3 Characters.Latin_1
        kwd.add("lc_a_circumflex"); // A.3.3 Characters.Latin_1
        kwd.add("lc_a_diaeresis"); // A.3.3 Characters.Latin_1
        kwd.add("lc_a_grave"); // A.3.3 Characters.Latin_1
        kwd.add("lc_a_ring"); // A.3.3 Characters.Latin_1
        kwd.add("lc_a_tilde"); // A.3.3 Characters.Latin_1
        kwd.add("lc_a"); // A.3.3 Characters.Latin_1
        kwd.add("lc_ae_diphthong"); // A.3.3 Characters.Latin_1
        kwd.add("lc_b"); // A.3.3 Characters.Latin_1
        kwd.add("lc_c_cedilla"); // A.3.3 Characters.Latin_1
        kwd.add("lc_c"); // A.3.3 Characters.Latin_1
        kwd.add("lc_d"); // A.3.3 Characters.Latin_1
        kwd.add("lc_e_acute"); // A.3.3 Characters.Latin_1
        kwd.add("lc_e_circumflex"); // A.3.3 Characters.Latin_1
        kwd.add("lc_e_diaeresis"); // A.3.3 Characters.Latin_1
        kwd.add("lc_e_grave"); // A.3.3 Characters.Latin_1
        kwd.add("lc_e"); // A.3.3 Characters.Latin_1
        kwd.add("lc_f"); // A.3.3 Characters.Latin_1
        kwd.add("lc_g"); // A.3.3 Characters.Latin_1
        kwd.add("lc_german_sharp_s"); // A.3.3 Characters.Latin_1
        kwd.add("lc_h"); // A.3.3 Characters.Latin_1
        kwd.add("lc_i_acute"); // A.3.3 Characters.Latin_1
        kwd.add("lc_i_circumflex"); // A.3.3 Characters.Latin_1
        kwd.add("lc_i_diaeresis"); // A.3.3 Characters.Latin_1
        kwd.add("lc_i_grave"); // A.3.3 Characters.Latin_1
        kwd.add("lc_i"); // A.3.3 Characters.Latin_1
        kwd.add("lc_icelandic_eth"); // A.3.3 Characters.Latin_1
        kwd.add("lc_icelandic_thorn"); // A.3.3 Characters.Latin_1
        kwd.add("lc_j"); // A.3.3 Characters.Latin_1
        kwd.add("lc_k"); // A.3.3 Characters.Latin_1
        kwd.add("lc_l"); // A.3.3 Characters.Latin_1
        kwd.add("lc_m"); // A.3.3 Characters.Latin_1
        kwd.add("lc_n_tilde"); // A.3.3 Characters.Latin_1
        kwd.add("lc_n"); // A.3.3 Characters.Latin_1
        kwd.add("lc_o_acute"); // A.3.3 Characters.Latin_1
        kwd.add("lc_o_circumflex"); // A.3.3 Characters.Latin_1
        kwd.add("lc_o_diaeresis"); // A.3.3 Characters.Latin_1
        kwd.add("lc_o_grave"); // A.3.3 Characters.Latin_1
        kwd.add("lc_o_oblique_stroke"); // A.3.3 Characters.Latin_1
        kwd.add("lc_o_tilde"); // A.3.3 Characters.Latin_1
        kwd.add("lc_o"); // A.3.3 Characters.Latin_1
        kwd.add("lc_p"); // A.3.3 Characters.Latin_1
        kwd.add("lc_q"); // A.3.3 Characters.Latin_1
        kwd.add("lc_r"); // A.3.3 Characters.Latin_1
        kwd.add("lc_s"); // A.3.3 Characters.Latin_1
        kwd.add("lc_t"); // A.3.3 Characters.Latin_1
        kwd.add("lc_u_acute"); // A.3.3 Characters.Latin_1
        kwd.add("lc_u_circumflex"); // A.3.3 Characters.Latin_1
        kwd.add("lc_u_diaeresis"); // A.3.3 Characters.Latin_1
        kwd.add("lc_u_grave"); // A.3.3 Characters.Latin_1
        kwd.add("lc_u"); // A.3.3 Characters.Latin_1
        kwd.add("lc_v"); // A.3.3 Characters.Latin_1
        kwd.add("lc_w"); // A.3.3 Characters.Latin_1
        kwd.add("lc_x"); // A.3.3 Characters.Latin_1
        kwd.add("lc_y_acute"); // A.3.3 Characters.Latin_1
        kwd.add("lc_y_diaeresis"); // A.3.3 Characters.Latin_1
        kwd.add("lc_y"); // A.3.3 Characters.Latin_1
        kwd.add("lc_z"); // A.3.3 Characters.Latin_1
        kwd.add("left_angle_quotation"); // A.3.3 Characters.Latin_1
        kwd.add("left_curly_bracket"); // A.3.3 Characters.Latin_1
        kwd.add("left_parenthesis"); // A.3.3 Characters.Latin_1
        kwd.add("left_square_bracket"); // A.3.3 Characters.Latin_1
        kwd.add("less_than_sign"); // A.3.3 Characters.Latin_1
        kwd.add("lf"); // A.3.3 Characters.Latin_1
        kwd.add("low_line"); // A.3.3 Characters.Latin_1
        kwd.add("macron"); // A.3.3 Characters.Latin_1
        kwd.add("masculine_ordinal_indicator"); // A.3.3 Characters.Latin_1
        kwd.add("micro_sign"); // A.3.3 Characters.Latin_1
        kwd.add("middle_dot"); // A.3.3 Characters.Latin_1
        kwd.add("minus_sign"); // A.3.3 Characters.Latin_1
        kwd.add("multiplication_sign"); // A.3.3 Characters.Latin_1
        kwd.add("mw"); // A.3.3 Characters.Latin_1
        kwd.add("nak"); // A.3.3 Characters.Latin_1
        kwd.add("nbh"); // A.3.3 Characters.Latin_1
        kwd.add("nbsp"); // A.3.3 Characters.Latin_1
        kwd.add("nel"); // A.3.3 Characters.Latin_1
        kwd.add("no_break_space"); // A.3.3 Characters.Latin_1
        kwd.add("not_sign"); // A.3.3 Characters.Latin_1
        kwd.add("nul"); // A.3.3 Characters.Latin_1
        kwd.add("number_sign"); // A.3.3 Characters.Latin_1
        kwd.add("osc"); // A.3.3 Characters.Latin_1
        kwd.add("paragraph_sign"); // A.3.3 Characters.Latin_1
        kwd.add("percent_sign"); // A.3.3 Characters.Latin_1
        kwd.add("pilcrow_sign"); // A.3.3 Characters.Latin_1
        kwd.add("pld"); // A.3.3 Characters.Latin_1
        kwd.add("plu"); // A.3.3 Characters.Latin_1
        kwd.add("plus_minus_sign"); // A.3.3 Characters.Latin_1
        kwd.add("plus_sign"); // A.3.3 Characters.Latin_1
        kwd.add("pm"); // A.3.3 Characters.Latin_1
        kwd.add("pound_sign"); // A.3.3 Characters.Latin_1
        kwd.add("pu1"); // A.3.3 Characters.Latin_1
        kwd.add("pu2"); // A.3.3 Characters.Latin_1
        kwd.add("question"); // A.3.3 Characters.Latin_1
        kwd.add("quotation"); // A.3.3 Characters.Latin_1
        kwd.add("registered_trade_mark_sign"); // A.3.3 Characters.Latin_1
        kwd.add("reverse_solidus"); // A.3.3 Characters.Latin_1
        kwd.add("ri"); // A.3.3 Characters.Latin_1
        kwd.add("right_angle_quotation"); // A.3.3 Characters.Latin_1
        kwd.add("right_curly_bracket"); // A.3.3 Characters.Latin_1
        kwd.add("right_parenthesis"); // A.3.3 Characters.Latin_1
        kwd.add("right_square_bracket"); // A.3.3 Characters.Latin_1
        kwd.add("ring_above"); // A.3.3 Characters.Latin_1
        kwd.add("rs"); // A.3.3 Characters.Latin_1
        kwd.add("sci"); // A.3.3 Characters.Latin_1
        kwd.add("section_sign"); // A.3.3 Characters.Latin_1
        kwd.add("semicolon"); // A.3.3 Characters.Latin_1
        kwd.add("si"); // A.3.3 Characters.Latin_1
        kwd.add("so"); // A.3.3 Characters.Latin_1
        kwd.add("soft_hyphen"); // A.3.3 Characters.Latin_1
        kwd.add("soh"); // A.3.3 Characters.Latin_1
        kwd.add("solidus"); // A.3.3 Characters.Latin_1
        kwd.add("sos"); // A.3.3 Characters.Latin_1
        kwd.add("spa"); // A.3.3 Characters.Latin_1
        kwd.add("space"); // A.3.3 Characters.Latin_1
        kwd.add("ss2"); // A.3.3 Characters.Latin_1
        kwd.add("ss3"); // A.3.3 Characters.Latin_1
        kwd.add("ssa"); // A.3.3 Characters.Latin_1
        kwd.add("st"); // A.3.3 Characters.Latin_1
        kwd.add("sts"); // A.3.3 Characters.Latin_1
        kwd.add("stx"); // A.3.3 Characters.Latin_1
        kwd.add("sub"); // A.3.3 Characters.Latin_1
        kwd.add("superscript_one"); // A.3.3 Characters.Latin_1
        kwd.add("superscript_three"); // A.3.3 Characters.Latin_1
        kwd.add("superscript_two"); // A.3.3 Characters.Latin_1
        kwd.add("syn"); // A.3.3 Characters.Latin_1
        kwd.add("tilde"); // A.3.3 Characters.Latin_1
        kwd.add("uc_a_acute"); // A.3.3 Characters.Latin_1
        kwd.add("uc_a_circumflex"); // A.3.3 Characters.Latin_1
        kwd.add("uc_a_diaeresis"); // A.3.3 Characters.Latin_1
        kwd.add("uc_a_grave"); // A.3.3 Characters.Latin_1
        kwd.add("uc_a_ring"); // A.3.3 Characters.Latin_1
        kwd.add("uc_a_tilde"); // A.3.3 Characters.Latin_1
        kwd.add("uc_ae_diphthong"); // A.3.3 Characters.Latin_1
        kwd.add("uc_c_cedilla"); // A.3.3 Characters.Latin_1
        kwd.add("uc_e_acute"); // A.3.3 Characters.Latin_1
        kwd.add("uc_e_circumflex"); // A.3.3 Characters.Latin_1
        kwd.add("uc_e_diaeresis"); // A.3.3 Characters.Latin_1
        kwd.add("uc_e_grave"); // A.3.3 Characters.Latin_1
        kwd.add("uc_i_acute"); // A.3.3 Characters.Latin_1
        kwd.add("uc_i_circumflex"); // A.3.3 Characters.Latin_1
        kwd.add("uc_i_diaeresis"); // A.3.3 Characters.Latin_1
        kwd.add("uc_i_grave"); // A.3.3 Characters.Latin_1
        kwd.add("uc_icelandic_eth"); // A.3.3 Characters.Latin_1
        kwd.add("uc_icelandic_thorn"); // A.3.3 Characters.Latin_1
        kwd.add("uc_n_tilde"); // A.3.3 Characters.Latin_1
        kwd.add("uc_o_acute"); // A.3.3 Characters.Latin_1
        kwd.add("uc_o_circumflex"); // A.3.3 Characters.Latin_1
        kwd.add("uc_o_diaeresis"); // A.3.3 Characters.Latin_1
        kwd.add("uc_o_grave"); // A.3.3 Characters.Latin_1
        kwd.add("uc_o_oblique_stroke"); // A.3.3 Characters.Latin_1
        kwd.add("uc_o_tilde"); // A.3.3 Characters.Latin_1
        kwd.add("uc_u_acute"); // A.3.3 Characters.Latin_1
        kwd.add("uc_u_circumflex"); // A.3.3 Characters.Latin_1
        kwd.add("uc_u_diaeresis"); // A.3.3 Characters.Latin_1
        kwd.add("uc_u_grave"); // A.3.3 Characters.Latin_1
        kwd.add("uc_y_acute"); // A.3.3 Characters.Latin_1
        kwd.add("us"); // A.3.3 Characters.Latin_1
        kwd.add("vertical_line"); // A.3.3 Characters.Latin_1
        kwd.add("vt"); // A.3.3 Characters.Latin_1
        kwd.add("vts"); // A.3.3 Characters.Latin_1
        kwd.add("yen_sign"); // A.3.3 Characters.Latin_1

        kwd.add("is_character"); // A.3.4 Characters.Conversions
        kwd.add("is_string"); // A.3.4 Characters.Conversions
        kwd.add("is_wide_character"); // A.3.4 Characters.Conversions
        kwd.add("is_wide_string"); // A.3.4 Characters.Conversions
        kwd.add("to_character"); // A.3.4 Characters.Conversions
        kwd.add("to_string"); // A.3.4 Characters.Conversions
        kwd.add("to_wide_character"); // A.3.4 Characters.Conversions
        kwd.add("to_wide_string"); // A.3.4 Characters.Conversions
        kwd.add("to_wide_wide_character"); // A.3.4 Characters.Conversions
        kwd.add("to_wide_wide_string"); // A.3.4 Characters.Conversions

        kwd.add("alignment"); // A.4.1 Strings
        kwd.add("direction"); // A.4.1 Strings
        kwd.add("membership"); // A.4.1 Strings
        kwd.add("space"); // A.4.1 Strings
        kwd.add("strings"); // A.4.1 Strings
        kwd.add("trim_end"); // A.4.1 Strings
        kwd.add("truncation"); // A.4.1 Strings
        kwd.add("wide_space"); // A.4.1 Strings
        kwd.add("wide_wide_space"); // A.4.1 Strings

        kwd.add("character_mapping_function"); // A.4.2 Strings.Maps
        kwd.add("character_mapping"); // A.4.2 Strings.Maps
        kwd.add("character_range"); // A.4.2 Strings.Maps
        kwd.add("character_ranges"); // A.4.2 Strings.Maps
        kwd.add("character_sequence"); // A.4.2 Strings.Maps
        kwd.add("character_set"); // A.4.2 Strings.Maps
        kwd.add("is_in"); // A.4.2 Strings.Maps
        kwd.add("is_subset"); // A.4.2 Strings.Maps
        kwd.add("maps"); // A.4.2 Strings.Maps
        kwd.add("null_set"); // A.4.2 Strings.Maps
        kwd.add("preelaborable_initialization"); // A.4.2 Strings.Maps
        kwd.add("to_domain"); // A.4.2 Strings.Maps
        kwd.add("to_mapping"); // A.4.2 Strings.Maps
        kwd.add("to_range"); // A.4.2 Strings.Maps
        kwd.add("to_ranges"); // A.4.2 Strings.Maps
        kwd.add("to_sequence"); // A.4.2 Strings.Maps
        kwd.add("to_set"); // A.4.2 Strings.Maps

        kwd.add("alphanumeric_set"); // A.4.6 String-Handling Sets and Mappings
        kwd.add("basic_map"); // A.4.6 String-Handling Sets and Mappings
        kwd.add("basic_set"); // A.4.6 String-Handling Sets and Mappings
        kwd.add("control_set"); // A.4.6 String-Handling Sets and Mappings
        kwd.add("decimal_digit_set"); // A.4.6 String-Handling Sets and Mappings
        kwd.add("graphic_set"); // A.4.6 String-Handling Sets and Mappings
        kwd.add("hexadecimal_digit_set"); // A.4.6 String-Handling Sets and Mappings
        kwd.add("iso_646_set"); // A.4.6 String-Handling Sets and Mappings
        kwd.add("letter_set"); // A.4.6 String-Handling Sets and Mappings
        kwd.add("lower_case_map"); // A.4.6 String-Handling Sets and Mappings
        kwd.add("lower_set"); // A.4.6 String-Handling Sets and Mappings
        kwd.add("special_set"); // A.4.6 String-Handling Sets and Mappings
        kwd.add("upper_case_map"); // A.4.6 String-Handling Sets and Mappings
        kwd.add("upper_set"); // A.4.6 String-Handling Sets and Mappings

        kwd.add("append_file"); // A.10.1 Text_IO
        kwd.add("close"); // A.10.1 Text_IO
        kwd.add("col"); // A.10.1 Text_IO
        kwd.add("count"); // A.10.1 Text_IO
        kwd.add("create"); // A.10.1 Text_IO
        kwd.add("current_error"); // A.10.1 Text_IO
        kwd.add("current_input"); // A.10.1 Text_IO
        kwd.add("current_output"); // A.10.1 Text_IO
        kwd.add("delete"); // A.10.1 Text_IO
        kwd.add("end_of_file"); // A.10.1 Text_IO
        kwd.add("end_of_line"); // A.10.1 Text_IO
        kwd.add("end_of_page"); // A.10.1 Text_IO
        kwd.add("field"); // A.10.1 Text_IO
        kwd.add("file_access"); // A.10.1 Text_IO
        kwd.add("file_mode"); // A.10.1 Text_IO
        kwd.add("file_type"); // A.10.1 Text_IO
        kwd.add("flush"); // A.10.1 Text_IO
        kwd.add("form"); // A.10.1 Text_IO
        kwd.add("get_immediate"); // A.10.1 Text_IO
        kwd.add("get_line"); // A.10.1 Text_IO
        kwd.add("get"); // A.10.1 Text_IO
        kwd.add("in_file"); // A.10.1 Text_IO
        kwd.add("is_open"); // A.10.1 Text_IO
        kwd.add("line_length"); // A.10.1 Text_IO
        kwd.add("line"); // A.10.1 Text_IO
        kwd.add("look_ahead"); // A.10.1 Text_IO
        kwd.add("lower_case"); // A.10.1 Text_IO
        kwd.add("mode"); // A.10.1 Text_IO
        kwd.add("name"); // A.10.1 Text_IO
        kwd.add("new_line"); // A.10.1 Text_IO
        kwd.add("new_page"); // A.10.1 Text_IO
        kwd.add("number_base"); // A.10.1 Text_IO
        kwd.add("open"); // A.10.1 Text_IO
        kwd.add("out_file"); // A.10.1 Text_IO
        kwd.add("page_length"); // A.10.1 Text_IO
        kwd.add("page"); // A.10.1 Text_IO
        kwd.add("positive_count"); // A.10.1 Text_IO
        kwd.add("put_line"); // A.10.1 Text_IO
        kwd.add("put"); // A.10.1 Text_IO
        kwd.add("reset"); // A.10.1 Text_IO
        kwd.add("set_col"); // A.10.1 Text_IO
        kwd.add("set_error"); // A.10.1 Text_IO
        kwd.add("set_input"); // A.10.1 Text_IO
        kwd.add("set_line_length"); // A.10.1 Text_IO
        kwd.add("set_line"); // A.10.1 Text_IO
        kwd.add("set_output"); // A.10.1 Text_IO
        kwd.add("set_page_length"); // A.10.1 Text_IO
        kwd.add("skip_line"); // A.10.1 Text_IO
        kwd.add("skip_page"); // A.10.1 Text_IO
        kwd.add("standard_error"); // A.10.1 Text_IO
        kwd.add("standard_input"); // A.10.1 Text_IO
        kwd.add("standard_output"); // A.10.1 Text_IO
        kwd.add("text_io"); // A.10.1 Text_IO
        kwd.add("type_set"); // A.10.1 Text_IO
        kwd.add("upper_case"); // A.10.1 Text_IO

        kwd.add("complex"); // G.1.1 Complex Types
        kwd.add("compose_from_cartesian"); // G.1.1 Complex Types
        kwd.add("compose_from_polar"); // G.1.1 Complex Types
        kwd.add("conjugate"); // G.1.1 Complex Types
        kwd.add("i"); // G.1.1 Complex Types
        kwd.add("im"); // G.1.1 Complex Types
        kwd.add("imaginary"); // G.1.1 Complex Types
        kwd.add("j"); // G.1.1 Complex Types
        kwd.add("re"); // G.1.1 Complex Types
        kwd.add("real"); // G.1.1 Complex Types
        kwd.add("set_im"); // G.1.1 Complex Types
        kwd.add("set_re"); // G.1.1 Complex Types
    }

    private Consts() {
    }

}
