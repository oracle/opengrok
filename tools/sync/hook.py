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
from command import Command
import logging

def run_hook(logger, script, path):
    """
    Change a working directory to specified path, run a command
    and change the working directory back to its original value.

    Return 0 on success, 1 on failure.
    """

    orig_cwd = os.getcwd()

    try:
        os.chdir(path)
    except:
        logger.error("Cannot chdir to {}".format(path))
        return 1

    cmd = Command([script])
    cmd.execute()
    if cmd.state is not "finished" or cmd.getretcode() != 0:
        logger.error("failed to execute {}".format(cmd))
        logger.debug(cmd.getoutput())
        return 1

    logger.info(cmd.getoutput())

    try:
        os.chdir(orig_cwd)
    except:
        logger.error("Cannot chdir to {}".format(orig_cwd))
        return 1

    return 0
