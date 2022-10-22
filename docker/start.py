#!/usr/bin/env python3

# CDDL HEADER START
#
# The contents of this file are subject to the terms of the
# Common Development and Distribution License (the "License").
# You may not use this file except in compliance with the License.
#
# See LICENSE.txt included in this distribution for the specific
# language governing permissions and limitations under the License.
#
# When distributing Covered Code, include this CDDL HEADER in each
# file and include the License file at LICENSE.txt.
# If applicable, add the following below this CDDL HEADER, with the
# fields enclosed by brackets "[]" replaced with your own identifying
# information: Portions Copyright [yyyy] [name of copyright owner]
#
# CDDL HEADER END

#
# Copyright (c) 2021, 2022, Oracle and/or its affiliates. All rights reserved.
#

import os
import logging
import multiprocessing
import signal
import shutil
import subprocess
import sys
import tempfile
import threading
import time
from pathlib import Path
from requests import get, ConnectionError
from flask import Flask
from flask_httpauth import HTTPTokenAuth
from waitress import serve

from opengrok_tools.utils.log import get_console_logger, \
    get_log_level, get_class_basename
from opengrok_tools.deploy import deploy_war
from opengrok_tools.utils.indexer import Indexer
from opengrok_tools.sync import do_sync
from opengrok_tools.config_merge import merge_config_files
from opengrok_tools.utils.opengrok import list_projects, \
    add_project, delete_project, get_configuration
from opengrok_tools.utils.readconfig import read_config
from opengrok_tools.utils.exitvals import SUCCESS_EXITVAL
from opengrok_tools.utils.mirror import check_configuration
from opengrok_tools.mirror import OPENGROK_NO_MIRROR_ENV
from periodic_timer import PeriodicTimer

fs_root = os.path.abspath('.').split(os.path.sep)[0] + os.path.sep
if os.environ.get('OPENGROK_TOMCAT_ROOT'):  # debug only
    tomcat_root = os.environ.get('OPENGROK_TOMCAT_ROOT')
else:
    tomcat_root = os.path.join(fs_root, "usr", "local", "tomcat")

if os.environ.get('OPENGROK_ROOT'):  # debug only
    OPENGROK_BASE_DIR = os.environ.get('OPENGROK_ROOT')
else:
    OPENGROK_BASE_DIR = os.path.join(fs_root, "opengrok")

OPENGROK_LIB_DIR = os.path.join(OPENGROK_BASE_DIR, "lib")
OPENGROK_DATA_ROOT = os.path.join(OPENGROK_BASE_DIR, "data")
OPENGROK_SRC_ROOT = os.path.join(OPENGROK_BASE_DIR, "src")
BODY_INCLUDE_FILE = os.path.join(OPENGROK_DATA_ROOT, "body_include")
OPENGROK_CONFIG_DIR = os.path.join(OPENGROK_BASE_DIR, "etc")
OPENGROK_CONFIG_FILE = os.path.join(OPENGROK_CONFIG_DIR,
                                    "configuration.xml")
OPENGROK_WEBAPPS_DIR = os.path.join(tomcat_root, "webapps")
OPENGROK_JAR = os.path.join(OPENGROK_LIB_DIR, 'opengrok.jar')

NOMIRROR_ENV_NAME = 'NOMIRROR'

expected_token = None
periodic_timer = None
app = Flask(__name__)
auth = HTTPTokenAuth(scheme='Bearer')
REINDEX_POINT = '/reindex'


@auth.verify_token
def verify_token(token):
    if expected_token is None:
        return "yes"

    if token is not None and token == expected_token:
        return "yes"


@app.route(REINDEX_POINT)
@auth.login_required
def index():
    global periodic_timer

    if periodic_timer:
        logger = logging.getLogger(__name__)
        logger.debug("Triggering reindex based on API call")

        periodic_timer.notify_all()
        return "Reindex triggered\n"

    return "Reindex not triggered - the timer is not initialized yet\n"


