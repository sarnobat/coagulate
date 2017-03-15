#!/bin/bash
FILE=$1

DIR=`dirname "$FILE"`

echo "<span>"
echo $FILE | sh file2img.sh
echo "<br>"
find "$DIR" -mindepth 1 -maxdepth 1 -type d | perl -MFile::Basename -pe 's{^(.+)$}{"<button onclick='\''moveFile(\"'"$FILE"'\", \"" . basename($1)."\", this)'\''>" . basename($1) ."</button>"}ge'
echo "<br>"
echo "</span>"
echo
