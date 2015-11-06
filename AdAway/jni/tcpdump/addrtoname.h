/*
 * Copyright (c) 1990, 1992, 1993, 1994, 1995, 1996, 1997
 *	The Regents of the University of California.  All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that: (1) source code distributions
 * retain the above copyright notice and this paragraph in its entirety, (2)
 * distributions including binary code include the above copyright notice and
 * this paragraph in its entirety in the documentation or other materials
 * provided with the distribution, and (3) all advertising materials mentioning
 * features or use of this software display the following acknowledgement:
 * ``This product includes software developed by the University of California,
 * Lawrence Berkeley Laboratory and its contributors.'' Neither the name of
 * the University nor the names of its contributors may be used to endorse
 * or promote products derived from this software without specific prior
 * written permission.
 * THIS SOFTWARE IS PROVIDED ``AS IS'' AND WITHOUT ANY EXPRESS OR IMPLIED
 * WARRANTIES, INCLUDING, WITHOUT LIMITATION, THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE.
 */

/* Name to address translation routines. */

enum {
    LINKADDR_ETHER,
    LINKADDR_FRELAY,
    LINKADDR_IEEE1394,
    LINKADDR_ATM
};

#define BUFSIZE 128

extern const char *linkaddr_string(netdissect_options *, const u_char *, const unsigned int, const unsigned int);
extern const char *etheraddr_string(netdissect_options *, const u_char *);
extern const char *le64addr_string(const u_char *);
extern const char *etherproto_string(u_short);
extern const char *tcpport_string(u_short);
extern const char *udpport_string(u_short);
extern const char *isonsap_string(const u_char *, register u_int);
extern const char *dnaddr_string(netdissect_options *, u_short);
extern const char *protoid_string(const u_char *);
extern const char *ipxsap_string(u_short);
extern const char *getname(netdissect_options *, const u_char *);
#ifdef INET6
extern const char *getname6(netdissect_options *, const u_char *);
#endif
extern const char *intoa(uint32_t);

extern void init_addrtoname(netdissect_options *, uint32_t, uint32_t);
extern struct hnamemem *newhnamemem(void);
#ifdef INET6
extern struct h6namemem *newh6namemem(void);
#endif
extern const char * ieee8021q_tci_string(const uint16_t);

#define ipaddr_string(ndo, p) getname(ndo, (const u_char *)(p))
#ifdef INET6
#define ip6addr_string(ndo, p) getname6(ndo, (const u_char *)(p))
#endif
