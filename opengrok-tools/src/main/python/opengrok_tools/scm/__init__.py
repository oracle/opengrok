from .cvs import CVSRepository
from .git import GitRepository
from .mercurial import MercurialRepository
from .repository import Repository
from .svn import SubversionRepository
from .teamware import TeamwareRepository
from .repo import RepoRepository

__all__ = [
    'CVSRepository',
    'GitRepository',
    'MercurialRepository',
    'SubversionRepository',
    'TeamwareRepository',
    'Repository',
    'RepoRepository',
]
