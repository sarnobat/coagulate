#echo "[DEBUG] Begin file2img.sh"
#PREFIX="http://netgear.rohidekar.com:44452"
PREFIX=http://localhost:1156/webdav/
while read line
do
	# TODO: I don't think this approach will work for recursing dirs
	# TODO: I can't get spans to occur horizontally if they have newlines inside them
#2>  echo "[DEBUG] $line"
  #echo "<span>"
link="$PREFIX\/$line"
#echo "$line"
  basename "$line" # | perl -pe 's{^(.*)\n}{<a href="'$link'">$1</a>}g'
  #echo "<br>"
  echo "$line"  | groovy ~/github/coagulate/pipes/file2image.groovy | perl -pe 's{^(.*)\n}{<img src="'$PREFIX'$1" height=53 onmouseenter="zoom(this)">\n}g'
  #echo "</span>"
done < "${1:-/dev/stdin}"
