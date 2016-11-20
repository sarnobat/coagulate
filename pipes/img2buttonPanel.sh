#!/bin/bash
IMG=$1
echo $IMG
FILE=`echo "$IMG" | perl -pe 's{<img.*?src=".*?44452([^"]*)".*>}{$1}g'`
#FILE='/home/sarnobat/other/favorites/trash/Getting started · Bootstrap_files/carousel.jpg'
echo $FILE
DIR=`dirname "$FILE"
#FILE_SIMPLE=`basename "$FILE"`
find "$DIR" -mindepth 1 -maxdepth 1 -type d | perl -MFile::Basename -pe 's{(.*)\n}{<button onclick="moveFile("'"$FILE"'","$1")">$1</button><br>\n}ges'
echo
