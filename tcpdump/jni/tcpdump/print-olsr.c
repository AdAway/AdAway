/*
 * Copyright (c) 1998-2007 The TCPDUMP project
 * Copyright (c) 2009  Florian Forster
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that: (1) source code
 * distributions retain the above copyright notice and this paragraph
 * in its entirety, and (2) distributions including binary code include
 * the above copyright notice and this paragraph in its entirety in
 * the documentation or other materials provided with the distribution.
 * THIS SOFTWARE IS PROVIDED ``AS IS'' AND
 * WITHOUT ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, WITHOUT
 * LIMITATION, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS
 * FOR A PARTICULAR PURPOSE.
 *
 * Optimized Link State Protocl (OLSR) as per rfc3626
 *
 * Original code by Hannes Gredler <hannes@juniper.net>
 * IPv6 additions by Florian Forster <octo at verplant.org>
 */

#define NETDISSECT_REWORKED
#ifdef HAVE_CONFIG_H
#include "config.h"
#endif

#include <tcpdump-stdinc.h>

#include "interface.h"
#include "addrtoname.h"
#include "extract.h"

/*
 * RFC 3626 common header
 *
 *  0                   1                   2                   3
 *  0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 * |         Packet Length         |    Packet Sequence Number     |
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 * |  Message Type |     Vtime     |         Message Size          |
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 * |                      Originator Address                       |
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 * |  Time To Live |   Hop Count   |    Message Sequence Number    |
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 * |                                                               |
 * :                            MESSAGE                            :
 * |                                                               |
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 * |  Message Type |     Vtime     |         Message Size          |
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 * |                      Originator Address                       |
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 * |  Time To Live |   Hop Count   |    Message Sequence Number    |
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 * |                                                               |
 * :                            MESSAGE                            :
 * |                                                               |
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 * :                                                               :
 */

struct olsr_common {
    uint8_t packet_len[2];
    uint8_t packet_seq[2];
};

#define OLSR_HELLO_MSG         1 /* rfc3626 */
#define OLSR_TC_MSG            2 /* rfc3626 */
#define OLSR_MID_MSG           3 /* rfc3626 */
#define OLSR_HNA_MSG           4 /* rfc3626 */
#define OLSR_POWERINFO_MSG   128
#define OLSR_NAMESERVICE_MSG 130
#define OLSR_HELLO_LQ_MSG    201 /* LQ extensions olsr.org */
#define OLSR_TC_LQ_MSG       202 /* LQ extensions olsr.org */

static const struct tok olsr_msg_values[] = {
    { OLSR_HELLO_MSG, "Hello" },
    { OLSR_TC_MSG, "TC" },
    { OLSR_MID_MSG, "MID" },
    { OLSR_HNA_MSG, "HNA" },
    { OLSR_POWERINFO_MSG, "Powerinfo" },
    { OLSR_NAMESERVICE_MSG, "Nameservice" },
    { OLSR_HELLO_LQ_MSG, "Hello-LQ" },
    { OLSR_TC_LQ_MSG, "TC-LQ" },
    { 0, NULL}
};

struct olsr_msg4 {
    uint8_t msg_type;
    uint8_t vtime;
    uint8_t msg_len[2];
    uint8_t originator[4];
    uint8_t ttl;
    uint8_t hopcount;
    uint8_t msg_seq[2];
};

struct olsr_msg6 {
    uint8_t msg_type;
    uint8_t vtime;
    uint8_t msg_len[2];
    uint8_t originator[16];
    uint8_t ttl;
    uint8_t hopcount;
    uint8_t msg_seq[2];
};

struct olsr_hello {
    uint8_t res[2];
    uint8_t htime;
    uint8_t will;
};

struct olsr_hello_link {
    uint8_t link_code;
    uint8_t res;
    uint8_t len[2];
};

struct olsr_tc {
    uint8_t ans_seq[2];
    uint8_t res[2];
};

struct olsr_hna4 {
    uint8_t network[4];
    uint8_t mask[4];
};

struct olsr_hna6 {
    uint8_t network[16];
    uint8_t mask[16];
};


#define OLSR_EXTRACT_LINK_TYPE(link_code) (link_code & 0x3)
#define OLSR_EXTRACT_NEIGHBOR_TYPE(link_code) (link_code >> 2)

static const struct tok olsr_link_type_values[] = {
    { 0, "Unspecified" },
    { 1, "Asymmetric" },
    { 2, "Symmetric" },
    { 3, "Lost" },
    { 0, NULL}
};

