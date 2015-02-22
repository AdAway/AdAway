/* $FreeBSD: src/sys/net80211/ieee80211_radiotap.h,v 1.5 2005/01/22 20:12:05 sam Exp $ */
/* NetBSD: ieee802_11_radio.h,v 1.2 2006/02/26 03:04:03 dyoung Exp  */
/* $Header: /tcpdump/master/tcpdump/ieee802_11_radio.h,v 1.3 2007-08-29 02:31:44 mcr Exp $ */

/*-
 * Copyright (c) 2003, 2004 David Young.  All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 * 1. Redistributions of source code must retain the above copyright
 *    notice, this list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright
 *    notice, this list of conditions and the following disclaimer in the
 *    documentation and/or other materials provided with the distribution.
 * 3. The name of David Young may not be used to endorse or promote
 *    products derived from this software without specific prior
 *    written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY DAVID YOUNG ``AS IS'' AND ANY
 * EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO,
 * THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A
 * PARTICULAR PURPOSE ARE DISCLAIMED.  IN NO EVENT SHALL DAVID
 * YOUNG BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED
 * TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
 * DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
 * OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY
 * OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY
 * OF SUCH DAMAGE.
 */
#ifndef _NET_IF_IEEE80211RADIOTAP_H_
#define _NET_IF_IEEE80211RADIOTAP_H_

/* A generic radio capture format is desirable. It must be
 * rigidly defined (e.g., units for fields should be given),
 * and easily extensible.
 *
 * The following is an extensible radio capture format. It is
 * based on a bitmap indicating which fields are present.
 *
 * I am trying to describe precisely what the application programmer
 * should expect in the following, and for that reason I tell the
 * units and origin of each measurement (where it applies), or else I
 * use sufficiently weaselly language ("is a monotonically nondecreasing
 * function of...") that I cannot set false expectations for lawyerly
 * readers.
 */

/*
 * The radio capture header precedes the 802.11 header.
 *
 * Note well: all radiotap fields are little-endian.
 */
struct ieee80211_radiotap_header {
	u_int8_t	it_version;	/* Version 0. Only increases
					 * for drastic changes,
					 * introduction of compatible
					 * new fields does not count.
					 */
	u_int8_t	it_pad;
	u_int16_t       it_len;         /* length of the whole
					 * header in bytes, including
					 * it_version, it_pad,
					 * it_len, and data fields.
					 */
	u_int32_t       it_present;     /* A bitmap telling which
					 * fields are present. Set bit 31
					 * (0x80000000) to extend the
					 * bitmap by another 32 bits.
					 * Additional extensions are made
					 * by setting bit 31.
					 */
};

