/*
 * Copyright (c) 2006 Paolo Abeni (Italy)
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 * 1. Redistributions of source code must retain the above copyright
 * notice, this list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright
 * notice, this list of conditions and the following disclaimer in the
 * documentation and/or other materials provided with the distribution.
 * 3. The name of the author may not be used to endorse or promote
 * products derived from this software without specific prior written
 * permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
 * A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
 * OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
 * LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
 * DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
 * THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 *
 * USB sniffing API implementation for Linux platform
 * By Paolo Abeni <paolo.abeni@email.it>
 * Modifications: Kris Katterjohn <katterjohn@gmail.com>
 *
 */

#ifdef HAVE_CONFIG_H
#include "config.h"
#endif

#include "pcap-int.h"
#include "pcap-usb-linux.h"
#include "pcap/usb.h"

#ifdef NEED_STRERROR_H
#include "strerror.h"
#endif

#include <ctype.h>
#include <errno.h>
#include <stdlib.h>
#include <unistd.h>
#include <fcntl.h>
#include <string.h>
#include <dirent.h>
#include <byteswap.h>
#include <netinet/in.h>
#include <sys/ioctl.h>
#include <sys/mman.h>
#ifdef HAVE_LINUX_USBDEVICE_FS_H
/*
 * We might need <linux/compiler.h> to define __user for
 * <linux/usbdevice_fs.h>.
 */
#ifdef HAVE_LINUX_COMPILER_H
#include <linux/compiler.h>
#endif /* HAVE_LINUX_COMPILER_H */
#include <linux/usbdevice_fs.h>
#endif /* HAVE_LINUX_USBDEVICE_FS_H */

#define USB_IFACE "usbmon"
#define USB_TEXT_DIR_OLD "/sys/kernel/debug/usbmon"
#define USB_TEXT_DIR "/sys/kernel/debug/usb/usbmon"
#define SYS_USB_BUS_DIR "/sys/bus/usb/devices"
#define PROC_USB_BUS_DIR "/proc/bus/usb"
#define USB_LINE_LEN 4096

#if __BYTE_ORDER == __LITTLE_ENDIAN
#define htols(s) s
#define htoll(l) l
#define htol64(ll) ll
#else
#define htols(s) bswap_16(s)
#define htoll(l) bswap_32(l)
#define htol64(ll) bswap_64(ll)
#endif

struct mon_bin_stats {
	u_int32_t queued;
	u_int32_t dropped;
};

struct mon_bin_get {
	pcap_usb_header *hdr;
	void *data;
	size_t data_len;   /* Length of data (can be zero) */
};

struct mon_bin_mfetch {
	int32_t *offvec;   /* Vector of events fetched */
	int32_t nfetch;    /* Number of events to fetch (out: fetched) */
	int32_t nflush;    /* Number of events to flush */
};

#define MON_IOC_MAGIC 0x92

#define MON_IOCQ_URB_LEN _IO(MON_IOC_MAGIC, 1)
#define MON_IOCX_URB  _IOWR(MON_IOC_MAGIC, 2, struct mon_bin_hdr)
#define MON_IOCG_STATS _IOR(MON_IOC_MAGIC, 3, struct mon_bin_stats)
#define MON_IOCT_RING_SIZE _IO(MON_IOC_MAGIC, 4)
#define MON_IOCQ_RING_SIZE _IO(MON_IOC_MAGIC, 5)
#define MON_IOCX_GET   _IOW(MON_IOC_MAGIC, 6, struct mon_bin_get)
#define MON_IOCX_MFETCH _IOWR(MON_IOC_MAGIC, 7, struct mon_bin_mfetch)
#define MON_IOCH_MFLUSH _IO(MON_IOC_MAGIC, 8)

#define MON_BIN_SETUP 	0x1 /* setup hdr is present*/
#define MON_BIN_SETUP_ZERO 	0x2 /* setup buffer is not available */
#define MON_BIN_DATA_ZERO 	0x4 /* data buffer is not available */
#define MON_BIN_ERROR 	0x8

