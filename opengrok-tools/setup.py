import os
import unittest
from setuptools import setup

SCRIPT_DIR = os.path.dirname(os.path.realpath(__file__))


def readme():
    with open(os.path.join(SCRIPT_DIR, 'src', 'main', 'python', 'README.md'),
              'r') as readme:
        return readme.read()


def my_test_suite():
    test_loader = unittest.TestLoader()
    test_suite = test_loader.discover(
        os.path.join(SCRIPT_DIR, 'src', 'test', 'python'), pattern='test_*.py')
    return test_suite


setup(
    name='opengrok-tools',
    version='1.1rc60',
    packages=[
        'opengrok_tools',
        'all',
        'all.utils',
        'all.scm',
    ],
    package_dir={
        'opengrok_tools': 'src/main/python/opengrok_tools',
        'all': 'src/main/python/opengrok_tools/all',
        'all.scm': 'src/main/python/opengrok_tools/all/scm',
        'all.utils': 'src/main/python/opengrok_tools/all/utils',
    },
    url='https://github.com/OpenGrok/OpenGrok',
    license='CDDL',
    author='Oracle',
    author_email='opengrok-dev@yahoogroups.com',
    description='Tools for managing OpenGrok instance',
    long_description=readme(),
    test_suite='setup.my_test_suite',
    install_requires=[
        'jsonschema==2.6.0',
        'pyyaml',
        'requests',
        'resource'
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
