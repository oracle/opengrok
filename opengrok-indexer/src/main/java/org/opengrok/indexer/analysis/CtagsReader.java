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
 * Copyright (c) 2005, 2019, Oracle and/or its affiliates. All rights reserved.
 * Portions Copyright (c) 2017, 2020, Chris Fraire <cfraire@me.com>.
 */
package org.opengrok.indexer.analysis;

import java.util.EnumMap;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.opengrok.indexer.logger.LoggerFactory;
import org.opengrok.indexer.util.SourceSplitter;

/**
 * Represents a reader of output from runs of ctags.
 */
public class CtagsReader {

    /**
     * Matches the Unicode word that occurs last in a string, ignoring any
     * trailing whitespace or non-word characters, and makes it accessible as
     * the first capture, {@code mtch.groups(1)}.
     */
    private static final Pattern LAST_UWORD = Pattern.compile("(?U)(\\w+)[\\W\\s]*$");

    /**
     * Matches a Unicode word character.
     */
    private static final Pattern WORD_CHAR = Pattern.compile("(?U)\\w");

    private static final Logger LOGGER = LoggerFactory.getLogger(
        CtagsReader.class);

    /** A value indicating empty method body in tags, so skip it. */
    private static final int MIN_METHOD_LINE_LENGTH = 6;

    /**
     * 96 is used by universal ctags for some lines, but it's too low,
     * OpenGrok can theoretically handle 50000 with 8G heap. Also this might
     * break scopes functionality, if set too low.
     */
    private static final int MAX_METHOD_LINE_LENGTH = 1030;

    private static final int MAX_CUT_LENGTH = 2000;

    /**
     * E.g. krb5 src/kdc/kdc_authdata.c has a signature for handle_authdata()
     * split across twelve lines, so use double that number.
     */
    private static final int MAX_CUT_LINES = 24;

    private final EnumMap<tagFields, String> fields = new EnumMap<>(
        tagFields.class);

    private final Definitions defs = new Definitions();

    private Supplier<SourceSplitter> splitterSupplier;
    private boolean triedSplitterSupplier;
    private SourceSplitter splitter;
    private long cutCacheKey;
    private String cutCacheValue;

    private int tabSize;

    /**
     * This should mimic
     * https://github.com/universal-ctags/ctags/blob/master/docs/format.rst or
     * http://ctags.sourceforge.net/FORMAT (for backwards compatibility).
     * Uncomment only those that are used ... (to avoid populating the hashmap
     * for every record).
     */
    public enum tagFields {
//        ARITY("arity"),
        CLASS("class"),
        //        INHERIT("inherit"), //this is not defined in above format docs, but both universal and exuberant ctags use it
        //        INTERFACE("interface"), //this is not defined in above format docs, but both universal and exuberant ctags use it
        //        ENUM("enum"),
        //        FILE("file"),
        //        FUNCTION("function"),
        //        KIND("kind"),
        LINE("line"),
        //        NAMESPACE("namespace"), //this is not defined in above format docs, but both universal and exuberant ctags use it
        //        PROGRAM("program"), //this is not defined in above format docs, but both universal and exuberant ctags use it
        SIGNATURE("signature");
//        STRUCT("struct"),
//        TYPEREF("typeref"),
//        UNION("union");

        //NOTE: if you edit above, always consult below charCmpEndOffset
        private final String name;

        /**
         * Sets {@code this.name} to {@code name}.
         * @param name the assignment value
         */
        tagFields(String name) {
            this.name = name;
        }

        /**
         * N.b. make this MAX. 8 chars! (backwards compat to DOS/Win).
         * 1 - means only 2 first chars are compared.
         * <p>This is very important, we only compare that amount of chars from
         * field types with input to save time. This number has to be long
         * enough to get rid of disambiguation.
         * <p>TODO:
         * <p>NOTE this is a big tradeoff in terms of input data, e.g. field
         * "find" will be considered "file" and overwrite the value, so if
         * ctags will send us buggy input. We will output buggy data TOO! NO
         * VALIDATION happens of input - but then we gain LOTS of speed, due to
         * not comparing the same field names again and again fully.
         */
        public static int charCmpEndOffset = 0;