/*
 * Private data for capturing on Linux USB.
 */
struct pcap_usb_linux {
	u_char *mmapbuf;	/* memory-mapped region pointer */
	size_t mmapbuflen;	/* size of region */
	int bus_index;
	u_int packets_read;
};

/* forward declaration */
static int usb_activate(pcap_t *);
static int usb_stats_linux(pcap_t *, struct pcap_stat *);
static int usb_stats_linux_bin(pcap_t *, struct pcap_stat *);
static int usb_read_linux(pcap_t *, int , pcap_handler , u_char *);
static int usb_read_linux_bin(pcap_t *, int , pcap_handler , u_char *);
static int usb_read_linux_mmap(pcap_t *, int , pcap_handler , u_char *);
static int usb_inject_linux(pcap_t *, const void *, size_t);
static int usb_setdirection_linux(pcap_t *, pcap_direction_t);
static void usb_cleanup_linux_mmap(pcap_t *);

/* facility to add an USB device to the device list*/
static int
usb_dev_add(pcap_if_t** alldevsp, int n, char *err_str)
{
	char dev_name[10];
	char dev_descr[30];
	snprintf(dev_name, 10, USB_IFACE"%d", n);
	snprintf(dev_descr, 30, "USB bus number %d", n);

	if (pcap_add_if(alldevsp, dev_name, 0,
	    dev_descr, err_str) < 0)
		return -1;
	return 0;
}

int
usb_findalldevs(pcap_if_t **alldevsp, char *err_str)
{
	struct dirent* data;
	int ret = 0;
	DIR* dir;
	int n;
	char* name;
	size_t len;

	/* try scanning sysfs usb bus directory */
	dir = opendir(SYS_USB_BUS_DIR);
	if (dir != NULL) {
		while ((ret == 0) && ((data = readdir(dir)) != 0)) {
			name = data->d_name;

			if (strncmp(name, "usb", 3) != 0)
				continue;

			if (sscanf(&name[3], "%d", &n) == 0)
				continue;

			ret = usb_dev_add(alldevsp, n, err_str);
		}

		closedir(dir);
		return ret;
	}

	/* that didn't work; try scanning procfs usb bus directory */
	dir = opendir(PROC_USB_BUS_DIR);
	if (dir != NULL) {
		while ((ret == 0) && ((data = readdir(dir)) != 0)) {
			name = data->d_name;
			len = strlen(name);

			/* if this file name does not end with a number it's not of our interest */
			if ((len < 1) || !isdigit(name[--len]))
				continue;
			while (isdigit(name[--len]));
			if (sscanf(&name[len+1], "%d", &n) != 1)
				continue;

			ret = usb_dev_add(alldevsp, n, err_str);
		}

		closedir(dir);
		return ret;
	}

	/* neither of them worked */
	return 0;
}

static
int usb_mmap(pcap_t* handle)
{
	struct pcap_usb_linux *handlep = handle->priv;
	int len = ioctl(handle->fd, MON_IOCQ_RING_SIZE);
	if (len < 0)
		return 0;

	handlep->mmapbuflen = len;
	handlep->mmapbuf = mmap(0, handlep->mmapbuflen, PROT_READ,
	    MAP_SHARED, handle->fd, 0);
	return handlep->mmapbuf != MAP_FAILED;
}

#ifdef HAVE_LINUX_USBDEVICE_FS_H

#define CTRL_TIMEOUT    (5*1000)        /* milliseconds */

#define USB_DIR_IN		0x80
#define USB_TYPE_STANDARD	0x00
#define USB_RECIP_DEVICE	0x00

#define USB_REQ_GET_DESCRIPTOR	6

#define USB_DT_DEVICE		1

