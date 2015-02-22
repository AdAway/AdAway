/*
 * This module implements decoding of OpenFlow protocol version 1.0 (wire
 * protocol 0x01). The decoder implements terse (default), detailed (-v) and
 * full (-vv) output formats and, as much as each format implies, detects and
 * tries to work around sizing anomalies inside the messages. The decoder marks
 * up bogus values of selected message fields and decodes partially captured
 * messages up to the snapshot end. It is based on the specification below:
 *
 * [OF10] http://www.openflow.org/documents/openflow-spec-v1.0.0.pdf
 *
 * Decoding of Ethernet frames nested in OFPT_PACKET_IN and OFPT_PACKET_OUT
 * messages is done only when the verbosity level set by command-line argument
 * is "-vvv" or higher. In that case the verbosity level is temporarily
 * decremented by 3 during the nested frame decoding. For example, running
 * tcpdump with "-vvvv" will do full decoding of OpenFlow and "-v" decoding of
 * the nested frames.
 *
 *
 * Copyright (c) 2013 The TCPDUMP project
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 * 1. Redistributions of source code must retain the above copyright
 *    notice, this list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright
 *    notice, this list of conditions and the following disclaimer in the
 *    documentation and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS
 * FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE
 * COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
 * INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING,
 * BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 * CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT
 * LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN
 * ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 */

#ifdef HAVE_CONFIG_H
#include "config.h"
#endif

#include <tcpdump-stdinc.h>

#include "interface.h"
#include "extract.h"
#include "addrtoname.h"
#include "ethertype.h"
#include "ipproto.h"
#include "openflow.h"

#define OFPT_HELLO                    0x00
#define OFPT_ERROR                    0x01
#define OFPT_ECHO_REQUEST             0x02
#define OFPT_ECHO_REPLY               0x03
#define OFPT_VENDOR                   0x04
#define OFPT_FEATURES_REQUEST         0x05
#define OFPT_FEATURES_REPLY           0x06
#define OFPT_GET_CONFIG_REQUEST       0x07
#define OFPT_GET_CONFIG_REPLY         0x08
#define OFPT_SET_CONFIG               0x09
#define OFPT_PACKET_IN                0x0a
#define OFPT_FLOW_REMOVED             0x0b
#define OFPT_PORT_STATUS              0x0c
#define OFPT_PACKET_OUT               0x0d
#define OFPT_FLOW_MOD                 0x0e
#define OFPT_PORT_MOD                 0x0f
#define OFPT_STATS_REQUEST            0x10
#define OFPT_STATS_REPLY              0x11
#define OFPT_BARRIER_REQUEST          0x12
#define OFPT_BARRIER_REPLY            0x13
#define OFPT_QUEUE_GET_CONFIG_REQUEST 0x14
#define OFPT_QUEUE_GET_CONFIG_REPLY   0x15
static const struct tok ofpt_str[] = {
	{ OFPT_HELLO,                    "HELLO"                    },
	{ OFPT_ERROR,                    "ERROR"                    },
	{ OFPT_ECHO_REQUEST,             "ECHO_REQUEST"             },
	{ OFPT_ECHO_REPLY,               "ECHO_REPLY"               },
	{ OFPT_VENDOR,                   "VENDOR"                   },
	{ OFPT_FEATURES_REQUEST,         "FEATURES_REQUEST"         },
	{ OFPT_FEATURES_REPLY,           "FEATURES_REPLY"           },
	{ OFPT_GET_CONFIG_REQUEST,       "GET_CONFIG_REQUEST"       },
	{ OFPT_GET_CONFIG_REPLY,         "GET_CONFIG_REPLY"         },
	{ OFPT_SET_CONFIG,               "SET_CONFIG"               },
	{ OFPT_PACKET_IN,                "PACKET_IN"                },
	{ OFPT_FLOW_REMOVED,             "FLOW_REMOVED"             },
	{ OFPT_PORT_STATUS,              "PORT_STATUS"              },
	{ OFPT_PACKET_OUT,               "PACKET_OUT"               },
	{ OFPT_FLOW_MOD,                 "FLOW_MOD"                 },
	{ OFPT_PORT_MOD,                 "PORT_MOD"                 },
	{ OFPT_STATS_REQUEST,            "STATS_REQUEST"            },
	{ OFPT_STATS_REPLY,              "STATS_REPLY"              },
	{ OFPT_BARRIER_REQUEST,          "BARRIER_REQUEST"          },
	{ OFPT_BARRIER_REPLY,            "BARRIER_REPLY"            },
	{ OFPT_QUEUE_GET_CONFIG_REQUEST, "QUEUE_GET_CONFIG_REQUEST" },
	{ OFPT_QUEUE_GET_CONFIG_REPLY,   "QUEUE_GET_CONFIG_REPLY"   },
	{ 0, NULL }
};

#define OFPPC_PORT_DOWN    (1 << 0)
#define OFPPC_NO_STP       (1 << 1)
#define OFPPC_NO_RECV      (1 << 2)
#define OFPPC_NO_RECV_STP  (1 << 3)
#define OFPPC_NO_FLOOD     (1 << 4)
#define OFPPC_NO_FWD       (1 << 5)
#define OFPPC_NO_PACKET_IN (1 << 6)
static const struct tok ofppc_bm[] = {
	{ OFPPC_PORT_DOWN,    "PORT_DOWN"    },
	{ OFPPC_NO_STP,       "NO_STP"       },
	{ OFPPC_NO_RECV,      "NO_RECV"      },
	{ OFPPC_NO_RECV_STP,  "NO_RECV_STP"  },
	{ OFPPC_NO_FLOOD,     "NO_FLOOD"     },
	{ OFPPC_NO_FWD,       "NO_FWD"       },
	{ OFPPC_NO_PACKET_IN, "NO_PACKET_IN" },
	{ 0, NULL }
};
#define OFPPC_U (~(OFPPC_PORT_DOWN | OFPPC_NO_STP | OFPPC_NO_RECV | \
                   OFPPC_NO_RECV_STP | OFPPC_NO_FLOOD | OFPPC_NO_FWD | \
                   OFPPC_NO_PACKET_IN))

#define OFPPS_LINK_DOWN   (1 << 0)
#define OFPPS_STP_LISTEN  (0 << 8)
#define OFPPS_STP_LEARN   (1 << 8)
#define OFPPS_STP_FORWARD (2 << 8)
#define OFPPS_STP_BLOCK   (3 << 8)
#define OFPPS_STP_MASK    (3 << 8)
static const struct tok ofpps_bm[] = {
	{ OFPPS_LINK_DOWN,   "LINK_DOWN"   },
	{ OFPPS_STP_LISTEN,  "STP_LISTEN"  },
	{ OFPPS_STP_LEARN,   "STP_LEARN"   },
	{ OFPPS_STP_FORWARD, "STP_FORWARD" },
	{ OFPPS_STP_BLOCK,   "STP_BLOCK"   },
	{ 0, NULL }
};
#define OFPPS_U (~(OFPPS_LINK_DOWN | OFPPS_STP_LISTEN | OFPPS_STP_LEARN | \
                   OFPPS_STP_FORWARD | OFPPS_STP_BLOCK))

#define OFPP_MAX        0xff00
#define OFPP_IN_PORT    0xfff8
#define OFPP_TABLE      0xfff9
#define OFPP_NORMAL     0xfffa
#define OFPP_FLOOD      0xfffb
#define OFPP_ALL        0xfffc
#define OFPP_CONTROLLER 0xfffd
#define OFPP_LOCAL      0xfffe
#define OFPP_NONE       0xffff
static const struct tok ofpp_str[] = {
	{ OFPP_MAX,        "MAX"        },
	{ OFPP_IN_PORT,    "IN_PORT"    },
	{ OFPP_TABLE,      "TABLE"      },
	{ OFPP_NORMAL,     "NORMAL"     },
	{ OFPP_FLOOD,      "FLOOD"      },
	{ OFPP_ALL,        "ALL"        },
	{ OFPP_CONTROLLER, "CONTROLLER" },
	{ OFPP_LOCAL,      "LOCAL"      },
	{ OFPP_NONE,       "NONE"       },
	{ 0, NULL }
};

#define OFPPF_10MB_HD    (1 <<  0)
#define OFPPF_10MB_FD    (1 <<  1)
#define OFPPF_100MB_HD   (1 <<  2)
#define OFPPF_100MB_FD   (1 <<  3)
#define OFPPF_1GB_HD     (1 <<  4)
#define OFPPF_1GB_FD     (1 <<  5)
#define OFPPF_10GB_FD    (1 <<  6)
#define OFPPF_COPPER     (1 <<  7)
#define OFPPF_FIBER      (1 <<  8)
#define OFPPF_AUTONEG    (1 <<  9)
#define OFPPF_PAUSE      (1 << 10)
#define OFPPF_PAUSE_ASYM (1 << 11)
static const struct tok ofppf_bm[] = {
	{ OFPPF_10MB_HD,    "10MB_HD"    },
	{ OFPPF_10MB_FD,    "10MB_FD"    },
	{ OFPPF_100MB_HD,   "100MB_HD"   },
	{ OFPPF_100MB_FD,   "100MB_FD"   },
	{ OFPPF_1GB_HD,     "1GB_HD"     },
	{ OFPPF_1GB_FD,     "1GB_FD"     },
	{ OFPPF_10GB_FD,    "10GB_FD"    },
	{ OFPPF_COPPER,     "COPPER"     },
	{ OFPPF_FIBER,      "FIBER"      },
	{ OFPPF_AUTONEG,    "AUTONEG"    },
	{ OFPPF_PAUSE,      "PAUSE"      },
	{ OFPPF_PAUSE_ASYM, "PAUSE_ASYM" },
	{ 0, NULL }
};
#define OFPPF_U (~(OFPPF_10MB_HD | OFPPF_10MB_FD | OFPPF_100MB_HD | \
                   OFPPF_100MB_FD | OFPPF_1GB_HD | OFPPF_1GB_FD | \
                   OFPPF_10GB_FD | OFPPF_COPPER | OFPPF_FIBER | \
                   OFPPF_AUTONEG | OFPPF_PAUSE | OFPPF_PAUSE_ASYM))

#define OFPQT_NONE     0x0000
#define OFPQT_MIN_RATE 0x0001
static const struct tok ofpqt_str[] = {
	{ OFPQT_NONE,     "NONE"     },
	{ OFPQT_MIN_RATE, "MIN_RATE" },
	{ 0, NULL }
};

