// Copyright (c) 2014 Cesanta Software
// All rights reserved
//
// This example demostrates basic use of Mongoose embedded web server.
// $Date: 2014-09-09 22:20:23 UTC $

#include <stdio.h>
#include <string.h>
#include "mongoose.h"

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

int main(void) {
  struct mg_server *server;

  // Create and configure the server
  server = mg_create_server(NULL, ev_handler);
  mg_set_option(server, "listening_port", "127.0.0.1:80,127.0.0.1:443");

  // Serve request. Hit Ctrl-C to terminate the program
  printf("Starting on port %s\n", mg_get_option(server, "listening_port"));
  for (;;) {
    mg_poll_server(server, 1000);
  }

  // Cleanup, and free server instance
  mg_destroy_server(&server);

  return 0;
}
