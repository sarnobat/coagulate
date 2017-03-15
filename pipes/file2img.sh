PREFIX="http://netgear.rohidekar.com:44452"
while read line
do
  echo "$line" | perl -pe 's{^(.*)\n}{<img src="'$PREFIX'$1" height=53 onmouseenter="zoom(this)">\n}g'
  basename "$line"
done < "${1:-/dev/stdin}"