#define OFPFW_IN_PORT      (1 << 0)
#define OFPFW_DL_VLAN      (1 << 1)
#define OFPFW_DL_SRC       (1 << 2)
#define OFPFW_DL_DST       (1 << 3)
#define OFPFW_DL_TYPE      (1 << 4)
#define OFPFW_NW_PROTO     (1 << 5)
#define OFPFW_TP_SRC       (1 << 6)
#define OFPFW_TP_DST       (1 << 7)
#define OFPFW_NW_SRC_SHIFT 8
#define OFPFW_NW_SRC_BITS  6
#define OFPFW_NW_SRC_MASK  (((1 << OFPFW_NW_SRC_BITS) - 1) << OFPFW_NW_SRC_SHIFT)
#define OFPFW_NW_DST_SHIFT 14
#define OFPFW_NW_DST_BITS  6
#define OFPFW_NW_DST_MASK  (((1 << OFPFW_NW_DST_BITS) - 1) << OFPFW_NW_DST_SHIFT)
#define OFPFW_DL_VLAN_PCP  (1 << 20)
#define OFPFW_NW_TOS       (1 << 21)
#define OFPFW_ALL          ((1 << 22) - 1)
static const struct tok ofpfw_bm[] = {
	{ OFPFW_IN_PORT,     "IN_PORT"     },
	{ OFPFW_DL_VLAN,     "DL_VLAN"     },
	{ OFPFW_DL_SRC,      "DL_SRC"      },
	{ OFPFW_DL_DST,      "DL_DST"      },
	{ OFPFW_DL_TYPE,     "DL_TYPE"     },
	{ OFPFW_NW_PROTO,    "NW_PROTO"    },
	{ OFPFW_TP_SRC,      "TP_SRC"      },
	{ OFPFW_TP_DST,      "TP_DST"      },
	{ OFPFW_DL_VLAN_PCP, "DL_VLAN_PCP" },
	{ OFPFW_NW_TOS,      "NW_TOS"      },
	{ 0, NULL }
};
/* The above array does not include bits 8~13 (OFPFW_NW_SRC_*) and 14~19
 * (OFPFW_NW_DST_*), which are not a part of the bitmap and require decoding
 * other than that of tok2str(). The macro below includes these bits such that
 * they are not reported as bogus in the decoding. */
#define OFPFW_U (~(OFPFW_ALL))

#define OFPAT_OUTPUT       0x0000
#define OFPAT_SET_VLAN_VID 0x0001
#define OFPAT_SET_VLAN_PCP 0x0002
#define OFPAT_STRIP_VLAN   0x0003
#define OFPAT_SET_DL_SRC   0x0004
#define OFPAT_SET_DL_DST   0x0005
#define OFPAT_SET_NW_SRC   0x0006
#define OFPAT_SET_NW_DST   0x0007
#define OFPAT_SET_NW_TOS   0x0008
#define OFPAT_SET_TP_SRC   0x0009
#define OFPAT_SET_TP_DST   0x000a
#define OFPAT_ENQUEUE      0x000b
#define OFPAT_VENDOR       0xffff
static const struct tok ofpat_str[] = {
	{ OFPAT_OUTPUT,       "OUTPUT"       },
	{ OFPAT_SET_VLAN_VID, "SET_VLAN_VID" },
	{ OFPAT_SET_VLAN_PCP, "SET_VLAN_PCP" },
	{ OFPAT_STRIP_VLAN,   "STRIP_VLAN"   },
	{ OFPAT_SET_DL_SRC,   "SET_DL_SRC"   },
	{ OFPAT_SET_DL_DST,   "SET_DL_DST"   },
	{ OFPAT_SET_NW_SRC,   "SET_NW_SRC"   },
	{ OFPAT_SET_NW_DST,   "SET_NW_DST"   },
	{ OFPAT_SET_NW_TOS,   "SET_NW_TOS"   },
	{ OFPAT_SET_TP_SRC,   "SET_TP_SRC"   },
	{ OFPAT_SET_TP_DST,   "SET_TP_DST"   },
	{ OFPAT_ENQUEUE,      "ENQUEUE"      },
	{ OFPAT_VENDOR,       "VENDOR"       },
	{ 0, NULL }
};

/* bit-shifted, w/o vendor action */
static const struct tok ofpat_bm[] = {
	{ 1 << OFPAT_OUTPUT,       "OUTPUT"       },
	{ 1 << OFPAT_SET_VLAN_VID, "SET_VLAN_VID" },
	{ 1 << OFPAT_SET_VLAN_PCP, "SET_VLAN_PCP" },
	{ 1 << OFPAT_STRIP_VLAN,   "STRIP_VLAN"   },
	{ 1 << OFPAT_SET_DL_SRC,   "SET_DL_SRC"   },
	{ 1 << OFPAT_SET_DL_DST,   "SET_DL_DST"   },
	{ 1 << OFPAT_SET_NW_SRC,   "SET_NW_SRC"   },
	{ 1 << OFPAT_SET_NW_DST,   "SET_NW_DST"   },
	{ 1 << OFPAT_SET_NW_TOS,   "SET_NW_TOS"   },
	{ 1 << OFPAT_SET_TP_SRC,   "SET_TP_SRC"   },
	{ 1 << OFPAT_SET_TP_DST,   "SET_TP_DST"   },
	{ 1 << OFPAT_ENQUEUE,      "ENQUEUE"      },
	{ 0, NULL }
};
#define OFPAT_U (~(1 << OFPAT_OUTPUT | 1 << OFPAT_SET_VLAN_VID | \
                   1 << OFPAT_SET_VLAN_PCP | 1 << OFPAT_STRIP_VLAN | \
                   1 << OFPAT_SET_DL_SRC | 1 << OFPAT_SET_DL_DST | \
                   1 << OFPAT_SET_NW_SRC | 1 << OFPAT_SET_NW_DST | \
                   1 << OFPAT_SET_NW_TOS | 1 << OFPAT_SET_TP_SRC | \
                   1 << OFPAT_SET_TP_DST | 1 << OFPAT_ENQUEUE))

#define OFPC_FLOW_STATS   (1 << 0)
#define OFPC_TABLE_STATS  (1 << 1)
#define OFPC_PORT_STATS   (1 << 2)
#define OFPC_STP          (1 << 3)
#define OFPC_RESERVED     (1 << 4)
#define OFPC_IP_REASM     (1 << 5)
#define OFPC_QUEUE_STATS  (1 << 6)
#define OFPC_ARP_MATCH_IP (1 << 7)
static const struct tok ofp_capabilities_bm[] = {
	{ OFPC_FLOW_STATS,   "FLOW_STATS"   },
	{ OFPC_TABLE_STATS,  "TABLE_STATS"  },
	{ OFPC_PORT_STATS,   "PORT_STATS"   },
	{ OFPC_STP,          "STP"          },
	{ OFPC_RESERVED,     "RESERVED"     }, /* not in the mask below */
	{ OFPC_IP_REASM,     "IP_REASM"     },
	{ OFPC_QUEUE_STATS,  "QUEUE_STATS"  },
	{ OFPC_ARP_MATCH_IP, "ARP_MATCH_IP" },
	{ 0, NULL }
};
#define OFPCAP_U (~(OFPC_FLOW_STATS | OFPC_TABLE_STATS | OFPC_PORT_STATS | \
                    OFPC_STP | OFPC_IP_REASM | OFPC_QUEUE_STATS | \
                    OFPC_ARP_MATCH_IP))

#define OFPC_FRAG_NORMAL 0x0000
#define OFPC_FRAG_DROP   0x0001
#define OFPC_FRAG_REASM  0x0002
#define OFPC_FRAG_MASK   0x0003
static const struct tok ofp_config_str[] = {
	{ OFPC_FRAG_NORMAL, "FRAG_NORMAL" },
	{ OFPC_FRAG_DROP,   "FRAG_DROP"   },
	{ OFPC_FRAG_REASM,  "FRAG_REASM"  },
	{ 0, NULL }
};

#define OFPFC_ADD           0x0000
#define OFPFC_MODIFY        0x0001
#define OFPFC_MODIFY_STRICT 0x0002
#define OFPFC_DELETE        0x0003
#define OFPFC_DELETE_STRICT 0x0004
static const struct tok ofpfc_str[] = {
	{ OFPFC_ADD,           "ADD"           },
	{ OFPFC_MODIFY,        "MODIFY"        },
	{ OFPFC_MODIFY_STRICT, "MODIFY_STRICT" },
	{ OFPFC_DELETE,        "DELETE"        },
	{ OFPFC_DELETE_STRICT, "DELETE_STRICT" },
	{ 0, NULL }
};

static const struct tok bufferid_str[] = {
	{ 0xffffffff, "NONE" },
	{ 0, NULL }
};

#define OFPFF_SEND_FLOW_REM (1 << 0)
#define OFPFF_CHECK_OVERLAP (1 << 1)
#define OFPFF_EMERG         (1 << 2)
static const struct tok ofpff_bm[] = {
	{ OFPFF_SEND_FLOW_REM, "SEND_FLOW_REM" },
	{ OFPFF_CHECK_OVERLAP, "CHECK_OVERLAP" },
	{ OFPFF_EMERG,         "EMERG"         },
	{ 0, NULL }
};
#define OFPFF_U (~(OFPFF_SEND_FLOW_REM | OFPFF_CHECK_OVERLAP | OFPFF_EMERG))

#define OFPST_DESC      0x0000
#define OFPST_FLOW      0x0001
#define OFPST_AGGREGATE 0x0002
#define OFPST_TABLE     0x0003
#define OFPST_PORT      0x0004
#define OFPST_QUEUE     0x0005
#define OFPST_VENDOR    0xffff
static const struct tok ofpst_str[] = {
	{ OFPST_DESC,      "DESC"      },
	{ OFPST_FLOW,      "FLOW"      },
	{ OFPST_AGGREGATE, "AGGREGATE" },
	{ OFPST_TABLE,     "TABLE"     },
	{ OFPST_PORT,      "PORT"      },
	{ OFPST_QUEUE,     "QUEUE"     },
	{ OFPST_VENDOR,    "VENDOR"    },
	{ 0, NULL }
};

static const struct tok tableid_str[] = {
	{ 0xfe, "EMERG" },
	{ 0xff, "ALL"   },
	{ 0, NULL }
};

#define OFPQ_ALL      0xffffffff
static const struct tok ofpq_str[] = {
	{ OFPQ_ALL, "ALL" },
	{ 0, NULL }
};

#define OFPSF_REPLY_MORE 0x0001
static const struct tok ofpsf_reply_bm[] = {
	{ OFPSF_REPLY_MORE, "MORE" },
	{ 0, NULL }
};
#define OFPSF_REPLY_U (~(OFPSF_REPLY_MORE))

