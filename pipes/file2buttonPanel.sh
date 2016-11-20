echo "$1"
#echo "$1" | perl -MFile::Basename  -pe 's{([^\n]+)}{"<button onclick=alert(\"$1\"," .dirname($1).  "\")>".basename(dirname($1))."</button><br>"}ges'    
DIR=`dirname "$1"`
find "$DIR" -mindepth 1 -maxdepth 1 -type d
echo "----------"