def rest_function(logger, rest_port):
    logger.info("Starting REST app on port {}".format(rest_port))
    serve(app, host="0.0.0.0", port=rest_port)


def set_url_root(logger, url_root):
    """
    Set URL root and URI based on input
    :param logger: logger instance
    :param url_root: input
    :return: URI and URL root
    """
    if not url_root:
        url_root = '/'

    if ' ' in url_root:
        logger.warn('Deployment path contains spaces. Deploying to root')
        url_root = '/'

    # Remove leading and trailing slashes
    if url_root.startswith('/'):
        url_root = url_root[1:]
    if url_root.endswith('/'):
        url_root = url_root[:-1]

    uri = "http://localhost:8080/" + url_root
    #
    # Make sure URI ends with slash. This is important for the various API
    # calls, notably for those that check the HTTP error code.
    # Normally accessing the URI without the terminating slash results in
    # HTTP redirect (code 302) instead of success (200).
    #
    if not uri.endswith('/'):
        uri = uri + '/'

    return uri, url_root


def get_war_name(url_root):
    """
    :param url_root: web app URL root
    :return: filename of the WAR file
    """
    if len(url_root) == 0:
        return "ROOT.war"

    return url_root + ".war"


def deploy(logger, url_root):
    """
    Deploy the web application
    :param logger: logger instance
    :param url_root: web app URL root
    """

    logger.info('Deploying web application')
    webapps_dir = os.path.join(tomcat_root, 'webapps')
    if not os.path.isdir(webapps_dir):
        raise Exception("{} is not a directory".format(webapps_dir))

    for item in os.listdir(webapps_dir):
        subdir = os.path.join(webapps_dir, item)
        if os.path.isdir(subdir):
            logger.debug("Removing '{}' directory recursively".format(subdir))
            shutil.rmtree(subdir)

    deploy_war(logger, os.path.join(OPENGROK_LIB_DIR, "source.war"),
               os.path.join(OPENGROK_WEBAPPS_DIR, get_war_name(url_root)),
               OPENGROK_CONFIG_FILE, None)


def setup_redirect_source(logger, url_root):
    """
    Set up redirect from /source
    """
    logger.debug("Setting up redirect from /source to '{}'".format(url_root))
    source_dir = os.path.join(OPENGROK_WEBAPPS_DIR, "source")
    if not os.path.isdir(source_dir):
        os.makedirs(source_dir)

    with open(os.path.join(source_dir, "index.jsp"), "w+") as index:
        index.write("<% response.sendRedirect(\"/{}\"); %>".format(url_root))


def wait_for_tomcat(logger, uri):
    """
    Active/busy waiting for Tomcat to come up.
    Currently, there is no upper time bound.
    """
    logger.info("Waiting for Tomcat to start")

    while True:
        try:
            ret = get(uri)
            status = ret.status_code
        except ConnectionError:
            status = 0

        if status != 200:
            logger.debug("Got status {} for {}, sleeping for 1 second".
                         format(status, uri))
            time.sleep(1)
        else:
            break

    logger.info("Tomcat is ready")


def refresh_projects(logger, uri):
    """
    Ensure each immediate source root subdirectory is a project.
    """
    webapp_projects = list_projects(logger, uri)
    if not webapp_projects:
        return

    logger.debug('Projects from the web app: {}'.format(webapp_projects))
    src_root = OPENGROK_SRC_ROOT

    # Add projects.
    for item in os.listdir(src_root):
        logger.debug('Got item {}'.format(item))
        if os.path.isdir(os.path.join(src_root, item)):
            if item not in webapp_projects:
                logger.info("Adding project {}".format(item))
                add_project(logger, item, uri)

    # Remove projects
    for item in webapp_projects:
        if not os.path.isdir(os.path.join(src_root, item)):
            logger.info("Deleting project {}".format(item))
            delete_project(logger, item, uri)


