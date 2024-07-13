#
# Launch web server from a root shell.
#

APP_FOLDER=$(find /data/app -type d -name "org.adaway-*")
LIB_FOLDER="${APP_FOLDER}/lib/arm64"
WEBSERVER_EXEC="$LIB_FOLDER/libwebserver_exec.so"
WEBSERVER_ARGS="--resources /data/user/0/org.adaway/files/webserver --debug"

if [ ! -e $WEBSERVER_EXEC ]; then
  echo "Web server exec not found" >&2
  exit 1
fi

echo "Running LD_LIBRARY_PATH=$LIB_FOLDER $WEBSERVER_EXEC $WEBSERVER_ARGS"
LD_LIBRARY_PATH=$LIB_FOLDER $WEBSERVER_EXEC $WEBSERVER_ARGS
