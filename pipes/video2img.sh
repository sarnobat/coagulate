PREFIX="http://netgear.rohidekar.com:44452"
while read line
do
  path="$line"
  dir=`dirname "$path"`
  file=`basename "$path"`
echo $line
  echo "$line" | perl -MFile::Basename  -pe 's{^(.*)\n}{"<img src=\"http://netgear.rohidekar.com:44452" . dirname($1) . "/_thumbnails/" . basename($1) . ".jpg\" height=53 onmouseenter=\"zoom(this)\">\n"}ge' ;
  echo "$file<br>sridhar"
done < "${1:-/dev/stdin}"
