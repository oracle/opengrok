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
 * Copyright (c) 2005, 2017, Oracle and/or its affiliates. All rights reserved.
 * Portions Copyright (c) 2017, Chris Fraire <cfraire@me.com>.
 */

package org.opensolaris.opengrok.analysis;

import java.util.EnumMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.opensolaris.opengrok.logger.LoggerFactory;

/**
 * Represents a reader of output from runs of ctags.
 */
public class CtagsReader {

    private static final Logger LOGGER = LoggerFactory.getLogger(
        CtagsReader.class);

    /** A value indicating empty method body in tags, so skip it */
    private final int MIN_METHOD_LINE_LENGTH = 6;

    /**
     * 96 is used by universal ctags for some lines, but it's too low,
     * OpenGrok can theoretically handle 50000 with 8G heap. Also this might
     * break scopes functionality, if set too low.
     */
    private final int MAX_METHOD_LINE_LENGTH = 1030;

    private final EnumMap<tagFields, String> fields = new EnumMap<>(
        tagFields.class);

    private final Definitions defs = new Definitions();

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
         * Quickly get if the field name matches allowed/consumed ones
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

    /**
     * Gets the instance's definitions.
     * @return a defined instance
     */
    public Definitions getDefinitions() {
        return defs;
    }

    /**
     * Reads a line into the instance's definitions.
     * @param tagLine a defined line or null to no-op
     */
    public void readLine(String tagLine) {
        if (tagLine == null) return;

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
                } else {
                    //unknown field name
                    //don't log on purpose, since we don't consume all possible
                    // fields, so just ignore this error for now
//                    LOGGER.log(Level.WARNING, "Unknown field name found: {0}",
//                        fld.substring(0, sep - 1));
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

        final String match;
        int mlength = p - mstart;
        if ((p > 0) && (mlength > MIN_METHOD_LINE_LENGTH)) {
            if (mlength < MAX_METHOD_LINE_LENGTH) {
                match = tagLine.substring(mstart + 3, p - 4).
                    replace("\\/", "/").replaceAll("[ \t]+", " ");
                //TODO per format we should also recognize \r and \n and \\
            } else {
                LOGGER.log(Level.FINEST, "Ctags: stripping method" +
                    " body for def {0} line {1}(scopes/highlight" +
                    " might break)", new Object[]{def, lnum});
                match = tagLine.substring(mstart + 3, mstart +
                    MAX_METHOD_LINE_LENGTH - 1). // +3 - 4 = -1
                    replace("\\/", "/").replaceAll("[ \t]+", " ");
            }
        } else { //tag is wrong format; cannot extract tagaddress from it; skip
            return;
        }

        // Bug #809: Keep track of which symbols have already been
        // seen to prevent duplicating them in memory.

        final String type = classInher == null ? kind : kind + " in " +
            classInher;
        addTag(defs, lnum, def, type, match, classInher, signature);
        if (signature != null) {
            // TODO if some languages use different character for separating
            // arguments, below needs to be adjusted
            String[] args = signature.split(",");
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

                // Strip out all non 'word' class symbols
                // which leaves just names intact.
                String[] names = arg.trim().split("[\\W]");
                String name;

                // Walk the array backwards from the end and
                // the parameter name should always be the first
                // non-empty element encountered.
                for (int ii = names.length - 1; ii >= 0; ii--) {
                    name = names[ii];
                    if (name.length() > 0) {
                        addTag(defs, lnum, name, "argument", def.trim() +
                            signature.trim(), null, signature);
                        break;
                    }
                }
            }
        }
//        log.fine("Read = " + def + " : " + lnum + " = " + kind + " IS " +
//            inher + " M " + match);
        fields.clear();
    }

    /**
     * Adds a tag to a {@code Definitions} instance.
     */
    private void addTag(Definitions defs, String lnum, String symbol,
        String type, String text, String namespace, String signature) {
        // The strings are frequently repeated (a symbol can be used in
        // multiple definitions, multiple definitions can have the same type,
        // one line can contain multiple definitions). Intern them to minimize
        // the space consumed by them (see bug #809).
        int lineno = 0;
        try {
            lineno = Integer.parseInt(lnum);
        } catch (NumberFormatException nfe) {
            LOGGER.log(Level.WARNING, "CTags line number parsing problem(but" +
                " I will continue with line # 0) for symbol {0}", symbol);
        }
        defs.addTag(lineno, symbol.trim().intern(), type.trim().intern(),
            text.trim().intern(), namespace == null ? null :
            namespace.trim().intern(), signature);
    }
}