#define OFPR_NO_MATCH 0x00
#define OFPR_ACTION   0x01
static const struct tok ofpr_str[] = {
	{ OFPR_NO_MATCH, "NO_MATCH" },
	{ OFPR_ACTION,   "ACTION"   },
	{ 0, NULL }
};

#define OFPRR_IDLE_TIMEOUT 0x00
#define OFPRR_HARD_TIMEOUT 0x01
#define OFPRR_DELETE       0x02
static const struct tok ofprr_str[] = {
	{ OFPRR_IDLE_TIMEOUT, "IDLE_TIMEOUT" },
	{ OFPRR_HARD_TIMEOUT, "HARD_TIMEOUT" },
	{ OFPRR_DELETE,       "DELETE"       },
	{ 0, NULL }
};

#define OFPPR_ADD    0x00
#define OFPPR_DELETE 0x01
#define OFPPR_MODIFY 0x02
static const struct tok ofppr_str[] = {
	{ OFPPR_ADD,    "ADD"    },
	{ OFPPR_DELETE, "DELETE" },
	{ OFPPR_MODIFY, "MODIFY" },
	{ 0, NULL }
};

#define OFPET_HELLO_FAILED    0x0000
#define OFPET_BAD_REQUEST     0x0001
#define OFPET_BAD_ACTION      0x0002
#define OFPET_FLOW_MOD_FAILED 0x0003
#define OFPET_PORT_MOD_FAILED 0x0004
#define OFPET_QUEUE_OP_FAILED 0x0005
static const struct tok ofpet_str[] = {
	{ OFPET_HELLO_FAILED,    "HELLO_FAILED"    },
	{ OFPET_BAD_REQUEST,     "BAD_REQUEST"     },
	{ OFPET_BAD_ACTION,      "BAD_ACTION"      },
	{ OFPET_FLOW_MOD_FAILED, "FLOW_MOD_FAILED" },
	{ OFPET_PORT_MOD_FAILED, "PORT_MOD_FAILED" },
	{ OFPET_QUEUE_OP_FAILED, "QUEUE_OP_FAILED" },
	{ 0, NULL }
};

#define OFPHFC_INCOMPATIBLE 0x0000
#define OFPHFC_EPERM        0x0001
static const struct tok ofphfc_str[] = {
	{ OFPHFC_INCOMPATIBLE, "INCOMPATIBLE" },
	{ OFPHFC_EPERM,        "EPERM"        },
	{ 0, NULL }
};

#define OFPBRC_BAD_VERSION    0x0000
#define OFPBRC_BAD_TYPE       0x0001
#define OFPBRC_BAD_STAT       0x0002
#define OFPBRC_BAD_VENDOR     0x0003
#define OFPBRC_BAD_SUBTYPE    0x0004
#define OFPBRC_EPERM          0x0005
#define OFPBRC_BAD_LEN        0x0006
#define OFPBRC_BUFFER_EMPTY   0x0007
#define OFPBRC_BUFFER_UNKNOWN 0x0008
static const struct tok ofpbrc_str[] = {
	{ OFPBRC_BAD_VERSION,    "BAD_VERSION"    },
	{ OFPBRC_BAD_TYPE,       "BAD_TYPE"       },
	{ OFPBRC_BAD_STAT,       "BAD_STAT"       },
	{ OFPBRC_BAD_VENDOR,     "BAD_VENDOR"     },
	{ OFPBRC_BAD_SUBTYPE,    "BAD_SUBTYPE"    },
	{ OFPBRC_EPERM,          "EPERM"          },
	{ OFPBRC_BAD_LEN,        "BAD_LEN"        },
	{ OFPBRC_BUFFER_EMPTY,   "BUFFER_EMPTY"   },
	{ OFPBRC_BUFFER_UNKNOWN, "BUFFER_UNKNOWN" },
	{ 0, NULL }
};

#define OFPBAC_BAD_TYPE        0x0000
#define OFPBAC_BAD_LEN         0x0001
#define OFPBAC_BAD_VENDOR      0x0002
#define OFPBAC_BAD_VENDOR_TYPE 0x0003
#define OFPBAC_BAD_OUT_PORT    0x0004
#define OFPBAC_BAD_ARGUMENT    0x0005
#define OFPBAC_EPERM           0x0006
#define OFPBAC_TOO_MANY        0x0007
#define OFPBAC_BAD_QUEUE       0x0008
static const struct tok ofpbac_str[] = {
	{ OFPBAC_BAD_TYPE,        "BAD_TYPE"        },
	{ OFPBAC_BAD_LEN,         "BAD_LEN"         },
	{ OFPBAC_BAD_VENDOR,      "BAD_VENDOR"      },
	{ OFPBAC_BAD_VENDOR_TYPE, "BAD_VENDOR_TYPE" },
	{ OFPBAC_BAD_OUT_PORT,    "BAD_OUT_PORT"    },
	{ OFPBAC_BAD_ARGUMENT,    "BAD_ARGUMENT"    },
	{ OFPBAC_EPERM,           "EPERM"           },
	{ OFPBAC_TOO_MANY,        "TOO_MANY"        },
	{ OFPBAC_BAD_QUEUE,       "BAD_QUEUE"       },
	{ 0, NULL }
};

#define OFPFMFC_ALL_TABLES_FULL   0x0000
#define OFPFMFC_OVERLAP           0x0001
#define OFPFMFC_EPERM             0x0002
#define OFPFMFC_BAD_EMERG_TIMEOUT 0x0003
#define OFPFMFC_BAD_COMMAND       0x0004
#define OFPFMFC_UNSUPPORTED       0x0005
static const struct tok ofpfmfc_str[] = {
	{ OFPFMFC_ALL_TABLES_FULL,   "ALL_TABLES_FULL"   },
	{ OFPFMFC_OVERLAP,           "OVERLAP"           },
	{ OFPFMFC_EPERM,             "EPERM"             },
	{ OFPFMFC_BAD_EMERG_TIMEOUT, "BAD_EMERG_TIMEOUT" },
	{ OFPFMFC_BAD_COMMAND,       "BAD_COMMAND"       },
	{ OFPFMFC_UNSUPPORTED,       "UNSUPPORTED"       },
	{ 0, NULL }
};

#define OFPPMFC_BAD_PORT    0x0000
#define OFPPMFC_BAD_HW_ADDR 0x0001
static const struct tok ofppmfc_str[] = {
	{ OFPPMFC_BAD_PORT,    "BAD_PORT"    },
	{ OFPPMFC_BAD_HW_ADDR, "BAD_HW_ADDR" },
	{ 0, NULL }
};

#define OFPQOFC_BAD_PORT  0x0000
#define OFPQOFC_BAD_QUEUE 0x0001
#define OFPQOFC_EPERM     0x0002
static const struct tok ofpqofc_str[] = {
	{ OFPQOFC_BAD_PORT,  "BAD_PORT"  },
	{ OFPQOFC_BAD_QUEUE, "BAD_QUEUE" },
	{ OFPQOFC_EPERM,     "EPERM"     },
	{ 0, NULL }
};

static const struct tok empty_str[] = {
	{ 0, NULL }
};

/* lengths (fixed or minimal) of particular protocol structures */
#define OF_SWITCH_CONFIG_LEN              12
#define OF_PHY_PORT_LEN                   48
#define OF_SWITCH_FEATURES_LEN            32
#define OF_PORT_STATUS_LEN                64
#define OF_PORT_MOD_LEN                   32
#define OF_PACKET_IN_LEN                  20
#define OF_ACTION_OUTPUT_LEN               8
#define OF_ACTION_VLAN_VID_LEN             8
#define OF_ACTION_VLAN_PCP_LEN             8
#define OF_ACTION_DL_ADDR_LEN             16
#define OF_ACTION_NW_ADDR_LEN              8
#define OF_ACTION_TP_PORT_LEN              8
#define OF_ACTION_NW_TOS_LEN               8
#define OF_ACTION_VENDOR_HEADER_LEN        8
#define OF_ACTION_HEADER_LEN               8
#define OF_PACKET_OUT_LEN                 16
#define OF_MATCH_LEN                      40
#define OF_FLOW_MOD_LEN                   72
#define OF_FLOW_REMOVED_LEN               88
#define OF_ERROR_MSG_LEN                  12
#define OF_STATS_REQUEST_LEN              12
#define OF_STATS_REPLY_LEN                12
#define OF_DESC_STATS_LEN               1056
#define OF_FLOW_STATS_REQUEST_LEN         44
#define OF_FLOW_STATS_LEN                 88
#define OF_AGGREGATE_STATS_REQUEST_LEN    44
#define OF_AGGREGATE_STATS_REPLY_LEN      24
#define OF_TABLE_STATS_LEN                64
#define OF_PORT_STATS_REQUEST_LEN          8
#define OF_PORT_STATS_LEN                104
#define OF_VENDOR_HEADER_LEN              12
#define OF_QUEUE_PROP_HEADER_LEN           8
#define OF_QUEUE_PROP_MIN_RATE_LEN        16
#define OF_PACKET_QUEUE_LEN                8
#define OF_QUEUE_GET_CONFIG_REQUEST_LEN   12
#define OF_QUEUE_GET_CONFIG_REPLY_LEN     16
#define OF_ACTION_ENQUEUE_LEN             16
#define OF_QUEUE_STATS_REQUEST_LEN         8
#define OF_QUEUE_STATS_LEN                32

/* miscellaneous constants from [OF10] */
#define OFP_MAX_TABLE_NAME_LEN     32
#define OFP_MAX_PORT_NAME_LEN      16
#define DESC_STR_LEN              256
#define SERIAL_NUM_LEN             32
#define OFP_ETH_ALEN                6
#define OFP_VLAN_NONE          0xffff

static const char *
vlan_str(const uint16_t vid) {
	static char buf[sizeof("65535 (bogus)")];
	const char *fmt;

	if (vid == OFP_VLAN_NONE)
		return "NONE";
	fmt = (vid > 0 && vid < 0x0fff) ? "%u" : "%u (bogus)";
	snprintf(buf, sizeof(buf), fmt, vid);
	return buf;
}

static const char *
pcp_str(const uint8_t pcp) {
	static char buf[sizeof("255 (bogus)")];
	snprintf(buf, sizeof(buf), pcp <= 7 ? "%u" : "%u (bogus)", pcp);
	return buf;
}

static void
of10_bitmap_print(const struct tok *t, const uint32_t v, const uint32_t u) {
	const char *sep = " (";

	if (v == 0)
		return;
	/* assigned bits */
	for (; t->s != NULL; t++)
		if (v & t->v) {
			printf("%s%s", sep, t->s);
			sep = ", ";
		}
	/* unassigned bits? */
	printf(v & u ? ") (bogus)" : ")");
}

