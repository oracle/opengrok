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
 * Portions Copyright (c) 2019, Chris Fraire <cfraire@me.com>.
 */
package org.opengrok.indexer.util;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.PrintStream;
import java.text.ParseException;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * OptionParser is a class for command-line option analysis.
 *
 * Now that Java 8 has the crucial Lambda and Consumer interfaces, we can
 * implement a more powerful program option parsing mechanism, ala ruby
 * OptionParser style.
 *
 * Features
 *   o  An option can have multiple short names (-x) and multiple long
 *      names (--xyz). Thus, an option that displays a programs usage
 *      may be available as -h, -?, --help, --usage, and --about.
 *
 *   o  An option may be specified as having no argument, an optional
 *      argument, or a required argument. Arguments may be validated
 *      against a regular expression pattern or a list of valid values.
 *
 *   o  The argument specification and the code to handle it are
 *      written in the same place. The argument description may consist
 *      of one or more lines to be used when displaying usage summary.
 *
 *   o  The option summary is produced without maintaining strings
 *      in a separate setting.
 *
 *   o  Users are allowed to enter initial substrings for long option
 *      names as long as there is no ambiguity.
 *
 *   o  Supports the ability to coerce command line arguments into objects.
 *      This class readily supports Boolean (yes/no,true/false,on/off),
 *      Float, Double, Integer, and String[] (strings separated by comma)
 *      objects. The programmer may define additional coercions of their own.
 *
 * @author Steven Haehn
 */
public class OptionParser {

    // Used to hold data type converters
    private static final Map<Class<?>, DataParser> converters = new HashMap<>();

    static class DataParser {
        Class<?> dataType;
        Function<String, Object> converter;

        DataParser(Class<?> cls, Function<String, Object> converter) {
            this.dataType = cls;
            this.converter = converter;
        }
    }

    // Supported internal data type converters.
    static {
        accept(Integer.class, Integer::parseInt);
        accept(Boolean.class, OptionParser::parseVerity);
        accept(Float.class, Float::parseFloat);
        accept(Double.class, Double::parseDouble);
        accept(String[].class, s -> s.split(","));
    }

    // Option object referenced by its name(s)
    private final Map<String, Option> options;

    // List of options in order of declaration
    private final List<Option> optionList;

    // Keeps track of separator elements placed in option summary
    private final List<Object> usageSummary;

    private boolean scanning = false;

    private String prologue;  // text emitted before option summary
    private String epilogue;  // text emitted after options summary

    public class Option {

        List<String> names;          // option names/aliases
        String argument;             // argument name for summary
        String value;                // user entered value for option
        Class<?> valueType;          // eg. Integer.class, other than String
        Pattern valuePattern;        // pattern used to accept value
        List<String> allowedValues;  // list of restricted values
        Boolean mandatory;           // true/false when argument present
        StringBuilder description;   // option description for summary
        Consumer<Object> action;     // code to execute when option encountered

        public Option() {
            names = new ArrayList<>();
        }

        void addOption(String option, String arg) throws IllegalArgumentException {
            addAlias(option);
            setArgument(arg);
        }

        void addAlias(String alias) throws IllegalArgumentException {
            names.add(alias);

            if (options.containsKey(alias)) {
                throw new IllegalArgumentException("** Programmer error! Option " + alias + " already defined");
            }

            options.put(alias, this);
        }

        void setAllowedValues(String[] allowed) {
            allowedValues = Arrays.asList(allowed);
        }

        void setValueType(Class<?> type) {
            valueType = type;
        }

        void setArgument(String arg) {
            argument = arg.trim();
            mandatory = !argument.startsWith("[");
        }

        void setPattern(String pattern) {
            valuePattern = Pattern.compile(pattern);
        }

        void addDescription(String descrip) {
            if (description == null) {
                description = new StringBuilder();
            }
            description.append(descrip);
            description.append("\n");
        }

        /**
         * Code to be activated when option encountered.
         *
         * @param action is the code that will be called when the
         * parser encounters the associated named option in its
         * argument list.
         */
        public void execute(Consumer<Object> action) {
            this.action = action;
        }

