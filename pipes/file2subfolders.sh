dirname "$1" | xargs -I% -n 1 find -L % -mindepth 1 -maxdepth 1 -type d
