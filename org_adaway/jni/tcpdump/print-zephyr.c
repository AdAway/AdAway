/*
 * Decode and print Zephyr packets.
 *
 *	http://web.mit.edu/zephyr/doc/protocol
 *
 * Copyright (c) 2001 Nickolai Zeldovich <kolya@MIT.EDU>
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that: (1) source code
 * distributions retain the above copyright notice and this paragraph
 * in its entirety, and (2) distributions including binary code include
 * the above copyright notice and this paragraph in its entirety in
 * the documentation or other materials provided with the distribution.
 * The name of the author(s) may not be used to endorse or promote
 * products derived from this software without specific prior written
 * permission.  THIS SOFTWARE IS PROVIDED ``AS IS'' AND WITHOUT ANY
 * EXPRESS OR IMPLIED WARRANTIES, INCLUDING, WITHOUT LIMITATION, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR
 * PURPOSE.
 */

#ifndef lint
static const char rcsid[] _U_ =
    "@(#) $Header: /tcpdump/master/tcpdump/print-zephyr.c,v 1.8.2.1 2005/04/21 06:51:24 guy Exp $";
#endif

#ifdef HAVE_CONFIG_H
#include "config.h"
#endif

#include <tcpdump-stdinc.h>

#include <stdio.h>
#include <string.h>
#include <stdlib.h>

#include "interface.h"

struct z_packet {
    char *version;
    int numfields;
    int kind;
    char *uid;
    int port;
    int auth;
    int authlen;
    char *authdata;
    char *class;
    char *inst;
    char *opcode;
    char *sender;
    const char *recipient;
    char *format;
    int cksum;
    int multi;
    char *multi_uid;
    /* Other fields follow here.. */
};

enum z_packet_type {
    Z_PACKET_UNSAFE = 0,
    Z_PACKET_UNACKED,
    Z_PACKET_ACKED,
    Z_PACKET_HMACK,
    Z_PACKET_HMCTL,
    Z_PACKET_SERVACK,
    Z_PACKET_SERVNAK,
    Z_PACKET_CLIENTACK,
    Z_PACKET_STAT
};

static struct tok z_types[] = {
    { Z_PACKET_UNSAFE,		"unsafe" },
    { Z_PACKET_UNACKED,		"unacked" },
    { Z_PACKET_ACKED,		"acked" },
    { Z_PACKET_HMACK,		"hm-ack" },
    { Z_PACKET_HMCTL,		"hm-ctl" },
    { Z_PACKET_SERVACK,		"serv-ack" },
    { Z_PACKET_SERVNAK,		"serv-nak" },
    { Z_PACKET_CLIENTACK,	"client-ack" },
    { Z_PACKET_STAT,		"stat" }
};

char z_buf[256];

static char *
parse_field(char **pptr, int *len)
{
    char *s;

    if (*len <= 0 || !pptr || !*pptr)
	return NULL;
    if (*pptr > (char *) snapend)
	return NULL;

    s = *pptr;
    while (*pptr <= (char *) snapend && *len >= 0 && **pptr) {
	(*pptr)++;
	(*len)--;
    }
    (*pptr)++;
    (*len)--;
    if (*len < 0 || *pptr > (char *) snapend)
	return NULL;
    return s;
}

static const char *
z_triple(char *class, char *inst, const char *recipient)
{
    if (!*recipient)
	recipient = "*";
    snprintf(z_buf, sizeof(z_buf), "<%s,%s,%s>", class, inst, recipient);
    z_buf[sizeof(z_buf)-1] = '\0';
    return z_buf;
}

static const char *
str_to_lower(char *string)
{
    strncpy(z_buf, string, sizeof(z_buf));
    z_buf[sizeof(z_buf)-1] = '\0';

    string = z_buf;
    while (*string) {
	*string = tolower((unsigned char)(*string));
	string++;
    }

    return z_buf;
}

