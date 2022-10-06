#!/usr/bin/env python3

"""
 Update the README on Docker hub.

 This script uses the following secure variables:
  - DOCKER_USERNAME
  - DOCKER_PASSWORD

 These are set via https://github.com/oracle/opengrok/settings/secrets
"""

import logging
import os
import sys

import requests

API_URL = "https://hub.docker.com/v2"
IMAGE = "opengrok/docker"
MAIN_REPO_SLUG = "oracle/opengrok"


def get_token(username, password):
    """
    :param username: Docker hub username
    :param password: Docker hub password
    :return JWT token string from Docker hub API response
    """

    logger = logging.getLogger(__name__)

    logger.debug("Getting Docker hub token using username/password")
    headers = {"Content-Type": "application/json"}
    data = {"username": f"{username}", "password": f"{password}"}
    response = requests.post(f"{API_URL}/users/login/", headers=headers, json=data)
    response.raise_for_status()

    return response.json()["token"]


def update_readme(image, readme_file_path, username, password):
    """
    Update README file for given image on Docker hub.
    :param image: image path (in the form of "namespace_name/repository_name")
    :param readme_file_path path to the README file
    :param username: Docker hub username
    :param password: Docker hub password
    """

    logger = logging.getLogger(__name__)

    token = get_token(username, password)
    headers = {"Content-Type": "application/json", "Authorization": f"JWT {token}"}
    with open(readme_file_path, "r") as readme_fp:
        readme_data = readme_fp.read()
    logger.info("Updating README file on Docker hub")
    body_data = {"full_description": readme_data}
    response = requests.patch(
        f"{API_URL}/repositories/{image}/",
        headers=headers,
        json=body_data,
    )
    response.raise_for_status()


def check_push_env():
    """
    Check environment variables.
    Will exit the program if the environment is not setup for pushing images to Docker hub.
    Specifically, number of environment variables is required:
      - DOCKER_USERNAME
      - DOCKER_PASSWORD
    :return Docker hub username and password
    """

    logger = logging.getLogger(__name__)

    if os.environ.get("OPENGROK_PULL_REQUEST"):
        logger.info("Not updating Docker hub README for pull requests")
        sys.exit(0)

    docker_username = os.environ.get("DOCKER_USERNAME")
    if docker_username is None:
        logger.info("DOCKER_USERNAME is empty, exiting")
        sys.exit(1)

    docker_password = os.environ.get("DOCKER_PASSWORD")
    if docker_password is None:
        logger.info("DOCKER_PASSWORD is empty, exiting")
        sys.exit(1)

    return docker_username, docker_password


def main():
    """
    main program - update Docker hub README and exit.
    """
    logging.basicConfig(level=logging.INFO)
    logger = logging.getLogger(__name__)

    docker_username, docker_password = check_push_env()
    try:
        update_readme(IMAGE, "docker/README.md", docker_username, docker_password)
    except requests.exceptions.HTTPError as exc:
        logger.error(exc)
        sys.exit(1)


if __name__ == "__main__":
    main()
