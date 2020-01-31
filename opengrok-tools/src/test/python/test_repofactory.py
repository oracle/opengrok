#!/usr/bin/env python3

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
# Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
#

import os
import tempfile
from git import Repo

from opengrok_tools.scm import GitRepository
from opengrok_tools.scm.repofactory import get_repository


def test_repofactory_timeout():
    with tempfile.TemporaryDirectory() as source_root:
        repo_name = "foo"
        repo_path = os.path.join(source_root, repo_name)
        Repo.init(repo_path)
        timeout = 3

        project_name = "foo"    # does not matter for this test
        repo = get_repository(repo_path,
                              "git", project_name,
                              timeout=timeout)
        assert repo is not None
        assert isinstance(repo, GitRepository)
        assert repo.timeout == timeout
