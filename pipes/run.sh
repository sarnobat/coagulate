echo "TODO: Nio Thumbnail images would be less memory intensive."

cat head.html | tee videos_home.html > /dev/null
ls -d -1 /Unsorted/Videos/home |  sh printsubdir_videos.sh | head -200 | tee -a videos_home.html
cat head.html | tee favorites.html > /dev/null
ls -d /home/sarnobat/other/favorites/  | sh printsubdir.sh | tee -a favorites.html

cat head.html | tee other.html > /dev/null
ls -d /home/sarnobat/other/  | sh printsubdir.sh | tee -a other.html >/dev/null
#cat head.html | tee other_sort.html ; cat other.html | grep '<img' | xargs -n 1 -d'\n' sh img2buttonPanel.sh  | tee -a other_sort.html
cd ~/github/coagulate/pipes; cat head.html| tee other_sort.html; find '/e/Drive J/pictures/Other (new)/pictures/misc_sync_master/' -maxdepth 1 -iname "*jpg" | head  -80 |  xargs --delimiter '\n' --max-args=1 sh file2buttonPanel.sh  | perl -pe 's{height=33}{height=250}g' | tee -a other_sort.html >/dev/null
cd ~/github/coagulate/pipes; cat head.html| tee images_sort.html; find '/Unsorted/new/images/' -maxdepth 1 -iname "*jpg" | shuf | grep -v '@' | head  -80 |  xargs --delimiter '\n' --max-args=1 sh file2buttonPanel.sh  | perl -pe 's{height=33}{height=250}g' | tee -a images_sort.html >/dev/null
cd ~/github/coagulate/pipes; find '/Unsorted/new/images/atletico' -maxdepth 1 -iname "*jpg" | shuf | grep -v '@' | head  -80 |  xargs --delimiter '\n' --max-args=1 sh file2buttonPanel.sh  | perl -pe 's{height=33}{height=250}g' | cat head.html - | tee atletico_sort.html >/dev/null
cd ~/github/coagulate/pipes; find '/Unsorted/new/images/atletico' -maxdepth 1 -type f -iname "*calderon*" -o -iname "*metropol*" | shuf | grep -v '@' | head  -80 |  xargs --delimiter '\n' --max-args=1 sh file2buttonPanel.sh  | perl -pe 's{height=33}{height=250}g' | cat head.html - | tee atletico_stadium_sort.html >/dev/null

cat head.html | tee atletico.html > /dev/null
ls -d /media/sarnobat/e/new/Atletico  | sh printsubdir.sh |head -80 | tee -a atletico_e.html >/dev/null

cat head.html | tee iPhone.html > /dev/null
ls -d /media/sarnobat/e/new/Photos/iPhone  | sh printsubdir.sh | head -80 |tee -a iPhone.html  >/dev/null
ls -d /media/sarnobat/Unsorted/new/Photos/iPhone | sh printsubdir.sh | head -80|tee -a iPhone.html >/dev/null

ls -d /Unsorted/new/images/*jpg  | sh printsubdir.sh | head -80 | cat head.html - | tee images.html  >/dev/null
ls -d /Unsorted/new/images/atletico/*{jpg,png,jpeg,gif} | sh printsubdir.sh | head -80 | shuf | cat head.html - | tee atletico.html >/dev/null


# TODO: /home/sarnobat/sarnobat.git/index/cats.txt

