#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <signal.h>
#include <android/log.h>
#include <errno.h>
#include "mongoose.h"

#define THIS_FILE "AdAway"

static const char *empty_html =
  " ";

static void send_reply(struct mg_connection *conn) {
  mg_send_status(conn, 200);
  mg_send_header(conn, "Content-Type", "text/plain");
  mg_send_data(conn, empty_html, strlen(empty_html));
}

static int ev_handler(struct mg_connection *conn, enum mg_event ev) {
  if (ev == MG_REQUEST) {
    send_reply(conn);
    return MG_TRUE;
  } else if (ev == MG_AUTH) {
    return MG_TRUE;
  } else {
    return MG_FALSE;
  }
}


#define OOM_ADJ_PATH	"/proc/self/oom_adj"
/*
* The magic "don't kill me", as documented in eg:
* http://lxr.linux.no/#linux+v2.6.32/Documentation/filesystems/proc.txt
*/
#define OOM_ADJ_NOKILL	-17
static int oom_adj_save = INT_MIN;
/*
* Tell the kernel's out-of-memory killer to avoid this process.
* Returns the previous oom_adj value or zero.
*/
void oom_adjust_setup(void) {
  FILE *fp;
  if ((fp = fopen(OOM_ADJ_PATH, "r+")) != NULL) {
    if (fscanf(fp, "%d", &oom_adj_save) != 1)
      __android_log_print(ANDROID_LOG_INFO, THIS_FILE, "error reading %s: %s", OOM_ADJ_PATH, strerror(errno));
    else {
      rewind(fp);
      if (fprintf(fp, "%d\n", OOM_ADJ_NOKILL) <= 0)
        __android_log_print(ANDROID_LOG_INFO, THIS_FILE, "error writing %s: %s", OOM_ADJ_PATH, strerror(errno));
      else
        __android_log_print(ANDROID_LOG_INFO, THIS_FILE, "Set %s from %d to %d", OOM_ADJ_PATH, oom_adj_save, OOM_ADJ_NOKILL);
    }
    fclose(fp);
  }
}

int main(void) {
  struct mg_server *server;

  __android_log_print(ANDROID_LOG_INFO, THIS_FILE, "Native Webserver: Starting Webserver...");

  oom_adjust_setup();

  // Create and configure the server
  server = mg_create_server(NULL, ev_handler);
  mg_set_option(server, "listening_port", "127.0.0.1:80,127.0.0.1:443");

  // Serve request. Hit Ctrl-C to terminate the program
  __android_log_print(ANDROID_LOG_INFO, THIS_FILE, "Native Webserver: Webserver started!");
  for (;;) {
    mg_poll_server(server, 1000);
  }

  // Cleanup, and free server instance
  mg_destroy_server(&server);

  __android_log_print(ANDROID_LOG_INFO, THIS_FILE, "Native Webserver: exited");

  return 0;
}
