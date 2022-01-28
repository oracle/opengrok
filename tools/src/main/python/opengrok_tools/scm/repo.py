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


class RepoRepository(Repository):
    def __init__(self, name, logger, path, project, command, env, hooks, timeout):
        super().__init__(name, logger, path, project, command, env, hooks, timeout)

        self.command = self._repository_command(command, default=lambda: which('repo'))

        if not self.command:
            raise RepositoryException("Cannot get repo command")

    def top_level(self):
        """
        It is not desired to descend into sub-repositories when syncing.
        :return:
        """
        return True

    def reposync(self):
        return self._run_custom_sync_command([self.command, 'sync', '-c'])

    def incoming_check(self):
        return self._run_custom_incoming_command([self.command, 'sync', '-n'])
