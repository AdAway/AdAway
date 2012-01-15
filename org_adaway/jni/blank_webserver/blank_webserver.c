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

static void *callback(enum mg_event event,
                      struct mg_connection *conn,
                      const struct mg_request_info *request_info) {
  if (event == MG_NEW_REQUEST) {
    // print blank page
    mg_printf(conn, "HTTP/1.1 200 OK\r\n"
              "Content-Type: text/plain\r\n\r\n"
              "", request_info->uri);
    return "";  // Mark as processed
  } else {
    return NULL;
  }
}

// from http://www.enderunix.org/documents/eng/daemon.php
void daemonize()
{
int i,lfp;
char str[10];
    if(getppid()==1) return; /* already a daemon */
    i=fork();
    if (i<0) exit(1); /* fork error */
    if (i>0) exit(0); /* parent exits */
    /* child (daemon) continues */
    setsid(); /* obtain a new process group */
}

// don't use 0.0.0.0 as ip address, it will be accessible in the network
static const char *options[] = {
  "listening_ports", "127.0.0.1:80,443",
  NULL
};

int main(void) {
  struct mg_context *ctx;
  
  __android_log_print(ANDROID_LOG_INFO,"AdAway", "Native Webserver: Starting Webserver...");

  daemonize();
  
  /* Setup signal handler: quit on Ctrl-C */
  signal(SIGTERM, signal_handler);
  signal(SIGINT, signal_handler);

  /* Start mongoose */
  ctx = mg_start(&callback, NULL, options);
  
  __android_log_print(ANDROID_LOG_INFO,"AdAway", "Native Webserver: Webserver started!");
  
  while (exit_flag == 0) {
    sleep(1);
  }
  
  /* Stop mongoose */
  __android_log_print(ANDROID_LOG_INFO,"AdAway", "Native Webserver: Exiting on signal %d, waiting for all threads to finish...", exit_flag);
  fflush(stdout);
  mg_stop(ctx);
  __android_log_print(ANDROID_LOG_INFO,"AdAway", "Native Webserver: done.\n", exit_flag);

  return 0;
}