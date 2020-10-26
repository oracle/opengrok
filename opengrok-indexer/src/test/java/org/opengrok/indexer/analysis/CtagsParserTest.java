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
 * Copyright (c) 2016, 2020, Oracle and/or its affiliates. All rights reserved.
 * Portions Copyright (c) 2017, Chris Fraire <cfraire@me.com>.
 */
package org.opengrok.indexer.analysis;

import static org.junit.Assert.assertEquals;
import org.junit.Test;

/**
 *
 * @author Lubos Kosco
 */
public class CtagsParserTest {

    @Test
    public void ctags_vs_universal_ctags() throws Exception {
        String universal_ctags_c = "TEST\tsample.c\t/^#define TEST($/;\"\tmacro\tline:6\tsignature:(x)\n"
                + "foo\tsample.c\t/^int foo(int a, int b) {$/;\"\tfunction\tline:8\ttyperef:typename:int\tsignature:(int a, int b)\n"
                + "c\tsample.c\t/^    int c;$/;\"\tlocal\tline:13\tfunction:foo\ttyperef:typename:int\n"
                + "msg\tsample.c\t/^    const char *msg = \"this is } sample { string\";$/;\"\tlocal\tline:14\tfunction:foo"
                + "\ttyperef:typename:const char *\n"
                + "bar\tsample.c\t/^int bar(int x \\/* } *\\/)$/;\"\tfunction\tline:24\ttyperef:typename:int\tsignature:(int x )\n"
                + "d\tsample.c\t/^    int d;$/;\"\tlocal\tline:27\tfunction:bar\ttyperef:typename:int\n"
                + "f\tsample.c\t/^    int f;$/;\"\tlocal\tline:28\tfunction:bar\ttyperef:typename:int\n"
                + "main\tsample.c\t/^int main(int argc, char *argv[]) {$/;\"\tfunction\tline:41\ttyperef:typename:int"
                + "\tsignature:(int argc, char *argv[])\n"
                + "res\tsample.c\t/^    int res;$/;\"\tlocal\tline:42\tfunction:main\ttyperef:typename:int";
        String ctags_c = "TEST\tsample.c\t6;\"\tmacro\tline:6\n" // this has wrong address so it shouldn't be detected
                + "foo\tsample.c\t/^int foo(int a, int b) {$/;\"\tfunction\tline:8\tsignature:(int a, int b)\n"
                + "c\tsample.c\t/^    int c;$/;\"\tlocal\tline:13\n"
                + "msg\tsample.c\t/^    const char *msg = \"this is } sample { string\";$/;\"\tlocal\tline:14\n"
                + "bar\tsample.c\t/^int bar(int x \\/* } *\\/)$/;\"\tfunction\tline:24\tsignature:(int x )\n"
                + "d\tsample.c\t/^    int d;$/;\"\tlocal\tline:27\n"
                + "f\tsample.c\t/^    int f;$/;\"\tlocal\tline:28\n"
                + "main\tsample.c\t/^int main(int argc, char *argv[]) {$/;\"\tfunction\tline:41\tsignature:(int argc, char *argv[])\n"
                + "res\tsample.c\t/^    int res;$/;\"\tlocal\tline:42";
        Ctags lctags = new Ctags();
        Definitions ucDefs = lctags.testCtagsParser(universal_ctags_c);

        Definitions cDefs = lctags.testCtagsParser(ctags_c);

        assertEquals(13, cDefs.getTags().size());
        assertEquals(15, ucDefs.getTags().size());

        String uc_cxx = "TEST\tsample.cxx\t/^#define TEST($/;\"\tmacro\tline:7\tsignature:(x)\n"
                + "SomeClass\tsample.cxx\t/^class SomeClass {$/;\"\tclass\tline:9\n"
                + "SomeClass\tsample.cxx\t/^    SomeClass() \\/* I'm constructor *\\/$/;\"\tfunction\tline:11\tclass:SomeClass\tsignature:()\n"
                + "~SomeClass\tsample.cxx\t/^    ~SomeClass() \\/\\/ destructor$/;\"\tfunction\tline:17\tclass:SomeClass\tsignature:()\n"
                + "MemberFunc\tsample.cxx\t/^    int MemberFunc(int a, int b) const {$/;\"\tfunction\tline:22"
                + "\tclass:SomeClass\ttyperef:typename:int\tsignature:(int a, int b) const\n"
                + "operator ++\tsample.cxx\t/^    int operator++(int) {$/;\"\tfunction\tline:27\tclass:SomeClass"
                + "\ttyperef:typename:int\tsignature:(int)\n"
                + "TemplateMember\tsample.cxx\t/^    size_t TemplateMember(std::vector<T>& v) {$/;\"\tfunction\tline:32"
                + "\tclass:SomeClass\ttyperef:typename:size_t\tsignature:(std::vector<T>& v)\n"
                + "attr_\tsample.cxx\t/^    int attr_;$/;\"\tmember\tline:37\tclass:SomeClass\ttyperef:typename:int\n"
                + "ns1\tsample.cxx\t/^namespace ns1 {$/;\"\tnamespace\tline:40\n"
                + "NamespacedClass\tsample.cxx\t/^    class NamespacedClass {$/;\"\tclass\tline:42\tnamespace:ns1\n"
                + "SomeFunc\tsample.cxx\t/^        static void SomeFunc(const std::string& arg) {$/;\"\tfunction\tline:44"
                + "\tclass:ns1::NamespacedClass\ttyperef:typename:void\tsignature:(const std::string& arg)\n"
                + "ns2\tsample.cxx\t/^    namespace ns2 {$/;\"\tnamespace\tline:49\tnamespace:ns1\n"
                + "foo\tsample.cxx\t/^        int foo(int a, int b) {$/;\"\tfunction\tline:51\tnamespace:ns1::ns2"
                + "\ttyperef:typename:int\tsignature:(int a, int b)\n"
                + "t\tsample.cxx\t/^            SomeClass t;$/;\"\tlocal\tline:52\tfunction:ns1::ns2::foo\ttyperef:typename:SomeClass\n"
                + "bar\tsample.cxx\t/^int bar(int x \\/* } *\\/)$/;\"\tfunction\tline:59\ttyperef:typename:int\tsignature:(int x )\n"
                + "d\tsample.cxx\t/^    int d;$/;\"\tlocal\tline:62\tfunction:bar\ttyperef:typename:int\n"
                + "f\tsample.cxx\t/^    int f;$/;\"\tlocal\tline:63\tfunction:bar\ttyperef:typename:int\n"
                + "main\tsample.cxx\t/^int main(int argc, char *argv[]) {$/;\"\tfunction\tline:76\ttyperef:typename:int"
                + "\tsignature:(int argc, char *argv[])\n"
                + "c\tsample.cxx\t/^    SomeClass c;$/;\"\tlocal\tline:77\tfunction:main\ttyperef:typename:SomeClass\n"
                + "res\tsample.cxx\t/^    int res;$/;\"\tlocal\tline:78\tfunction:main\ttyperef:typename:int";

        String c_cxx = "TEST\tsample.cxx\t7;\"\tmacro\tline:7\n"
                + "SomeClass\tsample.cxx\t/^class SomeClass {$/;\"\tclass\tline:9\n"
                + "SomeClass\tsample.cxx\t/^    SomeClass() \\/* I'm constructor *\\/$/;\"\tfunction\tline:11\tclass:SomeClass\tsignature:()\n"
                + "endl\tsample.cxx\t/^        std::cout << \"Hello\" << std::endl;$/;\"\tmember\tline:14\tclass:SomeClass::std\n"
                + "~SomeClass\tsample.cxx\t/^    ~SomeClass() \\/\\/ destructor$/;\"\tfunction\tline:17\tclass:SomeClass\tsignature:()\n"
                + "endl\tsample.cxx\t/^        std::cout << \"Bye\" << std::endl;$/;\"\tmember\tline:19\tclass:SomeClass::std\n"
                + "MemberFunc\tsample.cxx\t/^    int MemberFunc(int a, int b) const {$/;\"\tfunction\tline:22\tclass:SomeClass"
                + "\tsignature:(int a, int b) const\n"
                + "operator ++\tsample.cxx\t/^    int operator++(int) {$/;\"\tfunction\tline:27\tclass:SomeClass\tsignature:(int)\n"
                + "TemplateMember\tsample.cxx\t/^    size_t TemplateMember(std::vector<T>& v) {$/;\"\tfunction\tline:32"
                + "\tclass:SomeClass\tsignature:(std::vector<T>& v)\n"
                + "attr_\tsample.cxx\t/^    int attr_;$/;\"\tmember\tline:37\tclass:SomeClass\n"
                + "ns1\tsample.cxx\t/^namespace ns1 {$/;\"\tnamespace\tline:40\n"
                + "NamespacedClass\tsample.cxx\t/^    class NamespacedClass {$/;\"\tclass\tline:42\tnamespace:ns1\n"
                + "SomeFunc\tsample.cxx\t/^        static void SomeFunc(const std::string& arg) {$/;\"\tfunction\tline:44"
                + "\tclass:ns1::NamespacedClass\tsignature:(const std::string& arg)\n"
                + "arg\tsample.cxx\t/^            std::cout << arg;$/;\"\tlocal\tline:45\n"
                + "ns2\tsample.cxx\t/^    namespace ns2 {$/;\"\tnamespace\tline:49\tnamespace:ns1\n"
                + "foo\tsample.cxx\t/^        int foo(int a, int b) {$/;\"\tfunction\tline:51\tnamespace:ns1::ns2\tsignature:(int a, int b)\n"
                + "t\tsample.cxx\t/^            SomeClass t;$/;\"\tlocal\tline:52\n"
                + "bar\tsample.cxx\t/^int bar(int x \\/* } *\\/)$/;\"\tfunction\tline:59\tsignature:(int x )\n"
                + "d\tsample.cxx\t/^    int d;$/;\"\tlocal\tline:62\n"
                + "f\tsample.cxx\t/^    int f;$/;\"\tlocal\tline:63\n"
                + "endl\tsample.cxx\t/^    std::cout << TEST(\"test { message|$#@$!!#\") << std::endl;$/;\"\tmember\tline:64\tclass:std\n"
                + "main\tsample.cxx\t/^int main(int argc, char *argv[]) {$/;\"\tfunction\tline:76\tsignature:(int argc, char *argv[])\n"
                + "c\tsample.cxx\t/^    SomeClass c;$/;\"\tlocal\tline:77\n"
                + "res\tsample.cxx\t/^    int res;$/;\"\tlocal\tline:78\n"
                + "endl\tsample.cxx\t/^    std::cout << \"this is just a {sample}}\" << std::endl;$/;\"\tmember\tline:79\tclass:std\n"
                + "endl\tsample.cxx\t/^    std::cout << \"result = {\" << res << \"}\" << std::endl;$/;\"\tmember\tline:82\tclass:std\n"
                + "endl\tsample.cxx\t/^    std::cout << c.MemberFunc(1, 2) << std::endl;$/;\"\tmember\tline:84\tclass:std\n"
                + "endl\tsample.cxx\t/^    std::cout << c++ << std::endl;$/;\"\tmember\tline:85\tclass:std";

        ucDefs = lctags.testCtagsParser(uc_cxx);

        cDefs = lctags.testCtagsParser(c_cxx);

        assertEquals(37, cDefs.getTags().size());
        assertEquals(31, ucDefs.getTags().size());

        String uc_java = "org.opengrok.indexer.analysis.java\thome/jobs/OpenGrokAnt/workspace/testdata/sources/java/Sample.java"
                + "\t/^package org.opengrok.indexer.analysis.java;$/;\"\tpackage\tline:23\n"
                + "Sample\thome/jobs/OpenGrokAnt/workspace/testdata/sources/java/Sample.java"
                + "\t/^public class Sample {$/;\"\tclass\tline:25\n"
                + "MY_MEMBER\thome/jobs/OpenGrokAnt/workspace/testdata/sources/java/Sample.java\t/^"
                + "    static private String MY_MEMBER = \"value\";$/;\"\tfield\tline:27\tclass:Sample\n"
                + "Sample\thome/jobs/OpenGrokAnt/workspace/testdata/sources/java/Sample.java\t/^"
                + "    public Sample() {$/;\"\tmethod\tline:29\tclass:Sample\tsignature:()\n"
                + "Method\thome/jobs/OpenGrokAnt/workspace/testdata/sources/java/Sample.java\t/^"
                + "    public int Method(int arg) {$/;\"\tmethod\tline:33\tclass:Sample\tsignature:(int arg)\n"
                + "res\thome/jobs/OpenGrokAnt/workspace/testdata/sources/java/Sample.java\t/^"
                + "        int res = 5;$/;\"\tlocal\tline:34\n"
                + "i\thome/jobs/OpenGrokAnt/workspace/testdata/sources/java/Sample.java\t/^"
                + "        InnerClass i = new InnerClass();$/;\"\tlocal\tline:38\n"
                + "AbstractMethod\thome/jobs/OpenGrokAnt/workspace/testdata/sources/java/Sample.java\t/^"
                + "    public abstract int AbstractMethod(int test);$/;\"\tmethod\tline:43\tclass:Sample\tsignature:(int test)\n"
                + "InnerClass\thome/jobs/OpenGrokAnt/workspace/testdata/sources/java/Sample.java\t/^"
                + "    private class InnerClass {$/;\"\tclass\tline:45\tclass:Sample\n"
                + "InnerMethod\thome/jobs/OpenGrokAnt/workspace/testdata/sources/java/Sample.java\t/^"
                + "        public String InnerMethod() {$/;\"\tmethod\tline:47\tclass:Sample.InnerClass\tsignature:()\n"
                + "main\thome/jobs/OpenGrokAnt/workspace/testdata/sources/java/Sample.java\t/^"
                + "    public static void main(String args[]) {$/;\"\tmethod\tline:60\tclass:Sample\tsignature:(String args[])\n"
                + "num1\thome/jobs/OpenGrokAnt/workspace/testdata/sources/java/Sample.java\t/^"
                + "        int num1, num2;$/;\"\tlocal\tline:61\n"
                + "num2\thome/jobs/OpenGrokAnt/workspace/testdata/sources/java/Sample.java\t/^"
                + "        int num1, num2;$/;\"\tlocal\tline:61";
        //note for java we disable local vars for exuberant ctags, but enable for universal ones
        String c_java = "org.opengrok.indexer.analysis.java\thome/jobs/OpenGrokAnt/workspace/testdata/sources/java/Sample.java"
                + "\t/^package org.opengrok.indexer.analysis.java;$/;\"\tpackage\tline:23\n"
                + "Sample\thome/jobs/OpenGrokAnt/workspace/testdata/sources/java/Sample.java"
                + "\t/^public class Sample {$/;\"\tclass\tline:25\n"
                + "MY_MEMBER\thome/jobs/OpenGrokAnt/workspace/testdata/sources/java/Sample.java\t/^"
                + "    static private String MY_MEMBER = \"value\";$/;\"\tfield\tline:27\tclass:Sample\n"
                + "Sample\thome/jobs/OpenGrokAnt/workspace/testdata/sources/java/Sample.java\t/^"
                + "    public Sample() {$/;\"\tmethod\tline:29\tclass:Sample\tsignature:()\n"
                + "Method\thome/jobs/OpenGrokAnt/workspace/testdata/sources/java/Sample.java\t/^"
                + "    public int Method(int arg) {$/;\"\tmethod\tline:33\tclass:Sample\tsignature:(int arg)\n"
                + "AbstractMethod\thome/jobs/OpenGrokAnt/workspace/testdata/sources/java/Sample.java\t/^"
                + "    public abstract int AbstractMethod(int test);$/;\"\tmethod\tline:43\tclass:Sample\tsignature:(int test)\n"
                + "InnerClass\thome/jobs/OpenGrokAnt/workspace/testdata/sources/java/Sample.java\t/^"
                + "    private class InnerClass {$/;\"\tclass\tline:45\tclass:Sample\n"
                + "InnerMethod\thome/jobs/OpenGrokAnt/workspace/testdata/sources/java/Sample.java\t/^"
                + "        public String InnerMethod() {$/;\"\tmethod\tline:47\tclass:Sample.InnerClass\tsignature:()\n"
                + "main\thome/jobs/OpenGrokAnt/workspace/testdata/sources/java/Sample.java\t/^"
                + "    public static void main(String args[]) {$/;\"\tmethod\tline:60\tclass:Sample\tsignature:(String args[])";

        ucDefs = lctags.testCtagsParser(uc_java);

        cDefs = lctags.testCtagsParser(c_java);

        assertEquals(12, cDefs.getTags().size());
        assertEquals(16, ucDefs.getTags().size());
    }

}
