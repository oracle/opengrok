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
 * Portions Copyright (c) 2017, Steven Haehn.
 */
package org.opengrok.indexer.util;

import java.lang.reflect.Field;
import java.text.ParseException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.opengrok.indexer.index.Indexer;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author shaehn
 */
public class OptionParserTest {

    int actionCounter;

    @BeforeEach
    public void setUp() {
        actionCounter = 0;
    }

    // Scan parser should ignore all options it does not recognize.
    @Test
    public void scanParserIgnoreUnrecognizableOptions() throws ParseException {

        String configPath = "/the/config/path";

        OptionParser scanner = OptionParser.scan(parser -> {
            parser.on("-R configPath").execute(v -> {
                assertEquals(v, configPath);
                actionCounter++;
            });
        });

        String[] args = {"-a", "-b", "-R", configPath};
        scanner.parse(args);
        assertEquals(1, actionCounter);
    }

    // Validate that option can have multiple names
    // with both short and long versions.
    @Test
    public void optionNameAliases() throws ParseException {

        OptionParser opts = OptionParser.execute(parser -> {

            parser.on("-?", "--help").execute(v -> {
                assertEquals("", v);
                actionCounter++;
            });
        });

        String[] args = {"-?"};
        opts.parse(args);
        assertEquals(1, actionCounter);

        String[] args2 = {"--help"};
        opts.parse(args2);
        assertEquals(2, actionCounter);
    }

    // Show that parser will throw exception
    // when option is not recognized.
    @Test
    public void unrecognizedOption() {

        OptionParser opts = OptionParser.execute(parser -> {
            parser.on("-?", "--help").execute(v -> {
            });
        });

        try {
            String[] args = {"--unrecognizedOption"};
            opts.parse(args);

        } catch (ParseException e) {
            String msg = e.getMessage();
            assertEquals("Unknown option: --unrecognizedOption", msg);
        }
    }

    // Show that parser will throw exception when missing option value
    @Test
    public void missingOptionValue() {

        OptionParser opts = OptionParser.execute(parser -> {
            parser.on("-a=VALUE").execute(v -> {
            });
        });

        try {
            String[] args = {"-a"}; // supply option without value
            opts.parse(args);

        } catch (ParseException e) {
            String msg = e.getMessage();
            assertEquals("Option -a requires a value.", msg);
        }
    }

    // Test parser ability to find short option value whether
    // it is glued next to the option (eg. -xValue), or comes
    // as a following argument (eg. -x Value)
    @Test
    public void shortOptionValue() throws ParseException {

        OptionParser opts = OptionParser.execute(parser -> {
            parser.on("-a=VALUE").execute(v -> {
                assertEquals("3", v);
                actionCounter++;
            });
        });

        String[] separateValue = {"-a", "3"};
        opts.parse(separateValue);
        assertEquals(1, actionCounter);

        String[] joinedValue = {"-a3"};
        opts.parse(joinedValue);
        assertEquals(2, actionCounter);
    }

    // Validate the ability of parser to convert
    // string option values into internal data types.
    @Test
    public void testSupportedDataCoercion() throws ParseException {

        OptionParser opts = OptionParser.execute(parser -> {

            parser.on("--int=VALUE", Integer.class).execute(v -> {
                assertEquals(3, v);
                actionCounter++;
            });

            parser.on("--float=VALUE", Float.class).execute(v -> {
                assertEquals((float) 3.23, v);
                actionCounter++;
            });

            parser.on("--double=VALUE", Double.class).execute(v -> {
                assertEquals(3.23, v);
                actionCounter++;
            });

            parser.on("-t", "--truth", "=VALUE", Boolean.class).execute(v -> {
                assertTrue((Boolean) v);
                actionCounter++;
            });

            parser.on("-f VALUE", Boolean.class).execute(v -> {
                assertFalse((Boolean) v);
                actionCounter++;
            });

            parser.on("-a array", String[].class).execute(v -> {
                String[] x = {"a", "b", "c"};
                assertArrayEquals(x, (String[]) v);
                actionCounter++;
            });
        });

        String[] integer = {"--int", "3"};
        opts.parse(integer);
        assertEquals(1, actionCounter);

        String[] floats = {"--float", "3.23"};
        opts.parse(floats);
        assertEquals(2, actionCounter);

        String[] doubles = {"--double", "3.23"};
        opts.parse(doubles);
        assertEquals(3, actionCounter);

        actionCounter = 0;
        String[] verity = {"-t", "true", "-t", "True", "-t", "on", "-t", "ON", "-t", "yeS"};
        opts.parse(verity);
        assertEquals(5, actionCounter);

        actionCounter = 0;
        String[] falsehood = {"-f", "false", "-f", "FALSE", "-f", "oFf", "-f", "no", "-f", "NO"};
        opts.parse(falsehood);
        assertEquals(5, actionCounter);

        try {  // test illegal value to Boolean
            String[] liar = {"--truth", "liar"};
            opts.parse(liar);
        } catch (ParseException e) {
            String msg = e.getMessage();
            assertEquals("Failed to parse (liar) as value of [-t, --truth]", msg);
        }

        actionCounter = 0;
        String[] array = {"-a", "a,b,c"};
        opts.parse(array);
        assertEquals(1, actionCounter);
    }

