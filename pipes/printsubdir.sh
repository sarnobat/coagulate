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
  ## Images
  find "$line" -maxdepth 1 -type f -iname "*jpg" \
	| head -3 | sh file2img.sh 
	echo "<td>"
	echo "</tr>"

  ## Videos
  find "$line" -maxdepth 1 -type f -iname "*mp4" \
        | head -3 | sh video2img.sh
        echo "<td>"
        echo "</tr>"
		  
  ## Directories
  echo "<tr><td></td><td>"
  find "$line" -mindepth 1 -maxdepth 1 -type d \
        -not -regex '.*Preview.*' \
        -not -regex '.*Thumbnail.*' \
	-not -regex '.*ignore_iPhoto_Library.*"' \
	| grep -v 'ignore_iPhoto' \
	| sh printsubdir.sh
  echo "</td></tr>"

done < "${1:-/dev/stdin}"

echo "</table>"
