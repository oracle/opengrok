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

import logging
import sys


class MinLogLevelFilter(logging.Filter):
    def __init__(self, level):
        super()
        self.level = level

    def filter(self, record):
        if record.levelno >= self.level:
            return True

        return False


class MaxLogLevelFilter(logging.Filter):
    def __init__(self, level):
        super()
        self.level = level

    def filter(self, record):
        if record.levelno <= self.level:
            return True

        return False


def get_console_logger(name, level=logging.INFO):
    """
    Get logger that logs logging.ERROR and higher to stderr, the rest
    to stdout.
    :param level: base logging level
    :param name: name of the logger
    :return: logger
    """
    formatter = logging.Formatter('%(message)s')

    err_handler = logging.StreamHandler(stream=sys.stderr)
    err_handler.setFormatter(formatter)
    err_handler.setLevel(logging.ERROR)

    stdout_handler = logging.StreamHandler(stream=sys.stdout)
    stdout_handler.setFormatter(formatter)
    stdout_handler.setLevel(logging.INFO)

    min_filter = MinLogLevelFilter(logging.ERROR)
    err_handler.addFilter(min_filter)

    max_filter = MaxLogLevelFilter(logging.INFO)
    stdout_handler.addFilter(max_filter)

    logger = logging.getLogger(name)
    logger.setLevel(level)
    logger.addHandler(stdout_handler)
    logger.addHandler(err_handler)

    return logger
