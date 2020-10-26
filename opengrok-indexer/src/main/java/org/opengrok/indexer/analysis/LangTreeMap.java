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

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Represents an implementation of {@link LangMap} using structures with natural
 * ordering of file specifications.
 */
public class LangTreeMap implements LangMap {

    private final TreeMap<String, String> langMap = new TreeMap<>();
    private final TreeSet<String> exclusions = new TreeSet<>();

    /**
     * {@inheritDoc}
     */
    @Override
    public void clear() {
        langMap.clear();
        exclusions.clear();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void add(String fileSpec, String ctagsLang) {
        if (fileSpec == null) {
            throw new IllegalArgumentException("fileSpec is required");
        }
        if (ctagsLang == null) {
            // Some analyzers may not have a defined, associated ctags lang.
            return;
        }
        validateFileSpec(fileSpec);
        langMap.put(fileSpec, ctagsLang);
        exclusions.remove(fileSpec);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void exclude(String fileSpec) {
        if (fileSpec == null) {
            throw new IllegalArgumentException("fileSpec is required");
        }
        validateFileSpec(fileSpec);
        exclusions.add(fileSpec);
        langMap.remove(fileSpec);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<String> getCtagsArgs() {
        List<String> result = new ArrayList<>();

        for (Map.Entry<String, String> entry : langMap.entrySet()) {
            if (entry.getKey().startsWith(".")) {
                result.add(asExtensionMap(entry.getKey(), entry.getValue()));
            } else {
                result.add(asPrefixMap(entry.getKey(), entry.getValue()));
            }
        }
        for (String entry : exclusions) {
            if (entry.startsWith(".")) {
                result.addAll(asExtensionExclusion(entry));
            } else {
                result.add(asPrefixExclusion(entry));
            }
        }
        return result;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public LangMap mergeSecondary(LangMap other) {
        if (other == null) {
            throw new IllegalArgumentException("other is null");
        }

        LangTreeMap unified = new LangTreeMap();
        unified.langMap.putAll(langMap);
        unified.exclusions.addAll(exclusions);

        for (Map.Entry<String, String> entry : other.getAdditions().entrySet()) {
            if (!langMap.containsKey(entry.getKey()) && !exclusions.contains(entry.getKey())) {
                unified.langMap.put(entry.getKey(), entry.getValue());
            }
        }
        for (String entry : other.getExclusions()) {
            if (!langMap.containsKey(entry)) {
                unified.exclusions.add(entry);
            }
        }

        return unified;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public LangMap unmodifiable() {
        return new LangMap() {
            final LangMap source = LangTreeMap.this;

            @Override
            public void clear() {
                throw new UnsupportedOperationException();
            }

            @Override
            public void add(String fileSpec, String ctagsLang) {
                throw new UnsupportedOperationException();
            }

            @Override
            public void exclude(String fileSpec) {
                throw new UnsupportedOperationException();
            }

            @Override
            public List<String> getCtagsArgs() {
                return source.getCtagsArgs();
            }

            @Override
            public LangMap mergeSecondary(LangMap other) {
                return source.mergeSecondary(other);
            }

            @Override
            public Map<String, String> getAdditions() {
                return source.getAdditions();
            }

            @Override
            public Set<String> getExclusions() {
                return source.getExclusions();
            }

            @Override
            public LangMap unmodifiable() {
                return this;
            }
        };
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Map<String, String> getAdditions() {
        return Collections.unmodifiableMap(langMap);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Set<String> getExclusions() {
        return Collections.unmodifiableSet(exclusions);
    }

    private void validateFileSpec(String fileSpec) {
        if (fileSpec.startsWith(".")) {
            if (fileSpec.indexOf(".", 1) > 0) {
                throw new IllegalArgumentException("Invalid extension " + fileSpec);
            }
        }
    }

    private String asLangmapOption(String spec, String ctagsLang) {
        return "--langmap=" + ctagsLang + ":+" + spec;
    }

    private String asMapAllExclusion(String spec) {
        return "--map-all=-" + spec;
    }

    private String asExtensionMap(String fileSpec, String ctagsLang) {
        return asLangmapOption(asAllCasesExtension(fileSpec), ctagsLang);
    }

    private String asPrefixMap(String fileSpec, String ctagsLang) {
        return asLangmapOption(asAllCasesPrefix(fileSpec), ctagsLang);
    }

    private Collection<? extends String> asExtensionExclusion(String fileSpec) {
        List<String> extensionList = asAllCasesExtensionList(fileSpec);
        ArrayList<String> result = new ArrayList<>();
        for (String ext : extensionList) {
            result.add(asMapAllExclusion(ext));
        }
        return result;
    }

    private String asPrefixExclusion(String fileSpec) {
        return asMapAllExclusion(asAllCasesPrefix(fileSpec));
    }

    private String asAllCasesPrefix(String fileSpec) {
        String lower = checkNaiveCaseFolding(fileSpec);
        if (lower == null) {
            /*
             * If naive case-folding doesn't transform the string, then return
             * just the original fileSpec enclosed in parentheses to indicate
             * a file name pattern with a trailing wildcard.
             */
            return "(" + fileSpec + "*)";
        }

        StringBuilder result = new StringBuilder("(");
        for (int i = 0; i < lower.length(); ++i) {
            char c = lower.charAt(i);
            char C = Character.toUpperCase(c);
            if (c == C) {
                result.append(c);
            } else {
                result.append("[");
                result.append(c);
                result.append(C);
                result.append("]");
            }
        }
        result.append("*)");
        return result.toString();
    }

    private String asAllCasesExtension(String extension) {
        List<String> extensionList = asAllCasesExtensionList(extension);
        return String.join("", extensionList); // no delimiter
    }

    private List<String> asAllCasesExtensionList(String extension) {
        String lower = checkNaiveCaseFolding(extension);
        if (lower == null) {
            /*
             * If naive case-folding doesn't transform the string, then return
             * just the original extension.
             */
            return Collections.singletonList(extension);
        }

        /*
         * On case-sensitive filesystems, ctags is also case-sensitive.
         * OpenGrok, however, matches filename extensions and prefixes after
         * first upper-casing the values. To fully accord the two match
         * techniques on a technical basis would mean specifying to ctags e.g.
         * .pas.Pas.pAs.paS.PAs.PaS.PAS but for pragmatism we'll just specify
         * the lower-, upper-, and initial-case forms.
         */
        ArrayList<String> result = new ArrayList<>();
        result.add(lower);
        // If e.g. .PAS but not .C ...
        if (lower.length() > 2) {
            StringBuilder initial = new StringBuilder(lower);
            initial.setCharAt(1, Character.toUpperCase(lower.charAt(1)));
            result.add(initial.toString());
        }
        result.add(lower.toUpperCase(Locale.ROOT));
        return result;
    }

    /**
     * @return the lower-case transformation of {@code value}
     */
    private static String checkNaiveCaseFolding(String value) {
        String lower = value.toLowerCase(Locale.ROOT);
        String upper = value.toUpperCase(Locale.ROOT);
        String upper2 = lower.toUpperCase(Locale.ROOT);
        // Build upper3 character-by-character toUpperCase().
        StringBuilder upper3 = new StringBuilder(value.length());
        for (int i = 0; i < lower.length(); ++i) {
            upper3.append(Character.toUpperCase(lower.charAt(i)));
        }
        if (lower.length() != upper.length() || lower.length() != value.length() ||
                !upper.equals(upper2) || !upper.equals(upper3.toString())) {
            /*
             * If naive case-folding doesn't transform the string, then return
             * null to indicate to use the original value by itself.
             */
            return null;
        }
        return lower;
    }
}
