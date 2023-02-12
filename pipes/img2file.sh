cat - | perl -pe 's{<img.*?src="([^"]*)".*>}{$1}g' 