static const u_char *
of10_data_print(const u_char *cp, const u_char *ep, const u_int len) {
	if (len == 0)
		return cp;
	/* data */
	printf("\n\t data (%u octets)", len);
	TCHECK2(*cp, len);
	if (vflag >= 2)
		hex_and_ascii_print("\n\t  ", cp, len);
	return cp + len;

trunc:
	printf(" [|openflow]");
	return ep;
}

/* Vendor ID is mandatory, data is optional. */
static const u_char *
of10_vendor_data_print(const u_char *cp, const u_char *ep, const u_int len) {
	if (len < 4)
		goto corrupt;
	/* vendor */
	TCHECK2(*cp, 4);
	printf(", vendor 0x%08x", EXTRACT_32BITS(cp));
	cp += 4;
	/* data */
	return of10_data_print(cp, ep, len - 4);

corrupt: /* skip the undersized data */
	printf(" (corrupt)");
	TCHECK2(*cp, len);
	return cp + len;
trunc:
	printf(" [|openflow]");
	return ep;
}

static const u_char *
of10_packet_data_print(const u_char *cp, const u_char *ep, const u_int len) {
	if (len == 0)
		return cp;
	/* data */
	printf("\n\t data (%u octets)", len);
	if (vflag < 3)
		return cp + len;
	TCHECK2(*cp, len);
	vflag -= 3;
	printf(", frame decoding below\n");
	ether_print(gndo, cp, len, snapend - cp, NULL, NULL);
	vflag += 3;
	return cp + len;

trunc:
	printf(" [|openflow]");
	return ep;
}

/* [OF10] Section 5.2.1 */
static const u_char *
of10_phy_ports_print(const u_char *cp, const u_char *ep, u_int len) {
	const u_char *cp0 = cp;
	const u_int len0 = len;

	while (len) {
		if (len < OF_PHY_PORT_LEN)
			goto corrupt;
		/* port_no */
		TCHECK2(*cp, 2);
		printf("\n\t  port_no %s", tok2str(ofpp_str, "%u", EXTRACT_16BITS(cp)));
		cp += 2;
		/* hw_addr */
		TCHECK2(*cp, OFP_ETH_ALEN);
		printf(", hw_addr %s", etheraddr_string(cp));
		cp += OFP_ETH_ALEN;
		/* name */
		TCHECK2(*cp, OFP_MAX_PORT_NAME_LEN);
		printf(", name '");
		fn_print(cp, cp + OFP_MAX_PORT_NAME_LEN);
		printf("'");
		cp += OFP_MAX_PORT_NAME_LEN;

		if (vflag < 2) {
			TCHECK2(*cp, 24);
			cp += 24;
			goto next_port;
		}
		/* config */
		TCHECK2(*cp, 4);
		printf("\n\t   config 0x%08x", EXTRACT_32BITS(cp));
		of10_bitmap_print(ofppc_bm, EXTRACT_32BITS(cp), OFPPC_U);
		cp += 4;
		/* state */
		TCHECK2(*cp, 4);
		printf("\n\t   state 0x%08x", EXTRACT_32BITS(cp));
		of10_bitmap_print(ofpps_bm, EXTRACT_32BITS(cp), OFPPS_U);
		cp += 4;
		/* curr */
		TCHECK2(*cp, 4);
		printf("\n\t   curr 0x%08x", EXTRACT_32BITS(cp));
		of10_bitmap_print(ofppf_bm, EXTRACT_32BITS(cp), OFPPF_U);
		cp += 4;
		/* advertised */
		TCHECK2(*cp, 4);
		printf("\n\t   advertised 0x%08x", EXTRACT_32BITS(cp));
		of10_bitmap_print(ofppf_bm, EXTRACT_32BITS(cp), OFPPF_U);
		cp += 4;
		/* supported */
		TCHECK2(*cp, 4);
		printf("\n\t   supported 0x%08x", EXTRACT_32BITS(cp));
		of10_bitmap_print(ofppf_bm, EXTRACT_32BITS(cp), OFPPF_U);
		cp += 4;
		/* peer */
		TCHECK2(*cp, 4);
		printf("\n\t   peer 0x%08x", EXTRACT_32BITS(cp));
		of10_bitmap_print(ofppf_bm, EXTRACT_32BITS(cp), OFPPF_U);
		cp += 4;
next_port:
		len -= OF_PHY_PORT_LEN;
	} /* while */
	return cp;

corrupt: /* skip the undersized trailing data */
	printf(" (corrupt)");
	TCHECK2(*cp0, len0);
	return cp0 + len0;
trunc:
	printf(" [|openflow]");
	return ep;
}

/* [OF10] Section 5.2.2 */
static const u_char *
of10_queue_props_print(const u_char *cp, const u_char *ep, u_int len) {
	const u_char *cp0 = cp;
	const u_int len0 = len;
	uint16_t property, plen, rate;

	while (len) {
		u_char plen_bogus = 0, skip = 0;

		if (len < OF_QUEUE_PROP_HEADER_LEN)
			goto corrupt;
		/* property */
		TCHECK2(*cp, 2);
		property = EXTRACT_16BITS(cp);
		cp += 2;
		printf("\n\t   property %s", tok2str(ofpqt_str, "invalid (0x%04x)", property));
		/* len */
		TCHECK2(*cp, 2);
		plen = EXTRACT_16BITS(cp);
		cp += 2;
		printf(", len %u", plen);
		if (plen < OF_QUEUE_PROP_HEADER_LEN || plen > len)
			goto corrupt;
		/* pad */
		TCHECK2(*cp, 4);
		cp += 4;
		/* property-specific constraints and decoding */
		switch (property) {
		case OFPQT_NONE:
			plen_bogus = plen != OF_QUEUE_PROP_HEADER_LEN;
			break;
		case OFPQT_MIN_RATE:
			plen_bogus = plen != OF_QUEUE_PROP_MIN_RATE_LEN;
			break;
		default:
			skip = 1;
		}
		if (plen_bogus) {
			printf(" (bogus)");
			skip = 1;
		}
		if (skip) {
			TCHECK2(*cp, plen - 4);
			cp += plen - 4;
			goto next_property;
		}
		if (property == OFPQT_MIN_RATE) { /* the only case of property decoding */
			/* rate */
			TCHECK2(*cp, 2);
			rate = EXTRACT_16BITS(cp);
			cp += 2;
			if (rate > 1000)
				printf(", rate disabled");
			else
				printf(", rate %u.%u%%", rate / 10, rate % 10);
			/* pad */
			TCHECK2(*cp, 6);
			cp += 6;
		}
next_property:
		len -= plen;
	} /* while */
	return cp;

corrupt: /* skip the rest of queue properties */
	printf(" (corrupt)");
	TCHECK2(*cp0, len0);
	return cp0 + len0;
trunc:
	printf(" [|openflow]");
	return ep;
}

/* ibid */
static const u_char *
of10_queues_print(const u_char *cp, const u_char *ep, u_int len) {
	const u_char *cp0 = cp;
	const u_int len0 = len;
	uint16_t desclen;

	while (len) {
		if (len < OF_PACKET_QUEUE_LEN)
			goto corrupt;
		/* queue_id */
		TCHECK2(*cp, 4);
		printf("\n\t  queue_id %u", EXTRACT_32BITS(cp));
		cp += 4;
		/* len */
		TCHECK2(*cp, 2);
		desclen = EXTRACT_16BITS(cp);
		cp += 2;
		printf(", len %u", desclen);
		if (desclen < OF_PACKET_QUEUE_LEN || desclen > len)
			goto corrupt;
		/* pad */
		TCHECK2(*cp, 2);
		cp += 2;
		/* properties */
		if (vflag < 2) {
			TCHECK2(*cp, desclen - OF_PACKET_QUEUE_LEN);
			cp += desclen - OF_PACKET_QUEUE_LEN;
			goto next_queue;
		}
		if (ep == (cp = of10_queue_props_print(cp, ep, desclen - OF_PACKET_QUEUE_LEN)))
			return ep; /* end of snapshot */
next_queue:
		len -= desclen;
	} /* while */
	return cp;

corrupt: /* skip the rest of queues */
	printf(" (corrupt)");
	TCHECK2(*cp0, len0);
	return cp0 + len0;
trunc:
	printf(" [|openflow]");
	return ep;
}

/* [OF10] Section 5.2.3 */
static const u_char *
of10_match_print(const char *pfx, const u_char *cp, const u_char *ep) {
	uint32_t wildcards;
	uint16_t dl_type;
	uint8_t nw_proto;
	u_char nw_bits;
	const char *field_name;

	/* wildcards */
	TCHECK2(*cp, 4);
	wildcards = EXTRACT_32BITS(cp);
	if (wildcards & OFPFW_U)
		printf("%swildcards 0x%08x (bogus)", pfx, wildcards);
	cp += 4;
	/* in_port */
	TCHECK2(*cp, 2);
	if (! (wildcards & OFPFW_IN_PORT))
		printf("%smatch in_port %s", pfx, tok2str(ofpp_str, "%u", EXTRACT_16BITS(cp)));
	cp += 2;
	/* dl_src */
	TCHECK2(*cp, OFP_ETH_ALEN);
	if (! (wildcards & OFPFW_DL_SRC))
		printf("%smatch dl_src %s", pfx, etheraddr_string(cp));
	cp += OFP_ETH_ALEN;
	/* dl_dst */
	TCHECK2(*cp, OFP_ETH_ALEN);
	if (! (wildcards & OFPFW_DL_DST))
		printf("%smatch dl_dst %s", pfx, etheraddr_string(cp));
	cp += OFP_ETH_ALEN;
	/* dl_vlan */
	TCHECK2(*cp, 2);
	if (! (wildcards & OFPFW_DL_VLAN))
		printf("%smatch dl_vlan %s", pfx, vlan_str(EXTRACT_16BITS(cp)));
	cp += 2;
	/* dl_vlan_pcp */
	TCHECK2(*cp, 1);
	if (! (wildcards & OFPFW_DL_VLAN_PCP))
		printf("%smatch dl_vlan_pcp %s", pfx, pcp_str(*cp));
	cp += 1;
	/* pad1 */
	TCHECK2(*cp, 1);
	cp += 1;
	/* dl_type */
	TCHECK2(*cp, 2);
	dl_type = EXTRACT_16BITS(cp);
	cp += 2;
	if (! (wildcards & OFPFW_DL_TYPE))
		printf("%smatch dl_type 0x%04x", pfx, dl_type);
	/* nw_tos */
	TCHECK2(*cp, 1);
	if (! (wildcards & OFPFW_NW_TOS))
		printf("%smatch nw_tos 0x%02x", pfx, *cp);
	cp += 1;
	/* nw_proto */
	TCHECK2(*cp, 1);
	nw_proto = *cp;
	cp += 1;
	if (! (wildcards & OFPFW_NW_PROTO)) {
		field_name = ! (wildcards & OFPFW_DL_TYPE) && dl_type == ETHERTYPE_ARP
		  ? "arp_opcode" : "nw_proto";
		printf("%smatch %s %u", pfx, field_name, nw_proto);
	}
	/* pad2 */
	TCHECK2(*cp, 2);
	cp += 2;
	/* nw_src */
	TCHECK2(*cp, 4);
	nw_bits = (wildcards & OFPFW_NW_SRC_MASK) >> OFPFW_NW_SRC_SHIFT;
	if (nw_bits < 32)
		printf("%smatch nw_src %s/%u", pfx, ipaddr_string(cp), 32 - nw_bits);
	cp += 4;
	/* nw_dst */
	TCHECK2(*cp, 4);
	nw_bits = (wildcards & OFPFW_NW_DST_MASK) >> OFPFW_NW_DST_SHIFT;
	if (nw_bits < 32)
		printf("%smatch nw_dst %s/%u", pfx, ipaddr_string(cp), 32 - nw_bits);
	cp += 4;
	/* tp_src */
	TCHECK2(*cp, 2);
	if (! (wildcards & OFPFW_TP_SRC)) {
		field_name = ! (wildcards & OFPFW_DL_TYPE) && dl_type == ETHERTYPE_IP
		  && ! (wildcards & OFPFW_NW_PROTO) && nw_proto == IPPROTO_ICMP
		  ? "icmp_type" : "tp_src";
		printf("%smatch %s %u", pfx, field_name, EXTRACT_16BITS(cp));
	}
	cp += 2;
	/* tp_dst */
	TCHECK2(*cp, 2);
	if (! (wildcards & OFPFW_TP_DST)) {
		field_name = ! (wildcards & OFPFW_DL_TYPE) && dl_type == ETHERTYPE_IP
		  && ! (wildcards & OFPFW_NW_PROTO) && nw_proto == IPPROTO_ICMP
		  ? "icmp_code" : "tp_dst";
		printf("%smatch %s %u", pfx, field_name, EXTRACT_16BITS(cp));
	}
	return cp + 2;

trunc:
	printf(" [|openflow]");
	return ep;
}