        /**
         * Quickly get if the field name matches allowed/consumed ones.
         * @param fullName the name to look up
         * @return a defined value, or null if unmatched
         */
        public static CtagsReader.tagFields quickValueOf(String fullName) {
            int i;
            boolean match;
            for (tagFields x : tagFields.values()) {
                match = true;
                for (i = 0; i <= charCmpEndOffset; i++) {
                    if (x.name.charAt(i) != fullName.charAt(i)) {
                        match = false;
                        break;
                    }
                }
                if (match) {
                    return x;
                }
            }
            return null;
        }
    }

    public int getTabSize() {
        return tabSize;
    }

    public void setTabSize(int tabSize) {
        this.tabSize = tabSize;
    }

    /**
     * Gets the instance's definitions.
     * @return a defined instance
     */
    public Definitions getDefinitions() {
        return defs;
    }

    /**
     * Sets the supplier of a {@link SourceSplitter} to use when ctags pattern
     * is insufficient, and the reader could use the source data.
     * <p>
     * N.b. because an I/O exception can occur, the supplier may return
     * {@code null}, which the {@link CtagsReader} handles.
     * @param obj defined instance or {@code null}
     */
    public void setSplitterSupplier(Supplier<SourceSplitter> obj) {
        splitter = null;
        triedSplitterSupplier = false;
        splitterSupplier = obj;
    }

    /**
     * Reads a line into the instance's definitions.
     * @param tagLine a defined line or null to no-op
     */
    public void readLine(String tagLine) {
        if (tagLine == null) {
            return;
        }

        int p = tagLine.indexOf('\t');
        if (p <= 0) {
            //log.fine("SKIPPING LINE - NO TAB");
            return;
        }
        String def = tagLine.substring(0, p);
        int mstart = tagLine.indexOf('\t', p + 1);

        String kind = null;

        int lp = tagLine.length();
        while ((p = tagLine.lastIndexOf('\t', lp - 1)) > 0) {
            //log.fine(" p = " + p + " lp = " + lp);
            String fld = tagLine.substring(p + 1, lp);
            //log.fine("FIELD===" + fld);
            lp = p;

            int sep = fld.indexOf(':');
            if (sep != -1) {
                tagFields pos = tagFields.quickValueOf(fld);
                if (pos != null) {
                    String val = fld.substring(sep + 1);
                    fields.put(pos, val);
                }
            } else {
                //TODO no separator, assume this is the kind
                kind = fld;
                break;
            }
        }

        String lnum = fields.get(tagFields.LINE);
        String signature = fields.get(tagFields.SIGNATURE);
        String classInher = fields.get(tagFields.CLASS);

        final String whole;
        final String match;
        int mlength = p - mstart;
        if ((p > 0) && (mlength > MIN_METHOD_LINE_LENGTH)) {
            whole = cutPattern(tagLine, mstart, p);
            if (mlength < MAX_METHOD_LINE_LENGTH) {
                match = whole.replaceAll("[ \t]+", " ");
                //TODO per format we should also recognize \r and \n
            } else {
                LOGGER.log(Level.FINEST, "Ctags: stripping method" +
                    " body for def {0} line {1}(scopes/highlight" +
                    " might break)", new Object[]{def, lnum});
                match = whole.substring(0, MAX_METHOD_LINE_LENGTH).replaceAll(
                    "[ \t]+", " ");
            }
        } else { //tag is wrong format; cannot extract tagaddress from it; skip
            return;
        }

        // Bug #809: Keep track of which symbols have already been
        // seen to prevent duplicating them in memory.

        final String type = classInher == null ? kind : kind + " in " +
            classInher;

        int lineno;
        try {
            lineno = Integer.parseUnsignedInt(lnum);
        } catch (NumberFormatException e) {
            lineno = 0;
            LOGGER.log(Level.WARNING, "CTags line number parsing problem(but" +
                " I will continue with line # 0) for symbol {0}", def);
        }

        CpatIndex cidx = bestIndexOfTag(lineno, whole, def);
        addTag(defs, cidx.lineno, def, type, match, classInher, signature,
            cidx.lineStart, cidx.lineEnd);

        String[] args;
        if (signature != null && !signature.equals("()") &&
                !signature.startsWith("() ") && (args =
                splitSignature(signature)) != null) {
            for (String arg : args) {
                //TODO this algorithm assumes that data types occur to
                //     the left of the argument name, so it will not
                //     work for languages like rust, kotlin, etc. which
                //     place the data type to the right of the argument name.
                //     Need an attribute from ctags to indicate data type
                //     location.
                // ------------------------------------------------------------
                // When no assignment of default values,
                // expecting: <type> <name>, or <name>
                //
                // When default value assignment applied to parameter,
                // expecting: <type> <name> = <value> or
                //            <name> = <value>
                // (Note whitespace content made irrelevant)

                // Need to ditch the default assignment value
                // so that the extraction loop below will work.
                // This assumes all languages use '=' to assign value.

                if (arg.contains("=")) {
                    String[] a = arg.split("=");
                    arg = a[0];  // throws away assigned value
                }
                arg = arg.trim();
                if (arg.length() < 1) {
                    continue;
                }

                cidx = bestIndexOfArg(lineno, whole, arg);

                String name = null;
                Matcher mname = LAST_UWORD.matcher(arg);
                if (mname.find()) {
                    name = mname.group(1);
                } else if (arg.equals("...")) {
                    name = arg;
                }
                if (name != null) {
                    addTag(defs, cidx.lineno, name, "argument", def.trim() +
                        signature.trim(), null, signature, cidx.lineStart,
                        cidx.lineEnd);
                } else {
                    if (LOGGER.isLoggable(Level.FINEST)) {
                        LOGGER.log(Level.FINEST,
                            "Not matched arg:{0}|sig:{1}",
                            new Object[]{arg, signature});
                    }
                }
            }
        }
//        log.fine("Read = " + def + " : " + lnum + " = " + kind + " IS " +
//            inher + " M " + match);
        fields.clear();
    }

