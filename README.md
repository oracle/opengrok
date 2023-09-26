
Copyright (c) 2006, 2023 Oracle and/or its affiliates. All rights reserved.


# OpenGrok - a wicked fast source browser
[![Github actions build](https://github.com/oracle/opengrok/workflows/Build/badge.svg?branch=master)](https://github.com/oracle/opengrok/actions)
[![Coverage status](https://coveralls.io/repos/oracle/opengrok/badge.svg?branch=master)](https://coveralls.io/r/oracle/opengrok?branch=master)
[![SonarQube status](https://sonarcloud.io/api/project_badges/measure?project=org.opengrok%3Aopengrok-top&metric=alert_status)](https://sonarcloud.io/dashboard?id=org.opengrok%3Aopengrok-top)
[![License](https://img.shields.io/badge/License-CDDL%201.0-blue.svg)](https://opensource.org/licenses/CDDL-1.0)

- [OpenGrok - a wicked fast source browser](#opengrok---a-wicked-fast-source-browser)
  - [1. Introduction](#1-introduction)
  - [2. OpenGrok install and setup](#2-opengrok-install-and-setup)
  - [3. Information for developers](#3-information-for-developers)
  - [4. Authors](#4-authors)
  - [5. Contact us](#5-contact-us)
  - [6. Run as container](#6-run-as-container)

## 1. Introduction

OpenGrok is a fast and usable source code search and cross reference
engine, written in Java. It helps you search, cross-reference and navigate
your source tree. It can understand various program file formats and
version control histories of many source code management systems.

Official page of the project is on:
<https://oracle.github.io/opengrok/>

## 2. Installation

See https://github.com/oracle/opengrok/wiki/How-to-setup-OpenGrok

### 2. 1. Updating

OpenGrok uses [semantic versioning](https://semver.org/) and the version components further indicate more details about updating to newer version. The version scheme is *major.minor.micro* and change in any component is interpreted as:

 - major - major backwards incompatible update, will require full reindex as well as configuration changes
 - minor - full clean reindex of your repositories is needed (e. g. index format has changed)
 - micro - redeploy web application

Generally it is possible to go backward only within the micro version.

## 3. Information for developers

See https://github.com/oracle/opengrok/wiki/Developer-intro and https://github.com/oracle/opengrok/wiki/Developers

## 4. Authors

The project has been originally conceived in [Sun Microsystems](https://en.wikipedia.org/wiki/Sun_Microsystems) by Chandan B.N.

For full list of contributors see https://github.com/oracle/opengrok/graphs/contributors

## 5. Contact us

Use the [Github Discussions](https://github.com/oracle/opengrok/discussions).

## 6. Run as container

You can run OpenGrok as a Docker container as described [here](docker/README.md).

## Contributing

This project welcomes contributions from the community. Before submitting a pull request, please [review our contribution guide](./CONTRIBUTING.md)

## Security

Please consult the [security guide](./SECURITY.md) for our responsible security vulnerability disclosure process

## License

Copyright (c) 2006, 2023 Oracle and/or its affiliates.
