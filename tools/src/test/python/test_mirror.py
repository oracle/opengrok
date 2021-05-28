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
# Copyright (c) 2019, 2021, Oracle and/or its affiliates. All rights reserved.
# Portions Copyright (c) 2020, Krystof Tulinger <k.tulinger@seznam.cz>
#

import os
import stat
import tempfile

import pytest
import requests
from mockito import verify, patch, spy2, mock, ANY, when, unstub

import opengrok_tools.mirror
from conftest import posix_only, system_binary
from opengrok_tools.scm import Repository
from opengrok_tools.scm.git import GitRepository
from opengrok_tools.scm.repository import RepositoryException
from opengrok_tools.utils.command import Command
from opengrok_tools.utils.exitvals import (
    CONTINUE_EXITVAL, FAILURE_EXITVAL, SUCCESS_EXITVAL
)
from opengrok_tools.utils.mirror import check_project_configuration, \
    check_configuration, mirror_project, run_command, get_repos_for_project, \
    HOOKS_PROPERTY, PROXY_PROPERTY, IGNORED_REPOS_PROPERTY, \
    PROJECTS_PROPERTY, DISABLED_CMD_PROPERTY, DISABLED_PROPERTY, \
    CMD_TIMEOUT_PROPERTY, HOOK_TIMEOUT_PROPERTY, DISABLED_REASON_PROPERTY, \
    INCOMING_PROPERTY, IGNORE_ERR_PROPERTY, HOOK_PRE_PROPERTY, \
    HOOKDIR_PROPERTY, HOOK_POST_PROPERTY
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


@posix_only
def test_invalid_project_config_nonexec_hook():
    with tempfile.TemporaryDirectory() as tmpdir:
        with open(os.path.join(tmpdir, "foo.sh"), 'w+') as tmpfile:
            tmpfile.write("foo\n")
            config = {"foo": {HOOKS_PROPERTY: {"pre": "foo.sh"}}}
            assert not check_project_configuration(config, hookdir=tmpdir)


@posix_only
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


def test_valid_config_proxy():
    assert check_configuration({
        PROJECTS_PROPERTY: {"foo": {PROXY_PROPERTY: True}},
        PROXY_PROPERTY: "proxy"
    })


def test_valid_config_incoming():
    assert check_configuration({
        PROJECTS_PROPERTY: {"foo": {INCOMING_PROPERTY: True}},
        INCOMING_PROPERTY: "False"
    })


def test_project_config_incoming_valid():
    assert check_project_configuration({'foo': {INCOMING_PROPERTY: "T"}})
    assert check_project_configuration({'foo': {INCOMING_PROPERTY: "F"}})


def test_empty_project_config():
    assert check_project_configuration({'foo': None})


def test_disabled_command_api():
    """
    Test that mirror_project() calls call_rest_api() if API
    call is specified in the configuration for disabled project.
    """
    def mock_call_rest_api(command, b, http_headers=None, timeout=None):
        return mock(spec=requests.Response)

    with patch(opengrok_tools.utils.mirror.call_rest_api,
               mock_call_rest_api):
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
                          {PROJECT_SUBST: project_name},
                          http_headers=None, timeout=None)


def test_disabled_command_api_text_append(monkeypatch):
    """
    Test that message text is appended if DISABLED_PROPERTY is a string.
    """

    text_to_append = "foo bar"

    def mock_call_rest_api(command, b, http_headers=None, timeout=None):
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


@pytest.mark.parametrize("hook_type", [HOOK_PRE_PROPERTY, HOOK_POST_PROPERTY])
@pytest.mark.parametrize("per_project", [True, False])
def test_ignore_errors(monkeypatch, hook_type, per_project):
    """
    Test that per project ignore errors property overrides failed hook.
    """

    def mock_get_repos(*args, **kwargs):
        return [mock(spec=GitRepository)]

    spy2(opengrok_tools.utils.mirror.process_hook)
    project_name = "foo"
    hook_dir = "/befelemepeseveze"
    hook_name = "nonexistent"
    if per_project:
        config = {
            HOOKDIR_PROPERTY: hook_dir,
            PROJECTS_PROPERTY: {
                project_name: {IGNORE_ERR_PROPERTY: True,
                               HOOKS_PROPERTY: {hook_type: hook_name}}
            }
        }
    else:
        config = {
            IGNORE_ERR_PROPERTY: True,
            HOOKDIR_PROPERTY: hook_dir,
            PROJECTS_PROPERTY: {
                project_name: {HOOKS_PROPERTY: {hook_type: hook_name}}
            }
        }

    with monkeypatch.context() as m:
        mock_get_repos.called = False
        m.setattr("opengrok_tools.utils.mirror.get_repos_for_project",
                  mock_get_repos)

        src_root = "srcroot"
        assert mirror_project(config, project_name, False,
                              None, src_root) == SUCCESS_EXITVAL
        verify(opengrok_tools.utils.mirror).\
            process_hook(hook_type, os.path.join(hook_dir, hook_name),
                         src_root, project_name, None, None)
        # Necessary to disable the process_hook spy otherwise mockito will
        # complain about recursive invocation.
        unstub()


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

    def mock_get_repos(*args, headers=None, timeout=None):
        return [test_repo]

    def mock_get_repo_type(*args, headers=None, timeout=None):
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


@system_binary('touch')
def test_mirroring_custom_incoming_invoke_command(touch_binary):
    checking_file = 'incoming.txt'
    with tempfile.TemporaryDirectory() as repository_root:
        repository = GitRepository(mock(), repository_root, 'test-1', {
            'incoming': [touch_binary, checking_file]
        }, None, None, None)
        assert repository.incoming() is False
        assert checking_file in os.listdir(repository_root)


@system_binary('echo')
def test_mirroring_custom_incoming_changes(echo_binary):
    with tempfile.TemporaryDirectory() as repository_root:
        repository = GitRepository(mock(), repository_root, 'test-1', {
            'incoming': [echo_binary, 'new incoming changes!']
        }, None, None, None)
        assert repository.incoming() is True


@system_binary('true')
def test_mirroring_custom_incoming_no_changes(true_binary):
    with tempfile.TemporaryDirectory() as repository_root:
        repository = GitRepository(mock(), repository_root, 'test-1', {
            'incoming': true_binary
        }, None, None, None)
        assert repository.incoming() is False


@system_binary('false')
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


@system_binary('touch')
def test_mirroring_custom_sync_invoke_command(touch_binary):
    checking_file = 'sync.txt'
    with tempfile.TemporaryDirectory() as repository_root:
        repository = GitRepository(mock(), repository_root, 'test-1', {
            'sync': [touch_binary, checking_file]
        }, None, None, None)
        assert repository.sync() == 0
        assert checking_file in os.listdir(repository_root)


@system_binary('true')
def test_mirroring_custom_sync_success(true_binary):
    with tempfile.TemporaryDirectory() as repository_root:
        repository = GitRepository(mock(), repository_root, 'test-1', {
            'sync': true_binary
        }, None, None, None)
        assert 0 == repository.sync()


@system_binary('false')
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