/* Name                                 Data type       Units
 * ----                                 ---------       -----
 *
 * IEEE80211_RADIOTAP_TSFT              u_int64_t       microseconds
 *
 *      Value in microseconds of the MAC's 64-bit 802.11 Time
 *      Synchronization Function timer when the first bit of the
 *      MPDU arrived at the MAC. For received frames, only.
 *
 * IEEE80211_RADIOTAP_CHANNEL           2 x u_int16_t   MHz, bitmap
 *
 *      Tx/Rx frequency in MHz, followed by flags (see below).
 *	Note that IEEE80211_RADIOTAP_XCHANNEL must be used to
 *	represent an HT channel as there is not enough room in
 *	the flags word.
 *
 * IEEE80211_RADIOTAP_FHSS              u_int16_t       see below
 *
 *      For frequency-hopping radios, the hop set (first byte)
 *      and pattern (second byte).
 *
 * IEEE80211_RADIOTAP_RATE              u_int8_t        500kb/s or index
 *
 *      Tx/Rx data rate.  If bit 0x80 is set then it represents an
 *	an MCS index and not an IEEE rate.
 *
 * IEEE80211_RADIOTAP_DBM_ANTSIGNAL     int8_t          decibels from
 *                                                      one milliwatt (dBm)
 *
 *      RF signal power at the antenna, decibel difference from
 *      one milliwatt.
 *
 * IEEE80211_RADIOTAP_DBM_ANTNOISE      int8_t          decibels from
 *                                                      one milliwatt (dBm)
 *
 *      RF noise power at the antenna, decibel difference from one
 *      milliwatt.
 *
 * IEEE80211_RADIOTAP_DB_ANTSIGNAL      u_int8_t        decibel (dB)
 *
 *      RF signal power at the antenna, decibel difference from an
 *      arbitrary, fixed reference.
 *
 * IEEE80211_RADIOTAP_DB_ANTNOISE       u_int8_t        decibel (dB)
 *
 *      RF noise power at the antenna, decibel difference from an
 *      arbitrary, fixed reference point.
 *
 * IEEE80211_RADIOTAP_LOCK_QUALITY      u_int16_t       unitless
 *
 *      Quality of Barker code lock. Unitless. Monotonically
 *      nondecreasing with "better" lock strength. Called "Signal
 *      Quality" in datasheets.  (Is there a standard way to measure
 *      this?)
 *
 * IEEE80211_RADIOTAP_TX_ATTENUATION    u_int16_t       unitless
 *
 *      Transmit power expressed as unitless distance from max
 *      power set at factory calibration.  0 is max power.
 *      Monotonically nondecreasing with lower power levels.
 *
 * IEEE80211_RADIOTAP_DB_TX_ATTENUATION u_int16_t       decibels (dB)
 *
 *      Transmit power expressed as decibel distance from max power
 *      set at factory calibration.  0 is max power.  Monotonically
 *      nondecreasing with lower power levels.
 *
 * IEEE80211_RADIOTAP_DBM_TX_POWER      int8_t          decibels from
 *                                                      one milliwatt (dBm)
 *
 *      Transmit power expressed as dBm (decibels from a 1 milliwatt
 *      reference). This is the absolute power level measured at
 *      the antenna port.
 *
 * IEEE80211_RADIOTAP_FLAGS             u_int8_t        bitmap
 *
 *      Properties of transmitted and received frames. See flags
 *      defined below.
 *
 * IEEE80211_RADIOTAP_ANTENNA           u_int8_t        antenna index
 *
 *      Unitless indication of the Rx/Tx antenna for this packet.
 *      The first antenna is antenna 0.
 *
 * IEEE80211_RADIOTAP_RX_FLAGS          u_int16_t       bitmap
 *
 *     Properties of received frames. See flags defined below.
 *
 * IEEE80211_RADIOTAP_XCHANNEL          u_int32_t	bitmap
 *					u_int16_t	MHz
 *					u_int8_t	channel number
 *					u_int8_t	.5 dBm
 *
 *	Extended channel specification: flags (see below) followed by
 *	frequency in MHz, the corresponding IEEE channel number, and
 *	finally the maximum regulatory transmit power cap in .5 dBm
 *	units.  This property supersedes IEEE80211_RADIOTAP_CHANNEL
 *	and only one of the two should be present.
 *
 * IEEE80211_RADIOTAP_MCS		u_int8_t	known
 *					u_int8_t	flags
 *					u_int8_t	mcs
 *
 *	Bitset indicating which fields have known values, followed
 *	by bitset of flag values, followed by the MCS rate index as
 *	in IEEE 802.11n.
 *
 * IEEE80211_RADIOTAP_VENDOR_NAMESPACE
 *					u_int8_t  OUI[3]
 *                                   u_int8_t  subspace
 *                                   u_int16_t length
 *
 *     The Vendor Namespace Field contains three sub-fields. The first
 *     sub-field is 3 bytes long. It contains the vendor's IEEE 802
 *     Organizationally Unique Identifier (OUI). The fourth byte is a
 *     vendor-specific "namespace selector."
 *
 */
enum ieee80211_radiotap_type {
	IEEE80211_RADIOTAP_TSFT = 0,
	IEEE80211_RADIOTAP_FLAGS = 1,
	IEEE80211_RADIOTAP_RATE = 2,
	IEEE80211_RADIOTAP_CHANNEL = 3,
	IEEE80211_RADIOTAP_FHSS = 4,
	IEEE80211_RADIOTAP_DBM_ANTSIGNAL = 5,
	IEEE80211_RADIOTAP_DBM_ANTNOISE = 6,
	IEEE80211_RADIOTAP_LOCK_QUALITY = 7,
	IEEE80211_RADIOTAP_TX_ATTENUATION = 8,
	IEEE80211_RADIOTAP_DB_TX_ATTENUATION = 9,
	IEEE80211_RADIOTAP_DBM_TX_POWER = 10,
	IEEE80211_RADIOTAP_ANTENNA = 11,
	IEEE80211_RADIOTAP_DB_ANTSIGNAL = 12,
	IEEE80211_RADIOTAP_DB_ANTNOISE = 13,
	IEEE80211_RADIOTAP_RX_FLAGS = 14,
	/* NB: gap for netbsd definitions */
	IEEE80211_RADIOTAP_XCHANNEL = 18,
	IEEE80211_RADIOTAP_MCS = 19,
	IEEE80211_RADIOTAP_NAMESPACE = 29,
	IEEE80211_RADIOTAP_VENDOR_NAMESPACE = 30,
	IEEE80211_RADIOTAP_EXT = 31
};

