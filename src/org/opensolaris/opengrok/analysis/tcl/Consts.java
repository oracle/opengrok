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
 * Copyright 2006 Sun Microsystems, Inc.  All rights reserved.
 * Use is subject to license terms.
 */
package org.opensolaris.opengrok.analysis.tcl;

import java.util.HashSet;
import java.util.Set;

/**
  * Holds static hash set containing Tcl keywords
  */
public class Consts {
    public static final Set<String> kwd = new HashSet<String>();
    static {
        // Tcl cmds
        kwd.add("after");
        kwd.add("append");
        kwd.add("apply");
        kwd.add("array");
        kwd.add("auto_execok");
        kwd.add("auto_import");
        kwd.add("auto_load");
        kwd.add("auto_mkindex");
        kwd.add("auto_qualify");
        kwd.add("auto_reset");
        kwd.add("bgerror");
        kwd.add("binary");
        kwd.add("break");
        kwd.add("catch");
        kwd.add("cd");
        kwd.add("chan");
        kwd.add("clock");
        kwd.add("close");
        kwd.add("concat");
        kwd.add("continue");
        kwd.add("dde");
        kwd.add("dict");
        kwd.add("else");
        kwd.add("elseif");
        kwd.add("encoding");
        kwd.add("eof");
        kwd.add("error");
        kwd.add("eval");
        kwd.add("exec");
        kwd.add("exit");
        kwd.add("expr");
        kwd.add("fblocked");
        kwd.add("fconfigure");
        kwd.add("fcopy");
        kwd.add("file");
        kwd.add("fileevent");
        kwd.add("filename");
        kwd.add("flush");
        kwd.add("for");
        kwd.add("foreach");
        kwd.add("format");
        kwd.add("gets");
        kwd.add("glob");
        kwd.add("global");
        kwd.add("history");
        kwd.add("http");
        kwd.add("if");
        kwd.add("incr");
        kwd.add("info");
        kwd.add("interp");
        kwd.add("join");
        kwd.add("lappend");
        kwd.add("lassign");
        kwd.add("lindex");
        kwd.add("linsert");
        kwd.add("list");
        kwd.add("llength");
        kwd.add("load");
        kwd.add("lrange");
        kwd.add("lrepeat");
        kwd.add("lreplace");
        kwd.add("lreverse");
        kwd.add("lsearch");
        kwd.add("lset");
        kwd.add("lsort");
        kwd.add("mathfunc");
        kwd.add("mathop");
        kwd.add("memory");
        kwd.add("msgcat");
        kwd.add("namespace");
        kwd.add("open");
        kwd.add("package");
        kwd.add("parray");
        kwd.add("pid");
        kwd.add("pkg::create");
        kwd.add("pkg_mkIndex");
        kwd.add("platform");
        kwd.add("platform::shell");
        kwd.add("proc");
        kwd.add("puts");
        kwd.add("pwd");
        kwd.add("read");
        kwd.add("regexp");
        kwd.add("registry");
        kwd.add("regsub");
        kwd.add("rename");
        kwd.add("return");
        kwd.add("scan");
        kwd.add("seek");
        kwd.add("set");
        kwd.add("socket");
        kwd.add("source");
        kwd.add("split");
        kwd.add("string");
        kwd.add("subst");
        kwd.add("switch");
        kwd.add("tell");
        kwd.add("then");
        kwd.add("time");
        kwd.add("tm");
        kwd.add("trace");
        kwd.add("unknown");
        kwd.add("unload");
        kwd.add("unset");
        kwd.add("update");
        kwd.add("uplevel");
        kwd.add("upvar");
        kwd.add("variable");
        kwd.add("vwait");
        kwd.add("while");
        // Tk cmds
        kwd.add("bell");
        kwd.add("bind");
        kwd.add("bindtags");
        kwd.add("bitmap");
        kwd.add("button");
        kwd.add("canvas");
        kwd.add("checkbutton");
        kwd.add("clipboard");
        kwd.add("colors");
        kwd.add("console");
        kwd.add("cursors");
        kwd.add("destroy");
        kwd.add("entry");
        kwd.add("event");
        kwd.add("focus");
        kwd.add("font");
        kwd.add("frame");
        kwd.add("grab");
        kwd.add("grid");
        kwd.add("image");
        kwd.add("keysyms");
        kwd.add("label");
        kwd.add("labelframe");
        kwd.add("listbox");
        kwd.add("loadTk");
        kwd.add("lower");
        kwd.add("menu");
        kwd.add("menubutton");
        kwd.add("message");
        kwd.add("option");
        kwd.add("options");
        kwd.add("pack");
        kwd.add("panedwindow");
        kwd.add("photo");
        kwd.add("place");
        kwd.add("radiobutton");
        kwd.add("raise");
        kwd.add("scale");
        kwd.add("scrollbar");
        kwd.add("selection");
        kwd.add("send");
        kwd.add("spinbox");
        kwd.add("text");
        kwd.add("tk");
        kwd.add("tk_bisque");
        kwd.add("tk_chooseColor");
        kwd.add("tk_chooseDirectory");
        kwd.add("tk_dialog");
        kwd.add("tk_focusFollowsMouse");
        kwd.add("tk_focusNext");
        kwd.add("tk_focusPrev");
        kwd.add("tk_getOpenFile");
        kwd.add("tk_getSaveFile");
        kwd.add("tk_menuSetFocus");
        kwd.add("tk_messageBox");
        kwd.add("tk_optionMenu");
        kwd.add("tk_popup");
        kwd.add("tk_setPalette");
        kwd.add("tk_textCopy");
        kwd.add("tk_textCut");
        kwd.add("tk_textPaste");
        kwd.add("tkerror");
        kwd.add("tkvars");
        kwd.add("tkwait");
        kwd.add("toplevel");
        kwd.add("ttk_button");
        kwd.add("ttk_checkbutton");
        kwd.add("ttk_combobox");
        kwd.add("ttk_entry");
        kwd.add("ttk_frame");
        kwd.add("ttk_image");
        kwd.add("ttk_intro");
        kwd.add("ttk_label");
        kwd.add("ttk_labelframe");
        kwd.add("ttk_menubutton");
        kwd.add("ttk_notebook");
        kwd.add("ttk_panedwindow");
        kwd.add("ttk_progressbar");
        kwd.add("ttk_radiobutton");
        kwd.add("ttk_scrollbar");
        kwd.add("ttk_separator");
        kwd.add("ttk_sizegrip");
        kwd.add("ttk_style");
        kwd.add("ttk_treeview");
        kwd.add("ttk_widget");
        kwd.add("winfo");
        kwd.add("wm");
    }
}
