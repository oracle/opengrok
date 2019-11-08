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


def do_api_call(command, uri, verb, headers, json_data):
    logger = logging.getLogger(__name__)

    if verb == 'PUT':
        return put(logger, uri, headers=headers, data=json_data)
    elif verb == 'POST':
        return post(logger, uri, headers=headers, data=json_data)
    elif verb == 'DELETE':
        return delete(logger, uri, headers=headers)
    else:
        raise Exception('Unknown HTTP verb in command {}'.
                        format(command))


def call_rest_api(command, pattern, name):
    """
    Make RESTful API call. Occurrence of the pattern in the URI
    (first part of the command) or data payload will be replaced by the name.

    :param command: command (list of URI, HTTP verb, data payload)
    :param pattern: pattern for command name and/or data substitution
    :param name: command name
    :return return value from given requests method
    """
    command = command.get(COMMAND_PROPERTY)
    uri = command[0].replace(pattern, name)
    verb = command[1]
    data = command[2]

    logger = logging.getLogger(__name__)

    headers = None
    json_data = None
    if data:
        headers = {'Content-Type': 'application/json'}
        json_data = json.dumps(data).replace(pattern, name)
        logger.debug("JSON data: {}".format(json_data))

    return do_api_call(command, uri, verb, headers, json_data)
