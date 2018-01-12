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


class MercurialRepository(Repository):
    def __init__(self, logger, path, project, command, env, hooks):

        super().__init__(logger, path, project, command, env, hooks)

        if command:
            self.command = command
        else:
            self.command = which("hg")

        if not self.command:
            self.logger.error("Cannot get hg command")
            raise OSError

    def get_branch(self):
        hg_command = [self.command, "branch"]
        cmd = Command(hg_command, work_dir=self.path, env_vars=self.env)
        cmd.execute()
        if cmd.getstate() != Command.FINISHED:
            self.logger.debug(cmd.getoutput())
            self.logger.error("failed to get branch for {}".
                              format(self.path))
            return None
        else:
            if not cmd.getoutput():
                return None
            if len(cmd.getoutput()) == 0:
                return None
            return cmd.getoutput()[0].strip()

    def reposync(self):
        branch = self.get_branch()
        if not branch:
            # Error logged allready in get_branch().
            return 1

        hg_command = [self.command, "incoming", "-v"]
        if branch != "default":
            hg_command.append("-b")
            hg_command.append(branch)
        cmd = Command(hg_command, work_dir=self.path, env_vars=self.env)
        cmd.execute()
        self.logger.info(cmd.getoutputstr())
	#
	# 'hg incoming' will return 1 if there are no incoming changesets,
	# so do not check the return value.
	#
        if cmd.getstate() != Command.FINISHED:
            self.logger.error("failed to run 'hg incoming'")
            return 1

        hg_command = [self.command, "pull"]
        if branch != "default":
            hg_command.append("-b")
            hg_command.append(branch)
        cmd = Command(hg_command, work_dir=self.path, env_vars=self.env)
        cmd.execute()
        self.logger.info(cmd.getoutputstr())
        if cmd.getretcode() != 0 or cmd.getstate() != Command.FINISHED:
            self.logger.error("failed to perform pull")
            return 1

        hg_command = [self.command, "update"]
        # Avoid remote branch lookup for default branches since
        # some servers do not support it.
        if branch == "default":
            hg_command.append("--check")
        cmd = Command(hg_command, work_dir=self.path, env_vars=self.env)
        cmd.execute()
        self.logger.info(cmd.getoutputstr())
        if cmd.getretcode() != 0 or cmd.getstate() != Command.FINISHED:
            self.logger.error("failed to perform pull and update")
            return 1

        return 0