static const struct tok olsr_neighbor_type_values[] = {
    { 0, "Not-Neighbor" },
    { 1, "Symmetric" },
    { 2, "Symmetric-MPR" },
    { 0, NULL}
};

struct olsr_lq_neighbor4 {
    uint8_t neighbor[4];
    uint8_t link_quality;
    uint8_t neighbor_link_quality;
    uint8_t res[2];
};

struct olsr_lq_neighbor6 {
    uint8_t neighbor[16];
    uint8_t link_quality;
    uint8_t neighbor_link_quality;
    uint8_t res[2];
};

/*
 * macro to convert the 8-bit mantissa/exponent to a double float
 * taken from olsr.org.
 */
#define VTIME_SCALE_FACTOR    0.0625
#define ME_TO_DOUBLE(me) \
  (double)(VTIME_SCALE_FACTOR*(1+(double)(me>>4)/16)*(double)(1<<(me&0x0F)))

/*
 * print a neighbor list with LQ extensions.
 */
static int
olsr_print_lq_neighbor4(netdissect_options *ndo,
                        const u_char *msg_data, u_int hello_len)
{
    struct olsr_lq_neighbor4 *lq_neighbor;

    while (hello_len >= sizeof(struct olsr_lq_neighbor4)) {

        lq_neighbor = (struct olsr_lq_neighbor4 *)msg_data;
        if (!ND_TTEST(*lq_neighbor))
            return (-1);

        ND_PRINT((ndo, "\n\t      neighbor %s, link-quality %.2lf%%"
               ", neighbor-link-quality %.2lf%%",
               ipaddr_string(ndo, lq_neighbor->neighbor),
               ((double)lq_neighbor->link_quality/2.55),
               ((double)lq_neighbor->neighbor_link_quality/2.55)));

        msg_data += sizeof(struct olsr_lq_neighbor4);
        hello_len -= sizeof(struct olsr_lq_neighbor4);
    }
    return (0);
}

#if INET6
static int
olsr_print_lq_neighbor6(netdissect_options *ndo,
                        const u_char *msg_data, u_int hello_len)
{
    struct olsr_lq_neighbor6 *lq_neighbor;

    while (hello_len >= sizeof(struct olsr_lq_neighbor6)) {

        lq_neighbor = (struct olsr_lq_neighbor6 *)msg_data;
        if (!ND_TTEST(*lq_neighbor))
            return (-1);

        ND_PRINT((ndo, "\n\t      neighbor %s, link-quality %.2lf%%"
               ", neighbor-link-quality %.2lf%%",
               ip6addr_string(ndo, lq_neighbor->neighbor),
               ((double)lq_neighbor->link_quality/2.55),
               ((double)lq_neighbor->neighbor_link_quality/2.55)));

        msg_data += sizeof(struct olsr_lq_neighbor6);
        hello_len -= sizeof(struct olsr_lq_neighbor6);
    }
    return (0);
}
#endif /* INET6 */

/*
 * print a neighbor list.
 */
static int
olsr_print_neighbor(netdissect_options *ndo,
                    const u_char *msg_data, u_int hello_len)
{
    int neighbor;

    ND_PRINT((ndo, "\n\t      neighbor\n\t\t"));
    neighbor = 1;

    while (hello_len >= sizeof(struct in_addr)) {

        if (!ND_TTEST2(*msg_data, sizeof(struct in_addr)))
            return (-1);
        /* print 4 neighbors per line */

        ND_PRINT((ndo, "%s%s", ipaddr_string(ndo, msg_data),
               neighbor % 4 == 0 ? "\n\t\t" : " "));

        msg_data += sizeof(struct in_addr);
        hello_len -= sizeof(struct in_addr);
    }
    return (0);
}


