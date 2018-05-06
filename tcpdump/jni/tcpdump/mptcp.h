/**
 * Copyright (c) 2012
 *
 * Gregory Detal <gregory.detal@uclouvain.be>
 * Christoph Paasch <christoph.paasch@uclouvain.be>
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 * 1. Redistributions of source code must retain the above copyright
 *    notice, this list of conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright
 *    notice, this list of conditions and the following disclaimer in the
 *    documentation and/or other materials provided with the distribution.
 *
 * 3. Neither the name of the University nor of the Laboratory may be used
 *    to endorse or promote products derived from this software without
 *    specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE REGENTS AND CONTRIBUTORS ``AS IS'' AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED.  IN NO EVENT SHALL THE REGENTS OR CONTRIBUTORS BE LIABLE
 * FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 * DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS
 * OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION)
 * HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT
 * LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY
 * OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF
 * SUCH DAMAGE.
 */

#define MPTCP_SUB_CAPABLE       0x0
#define MPTCP_SUB_JOIN          0x1
#define MPTCP_SUB_DSS           0x2
#define MPTCP_SUB_ADD_ADDR      0x3
#define MPTCP_SUB_REMOVE_ADDR   0x4
#define MPTCP_SUB_PRIO          0x5
#define MPTCP_SUB_FAIL          0x6
#define MPTCP_SUB_FCLOSE        0x7


struct mptcp_option {
        u_int8_t        kind;
        u_int8_t        len;
        u_int8_t        sub_etc;        /* subtype upper 4 bits, other stuff lower 4 bits */
};

#define MPTCP_OPT_SUBTYPE(sub_etc)      (((sub_etc) >> 4) & 0xF)

struct mp_capable {
        u_int8_t        kind;
        u_int8_t        len;
        u_int8_t        sub_ver;
        u_int8_t        flags;
        u_int8_t        sender_key[8];
        u_int8_t        receiver_key[8];
};

#define MP_CAPABLE_OPT_VERSION(sub_ver) (((sub_ver) >> 0) & 0xF)
#define MP_CAPABLE_C                    0x80
#define MP_CAPABLE_S                    0x01

struct mp_join {
        u_int8_t        kind;
        u_int8_t        len;
        u_int8_t        sub_b;
        u_int8_t        addr_id;
        union {
                struct {
                        u_int8_t         token[4];
                        u_int8_t         nonce[4];
                } syn;
                struct {
                        u_int8_t         mac[8];
                        u_int8_t         nonce[4];
                } synack;
                struct {
                        u_int8_t        mac[20];
                } ack;
        } u;
};

#define MP_JOIN_B                       0x01

struct mp_dss {
        u_int8_t        kind;
        u_int8_t        len;
        u_int8_t        sub;
        u_int8_t        flags;
};

#define MP_DSS_F                        0x10
#define MP_DSS_m                        0x08
#define MP_DSS_M                        0x04
#define MP_DSS_a                        0x02
#define MP_DSS_A                        0x01

struct mp_add_addr {
        u_int8_t        kind;
        u_int8_t        len;
        u_int8_t        sub_ipver;
        u_int8_t        addr_id;
        union {
                struct {
                        u_int8_t         addr[4];
                        u_int8_t         port[2];
                } v4;
                struct {
                        u_int8_t         addr[16];
                        u_int8_t         port[2];
                } v6;
        } u;
};

#define MP_ADD_ADDR_IPVER(sub_ipver)    (((sub_ipver) >> 0) & 0xF)

struct mp_remove_addr {
        u_int8_t        kind;
        u_int8_t        len;
        u_int8_t        sub;
        /* list of addr_id */
        u_int8_t        addrs_id;
};

struct mp_fail {
        u_int8_t        kind;
        u_int8_t        len;
        u_int8_t        sub;
        u_int8_t        resv;
        u_int8_t        data_seq[8];
};

struct mp_close {
        u_int8_t        kind;
        u_int8_t        len;
        u_int8_t        sub;
        u_int8_t        rsv;
        u_int8_t        key[8];
};

struct mp_prio {
        u_int8_t        kind;
        u_int8_t        len;
        u_int8_t        sub_b;
        u_int8_t        addr_id;
};

#define MP_PRIO_B                       0x01
