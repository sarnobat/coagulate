# This is for rapid development. Probably you'll need a java program as well for non-trivial transformations.

PREFIX='http://netgear.rohidekar.com:44452'
while read line
do
  echo "$line" \
	| perl -pe 's{(^.*(jpg|gif))\n}{  <td><img src="'$PREFIX'$1" height=100 title="$1"></td>\n}gi' \
	| perl -pe 's{(^.*(mwk))\n}{       <iframe src="'$PREFIX'$1" ></iframe>}g'
#	| perl -pe 's{(^/.*)\n}{$1\n}g' \
#	| perl -pe 's{^(/.*)}{</tr><tr>$1}g'
#	| perl -pe 's{([^^])/}{$1</td><td>}g'
done < "${1:-/dev/stdin}"