/* [OF10] Section 5.2.4 */
static const u_char *
of10_actions_print(const char *pfx, const u_char *cp, const u_char *ep,
                   u_int len) {
	const u_char *cp0 = cp;
	const u_int len0 = len;
	uint16_t type, alen, output_port;

	while (len) {
		u_char alen_bogus = 0, skip = 0;

		if (len < OF_ACTION_HEADER_LEN)
			goto corrupt;
		/* type */
		TCHECK2(*cp, 2);
		type = EXTRACT_16BITS(cp);
		cp += 2;
		printf("%saction type %s", pfx, tok2str(ofpat_str, "invalid (0x%04x)", type));
		/* length */
		TCHECK2(*cp, 2);
		alen = EXTRACT_16BITS(cp);
		cp += 2;
		printf(", len %u", alen);
		/* On action size underrun/overrun skip the rest of the action list. */
		if (alen < OF_ACTION_HEADER_LEN || alen > len)
			goto corrupt;
		/* On action size inappropriate for the given type or invalid type just skip
		 * the current action, as the basic length constraint has been met. */
		switch (type) {
		case OFPAT_OUTPUT:
		case OFPAT_SET_VLAN_VID:
		case OFPAT_SET_VLAN_PCP:
		case OFPAT_STRIP_VLAN:
		case OFPAT_SET_NW_SRC:
		case OFPAT_SET_NW_DST:
		case OFPAT_SET_NW_TOS:
		case OFPAT_SET_TP_SRC:
		case OFPAT_SET_TP_DST:
			alen_bogus = alen != 8;
			break;
		case OFPAT_SET_DL_SRC:
		case OFPAT_SET_DL_DST:
		case OFPAT_ENQUEUE:
			alen_bogus = alen != 16;
			break;
		case OFPAT_VENDOR:
			alen_bogus = alen % 8 != 0; /* already >= 8 so far */
			break;
		default:
			skip = 1;
		}
		if (alen_bogus) {
			printf(" (bogus)");
			skip = 1;
		}
		if (skip) {
			TCHECK2(*cp, alen - 4);
			cp += alen - 4;
			goto next_action;
		}
		/* OK to decode the rest of the action structure */
		switch (type) {
		case OFPAT_OUTPUT:
			/* port */
			TCHECK2(*cp, 2);
			output_port = EXTRACT_16BITS(cp);
			cp += 2;
			printf(", port %s", tok2str(ofpp_str, "%u", output_port));
			/* max_len */
			TCHECK2(*cp, 2);
			if (output_port == OFPP_CONTROLLER)
				printf(", max_len %u", EXTRACT_16BITS(cp));
			cp += 2;
			break;
		case OFPAT_SET_VLAN_VID:
			/* vlan_vid */
			TCHECK2(*cp, 2);
			printf(", vlan_vid %s", vlan_str(EXTRACT_16BITS(cp)));
			cp += 2;
			/* pad */
			TCHECK2(*cp, 2);
			cp += 2;
			break;
		case OFPAT_SET_VLAN_PCP:
			/* vlan_pcp */
			TCHECK2(*cp, 1);
			printf(", vlan_pcp %s", pcp_str(*cp));
			cp += 1;
			/* pad */
			TCHECK2(*cp, 3);
			cp += 3;
			break;
		case OFPAT_SET_DL_SRC:
		case OFPAT_SET_DL_DST:
			/* dl_addr */
			TCHECK2(*cp, OFP_ETH_ALEN);
			printf(", dl_addr %s", etheraddr_string(cp));
			cp += OFP_ETH_ALEN;
			/* pad */
			TCHECK2(*cp, 6);
			cp += 6;
			break;
		case OFPAT_SET_NW_SRC:
		case OFPAT_SET_NW_DST:
			/* nw_addr */
			TCHECK2(*cp, 4);
			printf(", nw_addr %s", ipaddr_string(cp));
			cp += 4;
			break;
		case OFPAT_SET_NW_TOS:
			/* nw_tos */
			TCHECK2(*cp, 1);
			printf(", nw_tos 0x%02x", *cp);
			cp += 1;
			/* pad */
			TCHECK2(*cp, 3);
			cp += 3;
			break;
		case OFPAT_SET_TP_SRC:
		case OFPAT_SET_TP_DST:
			/* nw_tos */
			TCHECK2(*cp, 2);
			printf(", tp_port %u", EXTRACT_16BITS(cp));
			cp += 2;
			/* pad */
			TCHECK2(*cp, 2);
			cp += 2;
			break;
		case OFPAT_ENQUEUE:
			/* port */
			TCHECK2(*cp, 2);
			printf(", port %s", tok2str(ofpp_str, "%u", EXTRACT_16BITS(cp)));
			cp += 2;
			/* pad */
			TCHECK2(*cp, 6);
			cp += 6;
			/* queue_id */
			TCHECK2(*cp, 4);
			printf(", queue_id %s", tok2str(ofpq_str, "%u", EXTRACT_32BITS(cp)));
			cp += 4;
			break;
		case OFPAT_VENDOR:
			if (ep == (cp = of10_vendor_data_print(cp, ep, alen - 4)))
				return ep; /* end of snapshot */
			break;
		case OFPAT_STRIP_VLAN:
			/* pad */
			TCHECK2(*cp, 4);
			cp += 4;
			break;
		} /* switch */
next_action:
		len -= alen;
	} /* while */
	return cp;

corrupt: /* skip the rest of actions */
	printf(" (corrupt)");
	TCHECK2(*cp0, len0);
	return cp0 + len0;
trunc:
	printf(" [|openflow]");
	return ep;
}

/* [OF10] Section 5.3.1 */
static const u_char *
of10_features_reply_print(const u_char *cp, const u_char *ep, const u_int len) {
	/* datapath_id */
	TCHECK2(*cp, 8);
	printf("\n\t dpid 0x%016" PRIx64, EXTRACT_64BITS(cp));
	cp += 8;
	/* n_buffers */
	TCHECK2(*cp, 4);
	printf(", n_buffers %u", EXTRACT_32BITS(cp));
	cp += 4;
	/* n_tables */
	TCHECK2(*cp, 1);
	printf(", n_tables %u", *cp);
	cp += 1;
	/* pad */
	TCHECK2(*cp, 3);
	cp += 3;
	/* capabilities */
	TCHECK2(*cp, 4);
	printf("\n\t capabilities 0x%08x", EXTRACT_32BITS(cp));
	of10_bitmap_print(ofp_capabilities_bm, EXTRACT_32BITS(cp), OFPCAP_U);
	cp += 4;
	/* actions */
	TCHECK2(*cp, 4);
	printf("\n\t actions 0x%08x", EXTRACT_32BITS(cp));
	of10_bitmap_print(ofpat_bm, EXTRACT_32BITS(cp), OFPAT_U);
	cp += 4;
	/* ports */
	return of10_phy_ports_print(cp, ep, len - OF_SWITCH_FEATURES_LEN);

trunc:
	printf(" [|openflow]");
	return ep;
}

/* [OF10] Section 5.3.3 */
static const u_char *
of10_flow_mod_print(const u_char *cp, const u_char *ep, const u_int len) {
	uint16_t command;

	/* match */
	if (ep == (cp = of10_match_print("\n\t ", cp, ep)))
		return ep; /* end of snapshot */
	/* cookie */
	TCHECK2(*cp, 8);
	printf("\n\t cookie 0x%016" PRIx64, EXTRACT_64BITS(cp));
	cp += 8;
	/* command */
	TCHECK2(*cp, 2);
	command = EXTRACT_16BITS(cp);
	printf(", command %s", tok2str(ofpfc_str, "invalid (0x%04x)", command));
	cp += 2;
	/* idle_timeout */
	TCHECK2(*cp, 2);
	if (EXTRACT_16BITS(cp))
		printf(", idle_timeout %u", EXTRACT_16BITS(cp));
	cp += 2;
	/* hard_timeout */
	TCHECK2(*cp, 2);
	if (EXTRACT_16BITS(cp))
		printf(", hard_timeout %u", EXTRACT_16BITS(cp));
	cp += 2;
	/* priority */
	TCHECK2(*cp, 2);
	if (EXTRACT_16BITS(cp))
		printf(", priority %u", EXTRACT_16BITS(cp));
	cp += 2;
	/* buffer_id */
	TCHECK2(*cp, 4);
	if (command == OFPFC_ADD || command == OFPFC_MODIFY ||
	    command == OFPFC_MODIFY_STRICT)
		printf(", buffer_id %s", tok2str(bufferid_str, "0x%08x", EXTRACT_32BITS(cp)));
	cp += 4;
	/* out_port */
	TCHECK2(*cp, 2);
	if (command == OFPFC_DELETE || command == OFPFC_DELETE_STRICT)
		printf(", out_port %s", tok2str(ofpp_str, "%u", EXTRACT_16BITS(cp)));
	cp += 2;
	/* flags */
	TCHECK2(*cp, 2);
	printf(", flags 0x%04x", EXTRACT_16BITS(cp));
	of10_bitmap_print(ofpff_bm, EXTRACT_16BITS(cp), OFPFF_U);
	cp += 2;
	/* actions */
	return of10_actions_print("\n\t ", cp, ep, len - OF_FLOW_MOD_LEN);

trunc:
	printf(" [|openflow]");
	return ep;
}