/* probe the descriptors of the devices attached to the bus */
/* the descriptors will end up in the captured packet stream */
/* and be decoded by external apps like wireshark */
/* without these identifying probes packet data can't be fully decoded */
static void
probe_devices(int bus)
{
	struct usbdevfs_ctrltransfer ctrl;
	struct dirent* data;
	int ret = 0;
	char buf[40];
	DIR* dir;

	/* scan usb bus directories for device nodes */
	snprintf(buf, sizeof(buf), "/dev/bus/usb/%03d", bus);
	dir = opendir(buf);
	if (!dir)
		return;

	while ((ret >= 0) && ((data = readdir(dir)) != 0)) {
		int fd;
		char* name = data->d_name;

		if (name[0] == '.')
			continue;

		snprintf(buf, sizeof(buf), "/dev/bus/usb/%03d/%s", bus, data->d_name);

		fd = open(buf, O_RDWR);
		if (fd == -1)
			continue;

		/*
		 * Sigh.  Different kernels have different member names
		 * for this structure.
		 */
#ifdef HAVE_USBDEVFS_CTRLTRANSFER_BREQUESTTYPE
		ctrl.bRequestType = USB_DIR_IN | USB_TYPE_STANDARD | USB_RECIP_DEVICE;
		ctrl.bRequest = USB_REQ_GET_DESCRIPTOR;
		ctrl.wValue = USB_DT_DEVICE << 8;
		ctrl.wIndex = 0;
 		ctrl.wLength = sizeof(buf);
#else
		ctrl.requesttype = USB_DIR_IN | USB_TYPE_STANDARD | USB_RECIP_DEVICE;
		ctrl.request = USB_REQ_GET_DESCRIPTOR;
		ctrl.value = USB_DT_DEVICE << 8;
		ctrl.index = 0;
 		ctrl.length = sizeof(buf);
#endif
		ctrl.data = buf;
		ctrl.timeout = CTRL_TIMEOUT;

		ret = ioctl(fd, USBDEVFS_CONTROL, &ctrl);

		close(fd);
	}
	closedir(dir);
}
#endif /* HAVE_LINUX_USBDEVICE_FS_H */

pcap_t *
usb_create(const char *device, char *ebuf, int *is_ours)
{
	const char *cp;
	char *cpend;
	long devnum;
	pcap_t *p;

	/* Does this look like a USB monitoring device? */
	cp = strrchr(device, '/');
	if (cp == NULL)
		cp = device;
	/* Does it begin with USB_IFACE? */
	if (strncmp(cp, USB_IFACE, sizeof USB_IFACE - 1) != 0) {
		/* Nope, doesn't begin with USB_IFACE */
		*is_ours = 0;
		return NULL;
	}
	/* Yes - is USB_IFACE followed by a number? */
	cp += sizeof USB_IFACE - 1;
	devnum = strtol(cp, &cpend, 10);
	if (cpend == cp || *cpend != '\0') {
		/* Not followed by a number. */
		*is_ours = 0;
		return NULL;
	}
	if (devnum < 0) {
		/* Followed by a non-valid number. */
		*is_ours = 0;
		return NULL;
	}

	/* OK, it's probably ours. */
	*is_ours = 1;

	p = pcap_create_common(device, ebuf, sizeof (struct pcap_usb_linux));
	if (p == NULL)
		return (NULL);

	p->activate_op = usb_activate;
	return (p);
}

