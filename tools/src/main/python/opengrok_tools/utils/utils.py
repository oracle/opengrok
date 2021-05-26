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
# Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
#

import os
from shutil import which
from logging import log
import logging
import sys
from urllib.parse import urlparse
from distutils import util
from .exitvals import (
    FAILURE_EXITVAL,
)


def is_exe(fpath):
    return os.path.isfile(fpath) and os.access(fpath, os.X_OK)


def check_create_dir(logger, path):
    """
    Make sure the directory specified by the path exists. If unsuccessful,
    exit the program.
    """
    if not os.path.isdir(path):
        try:
            os.makedirs(path)
        except OSError:
            logger.error("cannot create {} directory".format(path))
            sys.exit(FAILURE_EXITVAL)


def get_command(logger, path, name, level=logging.ERROR):
    """
    Get the path to the command specified by path and name.
    If the path does not contain executable, search for the command
    according to name in OS environment and/or dirname.

    The logging level can be used to set different log level to error messages.
    This is handy when trying to determine optional command.

    Return path to the command or None.
    """

    cmd_file = None
    if path:
        cmd_file = which(path)
        if not is_exe(cmd_file):
            log(level, "file {} is not executable file".
                       format(path))
            return None
    else:
        cmd_file = which(name)
        if not cmd_file:
            # try to search within dirname()
            cmd_file = which(name,
                             path=os.path.dirname(sys.argv[0]))
            if not cmd_file:
                log(level, "cannot determine path to the {} command".
                           format(name))
                return None
    logger.debug("{} = {}".format(name, cmd_file))

    return cmd_file


def get_int(logger, name, value):
    """
    If the supplied value is integer, return it. Otherwise return None.
    """
    if not value:
        return None

    try:
        return int(value)
    except ValueError:
        logger.error("'{}' is not a number: {}".format(name, value))
        return None


def get_bool(logger, name, value):
    """
    If the supplied value is bool, return it. Otherwise return None.
    """
    if value is None:
        return None

    try:
        return bool(util.strtobool(value))
    except ValueError:
        logger.error("'{}' is not a number: {}".format(name, value))
        return None


def is_web_uri(url):
    """
    Check if given string is web URL.
    """
    o = urlparse(url)
    return o.scheme in ['http', 'https']
