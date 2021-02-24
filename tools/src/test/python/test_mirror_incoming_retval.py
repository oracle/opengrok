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
# Copyright (c) 2020, 2021, Oracle and/or its affiliates. All rights reserved.
# Portions Copyright (c) 2020, Krystof Tulinger <k.tulinger@seznam.cz>
#

import multiprocessing
import os
import sys
import tempfile

import pytest
from git import Repo

import opengrok_tools.mirror
from opengrok_tools.scm import get_repository
from opengrok_tools.utils.exitvals import CONTINUE_EXITVAL


@pytest.mark.skipif(not os.name.startswith("posix"), reason="requires posix")
def test_incoming_retval(monkeypatch):
    """
    Test that the special CONTINUE_EXITVAL value bubbles all the way up to
    the mirror.py return value.
    """

    # The default has changed for python 3.8 (see https://github.com/oracle/opengrok/issues/3296).
    # Because of the mocking we need to use the "fork" type to propagate all mocks to the
    # processes spawned by mirror command
    multiprocessing.set_start_method('fork')

    class MockResponse:

        # mock json() method always returns a specific testing dictionary
        @staticmethod
        def json():
            return "true"

        @staticmethod
        def raise_for_status():
            pass

    with tempfile.TemporaryDirectory() as source_root:
        repo_name = "parent_repo"
        repo_path = os.path.join(source_root, repo_name)
        cloned_repo_name = "cloned_repo"
        cloned_repo_path = os.path.join(source_root, cloned_repo_name)
        project_name = "foo"  # does not matter for this test

        os.mkdir(repo_path)

        def mock_get_repos(*args, **kwargs):
            return [get_repository(cloned_repo_path,
                                   "git", project_name)]

        def mock_get(*args, **kwargs):
            return MockResponse()

        def mock_get_config_value(*args, **kwargs):
            return source_root

        # Clone a Git repository so that it can pull.
        repo = Repo.init(repo_path)
        with repo.config_writer() as git_config:
            git_config.set_value('user', 'email', 'someone@example.com')
            git_config.set_value('user', 'name', 'John Doe')

        new_file_path = os.path.join(repo_path, 'foo')
        with open(new_file_path, 'w'):
            pass
        assert os.path.isfile(new_file_path)
        index = repo.index
        index.add([new_file_path])
        index.commit("add file")
        repo.clone(cloned_repo_path)

        with monkeypatch.context() as m:
            m.setattr(sys, 'argv', ['prog', "-I", project_name])

            # With mocking done via pytest it is necessary to patch
            # the call-site rather than the absolute object path.
            m.setattr("opengrok_tools.mirror.get_config_value", mock_get_config_value)
            m.setattr("opengrok_tools.utils.mirror.get_repos_for_project", mock_get_repos)
            m.setattr("opengrok_tools.utils.mirror.do_api_call", mock_get)

            assert opengrok_tools.mirror.main() == CONTINUE_EXITVAL
