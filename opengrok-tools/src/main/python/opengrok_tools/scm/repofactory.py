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

import logging

from .cvs import CVSRepository
from .git import GitRepository
from .mercurial import MercurialRepository
from .repo import RepoRepository
from .svn import SubversionRepository
from .teamware import TeamwareRepository

logger = logging.getLogger(__name__)


def get_repository(path, repo_type, project, commands, env, hooks,
                   timeout):
    """
    :param path: full path
    :param repo_type: repository type
    :param project: project name
    :param commands: commands list
    :param env: environment varibables dictionary
    :param hooks: hook dictionary
    :param timeout: timeout in seconds
    :return: a Repository derived object according to the type specified
    or None if given repository type cannot be found.
    """

    repo_lower = repo_type.lower()

    logger.debug("Constructing repo object for path {}".format(path))

    if not commands:
        commands = {}

    if repo_lower in ["mercurial", "hg"]:
        return MercurialRepository(logger, path, project,
                                   commands.get("hg"),
                                   env, hooks, timeout)
    elif repo_lower in ["teamware", "sccs"]:
        return TeamwareRepository(logger, path, project,
                                  commands.get("teamware"),
                                  env, hooks, timeout)
    elif repo_lower.lower() == "cvs":
        return CVSRepository(logger, path, project,
                             commands.get("cvs"),
                             env, hooks, timeout)
    elif repo_lower in ["svn", "subversion"]:
        return SubversionRepository(logger, path, project,
                                    commands.get("svn"),
                                    env, hooks, timeout)
    elif repo_lower == "git":
        return GitRepository(logger, path, project,
                             commands.get("git"),
                             env, hooks, timeout)
    elif repo_lower == "repo":
        return RepoRepository(logger, path, project,
                              commands.get("repo"),
                              env, hooks, timeout)
    else:
        logger.warning("Unsupported repository type {}: {}".
                       format(repo_type, path))
        return None
