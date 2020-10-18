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
# Portions Copyright 2020 Robert Williams

from shutil import which

from .repository import Repository, RepositoryException
from ..utils.command import Command


class PerforceRepository(Repository):
    def __init__(self, logger, path, project, command, env, hooks, timeout):
        super().__init__(logger, path, project, command, env, hooks, timeout)

        self.command = self._repository_command(
            command,
            default=lambda: which('p4')
        )

        if not self.command:
            raise RepositoryException("Cannot get perforce command")

    def reposync(self):
        perforce_command = [self.command, "sync"]
        cmd = self.getCommand(perforce_command, work_dir=self.path,
                              env_vars=self.env, logger=self.logger)
        cmd.execute()
        self.logger.info("output of {}:".format(perforce_command))
        self.logger.info(cmd.getoutputstr())
        if cmd.getretcode() != 0 or cmd.getstate() != Command.FINISHED:
            cmd.log_error("failed to perform sync")
            return 1
        return 0