static int
usb_activate(pcap_t* handle)
{
	struct pcap_usb_linux *handlep = handle->priv;
	char 		full_path[USB_LINE_LEN];

	/* Initialize some components of the pcap structure. */
	handle->bufsize = handle->snapshot;
	handle->offset = 0;
	handle->linktype = DLT_USB_LINUX;

	handle->inject_op = usb_inject_linux;
	handle->setfilter_op = install_bpf_program; /* no kernel filtering */
	handle->setdirection_op = usb_setdirection_linux;
	handle->set_datalink_op = NULL;	/* can't change data link type */
	handle->getnonblock_op = pcap_getnonblock_fd;
	handle->setnonblock_op = pcap_setnonblock_fd;

	/*get usb bus index from device name */
	if (sscanf(handle->opt.source, USB_IFACE"%d", &handlep->bus_index) != 1)
	{
		snprintf(handle->errbuf, PCAP_ERRBUF_SIZE,
			"Can't get USB bus index from %s", handle->opt.source);
		return PCAP_ERROR;
	}

	/*now select the read method: try to open binary interface */
	snprintf(full_path, USB_LINE_LEN, LINUX_USB_MON_DEV"%d", handlep->bus_index);
	handle->fd = open(full_path, O_RDONLY, 0);
	if (handle->fd >= 0)
	{
		if (handle->opt.rfmon) {
			/*
			 * Monitor mode doesn't apply to USB devices.
			 */
			close(handle->fd);
			return PCAP_ERROR_RFMON_NOTSUP;
		}

		/* binary api is available, try to use fast mmap access */
		if (usb_mmap(handle)) {
			handle->linktype = DLT_USB_LINUX_MMAPPED;
			handle->stats_op = usb_stats_linux_bin;
			handle->read_op = usb_read_linux_mmap;
			handle->cleanup_op = usb_cleanup_linux_mmap;
#ifdef HAVE_LINUX_USBDEVICE_FS_H
			probe_devices(handlep->bus_index);
#endif

			/*
			 * "handle->fd" is a real file, so "select()" and
			 * "poll()" work on it.
			 */
			handle->selectable_fd = handle->fd;
			return 0;
		}

		/* can't mmap, use plain binary interface access */
		handle->stats_op = usb_stats_linux_bin;
		handle->read_op = usb_read_linux_bin;
#ifdef HAVE_LINUX_USBDEVICE_FS_H
		probe_devices(handlep->bus_index);
#endif
	}
	else {
		/*Binary interface not available, try open text interface */
		snprintf(full_path, USB_LINE_LEN, USB_TEXT_DIR"/%dt", handlep->bus_index);
		handle->fd = open(full_path, O_RDONLY, 0);
		if (handle->fd < 0)
		{
			if (errno == ENOENT)
			{
				/*
				 * Not found at the new location; try
				 * the old location.
				 */
				snprintf(full_path, USB_LINE_LEN, USB_TEXT_DIR_OLD"/%dt", handlep->bus_index);
				handle->fd = open(full_path, O_RDONLY, 0);
			}
			if (handle->fd < 0) {
				/* no more fallback, give it up*/
				snprintf(handle->errbuf, PCAP_ERRBUF_SIZE,
					"Can't open USB bus file %s: %s", full_path, strerror(errno));
				return PCAP_ERROR;
			}
		}

		if (handle->opt.rfmon) {
			/*
			 * Monitor mode doesn't apply to USB devices.
			 */
			close(handle->fd);
			return PCAP_ERROR_RFMON_NOTSUP;
		}

		handle->stats_op = usb_stats_linux;
		handle->read_op = usb_read_linux;
	}

	/*
	 * "handle->fd" is a real file, so "select()" and "poll()"
	 * work on it.
	 */
	handle->selectable_fd = handle->fd;

	/* for plain binary access and text access we need to allocate the read
	 * buffer */
	handle->buffer = malloc(handle->bufsize);
	if (!handle->buffer) {
		snprintf(handle->errbuf, PCAP_ERRBUF_SIZE,
			 "malloc: %s", pcap_strerror(errno));
		close(handle->fd);
		return PCAP_ERROR;
	}
	return 0;
}

static inline int
ascii_to_int(char c)
{
	return c < 'A' ? c- '0': ((c<'a') ? c - 'A' + 10: c-'a'+10);
}

/*
 * see <linux-kernel-source>/Documentation/usb/usbmon.txt and
 * <linux-kernel-source>/drivers/usb/mon/mon_text.c for urb string
 * format description
 */
