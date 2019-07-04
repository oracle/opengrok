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
# Copyright (c) 2018, 2020, Oracle and/or its affiliates. All rights reserved.
#
import pytest
import platform

from opengrok_tools.utils.command import Command
from opengrok_tools.version import __version__ as version


@pytest.mark.skipif(platform.system() == 'Windows',
                    reason="broken on Windows")
@pytest.mark.parametrize(
    ('command'),
    (
            ('opengrok'),
            ('opengrok-indexer'),
            ('opengrok-groups'),
            ('opengrok-config-merge'),
            ('opengrok-deploy'),
            ('opengrok-java'),
            ('opengrok-mirror'),
            ('opengrok-projadm'),
            ('opengrok-reindex-project'),
            ('opengrok-sync'),
    )
)
def test_opengrok_version(command):
    """
    Test that installed command has the version option
    :param command: the command name
    :return:
    """
    cmd = Command([command, '--version'])
    cmd.execute()
    assert cmd.getretcode() == 0
    assert cmd.getstate() == Command.FINISHED
    assert version in cmd.getoutputstr()