        String getUsage() {
            StringBuilder line = new StringBuilder();
            String separator = "";
            for (String name : names) {
                line.append(separator);
                line.append(name);
                separator = ", ";
            }

            if (argument != null) {
                line.append(' ');
                line.append(argument);
            }
            line.append("\n");
            if (description != null) {
                line.append("\t");
                line.append(description.toString().replaceAll("\\n", "\n\t"));
            }

            return line.toString();
        }
    }

    /**
     * Instantiate a new option parser
     *
     * This allows the programmer to create an empty option parser that can
     * be added to incrementally elsewhere in the program being built. For
     * example:
     *
     *   OptionParser parser = OptionParser();
     *     .
     *     .
     *   parser.prologue = "Usage: program [options] [file [...]]
     *
     *   parser.on("-?", "--help", "Display this usage.").execute( v -&gt; {
     *       parser.help();
     *   });
     */
    public OptionParser() {
        optionList = new ArrayList<>();
        options = new HashMap<>();
        usageSummary = new ArrayList<>();
    }

    // Allowable text values for Boolean.class, with case insensitivity.
    private static final Pattern VERITY = Pattern.compile("(?i)(true|yes|on)");
    private static final Pattern FALSEHOOD = Pattern.compile("(?i)(false|no|off)");

    private static Boolean parseVerity(String text) {
        Matcher m = VERITY.matcher(text);
        boolean veracity;

        if (m.matches()) {
            veracity = true;
        } else {
            m = FALSEHOOD.matcher(text);
            if (m.matches()) {
                veracity = false;
            } else {
                throw new IllegalArgumentException();
            }
        }
        return veracity;
    }

    /**
     * Supply parser with data conversion mechanism for option value.
     * The following is an example usage used internally:
     *
     *    accept(Integer.class, s -&gt; { return Integer.parseInt(s); });
     *
     * @param type is the internal data class to which an option
     * value should be converted.
     *
     * @param parser is the conversion code that will take the given
     * option value string and produce the named data type.
     */
    public static void accept(Class<?> type, Function<String, Object> parser) {
        converters.put(type, new DataParser(type, parser));
    }

    /**
     * Instantiate a new options parser and construct option actionable components.
     *
     * As an example:
     *
     * <code>
     *   OptionParser opts = OptionParser.execute(parser -&gt; {
     *
     *      parser.prologue =
     *          String.format("\nUsage: %s [options] [subDir1 [...]]\n", program);
     *
     *      parser.on("-?", "--help", "Display this usage.").execute( v -&gt; {
     *          parser.help();
     *      });
     *
     *      parser.epilogue = "That's all folks!";
     *   }
     * </code>
     *
     * @param parser consumer
     * @return OptionParser object
     */
    public static OptionParser execute(Consumer<OptionParser> parser) {
        OptionParser me = new OptionParser();
        parser.accept(me);
        return me;
    }

    /**
     * Provide a 'scanning' option parser.
     *
     * This type of parser only operates on the arguments for which it
     * is constructed. All other arguments passed to it are ignored.
     * That is, it won't raise any errors for unrecognizable input as
     * the normal option parser would.
     *
     * @param parser consumer
     * @return OptionParser object
     */
    public static OptionParser scan(Consumer<OptionParser> parser) {
        OptionParser me = new OptionParser();
        parser.accept(me);
        me.scanning = true;
        return me;
    }