static int
usb_read_linux(pcap_t *handle, int max_packets, pcap_handler callback, u_char *user)
{
	/* see:
	* /usr/src/linux/Documentation/usb/usbmon.txt
	* for message format
	*/
	struct pcap_usb_linux *handlep = handle->priv;
	unsigned timestamp;
	int tag, cnt, ep_num, dev_addr, dummy, ret, urb_len, data_len;
	char etype, pipeid1, pipeid2, status[16], urb_tag, line[USB_LINE_LEN];
	char *string = line;
	u_char * rawdata = handle->buffer;
	struct pcap_pkthdr pkth;
	pcap_usb_header* uhdr = (pcap_usb_header*)handle->buffer;
	u_char urb_transfer=0;
	int incoming=0;

	/* ignore interrupt system call errors */
	do {
		ret = read(handle->fd, line, USB_LINE_LEN - 1);
		if (handle->break_loop)
		{
			handle->break_loop = 0;
			return -2;
		}
	} while ((ret == -1) && (errno == EINTR));
	if (ret < 0)
	{
		if (errno == EAGAIN)
			return 0;	/* no data there */

		snprintf(handle->errbuf, PCAP_ERRBUF_SIZE,
		    "Can't read from fd %d: %s", handle->fd, strerror(errno));
		return -1;
	}

	/* read urb header; %n argument may increment return value, but it's
	* not mandatory, so does not count on it*/
	string[ret] = 0;
	ret = sscanf(string, "%x %d %c %c%c:%d:%d %s%n", &tag, &timestamp, &etype,
		&pipeid1, &pipeid2, &dev_addr, &ep_num, status,
		&cnt);
	if (ret < 8)
	{
		snprintf(handle->errbuf, PCAP_ERRBUF_SIZE,
		    "Can't parse USB bus message '%s', too few tokens (expected 8 got %d)",
		    string, ret);
		return -1;
	}
	uhdr->id = tag;
	uhdr->device_address = dev_addr;
	uhdr->bus_id = handlep->bus_index;
	uhdr->status = 0;
	string += cnt;

	/* don't use usbmon provided timestamp, since it have low precision*/
	if (gettimeofday(&pkth.ts, NULL) < 0)
	{
		snprintf(handle->errbuf, PCAP_ERRBUF_SIZE,
			"Can't get timestamp for message '%s' %d:%s",
			string, errno, strerror(errno));
		return -1;
	}
	uhdr->ts_sec = pkth.ts.tv_sec;
	uhdr->ts_usec = pkth.ts.tv_usec;

	/* parse endpoint information */
	if (pipeid1 == 'C')
		urb_transfer = URB_CONTROL;
	else if (pipeid1 == 'Z')
		urb_transfer = URB_ISOCHRONOUS;
	else if (pipeid1 == 'I')
		urb_transfer = URB_INTERRUPT;
	else if (pipeid1 == 'B')
		urb_transfer = URB_BULK;
	if (pipeid2 == 'i') {
		ep_num |= URB_TRANSFER_IN;
		incoming = 1;
	}
	if (etype == 'C')
		incoming = !incoming;

	/* direction check*/
	if (incoming)
	{
		if (handle->direction == PCAP_D_OUT)
			return 0;
	}
	else
		if (handle->direction == PCAP_D_IN)
			return 0;
	uhdr->event_type = etype;
	uhdr->transfer_type = urb_transfer;
	uhdr->endpoint_number = ep_num;
	pkth.caplen = sizeof(pcap_usb_header);
	rawdata += sizeof(pcap_usb_header);

	/* check if this is a setup packet */
	ret = sscanf(status, "%d", &dummy);
	if (ret != 1)
	{
		/* this a setup packet, setup data can be filled with underscore if
		* usbmon has not been able to read them, so we must parse this fields as
		* strings */
		pcap_usb_setup* shdr;
		char str1[3], str2[3], str3[5], str4[5], str5[5];
		ret = sscanf(string, "%s %s %s %s %s%n", str1, str2, str3, str4,
		str5, &cnt);
		if (ret < 5)
		{
			snprintf(handle->errbuf, PCAP_ERRBUF_SIZE,
				"Can't parse USB bus message '%s', too few tokens (expected 5 got %d)",
				string, ret);
			return -1;
		}
		string += cnt;

		/* try to convert to corresponding integer */
		shdr = &uhdr->setup;
		shdr->bmRequestType = strtoul(str1, 0, 16);
		shdr->bRequest = strtoul(str2, 0, 16);
		shdr->wValue = htols(strtoul(str3, 0, 16));
		shdr->wIndex = htols(strtoul(str4, 0, 16));
		shdr->wLength = htols(strtoul(str5, 0, 16));

		uhdr->setup_flag = 0;
	}
	else
		uhdr->setup_flag = 1;

	/* read urb data */
	ret = sscanf(string, " %d%n", &urb_len, &cnt);
	if (ret < 1)
	{
		snprintf(handle->errbuf, PCAP_ERRBUF_SIZE,
		  "Can't parse urb length from '%s'", string);
		return -1;
	}
	string += cnt;

	/* urb tag is not present if urb length is 0, so we can stop here
	 * text parsing */
	pkth.len = urb_len+pkth.caplen;
	uhdr->urb_len = urb_len;
	uhdr->data_flag = 1;
	data_len = 0;
	if (uhdr->urb_len == 0)
		goto got;

	/* check for data presence; data is present if and only if urb tag is '=' */
	if (sscanf(string, " %c", &urb_tag) != 1)
	{
		snprintf(handle->errbuf, PCAP_ERRBUF_SIZE,
			"Can't parse urb tag from '%s'", string);
		return -1;
	}

	if (urb_tag != '=')
		goto got;

	/* skip urb tag and following space */
	string += 3;

	/* if we reach this point we got some urb data*/
	uhdr->data_flag = 0;

	/* read all urb data; if urb length is greater then the usbmon internal
	 * buffer length used by the kernel to spool the URB, we get only
	 * a partial information.
	 * At least until linux 2.6.17 there is no way to set usbmon intenal buffer
	 * length and default value is 130. */
	while ((string[0] != 0) && (string[1] != 0) && (pkth.caplen < handle->snapshot))
	{
		rawdata[0] = ascii_to_int(string[0]) * 16 + ascii_to_int(string[1]);
		rawdata++;
		string+=2;
		if (string[0] == ' ')
			string++;
		pkth.caplen++;
		data_len++;
	}

got:
	uhdr->data_len = data_len;
	if (pkth.caplen > handle->snapshot)
		pkth.caplen = handle->snapshot;

	if (handle->fcode.bf_insns == NULL ||
	    bpf_filter(handle->fcode.bf_insns, handle->buffer,
	      pkth.len, pkth.caplen)) {
		handlep->packets_read++;
		callback(user, &pkth, handle->buffer);
		return 1;
	}
	return 0;	/* didn't pass filter */
}