    /**
     * Cuts the ctags TAG FILE FORMAT search pattern from the specified
     * {@code tagLine} between the specified tab positions, and un-escapes
     * {@code \\} and {@code \/}.
     * @return a defined string
     */
    private static String cutPattern(String tagLine, int startTab, int endTab) {
        // Three lead character represents "\t/^".
        String cut = tagLine.substring(startTab + 3, endTab);

        /**
         * Formerly this class cut four characters from the end, but my testing
         * revealed a bug for short lines in files with macOS endings (e.g.
         * cyrus-sasl mac/libdes/src/des_enc.c) where the pattern-ending $ is
         * not present. Now, inspect the end of the pattern to determine the
         * true cut -- which is appropriate for all content anyway.
         */
        if (cut.endsWith("$/;\"")) {
            cut = cut.substring(0, cut.length() - 4);
        } else if (cut.endsWith("/;\"")) {
            cut = cut.substring(0, cut.length() - 3);
        } else {
            /**
             * The former logic did the following without the inspections above.
             * Leaving this here as a fallback.
             */
            cut = cut.substring(0, cut.length() - 4);
        }
        return cut.replace("\\\\", "\\").replace("\\/", "/");
    }

    /**
     * Adds a tag to a {@code Definitions} instance.
     */
    private void addTag(Definitions defs, int lineno, String symbol,
            String type, String text, String namespace, String signature,
            int lineStart, int lineEnd) {
        // The strings are frequently repeated (a symbol can be used in
        // multiple definitions, multiple definitions can have the same type,
        // one line can contain multiple definitions). Intern them to minimize
        // the space consumed by them (see bug #809).
        defs.addTag(lineno, symbol.trim().intern(), type.trim().intern(),
            text.trim().intern(), namespace == null ? null :
            namespace.trim().intern(), signature, lineStart, lineEnd);
    }

    /**
     * Searches for the index of the best match of {@code str} in {@code whole}
     * in a multi-stage algorithm that first starts strictly to disfavor
     * abutting words and then relaxes -- and also works around ctags's possibly
     * having returned a partial line or only one line of a multi-line language
     * syntax.
     * @return a defined instance
     */
    private CpatIndex bestIndexOfTag(int lineno, String whole, String str) {
        if (whole.length() < 1) {
            return new CpatIndex(lineno, 0, 1, true);
        }
        String origWhole = whole;

        int t = tabSize;
        int s, e;

        int woff = strictIndexOf(whole, str);
        if (woff < 0) {
            /*
             * When a splitter is available, search the entire line.
             * (N.b. use 0-based indexing vs ctags's 1-based.)
             */
            String cut = trySplitterCut(lineno - 1, 1);
            if (cut == null || !cut.startsWith(whole)) {
                if (LOGGER.isLoggable(Level.FINE)) {
                    String readablecut = cut != null ? cut : "null\n";
                    LOGGER.log(Level.FINE,
                        "Bad cut:{0}|versus:{1}|line {2}",
                        new Object[]{readablecut, whole, lineno});
                }
            } else {
                whole = cut;
                woff = strictIndexOf(whole, str);
            }

            if (woff < 0) {
                /** At this point, do a lax search of the substring. */
                woff = whole.indexOf(str);
            }
        }

        if (woff >= 0) {
            s = ExpandTabsReader.translate(whole, woff, t);
            e = ExpandTabsReader.translate(whole, woff + str.length(), t);
            return new CpatIndex(lineno, s, e);
        }
        /**
         * When ctags has truncated a pattern, or when it spans multiple lines,
         * then `str' might not be found in `whole'. In that case, return an
         * imprecise index for the last character as the best we can do.
         */
        s = ExpandTabsReader.translate(origWhole, origWhole.length() - 1, t);
        e = ExpandTabsReader.translate(origWhole, origWhole.length(), t);
        return new CpatIndex(lineno, s, e, true);
    }

