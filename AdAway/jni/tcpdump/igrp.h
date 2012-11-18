/* @(#) $Header: /tcpdump/master/tcpdump/igrp.h,v 1.6 2002/12/11 07:13:52 guy Exp $ (LBL) */
/* Cisco IGRP definitions */

/* IGRP Header */

struct igrphdr {
	u_int8_t ig_vop;	/* protocol version number / opcode */
#define IGRP_V(x)	(((x) & 0xf0) >> 4)
#define IGRP_OP(x)	((x) & 0x0f)
	u_int8_t ig_ed;		/* edition number */
	u_int16_t ig_as;	/* autonomous system number */
	u_int16_t ig_ni;	/* number of subnet in local net */
	u_int16_t ig_ns;	/* number of networks in AS */
	u_int16_t ig_nx;	/* number of networks ouside AS */
	u_int16_t ig_sum;	/* checksum of IGRP header & data */
};

#define IGRP_UPDATE	1
#define IGRP_REQUEST	2

/* IGRP routing entry */

struct igrprte {
	u_int8_t igr_net[3];	/* 3 significant octets of IP address */
	u_int8_t igr_dly[3];	/* delay in tens of microseconds */
	u_int8_t igr_bw[3];	/* bandwidth in units of 1 kb/s */
	u_int8_t igr_mtu[2];	/* MTU in octets */
	u_int8_t igr_rel;	/* percent packets successfully tx/rx */
	u_int8_t igr_ld;	/* percent of channel occupied */
	u_int8_t igr_hct;	/* hop count */
};

#define IGRP_RTE_SIZE	14	/* don't believe sizeof ! */
