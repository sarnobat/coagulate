#!/bin/bash

set -m

cd .groovy/lib
rm json-20090211.jar
rm ~/.groovy/lib/json-20090211.jar
mkdir -p ~/.groovy/lib
cp *jar ~/.groovy/lib/
cd ../..

groovy server.groovy &

sleep 3s

open "index.html" || xdg-open "index.html"

fg
