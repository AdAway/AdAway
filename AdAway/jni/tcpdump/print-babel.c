/*
 * Copyright (c) 2007-2011 Grégoire Henry, Juliusz Chroboczek
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 * 1. Redistributions of source code must retain the above copyright
 *    notice, this list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright
 *    notice, this list of conditions and the following disclaimer in the
 *    documentation and/or other materials provided with the distribution.
 * 3. Neither the name of the project nor the names of its contributors
 *    may be used to endorse or promote products derived from this software
 *    without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE PROJECT AND CONTRIBUTORS ``AS IS'' AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED.  IN NO EVENT SHALL THE PROJECT OR CONTRIBUTORS BE LIABLE
 * FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 * DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS
 * OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION)
 * HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT
 * LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY
 * OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF
 * SUCH DAMAGE.
 */

#ifdef HAVE_CONFIG_H
#include "config.h"
#endif

#include <tcpdump-stdinc.h>

#include <stdio.h>
#include <string.h>

#include "addrtoname.h"
#include "interface.h"
#include "extract.h"

static void babel_print_v2(const u_char *cp, u_int length);

void
babel_print(const u_char *cp, u_int length) {
    printf("babel");

    TCHECK2(*cp, 4);

    if(cp[0] != 42) {
        printf(" malformed header");
        return;
    } else {
        printf(" %d", cp[1]);
    }

    switch(cp[1]) {
    case 2:
        babel_print_v2(cp,length);
        break;
    default:
        printf(" unknown version");
        break;
    }

    return;

 trunc:
    printf(" [|babel]");
    return;
}

/* TLVs */
#define MESSAGE_PAD1 0
#define MESSAGE_PADN 1
#define MESSAGE_ACK_REQ 2
#define MESSAGE_ACK 3
#define MESSAGE_HELLO 4
#define MESSAGE_IHU 5
#define MESSAGE_ROUTER_ID 6
#define MESSAGE_NH 7
#define MESSAGE_UPDATE 8
#define MESSAGE_REQUEST 9
#define MESSAGE_MH_REQUEST 10
#define MESSAGE_TSPC 11
#define MESSAGE_HMAC 12

/* sub-TLVs */
#define MESSAGE_SUB_PAD1 0
#define MESSAGE_SUB_PADN 1
#define MESSAGE_SUB_DIVERSITY 2

/* Diversity sub-TLV channel codes */
static const struct tok diversity_str[] = {
    { 0,   "reserved" },
    { 255, "all"      },
    { 0, NULL }
};

static const char *
format_id(const u_char *id)
{
    static char buf[25];
    snprintf(buf, 25, "%02x:%02x:%02x:%02x:%02x:%02x:%02x:%02x",
             id[0], id[1], id[2], id[3], id[4], id[5], id[6], id[7]);
    buf[24] = '\0';
    return buf;
}

static const unsigned char v4prefix[16] =
    {0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0xFF, 0xFF, 0, 0, 0, 0 };

static const char *
format_prefix(const u_char *prefix, unsigned char plen)
{
    static char buf[50];
    if(plen >= 96 && memcmp(prefix, v4prefix, 12) == 0)
        snprintf(buf, 50, "%s/%u", ipaddr_string(prefix + 12), plen - 96);
    else
#ifdef INET6
        snprintf(buf, 50, "%s/%u", ip6addr_string(prefix), plen);
#else
        snprintf(buf, 50, "IPv6 addresses not supported");
#endif
    buf[49] = '\0';
    return buf;
}

static const char *
format_address(const u_char *prefix)
{
    if(memcmp(prefix, v4prefix, 12) == 0)
        return ipaddr_string(prefix + 12);
    else
#ifdef INET6
        return ip6addr_string(prefix);
#else
        return "IPv6 addresses not supported";
#endif
}

static const char *
format_interval(const u_int16_t i)
{
    static char buf[sizeof("0000.0s")];

    if (i == 0)
        return "0.0s (bogus)";
    snprintf(buf, sizeof(buf), "%u.%us", i / 10, i % 10);
    return buf;
}

