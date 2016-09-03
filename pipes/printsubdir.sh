while read line
do
  echo "$line" | perl -pe 's{/}{</td><td>}g' \
	| perl -pe 's{^</td>}{</tr><tr>}g'
  find "$line" -maxdepth 1 -type f -iname "*jpg" | head -5 | sh file2img.sh 
done < "${1:-/dev/stdin}"
