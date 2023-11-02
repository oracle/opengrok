# The package version is taken from maven.
# this "variable" is replaced by maven on the fly so don't change it here
# see pom.xml for this module


def get_version(version):
    """
    Detect the mvn build versus the local python install run.
    :param version: the new version string to be applied
    :return: the mvn version, or local version number
    """
    if 'project.python.package.version' in version:
        return '0.0.1'
    return version


__version__ = get_version('${project.python.package.version}')