    /**
     * Construct option recognition and description object
     *
     * This method is used to build the option object which holds
     * its recognition and validation criteria, description and
     * ultimately its data handler.
     *
     * The 'on' parameters consist of formatted strings which provide the
     * option names, whether or not the option takes on a value and,
     * if so, the option value type (mandatory/optional). The data type
     * of the option value may also be provided to allow the parser to
     * handle conversion from a string to an internally supported data type.
     *
     * Other parameters which may be provided are:
     *
     *  o String array of legal option values (eg. {"on","off"})
     *  o Regular expression pattern that option value must match
     *  o Multiple line description for the option.
     *
     * There are two forms of option names, short and long. The short
     * option names are a single character in length and are recognized
     * with a single "-" character to the left of the name (eg. -o).
     * The long option names hold more than a single character and are
     * recognized via "--" to the left of the name (eg. --option). The
     * syntax for specifying an option, whether it takes on a value or
     * not, and whether that value is mandatory or optional is as follows.
     * (Note, the use of OPT is an abbreviation for OPTIONAL.)
     *
     * Short name 'x':
     *    -x, -xVALUE, -x=VALUE, -x[OPT], -x[=OPT], -x PLACE
     *
     * The option has the short name 'x'. The first form has no value.
     * the next two require values, the next two indicate that the value
     * is optional (delineated by the '[' character). The last form
     * indicates that the option must have a value, but that it follows
     * the option indicator.
     *
     * Long name 'switch':
     *    --switch, --switch=VALUE, --switch=[OPT], --switch PLACE
     *
     * The option has the long name 'switch'. The first form indicates
     * it does not require a value, The second form indicates that the
     * option requires a value. The third form indicates that option may
     * or may not have value. The last form indicates that a value is
     * required, but that it follows the option indicator.
     *
     * Since an option may have multiple names (aliases), there is a
     * short hand for describing those which take on a value.
     *
     * Option value shorthand:  =VALUE, =[OPT]
     *
     * The first form indicates that the option value is required, the
     * second form indicates that the value is optional. For example
     * the following code says there is an option known by the aliases
     * -a, -b, and -c and that it needs a required value shown as N.
     *
     * <code>
     *     opt.on( "-a", "-b", "-c", "=N" )
     * </code>
     *
     * When an option takes on a value, 'on' may accept a regular expression
     * indicating what kind of values are acceptable. The regular expression
     * is indicated by surrounding the expression with '/' character. For
     * example, "/pattern/" indicates that the only value acceptable is the
     * word 'pattern'.
     *
     * Any string that does not start with a '-', '=', or '/' is used as a
     * description for the option in the summary. Multiple descriptions may
     * be given; they will be shown on additional lines.
     *
     * For programmers:  If a switch starts with 3 dashes (---) it will
     * be hidden from the usage summary and manual generation. It is meant
     * for unit testing access.
     *
     * @param args arguments
     * @return Option
     */
    public Option on(Object... args) {

        Option opt = new Option();

        // Once description starts, then no other option settings are eligible.
        boolean addedDescription = false;

        for (Object arg : args) {
            if (arg instanceof String) {
                String argument = (String) arg;
                if (addedDescription) {
                    opt.addDescription(argument);
                } else if (argument.startsWith("--")) {
                    // handle --switch --switch=ARG --switch=[OPT] --switch PLACE
                    String[] parts = argument.split("[ =]");

                    if (parts.length == 1) {
                        opt.addAlias(parts[0]);
                    } else {
                        opt.addOption(parts[0], parts[1]);
                    }
                } else if (argument.startsWith("-")) {
                    // handle -x -xARG -x=ARG -x[OPT] -x[=OPT] -x PLACE
                    String optName = argument.substring(0, 2);
                    String remainder = argument.substring(2);
                    opt.addOption(optName, remainder);

                } else if (argument.startsWith("=")) {
                    opt.setArgument(argument.substring(1));
                } else if (argument.startsWith("/")) {
                    // regular expression (sans '/'s)
                    opt.setPattern(argument.substring(1, argument.length() - 1));
                } else {
                    // this is description
                    opt.addDescription(argument);
                    addedDescription = true;
                }
            // This is indicator for a addOption of specific allowable option values
            } else if (arg instanceof String[]) {
                opt.setAllowedValues((String[]) arg);
            // This is indicator for option value data type
            // to which the parser will take and convert.
            } else if (arg instanceof Class) {
                opt.setValueType((Class<?>) arg);
            } else if (arg == null) {
                throw new IllegalArgumentException("arg is null");
            } else {
                throw new IllegalArgumentException("Invalid arg: " +
                        arg.getClass().getSimpleName() + " " + arg);
            }
        }

        // options starting with 3 dashes are to be hidden from usage.
        // (the idea here is to hide any unit test entries from general user)
        if (!opt.names.get(0).startsWith("---")) {
            optionList.add(opt);
            usageSummary.add(opt);
        }

        return opt;
    }

