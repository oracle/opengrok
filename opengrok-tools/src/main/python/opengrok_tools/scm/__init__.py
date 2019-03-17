from .cvs import CVSRepository
from .git import GitRepository
from .mercurial import MercurialRepository
from .repo import RepoRepository
from .repofactory import get_repository
from .repository import Repository
from .svn import SubversionRepository
from .teamware import TeamwareRepository

__all__ = [
    'CVSRepository',
    'GitRepository',
    'MercurialRepository',
    'SubversionRepository',
    'TeamwareRepository',
    'Repository',
    'RepoRepository',
    'get_repository',
]
