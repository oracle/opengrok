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
 * Copyright (c) 2017, 2019, Chris Fraire <cfraire@me.com>.
 */
package org.opengrok.indexer.analysis;

import org.opengrok.indexer.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Represents a utility class to show the user details of {@link AnalyzerGuru}.
 */
public class AnalyzerGuruHelp {

    private AnalyzerGuruHelp() {
    }

    /**
     * Gets a reportable hunk of text that details
     * {@link AnalyzerGuru#getPrefixesMap()},
     * {@link AnalyzerGuru#getExtensionsMap()},
     * {@link AnalyzerGuru#getMagicsMap()}, and
     * {@link AnalyzerGuru#getAnalyzerFactoryMatchers()}.
     * @return a defined, multi-line String
     */
    public static String getUsage() {
        StringBuilder b = new StringBuilder();
        b.append("AnalyzerGuru prefixes:\n");
        byKey(AnalyzerGuru.getPrefixesMap()).forEach((kv) -> {
            b.append(String.format("%-10s : %s\n", reportable(kv.key + '*'),
                reportable(kv.fac)));
        });

        b.append("\nAnalyzerGuru extensions:\n");
        byKey(AnalyzerGuru.getExtensionsMap()).forEach((kv) -> {
            b.append(String.format("*.%-7s : %s\n",
                reportable(kv.key.toLowerCase(Locale.ROOT)),
                reportable(kv.fac)));
        });

        b.append("\nAnalyzerGuru magic strings:\n");
        byFactory(AnalyzerGuru.getMagicsMap()).forEach((kv) -> {
            b.append(String.format("%-23s : %s\n", reportable(kv.key),
                reportable(kv.fac)));
        });

        b.append("\nAnalyzerGuru magic matchers:\n");
        AnalyzerGuru.getAnalyzerFactoryMatchers().forEach((m) -> {
            if (m.getIsPreciseMagic()) {
                b.append(reportable(m));
            }
        });
        AnalyzerGuru.getAnalyzerFactoryMatchers().forEach((m) -> {
            if (!m.getIsPreciseMagic()) {
                b.append(reportable(m));
            }
        });

        return b.toString();
    }

    private static String reportable(AnalyzerFactory fac) {
        String nm = fac.getName();
        return nm == null ? fac.getClass().getSimpleName() : nm;
    }

    private static String reportable(String value) {
        if (value.startsWith("#!")) {
            return value;
        }

        boolean allAsciiPrintable = true;
        for (int i = 0; i < value.length(); ++i) {
            char c = value.charAt(i);
            if (c < ' ' || c > '~') {
                allAsciiPrintable = false;
                break;
            }
        }
        if (allAsciiPrintable) {
            return value;
        }

        StringBuilder b = new StringBuilder();
        int i = 0;
        while (i < value.length()) {
            int cp = Character.codePointAt(value, i);
            i += Character.charCount(cp);
            if (Character.isAlphabetic(cp)) {
                b.appendCodePoint(cp);
            } else {
                b.append("\\{");
                b.append(String.format("%x", cp));
                b.append("}");
            }
        }
        return b.toString();
    }

    private static String reportable(FileAnalyzerFactory.Matcher m) {
        final String MATCHER_FMT = "%-11s %-1s %s\n";
        StringBuilder b = new StringBuilder();
        String[] lines = splitLines(m.description(), 66);
        for (int i = 0; i < lines.length; ++i) {
            if (i < 1) {
                b.append(String.format(MATCHER_FMT, reportable(m.forFactory()),
                    ":", lines[i]));
            } else {
                b.append(String.format(MATCHER_FMT, "", "", lines[i]));
            }
        }
        return b.toString();
    }

    private static String[] splitLines(String str, int width) {
        List<String> res = new ArrayList<>();
        StringBuilder b = new StringBuilder();

        int llen = 0;
        int i = 0;
        while (i < str.length()) {
            int wlen = StringUtils.whitespaceOrControlLength(str, i, false);
            if (wlen > 0) {
                String word = str.substring(i, i + wlen);
                if (llen < 1) {
                    b.append(word);
                    llen = word.length();
                } else if (llen + 1 + wlen <= width) {
                    b.append(" ");
                    b.append(word);
                    llen += word.length() + 1;
                } else {
                    res.add(b.toString());
                    b.setLength(0);
                    b.append(word);
                    llen = word.length();
                }
                i += wlen;
            }

            int slen = StringUtils.whitespaceOrControlLength(str, i, true);
            i += slen;
        }
        if (b.length() > 0) {
            res.add(b.toString());
            b.setLength(0);
        }

        return res.stream().toArray(String[]::new);
    }

    private static List<MappedFactory> byKey(
        Map<String, AnalyzerFactory> mapped) {

        List<MappedFactory> res = mapped.entrySet().stream().map((t) -> {
            return new MappedFactory(t.getKey(), t.getValue());
        }).collect(Collectors.toList());

        res.sort((mf1, mf2) -> {
            return mf1.key.toLowerCase(Locale.ROOT).compareTo(
                mf2.key.toLowerCase(Locale.ROOT));
        });
        return res;
    }

    private static List<MappedFactory> byFactory(
        Map<String, AnalyzerFactory> mapped) {

        List<MappedFactory> res = mapped.entrySet().stream().map((t) -> {
            return new MappedFactory(t.getKey(), t.getValue());
        }).collect(Collectors.toList());

        res.sort((mf1, mf2) -> {
            String r1 = reportable(mf1.fac);
            String r2 = reportable(mf2.fac);
            int cmp = r1.toLowerCase(Locale.ROOT).compareTo(
                r2.toLowerCase(Locale.ROOT));
            if (cmp != 0) {
                return cmp;
            }
            return mf1.key.toLowerCase(Locale.ROOT).compareTo(
                mf2.key.toLowerCase(Locale.ROOT));
        });
        return res;
    }

    private static class MappedFactory {
        public final String key;
        public final AnalyzerFactory fac;

        MappedFactory(String key, AnalyzerFactory fac) {
            this.key = key;
            this.fac = fac;
        }
    }
}