static int
usb_inject_linux(pcap_t *handle, const void *buf, size_t size)
{
	snprintf(handle->errbuf, PCAP_ERRBUF_SIZE, "inject not supported on "
		"USB devices");
	return (-1);
}

static int
usb_stats_linux(pcap_t *handle, struct pcap_stat *stats)
{
	struct pcap_usb_linux *handlep = handle->priv;
	int dummy, ret, consumed, cnt;
	char string[USB_LINE_LEN];
	char token[USB_LINE_LEN];
	char * ptr = string;
	int fd;

	snprintf(string, USB_LINE_LEN, USB_TEXT_DIR"/%ds", handlep->bus_index);
	fd = open(string, O_RDONLY, 0);
	if (fd < 0)
	{
		if (errno == ENOENT)
		{
			/*
			 * Not found at the new location; try the old
			 * location.
			 */
			snprintf(string, USB_LINE_LEN, USB_TEXT_DIR_OLD"/%ds", handlep->bus_index);
			fd = open(string, O_RDONLY, 0);
		}
		if (fd < 0) {
			snprintf(handle->errbuf, PCAP_ERRBUF_SIZE,
				"Can't open USB stats file %s: %s",
				string, strerror(errno));
			return -1;
		}
	}

	/* read stats line */
	do {
		ret = read(fd, string, USB_LINE_LEN-1);
	} while ((ret == -1) && (errno == EINTR));
	close(fd);

	if (ret < 0)
	{
		snprintf(handle->errbuf, PCAP_ERRBUF_SIZE,
			"Can't read stats from fd %d ", fd);
		return -1;
	}
	string[ret] = 0;

	/* extract info on dropped urbs */
	for (consumed=0; consumed < ret; ) {
		/* from the sscanf man page:
 		 * The C standard says: "Execution of a %n directive does
 		 * not increment the assignment count returned at the completion
		 * of  execution" but the Corrigendum seems to contradict this.
		 * Do not make any assumptions on the effect of %n conversions
		 * on the return value and explicitly check for cnt assignmet*/
		int ntok;

		cnt = -1;
		ntok = sscanf(ptr, "%s%n", token, &cnt);
		if ((ntok < 1) || (cnt < 0))
			break;
		consumed += cnt;
		ptr += cnt;
		if (strcmp(token, "nreaders") == 0)
			ret = sscanf(ptr, "%d", &stats->ps_drop);
		else
			ret = sscanf(ptr, "%d", &dummy);
		if (ntok != 1)
			break;
		consumed += cnt;
		ptr += cnt;
	}

	stats->ps_recv = handlep->packets_read;
	stats->ps_ifdrop = 0;
	return 0;
}

