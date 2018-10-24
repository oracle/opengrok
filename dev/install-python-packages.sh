#!/bin/bash

python3 -m pip install --upgrade pip

python3 -m pip install -r opengrok-tools/src/main/python/requirements.txt
if [[ $? != 0 ]]; then
	echo "cannot install Python requirement packages"
	exit 1
fi

python3 -m pip install pep8 flake8 virtualenv pylint
if [[ $? != 0 ]]; then
	echo "cannot install Python packages"
	exit 1
fi