    private String argValue(String arg, boolean mandatory) {
        // Initially assume that the given argument is going
        // to be the option's value. Note that if the argument
        // is actually another option (starts with '-') then
        // there is no value available. If the option is required
        // to have a value, null is returned. If the option
        // does not require a value, an empty string is returned.
        String value = arg;
        boolean isOption = value.startsWith("-");

        if (mandatory) {
            if (isOption ) {
                value = null;
            }
        } else if (isOption) {
            value = "";
        }
        return value;
    }

    private String getOption(String arg, int index) throws ParseException {
        String option = null;

        if ( arg.equals("-")) {
            throw new ParseException("Stand alone '-' found in arguments, not allowed", index);
        }

        if (arg.startsWith("-")) {
            if (arg.startsWith("--")) {
                option = arg;                 // long name option (--longOption)
            } else if (arg.length() > 2) {
                option = arg.substring(0, 2); // short name option (-xValue)
            } else {
                option = arg;                 // short name option (-x)
            }
        }
        return option;
    }

    /**
     * Discover full name of partial option name.
     *
     * @param option is the initial substring of a long option name.
     * @param index into original argument list (only used by ParseException)
     * @return full name of given option substring, or null when not found.
     * @throws ParseException when more than one candidate name is found.
     */
    protected String candidate(String option, int index) throws ParseException {
        boolean found = options.containsKey(option);
        List<String> candidates = new ArrayList<>();
        String candidate = null;

        if (found) {
            candidate = option;
        } else {
            // Now check to see if initial substring was entered.
            for (String key: options.keySet()) {
                if (key.startsWith(option)) {
                    candidates.add(key);
                }
            }
            if (candidates.size() == 1 ) {
                candidate = candidates.get(0);
            } else if (candidates.size() > 1) {
                throw new ParseException(
                    "Ambiguous option " + option + " matches " + candidates, index);
            }
        }
        return candidate;
    }

    /**
     * Parse given set of arguments and activate handlers
     *
     * This code parses the given set of parameters looking for a described
     * set of options and activates the code segments associated with the
     * option.
     *
     * Parsing is discontinued when a lone "--" is encountered in the list of
     * arguments. If this is a normal non-scan parser, unrecognized options
     * will cause a parse exception. If this is a scan parser, unrecognized
     * options are ignored.
     *
     * @param args argument vector
     * @return non-option parameters, or all arguments after "--" encountered.
     * @throws ParseException parse exception
     */

    public String[] parse(String[] args) throws ParseException {
        int ii = 0;
        int optind = -1;
        String option;
        while (ii < args.length) {
            option = getOption(args[ii], ii);

            // When scanning for specific options...
            if (scanning) {
                if (option == null || (option = candidate(option, ii)) == null) {
                    optind = ++ii;  // skip over everything else
                    continue;
                }
            }

            if (option == null) {  // no more options? we be done.
                break;
            } else if (option.equals("--")) {  // parsing escape found? we be done.
                optind = ii + 1;
                break;
            } else {

                if ( !scanning ) {
                    String candidate = candidate(option, ii);
                    if (candidate != null) {
                        option = candidate;
                    } else {
                        throw new ParseException("Unknown option: " + option, ii);
                    }
                }
                Option opt = options.get(option);
                opt.value = null;

                if (option.length() == 2 && !option.equals(args[ii])) {  // catches -xValue
                    opt.value = args[ii].substring(2);
                }

                // No argument required?
                if (opt.argument == null || opt.argument.equals("")) {
                    if (opt.value != null) {
                        throw new ParseException("Option " + option + " does not use value.", ii);
                    }
                    opt.value = "";

                // Argument specified but value not yet acquired
                } else if (opt.value == null) {

                    ii++;   // next argument may hold argument value

                    // When option is last in list...
                    if (ii >= args.length) {
                        if (!opt.mandatory) {
                            opt.value = "";  // indicate this option's value was optional
                        }
                    } else {

                        // Look at next argument for value
                        opt.value = argValue(args[ii], opt.mandatory);

                        if (opt.value != null && opt.value.equals("")) {
                            // encountered another option so this
                            // option's value was not required. Backup
                            // argument list index to handle so loop
                            // can re-examine this option.
                            ii--;
                        }
                    }
                }

                // If there is no value setting for the
                // option by now, throw a hissy fit.
                if (opt.value == null) {
                    throw new ParseException("Option " + option + " requires a value.", ii);
                }

                // Only specific values allowed?
                if (opt.allowedValues != null) {
                    if (!opt.allowedValues.contains(opt.value)) {
                        throw new ParseException(
                           "'" + opt.value +
                           "' is unknown value for option " + opt.names +
                           ". Must be one of " + opt.allowedValues, ii);
                    }
                }

                Object value = opt.value;

                // Should option argument match some pattern?
                if (opt.valuePattern != null) {
                    Matcher m = opt.valuePattern.matcher(opt.value);
                    if (!m.matches()) {
                        throw new ParseException(
                           "Value '" + opt.value + "' for option " + opt.names + opt.argument +
                           "\n does not match pattern " + opt.valuePattern, ii);
                    }

                // Handle special conversions of input
                // arguments before sending to action handler.
                } else if (opt.valueType != null) {

                    if (!converters.containsKey(opt.valueType)) {
                        throw new ParseException(
                            "No conversion handler for data type " + opt.valueType, ii);
                    }

                    try {
                        DataParser data = converters.get(opt.valueType);
                        value = data.converter.apply(opt.value);

                    } catch (Exception e) {
                        System.err.println("** " + e.getMessage());
                        throw new ParseException("Failed to parse (" + opt.value + ") as value of " + opt.names, ii);
                    }
                }

                if (opt.action != null) {
                    opt.action.accept(value); // 'do' assigned action
                }
                optind = ++ii;
            }
        }

        // Prepare to gather any remaining arguments
        // to send back to calling program.

        String[] remainingArgs = null;

        if (optind == -1) {
            remainingArgs = args;
        } else if (optind < args.length) {
            remainingArgs = Arrays.copyOfRange(args, optind, args.length);
        } else {
            remainingArgs = new String[0];  // all args used up, send back empty.
        }

        return remainingArgs;
    }

