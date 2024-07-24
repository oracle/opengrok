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
# Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
#

import os
import shutil
import tempfile

import pytest
from mockito import mock
from opengrok_tools.scm.mercurial import MercurialRepository
from opengrok_tools.utils.command import Command


def add_commit_file(file_path, repo_path, comment):
    """
    :param file_path: path to the file to be created
    :param repo_path: Mercurial repository path
    :param comment: content and commit comment
    """
    with open(file_path, "w") as fp:
        fp.write(comment)
    assert os.path.exists(file_path)

    cmd = Command(["hg", "add", file_path], work_dir=repo_path)
    cmd.execute()
    assert cmd.getretcode() == 0

    cmd = Command(["hg", "commit", "-m", comment], work_dir=repo_path)
    cmd.execute()
    assert cmd.getretcode() == 0


@pytest.mark.parametrize("create_file_in_parent", [True, False])
@pytest.mark.skipif(shutil.which("hg") is None, reason="need hg")
def test_strip_outgoing(create_file_in_parent):
    with tempfile.TemporaryDirectory() as test_root:
        # Initialize Mercurial repository.
        repo_parent_path = os.path.join(test_root, "parent")
        os.mkdir(repo_parent_path)
        cmd = Command(["hg", "init"], work_dir=repo_parent_path)
        cmd.execute()
        assert cmd.getretcode() == 0

        #
        # Create a file in the parent repository. This is done so that
        # after the strip is done in the cloned repository, the branch
        # is still known for 'hg out'. Normally this would be the case.
        #
        if create_file_in_parent:
            file_path = os.path.join(repo_parent_path, "foo.txt")
            add_commit_file(file_path, repo_parent_path, "parent")

        # Clone the repository and create couple of new changesets.
        repo_clone_path = os.path.join(test_root, "clone")
        cmd = Command(
            ["hg", "clone", repo_parent_path, repo_clone_path], work_dir=test_root
        )
        cmd.execute()
        assert cmd.getretcode() == 0

        file_path = os.path.join(repo_clone_path, "foo.txt")
        add_commit_file(file_path, repo_clone_path, "first")

        with open(file_path, "a") as fp:
            fp.write("bar")
        cmd = Command(["hg", "commit", "-m", "second"], work_dir=repo_clone_path)
        cmd.execute()
        assert cmd.getretcode() == 0

        # Strip the changesets.
        repository = MercurialRepository(
            "hgout", mock(), repo_clone_path, "test-1", None, None, None, None
        )
        assert repository.strip_outgoing()
        assert not repository.strip_outgoing()