static int
usb_setdirection_linux(pcap_t *p, pcap_direction_t d)
{
	p->direction = d;
	return 0;
}


static int
usb_stats_linux_bin(pcap_t *handle, struct pcap_stat *stats)
{
	struct pcap_usb_linux *handlep = handle->priv;
	int ret;
	struct mon_bin_stats st;
	ret = ioctl(handle->fd, MON_IOCG_STATS, &st);
	if (ret < 0)
	{
		snprintf(handle->errbuf, PCAP_ERRBUF_SIZE,
			"Can't read stats from fd %d:%s ", handle->fd, strerror(errno));
		return -1;
	}

	stats->ps_recv = handlep->packets_read + st.queued;
	stats->ps_drop = st.dropped;
	stats->ps_ifdrop = 0;
	return 0;
}

/*
 * see <linux-kernel-source>/Documentation/usb/usbmon.txt and
 * <linux-kernel-source>/drivers/usb/mon/mon_bin.c binary ABI
 */
static int
usb_read_linux_bin(pcap_t *handle, int max_packets, pcap_handler callback, u_char *user)
{
	struct pcap_usb_linux *handlep = handle->priv;
	struct mon_bin_get info;
	int ret;
	struct pcap_pkthdr pkth;
	int clen = handle->snapshot - sizeof(pcap_usb_header);

	/* the usb header is going to be part of 'packet' data*/
	info.hdr = (pcap_usb_header*) handle->buffer;
	info.data = handle->buffer + sizeof(pcap_usb_header);
	info.data_len = clen;

	/* ignore interrupt system call errors */
	do {
		ret = ioctl(handle->fd, MON_IOCX_GET, &info);
		if (handle->break_loop)
		{
			handle->break_loop = 0;
			return -2;
		}
	} while ((ret == -1) && (errno == EINTR));
	if (ret < 0)
	{
		if (errno == EAGAIN)
			return 0;	/* no data there */

		snprintf(handle->errbuf, PCAP_ERRBUF_SIZE,
		    "Can't read from fd %d: %s", handle->fd, strerror(errno));
		return -1;
	}

	/* we can get less that than really captured from kernel, depending on
	 * snaplen, so adjust header accordingly */
	if (info.hdr->data_len < clen)
		clen = info.hdr->data_len;
	info.hdr->data_len = clen;
	pkth.caplen = clen + sizeof(pcap_usb_header);
	pkth.len = info.hdr->data_len + sizeof(pcap_usb_header);
	pkth.ts.tv_sec = info.hdr->ts_sec;
	pkth.ts.tv_usec = info.hdr->ts_usec;

	if (handle->fcode.bf_insns == NULL ||
	    bpf_filter(handle->fcode.bf_insns, handle->buffer,
	      pkth.len, pkth.caplen)) {
		handlep->packets_read++;
		callback(user, &pkth, handle->buffer);
		return 1;
	}

	return 0;	/* didn't pass filter */
}

