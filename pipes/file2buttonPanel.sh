#!/bin/bash
FILE=$1

DIR=`dirname "$FILE"`

echo "<span>"
echo $FILE | sh file2img.sh
echo "<br>"
find "$DIR/" -mindepth 1 -maxdepth 1 -type d -o -type l | sort | tee dirs.txt \
	| perl -MFile::Basename -pe 's{^(.+)$}{"<button onclick='\''moveFile(\"'"$FILE"'\", \"" . basename($1)."\", this)'\'' value=\"" . basename($1) . "\">" . basename($1) ."</button>"}ge' | tee dirs_buttons.txt
echo "<br>"
echo "</span>"
echo