def save_config(logger, uri, config_path):
    """
    Retrieve configuration from the web app and write it to file.
    :param logger: logger instance
    :param uri: web app URI
    :param config_path: file path
    """

    config = get_configuration(logger, uri)
    if config is None:
        return

    logger.info('Saving configuration to {}'.format(config_path))
    with open(config_path, "w+") as config_file:
        config_file.write(config)


def merge_commands_env(commands, env):
    """
    Merge environment into command structure. If any of the commands has
    an environment already set, the env is merged in.
    :param commands: commands structure
    :param env: environment dictionary
    :return: updated commands structure
    """
    for cmd in commands:
        cmd_env = cmd.get('env')
        if cmd_env:
            cmd.env.update(env)
        else:
            cmd['env'] = env

    return commands


def indexer_no_projects(logger, uri, config_path, extra_indexer_options):
    """
    Project less indexer
    """

    wait_for_tomcat(logger, uri)

    while True:
        indexer_options = ['-s', OPENGROK_SRC_ROOT,
                           '-d', OPENGROK_DATA_ROOT,
                           '-c', '/usr/local/bin/ctags',
                           '--remote', 'on',
                           '-H',
                           '-W', config_path,
                           '-U', uri]
        if extra_indexer_options:
            logger.debug("Adding extra indexer options: {}".
                         format(extra_indexer_options))
            indexer_options.extend(extra_indexer_options.split())
        indexer = Indexer(indexer_options, logger=logger,
                          jar=OPENGROK_JAR, doprint=True)
        indexer.execute()

        logger.info("Waiting for reindex to be triggered")
        global periodic_timer
        periodic_timer.wait_for_event()


def project_syncer(logger, loglevel, uri, config_path, numworkers, env):
    """
    Wrapper for running opengrok-sync.
    To be run in a thread/process in the background.
    """

    wait_for_tomcat(logger, uri)

    while True:
        refresh_projects(logger, uri)

        if os.environ.get('OPENGROK_SYNC_YML'):  # debug only
            config_file = os.environ.get('OPENGROK_SYNC_YML')
        else:
            config_file = os.path.join(fs_root, 'scripts', 'sync.yml')
        config = read_config(logger, config_file)
        if config is None:
            logger.error("Cannot read config file from {}".format(config_file))
            raise Exception("no sync config")

        projects = list_projects(logger, uri)
        if projects:
            #
            # The driveon=True is needed for the initial indexing of newly
            # added project, otherwise the incoming check in the
            # opengrok-mirror program would short circuit it.
            #
            if env:
                logger.info('Merging commands with environment')
                commands = merge_commands_env(config["commands"], env)
                logger.debug(config['commands'])
            else:
                commands = config["commands"]

            logger.info("Sync starting")
            do_sync(loglevel, commands, config.get('cleanup'),
                    projects, config.get("ignore_errors"), uri,
                    numworkers, driveon=True, logger=logger, print_output=True)
            logger.info("Sync done")

            # Workaround for https://github.com/oracle/opengrok/issues/1670
            Path(os.path.join(OPENGROK_DATA_ROOT, 'timestamp')).touch()

            save_config(logger, uri, config_path)

        logger.info("Waiting for reindex to be triggered")
        global periodic_timer
        periodic_timer.wait_for_event()


def create_bare_config(logger, use_projects, extra_indexer_options=None):
    """
    Create bare configuration file with a few basic settings.
    """

    logger.info('Creating bare configuration in {}'.
                format(OPENGROK_CONFIG_FILE))
    indexer_options = ['-s', OPENGROK_SRC_ROOT,
                       '-d', OPENGROK_DATA_ROOT,
                       '-c', '/usr/local/bin/ctags',
                       '--remote', 'on',
                       '-H',
                       '-S',
                       '-W', OPENGROK_CONFIG_FILE,
                       '--noIndex']

    if extra_indexer_options:
        if type(extra_indexer_options) is not list:
            raise Exception("extra_indexer_options has to be a list")
        indexer_options.extend(extra_indexer_options)
    if use_projects:
        indexer_options.append('-P')
    indexer = Indexer(indexer_options,
                      jar=OPENGROK_JAR,
                      logger=logger, doprint=True)
    indexer.execute()
    ret = indexer.getretcode()
    if ret != SUCCESS_EXITVAL:
        logger.error('Command returned {}'.format(ret))
        logger.error(indexer.geterroutput())
        raise Exception("Failed to create bare configuration")


