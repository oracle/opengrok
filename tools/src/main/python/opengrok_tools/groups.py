#!/usr/bin/env python3

# CDDL HEADER START
#
# The contents of this file are subject to the terms of the
# Common Development and Distribution License (the "License").
# You may not use this file except in compliance with the License.
#
# See LICENSE.txt included in this distribution for the specific
# language governing permissions and limitations under the License.
#
# When distributing Covered Code, include this CDDL HEADER in each
# file and include the License file at LICENSE.txt.
# If applicable, add the following below this CDDL HEADER, with the
# fields enclosed by brackets "[]" replaced with your own identifying
# information: Portions Copyright [yyyy] [name of copyright owner]
#
# CDDL HEADER END

#
# Copyright (c) 2016, 2024, Oracle and/or its affiliates. All rights reserved.
#

import argparse
import sys

from .utils.java import Java
from .utils.log import get_console_logger, get_class_basename, fatal
from .utils.parsers import get_java_parser
from .utils.exitvals import (
    FAILURE_EXITVAL,
    SUCCESS_EXITVAL
)
"""
 Script for manipulating project groups
"""


def main():
    parser = argparse.ArgumentParser(description='Java wrapper for project '
                                                 'group manipulation',
                                     parents=[get_java_parser()])

    try:
        args = parser.parse_args()
    except ValueError as e:
        fatal(e)

    logger = get_console_logger(get_class_basename(), args.loglevel)

    cmd = Java(args.options, classpath=args.jar, java=args.java,
               java_opts=args.java_opts, redirect_stderr=False,
               main_class='org.opengrok.indexer.configuration.Groups',
               logger=logger, doprint=args.doprint,
               max_line_length=-1, max_lines=-1)
    cmd.execute()
    ret = cmd.getretcode()
    if ret is None or ret != SUCCESS_EXITVAL:
        logger.error(cmd.geterroutput())
        logger.error("command failed (return code {})".format(ret))
        sys.exit(FAILURE_EXITVAL)
    else:
        print(cmd.getoutputstr())


if __name__ == '__main__':
    main()
