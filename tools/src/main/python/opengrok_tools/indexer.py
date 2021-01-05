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
# Copyright (c) 2008, 2020, Oracle and/or its affiliates. All rights reserved.
# Portions Copyright (c) 2017-2018, Chris Fraire <cfraire@me.com>.
#


import argparse
import os
import sys
import tempfile

from .utils.indexer import FindCtags, Indexer
from .utils.log import get_console_logger, get_class_basename, fatal
from .utils.parsers import get_java_parser
from .utils.exitvals import (
    FAILURE_EXITVAL,
    SUCCESS_EXITVAL
)

from filelock import Timeout, FileLock

"""
  opengrok.jar wrapper

  This script can be used to run the OpenGrok indexer.
"""


def get_project_from_options(options):
    """
    return project name from indexer arguments or None (assumes single project)
    """
    for i, v in enumerate(options):
        if v == '-P':
            try:
                return options[i + 1]
            except IndexError:
                return None

    return None


def main():
    parser = argparse.ArgumentParser(description='OpenGrok indexer wrapper',
                                     parents=[get_java_parser()])
    parser.add_argument('-C', '--no_ctags_check', action='store_true',
                        default=False, help='Suppress checking for ctags')

    try:
        args = parser.parse_args()
    except ValueError as e:
        fatal(e)

    logger = get_console_logger(get_class_basename(), args.loglevel)

    #
    # Since it is not possible to tell what kind of action is performed,
    # always check ctags presence unless told not to.
    #
    if not args.no_ctags_check and not FindCtags(logger):
        logger.warning("Unable to determine Universal CTags command")

    if args.doprint is None:
        logger.debug("Console logging is enabled by default")
        doprint = True
    else:
        doprint = args.doprint[0]
        logger.debug("Console logging: {}".format(doprint))

    project = get_project_from_options(args.options)
    if project:
        lockfile = project + "-indexer"
    else:
        lockfile = os.path.basename(sys.argv[0])

    lock = FileLock(os.path.join(tempfile.gettempdir(), lockfile + ".lock"))
    try:
        with lock.acquire(timeout=0):
            optional_args = os.environ.get("OPENGROK_INDEXER_OPTIONAL_ARGS")
            options = args.options
            if optional_args and len(optional_args) > 0 and options:
                logger.debug("adding optional indexer arguments: {}".
                             format(optional_args))
                if options:
                    options.extend(optional_args)

            indexer = Indexer(options, logger=logger, java=args.java,
                              jar=args.jar, java_opts=args.java_opts,
                              env_vars=args.environment, doprint=doprint)
            indexer.execute()
            ret = indexer.getretcode()
            if ret is None or ret != SUCCESS_EXITVAL:
                # The output is already printed thanks to 'doprint' above.
                logger.error("Indexer command failed (return code {})".
                             format(ret))
                sys.exit(FAILURE_EXITVAL)
    except Timeout:
        logger.warning("Already running, exiting.")
        return FAILURE_EXITVAL


if __name__ == '__main__':
    main()
