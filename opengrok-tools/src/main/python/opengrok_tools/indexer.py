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

from .utils.indexer import FindCtags, Indexer
from .utils.java import get_javaparser
from .utils.log import get_console_logger

"""
  opengrok.jar wrapper

  This script can be used to run the OpenGrok indexer.
"""


def main():
    parser = argparse.ArgumentParser(description='OpenGrok indexer wrapper',
                                     parents=[get_javaparser()])
    parser.add_argument('-C', '--no_ctags_check', action='store_true',
                        default=False, help='Suppress checking for ctags')

    args = parser.parse_args()

    loglevel = logging.INFO
    if args.debug:
        loglevel = logging.DEBUG
    logger = get_console_logger(__name__, loglevel)

    #
    # Since it is not possible to tell what kind of action is performed,
    # always check ctags presence unless told not to.
    #
    if not args.no_ctags_check and not FindCtags(logger):
        logger.warning("Unable to determine Universal CTags command")

    indexer = Indexer(args.options, logger=logger, java=args.java,
                      jar=args.jar, java_opts=args.java_opts,
                      env_vars=args.environment, doprint=True)
    indexer.execute()
    ret = indexer.getretcode()
    if ret is None or ret != 0:
        # The output is already printed thanks to 'doprint' above.
        logger.error("Indexer command failed (return code {})".format(ret))
        sys.exit(1)


if __name__ == '__main__':
    main()
