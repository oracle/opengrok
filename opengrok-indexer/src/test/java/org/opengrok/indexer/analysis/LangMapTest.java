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
package org.opengrok.indexer.analysis;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Represents a container for tests of {@link LangMap}.
 */
class LangMapTest {

    @Test
    void testEmptyMap() {
        LangMap map = new LangTreeMap();
        List<String> args = map.getCtagsArgs();
        assertTrue(args.isEmpty(), "args should be empty");
    }

    @Test
    void testMakefile() {
        LangMap map = new LangTreeMap();
        map.add("Makefile", "Sh");
        List<String> args = map.getCtagsArgs();
        assertArrayEquals(new Object[]{"--langmap=Sh:+([mM][aA][kK][eE][fF][iI][lL][eE]*)"}, args.toArray(),
                "args should have one all-case Makefile entry");
    }

    @Test
    void test1Prefix2Extension() {
        LangMap map = new LangTreeMap();
        map.add("Makefile", "Sh");
        map.add(".FOO", "XML");
        map.add(".B", "Basic");
        List<String> args = map.getCtagsArgs();
        assertArrayEquals(new Object[] {
                "--langmap=Basic:+.b.B",
                "--langmap=XML:+.foo.Foo.FOO",
                "--langmap=Sh:+([mM][aA][kK][eE][fF][iI][lL][eE]*)"
        }, args.toArray(), "args should have specific, multiple entries");
    }

    @Test
    void testNonTrivialCaseFolding() {
        LangMap map = new LangTreeMap();
        map.add("groß", "Sh");
        List<String> args = map.getCtagsArgs();
        assertArrayEquals(new Object[]{"--langmap=Sh:+(groß*)"}, args.toArray(), "args should have one original-case groß entry");
    }

    @Test
    void testMerge1() {
        LangMap map1 = new LangTreeMap();
        map1.add("Makefile", "Sh");
        map1.add(".B", "Basic");

        LangMap map2 = new LangTreeMap();
        map2.add(".B", "C");
        map2.add(".ZZZ", "C++");

        LangMap map3 = map1.mergeSecondary(map2);
        List<String> args = map3.getCtagsArgs();
        assertArrayEquals(new Object[] {
                "--langmap=Basic:+.b.B",
                "--langmap=C++:+.zzz.Zzz.ZZZ",
                "--langmap=Sh:+([mM][aA][kK][eE][fF][iI][lL][eE]*)"
        }, args.toArray(), "args should have specific, multiple entries");
    }

    @Test
    void testMerge2() {
        LangMap map1 = new LangTreeMap();
        map1.exclude(".B");

        LangMap map2 = new LangTreeMap();
        map2.add(".B", "C");
        map2.add(".ZZZ", "C++");

        LangMap map3 = map1.mergeSecondary(map2);
        List<String> args = map3.getCtagsArgs();
        assertArrayEquals(new Object[] {
                "--langmap=C++:+.zzz.Zzz.ZZZ",
                "--map-all=-.b",
                "--map-all=-.B"
        }, args.toArray(), "args should have specific, multiple entries");
    }

    @Test
    void testExcludeMakefile() {
        LangMap map = new LangTreeMap();
        map.exclude("Makefile");
        List<String> args = map.getCtagsArgs();
        assertArrayEquals(new Object[]{"--map-all=-([mM][aA][kK][eE][fF][iI][lL][eE]*)"}, args.toArray(),
                "args should have one all-case Makefile entry");
    }

    @Test
    void testExcludeExtension() {
        LangMap map = new LangTreeMap();
        map.exclude(".d");
        List<String> args = map.getCtagsArgs();
        assertArrayEquals(new Object[] {
                "--map-all=-.d",
                "--map-all=-.D"
        }, args.toArray(), "args should have specific, multiple entries");
    }

    @Test
    void testExcludeExtensionThenAdd() {
        LangMap map = new LangTreeMap();
        map.exclude(".d");
        map.add(".d", "D");
        List<String> args = map.getCtagsArgs();
        assertArrayEquals(new Object[]{"--langmap=D:+.d.D"}, args.toArray(), "args should have a specified entry");
    }

    @Test
    void testAddExtensionThenExclude() {
        LangMap map = new LangTreeMap();
        map.add(".d", "D");
        map.exclude(".d");
        List<String> args = map.getCtagsArgs();
        assertArrayEquals(new Object[] {
                "--map-all=-.d",
                "--map-all=-.D"
        }, args.toArray(), "args should have specific, multiple entries");
    }

    @Test
    void testBadExtensionFileSpec() {
        LangMap map = new LangTreeMap();

        assertThrows(IllegalArgumentException.class, () -> map.add(".c.in", "foo"));
    }

    @Test
    void testImmutabilityOfUnmodifiable1() {
        LangMap map = new LangTreeMap();
        LangMap map2 = map.unmodifiable();

        assertThrows(UnsupportedOperationException.class, () -> map2.add(".FOO", "foo"));
    }

    @Test
    void testImmutabilityOfUnmodifiable2() {
        LangMap map = new LangTreeMap();
        LangMap map2 = map.unmodifiable();

        assertThrows(UnsupportedOperationException.class, () -> map2.exclude(".FOO"));
    }

    @Test
    void testImmutabilityOfAdditionsView() {
        LangMap map = new LangTreeMap();
        Map<String, String> additions = map.getAdditions();

        assertThrows(UnsupportedOperationException.class, additions::clear);
    }

    @Test
    void testImmutabilityOfExclusionsView() {
        LangMap map = new LangTreeMap();
        Set<String> exclusions = map.getExclusions();

        assertThrows(UnsupportedOperationException.class, exclusions::clear);
    }
}
