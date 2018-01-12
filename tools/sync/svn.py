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

from command import Command
from repository import Repository
from utils import which


class SubversionRepository(Repository):
    def __init__(self, logger, path, project, command, env, hooks):

        super().__init__(logger, path, project, command, env, hooks)

        if command:
            self.command = command
        else:
            self.command = which("svn")

        if not self.command:
            self.logger.error("Cannot get svn command")
            raise OSError

    def reposync(self):
        hg_command = [self.command, "update"]
        cmd = Command(hg_command, work_dir=self.path, env_vars=self.env)
        cmd.execute()
        self.logger.info(cmd.getoutputstr())
        if cmd.getretcode() != 0 or cmd.getstate() != Command.FINISHED:
            self.logger.error("failed to perform update for {}".
                              format(self.path))
            return 1

        return 0
