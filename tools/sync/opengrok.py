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
from command import Command


def get_repos(logger, project, messages_file):
    """
    Get list of repositories for given project name.
    For the time being this is done by executing the messages_file command.

    Return  string with the result on success, None on failure.

    XXX replace this with REST request after issue #1801

    """

    cmd = Command([messages_file, '-n', 'project', '-t', project, 'get-repos'])
    cmd.execute()
    if cmd.state is not "finished" or cmd.getretcode() != 0:
        logger.error("execution of command '{}' failed".format(cmd))
        return None

    ret = []
    for line in cmd.getoutput():
        ret.append(line.strip())

    return ret


def get_first_line(logger, command):
    cmd = Command(command)
    cmd.execute()
    if cmd.state is not "finished" or cmd.getretcode() != 0:
        logger.error("execution of command '{}' failed".format(cmd))
        return None

    if len(cmd.getoutput()) != 1:
        logger.error("output from {} has more than 1 line ({})".
            format(cmd, len(cmd.getoutput())))
        return None

    return cmd.getoutput()[0].strip()


def get_config_value(logger, name, messages_file):
    """
    Get list of repositories for given project name.
    For the time being this is done by executing the messages_file command.

    Return string with the result on success, None on failure.

    XXX replace this with REST request after issue #1801

    """

    return get_first_line(logger, [messages_file, '-n', 'config', '-t',
                          'get', name])


def get_repo_type(logger, path, messages_file):
    """
    Get repository type for given path relative to sourceRoot.

    Return string with the result on success, None on failure.

    XXX replace this with REST request after issue #1801
    """

    return get_first_line(logger, [messages_file, '-n', 'repository', '-t',
                           path, 'get-repo-type'])
