echo "TODO: Nio Thumbnail images would be less memory intensive."

##
## Sort
##
cd ~/github/coagulate/pipes; find /Unsorted/new/screenshots -maxdepth 1 -type f -iname "*" \
	| head  -280 \
        | xargs --delimiter '\n' --max-args=1 sh file2buttonPanel.sh  \
        | perl -pe 's{height=33}{height=250}g' \
        | cat head.html - \
        | tee screenshots_sort.html > /dev/null 

cd ~/github/coagulate/pipes; find '/Unsorted/Videos/' -maxdepth 1 -type f -iname "**" \
	| shuf \
	| grep -v '@' \
	| grep -v '.fuse' \
	| grep -v '.txt' \
	| grep -v '.log' \
	| head  -280 \
	|  xargs --delimiter '\n' --max-args=1 sh file2buttonPanel.sh  \
	| perl -pe 's{height=33}{height=250}g' \
	| cat head.html - \
	| tee videos_sort.html >/dev/null

echo "Success: videos_sort.html"

cd ~/github/coagulate/pipes; find '/Unsorted/Videos/' -maxdepth 1 -type f -iname "**" \
	| grep -i -e oops   -e skirt -e boob -e danc -e dress -e poderosa -e expose \
	   -e '\bass\b' -e '\bbutt\b' -e '\bhot\b' -e 'poderosa' -e 'uncensor' -e 'bikini'  \
	   -e 'photo*shoot' -e 'wardrobe*malfun*' \
	| shuf \
	| grep -v '@' \
	| grep -v '.fuse' \
	| head  -280 \
	| xargs --delimiter '\n' --max-args=1 sh file2buttonPanel.sh  \
	| perl -pe 's{height=33}{height=250}g' \
	| cat head.html - \
	| tee videos_other_sort.html >/dev/null

echo "Success: videos_other_sort.html"

cd ~/github/coagulate/pipes; find '/Unsorted/Videos/' -maxdepth 1 -type f \
	  -iname "*adrid*" -o -iname "*calderon*" -o -iname "*metropolitano*" -o -iname "*atl*ti*" \
	  -o -iname "*maldini*" \
	| shuf \
	| grep -v '@' \
	| grep -v '.fuse' \
	| head  -180 \
	| xargs --delimiter '\n' --max-args=1 sh file2buttonPanel.sh  \
	| perl -pe 's{height=33}{height=250}g' \
	| cat head.html - \
	| tee videos_atletico_sort.html >/dev/null

echo "Success: videos_atletico_sort.html"

##
## Sort (file2buttonPanel.sh)
##

cd ~/github/coagulate/pipes; cat head.html| tee other_sort.html; find '/e/Drive J/pictures/Other (new)/pictures/misc_sync_master/' -maxdepth 1 -iname "*jpg" | head  -80 |  xargs --delimiter '\n' --max-args=1 sh file2buttonPanel.sh  | perl -pe 's{height=33}{height=250}g' | tee other_sort.html >/dev/null
cd ~/github/coagulate/pipes; cat head.html| tee images_sort.html; find '/Unsorted/new/images/' -maxdepth 1 -iname "*jpg" | shuf | grep -v '@' | head  -80 |  xargs --delimiter '\n' --max-args=1 sh file2buttonPanel.sh  | perl -pe 's{height=33}{height=250}g' | tee images_sort.html >/dev/null
cd ~/github/coagulate/pipes; find '/Unsorted/new/images/atletico' -maxdepth 1 -iname "*jpg" | shuf | grep -v '@' | head  -80 |  xargs --delimiter '\n' --max-args=1 sh file2buttonPanel.sh  | perl -pe 's{height=33}{height=250}g' | cat head.html - | tee atletico_sort.html >/dev/null
cd ~/github/coagulate/pipes; find '/Unsorted/new/images/atletico' -maxdepth 1 -type f -iname "*calderon*" -o -iname "*metropol*" | shuf | grep -v '@' | head  -80 |  xargs --delimiter '\n' --max-args=1 sh file2buttonPanel.sh  | perl -pe 's{height=33}{height=250}g' | cat head.html - | tee atletico_stadium_sort.html >/dev/null




##
## Recursive (printsubdir.sh)
##

ls -d -1 /Unsorted/Videos/home 							| sh printsubdir_videos.sh | head -200 	| cat head.html - >  videos_home.html
ls -d /home/sarnobat/other/favorites/  					| sh printsubdir.sh 					| cat head.html - | tee favorites.html > /dev/null
ls -d /home/sarnobat/other/  							| sh printsubdir.sh 					| cat head.html - | tee other.html >/dev/null
ls -d /media/sarnobat/e/new/Atletico  					| sh printsubdir.sh | head -80 			| cat head.html - | tee atletico_e.html >/dev/null
ls -d /media/sarnobat/e/new/Photos/iPhone  				| sh printsubdir.sh | head -80 			| cat head.html - | tee iPhone.html  >/dev/null
ls -d /media/sarnobat/Unsorted/new/Photos/iPhone 		| sh printsubdir.sh | head -80 			| cat head.html - | tee iPhone.html >/dev/null
ls -d /Unsorted/new/images/*jpg  						| sh printsubdir.sh | head -80 			| cat head.html - | tee images.html  >/dev/null
ls -d /Unsorted/new/images/atletico/*{jpg,png,jpeg,gif} | sh printsubdir.sh | head -80 | shuf 	| cat head.html - | tee atletico.html >/dev/null


# TODO: /home/sarnobat/sarnobat.git/index/cats.txt

