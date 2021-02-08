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
# Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
#

import os
import logging
import multiprocessing
import shutil
import subprocess
import threading
import time
from pathlib import Path
from requests import get, ConnectionError

from opengrok_tools.utils.log import get_console_logger, \
    get_log_level, get_class_basename
from opengrok_tools.deploy import deploy_war
from opengrok_tools.utils.indexer import Indexer
from opengrok_tools.sync import do_sync
from opengrok_tools.utils.opengrok import list_projects, \
    add_project, delete_project, get_configuration
from opengrok_tools.utils.readconfig import read_config
from opengrok_tools.utils.exitvals import SUCCESS_EXITVAL


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
OPENGROK_CONFIG_FILE = os.path.join(OPENGROK_BASE_DIR, "etc",
                                    "configuration.xml")
OPENGROK_WEBAPPS_DIR = os.path.join(tomcat_root, "webapps")


def set_url_root(logger, url_root):
    """
    Set URL root and URI based on input
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
    Currently there is no upper time bound.
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
    :param uri: web app URI
    :param config_path: file path
    """

    logger.info('Saving configuration to {}'.format(config_path))
    config = get_configuration(logger, uri)
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


def syncer(logger, loglevel, uri, config_path, reindex, numworkers, env):
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
        #
        # The driveon=True is needed for the initial indexing of newly
        # added project, otherwise the incoming check in the opengrok-mirror
        # would short circuit it.
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

        sleep_seconds = int(reindex) * 60
        logger.info("Sleeping for {} seconds".format(sleep_seconds))
        time.sleep(sleep_seconds)


def create_bare_config(logger):
    """
    Create bare configuration file with a few basic settings.
    """

    logger.info('Creating bare configuration in {}'.
                format(OPENGROK_CONFIG_FILE))
    indexer = Indexer(['-s', OPENGROK_SRC_ROOT,
                       '-d', OPENGROK_DATA_ROOT,
                       '-c', '/usr/local/bin/ctags',
                       '--remote', 'on',
                       '-P', '-H',
                       '-W', OPENGROK_CONFIG_FILE,
                       '--noIndex'],
                      jar=os.path.join(OPENGROK_LIB_DIR,
                                       'opengrok.jar'),
                      logger=logger, doprint=True)
    indexer.execute()
    ret = indexer.getretcode()
    if ret != SUCCESS_EXITVAL:
        logger.error('Command returned {}'.format(ret))
        logger.error(indexer.geterroutput())
        raise Exception("Failed to create bare configuration")


def main():
    log_level = os.environ.get('OPENGROK_LOG_LEVEL')
    if log_level:
        log_level = get_log_level(log_level)
    else:
        log_level = logging.INFO

    logger = get_console_logger(get_class_basename(), log_level)

    uri, url_root = set_url_root(logger, os.environ.get('URL_ROOT'))
    logger.debug("URL_ROOT = {}".format(url_root))
    logger.debug("URI = {}".format(uri))

    # default period for reindexing (in minutes)
    reindex_env = os.environ.get('REINDEX')
    if reindex_env:
        reindex_min = reindex_env
    else:
        reindex_min = 10
    logger.debug("reindex period = {} minutes".format(reindex_min))

    # Note that deploy is done before Tomcat is started.
    deploy(logger, url_root)

    if url_root != '/source':
        setup_redirect_source(logger, url_root)

    env = {}
    if os.environ.get('INDEXER_OPT'):
        env['OPENGROK_INDEXER_OPTIONAL_ARGS'] = \
            os.environ.get('INDEXER_OPT')
    if os.environ.get('NOMIRROR'):
        env['OPENGROK_NO_MIRROR'] = os.environ.get('NOMIRROR')
    logger.debug('Extra environment: {}'.format(env))

    #
    # Create empty configuration to avoid the non existent file exception
    # in the web app during the first web app startup.
    #
    if not os.path.exists(OPENGROK_CONFIG_FILE) or \
            os.path.getsize(OPENGROK_CONFIG_FILE) == 0:
        create_bare_config(logger)

    if os.environ.get('WORKERS'):
        num_workers = os.environ.get('WORKERS')
    else:
        num_workers = multiprocessing.cpu_count()
    logger.info('Number of sync workers: {}'.format(num_workers))

    logger.debug("Starting sync thread")
    thread = threading.Thread(target=syncer, name="Sync thread",
                              args=(logger, log_level, uri,
                                    OPENGROK_CONFIG_FILE,
                                    reindex_min, num_workers, env))
    thread.start()

    # Start Tomcat last. It will be the foreground process.
    logger.info("Starting Tomcat")
    subprocess.run([os.path.join(tomcat_root, 'bin', 'catalina.sh'), 'run'])


if __name__ == "__main__":
    main()
