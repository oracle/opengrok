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
# Copyright (c) 2019, 2020, Oracle and/or its affiliates. All rights reserved.
# Portions Copyright (c) 2020, Krystof Tulinger <k.tulinger@seznam.cz>
#

import multiprocessing
import os
import stat
import sys
import tempfile

import pytest
import requests
from git import Repo
from mockito import verify, patch, spy2, mock, ANY, when

import opengrok_tools.mirror
from opengrok_tools.scm import Repository
from opengrok_tools.scm.git import GitRepository
from opengrok_tools.scm.repofactory import get_repository
from opengrok_tools.scm.repository import RepositoryException
from opengrok_tools.utils.command import Command
from opengrok_tools.utils.exitvals import (
    CONTINUE_EXITVAL, FAILURE_EXITVAL
)
from opengrok_tools.utils.mirror import check_project_configuration, \
    check_configuration, mirror_project, run_command, get_repos_for_project, \
    HOOKS_PROPERTY, PROXY_PROPERTY, IGNORED_REPOS_PROPERTY, \
    PROJECTS_PROPERTY, DISABLED_CMD_PROPERTY, DISABLED_PROPERTY, \
    CMD_TIMEOUT_PROPERTY, HOOK_TIMEOUT_PROPERTY, DISABLED_REASON_PROPERTY
from opengrok_tools.utils.patterns import COMMAND_PROPERTY, PROJECT_SUBST


def test_empty_project_configuration():
    config = {}
    assert check_project_configuration(config)


def test_none_project_configuration():
    assert check_project_configuration(None)


def test_invalid_project_config_proxy():
    config = {"foo": {PROXY_PROPERTY: True}}
    assert not check_project_configuration(config, proxy=False)


def test_invalid_project_config_option():
    config = {"foo": {"totally_unknown_option": "value"}}
    assert not check_project_configuration(config, proxy=False)


def test_invalid_project_config_regex():
    config = {"[]": {PROXY_PROPERTY: True}}
    assert not check_project_configuration(config, proxy=False)


def test_invalid_project_config_hookdir():
    config = {"foo": {HOOKS_PROPERTY: {"pre": "value"}}}
    assert not check_project_configuration(config, hookdir="/nonexistentdir")


def test_invalid_project_config_ignoredrepos():
    config = {"foo": {IGNORED_REPOS_PROPERTY: {"foo": "bar"}}}
    assert not check_project_configuration(config)


def test_invalid_project_config_hooks():
    with tempfile.TemporaryDirectory() as tmpdir:
        config = {"foo": {HOOKS_PROPERTY: {"pre": "nonexistentfile.sh"}}}
        assert not check_project_configuration(config, hookdir=tmpdir)


def test_invalid_project_config_hooknames():
    with tempfile.TemporaryDirectory() as tmpdir:
        config = {"foo": {HOOKS_PROPERTY: {"blah": "value"}}}
        assert not check_project_configuration(config, hookdir=tmpdir)


@pytest.mark.skipif(not os.name.startswith("posix"), reason="requires posix")
def test_invalid_project_config_nonexec_hook():
    with tempfile.TemporaryDirectory() as tmpdir:
        with open(os.path.join(tmpdir, "foo.sh"), 'w+') as tmpfile:
            tmpfile.write("foo\n")
            config = {"foo": {HOOKS_PROPERTY: {"pre": "foo.sh"}}}
            assert not check_project_configuration(config, hookdir=tmpdir)


@pytest.mark.skipif(not os.name.startswith("posix"), reason="requires posix")
def test_valid_project_config_hook():
    with tempfile.TemporaryDirectory() as tmpdir:
        with open(os.path.join(tmpdir, "foo.sh"), 'w+') as tmpfile:
            tmpfile.write("foo\n")
            st = os.stat(tmpfile.name)
            os.chmod(tmpfile.name, st.st_mode | stat.S_IEXEC)
            config = {"foo": {HOOKS_PROPERTY: {"pre": "foo.sh"}}}
            assert check_project_configuration(config, hookdir=tmpdir)


def test_invalid_config_option():
    assert not check_configuration({"nonexistent": True})


def test_valid_config():
    assert check_configuration({
        PROJECTS_PROPERTY: {"foo": {PROXY_PROPERTY: True}},
        PROXY_PROPERTY: "proxy"
    })


