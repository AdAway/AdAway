#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <signal.h>
#include <android/log.h>

#include <sys/wait.h>
#define WINCDECL

#include "mongoose.h"

static int exit_flag;

static void WINCDECL signal_handler(int sig_num) {
  exit_flag = sig_num;
}

// This function will be called by mongoose on every new request.
static int begin_request_handler(struct mg_connection *conn) {
  const struct mg_request_info *request_info = mg_get_request_info(conn);
  char content[100];

  // Prepare the message we're going to send
  int content_length = snprintf(content, sizeof(content), "");

  // Send HTTP reply to the client
  mg_printf(conn,
            "HTTP/1.1 200 OK\r\n"
            "Content-Type: text/plain\r\n"
            "Content-Length: %d\r\n" // Always set Content-Length
            "\r\n"
            "%s",
            content_length, content);

  // Returning non-zero tells mongoose that our function has replied to
  // the client, and mongoose should not send client any more data.
  return 1;
}

int main(void) {
  struct mg_context *ctx;
  struct mg_callbacks callbacks;
  
  __android_log_print(ANDROID_LOG_INFO,"AdAway", "Native Webserver: Starting Webserver...");

  // List of options. Last element must be NULL.
  // don't use 0.0.0.0 as ip address, it will be accessible in the network
  const char *options[] = {"listening_ports", "127.0.0.1:80,127.0.0.1:443", NULL};

  // Prepare callbacks structure. We have only one callback, the rest are NULL.
  memset(&callbacks, 0, sizeof(callbacks));
  callbacks.begin_request = begin_request_handler;

  // Setup signal handler: quit on Ctrl-C
  signal(SIGTERM, signal_handler);
  signal(SIGINT, signal_handler);
  
  // Start mongoose
  ctx = mg_start(&callbacks, NULL, options);
  
  __android_log_print(ANDROID_LOG_INFO,"AdAway", "Native Webserver: Webserver started!");

  while (exit_flag == 0) {
    sleep(1);
  }
  
  // Stop mongoose
  __android_log_print(ANDROID_LOG_INFO,"AdAway", "Native Webserver: Exiting on signal %d, waiting for all threads to finish...", exit_flag);
  fflush(stdout);
  mg_stop(ctx);
  __android_log_print(ANDROID_LOG_INFO,"AdAway", "Native Webserver: done.\n", exit_flag);

  return 0;
}
