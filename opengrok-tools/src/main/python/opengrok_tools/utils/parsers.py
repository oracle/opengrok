import argparse

from .log import add_log_level_argument
from ..version import __version__ as version


def get_baseparser():
    parser = argparse.ArgumentParser(add_help=False)
    add_log_level_argument(parser)
    parser.add_argument('-v', '--version', action='version', version=version,
                        help='Version of the tool')
    return parser


def get_javaparser():
    parser = argparse.ArgumentParser(add_help=False,
                                     parents=[get_baseparser()])
    parser.add_argument('-j', '--java',
                        help='path to java binary')
    parser.add_argument('-J', '--java_opts',
                        help='java options', action='append')
    parser.add_argument('-e', '--environment', action='append',
                        help='Environment variables in the form of name=value')

    group = parser.add_mutually_exclusive_group(required=True)
    group.add_argument('-a', '--jar',
                       help='Path to jar archive to run')
    group.add_argument('-c', '--classpath',
                       help='Class path')

    parser.add_argument('options', nargs='+', help='options')

    return parser