    private String getPrologue() {
        // Assign default prologue statement when none given.
        if (prologue == null) {
            prologue = "Usage: MyProgram [options]";
        }

        return prologue;
    }


    /**
     * Define the prologue to be presented before the options summary.
     * Example: Usage programName [options]
     * @param text that makes up the prologue.
     */
    public void setPrologue(String text) {
        prologue = text;
    }

    /**
     * Define the epilogue to be presented after the options summary.
     * @param text that makes up the epilogue.
     */
    public void setEpilogue(String text) {
        epilogue = text;
    }

    /**
     * Place text in option summary.
     * @param text to be inserted into option summary.
     *
     * Example usage:
     * <code>
     *  OptionParser opts = OptionParser.execute( parser -&gt; {
     *
     *    parser.prologue = String.format("Usage: %s [options] bubba smith", program);
     *    parser.separator("");
     *
     *    parser.on("-y value", "--why me", "This is a description").execute( v -&gt; {
     *        System.out.println("got " + v);
     *    });
     *
     *    parser.separator("  ----------------------------------------------");
     *    parser.separator("  Common Options:");
     *    ...
     *
     *    parser.separator("  ----------------------------------------------");
     *    parser.epilogue = "  That's all Folks!";
     * </code>
     */
    public void separator(String text) {
        usageSummary.add(text);
    }

    /**
     * Obtain option summary.
     * @param indent a string to be used as the option summary initial indent.
     * @return usage string
     */
    public String getUsage(String indent) {

        StringWriter wrt = new StringWriter();
        try (PrintWriter out = new PrintWriter(wrt)) {
            out.println(getPrologue());
            for (Object o : usageSummary) {
                // Need to be able to handle separator strings
                if (o instanceof String) {
                    out.println((String) o);
                } else {
                    out.println(indent + ((Option) o).getUsage());
                }
            }
            if (epilogue != null) {
                out.println(epilogue);
            }
            out.flush();
        }
        return wrt.toString();
    }

    /**
     * Obtain option summary.
     * @return option summary
     */
    public String getUsage() {
        return getUsage("  ");
    }

    /**
     * Print out option summary.
     */
    public void help() {
        System.out.println(getUsage());
    }

    /**
     * Print out option summary on provided output stream.
     * @param out print stream
     */
    public void help(PrintStream out) {
        out.println(getUsage());
    }

    protected List<Option> getOptionList() {
        return optionList;
    }
}