@pytest.mark.skipif(not os.name.startswith("posix"), reason="requires posix")
def test_incoming_retval(monkeypatch):
    """
    Test that the special CONTINUE_EXITVAL value bubbles all the way up to
    the mirror.py return value.
    """

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
            multiprocessing.set_start_method('fork')
            m.setattr(sys, 'argv', ['prog', "-I", project_name])

            # With mocking done via pytest it is necessary to patch
            # the call-site rather than the absolute object path.
            m.setattr("opengrok_tools.mirror.get_config_value",
                      lambda x, y, z: source_root)
            m.setattr("opengrok_tools.utils.mirror.get_repos_for_project",
                      mock_get_repos)
            m.setattr("opengrok_tools.utils.mirror.do_api_call", mock_get)

            assert opengrok_tools.mirror.main() == CONTINUE_EXITVAL


def test_empty_project_config():
    assert check_project_configuration({'foo': None})


def test_disabled_command_api():
    """
    Test that mirror_project() calls call_rest_api() if API
    call is specified in the configuration for disabled project.
    """
    with patch(opengrok_tools.utils.mirror.call_rest_api,
               lambda a, b, c: mock(spec=requests.Response)):
        project_name = "foo"
        config = {
            DISABLED_CMD_PROPERTY: {
                COMMAND_PROPERTY: [
                    "http://localhost:8080/source/api/v1/foo",
                    "POST", "data"]
            },
            PROJECTS_PROPERTY: {
                project_name: {DISABLED_PROPERTY: True}
            }
        }

        assert mirror_project(config, project_name, False,
                              None, None) == CONTINUE_EXITVAL
        verify(opengrok_tools.utils.mirror). \
            call_rest_api(config.get(DISABLED_CMD_PROPERTY),
                          PROJECT_SUBST, project_name)


def test_disabled_command_api_text_append(monkeypatch):
    """
    Test that message text is appended if DISABLED_PROPERTY is a string.
    """

    text_to_append = "foo bar"

    def mock_call_rest_api(command, b, c):
        disabled_command = config.get(DISABLED_CMD_PROPERTY)
        assert disabled_command
        command_args = disabled_command.get(COMMAND_PROPERTY)
        assert command_args
        data = command_args[2]
        assert data
        text = data.get("text")
        assert text
        assert text.find(text_to_append)

        return mock(spec=requests.Response)

    with monkeypatch.context() as m:
        m.setattr("opengrok_tools.utils.mirror.call_rest_api",
                  mock_call_rest_api)

        project_name = "foo"
        data = {'messageLevel': 'info', 'duration': 'PT5M',
                'tags': ['%PROJECT%'],
                'text': 'disabled project'}
        config = {
            DISABLED_CMD_PROPERTY: {
                COMMAND_PROPERTY: [
                    "http://localhost:8080/source/api/v1/foo",
                    "POST", data]
            },
            PROJECTS_PROPERTY: {
                project_name: {
                    DISABLED_REASON_PROPERTY: text_to_append,
                    DISABLED_PROPERTY: True
                }
            }
        }

        mirror_project(config, project_name, False, None, None)


def test_disabled_command_run():
    """
    Make sure that mirror_project() results in calling run_command().
    """
    spy2(opengrok_tools.utils.mirror.run_command)
    project_name = "foo"
    config = {
        DISABLED_CMD_PROPERTY: {
            COMMAND_PROPERTY: ["cat"]
        },
        PROJECTS_PROPERTY: {
            project_name: {DISABLED_PROPERTY: True}
        }
    }

    assert mirror_project(config, project_name, False,
                          None, None) == CONTINUE_EXITVAL
    verify(opengrok_tools.utils.mirror).run_command(ANY, project_name)


def test_disabled_command_run_args():
    """
    Make sure that run_command() calls Command.execute().
    """
    cmd = mock(spec=Command)
    project_name = "foo"
    run_command(cmd, project_name)
    verify(cmd).execute()


