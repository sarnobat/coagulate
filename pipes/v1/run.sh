find /Unsorted/images/other/ -type d | head -60 | xargs -n 1 -d '\n' ~/github/coagulate/pipes/v1/one_file | sh ~/github/coagulate/pipes/v1/file2htmlimg.sh | tee index.html
