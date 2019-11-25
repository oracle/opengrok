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
# Copyright (c) 2017, 2019, Oracle and/or its affiliates. All rights reserved.
#

import logging
import json

from .webutil import put, post, delete
from .patterns import COMMAND_PROPERTY


CONTENT_TYPE = 'Content-Type'
APPLICATION_JSON = 'application/json'   # default


def do_api_call(command, uri, verb, headers, data):
    verbs = {
        'PUT': put,
        'POST': post,
        'DELETE': delete
    }
    handler = verbs.get(verb)
    if handler is not None:
        logger = logging.getLogger(__name__)
        return handler(logger, uri, headers=headers, data=data)
    raise Exception('Unknown HTTP verb in command {}'.format(command))


def call_rest_api(command, pattern, name):
    """
    Make RESTful API call. Occurrence of the pattern in the URI
    (first part of the command) or data payload will be replaced by the name.

    Default content type is application/json.

    :param command: command (list of URI, HTTP verb, data payload)
    :param pattern: pattern for command name and/or data substitution
    :param name: command name
    :return return value from given requests method
    """

    logger = logging.getLogger(__name__)

    command = command.get(COMMAND_PROPERTY)
    if pattern and name:
        uri = command[0].replace(pattern, name)
    else:
        uri = command[0]

    verb = command[1]
    data = command[2]

    try:
        headers = command[3]
    except IndexError:
        headers = {}

    if not headers:
        headers = {}

    header_names = [x.lower() for x in headers.keys()]

    if data:
        if CONTENT_TYPE.lower() not in header_names:
            logger.debug("Adding header: {} = {}".
                         format(CONTENT_TYPE, APPLICATION_JSON))
            headers[CONTENT_TYPE] = APPLICATION_JSON

        for (k, v) in headers.items():
            if k.lower() == CONTENT_TYPE.lower():
                if headers[k].lower() == APPLICATION_JSON.lower():
                    logger.debug("Converting {} to JSON".format(data))
                    data = json.dumps(data)
                break

        if pattern and name:
            data = data.replace(pattern, name)
        logger.debug("entity data: {}".format(data))

    logger.debug("{} API call: {} with data '{}' and headers: {}".
                 format(verb, uri, data, headers))
    return do_api_call(command, uri, verb, headers, data)
