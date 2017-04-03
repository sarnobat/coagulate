#!/bin/bash
FILE=$1
FILE_ESCAPED=`echo "$1" | perl -pe 's{'\''}{&#39;}g'`

DIR=`dirname "$FILE"`

#echo "[DEBUG] about to call file2img.sh"
echo "<span>"
echo $FILE | sh file2img.sh
echo "<br>"
find "$DIR/" -mindepth 1 -maxdepth 1 -type d -o -type l | sort | tee dirs.txt \
	| perl -MFile::Basename -pe 's{^(.+)$}{"<button onclick='\''moveFile(\"'"$FILE_ESCAPED"'\", \"" . basename($1)."\", this)'\'' value=\"" . basename($1) . "\">" . basename($1) ."</button>"}ge' | tee dirs_buttons.txt
echo "<br>"
echo "</span>"
echo
