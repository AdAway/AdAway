typedef struct ppi_header {
	uint8_t		ppi_ver;
	uint8_t		ppi_flags;
	uint16_t	ppi_len;
	uint32_t	ppi_dlt;
} ppi_header_t;

#define	PPI_HDRLEN	8