/* ibid */
static const u_char *
of10_port_mod_print(const u_char *cp, const u_char *ep) {
	/* port_no */
	TCHECK2(*cp, 2);
	printf("\n\t port_no %s", tok2str(ofpp_str, "%u", EXTRACT_16BITS(cp)));
	cp += 2;
	/* hw_addr */
	TCHECK2(*cp, OFP_ETH_ALEN);
	printf(", hw_addr %s", etheraddr_string(cp));
	cp += OFP_ETH_ALEN;
	/* config */
	TCHECK2(*cp, 4);
	printf("\n\t config 0x%08x", EXTRACT_32BITS(cp));
	of10_bitmap_print(ofppc_bm, EXTRACT_32BITS(cp), OFPPC_U);
	cp += 4;
	/* mask */
	TCHECK2(*cp, 4);
	printf("\n\t mask 0x%08x", EXTRACT_32BITS(cp));
	of10_bitmap_print(ofppc_bm, EXTRACT_32BITS(cp), OFPPC_U);
	cp += 4;
	/* advertise */
	TCHECK2(*cp, 4);
	printf("\n\t advertise 0x%08x", EXTRACT_32BITS(cp));
	of10_bitmap_print(ofppf_bm, EXTRACT_32BITS(cp), OFPPF_U);
	cp += 4;
	/* pad */
	TCHECK2(*cp, 4);
	return cp + 4;

trunc:
	printf(" [|openflow]");
	return ep;
}

/* [OF10] Section 5.3.5 */
static const u_char *
of10_stats_request_print(const u_char *cp, const u_char *ep, u_int len) {
	const u_char *cp0 = cp;
	const u_int len0 = len;
	uint16_t type;

	/* type */
	TCHECK2(*cp, 2);
	type = EXTRACT_16BITS(cp);
	cp += 2;
	printf("\n\t type %s", tok2str(ofpst_str, "invalid (0x%04x)", type));
	/* flags */
	TCHECK2(*cp, 2);
	printf(", flags 0x%04x", EXTRACT_16BITS(cp));
	if (EXTRACT_16BITS(cp))
		printf(" (bogus)");
	cp += 2;
	/* type-specific body of one of fixed lengths */
	len -= OF_STATS_REQUEST_LEN;
	switch(type) {
	case OFPST_DESC:
	case OFPST_TABLE:
		if (len)
			goto corrupt;
		return cp;
	case OFPST_FLOW:
	case OFPST_AGGREGATE:
		if (len != OF_FLOW_STATS_REQUEST_LEN)
			goto corrupt;
		/* match */
		if (ep == (cp = of10_match_print("\n\t ", cp, ep)))
			return ep; /* end of snapshot */
		/* table_id */
		TCHECK2(*cp, 1);
		printf("\n\t table_id %s", tok2str(tableid_str, "%u", *cp));
		cp += 1;
		/* pad */
		TCHECK2(*cp, 1);
		cp += 1;
		/* out_port */
		TCHECK2(*cp, 2);
		printf(", out_port %s", tok2str(ofpp_str, "%u", EXTRACT_16BITS(cp)));
		return cp + 2;
	case OFPST_PORT:
		if (len != OF_PORT_STATS_REQUEST_LEN)
			goto corrupt;
		/* port_no */
		TCHECK2(*cp, 2);
		printf("\n\t port_no %s", tok2str(ofpp_str, "%u", EXTRACT_16BITS(cp)));
		cp += 2;
		/* pad */
		TCHECK2(*cp, 6);
		return cp + 6;
	case OFPST_QUEUE:
		if (len != OF_QUEUE_STATS_REQUEST_LEN)
			goto corrupt;
		/* port_no */
		TCHECK2(*cp, 2);
		printf("\n\t port_no %s", tok2str(ofpp_str, "%u", EXTRACT_16BITS(cp)));
		cp += 2;
		/* pad */
		TCHECK2(*cp, 2);
		cp += 2;
		/* queue_id */
		TCHECK2(*cp, 4);
		printf(", queue_id %s", tok2str(ofpq_str, "%u", EXTRACT_32BITS(cp)));
		return cp + 4;
	case OFPST_VENDOR:
		return of10_vendor_data_print(cp, ep, len);
	}
	return cp;

corrupt: /* skip the message body */
	printf(" (corrupt)");
	TCHECK2(*cp0, len0);
	return cp0 + len0;
trunc:
	printf(" [|openflow]");
	return ep;
}

/* ibid */
static const u_char *
of10_desc_stats_reply_print(const u_char *cp, const u_char *ep, const u_int len) {
	if (len != OF_DESC_STATS_LEN)
		goto corrupt;
	/* mfr_desc */
	TCHECK2(*cp, DESC_STR_LEN);
	printf("\n\t  mfr_desc '");
	fn_print(cp, cp + DESC_STR_LEN);
	printf("'");
	cp += DESC_STR_LEN;
	/* hw_desc */
	TCHECK2(*cp, DESC_STR_LEN);
	printf("\n\t  hw_desc '");
	fn_print(cp, cp + DESC_STR_LEN);
	printf("'");
	cp += DESC_STR_LEN;
	/* sw_desc */
	TCHECK2(*cp, DESC_STR_LEN);
	printf("\n\t  sw_desc '");
	fn_print(cp, cp + DESC_STR_LEN);
	printf("'");
	cp += DESC_STR_LEN;
	/* serial_num */
	TCHECK2(*cp, SERIAL_NUM_LEN);
	printf("\n\t  serial_num '");
	fn_print(cp, cp + SERIAL_NUM_LEN);
	printf("'");
	cp += SERIAL_NUM_LEN;
	/* dp_desc */
	TCHECK2(*cp, DESC_STR_LEN);
	printf("\n\t  dp_desc '");
	fn_print(cp, cp + DESC_STR_LEN);
	printf("'");
	return cp + DESC_STR_LEN;

corrupt: /* skip the message body */
	printf(" (corrupt)");
	TCHECK2(*cp, len);
	return cp + len;
trunc:
	printf(" [|openflow]");
	return ep;
}

/* ibid */
static const u_char *
of10_flow_stats_reply_print(const u_char *cp, const u_char *ep, u_int len) {
	const u_char *cp0 = cp;
	const u_int len0 = len;
	uint16_t entry_len;

	while (len) {
		if (len < OF_FLOW_STATS_LEN)
			goto corrupt;
		/* length */
		TCHECK2(*cp, 2);
		entry_len = EXTRACT_16BITS(cp);
		printf("\n\t length %u", entry_len);
		if (entry_len < OF_FLOW_STATS_LEN || entry_len > len)
			goto corrupt;
		cp += 2;
		/* table_id */
		TCHECK2(*cp, 1);
		printf(", table_id %s", tok2str(tableid_str, "%u", *cp));
		cp += 1;
		/* pad */
		TCHECK2(*cp, 1);
		cp += 1;
		/* match */
		if (ep == (cp = of10_match_print("\n\t  ", cp, ep)))
			return ep; /* end of snapshot */
		/* duration_sec */
		TCHECK2(*cp, 4);
		printf("\n\t  duration_sec %u", EXTRACT_32BITS(cp));
		cp += 4;
		/* duration_nsec */
		TCHECK2(*cp, 4);
		printf(", duration_nsec %u", EXTRACT_32BITS(cp));
		cp += 4;
		/* priority */
		TCHECK2(*cp, 2);
		printf(", priority %u", EXTRACT_16BITS(cp));
		cp += 2;
		/* idle_timeout */
		TCHECK2(*cp, 2);
		printf(", idle_timeout %u", EXTRACT_16BITS(cp));
		cp += 2;
		/* hard_timeout */
		TCHECK2(*cp, 2);
		printf(", hard_timeout %u", EXTRACT_16BITS(cp));
		cp += 2;
		/* pad2 */
		TCHECK2(*cp, 6);
		cp += 6;
		/* cookie */
		TCHECK2(*cp, 8);
		printf(", cookie 0x%016" PRIx64, EXTRACT_64BITS(cp));
		cp += 8;
		/* packet_count */
		TCHECK2(*cp, 8);
		printf(", packet_count %" PRIu64, EXTRACT_64BITS(cp));
		cp += 8;
		/* byte_count */
		TCHECK2(*cp, 8);
		printf(", byte_count %" PRIu64, EXTRACT_64BITS(cp));
		cp += 8;
		/* actions */
		if (ep == (cp = of10_actions_print("\n\t  ", cp, ep, entry_len - OF_FLOW_STATS_LEN)))
			return ep; /* end of snapshot */

		len -= entry_len;
	} /* while */
	return cp;

corrupt: /* skip the rest of flow statistics entries */
	printf(" (corrupt)");
	TCHECK2(*cp0, len0);
	return cp0 + len0;
trunc:
	printf(" [|openflow]");
	return ep;
}

/* ibid */
static const u_char *
of10_aggregate_stats_reply_print(const u_char *cp, const u_char *ep,
                                 const u_int len) {
	if (len != OF_AGGREGATE_STATS_REPLY_LEN)
		goto corrupt;
	/* packet_count */
	TCHECK2(*cp, 8);
	printf("\n\t packet_count %" PRIu64, EXTRACT_64BITS(cp));
	cp += 8;
	/* byte_count */
	TCHECK2(*cp, 8);
	printf(", byte_count %" PRIu64, EXTRACT_64BITS(cp));
	cp += 8;
	/* flow_count */
	TCHECK2(*cp, 4);
	printf(", flow_count %u", EXTRACT_32BITS(cp));
	cp += 4;
	/* pad */
	TCHECK2(*cp, 4);
	return cp + 4;

corrupt: /* skip the message body */
	printf(" (corrupt)");
	TCHECK2(*cp, len);
	return cp + len;
trunc:
	printf(" [|openflow]");
	return ep;
}

