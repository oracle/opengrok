import os

import pytest


def system_binary(name):
    def decorator(fn):
        return pytest.mark.parametrize(
            ('{}_binary'.format(name)), [
                pytest.param('/bin/{}'.format(name), marks=pytest.mark.skipif(not os.path.exists('/bin/{}'.format(name)), reason="requires /bin binaries")),
                pytest.param('/usr/bin/{}'.format(name), marks=pytest.mark.skipif(not os.path.exists('/usr/bin/{}'.format(name)), reason="requires /usr/bin binaries")),
            ])(fn)

    return decorator


def posix_only(fn):
    return pytest.mark.skipif(not os.name.startswith("posix"), reason="requires posix")(fn)
