#echo "[DEBUG] Begin file2img.sh"
PREFIX="http://netgear.rohidekar.com:44452"
while read line
do
#2>  echo "[DEBUG] $line"
  echo "$line"  | groovy ~/github/coagulate/pipes/file2image.groovy | perl -pe 's{^(.*)\n}{<img src="'$PREFIX'$1" height=53 onmouseenter="zoom(this)">\n}g'
link="$PREFIX\/$line"
#echo "$line"
  basename "$line" # | perl -pe 's{^(.*)\n}{<a href="'$link'">$1</a>}g'
done < "${1:-/dev/stdin}"