/* ibid */
static const u_char *
of10_table_stats_reply_print(const u_char *cp, const u_char *ep, u_int len) {
	const u_char *cp0 = cp;
	const u_int len0 = len;

	while (len) {
		if (len < OF_TABLE_STATS_LEN)
			goto corrupt;
		/* table_id */
		TCHECK2(*cp, 1);
		printf("\n\t table_id %s", tok2str(tableid_str, "%u", *cp));
		cp += 1;
		/* pad */
		TCHECK2(*cp, 3);
		cp += 3;
		/* name */
		TCHECK2(*cp, OFP_MAX_TABLE_NAME_LEN);
		printf(", name '");
		fn_print(cp, cp + OFP_MAX_TABLE_NAME_LEN);
		printf("'");
		cp += OFP_MAX_TABLE_NAME_LEN;
		/* wildcards */
		TCHECK2(*cp, 4);
		printf("\n\t  wildcards 0x%08x", EXTRACT_32BITS(cp));
		of10_bitmap_print(ofpfw_bm, EXTRACT_32BITS(cp), OFPFW_U);
		cp += 4;
		/* max_entries */
		TCHECK2(*cp, 4);
		printf("\n\t  max_entries %u", EXTRACT_32BITS(cp));
		cp += 4;
		/* active_count */
		TCHECK2(*cp, 4);
		printf(", active_count %u", EXTRACT_32BITS(cp));
		cp += 4;
		/* lookup_count */
		TCHECK2(*cp, 8);
		printf(", lookup_count %" PRIu64, EXTRACT_64BITS(cp));
		cp += 8;
		/* matched_count */
		TCHECK2(*cp, 8);
		printf(", matched_count %" PRIu64, EXTRACT_64BITS(cp));
		cp += 8;

		len -= OF_TABLE_STATS_LEN;
	} /* while */
	return cp;

corrupt: /* skip the undersized trailing data */
	printf(" (corrupt)");
	TCHECK2(*cp0, len0);
	return cp0 + len0;
trunc:
	printf(" [|openflow]");
	return ep;
}

/* ibid */
static const u_char *
of10_port_stats_reply_print(const u_char *cp, const u_char *ep, u_int len) {
	const u_char *cp0 = cp;
	const u_int len0 = len;

	while (len) {
		if (len < OF_PORT_STATS_LEN)
			goto corrupt;
		/* port_no */
		TCHECK2(*cp, 2);
		printf("\n\t  port_no %s", tok2str(ofpp_str, "%u", EXTRACT_16BITS(cp)));
		cp += 2;
		if (vflag < 2) {
			TCHECK2(*cp, OF_PORT_STATS_LEN - 2);
			cp += OF_PORT_STATS_LEN - 2;
			goto next_port;
		}
		/* pad */
		TCHECK2(*cp, 6);
		cp += 6;
		/* rx_packets */
		TCHECK2(*cp, 8);
		printf(", rx_packets %" PRIu64, EXTRACT_64BITS(cp));
		cp += 8;
		/* tx_packets */
		TCHECK2(*cp, 8);
		printf(", tx_packets %" PRIu64, EXTRACT_64BITS(cp));
		cp += 8;
		/* rx_bytes */
		TCHECK2(*cp, 8);
		printf(", rx_bytes %" PRIu64, EXTRACT_64BITS(cp));
		cp += 8;
		/* tx_bytes */
		TCHECK2(*cp, 8);
		printf(", tx_bytes %" PRIu64, EXTRACT_64BITS(cp));
		cp += 8;
		/* rx_dropped */
		TCHECK2(*cp, 8);
		printf(", rx_dropped %" PRIu64, EXTRACT_64BITS(cp));
		cp += 8;
		/* tx_dropped */
		TCHECK2(*cp, 8);
		printf(", tx_dropped %" PRIu64, EXTRACT_64BITS(cp));
		cp += 8;
		/* rx_errors */
		TCHECK2(*cp, 8);
		printf(", rx_errors %" PRIu64, EXTRACT_64BITS(cp));
		cp += 8;
		/* tx_errors */
		TCHECK2(*cp, 8);
		printf(", tx_errors %" PRIu64, EXTRACT_64BITS(cp));
		cp += 8;
		/* rx_frame_err */
		TCHECK2(*cp, 8);
		printf(", rx_frame_err %" PRIu64, EXTRACT_64BITS(cp));
		cp += 8;
		/* rx_over_err */
		TCHECK2(*cp, 8);
		printf(", rx_over_err %" PRIu64, EXTRACT_64BITS(cp));
		cp += 8;
		/* rx_crc_err */
		TCHECK2(*cp, 8);
		printf(", rx_crc_err %" PRIu64, EXTRACT_64BITS(cp));
		cp += 8;
		/* collisions */
		TCHECK2(*cp, 8);
		printf(", collisions %" PRIu64, EXTRACT_64BITS(cp));
		cp += 8;
next_port:
		len -= OF_PORT_STATS_LEN;
	} /* while */
	return cp;

corrupt: /* skip the undersized trailing data */
	printf(" (corrupt)");
	TCHECK2(*cp0, len0);
	return cp0 + len0;
trunc:
	printf(" [|openflow]");
	return ep;
}

/* ibid */
static const u_char *
of10_queue_stats_reply_print(const u_char *cp, const u_char *ep, u_int len) {
	const u_char *cp0 = cp;
	const u_int len0 = len;

	while (len) {
		if (len < OF_QUEUE_STATS_LEN)
			goto corrupt;
		/* port_no */
		TCHECK2(*cp, 2);
		printf("\n\t  port_no %s", tok2str(ofpp_str, "%u", EXTRACT_16BITS(cp)));
		cp += 2;
		/* pad */
		TCHECK2(*cp, 2);
		cp += 2;
		/* queue_id */
		TCHECK2(*cp, 4);
		printf(", queue_id %u", EXTRACT_32BITS(cp));
		cp += 4;
		/* tx_bytes */
		TCHECK2(*cp, 8);
		printf(", tx_bytes %" PRIu64, EXTRACT_64BITS(cp));
		cp += 8;
		/* tx_packets */
		TCHECK2(*cp, 8);
		printf(", tx_packets %" PRIu64, EXTRACT_64BITS(cp));
		cp += 8;
		/* tx_errors */
		TCHECK2(*cp, 8);
		printf(", tx_errors %" PRIu64, EXTRACT_64BITS(cp));
		cp += 8;

		len -= OF_QUEUE_STATS_LEN;
	} /* while */
	return cp;

corrupt: /* skip the undersized trailing data */
	printf(" (corrupt)");
	TCHECK2(*cp0, len0);
	return cp0 + len0;
trunc:
	printf(" [|openflow]");
	return ep;
}

/* ibid */
static const u_char *
of10_stats_reply_print(const u_char *cp, const u_char *ep, const u_int len) {
	const u_char *cp0 = cp;
	uint16_t type;

	/* type */
	TCHECK2(*cp, 2);
	type = EXTRACT_16BITS(cp);
	printf("\n\t type %s", tok2str(ofpst_str, "invalid (0x%04x)", type));
	cp += 2;
	/* flags */
	TCHECK2(*cp, 2);
	printf(", flags 0x%04x", EXTRACT_16BITS(cp));
	of10_bitmap_print(ofpsf_reply_bm, EXTRACT_16BITS(cp), OFPSF_REPLY_U);
	cp += 2;

	if (vflag > 0) {
		const u_char *(*decoder)(const u_char *, const u_char *, u_int) =
			type == OFPST_DESC      ? of10_desc_stats_reply_print      :
			type == OFPST_FLOW      ? of10_flow_stats_reply_print      :
			type == OFPST_AGGREGATE ? of10_aggregate_stats_reply_print :
			type == OFPST_TABLE     ? of10_table_stats_reply_print     :
			type == OFPST_PORT      ? of10_port_stats_reply_print      :
			type == OFPST_QUEUE     ? of10_queue_stats_reply_print     :
			type == OFPST_VENDOR    ? of10_vendor_data_print           :
			NULL;
		if (decoder != NULL)
			return decoder(cp, ep, len - OF_STATS_REPLY_LEN);
	}
	TCHECK2(*cp0, len);
	return cp0 + len;

trunc:
	printf(" [|openflow]");
	return ep;
}

/* [OF10] Section 5.3.6 */
static const u_char *
of10_packet_out_print(const u_char *cp, const u_char *ep, const u_int len) {
	const u_char *cp0 = cp;
	const u_int len0 = len;
	uint16_t actions_len;

	/* buffer_id */
	TCHECK2(*cp, 4);
	printf("\n\t buffer_id 0x%08x", EXTRACT_32BITS(cp));
	cp += 4;
	/* in_port */
	TCHECK2(*cp, 2);
	printf(", in_port %s", tok2str(ofpp_str, "%u", EXTRACT_16BITS(cp)));
	cp += 2;
	/* actions_len */
	TCHECK2(*cp, 2);
	actions_len = EXTRACT_16BITS(cp);
	cp += 2;
	if (actions_len > len - OF_PACKET_OUT_LEN)
		goto corrupt;
	/* actions */
	if (ep == (cp = of10_actions_print("\n\t ", cp, ep, actions_len)))
		return ep; /* end of snapshot */
	/* data */
	return of10_packet_data_print(cp, ep, len - OF_PACKET_OUT_LEN - actions_len);

corrupt: /* skip the rest of the message body */
	printf(" (corrupt)");
	TCHECK2(*cp0, len0);
	return cp0 + len0;
trunc:
	printf(" [|openflow]");
	return ep;
}

/* [OF10] Section 5.4.1 */
static const u_char *
of10_packet_in_print(const u_char *cp, const u_char *ep, const u_int len) {
	/* buffer_id */
	TCHECK2(*cp, 4);
	printf("\n\t buffer_id %s", tok2str(bufferid_str, "0x%08x", EXTRACT_32BITS(cp)));
	cp += 4;
	/* total_len */
	TCHECK2(*cp, 2);
	printf(", total_len %u", EXTRACT_16BITS(cp));
	cp += 2;
	/* in_port */
	TCHECK2(*cp, 2);
	printf(", in_port %s", tok2str(ofpp_str, "%u", EXTRACT_16BITS(cp)));
	cp += 2;
	/* reason */
	TCHECK2(*cp, 1);
	printf(", reason %s", tok2str(ofpr_str, "invalid (0x%02x)", *cp));
	cp += 1;
	/* pad */
	TCHECK2(*cp, 1);
	cp += 1;
	/* data */
	/* 2 mock octets count in OF_PACKET_IN_LEN but not in len */
	return of10_packet_data_print(cp, ep, len - (OF_PACKET_IN_LEN - 2));

trunc:
	printf(" [|openflow]");
	return ep;
}