    // Make sure that option can take specific addOption of values
    // and when an candidate values is seen, an exception is given.
    @Test
    public void specificOptionValues() {

        OptionParser opts = OptionParser.execute(parser -> {
            String[] onOff = {"on", "off"};
            parser.on("--setTest on/off", onOff).execute(v -> actionCounter++);
        });

        try {
            String[] args1 = {"--setTest", "on"};
            opts.parse(args1);
            assertEquals(1, actionCounter);

            String[] args2 = {"--setTest", "off"};
            opts.parse(args2);
            assertEquals(2, actionCounter);

            String[] args3 = {"--setTest", "nono"};
            opts.parse(args3);
        } catch (ParseException e) {
            String msg = e.getMessage();
            assertEquals("'nono' is unknown value for option [--setTest]. Must be one of [on, off]", msg);
        }
    }

    // See that option value matches a regular expression
    @Test
    public void optionValuePatternMatch() {

        OptionParser opts = OptionParser.execute(parser -> {

            parser.on("--pattern PERCENT", "/[0-9]+%?/").execute(v -> {
                actionCounter++;
            });
        });

        try {
            String[] args1 = {"--pattern", "3%"};
            opts.parse(args1);
            assertEquals(1, actionCounter);

            String[] args2 = {"--pattern", "120%"};
            opts.parse(args2);
            assertEquals(2, actionCounter);

            String[] args3 = {"--pattern", "75"};
            opts.parse(args3);
            assertEquals(3, actionCounter);

            String[] args4 = {"--pattern", "NotNumber"};
            opts.parse(args4);
        } catch (ParseException e) {
            String msg = e.getMessage();
            assertEquals(msg, "Value 'NotNumber' for option [--pattern]PERCENT\n" +
                    " does not match pattern [0-9]+%?");
        }
    }

    // Verify option may have non-required value
    @Test
    public void missingValueOnOptionAllowed() throws ParseException {

        OptionParser opts = OptionParser.execute(parser -> {

            parser.on("--value=[optional]").execute(v -> {
                actionCounter++;
                if (v.equals("")) {
                    assertEquals("", v);
                } else {
                    assertEquals("hasOne", v);
                }
            });
            parser.on("-o[=optional]").execute(v -> {
                actionCounter++;
                if (v.equals("")) {
                    assertEquals("", v);
                } else {
                    assertEquals("hasOne", v);
                }
            });
            parser.on("-v[optional]").execute(v -> {
                actionCounter++;
                if (v.equals("")) {
                    assertEquals("", v);
                } else {
                    assertEquals("hasOne", v);
                }
            });
        });

        String[] args1 = {"--value", "hasOne"};
        opts.parse(args1);
        assertEquals(1, actionCounter);

        String[] args2 = {"--value"};
        opts.parse(args2);
        assertEquals(2, actionCounter);

        String[] args3 = {"-ohasOne"};
        opts.parse(args3);
        assertEquals(3, actionCounter);

        String[] args4 = {"-o"};
        opts.parse(args4);
        assertEquals(4, actionCounter);

        String[] args5 = {"-v", "hasOne"};
        opts.parse(args5);
        assertEquals(5, actionCounter);

        String[] args6 = {"-v"};
        opts.parse(args6);
        assertEquals(6, actionCounter);

        String[] args7 = {"--value", "-o", "hasOne"};
        opts.parse(args7);
        assertEquals(8, actionCounter);
    }

