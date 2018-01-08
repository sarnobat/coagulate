#!/bin/bash
find /home/sarnobat/sarnobat.git/mwk/snippets -type f | head -3   | groovy ~/github/coagulate/pipes/v1/filePaths2htmlBlockIndent.groovy  | groovy ~/github/coagulate/pipes/v1/mwk2htmlCard.groovy | cat <(echo "<script  src='https://code.jquery.com/jquery-3.2.1.js'   integrity='sha256-DZAnKJ/6XZ9si04Hgrsxu/8s717jcIzLy3oi35EouyE='   crossorigin='anonymous'></script>") - | tee /tmp/index.html
find /Unsorted/images/other/ -type d | head -60 | xargs -n 1 -d '\n' ~/github/coagulate/pipes/v1/one_file | sh ~/github/coagulate/pipes/v1/file2htmlimg.sh | tee index.html
