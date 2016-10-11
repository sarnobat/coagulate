# TODO : We need to make this recursive in the outer invocation but not the inner invocation

echo "<table>"
while read line
do
  ## cells for indenting current dir
#  echo "$line" | perl -pe 's{(.)/}{$1</td><td>}g' \
#	| perl -pe 's{\n}{</td><td>\n}g' \
#	| perl -pe 's{^/}{</td></tr>\n<tr>/}g'

	echo "<tr>"
	echo "<td>"
	basename $line
	echo "</td>"
	echo "<td style='text-align : left'>"
  ## Images
  find "$line" -maxdepth 1 -type f -iname "*jpg" | head -5 | sh file2img.sh 
	echo "<td>"
	echo "</tr>"
		  
  ## Directories
  echo "<tr><td></td><td>"
  find "$line" -mindepth 1 -maxdepth 1 -type d | sh printsubdir.sh #| perl -pe 's{(.*)}{<tr>$1</tr>}g'
  echo "</td></tr>"

#  	echo "</td></tr>"
done < "${1:-/dev/stdin}"

echo "</table>"
