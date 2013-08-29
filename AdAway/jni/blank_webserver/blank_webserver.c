#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <signal.h>
#include <android/log.h>

#include <errno.h>

#include <sys/wait.h>
#define WINCDECL

#include "mongoose.h"

#define THIS_FILE "AdAway"

static int exit_flag;

static void WINCDECL signal_handler(int sig_num) {
  exit_flag = sig_num;
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
void oom_adjust_setup(void)
{
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
  
  __android_log_print(ANDROID_LOG_INFO, THIS_FILE, "Native Webserver: Starting Webserver...");
  
  pid_t pid;
  pid = getpid();
  
  __android_log_print(ANDROID_LOG_INFO, THIS_FILE, "Native Webserver: pid: %d", pid);
  
  oom_adjust_setup();

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
  
  __android_log_print(ANDROID_LOG_INFO, THIS_FILE, "Native Webserver: Webserver started!");

  while (exit_flag == 0) {
    sleep(1);
  }
  
  // Stop mongoose
  __android_log_print(ANDROID_LOG_INFO, THIS_FILE, "Native Webserver: Exiting on signal %d, waiting for all threads to finish...", exit_flag);
  fflush(stdout);
  mg_stop(ctx);
  __android_log_print(ANDROID_LOG_INFO, THIS_FILE, "Native Webserver: done.\n", exit_flag);

  return 0;
}