    /**
     * Searches for the index of the best match of {@code arg} in {@code whole}
     * in a multi-stage algorithm that first starts strictly to disfavor
     * abutting words and then relaxes -- and also works around ctags's possibly
     * having returned a partial line or only one line of a multi-line language
     * syntax or where ctags has transformed syntax.
     * <p>
     * E.g., the true source might read {@code const fru_regdef_t *d} with the
     * ctags signature reading {@code const fru_regdef_t * d}
     * @return a defined instance
     */
    private CpatIndex bestIndexOfArg(int lineno, String whole, String arg) {
        if (whole.length() < 1) {
            return new CpatIndex(lineno, 0, 1, true);
        }

        int t = tabSize;
        int s, e;

        // First search arg as-is in the current `whole' -- strict then lax.
        int woff = strictIndexOf(whole, arg);
        if (woff < 0) {
            woff = whole.indexOf(arg);
        }
        if (woff >= 0) {
            s = ExpandTabsReader.translate(whole, woff, t);
            e = ExpandTabsReader.translate(whole, woff + arg.length(), t);
            return new CpatIndex(lineno, s, e);
        }

        // Build a pattern from `arg' with looseness around whitespace.
        StringBuilder bld = new StringBuilder();
        int spos = 0;
        boolean lastWhitespace = false;
        boolean firstNonWhitespace = false;
        for (int i = 0; i < arg.length(); ++i) {
            char c = arg.charAt(i);
            if (Character.isWhitespace(c)) {
                if (!firstNonWhitespace) {
                    ++spos;
                } else if (!lastWhitespace) {
                    lastWhitespace = true;
                    if (spos < i) {
                        bld.append(Pattern.quote(arg.substring(spos, i)));
                    }
                    // m`\s*`
                    bld.append("\\s*");
                }
            } else {
                firstNonWhitespace = true;
                if (lastWhitespace) {
                    lastWhitespace = false;
                    spos = i;
                }
            }
        }
        if (spos < arg.length()) {
            bld.append(Pattern.quote(arg.substring(spos)));
        }
        if (bld.length() < 1) {
            if (LOGGER.isLoggable(Level.FINE)) {
                LOGGER.log(Level.FINE, "Odd arg:{0}|versus:{1}|line {2}",
                    new Object[]{arg, whole, lineno});
            }
            /**
             * When no fuzzy match can be generated, return an imprecise index
             * for the first character as the best we can do.
             */
            return new CpatIndex(lineno, 0, 1, true);
        }

        Pattern argpat = Pattern.compile(bld.toString());
        PatResult pr = bestMatch(whole, arg, argpat);
        if (pr.start >= 0) {
            s = ExpandTabsReader.translate(whole, pr.start, t);
            e = ExpandTabsReader.translate(whole, pr.end, t);
            return new CpatIndex(lineno, s, e);
        }

        /*
         * When a splitter is available, search the next several lines.
         * (N.b. use 0-based indexing vs ctags's 1-based.)
         */
        String cut = trySplitterCut(lineno - 1, MAX_CUT_LINES);
        if (cut == null || !cut.startsWith(whole)) {
            if (LOGGER.isLoggable(Level.FINE)) {
                String readablecut = cut != null ? cut : "null\n";
                LOGGER.log(Level.FINE, "Bad cut:{0}|versus:{1}|line {2}",
                    new Object[]{readablecut, whole, lineno});
            }
        } else {
            pr = bestMatch(cut, arg, argpat);
            if (pr.start >= 0) {
                return bestLineOfMatch(lineno, pr, cut);
            }
        }

        /**
         * When no match is found, return an imprecise index for the last
         * character as the best we can do.
         */
        s = ExpandTabsReader.translate(whole, whole.length() - 1, t);
        e = ExpandTabsReader.translate(whole, whole.length(), t);
        return new CpatIndex(lineno, s, e, true);
    }

