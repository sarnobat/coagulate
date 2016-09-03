while read line
do
  find "$line" -maxdepth 1 -type f -iname "*jpg"
done < "${1:-/dev/stdin}"
