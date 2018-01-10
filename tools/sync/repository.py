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

import abc


class Repository:
    """
    abstract class wrapper for Source Code Management repository
    """

    __metaclass__ = abc.ABCMeta

    def __init__(self, logger, path, project, command, env, hooks):
        self.logger = logger
        self.path = path
        self.project = project
        if env:
            self.env = env
        else:
            self.env = {}

    def __str__(self):
        return self.path

    def sync(self):
        # Eventually, there might be per-repository hooks added here.
        return self.reposync()

    @abc.abstractmethod
    def reposync(self):
        """
        Synchronize the repository by running sync command specific for
        given repository type.

        Return 1 on failure, 0 on success.
        """
        raise NotImplementedError()
