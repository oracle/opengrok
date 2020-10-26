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
 * Copyright (c) 2017, Chris Fraire <cfraire@me.com>.
 */
package org.opengrok.indexer.analysis.ruby;

import java.util.HashSet;
import java.util.Set;

/**
 * Represents a container for Ruby keywords and other string constants.
 */
public class Consts {

    public static final Set<String> kwd = new HashSet<>();
    static {
        kwd.add("false");
        kwd.add("FALSE");
        kwd.add("new");
        kwd.add("nil");
        kwd.add("NIL");
        kwd.add("true");
        kwd.add("TRUE");

        kwd.add("__ENCODING__"); // 2.4.0/keywords_rdoc
        kwd.add("__FILE__"); // 2.4.0/keywords_rdoc
        kwd.add("__LINE__"); // 2.4.0/keywords_rdoc
        kwd.add("alias"); // 2.4.0/keywords_rdoc
        kwd.add("and"); // 2.4.0/keywords_rdoc
        kwd.add("begin"); // 2.4.0/keywords_rdoc
        kwd.add("BEGIN"); // 2.4.0/keywords_rdoc
        kwd.add("break"); // 2.4.0/keywords_rdoc
        kwd.add("case"); // 2.4.0/keywords_rdoc
        kwd.add("class"); // 2.4.0/keywords_rdoc
        kwd.add("def"); // 2.4.0/keywords_rdoc
        kwd.add("defined?"); // 2.4.0/keywords_rdoc
        kwd.add("do"); // 2.4.0/keywords_rdoc
        kwd.add("else"); // 2.4.0/keywords_rdoc
        kwd.add("elsif"); // 2.4.0/keywords_rdoc
        kwd.add("end"); // 2.4.0/keywords_rdoc
        kwd.add("END"); // 2.4.0/keywords_rdoc
        kwd.add("ensure"); // 2.4.0/keywords_rdoc
        kwd.add("for"); // 2.4.0/keywords_rdoc
        kwd.add("if"); // 2.4.0/keywords_rdoc
        kwd.add("in"); // 2.4.0/keywords_rdoc
        kwd.add("module"); // 2.4.0/keywords_rdoc
        kwd.add("next"); // 2.4.0/keywords_rdoc
        kwd.add("not"); // 2.4.0/keywords_rdoc
        kwd.add("or"); // 2.4.0/keywords_rdoc
        kwd.add("redo"); // 2.4.0/keywords_rdoc
        kwd.add("rescue"); // 2.4.0/keywords_rdoc
        kwd.add("retry"); // 2.4.0/keywords_rdoc
        kwd.add("return"); // 2.4.0/keywords_rdoc
        kwd.add("self"); // 2.4.0/keywords_rdoc
        kwd.add("super"); // 2.4.0/keywords_rdoc
        kwd.add("then"); // 2.4.0/keywords_rdoc
        kwd.add("undef"); // 2.4.0/keywords_rdoc
        kwd.add("unless"); // 2.4.0/keywords_rdoc
        kwd.add("until"); // 2.4.0/keywords_rdoc
        kwd.add("when"); // 2.4.0/keywords_rdoc
        kwd.add("while"); // 2.4.0/keywords_rdoc
        kwd.add("yield"); // 2.4.0/keywords_rdoc

        kwd.add("__callee__"); // core-2.4.2/Kernel
        kwd.add("__dir__"); // core-2.4.2/Kernel
        kwd.add("__method__"); // core-2.4.2/Kernel
        kwd.add("abort"); // core-2.4.2/Kernel
        kwd.add("Array"); // core-2.4.2/Kernel
        kwd.add("at_exit"); // core-2.4.2/Kernel
        kwd.add("autoload?"); // core-2.4.2/Kernel
        kwd.add("autoload"); // core-2.4.2/Kernel
        kwd.add("binding"); // core-2.4.2/Kernel
        kwd.add("block_given?"); // core-2.4.2/Kernel
        kwd.add("callcc"); // core-2.4.2/Kernel
        kwd.add("caller_locations"); // core-2.4.2/Kernel
        kwd.add("caller"); // core-2.4.2/Kernel
        kwd.add("catch"); // core-2.4.2/Kernel
        kwd.add("chomp"); // core-2.4.2/Kernel
        kwd.add("chop"); // core-2.4.2/Kernel
        kwd.add("Complex"); // core-2.4.2/Kernel
        kwd.add("eval"); // core-2.4.2/Kernel
        kwd.add("exec"); // core-2.4.2/Kernel
        kwd.add("exit!"); // core-2.4.2/Kernel
        kwd.add("exit"); // core-2.4.2/Kernel
        kwd.add("fail"); // core-2.4.2/Kernel
        kwd.add("Float"); // core-2.4.2/Kernel
        kwd.add("fork"); // core-2.4.2/Kernel
        kwd.add("format"); // core-2.4.2/Kernel
        kwd.add("gets"); // core-2.4.2/Kernel
        kwd.add("global_variables"); // core-2.4.2/Kernel
        kwd.add("gsub"); // core-2.4.2/Kernel
        kwd.add("Hash"); // core-2.4.2/Kernel
        kwd.add("Integer"); // core-2.4.2/Kernel
        kwd.add("iterator?"); // core-2.4.2/Kernel
        kwd.add("lambda"); // core-2.4.2/Kernel
        kwd.add("load"); // core-2.4.2/Kernel
        kwd.add("local_variables"); // core-2.4.2/Kernel
        kwd.add("loop"); // core-2.4.2/Kernel
        kwd.add("open"); // core-2.4.2/Kernel
        kwd.add("p"); // core-2.4.2/Kernel
        kwd.add("print"); // core-2.4.2/Kernel
        kwd.add("printf"); // core-2.4.2/Kernel
        kwd.add("proc"); // core-2.4.2/Kernel
        kwd.add("putc"); // core-2.4.2/Kernel
        kwd.add("puts"); // core-2.4.2/Kernel
        kwd.add("raise"); // core-2.4.2/Kernel
        kwd.add("rand"); // core-2.4.2/Kernel
        kwd.add("Rational"); // core-2.4.2/Kernel
        kwd.add("readline"); // core-2.4.2/Kernel
        kwd.add("readlines"); // core-2.4.2/Kernel
        kwd.add("require_relative"); // core-2.4.2/Kernel
        kwd.add("require"); // core-2.4.2/Kernel
        kwd.add("select"); // core-2.4.2/Kernel
        kwd.add("set_trace_func"); // core-2.4.2/Kernel
        kwd.add("sleep"); // core-2.4.2/Kernel
        kwd.add("spawn"); // core-2.4.2/Kernel
        kwd.add("sprintf"); // core-2.4.2/Kernel
        kwd.add("srand"); // core-2.4.2/Kernel
        kwd.add("String"); // core-2.4.2/Kernel
        kwd.add("sub"); // core-2.4.2/Kernel
        kwd.add("syscall"); // core-2.4.2/Kernel
        kwd.add("system"); // core-2.4.2/Kernel
        kwd.add("test"); // core-2.4.2/Kernel
        kwd.add("throw"); // core-2.4.2/Kernel
        kwd.add("trace_var"); // core-2.4.2/Kernel
        kwd.add("trap"); // core-2.4.2/Kernel
        kwd.add("untrace_var"); // core-2.4.2/Kernel
        kwd.add("warn"); // core-2.4.2/Kernel

        kwd.add("ascii_only?"); // core-2.4.2/String
        kwd.add("b"); // core-2.4.2/String
        kwd.add("bytes"); // core-2.4.2/String
        kwd.add("bytesize"); // core-2.4.2/String
        kwd.add("byteslice"); // core-2.4.2/String
        kwd.add("capitalize!"); // core-2.4.2/String
        kwd.add("capitalize"); // core-2.4.2/String
        kwd.add("casecmp?"); // core-2.4.2/String
        kwd.add("casecmp"); // core-2.4.2/String
        kwd.add("center"); // core-2.4.2/String
        kwd.add("chars"); // core-2.4.2/String
        kwd.add("chomp!"); // core-2.4.2/String
        kwd.add("chop!"); // core-2.4.2/String
        kwd.add("chr"); // core-2.4.2/String
        kwd.add("clear"); // core-2.4.2/String
        kwd.add("codepoints"); // core-2.4.2/String
        kwd.add("concat"); // core-2.4.2/String
        kwd.add("count"); // core-2.4.2/String
        kwd.add("crypt"); // core-2.4.2/String
        kwd.add("delete!"); // core-2.4.2/String
        kwd.add("delete"); // core-2.4.2/String
        kwd.add("downcase!"); // core-2.4.2/String
        kwd.add("downcase"); // core-2.4.2/String
        kwd.add("dump"); // core-2.4.2/String
        kwd.add("each_byte"); // core-2.4.2/String
        kwd.add("each_char"); // core-2.4.2/String
        kwd.add("each_codepoint"); // core-2.4.2/String
        kwd.add("each_line"); // core-2.4.2/String
        kwd.add("empty?"); // core-2.4.2/String
        kwd.add("encode!"); // core-2.4.2/String
        kwd.add("encode"); // core-2.4.2/String
        kwd.add("encoding"); // core-2.4.2/String
        kwd.add("end_with?"); // core-2.4.2/String
        kwd.add("eql?"); // core-2.4.2/String
        kwd.add("force_encoding"); // core-2.4.2/String
        kwd.add("freeze"); // core-2.4.2/String
        kwd.add("getbyte"); // core-2.4.2/String
        kwd.add("gsub!"); // core-2.4.2/String
        kwd.add("hash"); // core-2.4.2/String
        kwd.add("hex"); // core-2.4.2/String
        kwd.add("include?"); // core-2.4.2/String
        kwd.add("index"); // core-2.4.2/String
        kwd.add("initialize_copy"); // core-2.4.2/String
        kwd.add("insert"); // core-2.4.2/String
        kwd.add("inspect"); // core-2.4.2/String
        kwd.add("intern"); // core-2.4.2/String
        kwd.add("length"); // core-2.4.2/String
        kwd.add("lines"); // core-2.4.2/String
        kwd.add("ljust"); // core-2.4.2/String
        kwd.add("lstrip!"); // core-2.4.2/String
        kwd.add("lstrip"); // core-2.4.2/String
        kwd.add("match?"); // core-2.4.2/String
        kwd.add("match"); // core-2.4.2/String
        kwd.add("next!"); // core-2.4.2/String
        kwd.add("oct"); // core-2.4.2/String
        kwd.add("ord"); // core-2.4.2/String
        kwd.add("partition"); // core-2.4.2/String
        kwd.add("prepend"); // core-2.4.2/String
        kwd.add("replace"); // core-2.4.2/String
        kwd.add("reverse!"); // core-2.4.2/String
        kwd.add("reverse"); // core-2.4.2/String
        kwd.add("rindex"); // core-2.4.2/String
        kwd.add("rjust"); // core-2.4.2/String
        kwd.add("rpartition"); // core-2.4.2/String
        kwd.add("rstrip!"); // core-2.4.2/String
        kwd.add("rstrip"); // core-2.4.2/String
        kwd.add("scan"); // core-2.4.2/String
        kwd.add("scrub!"); // core-2.4.2/String
        kwd.add("scrub"); // core-2.4.2/String
        kwd.add("setbyte"); // core-2.4.2/String
        kwd.add("size"); // core-2.4.2/String
        kwd.add("slice!"); // core-2.4.2/String
        kwd.add("slice"); // core-2.4.2/String
        kwd.add("split"); // core-2.4.2/String
        kwd.add("squeeze!"); // core-2.4.2/String
        kwd.add("squeeze"); // core-2.4.2/String
        kwd.add("start_with?"); // core-2.4.2/String
        kwd.add("strip!"); // core-2.4.2/String
        kwd.add("strip"); // core-2.4.2/String
        kwd.add("sub!"); // core-2.4.2/String
        kwd.add("succ!"); // core-2.4.2/String
        kwd.add("succ"); // core-2.4.2/String
        kwd.add("sum"); // core-2.4.2/String
        kwd.add("swapcase!"); // core-2.4.2/String
        kwd.add("swapcase"); // core-2.4.2/String
        kwd.add("to_c"); // core-2.4.2/String
        kwd.add("to_f"); // core-2.4.2/String
        kwd.add("to_i"); // core-2.4.2/String
        kwd.add("to_r"); // core-2.4.2/String
        kwd.add("to_s"); // core-2.4.2/String
        kwd.add("to_str"); // core-2.4.2/String
        kwd.add("to_sym"); // core-2.4.2/String
        kwd.add("tr_s!"); // core-2.4.2/String
        kwd.add("tr_s"); // core-2.4.2/String
        kwd.add("tr!"); // core-2.4.2/String
        kwd.add("tr"); // core-2.4.2/String
        kwd.add("try_convert"); // core-2.4.2/String
        kwd.add("unpack"); // core-2.4.2/String
        kwd.add("unpack1"); // core-2.4.2/String
        kwd.add("upcase!"); // core-2.4.2/String
        kwd.add("upcase"); // core-2.4.2/String
        kwd.add("upto"); // core-2.4.2/String
        kwd.add("valid_encoding?"); // core-2.4.2/String

        kwd.add("advise"); // core-2.4.2/IO
        kwd.add("autoclose?"); // core-2.4.2/IO
        kwd.add("autoclose="); // core-2.4.2/IO
        kwd.add("binmode?"); // core-2.4.2/IO
        kwd.add("binmode"); // core-2.4.2/IO
        kwd.add("binread"); // core-2.4.2/IO
        kwd.add("binwrite"); // core-2.4.2/IO
        kwd.add("close_on_exec?"); // core-2.4.2/IO
        kwd.add("close_on_exec="); // core-2.4.2/IO
        kwd.add("close_read"); // core-2.4.2/IO
        kwd.add("close_write"); // core-2.4.2/IO
        kwd.add("close"); // core-2.4.2/IO
        kwd.add("closed?"); // core-2.4.2/IO
        kwd.add("copy_stream"); // core-2.4.2/IO
        kwd.add("each"); // core-2.4.2/IO
        kwd.add("eof?"); // core-2.4.2/IO
        kwd.add("eof"); // core-2.4.2/IO
        kwd.add("external_encoding"); // core-2.4.2/IO
        kwd.add("fcntl"); // core-2.4.2/IO
        kwd.add("fdatasync"); // core-2.4.2/IO
        kwd.add("fileno"); // core-2.4.2/IO
        kwd.add("flush"); // core-2.4.2/IO
        kwd.add("for_fd"); // core-2.4.2/IO
        kwd.add("foreach"); // core-2.4.2/IO
        kwd.add("fsync"); // core-2.4.2/IO
        kwd.add("getc"); // core-2.4.2/IO
        kwd.add("internal_encoding"); // core-2.4.2/IO
        kwd.add("ioctl"); // core-2.4.2/IO
        kwd.add("isatty"); // core-2.4.2/IO
        kwd.add("lineno"); // core-2.4.2/IO
        kwd.add("lineno="); // core-2.4.2/IO
        kwd.add("pid"); // core-2.4.2/IO
        kwd.add("pipe"); // core-2.4.2/IO
        kwd.add("popen"); // core-2.4.2/IO
        kwd.add("pos"); // core-2.4.2/IO
        kwd.add("pos="); // core-2.4.2/IO
        kwd.add("read_nonblock"); // core-2.4.2/IO
        kwd.add("read"); // core-2.4.2/IO
        kwd.add("readbyte"); // core-2.4.2/IO
        kwd.add("readchar"); // core-2.4.2/IO
        kwd.add("readpartial"); // core-2.4.2/IO
        kwd.add("reopen"); // core-2.4.2/IO
        kwd.add("rewind"); // core-2.4.2/IO
        kwd.add("seek"); // core-2.4.2/IO
        kwd.add("set_encoding"); // core-2.4.2/IO
        kwd.add("stat"); // core-2.4.2/IO
        kwd.add("sync"); // core-2.4.2/IO
        kwd.add("sync="); // core-2.4.2/IO
        kwd.add("sysopen"); // core-2.4.2/IO
        kwd.add("sysread"); // core-2.4.2/IO
        kwd.add("sysseek"); // core-2.4.2/IO
        kwd.add("syswrite"); // core-2.4.2/IO
        kwd.add("tell"); // core-2.4.2/IO
        kwd.add("to_io"); // core-2.4.2/IO
        kwd.add("tty?"); // core-2.4.2/IO
        kwd.add("ungetbyte"); // core-2.4.2/IO
        kwd.add("ungetc"); // core-2.4.2/IO
        kwd.add("write_nonblock"); // core-2.4.2/IO
        kwd.add("write"); // core-2.4.2/IO

        kwd.add("absolute_path"); // core-2.4.2/File
        kwd.add("atime"); // core-2.4.2/File
        kwd.add("basename"); // core-2.4.2/File
        kwd.add("birthtime"); // core-2.4.2/File
        kwd.add("blockdev?"); // core-2.4.2/File
        kwd.add("chardev?"); // core-2.4.2/File
        kwd.add("chmod"); // core-2.4.2/File
        kwd.add("chown"); // core-2.4.2/File
        kwd.add("ctime"); // core-2.4.2/File
        kwd.add("directory?"); // core-2.4.2/File
        kwd.add("dirname"); // core-2.4.2/File
        kwd.add("executable_real?"); // core-2.4.2/File
        kwd.add("executable?"); // core-2.4.2/File
        kwd.add("exist?"); // core-2.4.2/File
        kwd.add("exists?"); // core-2.4.2/File
        kwd.add("expand_path"); // core-2.4.2/File
        kwd.add("extname"); // core-2.4.2/File
        kwd.add("file?"); // core-2.4.2/File
        kwd.add("flock"); // core-2.4.2/File
        kwd.add("fnmatch?"); // core-2.4.2/File
        kwd.add("fnmatch"); // core-2.4.2/File
        kwd.add("ftype"); // core-2.4.2/File
        kwd.add("grpowned?"); // core-2.4.2/File
        kwd.add("identical?"); // core-2.4.2/File
        kwd.add("join"); // core-2.4.2/File
        kwd.add("lchmod"); // core-2.4.2/File
        kwd.add("lchown"); // core-2.4.2/File
        kwd.add("link"); // core-2.4.2/File
        kwd.add("lstat"); // core-2.4.2/File
        kwd.add("mkfifo"); // core-2.4.2/File
        kwd.add("mtime"); // core-2.4.2/File
        kwd.add("owned?"); // core-2.4.2/File
        kwd.add("path"); // core-2.4.2/File
        kwd.add("pipe?"); // core-2.4.2/File
        kwd.add("readable_real?"); // core-2.4.2/File
        kwd.add("readable?"); // core-2.4.2/File
        kwd.add("readlink"); // core-2.4.2/File
        kwd.add("realdirpath"); // core-2.4.2/File
        kwd.add("realpath"); // core-2.4.2/File
        kwd.add("rename"); // core-2.4.2/File
        kwd.add("setgid?"); // core-2.4.2/File
        kwd.add("setuid?"); // core-2.4.2/File
        kwd.add("size?"); // core-2.4.2/File
        kwd.add("socket?"); // core-2.4.2/File
        kwd.add("sticky?"); // core-2.4.2/File
        kwd.add("symlink?"); // core-2.4.2/File
        kwd.add("symlink"); // core-2.4.2/File
        kwd.add("to_path"); // core-2.4.2/File
        kwd.add("truncate"); // core-2.4.2/File
        kwd.add("umask"); // core-2.4.2/File
        kwd.add("unlink"); // core-2.4.2/File
        kwd.add("utime"); // core-2.4.2/File
        kwd.add("world_readable?"); // core-2.4.2/File
        kwd.add("world_writable?"); // core-2.4.2/File
        kwd.add("writable_real?"); // core-2.4.2/File
        kwd.add("writable?"); // core-2.4.2/File
        kwd.add("zero?"); // core-2.4.2/File

        kwd.add("arity"); // core-2.4.2/Proc
        kwd.add("call"); // core-2.4.2/Proc
        kwd.add("curry"); // core-2.4.2/Proc
        kwd.add("lambda?"); // core-2.4.2/Proc
        kwd.add("parameters"); // core-2.4.2/Proc
        kwd.add("source_location"); // core-2.4.2/Proc
        kwd.add("to_proc"); // core-2.4.2/Proc

        kwd.add("all_symbols"); // core-2.4.2/Symbol
        kwd.add("id2name"); // core-2.4.2/Symbol
        kwd.add("compile"); // core-2.4.2/Regexp
        kwd.add("escape"); // core-2.4.2/Regexp
        kwd.add("last_match"); // core-2.4.2/Regexp
        kwd.add("quote"); // core-2.4.2/Regexp
        kwd.add("union"); // core-2.4.2/Regexp
        kwd.add("casefold?"); // core-2.4.2/Regexp

        kwd.add("fixed_encoding?"); // core-2.4.2/Regexp
        kwd.add("named_captures"); // core-2.4.2/Regexp
        kwd.add("names"); // core-2.4.2/Regexp
        kwd.add("options"); // core-2.4.2/Regexp
        kwd.add("source"); // core-2.4.2/Regexp

        kwd.add("abs"); // core-2.4.2/Integer
        kwd.add("bit_length"); // core-2.4.2/Integer
        kwd.add("ceil"); // core-2.4.2/Integer
        kwd.add("coerce"); // core-2.4.2/Integer
        kwd.add("denominator"); // core-2.4.2/Integer
        kwd.add("digits"); // core-2.4.2/Integer
        kwd.add("div"); // core-2.4.2/Integer
        kwd.add("divmod"); // core-2.4.2/Integer
        kwd.add("downto"); // core-2.4.2/Integer
        kwd.add("even?"); // core-2.4.2/Integer
        kwd.add("fdiv"); // core-2.4.2/Integer
        kwd.add("floor"); // core-2.4.2/Integer
        kwd.add("gcd"); // core-2.4.2/Integer
        kwd.add("gcdlcm"); // core-2.4.2/Integer
        kwd.add("integer?"); // core-2.4.2/Integer
        kwd.add("lcm"); // core-2.4.2/Integer
        kwd.add("magnitude"); // core-2.4.2/Integer
        kwd.add("modulo"); // core-2.4.2/Integer
        kwd.add("numerator"); // core-2.4.2/Integer
        kwd.add("odd?"); // core-2.4.2/Integer
        kwd.add("pred"); // core-2.4.2/Integer
        kwd.add("rationalize"); // core-2.4.2/Integer
        kwd.add("remainder"); // core-2.4.2/Integer
        kwd.add("round"); // core-2.4.2/Integer
        kwd.add("times"); // core-2.4.2/Integer
        kwd.add("to_int"); // core-2.4.2/Integer
    }

    private Consts() {
    }

}