/* [OF10] Section 5.4.2 */
static const u_char *
of10_flow_removed_print(const u_char *cp, const u_char *ep) {
	/* match */
	if (ep == (cp = of10_match_print("\n\t ", cp, ep)))
		return ep; /* end of snapshot */
	/* cookie */
	TCHECK2(*cp, 8);
	printf("\n\t cookie 0x%016" PRIx64, EXTRACT_64BITS(cp));
	cp += 8;
	/* priority */
	TCHECK2(*cp, 2);
	if (EXTRACT_16BITS(cp))
		printf(", priority %u", EXTRACT_16BITS(cp));
	cp += 2;
	/* reason */
	TCHECK2(*cp, 1);
	printf(", reason %s", tok2str(ofprr_str, "unknown (0x%02x)", *cp));
	cp += 1;
	/* pad */
	TCHECK2(*cp, 1);
	cp += 1;
	/* duration_sec */
	TCHECK2(*cp, 4);
	printf(", duration_sec %u", EXTRACT_32BITS(cp));
	cp += 4;
	/* duration_nsec */
	TCHECK2(*cp, 4);
	printf(", duration_nsec %u", EXTRACT_32BITS(cp));
	cp += 4;
	/* idle_timeout */
	TCHECK2(*cp, 2);
	if (EXTRACT_16BITS(cp))
		printf(", idle_timeout %u", EXTRACT_16BITS(cp));
	cp += 2;
	/* pad2 */
	TCHECK2(*cp, 2);
	cp += 2;
	/* packet_count */
	TCHECK2(*cp, 8);
	printf(", packet_count %" PRIu64, EXTRACT_64BITS(cp));
	cp += 8;
	/* byte_count */
	TCHECK2(*cp, 8);
	printf(", byte_count %" PRIu64, EXTRACT_64BITS(cp));
	return cp + 8;

trunc:
	printf(" [|openflow]");
	return ep;
}

/* [OF10] Section 5.4.4 */
static const u_char *
of10_error_print(const u_char *cp, const u_char *ep, const u_int len) {
	uint16_t type;
	const struct tok *code_str;

	/* type */
	TCHECK2(*cp, 2);
	type = EXTRACT_16BITS(cp);
	cp += 2;
	printf("\n\t type %s", tok2str(ofpet_str, "invalid (0x%04x)", type));
	/* code */
	TCHECK2(*cp, 2);
	code_str =
		type == OFPET_HELLO_FAILED    ? ofphfc_str  :
		type == OFPET_BAD_REQUEST     ? ofpbrc_str  :
		type == OFPET_BAD_ACTION      ? ofpbac_str  :
		type == OFPET_FLOW_MOD_FAILED ? ofpfmfc_str :
		type == OFPET_PORT_MOD_FAILED ? ofppmfc_str :
		type == OFPET_QUEUE_OP_FAILED ? ofpqofc_str :
		empty_str;
	printf(", code %s", tok2str(code_str, "invalid (0x%04x)", EXTRACT_16BITS(cp)));
	cp += 2;
	/* data */
	return of10_data_print(cp, ep, len - OF_ERROR_MSG_LEN);

trunc:
	printf(" [|openflow]");
	return ep;
}

const u_char *
of10_header_body_print(const u_char *cp, const u_char *ep, const uint8_t type,
                       const uint16_t len, const uint32_t xid) {
	const u_char *cp0 = cp;
	const u_int len0 = len;
	/* Thus far message length is not less than the basic header size, but most
	 * message types have additional assorted constraints on the length. Wherever
	 * possible, check that message length meets the constraint, in remaining
	 * cases check that the length is OK to begin decoding and leave any final
	 * verification up to a lower-layer function. When the current message is
	 * corrupt, proceed to the next message. */

	/* [OF10] Section 5.1 */
	printf("\n\tversion 1.0, type %s, length %u, xid 0x%08x",
	       tok2str(ofpt_str, "invalid (0x%02x)", type), len, xid);
	switch (type) {
	/* OpenFlow header only. */
	case OFPT_FEATURES_REQUEST: /* [OF10] Section 5.3.1 */
	case OFPT_GET_CONFIG_REQUEST: /* [OF10] Section 5.3.2 */
	case OFPT_BARRIER_REQUEST: /* [OF10] Section 5.3.7 */
	case OFPT_BARRIER_REPLY: /* ibid */
		if (len != OF_HEADER_LEN)
			goto corrupt;
		break;

	/* OpenFlow header and fixed-size message body. */
	case OFPT_SET_CONFIG: /* [OF10] Section 5.3.2 */
	case OFPT_GET_CONFIG_REPLY: /* ibid */
		if (len != OF_SWITCH_CONFIG_LEN)
			goto corrupt;
		if (vflag < 1)
			goto next_message;
		/* flags */
		TCHECK2(*cp, 2);
		printf("\n\t flags %s", tok2str(ofp_config_str, "invalid (0x%04x)", EXTRACT_16BITS(cp)));
		cp += 2;
		/* miss_send_len */
		TCHECK2(*cp, 2);
		printf(", miss_send_len %u", EXTRACT_16BITS(cp));
		return cp + 2;
	case OFPT_PORT_MOD:
		if (len != OF_PORT_MOD_LEN)
			goto corrupt;
		if (vflag < 1)
			goto next_message;
		return of10_port_mod_print(cp, ep);
	case OFPT_QUEUE_GET_CONFIG_REQUEST: /* [OF10] Section 5.3.4 */
		if (len != OF_QUEUE_GET_CONFIG_REQUEST_LEN)
			goto corrupt;
		if (vflag < 1)
			goto next_message;
		/* port */
		TCHECK2(*cp, 2);
		printf("\n\t port_no %s", tok2str(ofpp_str, "%u", EXTRACT_16BITS(cp)));
		cp += 2;
		/* pad */
		TCHECK2(*cp, 2);
		return cp + 2;
	case OFPT_FLOW_REMOVED:
		if (len != OF_FLOW_REMOVED_LEN)
			goto corrupt;
		if (vflag < 1)
			goto next_message;
		return of10_flow_removed_print(cp, ep);
	case OFPT_PORT_STATUS: /* [OF10] Section 5.4.3 */
		if (len != OF_PORT_STATUS_LEN)
			goto corrupt;
		if (vflag < 1)
			goto next_message;
		/* reason */
		TCHECK2(*cp, 1);
		printf("\n\t reason %s", tok2str(ofppr_str, "invalid (0x%02x)", *cp));
		cp += 1;
		/* pad */
		TCHECK2(*cp, 7);
		cp += 7;
		/* desc */
		return of10_phy_ports_print(cp, ep, OF_PHY_PORT_LEN);

	/* OpenFlow header, fixed-size message body and n * fixed-size data units. */
	case OFPT_FEATURES_REPLY:
		if (len < OF_SWITCH_FEATURES_LEN)
			goto corrupt;
		if (vflag < 1)
			goto next_message;
		return of10_features_reply_print(cp, ep, len);

	/* OpenFlow header and variable-size data. */
	case OFPT_HELLO: /* [OF10] Section 5.5.1 */
	case OFPT_ECHO_REQUEST: /* [OF10] Section 5.5.2 */
	case OFPT_ECHO_REPLY: /* [OF10] Section 5.5.3 */
		if (vflag < 1)
			goto next_message;
		return of10_data_print(cp, ep, len - OF_HEADER_LEN);

	/* OpenFlow header, fixed-size message body and variable-size data. */
	case OFPT_ERROR:
		if (len < OF_ERROR_MSG_LEN)
			goto corrupt;
		if (vflag < 1)
			goto next_message;
		return of10_error_print(cp, ep, len);
	case OFPT_VENDOR:
	  /* [OF10] Section 5.5.4 */
		if (len < OF_VENDOR_HEADER_LEN)
			goto corrupt;
		if (vflag < 1)
			goto next_message;
		return of10_vendor_data_print(cp, ep, len - OF_HEADER_LEN);
	case OFPT_PACKET_IN:
		/* 2 mock octets count in OF_PACKET_IN_LEN but not in len */
		if (len < OF_PACKET_IN_LEN - 2)
			goto corrupt;
		if (vflag < 1)
			goto next_message;
		return of10_packet_in_print(cp, ep, len);

	/* a. OpenFlow header. */
	/* b. OpenFlow header and one of the fixed-size message bodies. */
	/* c. OpenFlow header, fixed-size message body and variable-size data. */
	case OFPT_STATS_REQUEST:
		if (len < OF_STATS_REQUEST_LEN)
			goto corrupt;
		if (vflag < 1)
			goto next_message;
		return of10_stats_request_print(cp, ep, len);

	/* a. OpenFlow header and fixed-size message body. */
	/* b. OpenFlow header and n * fixed-size data units. */
	/* c. OpenFlow header and n * variable-size data units. */
	/* d. OpenFlow header, fixed-size message body and variable-size data. */
	case OFPT_STATS_REPLY:
		if (len < OF_STATS_REPLY_LEN)
			goto corrupt;
		if (vflag < 1)
			goto next_message;
		return of10_stats_reply_print(cp, ep, len);

	/* OpenFlow header and n * variable-size data units and variable-size data. */
	case OFPT_PACKET_OUT:
		if (len < OF_PACKET_OUT_LEN)
			goto corrupt;
		if (vflag < 1)
			goto next_message;
		return of10_packet_out_print(cp, ep, len);

	/* OpenFlow header, fixed-size message body and n * variable-size data units. */
	case OFPT_FLOW_MOD:
		if (len < OF_FLOW_MOD_LEN)
			goto corrupt;
		if (vflag < 1)
			goto next_message;
		return of10_flow_mod_print(cp, ep, len);

	/* OpenFlow header, fixed-size message body and n * variable-size data units. */
	case OFPT_QUEUE_GET_CONFIG_REPLY: /* [OF10] Section 5.3.4 */
		if (len < OF_QUEUE_GET_CONFIG_REPLY_LEN)
			goto corrupt;
		if (vflag < 1)
			goto next_message;
		/* port */
		TCHECK2(*cp, 2);
		printf("\n\t port_no %s", tok2str(ofpp_str, "%u", EXTRACT_16BITS(cp)));
		cp += 2;
		/* pad */
		TCHECK2(*cp, 6);
		cp += 6;
		/* queues */
		return of10_queues_print(cp, ep, len - OF_QUEUE_GET_CONFIG_REPLY_LEN);
	} /* switch (type) */
	goto next_message;

corrupt: /* skip the message body */
	printf(" (corrupt)");
next_message:
	TCHECK2(*cp0, len0 - OF_HEADER_LEN);
	return cp0 + len0 - OF_HEADER_LEN;
trunc:
	printf(" [|openflow]");
	return ep;
}
