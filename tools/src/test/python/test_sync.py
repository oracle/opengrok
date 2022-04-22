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
# Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
#

import logging

import pytest
from multiprocessing import pool

from mockito import verify, ANY, patch

from opengrok_tools.sync import do_sync
from opengrok_tools.utils.exitvals import SUCCESS_EXITVAL, FAILURE_EXITVAL


@pytest.mark.parametrize(['check_config', 'expected_times'], [(True, 0), (False, 1)])
def test_dosync_check_config_empty(check_config, expected_times):
    commands = []
    with patch(pool.Pool.map, lambda x, y, z: []):
        assert do_sync(logging.INFO, commands, None, ["foo", "bar"], [],
                       "http://localhost:8080/source", 42, check_config=check_config) == SUCCESS_EXITVAL
        verify(pool.Pool, times=expected_times).map(ANY, ANY, ANY)
