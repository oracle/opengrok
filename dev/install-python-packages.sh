#!/bin/bash

echo "Installing/upgrading pip.."
python3 -m pip install --upgrade pip setuptools

echo "Installing Python packages.."
python3 -m pip install pep8 virtualenv
if [[ $? != 0 ]]; then
	echo "cannot install Python packages"
	exit 1
fi
