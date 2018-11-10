import os
import unittest

from setuptools import setup

SCRIPT_DIR = os.path.dirname(os.path.realpath(__file__))


def extract_version_from_path(*file_paths):
    """
    Look for a version in a file
    see https://packaging.python.org/guides/single-sourcing-package-version/
    :param file_paths: path to the file
    :return: the version
    :raises: an RuntimeError when version is not available
    """

    def read(*parts):
        with open(os.path.join(SCRIPT_DIR, *parts), 'r') as fp:
            version = {}
            exec(fp.read(), version, None)
            return version['__version__']

    version = read(*file_paths)

    if version:
        return version
    raise RuntimeError("Unable to find version string.")


def readme():
    with open(os.path.join(SCRIPT_DIR, 'README.md'), 'r') as readme:
        return readme.read()


def my_test_suite():
    test_loader = unittest.TestLoader()
    test_suite = test_loader.discover(
        os.path.join(SCRIPT_DIR, 'src', 'test', 'python'), pattern='test_*.py')
    return test_suite


setup(
    name='opengrok-tools',
    version=extract_version_from_path('src', 'main',
                                      'python', 'opengrok_tools',
                                      'version.py'),
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
        'resource',
        'filelock'
    ],
    tests_require=[
        'parameterized'
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
