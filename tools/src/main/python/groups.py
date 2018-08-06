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
# Copyright (c) 2016, 2018, Oracle and/or its affiliates. All rights reserved.
#

import os
import sys
import argparse
from java import Java, get_javaparser
import logging


"""
 Script for manipulating project groups
"""

if __name__ == '__main__':

    parser = argparse.ArgumentParser(description='Java wrapper for project '
                                     'group manipulation',
                                     parents=[get_javaparser()])

    args = parser.parse_args()

    if args.debug:
        logging.basicConfig(level=logging.DEBUG)
    else:
        logging.basicConfig()

    logger = logging.getLogger(os.path.basename(sys.argv[0]))

    cmd = Java(args.options, classpath=args.jar, java=args.java,
               java_opts=args.java_opts,
               main_class='org.opengrok.indexer.configuration.Groups',
               logger=logger)
    cmd.execute()
    ret = cmd.getretcode()
    if ret is None or ret != 0:
        logger.error(cmd.getoutputstr())
        logger.error("command failed (return code {})".format(ret))
        sys.exit(1)