static const char *
format_interval_update(const u_int16_t i)
{
    return i == 0xFFFF ? "infinity" : format_interval(i);
}

/* Return number of octets consumed from the input buffer (not the prefix length
 * in bytes), or -1 for encoding error. */
static int
network_prefix(int ae, int plen, unsigned int omitted,
               const unsigned char *p, const unsigned char *dp,
               unsigned int len, unsigned char *p_r)
{
    unsigned pb;
    unsigned char prefix[16];
    int consumed = 0;

    if(plen >= 0)
        pb = (plen + 7) / 8;
    else if(ae == 1)
        pb = 4;
    else
        pb = 16;

    if(pb > 16)
        return -1;

    memset(prefix, 0, 16);

    switch(ae) {
    case 0: break;
    case 1:
        if(omitted > 4 || pb > 4 || (pb > omitted && len < pb - omitted))
            return -1;
        memcpy(prefix, v4prefix, 12);
        if(omitted) {
            if (dp == NULL) return -1;
            memcpy(prefix, dp, 12 + omitted);
        }
        if(pb > omitted) {
            memcpy(prefix + 12 + omitted, p, pb - omitted);
            consumed = pb - omitted;
        }
        break;
    case 2:
        if(omitted > 16 || (pb > omitted && len < pb - omitted))
            return -1;
        if(omitted) {
            if (dp == NULL) return -1;
            memcpy(prefix, dp, omitted);
        }
        if(pb > omitted) {
            memcpy(prefix + omitted, p, pb - omitted);
            consumed = pb - omitted;
        }
        break;
    case 3:
        if(pb > 8 && len < pb - 8) return -1;
        prefix[0] = 0xfe;
        prefix[1] = 0x80;
        if(pb > 8) {
            memcpy(prefix + 8, p, pb - 8);
            consumed = pb - 8;
        }
        break;
    default:
        return -1;
    }

    memcpy(p_r, prefix, 16);
    return consumed;
}

static int
network_address(int ae, const unsigned char *a, unsigned int len,
                unsigned char *a_r)
{
    return network_prefix(ae, -1, 0, a, NULL, len, a_r);
}

/*
 * Sub-TLVs consume the "extra data" of Babel TLVs (see Section 4.3 of RFC6126),
 * their encoding is similar to the encoding of TLVs, but the type namespace is
 * different:
 *
 * o Type 0 stands for Pad1 sub-TLV with the same encoding as the Pad1 TLV.
 * o Type 1 stands for PadN sub-TLV with the same encoding as the PadN TLV.
 * o Type 2 stands for Diversity sub-TLV, which propagates diversity routing
 *   data. Its body is a variable-length sequence of 8-bit unsigned integers,
 *   each representing per-hop number of interferring radio channel for the
 *   prefix. Channel 0 is invalid and must not be used in the sub-TLV, channel
 *   255 interferes with any other channel.
 *
 * Sub-TLV types 0 and 1 are valid for any TLV type, whether sub-TLV type 2 is
 * only valid for TLV type 8 (Update). Note that within an Update TLV a missing
 * Diversity sub-TLV is not the same as a Diversity sub-TLV with an empty body.
 * The former would mean a lack of any claims about the interference, and the
 * latter would state that interference is definitely absent. */
static void
subtlvs_print(const u_char *cp, const u_char *ep, const uint8_t tlv_type) {
    uint8_t subtype, sublen;
    const char *sep;

    while (cp < ep) {
        subtype = *cp++;
        if(subtype == MESSAGE_SUB_PAD1) {
            printf(" sub-pad1");
            continue;
        }
        if(cp == ep)
            goto corrupt;
        sublen = *cp++;
        if(cp + sublen > ep)
            goto corrupt;

        switch(subtype) {
        case MESSAGE_SUB_PADN:
            printf(" sub-padn");
            cp += sublen;
            break;
        case MESSAGE_SUB_DIVERSITY:
            printf(" sub-diversity");
            if (sublen == 0) {
                printf(" empty");
                break;
            }
            sep = " ";
            while(sublen--) {
                printf("%s%s", sep, tok2str(diversity_str, "%u", *cp++));
                sep = "-";
            }
            if(tlv_type != MESSAGE_UPDATE)
                printf(" (bogus)");
            break;
        default:
            printf(" sub-unknown-0x%02x", subtype);
            cp += sublen;
        } /* switch */
    } /* while */
    return;

 corrupt:
    printf(" (corrupt)");
}