void
zephyr_print(const u_char *cp, int length)
{
    struct z_packet z;
    char *parse = (char *) cp;
    int parselen = length;
    char *s;
    int lose = 0;

#define PARSE_STRING				\
	s = parse_field(&parse, &parselen);	\
	if (!s) lose = 1;

#define PARSE_FIELD_INT(field)			\
	PARSE_STRING				\
	if (!lose) field = strtol(s, 0, 16);

#define PARSE_FIELD_STR(field)			\
	PARSE_STRING				\
	if (!lose) field = s;

    PARSE_FIELD_STR(z.version);
    if (lose) return;
    if (strncmp(z.version, "ZEPH", 4))
	return;

    PARSE_FIELD_INT(z.numfields);
    PARSE_FIELD_INT(z.kind);
    PARSE_FIELD_STR(z.uid);
    PARSE_FIELD_INT(z.port);
    PARSE_FIELD_INT(z.auth);
    PARSE_FIELD_INT(z.authlen);
    PARSE_FIELD_STR(z.authdata);
    PARSE_FIELD_STR(z.class);
    PARSE_FIELD_STR(z.inst);
    PARSE_FIELD_STR(z.opcode);
    PARSE_FIELD_STR(z.sender);
    PARSE_FIELD_STR(z.recipient);
    PARSE_FIELD_STR(z.format);
    PARSE_FIELD_INT(z.cksum);
    PARSE_FIELD_INT(z.multi);
    PARSE_FIELD_STR(z.multi_uid);

    if (lose) {
	printf(" [|zephyr] (%d)", length);
	return;
    }

    printf(" zephyr");
    if (strncmp(z.version+4, "0.2", 3)) {
	printf(" v%s", z.version+4);
	return;
    }

    printf(" %s", tok2str(z_types, "type %d", z.kind));
    if (z.kind == Z_PACKET_SERVACK) {
	/* Initialization to silence warnings */
	char *ackdata = NULL;
	PARSE_FIELD_STR(ackdata);
	if (!lose && strcmp(ackdata, "SENT"))
	    printf("/%s", str_to_lower(ackdata));
    }
    if (*z.sender) printf(" %s", z.sender);

    if (!strcmp(z.class, "USER_LOCATE")) {
	if (!strcmp(z.opcode, "USER_HIDE"))
	    printf(" hide");
	else if (!strcmp(z.opcode, "USER_UNHIDE"))
	    printf(" unhide");
	else
	    printf(" locate %s", z.inst);
	return;
    }

    if (!strcmp(z.class, "ZEPHYR_ADMIN")) {
	printf(" zephyr-admin %s", str_to_lower(z.opcode));
	return;
    }

    if (!strcmp(z.class, "ZEPHYR_CTL")) {
	if (!strcmp(z.inst, "CLIENT")) {
	    if (!strcmp(z.opcode, "SUBSCRIBE") ||
		!strcmp(z.opcode, "SUBSCRIBE_NODEFS") ||
		!strcmp(z.opcode, "UNSUBSCRIBE")) {

		printf(" %ssub%s", strcmp(z.opcode, "SUBSCRIBE") ? "un" : "",
				   strcmp(z.opcode, "SUBSCRIBE_NODEFS") ? "" :
								   "-nodefs");
		if (z.kind != Z_PACKET_SERVACK) {
		    /* Initialization to silence warnings */
		    char *c = NULL, *i = NULL, *r = NULL;
		    PARSE_FIELD_STR(c);
		    PARSE_FIELD_STR(i);
		    PARSE_FIELD_STR(r);
		    if (!lose) printf(" %s", z_triple(c, i, r));
		}
		return;
	    }

	    if (!strcmp(z.opcode, "GIMME")) {
		printf(" ret");
		return;
	    }

	    if (!strcmp(z.opcode, "GIMMEDEFS")) {
		printf(" gimme-defs");
		return;
	    }

	    if (!strcmp(z.opcode, "CLEARSUB")) {
		printf(" clear-subs");
		return;
	    }

	    printf(" %s", str_to_lower(z.opcode));
	    return;
	}

	if (!strcmp(z.inst, "HM")) {
	    printf(" %s", str_to_lower(z.opcode));
	    return;
	}

	if (!strcmp(z.inst, "REALM")) {
	    if (!strcmp(z.opcode, "ADD_SUBSCRIBE"))
		printf(" realm add-subs");
	    if (!strcmp(z.opcode, "REQ_SUBSCRIBE"))
		printf(" realm req-subs");
	    if (!strcmp(z.opcode, "RLM_SUBSCRIBE"))
		printf(" realm rlm-sub");
	    if (!strcmp(z.opcode, "RLM_UNSUBSCRIBE"))
		printf(" realm rlm-unsub");
	    return;
	}
    }

    if (!strcmp(z.class, "HM_CTL")) {
	printf(" hm_ctl %s", str_to_lower(z.inst));
	printf(" %s", str_to_lower(z.opcode));
	return;
    }

    if (!strcmp(z.class, "HM_STAT")) {
	if (!strcmp(z.inst, "HMST_CLIENT") && !strcmp(z.opcode, "GIMMESTATS")) {
	    printf(" get-client-stats");
	    return;
	}
    }

    if (!strcmp(z.class, "WG_CTL")) {
	printf(" wg_ctl %s", str_to_lower(z.inst));
	printf(" %s", str_to_lower(z.opcode));
	return;
    }

    if (!strcmp(z.class, "LOGIN")) {
	if (!strcmp(z.opcode, "USER_FLUSH")) {
	    printf(" flush_locs");
	    return;
	}

	if (!strcmp(z.opcode, "NONE") ||
	    !strcmp(z.opcode, "OPSTAFF") ||
	    !strcmp(z.opcode, "REALM-VISIBLE") ||
	    !strcmp(z.opcode, "REALM-ANNOUNCED") ||
	    !strcmp(z.opcode, "NET-VISIBLE") ||
	    !strcmp(z.opcode, "NET-ANNOUNCED")) {
	    printf(" set-exposure %s", str_to_lower(z.opcode));
	    return;
	}
    }

    if (!*z.recipient)
	z.recipient = "*";

    printf(" to %s", z_triple(z.class, z.inst, z.recipient));
    if (*z.opcode)
	printf(" op %s", z.opcode);
    return;
}
