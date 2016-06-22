# TODO: Thumbnail images would be less memory intensive.

cat head.html | tee favorites.html
ls -d /home/sarnobat/other/favorites/  | sh printsubdir.sh | tee -a favorites.html

cat head.html | tee atletico.html
ls -d /media/sarnobat/e/new/Atletico  | sh printsubdir.sh | tee -a atletico.html

cat head.html | tee iPhone.html
ls -d /media/sarnobat/e/new/Photos/iPhone  | sh printsubdir.sh | tee -a iPhone.html
ls -d /media/sarnobat/Unsorted/new/Photos/iPhone  | sh printsubdir.sh | tee -a iPhone.html

