PREFIX='http://netgear.rohidekar.com:44452'
while read line
do
  echo "$line" \
	| perl -pe 's{(^.*jpg)\n}{  <img src="'$PREFIX'$1" height=100>\n}gi' \
	| perl -pe 's{(^/.*)\n}{$1\n}g' \
	| perl -pe 's{/}{</td><td>}g'
done < "${1:-/dev/stdin}"
