#!/usr/bin/env python3
#
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

#
# Copyright (c) 2017, 2021, Oracle and/or its affiliates. All rights reserved.
#

import argparse

from .log import add_log_level_argument
from ..version import __version__ as tools_version


def str2bool(v):
    if isinstance(v, bool):
        return v

    if isinstance(v, str):
        v_lower = v.lower()
        if v_lower in ('yes', 'true', 'y', '1'):
            return True
        elif v_lower in ('no', 'false', 'n', '0'):
            return False

    raise argparse.ArgumentTypeError('Boolean value or its string '
                                     'representation expected.')


def add_http_headers(parser):
    parser.add_argument('-H', '--header', nargs='+',
                        help='add HTTP header(s) to API requests. '
                             'In the form of \'name: value\' or @file_path '
                             'to read headers from input file')


def get_headers(headers_list):
    """
    Complement to add_http_headers() for parsing the data
    acquired from argument parsing.

    :param headers_list: list of 'name: value' strings
    :return: dictionary indexed with names
    """
    headers = {}
    if headers_list:
        for arg in headers_list:
            if arg.startswith("@"):
                file_path = arg[1:]
                with open(file_path) as file:
                    for line in file:
                        line = line.rstrip()
                        if ': ' in line:
                            name, value = line.split(': ')
                            headers[name] = value
            else:
                name, value = arg.split(': ')
                headers[name] = value

    return headers


def get_base_parser(tool_version=None):
    """
    Get the base parser which supports --version option reporting
    the overall version of the tools and the specific version of the
    invoked tool.
    :param tool_version: the specific version tool if applicable
    :return: the parser
    """
    parser = argparse.ArgumentParser(add_help=False)
    add_log_level_argument(parser)
    version = tools_version
    if tool_version:
        version += ' (v{})'.format(tool_version)
    parser.add_argument('-v', '--version', action='version', version=version,
                        help='Version of the tool')
    return parser


def get_java_parser():
    parser = argparse.ArgumentParser(add_help=False,
                                     parents=[get_base_parser()])
    parser.add_argument('-j', '--java',
                        help='path to java binary')
    parser.add_argument('-J', '--java_opts',
                        help='java options. Use one for every java option, '
                             'e.g. -J=-server -J=-Xmx16g',
                        action='append')
    parser.add_argument('-e', '--environment', action='append',
                        help='Environment variables in the form of name=value')
    parser.add_argument('--doprint', type=str2bool, nargs=1, default=None,
                        metavar='boolean',
                        help='Enable/disable printing of messages '
                             'from the application as they are produced.')

    group = parser.add_mutually_exclusive_group(required=True)
    group.add_argument('-a', '--jar',
                       help='Path to jar archive to run')
    group.add_argument('-c', '--classpath',
                       help='Class path')

    parser.add_argument('options', nargs='+', help='options')

    return parser
