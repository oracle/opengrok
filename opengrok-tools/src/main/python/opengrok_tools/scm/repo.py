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
# Copyright (c) 2018, 2020, Oracle and/or its affiliates. All rights reserved.
# Portions Copyright (c) 2020, Krystof Tulinger <k.tulinger@seznam.cz>
#

from shutil import which

from .repository import Repository, RepositoryException
from ..utils.command import Command


class RepoRepository(Repository):
    def __init__(self, logger, path, project, command, env, hooks, timeout):
        super().__init__(logger, path, project, command, env, hooks, timeout)

        self.command = self._repository_command(command, default=lambda: which('repo'))

        if not self.command:
            raise RepositoryException("Cannot get repo command")

    def reposync(self):
        repo_command = [self.command, "sync", "-cf"]
        cmd = self.getCommand(repo_command, work_dir=self.path,
                              env_vars=self.env, logger=self.logger)
        cmd.execute()
        self.logger.info("output of {}:".format(cmd))
        self.logger.info(cmd.getoutputstr())
        if cmd.getretcode() != 0 or cmd.getstate() != Command.FINISHED:
            cmd.log_error("failed to perform sync")
            return 1

        return 0

    def incoming_check(self):
        repo_command = [self.command, "sync", "-n"]
        cmd = self.getCommand(repo_command, work_dir=self.path,
                              env_vars=self.env, logger=self.logger)
        cmd.execute()
        self.logger.info("output of {}:".format(cmd))
        self.logger.info(cmd.getoutputstr())
        if cmd.getretcode() != 0 or cmd.getstate() != Command.FINISHED:
            cmd.log_error("failed to perform sync")
            raise RepositoryException('failed to check for incoming in '
                                      'repository {}'.format(self))

        if len(cmd.getoutput()) == 0:
            return False
        else:
            return True