    // Verify default option summary
    @Test
    public void defaultOptionSummary() {
        OptionParser opts = OptionParser.execute(parser -> {
            parser.on("--help").execute(v -> {
                String summary = parser.getUsage();
                // assertTrue(summary.startsWith("Usage: JUnitTestRunner [options]"));  // fails on travis
                assertTrue(summary.matches("(?s)Usage: \\w+ \\[options\\].*"));
            });
        });

        try {
            String[] args = {"--help"};
            opts.parse(args);
        } catch (ParseException e) {
            String msg = e.getMessage();
            assertEquals("Unknown option: --unrecognizedOption", msg);
        }
    }

    // Allowing user entry of initial substrings to long option names.
    // Therefore, must be able to catch when option entry matches more
    // than one entry.
    @Test
    public void catchAmbigousOptions() {
        OptionParser opts = OptionParser.execute(parser -> {
            parser.on("--help");
            parser.on("--help-me-out");
        });

        try {
            String[] args = {"--he"};
            opts.parse(args);
        } catch (ParseException e) {
            String msg = e.getMessage();
            assertEquals("Ambiguous option --he matches [--help-me-out, --help]", msg);
        }
    }

    // Allow user to enter an initial substring to long option names
    @Test
    public void allowInitialSubstringOptionNames() throws ParseException {
        OptionParser opts = OptionParser.execute(parser -> {
            parser.on("--help-me-out").execute(v -> actionCounter++);
        });

        String[] args = {"--help"};
        opts.parse(args);
        assertEquals(1, actionCounter);
    }

    // Specific test to evalutate the internal option candidate method
    @Test
    public void testInitialSubstringOptionNames() throws ParseException {
        OptionParser opts = OptionParser.execute(parser -> {
            parser.on("--help-me-out");
            parser.on("--longOption");
        });

        assertEquals("--longOption", opts.candidate("--l", 0));
        assertEquals("--help-me-out", opts.candidate("--h", 0));
        assertNull(opts.candidate("--thisIsUnknownOption", 0));
    }

    // Catch duplicate option names in parser construction.
    @Test
    public void catchDuplicateOptionNames() {
        try {
            OptionParser.execute(parser -> {
                parser.on("--duplicate");
                parser.on("--duplicate");
            });
        } catch (IllegalArgumentException e) {
            String msg = e.getMessage();
            assertEquals("** Programmer error! Option --duplicate already defined", msg);
        }
    }

    // Catch single '-' in argument list
    @Test
    public void catchNamelessOption() {
        OptionParser opts = OptionParser.execute(parser -> {
            parser.on("--help-me-out");
        });

        try {
            String[] args = {"-", "data"};
            opts.parse(args);
        } catch (ParseException e) {
            String msg = e.getMessage();
            assertEquals("Stand alone '-' found in arguments, not allowed", msg);
        }
    }

    // Fail options put into Indexer.java that do not have a description.
    @Test
    public void catchIndexerOptionsWithoutDescription() throws NoSuchFieldException, IllegalAccessException, ParseException {
        String[] argv = {"---unitTest"};
        Indexer.parseOptions(argv);

        // Use reflection to get the option parser from Indexer.
        Field f = Indexer.class.getDeclaredField("optParser");
        f.setAccessible(true);
        OptionParser op = (OptionParser) f.get(Indexer.class);

        for (OptionParser.Option o : op.getOptionList()) {
            assertNotNull(o.description, "'" + o.names.get(0) + "' option needs description");
            assertFalse(o.description.toString().isEmpty(),
                    "'" + o.names.get(0) + "' option needs non-empty description");
        }

        // This just tests that the description is actually null.
        op = OptionParser.execute(parser -> {
            parser.on("--help-me-out");
        });

        for (OptionParser.Option o : op.getOptionList()) {
            assertNull(o.description);
        }
    }
}
