/*
 * Copyright (c) 2014 Cesanta Software Limited
 * All rights reserved
 */

/*
 * Try it out with:
 * $ dig -t A www.google.com -4 @localhost -p 5533
 */

#include "../../mongoose.h"

#include <stdio.h>

static int s_exit_flag = 0;
static in_addr_t s_our_ip_addr;
static const char *s_listening_addr = "udp://:5533";

static void ev_handler(struct mg_connection *nc, int ev, void *ev_data) {
  struct mg_dmg_message *msg;
  struct mg_dmg_resource_record *rr;
  struct mg_dmg_reply reply;
  int i;

  switch (ev) {
    case MG_DNS_MESSAGE:
      msg = (struct mg_dmg_message *) ev_data;
      reply = mg_dmg_create_reply(&nc->send_mbuf, msg);

      for (i = 0; i < msg->num_questions; i++) {
        rr = &msg->questions[i];
        if (rr->rtype == MG_DNS_A_RECORD) {
          mg_dmg_reply_record(&reply, rr, NULL, rr->rtype, 3600,
                              &s_our_ip_addr, 4);
        }
      }

      /*
       * We don't set the error flag even if there were no answers
       * maching the MG_DNS_A_RECORD query type.
       * This indicates that we have (syntetic) answers for MG_DNS_A_RECORD.
       * See http://goo.gl/QWvufr for a distinction between NXDOMAIN and NODATA.
       */

      mg_dmg_send_reply(nc, &reply);
      break;
  }
}

int main(int argc, char *argv[]) {
  struct mg_mgr mgr;
  struct mg_connection *nc;
  int i;

  mg_mgr_init(&mgr, NULL);
  s_our_ip_addr = inet_addr("127.0.0.1");

  /* Parse command line arguments */
  for (i = 1; i < argc; i++) {
    if (strcmp(argv[i], "-D") == 0) {
      mgr.hexdump_file = argv[++i];
    } else if (strcmp(argv[i], "-l") == 0 && i + 1 < argc) {
      s_listening_addr = argv[++i];
    } else {
      s_our_ip_addr = inet_addr(argv[i]);
    }
  }

  fprintf(stderr, "Listening on '%s'\n", s_listening_addr);
  if ((nc = mg_bind(&mgr, s_listening_addr, ev_handler)) == NULL) {
    fprintf(stderr, "cannot bind to socket\n");
    exit(1);
  }
  mg_set_protocol_dns(nc);

  while (s_exit_flag == 0) {
    mg_mgr_poll(&mgr, 1000);
  }
  mg_mgr_free(&mgr);

  return 0;
}