def test_mirror_project_timeout(monkeypatch):
    """
    Test mirror_project() timeout inheritance/override from global
    configuration to get_repos_for_project(). The test merely verifies
    that the timeout values are passed between the expected functions,
    not whether it actually affects the execution.
    """
    cmd_timeout = 3
    hook_timeout = 4

    def mock_get_repos(*args, **kwargs):
        mock_get_repos.called = True

        assert kwargs[CMD_TIMEOUT_PROPERTY] == cmd_timeout

        # Technically this function should return list of Repository objects
        # however for this test this is not necessary.
        return ['foo']

    def mock_process_hook(hook_ident, hook, source_root, project_name_arg,
                          proxy, hook_timeout_arg):
        assert hook_timeout_arg == hook_timeout

        # We want to terminate mirror_project() once this function runs.
        # This way mirror_project() will return FAILURE_EXITVAL
        return False

    def test_mirror_project(config):
        retval = mirror_project(config, project_name, False,
                                "http://localhost:8080/source", "srcroot")
        assert retval == FAILURE_EXITVAL

        # TODO: is there better way to ensure that get_repos_for_project()
        #       was actually called ?
        assert mock_get_repos.called

    with monkeypatch.context() as m:
        mock_get_repos.called = False
        m.setattr("opengrok_tools.utils.mirror.get_repos_for_project",
                  mock_get_repos)
        m.setattr("opengrok_tools.utils.mirror.process_hook",
                  mock_process_hook)

        project_name = "foo"
        # override testing
        global_config_1 = {
            PROJECTS_PROPERTY: {
                project_name: {
                    CMD_TIMEOUT_PROPERTY: cmd_timeout,
                    HOOK_TIMEOUT_PROPERTY: hook_timeout
                }
            },
            CMD_TIMEOUT_PROPERTY: cmd_timeout * 2,
            HOOK_TIMEOUT_PROPERTY: hook_timeout * 2
        }
        # inheritance testing
        global_config_2 = {
            CMD_TIMEOUT_PROPERTY: cmd_timeout,
            HOOK_TIMEOUT_PROPERTY: hook_timeout
        }

        test_mirror_project(global_config_1)
        test_mirror_project(global_config_2)


def test_get_repos_for_project(monkeypatch):
    """
    Test argument passing between get_repos_for_project() and get_repository()
    """
    project_name = 'foo'
    proxy_dict = {"http_proxy": "http://foo.bar:80",
                  "https_proxy": "http://foo.bar:80"}
    git_cmd_path = "/foo/git"
    commands = {"git": git_cmd_path}
    timeout = 314159
    test_repo = "/" + project_name

    def mock_get_repos(*args):
        return [test_repo]

    def mock_get_repo_type(*args):
        return "Git"

    with tempfile.TemporaryDirectory() as source_root:
        # Note that it is actually not necessary to create real
        # Git repository for the test to work. This is due to
        # the way how Repository objects are created.
        with monkeypatch.context() as m:
            m.setattr("opengrok_tools.utils.mirror.get_repos",
                      mock_get_repos)
            m.setattr("opengrok_tools.utils.mirror.get_repo_type",
                      mock_get_repo_type)

            repos = get_repos_for_project(project_name, None, source_root,
                                          commands=commands,
                                          proxy=proxy_dict,
                                          command_timeout=timeout)
            assert len(repos) == 1
            assert isinstance(repos[0], GitRepository)
            git_repo = repos[0]
            assert git_repo.timeout == timeout
            assert git_repo.command == git_cmd_path
            assert git_repo.env.items() >= proxy_dict.items()

            # Now ignore the repository
            repos = get_repos_for_project(project_name, None, source_root,
                                          ignored_repos=['.'])
            assert len(repos) == 0


DEFAULT_COMMAND = 'default-command'


@pytest.mark.parametrize(['expected_command', 'config'], [
    (DEFAULT_COMMAND, None),
    ('/usr/bin/git', '/usr/bin/git'),
    (DEFAULT_COMMAND, {}),
    (DEFAULT_COMMAND, {'incoming': '/bin/false'}),
    (DEFAULT_COMMAND, []),
    ('/usr/local/bin/git', {'command': '/usr/local/bin/git'}),
    (
            '/usr/local/bin/git',
            {'command': '/usr/local/bin/git', 'incoming': '/bin/false'}
    )
])
def test_mirroring_custom_repository_command(expected_command, config):
    assert expected_command == Repository._repository_command(config, lambda: DEFAULT_COMMAND)


@pytest.mark.parametrize(
    ('touch_binary'), [
        pytest.param('/bin/touch',
                     marks=pytest.mark.skipif(
                         not os.path.exists('/bin/touch'),
                         reason="requires /bin binaries")),
        pytest.param('/usr/bin/touch',
                     marks=pytest.mark.skipif(
                         not os.path.exists('/usr/bin/touch'),
                         reason="requires /usr/bin binaries")),
    ]
)
def test_mirroring_custom_incoming_invoke_command(touch_binary):
    checking_file = 'incoming.txt'
    with tempfile.TemporaryDirectory() as repository_root:
        repository = GitRepository(mock(), repository_root, 'test-1', {
            'incoming': [touch_binary, checking_file]
        }, None, None, None)
        assert repository.incoming() is False
        assert checking_file in os.listdir(repository_root)


@pytest.mark.parametrize(
    ('echo_binary'), [
        pytest.param('/bin/echo',
                     marks=pytest.mark.skipif(
                         not os.path.exists('/bin/echo'),
                         reason="requires /bin binaries")),
        pytest.param('/usr/bin/echo',
                     marks=pytest.mark.skipif(
                         not os.path.exists('/usr/bin/echo'),
                         reason="requires /usr/bin binaries")),
    ]
)
def test_mirroring_custom_incoming_changes(echo_binary):
    with tempfile.TemporaryDirectory() as repository_root:
        repository = GitRepository(mock(), repository_root, 'test-1', {
            'incoming': [echo_binary, 'new incoming changes!']
        }, None, None, None)
        assert repository.incoming() is True