    /**
     * Searches strictly then laxly.
     */
    private PatResult bestMatch(String whole, String arg, Pattern argpat) {
        PatResult m = strictMatch(whole, arg, argpat);
        if (m.start >= 0) {
            return m;
        }
        Matcher marg = argpat.matcher(whole);
        if (marg.find()) {
            return new PatResult(marg.start(), marg.end(), marg.group());
        }
        // Return m, which was invalid if we got to here.
        return m;
    }

    /**
     * Like {@link String#indexOf(java.lang.String)} but strict that a
     * {@code substr} starting with a word character cannot abut another word
     * character on its left and likewise on the right for a {@code substr}
     * ending with a word character.
     */
    private int strictIndexOf(String whole, String substr) {
        boolean strictLeft = substr.length() > 0 && WORD_CHAR.matcher(
            String.valueOf(substr.charAt(0))).matches();
        boolean strictRight = substr.length() > 0 && WORD_CHAR.matcher(
            String.valueOf(substr.charAt(substr.length() - 1))).matches();

        int spos = 0;
        do {
            int woff = whole.indexOf(substr, spos);
            if (woff < 0) {
                return -1;
            }

            spos = woff + 1;
            String onechar;
            /**
             * Reject if the previous character is a word character, as that
             * would not accord with a clean symbol break
             */
            if (strictLeft && woff > 0) {
                onechar = String.valueOf(whole.charAt(woff - 1));
                if (WORD_CHAR.matcher(onechar).matches()) {
                    continue;
                }
            }
            /**
             * Reject if the following character is a word character, as that
             * would not accord with a clean symbol break
             */
            if (strictRight && woff + substr.length() < whole.length()) {
                onechar = String.valueOf(whole.charAt(woff + substr.length()));
                if (WORD_CHAR.matcher(onechar).matches()) {
                    continue;
                }
            }
            return woff;
        } while (spos < whole.length());
        return -1;
    }

    /**
     * Like {@link #strictIndexOf(java.lang.String, java.lang.String)} but using
     * a pattern.
     */
    private PatResult strictMatch(String whole, String substr, Pattern pat) {
        boolean strictLeft = substr.length() > 0 && WORD_CHAR.matcher(
            String.valueOf(substr.charAt(0))).matches();
        boolean strictRight = substr.length() > 0 && WORD_CHAR.matcher(
            String.valueOf(substr.charAt(substr.length() - 1))).matches();

        Matcher m = pat.matcher(whole);
        while (m.find()) {
            String onechar;
            /**
             * Reject if the previous character is a word character, as that
             * would not accord with a clean symbol break
             */
            if (strictLeft && m.start() > 0) {
                onechar = String.valueOf(whole.charAt(m.start() - 1));
                if (WORD_CHAR.matcher(onechar).matches()) {
                    continue;
                }
            }
            /**
             * Reject if the following character is a word character, as that
             * would not accord with a clean symbol break
             */
            if (strictRight && m.end() < whole.length()) {
                onechar = String.valueOf(whole.charAt(m.end()));
                if (WORD_CHAR.matcher(onechar).matches()) {
                    continue;
                }
            }
            return new PatResult(m.start(), m.end(), m.group());
        }
        return new PatResult(-1, -1, null);
    }

