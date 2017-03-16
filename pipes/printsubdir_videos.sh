#!/bin/bash
# TODO : We need to make this recursive in the outer invocation but not the inner invocation

echo "<table>"
while read line
do

	echo "<tr>"
	echo "<td>"
	basename "$line"
	echo "</td>"
	echo "<td style='text-align : left'>"

  ## Videos
  find "$line" -maxdepth 1 -type f -iname "*mp4" \
	| grep -v _thumbnail \
        | head -9 | sh video2img.sh
        echo "<td>"
        echo "</tr>"
		  
  ## Directories
  echo "<tr><td></td><td>"
  find "$line" -mindepth 1 -maxdepth 1 -type d \
        -not -regex '.*Preview.*' \
        -not -regex '.*Thumbnail.*' \
	-not -regex '.*ignore_iPhoto_Library.*"' \
	| grep -v 'ignore_iPhoto' \
	| grep -v thumbnail \
	| sh printsubdir_videos.sh
  echo "</td></tr>"

done < "${1:-/dev/stdin}"

echo "</table>"
