#!/bin/bash

set -m

cd .groovy/lib
mkdir -p ~/.groovy/lib
cp *jar ~/.groovy/lib/
cd ../..

groovy webserver_json.groovy &

sleep 3s

open "index.html" || xdg-open "index.html"

fg