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
# Copyright (c) 2008, 2018, Oracle and/or its affiliates. All rights reserved.
# Portions Copyright (c) 2017-2018, Chris Fraire <cfraire@me.com>.
#

import argparse
import logging
import sys

from .utils.java import Java, get_javaparser
from .utils.log import get_console_logger


def main():
    parser = argparse.ArgumentParser(description='java wrapper',
                                     parents=[get_javaparser()])
    parser.add_argument('-m', '--mainclass', required=True,
                        help='Main class')

    args = parser.parse_args()

    loglevel = logging.INFO
    if args.debug:
        loglevel = logging.DEBUG
    logger = get_console_logger(__name__, loglevel)

    java = Java(args.options, logger=logger, java=args.java,
                jar=args.jar, java_opts=args.java_opts,
                classpath=args.classpath, main_class=args.mainclass,
                env_vars=args.environment)
    java.execute()
    ret = java.getretcode()
    if ret is None or ret != 0:
        logger.error(java.getoutputstr())
        logger.error("java command failed (return code {})".format(ret))
        sys.exit(1)


if __name__ == '__main__':
    main()