#define ICHECK(i, l) \
	if ((i) + (l) > bodylen || (i) + (l) > length) goto corrupt;

static void
babel_print_v2(const u_char *cp, u_int length) {
    u_int i;
    u_short bodylen;
    u_char v4_prefix[16] =
        {0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0xFF, 0xFF, 0, 0, 0, 0 };
    u_char v6_prefix[16] = {0};

    TCHECK2(*cp, 4);
    if (length < 4)
        goto corrupt;
    bodylen = EXTRACT_16BITS(cp + 2);
    printf(" (%u)", bodylen);

    /* Process the TLVs in the body */
    i = 0;
    while(i < bodylen) {
        const u_char *message;
        u_int type, len;

        message = cp + 4 + i;

        TCHECK2(*message, 1);
        if((type = message[0]) == MESSAGE_PAD1) {
            printf(vflag ? "\n\tPad 1" : " pad1");
            i += 1;
            continue;
        }

        TCHECK2(*message, 2);
        ICHECK(i, 2);
        len = message[1];

        TCHECK2(*message, 2 + len);
        ICHECK(i, 2 + len);

        switch(type) {
        case MESSAGE_PADN: {
            if(!vflag)
                printf(" padN");
            else
                printf("\n\tPad %d", len + 2);
        }
            break;

        case MESSAGE_ACK_REQ: {
            u_short nonce, interval;
            if(!vflag)
                printf(" ack-req");
            else {
                printf("\n\tAcknowledgment Request ");
                if(len < 6) goto corrupt;
                nonce = EXTRACT_16BITS(message + 4);
                interval = EXTRACT_16BITS(message + 6);
                printf("%04x %s", nonce, format_interval(interval));
            }
        }
            break;

        case MESSAGE_ACK: {
            u_short nonce;
            if(!vflag)
                printf(" ack");
            else {
                printf("\n\tAcknowledgment ");
                if(len < 2) goto corrupt;
                nonce = EXTRACT_16BITS(message + 2);
                printf("%04x", nonce);
            }
        }
            break;

        case MESSAGE_HELLO:  {
            u_short seqno, interval;
            if(!vflag)
                printf(" hello");
            else {
                printf("\n\tHello ");
                if(len < 6) goto corrupt;
                seqno = EXTRACT_16BITS(message + 4);
                interval = EXTRACT_16BITS(message + 6);
                printf("seqno %u interval %s", seqno, format_interval(interval));
            }
        }
            break;

        case MESSAGE_IHU: {
            unsigned short txcost, interval;
            if(!vflag)
                printf(" ihu");
            else {
                u_char address[16];
                int rc;
                printf("\n\tIHU ");
                if(len < 6) goto corrupt;
                txcost = EXTRACT_16BITS(message + 4);
                interval = EXTRACT_16BITS(message + 6);
                rc = network_address(message[2], message + 8, len - 6, address);
                if(rc < 0) { printf("[|babel]"); break; }
                printf("%s txcost %u interval %s",
                       format_address(address), txcost, format_interval(interval));
            }
        }
            break;

        case MESSAGE_ROUTER_ID: {
            if(!vflag)
                printf(" router-id");
            else {
                printf("\n\tRouter Id");
                if(len < 10) goto corrupt;
                printf(" %s", format_id(message + 4));
            }
        }
            break;

        case MESSAGE_NH: {
            if(!vflag)
                printf(" nh");
            else {
                int rc;
                u_char nh[16];
                printf("\n\tNext Hop");
                if(len < 2) goto corrupt;
                rc = network_address(message[2], message + 4, len - 2, nh);
                if(rc < 0) goto corrupt;
                printf(" %s", format_address(nh));
            }
        }
            break;

        case MESSAGE_UPDATE: {
            if(!vflag) {
                printf(" update");
                if(len < 1)
                    printf("/truncated");
                else
                    printf("%s%s%s",
                           (message[3] & 0x80) ? "/prefix": "",
                           (message[3] & 0x40) ? "/id" : "",
                           (message[3] & 0x3f) ? "/unknown" : "");
            } else {
                u_short interval, seqno, metric;
                u_char plen;
                int rc;
                u_char prefix[16];
                printf("\n\tUpdate");
                if(len < 10) goto corrupt;
                plen = message[4] + (message[2] == 1 ? 96 : 0);
                rc = network_prefix(message[2], message[4], message[5],
                                    message + 12,
                                    message[2] == 1 ? v4_prefix : v6_prefix,
                                    len - 10, prefix);
                if(rc < 0) goto corrupt;
                interval = EXTRACT_16BITS(message + 6);
                seqno = EXTRACT_16BITS(message + 8);
                metric = EXTRACT_16BITS(message + 10);
                printf("%s%s%s %s metric %u seqno %u interval %s",
                       (message[3] & 0x80) ? "/prefix": "",
                       (message[3] & 0x40) ? "/id" : "",
                       (message[3] & 0x3f) ? "/unknown" : "",
                       format_prefix(prefix, plen),
                       metric, seqno, format_interval_update(interval));
                if(message[3] & 0x80) {
                    if(message[2] == 1)
                        memcpy(v4_prefix, prefix, 16);
                    else
                        memcpy(v6_prefix, prefix, 16);
                }
                /* extra data? */
                if((u_int)rc < len - 10)
                    subtlvs_print(message + 12 + rc, message + 2 + len, type);
            }
        }
            break;

        case MESSAGE_REQUEST: {
            if(!vflag)
                printf(" request");
            else {
                int rc;
                u_char prefix[16], plen;
                printf("\n\tRequest ");
                if(len < 2) goto corrupt;
                plen = message[3] + (message[2] == 1 ? 96 : 0);
                rc = network_prefix(message[2], message[3], 0,
                                    message + 4, NULL, len - 2, prefix);
                if(rc < 0) goto corrupt;
                plen = message[3] + (message[2] == 1 ? 96 : 0);
                printf("for %s",
                       message[2] == 0 ? "any" : format_prefix(prefix, plen));
            }
        }
            break;

        case MESSAGE_MH_REQUEST : {
            if(!vflag)
                printf(" mh-request");
            else {
                int rc;
                u_short seqno;
                u_char prefix[16], plen;
                printf("\n\tMH-Request ");
                if(len < 14) goto corrupt;
                seqno = EXTRACT_16BITS(message + 4);
                rc = network_prefix(message[2], message[3], 0,
                                    message + 16, NULL, len - 14, prefix);
                if(rc < 0) goto corrupt;
                plen = message[3] + (message[2] == 1 ? 96 : 0);
                printf("(%u hops) for %s seqno %u id %s",
                       message[6], format_prefix(prefix, plen),
                       seqno, format_id(message + 8));
            }
        }
            break;
        case MESSAGE_TSPC :
            if(!vflag)
                printf(" tspc");
            else {
                printf("\n\tTS/PC ");
                if(len < 6) goto corrupt;
                printf("timestamp %u packetcounter %u", EXTRACT_32BITS (message + 4),
                       EXTRACT_16BITS(message + 2));
            }
            break;
        case MESSAGE_HMAC : {
            if(!vflag)
                printf(" hmac");
            else {
                unsigned j;
                printf("\n\tHMAC ");
                if(len < 18) goto corrupt;
                printf("key-id %u digest-%u ", EXTRACT_16BITS(message + 2), len - 2);
                for (j = 0; j < len - 2; j++)
                    printf ("%02X", message[4 + j]);
            }
        }
            break;
        default:
            if(!vflag)
                printf(" unknown");
            else
                printf("\n\tUnknown message type %d", type);
        }
        i += len + 2;
    }
    return;

 trunc:
    printf(" [|babel]");
    return;

 corrupt:
    printf(" (corrupt)");
    return;
}