@pytest.mark.parametrize(
    ('true_binary'), [
        pytest.param('/bin/true',
                     marks=pytest.mark.skipif(
                         not os.path.exists('/bin/true'),
                         reason="requires /bin binaries")),
        pytest.param('/usr/bin/true',
                     marks=pytest.mark.skipif(
                         not os.path.exists('/usr/bin/true'),
                         reason="requires /usr/bin binaries")),
    ]
)
def test_mirroring_custom_incoming_no_changes(true_binary):
    with tempfile.TemporaryDirectory() as repository_root:
        repository = GitRepository(mock(), repository_root, 'test-1', {
            'incoming': true_binary
        }, None, None, None)
        assert repository.incoming() is False


@pytest.mark.parametrize(
    ('false_binary'), [
        pytest.param('/bin/false',
                     marks=pytest.mark.skipif(
                         not os.path.exists('/bin/false'),
                         reason="requires /bin binaries")),
        pytest.param('/usr/bin/false',
                     marks=pytest.mark.skipif(
                         not os.path.exists('/usr/bin/false'),
                         reason="requires /usr/bin binaries")),
    ]
)
def test_mirroring_custom_incoming_error(false_binary):
    with pytest.raises(RepositoryException):
        with tempfile.TemporaryDirectory() as repository_root:
            repository = GitRepository(mock(), repository_root, 'test-1', {
                'incoming': false_binary
            }, None, None, None)
            repository.incoming()


def test_mirroring_incoming_invoke_original_command():
    with tempfile.TemporaryDirectory() as repository_root:
        repository = GitRepository(mock(), repository_root, 'test-1',
                                   None, None, None, None)
        with when(repository).incoming_check().thenReturn(0):
            repository.incoming()
            verify(repository).incoming_check()


@pytest.mark.parametrize(
    ('touch_binary'), [
        pytest.param('/bin/touch',
                     marks=pytest.mark.skipif(
                         not os.path.exists('/bin/touch'),
                         reason="requires /bin binaries")),
        pytest.param('/usr/bin/touch',
                     marks=pytest.mark.skipif(
                         not os.path.exists('/usr/bin/touch'),
                         reason="requires /usr/bin binaries")),
    ]
)
def test_mirroring_custom_sync_invoke_command(touch_binary):
    checking_file = 'sync.txt'
    with tempfile.TemporaryDirectory() as repository_root:
        repository = GitRepository(mock(), repository_root, 'test-1', {
            'sync': [touch_binary, checking_file]
        }, None, None, None)
        assert repository.sync() == 0
        assert checking_file in os.listdir(repository_root)


@pytest.mark.parametrize(
    ('true_binary'), [
        pytest.param('/bin/true',
                     marks=pytest.mark.skipif(
                         not os.path.exists('/bin/true'),
                         reason="requires /bin binaries")),
        pytest.param('/usr/bin/true',
                     marks=pytest.mark.skipif(
                         not os.path.exists('/usr/bin/true'),
                         reason="requires /usr/bin binaries")),
    ]
)
def test_mirroring_custom_sync_success(true_binary):
    with tempfile.TemporaryDirectory() as repository_root:
        repository = GitRepository(mock(), repository_root, 'test-1', {
            'sync': true_binary
        }, None, None, None)
        assert 0 == repository.sync()


@pytest.mark.parametrize(
    ('false_binary'), [
        pytest.param('/bin/false',
                     marks=pytest.mark.skipif(
                         not os.path.exists('/bin/false'),
                         reason="requires /bin binaries")),
        pytest.param('/usr/bin/false',
                     marks=pytest.mark.skipif(
                         not os.path.exists('/usr/bin/false'),
                         reason="requires /usr/bin binaries")),
    ]
)
def test_mirroring_custom_sync_error(false_binary):
    with tempfile.TemporaryDirectory() as repository_root:
        repository = GitRepository(mock(), repository_root, 'test-1', {
            'sync': false_binary
        }, None, None, None)
        assert 1 == repository.sync()


def test_mirroring_sync_invoke_original_command():
    with tempfile.TemporaryDirectory() as repository_root:
        repository = GitRepository(mock(), repository_root, 'test-1',
                                   None, None, None, None)
        with when(repository).reposync().thenReturn(0):
            repository.sync()
            verify(repository).reposync()
