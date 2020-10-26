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
# Portions Copyright (c) 2020, Krystof Tulinger <k.tulinger@seznam.cz>
#
import os

import pytest


def system_binary(name):
    """
    Creates an argument of {name}_binary which automatically
    skips the test execution when the binary is not available on the system.

    :param name: the binary name (the command)
    """

    def decorator(fn):
        return pytest.mark.parametrize(
            ('{}_binary'.format(name)), [
                pytest.param('/bin/{}'.format(name), marks=pytest.mark.skipif(not os.path.exists('/bin/{}'.format(name)), reason="requires /bin binaries")),
                pytest.param('/usr/bin/{}'.format(name), marks=pytest.mark.skipif(not os.path.exists('/usr/bin/{}'.format(name)), reason="requires /usr/bin binaries")),
            ])(fn)

    return decorator


def posix_only(fn):
    """
    Automatically skips the test execution when the current system is not in posix family.
    """
    return pytest.mark.skipif(not os.name.startswith("posix"), reason="requires posix")(fn)