/*
 * see <linux-kernel-source>/Documentation/usb/usbmon.txt and
 * <linux-kernel-source>/drivers/usb/mon/mon_bin.c binary ABI
 */
#define VEC_SIZE 32
static int
usb_read_linux_mmap(pcap_t *handle, int max_packets, pcap_handler callback, u_char *user)
{
	struct pcap_usb_linux *handlep = handle->priv;
	struct mon_bin_mfetch fetch;
	int32_t vec[VEC_SIZE];
	struct pcap_pkthdr pkth;
	pcap_usb_header* hdr;
	int nflush = 0;
	int packets = 0;
	int clen, max_clen;

	max_clen = handle->snapshot - sizeof(pcap_usb_header);

	for (;;) {
		int i, ret;
		int limit = max_packets - packets;
		if (limit <= 0)
			limit = VEC_SIZE;
		if (limit > VEC_SIZE)
			limit = VEC_SIZE;

		/* try to fetch as many events as possible*/
		fetch.offvec = vec;
		fetch.nfetch = limit;
		fetch.nflush = nflush;
		/* ignore interrupt system call errors */
		do {
			ret = ioctl(handle->fd, MON_IOCX_MFETCH, &fetch);
			if (handle->break_loop)
			{
				handle->break_loop = 0;
				return -2;
			}
		} while ((ret == -1) && (errno == EINTR));
		if (ret < 0)
		{
			if (errno == EAGAIN)
				return 0;	/* no data there */

			snprintf(handle->errbuf, PCAP_ERRBUF_SIZE,
			    "Can't mfetch fd %d: %s", handle->fd, strerror(errno));
			return -1;
		}

		/* keep track of processed events, we will flush them later */
		nflush = fetch.nfetch;
		for (i=0; i<fetch.nfetch; ++i) {
			/* discard filler */
			hdr = (pcap_usb_header*) &handlep->mmapbuf[vec[i]];
			if (hdr->event_type == '@')
				continue;

			/* we can get less that than really captured from kernel, depending on
	 		* snaplen, so adjust header accordingly */
			clen = max_clen;
			if (hdr->data_len < clen)
				clen = hdr->data_len;

			/* get packet info from header*/
			pkth.caplen = clen + sizeof(pcap_usb_header_mmapped);
			pkth.len = hdr->data_len + sizeof(pcap_usb_header_mmapped);
			pkth.ts.tv_sec = hdr->ts_sec;
			pkth.ts.tv_usec = hdr->ts_usec;

			if (handle->fcode.bf_insns == NULL ||
			    bpf_filter(handle->fcode.bf_insns, (u_char*) hdr,
			      pkth.len, pkth.caplen)) {
				handlep->packets_read++;
				callback(user, &pkth, (u_char*) hdr);
				packets++;
			}
		}

		/* with max_packets specifying "unlimited" we stop afer the first chunk*/
		if (PACKET_COUNT_IS_UNLIMITED(max_packets) || (packets == max_packets))
			break;
	}

	/* flush pending events*/
	if (ioctl(handle->fd, MON_IOCH_MFLUSH, nflush) == -1) {
		snprintf(handle->errbuf, PCAP_ERRBUF_SIZE,
		    "Can't mflush fd %d: %s", handle->fd, strerror(errno));
		return -1;
	}
	return packets;
}

static void
usb_cleanup_linux_mmap(pcap_t* handle)
{
	struct pcap_usb_linux *handlep = handle->priv;

	/* if we have a memory-mapped buffer, unmap it */
	if (handlep->mmapbuf != NULL) {
		munmap(handlep->mmapbuf, handlep->mmapbuflen);
		handlep->mmapbuf = NULL;
	}
	pcap_cleanup_live_common(handle);
}
