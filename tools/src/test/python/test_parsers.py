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
#

import argparse
import pytest
import os
from opengrok_tools.utils.parsers import str2bool, get_headers


def test_str2bool_exception():
    with pytest.raises(argparse.ArgumentTypeError):
        str2bool('foo')


def test_str2bool_exception_empty_str():
    with pytest.raises(argparse.ArgumentTypeError):
        str2bool('')


def test_str2bool_bool():
    assert str2bool(True)
    assert not str2bool(False)


def test_str2bool():
    for val in ['true', 'y', 'yes']:
        assert str2bool(val)
        assert str2bool(val.upper())

    for val in ['false', 'n', 'no']:
        assert not str2bool(val)
        assert not str2bool(val.upper())


def test_get_headers():
    assert get_headers(None) == {}
    assert get_headers([]) == {}

    assert get_headers(['foo: bar', 'mumble: bumble']) == {'foo': 'bar', 'mumble': 'bumble'}


def test_get_headers_file(tmp_path):
    assert get_headers(None) == {}
    assert get_headers([]) == {}

    headers_file = {'Moomintroll': 'Snorkmaiden', 'Mymble': 'Little My'}
    file_path = tmp_path / "headers"
    headers_text = os.linesep.join(key + ': ' + str(val) for key, val in headers_file.items())
    file_path.write_text(headers_text)

    headers_expected = {'mumble': 'bumble'}
    headers_expected.update(headers_file)
    assert get_headers(['@' + str(file_path), 'mumble: bumble']) == headers_expected
