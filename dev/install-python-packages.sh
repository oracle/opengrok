#!/bin/bash

python3 -m pip install --upgrade pip
python3 -m pip install -r opengrok-tools/src/main/python/requirements.txt
python3 -m pip install pep8 flake8 virtualenv pylint
