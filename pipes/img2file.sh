echo "$1" | perl -pe 's{<img.*?src="([^"]*)".*>}{$1}g' 
