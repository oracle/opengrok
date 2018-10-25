#!/bin/bash

python3 -m pip install --upgrade pip

python3 -m pip install pep8 flake8 virtualenv
if [[ $? != 0 ]]; then
	echo "cannot install Python packages"
	exit 1
fi
