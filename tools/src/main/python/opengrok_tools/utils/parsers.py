import argparse

from .log import add_log_level_argument
from ..version import __version__ as tools_version


def str2bool(v):
    if isinstance(v, bool):
        return v

    if isinstance(v, str):
        v_lower = v.lower()
        if v_lower in ('yes', 'true', 'y', '1'):
            return True
        elif v_lower in ('no', 'false', 'n', '0'):
            return False

    raise argparse.ArgumentTypeError('Boolean value or its string '
                                     'representation expected.')


def get_base_parser(tool_version=None):
    """
    Get the base parser which supports --version option reporting
    the overall version of the tools and the specific version of the
    invoked tool.
    :param tool_version: the specific version tool if applicable
    :return: the parser
    """
    parser = argparse.ArgumentParser(add_help=False)
    add_log_level_argument(parser)
    version = tools_version
    if tool_version:
        version += ' (v{})'.format(tool_version)
    parser.add_argument('-v', '--version', action='version', version=version,
                        help='Version of the tool')
    return parser


def get_java_parser():
    parser = argparse.ArgumentParser(add_help=False,
                                     parents=[get_base_parser()])
    parser.add_argument('-j', '--java',
                        help='path to java binary')
    parser.add_argument('-J', '--java_opts',
                        help='java options. Use one for every java option, '
                             'e.g. -J=-server -J=-Xmx16g',
                        action='append')
    parser.add_argument('-e', '--environment', action='append',
                        help='Environment variables in the form of name=value')
    parser.add_argument('--doprint', type=str2bool, nargs=1, default=None,
                        metavar='boolean',
                        help='Enable/disable printing of messages '
                             'from the application as they are produced.')

    group = parser.add_mutually_exclusive_group(required=True)
    group.add_argument('-a', '--jar',
                       help='Path to jar archive to run')
    group.add_argument('-c', '--classpath',
                       help='Class path')

    parser.add_argument('options', nargs='+', help='options')

    return parser
