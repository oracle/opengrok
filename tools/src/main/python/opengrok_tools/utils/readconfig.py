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
# Copyright (c) 2018, 2019, Oracle and/or its affiliates. All rights reserved.
#

import logging
import json
import yaml
import sys

# The following is to make the JSON parsing work on Python 3.4.
try:
    from json.decoder import JSONDecodeError
except ImportError:
    JSONDecodeError = ValueError


def read_config(logger, inputfile):
    """
    Try to interpret inputfile as either JSON or YAML file,
    parse it and return an object representing its contents.

    If neither is valid, return None.
    """
    logging.debug("reading in {}".format(inputfile))
    cfg = None
    try:
        with open(inputfile) as data_file:
            data = data_file.read()

            try:
                logger.debug("trying JSON")
                cfg = json.loads(data)
            except JSONDecodeError:
                # Not a valid JSON file.
                logger.debug("got exception {}".format(sys.exc_info()[0]))
            else:
                return cfg

            try:
                logger.debug("trying YAML")
                cfg = yaml.safe_load(data)
            except AttributeError:
                # Not a valid YAML file.
                logger.debug("got exception {}".format(sys.exc_info()[0]))
            else:
                if cfg is None:
                    cfg = {}

                return cfg
    except IOError as e:
        logger.error("cannot open '{}': {}".format(inputfile, e.strerror))

    return cfg
