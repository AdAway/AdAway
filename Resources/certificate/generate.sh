rm localhost.*

openssl req -x509 -out localhost.crt -keyout localhost.key -newkey rsa:2048 -nodes -sha256 -days 1126 -subj /CN=localhost -extensions EXT -config ssl.conf
