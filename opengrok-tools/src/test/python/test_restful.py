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
#

import pytest

from requests.exceptions import HTTPError

from opengrok_tools.utils.restful import call_rest_api,\
    CONTENT_TYPE, APPLICATION_JSON
from opengrok_tools.utils.patterns import COMMAND_PROPERTY


def test_replacement(monkeypatch):
    """
    Test replacement performed both in the URL and data.
    """
    okay_status = 200

    class MockResponse:
        def p(self):
            pass

        def __init__(self):
            self.status_code = okay_status
            self.raise_for_status = self.p

    def mock_response(verb, uri, headers, data):
        # Spying on mocked function is maybe too much so verify
        # the arguments here.
        assert uri == "http://localhost:8080/source/api/v1/BAR"
        assert data == '"fooBARbar"'

        return MockResponse()

    for verb in ["PUT", "POST", "DELETE"]:
        command = {"command": ["http://localhost:8080/source/api/v1/%FOO%",
                               verb, "foo%FOO%bar"]}
        pattern = "%FOO%"
        value = "BAR"
        with monkeypatch.context() as m:
            m.setattr("opengrok_tools.utils.restful.do_api_call",
                      mock_response)
            assert call_rest_api(command, pattern, value). \
                status_code == okay_status


def test_unknown_verb():
    command = {"command": ["http://localhost:8080/source/api/v1/foo",
                           "FOOBAR", "data"]}
    pattern = "%FOO%"
    value = "BAR"
    with pytest.raises(Exception):
        call_rest_api(command, pattern, value)


def test_headers(monkeypatch):
    """
    Test HTTP header handling.
    """
    for verb in ["PUT", "POST", "DELETE"]:
        text_plain_header = {CONTENT_TYPE: 'text/plain'}
        for header_arg in [text_plain_header, None]:
            command = {"command": ["http://localhost:8080/source/api/v1/foo",
                                   verb, "data", header_arg]}

            def mock_response(uri, verb, headers, data):
                if header_arg:
                    assert text_plain_header.items() <= headers.items()
                else:
                    assert {CONTENT_TYPE: APPLICATION_JSON}.items() \
                        <= headers.items()

            with monkeypatch.context() as m:
                m.setattr("opengrok_tools.utils.restful.do_api_call",
                          mock_response)
                call_rest_api(command, None, None)


def test_restful_fail(monkeypatch):
    class MockResponse:
        def p(self):
            raise HTTPError("foo")

        def __init__(self):
            self.status_code = 400
            self.raise_for_status = self.p

    def mock_response(uri, headers, data, params, proxies):
        return MockResponse()

    with monkeypatch.context() as m:
        m.setattr("requests.put", mock_response)
        with pytest.raises(HTTPError):
            call_rest_api({'command': ['http://foo', 'PUT', 'data']},
                          None, None)


def test_invalid_command_negative():
    with pytest.raises(Exception):
        call_rest_api(None, None, None)

    with pytest.raises(Exception):
        call_rest_api({"foo": "bar"}, None, None)

    with pytest.raises(Exception):
        call_rest_api(["foo", "bar"], None, None)

    with pytest.raises(Exception):
        call_rest_api({COMMAND_PROPERTY: ["foo", "PUT", "data", "headers"]},
                      None, None)
