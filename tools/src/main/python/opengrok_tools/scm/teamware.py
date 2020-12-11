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
# Portions Copyright (c) 2020, Krystof Tulinger <k.tulinger@seznam.cz>
#

import os

from .repository import Repository, RepositoryException


class TeamwareRepository(Repository):
    def __init__(self, logger, path, project, command, env, hooks, timeout):
        super().__init__(logger, path, project, command, env, hooks, timeout)

        #
        # Teamware is different than the rest of the repositories.
        # It really needs a path to the tools since the 'bringover'
        # binary by itself is not sufficient to update the workspace.
        # So instead of passing path to the binary, the command
        # argument contains the path to the directory that contains
        # the binaries.
        #
        command = self._repository_command(command)
        if command:
            if not os.path.isdir(command):
                raise RepositoryException("Cannot construct Teamware "
                                          "repository: {} is not a "
                                          "directory".format(command))

            try:
                path = os.environ['PATH']
            except KeyError:
                raise OSError("Cannot get PATH env var")

            path += ":" + command
            self.env['PATH'] = path
        else:
            raise RepositoryException("Cannot get path to Teamware commands")

    def reposync(self):
        #
        # If there is no Teamware specific subdirectory, do not bother
        # syncing.
        #
        if not os.path.isdir(os.path.join(self.path, "Codemgr_wsdata")):
            self.logger.debug("Not a teamware repository: {} -> not syncing".
                              format(self.path))
            return 0

        return self._run_custom_sync_command(['bringover'])
