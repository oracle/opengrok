import os


def is_exe(fpath):
    return os.path.isfile(fpath) and os.access(fpath, os.X_OK)


def which(program):
    fpath, fname = os.path.split(program)
    if fpath:
        if is_exe(program):
            return program
    else:
        for path in os.environ["PATH"].split(os.pathsep):
            exe_file = os.path.join(path, program)
            if is_exe(exe_file):
                return exe_file

    return None


def check_create_dir(path):
    """
    Make sure the directory specified by the path exists.
    """
    if not os.path.isdir(path):
        try:
            os.mkdir(path)
        except OSError:
            logger.error("cannot create {} directory".format(path))
            sys.exit(1)


def get_dict_val(dictionary, key):
    """
    Get value of key in dictionary or None.
    """

    try:
        return dictionary[key]
    except KeyError:
        return None
