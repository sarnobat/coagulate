#echo "[DEBUG] Begin file2img.sh"
PREFIX="http://netgear.rohidekar.com:44452"
while read line
do
  #echo "[DEBUG] $line"
  echo "$line" | tee ~/trash/temp1.txt | groovy thumbnailForFile.groovy | tee  ~/trash/temp2.txt | perl -pe 's{^(.*)\n}{<img src="'$PREFIX'$1" height=53 onmouseenter="zoom(this)">\n}g'
  basename "$line"
done < "${1:-/dev/stdin}"
