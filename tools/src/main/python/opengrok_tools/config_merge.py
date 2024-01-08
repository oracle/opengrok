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
# Copyright (c) 2017, 2024, Oracle and/or its affiliates. All rights reserved.
#

import argparse
import logging
import sys

from .utils.java import Java
from .utils.log import fatal
from .utils.parsers import get_java_parser
from .utils.exitvals import (
    FAILURE_EXITVAL,
    SUCCESS_EXITVAL
)
"""
 Wrapper for the ConfigMerge Java program merging OpenGrok configuration files.
"""


def merge_config_files(read_only_config_path, current_config_path, out_file_path, jar,
                       loglevel=logging.INFO):
    """
    :param read_only_config_path: path to the read-only configuration
    :param current_config_path: path to the current configuration
    :param out_file_path: path to the merged configuration
    :param jar: path to the opengrok jar file
    :param loglevel: log level
    :return: either FAILURE_EXITVAL or SUCCESS_EXITVAL
    """

    return config_merge_wrapper([read_only_config_path, current_config_path, out_file_path], jar=jar,
                                loglevel=loglevel)


def config_merge_wrapper(options, loglevel=logging.INFO,
                         jar=None, java=None, java_opts=None,
                         doprint=False):

    # Avoid using utils.log.get_console_level() since the stdout of the program
    # is interpreted as data.
    logger = logging.getLogger(__name__)
    logger.setLevel(loglevel)

    cmd = Java(options, classpath=jar, java=java,
               java_opts=java_opts, redirect_stderr=False,
               main_class='org.opengrok.indexer.configuration.ConfigMerge',
               logger=logger, doprint=doprint)
    cmd.execute()
    ret = cmd.getretcode()
    if ret is None or ret != SUCCESS_EXITVAL:
        logger.error(cmd.geterroutput())
        logger.error("command failed (return code {})".format(ret))
        return FAILURE_EXITVAL

    return SUCCESS_EXITVAL


def main():
    parser = argparse.ArgumentParser(description='Java wrapper for project '
                                                 'configuration merging',
                                     parents=[get_java_parser()])

    try:
        args = parser.parse_args()
    except ValueError as e:
        fatal(e)

    return config_merge_wrapper(options=args.options, loglevel=args.loglevel,
                                jar=args.jar, java=args.java,
                                java_opts=args.java_opts, doprint=args.doprint)


if __name__ == '__main__':
    sys.exit(main())
