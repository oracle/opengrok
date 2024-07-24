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
# Copyright (c) 2018, 2024, Oracle and/or its affiliates. All rights reserved.
# Portions Copyright (c) 2020, Krystof Tulinger <k.tulinger@seznam.cz>
#

from shutil import which

from .repository import Repository, RepositoryException
from ..utils.command import Command


class MercurialRepository(Repository):
    def __init__(self, name, logger, path, project, command, env, hooks, timeout):
        super().__init__(name, logger, path, project, command, env, hooks, timeout)

        self.command = self._repository_command(command, default=lambda: which('hg'))

        if not self.command:
            raise RepositoryException("Cannot get hg command")

    def get_branch(self):
        hg_command = [self.command, "branch"]
        cmd = self.get_command(hg_command, work_dir=self.path,
                               env_vars=self.env, logger=self.logger)
        cmd.execute()
        self.logger.info("output of {}:".format(cmd))
        self.logger.info(cmd.getoutputstr())
        if cmd.getretcode() != 0 or cmd.getstate() != Command.FINISHED:
            cmd.log_error("failed to get branch")
            return None
        else:
            if not cmd.getoutput():
                self.logger.error("no output from {}".
                                  format(hg_command))
                return None
            if len(cmd.getoutput()) == 0:
                self.logger.error("empty output from {}".
                                  format(hg_command))
                return None
            return cmd.getoutput()[0].strip()

    def reposync(self):
        branch = self.get_branch()
        if not branch:
            # Error logged already in get_branch().
            return 1

        hg_command = [self.command, "pull"]
        if branch != "default":
            hg_command.append("-b")
            hg_command.append(branch)
        cmd = self.get_command(hg_command, work_dir=self.path,
                               env_vars=self.env, logger=self.logger)
        cmd.execute()
        self.logger.info("output of {}:".format(cmd))
        self.logger.info(cmd.getoutputstr())
        if cmd.getretcode() != 0 or cmd.getstate() != Command.FINISHED:
            cmd.log_error("failed to perform pull")
            return 1

        hg_command = [self.command, "update"]
        #
        # Avoid remote branch lookup for default branches since
        # some servers do not support it.
        #
        if branch == "default":
            hg_command.append("--check")

        #
        # In a multi-head situation, select the head with the
        # biggest index as this is likely the correct one.
        #
        hg_command.append("-r")
        hg_command.append("max(head() and branch(\".\"))")

        cmd = self.get_command(hg_command, work_dir=self.path,
                               env_vars=self.env, logger=self.logger)
        cmd.execute()
        self.logger.info("output of {}:".format(cmd))
        self.logger.info(cmd.getoutputstr())
        if cmd.getretcode() != 0 or cmd.getstate() != Command.FINISHED:
            cmd.log_error("failed to perform pull and update")
            return 1

        return 0

    def incoming_check(self):
        branch = self.get_branch()
        if not branch:
            # Error logged already in get_branch().
            raise RepositoryException('cannot get branch for repository {}'.
                                      format(self))

        hg_command = [self.command, 'incoming']
        if branch != "default":
            hg_command.append("-b")
            hg_command.append(branch)
        cmd = self.get_command(hg_command, work_dir=self.path,
                               env_vars=self.env, logger=self.logger)
        cmd.execute()
        self.logger.info("output of {}:".format(cmd))
        self.logger.info(cmd.getoutputstr())
        retcode = cmd.getretcode()
        if cmd.getstate() != Command.FINISHED or retcode not in [0, 1]:
            cmd.log_error("failed to perform incoming")
            raise RepositoryException('failed to perform incoming command '
                                      'for repository {}'.format(self))

        if retcode == 0:
            return True
        else:
            return False

    def strip_outgoing(self):
        """
        Check for outgoing changes and if found, strip them.
        :return: True if there were any changes stripped, False otherwise.
        """
        status, out = self._run_command([self.command, 'out', '-q', '-b', '.',
                                         '--template={rev}\\n'])
        #
        # If there are outgoing changes, 'hg out' returns 0, otherwise returns 1.
        # If the 'hg out' command fails for some reason, it will return 255.
        # Hence, check for positive value as bail out indication.
        #
        if status > 0:
            return False

        lines = list(filter(None, out.split('\n')))
        if len(lines) == 0:
            return False

        # The first revision is the oldest outgoing revision.
        self.logger.debug("Found outgoing changesets in repository {}: {}".
                          format(self, lines))
        cset = lines[0]
        if len(cset) > 0:
            self.logger.debug("Resetting the repository {} to parent of changeset '{}'".
                              format(self, cset))
            status, out = self._run_command([self.command, '--config', 'extensions.strip=',
                                             'strip', cset])
            if status != 0:
                raise RepositoryException("failed to reset {} to parent of changeset '{}': {}".
                                          format(self, cset, out))
            else:
                return True

        return False
