import os
import unittest

from setuptools import setup

SCRIPT_DIR = os.path.dirname(os.path.realpath(__file__))


def readme():
    with open(os.path.join(SCRIPT_DIR, 'README.md'), 'r') as readme:
        return readme.read()


def my_test_suite():
    test_loader = unittest.TestLoader()
    test_suite = test_loader.discover(
        os.path.join(SCRIPT_DIR, 'src', 'test', 'python'), pattern='test_*.py')
    return test_suite


def get_version(version):
    """
    Detect the mvn build versus the local python setup.py install run.
    :param version: the new version string to be applied
    :return: the mvn version, or local version number
    """
    if 'project.python.package.version' in version:
        return '0.0.1'
    return version


setup(
    name='opengrok-tools',
    # The package version is taken from maven.
    # this "variable" is replaced by maven on the fly so don't change it here
    # see pom.xml for this module
    version=get_version('${project.python.package.version}'),
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
        'requests>=2.20.0',
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