/* channel attributes */
#define	IEEE80211_CHAN_TURBO	0x00010	/* Turbo channel */
#define	IEEE80211_CHAN_CCK	0x00020	/* CCK channel */
#define	IEEE80211_CHAN_OFDM	0x00040	/* OFDM channel */
#define	IEEE80211_CHAN_2GHZ	0x00080	/* 2 GHz spectrum channel. */
#define	IEEE80211_CHAN_5GHZ	0x00100	/* 5 GHz spectrum channel */
#define	IEEE80211_CHAN_PASSIVE	0x00200	/* Only passive scan allowed */
#define	IEEE80211_CHAN_DYN	0x00400	/* Dynamic CCK-OFDM channel */
#define	IEEE80211_CHAN_GFSK	0x00800	/* GFSK channel (FHSS PHY) */
#define	IEEE80211_CHAN_GSM	0x01000	/* 900 MHz spectrum channel */
#define	IEEE80211_CHAN_STURBO	0x02000	/* 11a static turbo channel only */
#define	IEEE80211_CHAN_HALF	0x04000	/* Half rate channel */
#define	IEEE80211_CHAN_QUARTER	0x08000	/* Quarter rate channel */
#define	IEEE80211_CHAN_HT20	0x10000	/* HT 20 channel */
#define	IEEE80211_CHAN_HT40U	0x20000	/* HT 40 channel w/ ext above */
#define	IEEE80211_CHAN_HT40D	0x40000	/* HT 40 channel w/ ext below */

/* Useful combinations of channel characteristics, borrowed from Ethereal */
#define IEEE80211_CHAN_A \
        (IEEE80211_CHAN_5GHZ | IEEE80211_CHAN_OFDM)
#define IEEE80211_CHAN_B \
        (IEEE80211_CHAN_2GHZ | IEEE80211_CHAN_CCK)
#define IEEE80211_CHAN_G \
        (IEEE80211_CHAN_2GHZ | IEEE80211_CHAN_DYN)
#define IEEE80211_CHAN_TA \
        (IEEE80211_CHAN_5GHZ | IEEE80211_CHAN_OFDM | IEEE80211_CHAN_TURBO)
#define IEEE80211_CHAN_TG \
        (IEEE80211_CHAN_2GHZ | IEEE80211_CHAN_DYN  | IEEE80211_CHAN_TURBO)


/* For IEEE80211_RADIOTAP_FLAGS */
#define	IEEE80211_RADIOTAP_F_CFP	0x01	/* sent/received
						 * during CFP
						 */
#define	IEEE80211_RADIOTAP_F_SHORTPRE	0x02	/* sent/received
						 * with short
						 * preamble
						 */
#define	IEEE80211_RADIOTAP_F_WEP	0x04	/* sent/received
						 * with WEP encryption
						 */
#define	IEEE80211_RADIOTAP_F_FRAG	0x08	/* sent/received
						 * with fragmentation
						 */
#define	IEEE80211_RADIOTAP_F_FCS	0x10	/* frame includes FCS */
#define	IEEE80211_RADIOTAP_F_DATAPAD	0x20	/* frame has padding between
						 * 802.11 header and payload
						 * (to 32-bit boundary)
						 */
#define	IEEE80211_RADIOTAP_F_BADFCS	0x40	/* does not pass FCS check */

/* For IEEE80211_RADIOTAP_RX_FLAGS */
#define IEEE80211_RADIOTAP_F_RX_BADFCS	0x0001	/* frame failed crc check */
#define IEEE80211_RADIOTAP_F_RX_PLCP_CRC	0x0002	/* frame failed PLCP CRC check */

/* For IEEE80211_RADIOTAP_MCS known */
#define IEEE80211_RADIOTAP_MCS_BANDWIDTH_KNOWN		0x01
#define IEEE80211_RADIOTAP_MCS_MCS_INDEX_KNOWN		0x02	/* MCS index field */
#define IEEE80211_RADIOTAP_MCS_GUARD_INTERVAL_KNOWN	0x04
#define IEEE80211_RADIOTAP_MCS_HT_FORMAT_KNOWN		0x08
#define IEEE80211_RADIOTAP_MCS_FEC_TYPE_KNOWN		0x10
#define IEEE80211_RADIOTAP_MCS_STBC_KNOWN		0x20

/* For IEEE80211_RADIOTAP_MCS flags */
#define IEEE80211_RADIOTAP_MCS_BANDWIDTH_MASK	0x03
#define IEEE80211_RADIOTAP_MCS_BANDWIDTH_20	0
#define IEEE80211_RADIOTAP_MCS_BANDWIDTH_40	1
#define IEEE80211_RADIOTAP_MCS_BANDWIDTH_20L	2
#define IEEE80211_RADIOTAP_MCS_BANDWIDTH_20U	3
#define IEEE80211_RADIOTAP_MCS_SHORT_GI		0x04 /* short guard interval */
#define IEEE80211_RADIOTAP_MCS_HT_GREENFIELD	0x08
#define IEEE80211_RADIOTAP_MCS_FEC_LDPC		0x10
#define IEEE80211_RADIOTAP_MCS_STBC_MASK	0x60
#define		IEEE80211_RADIOTAP_MCS_STBC_1	1
#define		IEEE80211_RADIOTAP_MCS_STBC_2	2
#define		IEEE80211_RADIOTAP_MCS_STBC_3	3
#define IEEE80211_RADIOTAP_MCS_STBC_SHIFT	5

#endif /* _NET_IF_IEEE80211RADIOTAP_H_ */