def get_num_from_env(logger, env_name, default_value):
    value = default_value
    env_str = os.environ.get(env_name)
    if env_str:
        try:
            n = int(env_str)
            if n >= 0:
                value = n
        except ValueError:
            logger.error("{} is not a number: {}".
                         format(env_name, env_str))

    return value


def check_index_and_wipe_out(logger):
    """
    Check index by running the indexer. If the index does not match
    currently running version and the CHECK_INDEX environment variable
    is non-empty, wipe out the directories under data root.
    """
    check_index = os.environ.get('CHECK_INDEX')
    if check_index and os.path.exists(OPENGROK_CONFIG_FILE):
        logger.info('Checking if index matches current version')
        indexer_options = ['-R', OPENGROK_CONFIG_FILE, '--checkIndex']
        indexer = Indexer(indexer_options, logger=logger,
                          jar=OPENGROK_JAR, doprint=True)
        indexer.execute()
        if indexer.getretcode() == 1:
            logger.info('Wiping out data root')
            root = OPENGROK_DATA_ROOT
            for entry in os.listdir(root):
                path = os.path.join(root, entry)
                if os.path.isdir(path):
                    try:
                        logger.info("Removing '{}'".format(path))
                        shutil.rmtree(path)
                    except Exception as exc:
                        logger.error("cannot delete '{}': {}".format(path, exc))


def start_rest_thread(logger):
    rest_port = get_num_from_env(logger, 'REST_PORT', 5000)
    token = os.environ.get('REST_TOKEN')
    global expected_token
    if token:
        logger.debug("Setting expected token for REST endpoint"
                     "on port {}".format(rest_port))
        expected_token = token
    logger.debug("Starting REST thread to listen for requests "
                 "on port {} on the {} endpoint".
                 format(rest_port, REINDEX_POINT))
    rest_thread = threading.Thread(target=rest_function,
                                   name="REST thread",
                                   args=(logger, rest_port), daemon=True)
    rest_thread.start()


