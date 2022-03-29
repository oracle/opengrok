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
# Copyright (c) 2009, 2022, Oracle and/or its affiliates. All rights reserved.
#


import argparse
import os
import sys
import tempfile

from .utils.indexer import Indexer
from .utils.log import get_console_logger, get_class_basename, fatal
from .utils.opengrok import get_configuration
from .utils.parsers import get_java_parser, add_http_headers, get_headers
from .utils.exitvals import (
    FAILURE_EXITVAL,
    SUCCESS_EXITVAL
)

"""
 OpenGrok reindexing script for single project. Makes sure it uses
 logging template specific for the project and creates log directory.

"""


def get_logprop_file(template, pattern, project):
    """
    Return the filename of file with logging properties specific for given
    project.
    """

    with open(template, 'r') as f:
        data = f.read()

    data = data.replace(pattern, project)

    with tempfile.NamedTemporaryFile(delete=False) as tmpf:
        tmpf.write(data.encode())

    return tmpf.name


def get_config_file(logger, uri, headers=None, timeout=None):
    """
    Get fresh configuration from the webapp and store it in temporary file.
    Return file name on success, None on failure.
    """
    config = get_configuration(logger, uri, headers=headers, timeout=timeout)
    if config is None:
        return None

    with tempfile.NamedTemporaryFile(delete=False) as tmpf:
        tmpf.write(config.encode())

    return tmpf.name


def main():
    parser = argparse.ArgumentParser(description='OpenGrok indexer wrapper '
                                                 'for indexing single project',
                                     formatter_class=argparse.ArgumentDefaultsHelpFormatter,
                                     parents=[get_java_parser()],
                                     prog=sys.argv[0])
    parser.add_argument('-t', '--template',
                        help='Logging template file')
    parser.add_argument('-p', '--pattern',
                        help='Pattern to substitute in logging template with'
                             'project name')
    parser.add_argument('-P', '--project', required=True,
                        help='Project name')
    parser.add_argument('-d', '--directory',
                        help='Logging directory')
    parser.add_argument('-U', '--uri', default='http://localhost:8080/source',
                        help='URI of the webapp with context path')
    parser.add_argument('--printoutput', action='store_true', default=False)
    add_http_headers(parser)
    parser.add_argument('--api_timeout', type=int, default=3,
                        help='Set response timeout in seconds for RESTful API calls')

    cmd_args = sys.argv[1:]
    try:
        args = parser.parse_args(cmd_args)
    except ValueError as e:
        fatal(e)

    logger = get_console_logger(get_class_basename(), args.loglevel)

    # Make sure the log directory exists.
    if args.directory and not os.path.isdir(args.directory):
        os.makedirs(args.directory)

    # Get files needed for per-project reindex.
    headers = get_headers(args.header)
    conf_file = get_config_file(logger, args.uri, headers=headers, timeout=args.api_timeout)
    if conf_file is None:
        fatal("could not get config file to run the indexer")
    logprop_file = None
    if args.template and args.pattern:
        logprop_file = get_logprop_file(args.template, args.pattern,
                                        args.project)

    # Reindex with the modified logging.properties file and read-only config.
    indexer_options = ['-R', conf_file] + args.options
    extra_options = os.environ.get("OPENGROK_INDEXER_OPTIONAL_ARGS")
    if extra_options:
        logger.debug('indexer arguments extended with {}'.format(extra_options))
        # Prepend the extra options because we want the arguments to end with a project.
        indexer_options = extra_options.split() + indexer_options
    java_opts = []
    if args.java_opts:
        java_opts.extend(args.java_opts)
    if logprop_file:
        java_opts.append("-Djava.util.logging.config.file={}".
                         format(logprop_file))
    indexer = Indexer(indexer_options, logger=logger, jar=args.jar,
                      java=args.java, java_opts=java_opts,
                      env_vars=args.environment, doprint=args.doprint)
    indexer.execute()
    ret = indexer.getretcode()
    os.remove(conf_file)
    if logprop_file:
        os.remove(logprop_file)

    output_printed = False
    if args.printoutput:
        logger.info(indexer.getoutputstr())
        output_printed = True
    if ret is None or ret != SUCCESS_EXITVAL:
        if not output_printed:
            logger.error(indexer.getoutputstr())
        logger.error("Indexer command for project {} failed (return code {})".
                     format(args.project, ret))
        sys.exit(FAILURE_EXITVAL)


if __name__ == '__main__':
    main()
