#include <stdio.h>
#include <string.h>
#include "mongoose.h"

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

static const char *options[] = {
  "listening_ports", "0.0.0.0:80,443",
  NULL
};

int main(void) {
  struct mg_context *ctx;

  ctx = mg_start(&callback, NULL, options);
  getchar();  // Wait until user hits "enter"
  mg_stop(ctx);

  return 0;
}