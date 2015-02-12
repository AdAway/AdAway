/*
 * Oracle
 */
#ifdef HAVE_CONFIG_H
#include "config.h"
#endif

#include <tcpdump-stdinc.h>

#include <stdio.h>
#include <pcap.h>

#include "netdissect.h"
#include "interface.h"
#include "extract.h"
#include "ppi.h"

#ifdef DLT_PPI

static inline void
ppi_header_print(struct netdissect_options *ndo, const u_char *bp, u_int length)
{
	const ppi_header_t *hdr;
	u_int32_t dlt;
	u_int16_t len;

	hdr = (const ppi_header_t *)bp;

	len = EXTRACT_16BITS(&hdr->ppi_len);
	dlt = EXTRACT_32BITS(&hdr->ppi_dlt);

	if (!ndo->ndo_qflag) {
		ND_PRINT((ndo,", V.%d DLT %s (%d) len %d", hdr->ppi_ver,
			  pcap_datalink_val_to_name(dlt), dlt,
                          len));
        } else {
		ND_PRINT((ndo,", %s", pcap_datalink_val_to_name(dlt)));
        }

	ND_PRINT((ndo, ", length %u: ", length));
}

static void
ppi_print(struct netdissect_options *ndo,
               const struct pcap_pkthdr *h, const u_char *p)
{
	if_ndo_printer ndo_printer;
        if_printer printer;
	ppi_header_t *hdr;
	u_int caplen = h->caplen;
	u_int length = h->len;
	u_int32_t dlt;

	if (caplen < sizeof(ppi_header_t)) {
		ND_PRINT((ndo, "[|ppi]"));
		return;
	}
	hdr = (ppi_header_t *)p;
	dlt = EXTRACT_32BITS(&hdr->ppi_dlt);

	if (ndo->ndo_eflag)
		ppi_header_print(ndo, p, length);

	length -= sizeof(ppi_header_t);
	caplen -= sizeof(ppi_header_t);
	p += sizeof(ppi_header_t);

	if ((printer = lookup_printer(dlt)) != NULL) {
		printer(h, p);
	} else if ((ndo_printer = lookup_ndo_printer(dlt)) != NULL) {
		ndo_printer(ndo, h, p);
	} else {
		if (!ndo->ndo_eflag)
			ppi_header_print(ndo, (u_char *)hdr,
					length + sizeof(ppi_header_t));

		if (!ndo->ndo_suppress_default_print)
			ndo->ndo_default_print(ndo, p, caplen);
	}
}

/*
 * This is the top level routine of the printer.  'p' points
 * to the ether header of the packet, 'h->ts' is the timestamp,
 * 'h->len' is the length of the packet off the wire, and 'h->caplen'
 * is the number of bytes actually captured.
 */
u_int
ppi_if_print(struct netdissect_options *ndo,
               const struct pcap_pkthdr *h, const u_char *p)
{
	ppi_print(ndo, h, p);

	return (sizeof(ppi_header_t));
}

/*
 * Local Variables:
 * c-style: whitesmith
 * c-basic-offset: 8
 * End:
 */

#endif /* DLT_PPI */
