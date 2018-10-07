from .cvs import CVSRepository
from .git import GitRepository
from .mercurial import MercurialRepository
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
]
