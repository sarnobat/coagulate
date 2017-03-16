while read line
do
  echo "$line" | perl -MFile::Basename  -pe 's{^(.*)\n}{"<img src=\"http://netgear.rohidekar.com:44452" . dirname($1) . "/_thumbnails/" . basename($1) . ".jpg\" height=53 onmouseenter=\"zoom(this)\">\n"}ge' ;
done < "${1:-/dev/stdin}"
