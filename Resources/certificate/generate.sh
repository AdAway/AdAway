rm localhost.*

# Add compatiblity for MINGW
kernel=$(uname -s)
if [[ $kernel == MINGW* ]]; then
  export MSYS_NO_PATHCONV=1
fi

openssl req -x509 -out localhost.crt -keyout localhost.key -newkey rsa:2048 -nodes -sha256 -days 1126 -subj /CN=localhost -extensions EXT -config ssl.conf
