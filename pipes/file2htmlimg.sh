while read line
do
  echo "$line" | perl -pe 's{(.*jpg)\n}{  <img src="$1"><br>\n}g'
done < "${1:-/dev/stdin}"
