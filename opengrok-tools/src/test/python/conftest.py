import os
import pytest


@pytest.fixture(
    params=[
        pytest.param('/bin/true', marks=pytest.mark.skipif(not os.path.exists('/bin/true'), reason="requires /bin binaries")),
        pytest.param('/usr/bin/true', marks=pytest.mark.skipif(not os.path.exists('/usr/bin/true'), reason="requires /usr/bin binaries"))
    ]
)
def true_binary(request):
    return request.param


@pytest.fixture(
    params=[
        pytest.param('/bin/false', marks=pytest.mark.skipif(not os.path.exists('/bin/false'), reason="requires /bin binaries")),
        pytest.param('/usr/bin/false', marks=pytest.mark.skipif(not os.path.exists('/usr/bin/false'), reason="requires /usr/bin binaries"))
    ]
)
def false_binary(request):
    return request.param


@pytest.fixture(
    params=[
        pytest.param('/bin/touch', marks=pytest.mark.skipif(not os.path.exists('/bin/touch'), reason="requires /bin binaries")),
        pytest.param('/usr/bin/touch', marks=pytest.mark.skipif(not os.path.exists('/usr/bin/touch'), reason="requires /usr/bin binaries"))
    ]
)
def touch_binary(request):
    return request.param


@pytest.fixture(
    params=[
        pytest.param('/bin/echo', marks=pytest.mark.skipif(not os.path.exists('/bin/echo'), reason="requires /bin binaries")),
        pytest.param('/usr/bin/echo', marks=pytest.mark.skipif(not os.path.exists('/usr/bin/echo'), reason="requires /usr/bin binaries"))
    ]
)
def echo_binary(request):
    return request.param