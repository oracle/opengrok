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
# Copyright (c) 2018, 2023, Oracle and/or its affiliates. All rights reserved.
#

import os
from shutil import which
from logging import log
import logging
import sys
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
            os.makedirs(path, exist_ok=True)
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


def strtobool(value):
    """
    Convert string to 0 or 1
    :param value: string
    :return: 0 or 1
    """

    if value.lower() in ["y", "yes", "t", "true", "on", "1"]:
        return 1

    if value.lower() in ["n", "no", "f", "false", "off", "0"]:
        return 0

    raise ValueError("invalid value")


def get_bool(logger, name, value):
    """
    If the supplied value is bool or its representation, return the bool value.
    Otherwise return None.
    """
    if value is None:
        return None

    if type(value) is bool:
        return value

    try:
        return bool(strtobool(value))
    except ValueError:
        logger.error("'{}' is not a number: {}".format(name, value))
        return None