void
olsr_print(netdissect_options *ndo,
           const u_char *pptr, u_int length, int is_ipv6)
{
    union {
        const struct olsr_common *common;
        const struct olsr_msg4 *msg4;
        const struct olsr_msg6 *msg6;
        const struct olsr_hello *hello;
        const struct olsr_hello_link *hello_link;
        const struct olsr_tc *tc;
        const struct olsr_hna4 *hna;
    } ptr;

    u_int msg_type, msg_len, msg_tlen, hello_len;
    uint16_t name_entry_type, name_entry_len;
    u_int name_entry_padding;
    uint8_t link_type, neighbor_type;
    const u_char *tptr, *msg_data;

    tptr = pptr;

    if (length < sizeof(struct olsr_common)) {
        goto trunc;
    }

    ND_TCHECK2(*tptr, sizeof(struct olsr_common));

    ptr.common = (struct olsr_common *)tptr;
    length = min(length, EXTRACT_16BITS(ptr.common->packet_len));

    ND_PRINT((ndo, "OLSRv%i, seq 0x%04x, length %u",
            (is_ipv6 == 0) ? 4 : 6,
            EXTRACT_16BITS(ptr.common->packet_seq),
            length));

    tptr += sizeof(struct olsr_common);

    /*
     * In non-verbose mode, just print version.
     */
    if (ndo->ndo_vflag < 1) {
        return;
    }

    while (tptr < (pptr+length)) {
        union
        {
            struct olsr_msg4 *v4;
            struct olsr_msg6 *v6;
        } msgptr;
        int msg_len_valid = 0;

        ND_TCHECK2(*tptr, sizeof(struct olsr_msg4));

#if INET6
        if (is_ipv6)
        {
            msgptr.v6 = (struct olsr_msg6 *) tptr;
            msg_type = msgptr.v6->msg_type;
            msg_len = EXTRACT_16BITS(msgptr.v6->msg_len);
            if ((msg_len >= sizeof (struct olsr_msg6))
                    && (msg_len <= length))
                msg_len_valid = 1;

            /* infinite loop check */
            if (msg_type == 0 || msg_len == 0) {
                return;
            }

            ND_PRINT((ndo, "\n\t%s Message (%#04x), originator %s, ttl %u, hop %u"
                    "\n\t  vtime %.3lfs, msg-seq 0x%04x, length %u%s",
                    tok2str(olsr_msg_values, "Unknown", msg_type),
                    msg_type, ip6addr_string(ndo, msgptr.v6->originator),
                    msgptr.v6->ttl,
                    msgptr.v6->hopcount,
                    ME_TO_DOUBLE(msgptr.v6->vtime),
                    EXTRACT_16BITS(msgptr.v6->msg_seq),
                    msg_len, (msg_len_valid == 0) ? " (invalid)" : ""));
            if (!msg_len_valid) {
                return;
            }

            msg_tlen = msg_len - sizeof(struct olsr_msg6);
            msg_data = tptr + sizeof(struct olsr_msg6);
        }
        else /* (!is_ipv6) */
#endif /* INET6 */
        {
            msgptr.v4 = (struct olsr_msg4 *) tptr;
            msg_type = msgptr.v4->msg_type;
            msg_len = EXTRACT_16BITS(msgptr.v4->msg_len);
            if ((msg_len >= sizeof (struct olsr_msg4))
                    && (msg_len <= length))
                msg_len_valid = 1;

            /* infinite loop check */
            if (msg_type == 0 || msg_len == 0) {
                return;
            }

            ND_PRINT((ndo, "\n\t%s Message (%#04x), originator %s, ttl %u, hop %u"
                    "\n\t  vtime %.3lfs, msg-seq 0x%04x, length %u%s",
                    tok2str(olsr_msg_values, "Unknown", msg_type),
                    msg_type, ipaddr_string(ndo, msgptr.v4->originator),
                    msgptr.v4->ttl,
                    msgptr.v4->hopcount,
                    ME_TO_DOUBLE(msgptr.v4->vtime),
                    EXTRACT_16BITS(msgptr.v4->msg_seq),
                    msg_len, (msg_len_valid == 0) ? " (invalid)" : ""));
            if (!msg_len_valid) {
                return;
            }

            msg_tlen = msg_len - sizeof(struct olsr_msg4);
            msg_data = tptr + sizeof(struct olsr_msg4);
        }

        switch (msg_type) {
        case OLSR_HELLO_MSG:
        case OLSR_HELLO_LQ_MSG:
            if (msg_tlen < sizeof(struct olsr_hello))
                goto trunc;
            ND_TCHECK2(*msg_data, sizeof(struct olsr_hello));

            ptr.hello = (struct olsr_hello *)msg_data;
            ND_PRINT((ndo, "\n\t  hello-time %.3lfs, MPR willingness %u",
                   ME_TO_DOUBLE(ptr.hello->htime), ptr.hello->will));
            msg_data += sizeof(struct olsr_hello);
            msg_tlen -= sizeof(struct olsr_hello);

            while (msg_tlen >= sizeof(struct olsr_hello_link)) {
                int hello_len_valid = 0;

                /*
                 * link-type.
                 */
                ND_TCHECK2(*msg_data, sizeof(struct olsr_hello_link));

                ptr.hello_link = (struct olsr_hello_link *)msg_data;

                hello_len = EXTRACT_16BITS(ptr.hello_link->len);
                link_type = OLSR_EXTRACT_LINK_TYPE(ptr.hello_link->link_code);
                neighbor_type = OLSR_EXTRACT_NEIGHBOR_TYPE(ptr.hello_link->link_code);

                if ((hello_len <= msg_tlen)
                        && (hello_len >= sizeof(struct olsr_hello_link)))
                    hello_len_valid = 1;

                ND_PRINT((ndo, "\n\t    link-type %s, neighbor-type %s, len %u%s",
                       tok2str(olsr_link_type_values, "Unknown", link_type),
                       tok2str(olsr_neighbor_type_values, "Unknown", neighbor_type),
                       hello_len,
                       (hello_len_valid == 0) ? " (invalid)" : ""));

                if (hello_len_valid == 0)
                    break;

                msg_data += sizeof(struct olsr_hello_link);
                msg_tlen -= sizeof(struct olsr_hello_link);
                hello_len -= sizeof(struct olsr_hello_link);

                ND_TCHECK2(*msg_data, hello_len);
                if (msg_type == OLSR_HELLO_MSG) {
                    if (olsr_print_neighbor(ndo, msg_data, hello_len) == -1)
                        goto trunc;
                } else {
#if INET6
                    if (is_ipv6) {
                        if (olsr_print_lq_neighbor6(ndo, msg_data, hello_len) == -1)
                            goto trunc;
                    } else
#endif
                    {
                        if (olsr_print_lq_neighbor4(ndo, msg_data, hello_len) == -1)
                            goto trunc;
                    }
                }

                msg_data += hello_len;
                msg_tlen -= hello_len;
            }
            break;

        case OLSR_TC_MSG:
        case OLSR_TC_LQ_MSG:
            if (msg_tlen < sizeof(struct olsr_tc))
                goto trunc;
            ND_TCHECK2(*msg_data, sizeof(struct olsr_tc));

            ptr.tc = (struct olsr_tc *)msg_data;
            ND_PRINT((ndo, "\n\t    advertised neighbor seq 0x%04x",
                   EXTRACT_16BITS(ptr.tc->ans_seq)));
            msg_data += sizeof(struct olsr_tc);
            msg_tlen -= sizeof(struct olsr_tc);

            if (msg_type == OLSR_TC_MSG) {
                if (olsr_print_neighbor(ndo, msg_data, msg_tlen) == -1)
                    goto trunc;
            } else {
#if INET6
                if (is_ipv6) {
                    if (olsr_print_lq_neighbor6(ndo, msg_data, msg_tlen) == -1)
                        goto trunc;
                } else
#endif
                {
                    if (olsr_print_lq_neighbor4(ndo, msg_data, msg_tlen) == -1)
                        goto trunc;
                }
            }
            break;

        case OLSR_MID_MSG:
        {
            size_t addr_size = sizeof(struct in_addr);

#if INET6
            if (is_ipv6)
                addr_size = sizeof(struct in6_addr);
#endif

            while (msg_tlen >= addr_size) {
                ND_TCHECK2(*msg_data, addr_size);
#if INET6
                ND_PRINT((ndo, "\n\t  interface address %s",
                        is_ipv6 ? ip6addr_string(ndo, msg_data) :
                        ipaddr_string(ndo, msg_data)));
#else
                ND_PRINT((ndo, "\n\t  interface address %s",
                        ipaddr_string(ndo, msg_data)));
#endif

                msg_data += addr_size;
                msg_tlen -= addr_size;
            }
            break;
        }

        case OLSR_HNA_MSG:
            ND_PRINT((ndo, "\n\t  Advertised networks (total %u)",
                    (unsigned int) (msg_tlen / sizeof(struct olsr_hna6))));
#if INET6
            if (is_ipv6)
            {
                int i = 0;
                while (msg_tlen >= sizeof(struct olsr_hna6)) {
                    struct olsr_hna6 *hna6;

                    ND_TCHECK2(*msg_data, sizeof(struct olsr_hna6));

                    hna6 = (struct olsr_hna6 *)msg_data;

                    ND_PRINT((ndo, "\n\t    #%i: %s/%u",
                            i, ip6addr_string(ndo, hna6->network),
                            mask62plen (hna6->mask)));

                    msg_data += sizeof(struct olsr_hna6);
                    msg_tlen -= sizeof(struct olsr_hna6);
                }
            }
            else
#endif
            {
                int col = 0;
                while (msg_tlen >= sizeof(struct olsr_hna4)) {
                    ND_TCHECK2(*msg_data, sizeof(struct olsr_hna4));

                    ptr.hna = (struct olsr_hna4 *)msg_data;

                    /* print 4 prefixes per line */
                    ND_PRINT((ndo, "%s%s/%u",
                            col == 0 ? "\n\t    " : ", ",
                            ipaddr_string(ndo, ptr.hna->network),
                            mask2plen(EXTRACT_32BITS(ptr.hna->mask))));

                    msg_data += sizeof(struct olsr_hna4);
                    msg_tlen -= sizeof(struct olsr_hna4);

                    col = (col + 1) % 4;
                }
            }
            break;

        case OLSR_NAMESERVICE_MSG:
        {
            u_int name_entries = EXTRACT_16BITS(msg_data+2);
            u_int addr_size = 4;
            int name_entries_valid = 0;
            u_int i;

            if (is_ipv6)
                addr_size = 16;

            if ((name_entries > 0)
                    && ((name_entries * (4 + addr_size)) <= msg_tlen))
                name_entries_valid = 1;

            if (msg_tlen < 4)
                goto trunc;
            ND_TCHECK2(*msg_data, 4);

            ND_PRINT((ndo, "\n\t  Version %u, Entries %u%s",
                   EXTRACT_16BITS(msg_data),
                   name_entries, (name_entries_valid == 0) ? " (invalid)" : ""));

            if (name_entries_valid == 0)
                break;

            msg_data += 4;
            msg_tlen -= 4;

            for (i = 0; i < name_entries; i++) {
                int name_entry_len_valid = 0;

                if (msg_tlen < 4)
                    break;
                ND_TCHECK2(*msg_data, 4);

                name_entry_type = EXTRACT_16BITS(msg_data);
                name_entry_len = EXTRACT_16BITS(msg_data+2);

                msg_data += 4;
                msg_tlen -= 4;

                if ((name_entry_len > 0) && ((addr_size + name_entry_len) <= msg_tlen))
                    name_entry_len_valid = 1;

                ND_PRINT((ndo, "\n\t    #%u: type %#06x, length %u%s",
                        (unsigned int) i, name_entry_type,
                        name_entry_len, (name_entry_len_valid == 0) ? " (invalid)" : ""));

                if (name_entry_len_valid == 0)
                    break;

                /* 32-bit alignment */
                name_entry_padding = 0;
                if (name_entry_len%4 != 0)
                    name_entry_padding = 4-(name_entry_len%4);

                if (msg_tlen < addr_size + name_entry_len + name_entry_padding)
                    goto trunc;

                ND_TCHECK2(*msg_data, addr_size + name_entry_len + name_entry_padding);

#if INET6
                if (is_ipv6)
                    ND_PRINT((ndo, ", address %s, name \"",
                            ip6addr_string(ndo, msg_data)));
                else
#endif
                    ND_PRINT((ndo, ", address %s, name \"",
                            ipaddr_string(ndo, msg_data)));
                (void)fn_printn(ndo, msg_data + addr_size, name_entry_len, NULL);
                ND_PRINT((ndo, "\""));

                msg_data += addr_size + name_entry_len + name_entry_padding;
                msg_tlen -= addr_size + name_entry_len + name_entry_padding;
            } /* for (i = 0; i < name_entries; i++) */
            break;
        } /* case OLSR_NAMESERVICE_MSG */

            /*
             * FIXME those are the defined messages that lack a decoder
             * you are welcome to contribute code ;-)
             */
        case OLSR_POWERINFO_MSG:
        default:
            print_unknown_data(ndo, msg_data, "\n\t    ", msg_tlen);
            break;
        } /* switch (msg_type) */
        tptr += msg_len;
    } /* while (tptr < (pptr+length)) */

    return;

 trunc:
    ND_PRINT((ndo, "[|olsr]"));
}

/*
 * Local Variables:
 * c-style: whitesmith
 * c-basic-offset: 4
 * End:
 */
