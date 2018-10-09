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

from .command import Command


def run_hook(logger, script, path, env, timeout):
    """
    Change a working directory to specified path, run a command
    and change the working directory back to its original value.

    Return 0 on success, 1 on failure.
    """

    ret = 0
    logger.debug("Running hook '{}' in directory {}".
                 format(script, path))
    cmd = Command([script], logger=logger, work_dir=path, env_vars=env,
                  timeout=timeout)
    cmd.execute()
    if cmd.state is not "finished" or cmd.getretcode() != 0:
        logger.error("command failed: {} -> {}".format(cmd, cmd.getretcode()))
        ret = 1

    logger.info("command output:\n{}".format(cmd.getoutputstr()))

    return ret
