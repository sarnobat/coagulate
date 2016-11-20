#!/bin/bash
IMG=$1

FILE=`echo "$IMG" | perl -pe 's{<img.*?src=".*?44452([^"]*)".*>}{$1}g'`
DIR=`dirname "$FILE"`

echo "<span>"
echo $IMG
find "$DIR" -mindepth 1 -maxdepth 1 -type d | perl -MFile::Basename -pe 's{^(.+)$}{"<button onclick='\''moveFile(\"'"$FILE"'\", \"" . basename($1)."\", this)'\''>" . basename($1) ."</button>"}ge'
echo "<br>"
echo "</span>"
echo
