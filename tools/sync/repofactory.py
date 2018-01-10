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

from mercurial import MercurialRepository
from teamware import TeamwareRepository
from cvs import CVSRepository
from svn import SubversionRepository
from git import GitRepository
from utils import get_dict_val


def get_repository(logger, path, repo_type, project, commands, env, hooks):
    """
    Repository factory. Returns a Repository derived object according
    to the type specified or None if given repository type cannot
    be found.
    """
    repo_lower = repo_type.lower()

    logger.debug("Constructing repo object for path {}".format(path))

    if repo_lower in ["mercurial", "hg"]:
        return MercurialRepository(logger, path, project,
                                   get_dict_val(commands, "hg"),
                                   env, hooks)
    elif repo_lower in ["teamware", "sccs"]:
        return TeamwareRepository(logger, path, project,
                                  get_dict_val(commands, "teamware"),
                                  env, hooks)
    elif repo_lower.lower() == "cvs":
        return CVSRepository(logger, path, project,
                             get_dict_val(commands, "cvs"),
                             env, hooks)
    elif repo_lower == "svn":
        return SubversionRepository(logger, path, project,
                                    get_dict_val(commands, "svn"),
                                    env, hooks)
    elif repo_lower == "git":
        return GitRepository(logger, path, project,
                             get_dict_val(commands, "git"),
                             env, hooks)
    else:
        return None
