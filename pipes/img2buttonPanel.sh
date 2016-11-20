#!/bin/bash
IMG=$1
echo $IMG
FILE=`echo "$IMG" | perl -pe 's{<img.*?src=".*?44452([^"]*)".*>}{$1}g'`
#FILE='/home/sarnobat/other/favorites/trash/Getting started · Bootstrap_files/carousel.jpg'
#echo $FILE
DIR=`dirname "$FILE"`
#find "$DIR" -mindepth 1 -maxdepth 1 -type d | perl -MFile::Basename -pe 's{(.*)\n}{<button onclick=moveFile("'"$FILE"'","$1")>@{basename($1)}</button><br>\n}g'
find "$DIR" -mindepth 1 -maxdepth 1 -type d | perl -MFile::Basename -pe 's{^(.+)$}{"<button onclick=\"moveFile(\"'"$FILE"'\", \"" . basename($1)."\")>" . basename($1) ."</button>"}ge'
echo "<br>"
echo
