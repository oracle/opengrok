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
# Copyright (c) 2018, 2022, Oracle and/or its affiliates. All rights reserved.
# Portions Copyright (c) 2020, Krystof Tulinger <k.tulinger@seznam.cz>
#

from shutil import which

from .repository import Repository, RepositoryException
from ..utils.command import Command


class GitRepository(Repository):
    def __init__(self, name, logger, path, project, command, env, hooks, timeout):
        super().__init__(name, logger, path, project, command, env, hooks, timeout)

        self.command = self._repository_command(command, default=lambda: which('git'))

        if not self.command:
            raise RepositoryException("Cannot get git command")

    def _configure_git_pull(self):
        # The incoming() check relies on empty output so configure
        # the repository first to avoid getting extra output.
        git_command = [self.command, "config", "--local", "pull.ff", "only"]
        cmd = self.get_command(git_command, work_dir=self.path,
                               env_vars=self.env, logger=self.logger)
        cmd.execute()
        if cmd.getretcode() != 0 or cmd.getstate() != Command.FINISHED:
            cmd.log_error("failed to configure git pull.ff")

    def reposync(self):
        self._configure_git_pull()
        return self._run_custom_sync_command([self.command, 'pull', '--ff-only'])

    def incoming_check(self):
        self._configure_git_pull()
        return self._run_custom_incoming_command([self.command, 'pull', '--dry-run'])

    def strip_outgoing(self):
        self._configure_git_pull()
        status, out = self._run_command([self.command, 'log',
                                        '--pretty=tformat:%H', '--reverse', 'origin..'])
        if status == 0:
            cset = out.get(0)
            if cset:
                self.logger.debug("Resetting the repository {} to parent of changeset {}".
                                  format(self, cset))
                status, out = self._run_command([self.command, 'reset', '--hard',
                                                 cset + '^'])
                if status != 0:
                    raise RepositoryException("failed to reset {} to parent of changeset {}: {}".
                                              format(self, cset, out))
                else:
                    return True
            else:
                return False

        raise RepositoryException("failed to check for outgoing changes in {}: {}".
                                  format(self, status))
