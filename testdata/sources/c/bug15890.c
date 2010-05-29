// -*- coding: utf-8 -*-

/*
 * Test for bug #15890. Ctags and JFlex do not agree on line
 * numbering. JFlex regards \u000B, \u000C, \u0085, \u2028 and \u2029
 * as line terminator, whereas ctags doesn't. If one of these
 * characters occurred in a file, definitions that came after it would
 * not be recognized as definitions by the xrefs, since the line
 * numbers didn't match what ctags returned.
 */

/* This line contains \u000B:  */

/* This line contains \u000C:  */

/* This line contains \u0085:  */

/* This line contains \u2028:   */

/* This line contains \u2029:   */

/*
 * Now add a definition for the tests to check.
 */

int bug15890(int x)
{
  return x+1;
}
