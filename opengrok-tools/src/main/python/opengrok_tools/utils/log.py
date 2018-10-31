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


def get_console_logger(name, level=logging.INFO, format='%(message)s'):
    """
    Get logger that logs logging.ERROR and higher to stderr, the rest
    to stdout. For logging.DEBUG level more verbose format is used.

    :param name: name of the logger
    :param level: base logging level
    :param format: format string to use
    :return: logger
    """
    if level == logging.DEBUG:
        format = '%(asctime)s %(levelname)8s %(name)s | %(message)s'

    formatter = logging.Formatter(format)

    stderr_handler = logging.StreamHandler(stream=sys.stderr)
    stderr_handler.setFormatter(formatter)

    stdout_handler = logging.StreamHandler(stream=sys.stdout)
    stdout_handler.setFormatter(formatter)

    stderr_handler.addFilter(lambda rec: rec.levelno >= logging.ERROR)
    stdout_handler.addFilter(lambda rec: rec.levelno <= logging.INFO)

    logger = logging.getLogger(name)
    logger.setLevel(level)
    logger.propagate = False
    logger.addHandler(stdout_handler)
    logger.addHandler(stderr_handler)

    return logger