def main():
    log_level = os.environ.get('OPENGROK_LOG_LEVEL')
    if log_level:
        log_level = get_log_level(log_level)
    else:
        log_level = logging.INFO

    logger = get_console_logger(get_class_basename(), log_level)

    try:
        with open(os.path.join(OPENGROK_BASE_DIR, "VERSION"), "r") as f:
            version = f.read()
            logger.info("Running version {}".format(version))
    except Exception:
        pass

    uri, url_root = set_url_root(logger, os.environ.get('URL_ROOT'))
    logger.debug("URL_ROOT = {}".format(url_root))
    logger.debug("URI = {}".format(uri))

    sync_period_mins = get_num_from_env(logger, 'SYNC_PERIOD_MINUTES', 10)
    if sync_period_mins == 0:
        logger.info("periodic synchronization disabled")
    else:
        logger.info("synchronization period = {} minutes".format(sync_period_mins))

    # Note that deploy is done before Tomcat is started.
    deploy(logger, url_root)

    if url_root != 'source':
        setup_redirect_source(logger, url_root)

    env = {}
    extra_indexer_options = os.environ.get('INDEXER_OPT', '')
    if extra_indexer_options:
        logger.info("extra indexer options: {}".format(extra_indexer_options))
        env['OPENGROK_INDEXER_OPTIONAL_ARGS'] = extra_indexer_options
    if os.environ.get(NOMIRROR_ENV_NAME):
        env[OPENGROK_NO_MIRROR_ENV] = os.environ.get(NOMIRROR_ENV_NAME)
    logger.debug('Extra environment: {}'.format(env))

    use_projects = True
    if os.environ.get('AVOID_PROJECTS'):
        use_projects = False

    #
    # Create empty configuration to avoid the non-existent file exception
    # in the web app during the first web app startup.
    #
    if not os.path.exists(OPENGROK_CONFIG_FILE) or \
            os.path.getsize(OPENGROK_CONFIG_FILE) == 0:
        create_bare_config(logger, use_projects, extra_indexer_options.split())

    #
    # Index check needs read-only configuration, so it is called
    # right after create_bare_config().
    #
    check_index_and_wipe_out(logger)

    #
    # If there is read-only configuration file, merge it with current
    # configuration.
    #
    read_only_config_file = os.environ.get('READONLY_CONFIG_FILE')
    if read_only_config_file and os.path.exists(read_only_config_file):
        logger.info('Merging read-only configuration from \'{}\' with current '
                    'configuration in \'{}\''.format(read_only_config_file,
                                                     OPENGROK_CONFIG_FILE))
        out_file = None
        with tempfile.NamedTemporaryFile(mode='w+', delete=False,
                                         prefix='merged_config') as tmp_out:
            out_file = tmp_out.name
            merge_config_files(read_only_config_file, OPENGROK_CONFIG_FILE,
                               tmp_out, jar=OPENGROK_JAR, loglevel=log_level)

        if out_file and os.path.getsize(out_file) > 0:
            shutil.move(tmp_out.name, OPENGROK_CONFIG_FILE)
        else:
            logger.warning('Failed to merge read-only configuration, '
                           'leaving the original in place')
            if out_file:
                os.remove(out_file)

    sync_enabled = True
    if use_projects:
        mirror_config = os.path.join(OPENGROK_CONFIG_DIR, "mirror.yml")
        if not os.path.exists(mirror_config):
            with open(mirror_config, 'w') as fp:
                fp.write("# Empty config file for opengrok-mirror\n")

        num_workers = get_num_from_env(logger, 'WORKERS',
                                       multiprocessing.cpu_count())
        logger.info('Number of sync workers: {}'.format(num_workers))

        if not os.environ.get(NOMIRROR_ENV_NAME):
            conf = read_config(logger, mirror_config)
            logger.info("Checking mirror configuration in '{}'".
                        format(mirror_config))
            if not check_configuration(conf):
                logger.error("Mirror configuration in '{}' is invalid, "
                             "disabling sync".format(mirror_config))
                sync_enabled = False

        worker_function = project_syncer
        syncer_args = (logger, log_level, uri,
                       OPENGROK_CONFIG_FILE,
                       num_workers, env)
    else:
        worker_function = indexer_no_projects
        syncer_args = (logger, uri, OPENGROK_CONFIG_FILE,
                       extra_indexer_options)

    if sync_enabled:
        period_seconds = sync_period_mins * 60
        logger.debug(f"Creating and starting periodic timer (period {period_seconds} seconds)")
        global periodic_timer
        periodic_timer = PeriodicTimer(period_seconds)
        periodic_timer.start()

        logger.debug("Starting sync thread")
        sync_thread = threading.Thread(target=worker_function,
                                       name="Sync thread",
                                       args=syncer_args, daemon=True)
        sync_thread.start()

        start_rest_thread(logger)

    # Start Tomcat last. It will be the foreground process.
    logger.info("Starting Tomcat")
    global tomcat_popen
    tomcat_popen = subprocess.Popen([os.path.join(tomcat_root, 'bin',
                                                  'catalina.sh'),
                                    'run'])
    tomcat_popen.wait()


def signal_handler(signum, frame):
    print("Received signal {}".format(signum))

    global tomcat_popen
    if tomcat_popen:
        print("Terminating Tomcat {}".format(tomcat_popen))
        tomcat_popen.terminate()

    sys.exit(0)


if __name__ == "__main__":
    signal.signal(signal.SIGTERM, signal_handler)
    signal.signal(signal.SIGINT, signal_handler)

    main()
