# TODO : We need to make this recursive in the outer invocation but not the inner invocation
while read line
do
  ## Directories
  echo "$line" | perl -pe 's{(.)/}{$1</td><td>}g' \
	| perl -pe 's{\n}{</td><td>\n}g' \
	| perl -pe 's{^/}{</td></tr><tr>/}g'
  ## Images
  find "$line" -maxdepth 1 -type f -iname "*jpg" | head -5 | sh file2img.sh 
done < "${1:-/dev/stdin}"
