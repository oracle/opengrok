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

from ..utils.command import Command
from .repository import Repository, RepositoryException
from shutil import which


class GitRepository(Repository):
    def __init__(self, logger, path, project, command, env, hooks, timeout):

        super().__init__(logger, path, project, command, env, hooks, timeout)

        if command:
            self.command = command
        else:
            self.command = which("git")

        if not self.command:
            self.logger.error("Cannot get git command")
            raise OSError

    def reposync(self):
        git_command = [self.command, "pull", "--ff-only"]
        cmd = self.getCommand(git_command, work_dir=self.path,
                              env_vars=self.env, logger=self.logger)
        cmd.execute()
        self.logger.info(cmd.getoutputstr())
        if cmd.getretcode() != 0 or cmd.getstate() != Command.FINISHED:
            cmd.log_error("failed to perform pull")
            return 1

        return 0

    def incoming(self):
        git_command = [self.command, "pull", "--dry-run"]
        cmd = self.getCommand(git_command, work_dir=self.path,
                              env_vars=self.env, logger=self.logger)
        cmd.execute()
        if cmd.getretcode() != 0 or cmd.getstate() != Command.FINISHED:
            cmd.log_error("failed to perform pull")
            raise RepositoryException('failed to check for incoming in '
                                      'repository {}'.format(self))

        if len(cmd.getoutput()) == 0:
            return False
        else:
            return True
