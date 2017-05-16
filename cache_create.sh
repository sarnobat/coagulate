cd "$1"
echo "$PWD" | ionice groovy ~/github/coagulate/server_list_cli.groovy | tee _coagulate.txt