    /**
     * Finds the line with the longest content from {@code cut}.
     * <p>
     * The {@link Definitions} tag model is based on a match within a line.
     * "signature" fields, however, can be condensed from multiple lines; and a
     * fuzzy match can therefore span multiple lines.
     */
    private CpatIndex bestLineOfMatch(int lineno, PatResult pr, String cut) {
        // (N.b. use 0-based indexing vs ctags's 1-based.)
        int lineOff = splitter.getOffset(lineno - 1);
        int mOff = lineOff + pr.start;
        int mIndex = splitter.findLineIndex(mOff);
        int zOff = lineOff + pr.end - 1;
        int zIndex = splitter.findLineIndex(zOff);

        int t = tabSize;
        int resIndex = mIndex;
        int contentLength = 0;
        /**
         * Initialize the following just to silence warnings but with values
         * that will be detected as "bad fuzzy" later.
         */
        String whole = "";
        int s = 0;
        int e = 1;
        /*
         * Iterate to determine the length of the portion of cut that is
         * contained within each line.
         */
        for (int lIndex = mIndex; lIndex <= zIndex; ++lIndex) {
            String iwhole = splitter.getLine(lIndex);
            int lOff = splitter.getOffset(lIndex);
            int lOffZ = lOff + iwhole.length();
            int offStart = Math.max(pr.start + lineOff, lOff);
            int offEnd = Math.min(pr.end + lineOff, lOffZ);
            if (offEnd - offStart > contentLength) {
                contentLength = offEnd - offStart;
                resIndex = lIndex;
                whole = iwhole;
                // (The following are not yet adjusted for tabs.)
                s = offStart - lOff;
                e = offEnd - lOff;
            }
        }

        if (s >= 0 && s < whole.length() && e >= 0 && e <= whole.length()) {
            s = ExpandTabsReader.translate(whole, s, t);
            e = ExpandTabsReader.translate(whole, e, t);
            // (N.b. use ctags's 1-based indexing.)
            return new CpatIndex(resIndex + 1, s, e);
        }

        /**
         * This should not happen -- but if it does, log it and return an
         * imprecise index for the first character as the best we can do.
         */
        if (LOGGER.isLoggable(Level.FINE)) {
            LOGGER.log(Level.FINE,
                "Bad fuzzy:{0}|versus:{1}|line {2} pos {3}-{4}|{5}|",
                new Object[]{pr.capture, cut, lineno, s, e, whole});
        }
        return new CpatIndex(lineno, 0, 1, true);
    }

    /**
     * TODO if some languages use different character for separating arguments,
     * below needs to be adjusted.
     * @return a defined instance or null
     */
    private static String[] splitSignature(String signature) {
        int off0 = 0;
        int offz = signature.length();
        int soff = off0;
        int eoff = offz;
        if (soff >= eoff) {
            return null;
        }

        // Trim outer punctuation if it exists.
        while (soff < signature.length() && (signature.charAt(soff) == '(' ||
                signature.charAt(soff) == '{')) {
            ++soff;
        }
        while (eoff - 1 > soff && (signature.charAt(eoff - 1) == ')' ||
                signature.charAt(eoff - 1) == '}')) {
            --eoff;
        }
        if (soff > off0 || eoff < offz) {
            signature = signature.substring(soff, eoff);
        }
        return signature.split(",");
    }

    /**
     * Tries to cut lines from a splitter provided by {@code splitterSupplier}.
     * @return a defined instance if a successful cut is made or else
     * {@code null}
     */
    private String trySplitterCut(int lineOffset, int maxLines) {
        if (splitter == null) {
            if (splitterSupplier == null || triedSplitterSupplier) {
                return null;
            }
            triedSplitterSupplier = true;
            splitter = splitterSupplier.get();
            if (splitter == null) {
                return null;
            }
        }

        long newCutCacheKey = ((long) lineOffset << 32) | maxLines;
        if (cutCacheKey == newCutCacheKey) {
            return cutCacheValue;
        }

        StringBuilder cutbld = new StringBuilder();
        for (int i = lineOffset; i < lineOffset + maxLines &&
                i < splitter.count() && cutbld.length() < MAX_CUT_LENGTH;
                ++i) {
            cutbld.append(splitter.getLine(i));
        }
        if (cutbld.length() > MAX_CUT_LENGTH) {
            cutbld.setLength(MAX_CUT_LENGTH);
        }
        cutCacheValue = cutbld.toString();
        cutCacheKey = newCutCacheKey;
        return cutCacheValue;
    }

    /**
     * Represents an index into ctags pattern entries.
     */
    private static class CpatIndex {
        public final int lineno;
        public final int lineStart;
        public final int lineEnd;
        public final boolean imprecise;

        CpatIndex(int lineno, int lineStart, int lineEnd) {
            this.lineno = lineno;
            this.lineStart = lineStart;
            this.lineEnd = lineEnd;
            this.imprecise = false;
        }

        CpatIndex(int lineno, int lineStart, int lineEnd, boolean imprecise) {
            this.lineno = lineno;
            this.lineStart = lineStart;
            this.lineEnd = lineEnd;
            this.imprecise = imprecise;
        }
    }

    /**
     * Represents a result from a pattern match -- valid if lineStart is greater
     * than or equal to zero.
     */
    private static class PatResult {
        public final int start;
        public final int end;
        public final String capture;

        PatResult(int start, int end, String capture) {
            this.start = start;
            this.end = end;
            this.capture = capture;
        }
    }
}
