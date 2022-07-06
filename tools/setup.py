import os
from setuptools import setup

from src.main.python.opengrok_tools.version import __version__ as version

SCRIPT_DIR = os.path.dirname(os.path.realpath(__file__))


def readme():
    with open(os.path.join(SCRIPT_DIR, 'README-dist.txt'), 'r') as readme:
        return readme.read()


setup(
    name='opengrok-tools',
    version=version,
    packages=[
        'opengrok_tools',
        'opengrok_tools.utils',
        'opengrok_tools.scm',
    ],
    package_dir={
        'opengrok_tools': 'src/main/python/opengrok_tools',
        'opengrok_tools.scm': 'src/main/python/opengrok_tools/scm',
        'opengrok_tools.utils': 'src/main/python/opengrok_tools/utils',
    },
    url='https://github.com/oracle/opengrok',
    license='CDDL',
    author='Oracle',
    description='Tools for managing OpenGrok instance',
    long_description=readme(),
    python_requires='>=3.4, <4',
    install_requires=[
        'jsonschema==2.6.0',
        'pyyaml',
        'requests>=2.20.0',
        'resource',
        'filelock<3.3.0',
        'setuptools>=36.7.2',
    ],
    setup_requires=[
        'pytest-runner',
        'setuptools>=36.7.2',
    ],
    tests_require=[
        'pytest',
        'GitPython',
        'pytest-xdist',
        'mockito>=1.3.3',
        'pytest-mockito',
    ],
    entry_points={
        'console_scripts': [
            'opengrok-config-merge=opengrok_tools.config_merge:main',
            'opengrok-deploy=opengrok_tools.deploy:main',
            'opengrok-groups=opengrok_tools.groups:main',
            'opengrok=opengrok_tools.indexer:main',
            'opengrok-indexer=opengrok_tools.indexer:main',
            'opengrok-java=opengrok_tools.java:main',
            'opengrok-mirror=opengrok_tools.mirror:main',
            'opengrok-projadm=opengrok_tools.projadm:main',
            'opengrok-reindex-project=opengrok_tools.reindex_project:main',
            'opengrok-sync=opengrok_tools.sync:main',
        ]
    },
)
