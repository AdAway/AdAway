/* dnsmasq is Copyright (c) 2000-2009 Simon Kelley

   This program is free software; you can redistribute it and/or modify
   it under the terms of the GNU General Public License as published by
   the Free Software Foundation; version 2 dated June, 1991, or
   (at your option) version 3 dated 29 June, 2007.
 
   This program is distributed in the hope that it will be useful,
   but WITHOUT ANY WARRANTY; without even the implied warranty of
   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
   GNU General Public License for more details.
     
   You should have received a copy of the GNU General Public License
   along with this program.  If not, see <http://www.gnu.org/licenses/>.
*/

/* define this to get facilitynames */
#define SYSLOG_NAMES
#include "dnsmasq.h"
#include <setjmp.h>

static volatile int mem_recover = 0;
static jmp_buf mem_jmp;
static void one_file(char *file, int nest, int hard_opt);

/* Solaris headers don't have facility names. */
#ifdef HAVE_SOLARIS_NETWORK
static const struct {
  char *c_name;
  unsigned int c_val;
}  facilitynames[] = {
  { "kern",   LOG_KERN },
  { "user",   LOG_USER },
  { "mail",   LOG_MAIL },
  { "daemon", LOG_DAEMON },
  { "auth",   LOG_AUTH },
  { "syslog", LOG_SYSLOG },
  { "lpr",    LOG_LPR },
  { "news",   LOG_NEWS },
  { "uucp",   LOG_UUCP },
  { "audit",  LOG_AUDIT },
  { "cron",   LOG_CRON },
  { "local0", LOG_LOCAL0 },
  { "local1", LOG_LOCAL1 },
  { "local2", LOG_LOCAL2 },
  { "local3", LOG_LOCAL3 },
  { "local4", LOG_LOCAL4 },
  { "local5", LOG_LOCAL5 },
  { "local6", LOG_LOCAL6 },
  { "local7", LOG_LOCAL7 },
  { NULL, 0 }
};
#endif

#ifndef HAVE_GETOPT_LONG
struct myoption {
  const char *name;
  int has_arg;
  int *flag;
  int val;
};
#endif

#define OPTSTRING "951yZDNLERKzowefnbvhdkqr:m:p:c:l:s:i:t:u:g:a:x:S:C:A:T:H:Q:I:B:F:G:O:M:X:V:U:j:P:J:W:Y:2:4:6:7:8:0:3:"

/* options which don't have a one-char version */
#define LOPT_RELOAD    256
#define LOPT_NO_NAMES  257
#define LOPT_TFTP      258
#define LOPT_SECURE    259
#define LOPT_PREFIX    260
#define LOPT_PTR       261
#define LOPT_BRIDGE    262
#define LOPT_TFTP_MAX  263
#define LOPT_FORCE     264
#define LOPT_NOBLOCK   265
#define LOPT_LOG_OPTS  266
#define LOPT_MAX_LOGS  267
#define LOPT_CIRCUIT   268
#define LOPT_REMOTE    269
#define LOPT_SUBSCR    270
#define LOPT_INTNAME   271
#define LOPT_BANK      272
#define LOPT_DHCP_HOST 273
#define LOPT_APREF     274
#define LOPT_OVERRIDE  275
#define LOPT_TFTPPORTS 276
#define LOPT_REBIND    277
#define LOPT_NOLAST    278
#define LOPT_OPTS      279
#define LOPT_DHCP_OPTS 280
#define LOPT_MATCH     281
#define LOPT_BROADCAST 282
#define LOPT_NEGTTL    283
#define LOPT_ALTPORT   284
#define LOPT_SCRIPTUSR 285
#define LOPT_LOCAL     286
#define LOPT_NAPTR     287
#define LOPT_MINPORT   288
#define LOPT_DHCP_FQDN 289
#define LOPT_CNAME     290
#define LOPT_PXE_PROMT 291
#define LOPT_PXE_SERV  292
#define LOPT_TEST      293

#ifdef HAVE_GETOPT_LONG
static const struct option opts[] =  
#else
static const struct myoption opts[] = 
#endif
  { 
    { "version", 0, 0, 'v' },
    { "no-hosts", 0, 0, 'h' },
    { "no-poll", 0, 0, 'n' },
    { "help", 0, 0, 'w' },
    { "no-daemon", 0, 0, 'd' },
    { "log-queries", 0, 0, 'q' },
    { "user", 2, 0, 'u' },
    { "group", 2, 0, 'g' },
    { "resolv-file", 2, 0, 'r' },
    { "mx-host", 1, 0, 'm' },
    { "mx-target", 1, 0, 't' },
    { "cache-size", 2, 0, 'c' },
    { "port", 1, 0, 'p' },
    { "dhcp-leasefile", 2, 0, 'l' },
    { "dhcp-lease", 1, 0, 'l' },
    { "dhcp-host", 1, 0, 'G' },
    { "dhcp-range", 1, 0, 'F' },
    { "dhcp-option", 1, 0, 'O' },
    { "dhcp-boot", 1, 0, 'M' },
    { "domain", 1, 0, 's' },
    { "domain-suffix", 1, 0, 's' },
    { "interface", 1, 0, 'i' },
    { "listen-address", 1, 0, 'a' },
    { "bogus-priv", 0, 0, 'b' },
    { "bogus-nxdomain", 1, 0, 'B' },
    { "selfmx", 0, 0, 'e' },
    { "filterwin2k", 0, 0, 'f' },
    { "pid-file", 2, 0, 'x' },
    { "strict-order", 0, 0, 'o' },
    { "server", 1, 0, 'S' },
    { "local", 1, 0, LOPT_LOCAL },
    { "address", 1, 0, 'A' },
    { "conf-file", 2, 0, 'C' },
    { "no-resolv", 0, 0, 'R' },
    { "expand-hosts", 0, 0, 'E' },
    { "localmx", 0, 0, 'L' },
    { "local-ttl", 1, 0, 'T' },
    { "no-negcache", 0, 0, 'N' },
    { "addn-hosts", 1, 0, 'H' },
    { "query-port", 1, 0, 'Q' },
    { "except-interface", 1, 0, 'I' },
    { "no-dhcp-interface", 1, 0, '2' },
    { "domain-needed", 0, 0, 'D' },
    { "dhcp-lease-max", 1, 0, 'X' },
    { "bind-interfaces", 0, 0, 'z' },
    { "read-ethers", 0, 0, 'Z' },
    { "alias", 1, 0, 'V' },
    { "dhcp-vendorclass", 1, 0, 'U' },
    { "dhcp-userclass", 1, 0, 'j' },
    { "dhcp-ignore", 1, 0, 'J' },
    { "edns-packet-max", 1, 0, 'P' },
    { "keep-in-foreground", 0, 0, 'k' },
    { "dhcp-authoritative", 0, 0, 'K' },
    { "srv-host", 1, 0, 'W' },
    { "localise-queries", 0, 0, 'y' },
    { "txt-record", 1, 0, 'Y' },
    { "enable-dbus", 0, 0, '1' },
    { "bootp-dynamic", 2, 0, '3' },
    { "dhcp-mac", 1, 0, '4' },
    { "no-ping", 0, 0, '5' },
    { "dhcp-script", 1, 0, '6' },
    { "conf-dir", 1, 0, '7' },
    { "log-facility", 1, 0 ,'8' },
    { "leasefile-ro", 0, 0, '9' },
    { "dns-forward-max", 1, 0, '0' },
    { "clear-on-reload", 0, 0, LOPT_RELOAD },
    { "dhcp-ignore-names", 2, 0, LOPT_NO_NAMES },
    { "enable-tftp", 0, 0, LOPT_TFTP },
    { "tftp-secure", 0, 0, LOPT_SECURE },
    { "tftp-unique-root", 0, 0, LOPT_APREF },
    { "tftp-root", 1, 0, LOPT_PREFIX },
    { "tftp-max", 1, 0, LOPT_TFTP_MAX },
    { "ptr-record", 1, 0, LOPT_PTR },
    { "naptr-record", 1, 0, LOPT_NAPTR },
    { "bridge-interface", 1, 0 , LOPT_BRIDGE },
    { "dhcp-option-force", 1, 0, LOPT_FORCE },
    { "tftp-no-blocksize", 0, 0, LOPT_NOBLOCK },
    { "log-dhcp", 0, 0, LOPT_LOG_OPTS },
    { "log-async", 2, 0, LOPT_MAX_LOGS },
    { "dhcp-circuitid", 1, 0, LOPT_CIRCUIT },
    { "dhcp-remoteid", 1, 0, LOPT_REMOTE },
    { "dhcp-subscrid", 1, 0, LOPT_SUBSCR },
    { "interface-name", 1, 0, LOPT_INTNAME },
    { "dhcp-hostsfile", 1, 0, LOPT_DHCP_HOST },
    { "dhcp-optsfile", 1, 0, LOPT_DHCP_OPTS },
    { "dhcp-no-override", 0, 0, LOPT_OVERRIDE },
    { "tftp-port-range", 1, 0, LOPT_TFTPPORTS },
    { "stop-dns-rebind", 0, 0, LOPT_REBIND },
    { "all-servers", 0, 0, LOPT_NOLAST }, 
    { "dhcp-match", 1, 0, LOPT_MATCH }, 
    { "dhcp-broadcast", 1, 0, LOPT_BROADCAST },
    { "neg-ttl", 1, 0, LOPT_NEGTTL },
    { "dhcp-alternate-port", 2, 0, LOPT_ALTPORT },
    { "dhcp-scriptuser", 1, 0, LOPT_SCRIPTUSR },
    { "min-port", 1, 0, LOPT_MINPORT },
    { "dhcp-fqdn", 0, 0, LOPT_DHCP_FQDN },
    { "cname", 1, 0, LOPT_CNAME },
    { "pxe-prompt", 1, 0, LOPT_PXE_PROMT },
    { "pxe-service", 1, 0, LOPT_PXE_SERV },
    { "test", 0, 0, LOPT_TEST },
    { NULL, 0, 0, 0 }
  };

/* These must have more the one '1' bit */
#define ARG_DUP       3
#define ARG_ONE       5
#define ARG_USED_CL   7
#define ARG_USED_FILE 9

static struct {
  int opt;
  unsigned int rept;
  char * const flagdesc;
  char * const desc;
  char * const arg;
} usage[] = {
  { 'a', ARG_DUP, "ipaddr",  gettext_noop("Specify local address(es) to listen on."), NULL },
  { 'A', ARG_DUP, "/domain/ipaddr", gettext_noop("Return ipaddr for all hosts in specified domains."), NULL },
  { 'b', OPT_BOGUSPRIV, NULL, gettext_noop("Fake reverse lookups for RFC1918 private address ranges."), NULL },
  { 'B', ARG_DUP, "ipaddr", gettext_noop("Treat ipaddr as NXDOMAIN (defeats Verisign wildcard)."), NULL }, 
  { 'c', ARG_ONE, "cachesize", gettext_noop("Specify the size of the cache in entries (defaults to %s)."), "$" },
  { 'C', ARG_DUP, "path", gettext_noop("Specify configuration file (defaults to %s)."), CONFFILE },
  { 'd', OPT_DEBUG, NULL, gettext_noop("Do NOT fork into the background: run in debug mode."), NULL },
  { 'D', OPT_NODOTS_LOCAL, NULL, gettext_noop("Do NOT forward queries with no domain part."), NULL }, 
  { 'e', OPT_SELFMX, NULL, gettext_noop("Return self-pointing MX records for local hosts."), NULL },
  { 'E', OPT_EXPAND, NULL, gettext_noop("Expand simple names in /etc/hosts with domain-suffix."), NULL },
  { 'f', OPT_FILTER, NULL, gettext_noop("Don't forward spurious DNS requests from Windows hosts."), NULL },
  { 'F', ARG_DUP, "ipaddr,ipaddr,time", gettext_noop("Enable DHCP in the range given with lease duration."), NULL },
  { 'g', ARG_ONE, "groupname", gettext_noop("Change to this group after startup (defaults to %s)."), CHGRP },
  { 'G', ARG_DUP, "<hostspec>", gettext_noop("Set address or hostname for a specified machine."), NULL },
  { LOPT_DHCP_HOST, ARG_ONE, "<filename>", gettext_noop("Read DHCP host specs from file"), NULL },
  { LOPT_DHCP_OPTS, ARG_ONE, "<filename>", gettext_noop("Read DHCP option specs from file"), NULL },
  { 'h', OPT_NO_HOSTS, NULL, gettext_noop("Do NOT load %s file."), HOSTSFILE },
  { 'H', ARG_DUP, "path", gettext_noop("Specify a hosts file to be read in addition to %s."), HOSTSFILE },
  { 'i', ARG_DUP, "interface", gettext_noop("Specify interface(s) to listen on."), NULL },
  { 'I', ARG_DUP, "int", gettext_noop("Specify interface(s) NOT to listen on.") , NULL },
  { 'j', ARG_DUP, "<tag>,<class>", gettext_noop("Map DHCP user class to tag."), NULL },
  { LOPT_CIRCUIT, ARG_DUP, "<tag>,<circuit>", gettext_noop("Map RFC3046 circuit-id to tag."), NULL },
  { LOPT_REMOTE, ARG_DUP, "<tag>,<remote>", gettext_noop("Map RFC3046 remote-id to tag."), NULL },
  { LOPT_SUBSCR, ARG_DUP, "<tag>,<remote>", gettext_noop("Map RFC3993 subscriber-id to tag."), NULL },
  { 'J', ARG_DUP, "=<id>[,<id>]", gettext_noop("Don't do DHCP for hosts with tag set."), NULL },
  { LOPT_BROADCAST, ARG_DUP, "=<id>[,<id>]", gettext_noop("Force broadcast replies for hosts with tag set."), NULL }, 
  { 'k', OPT_NO_FORK, NULL, gettext_noop("Do NOT fork into the background, do NOT run in debug mode."), NULL },
  { 'K', OPT_AUTHORITATIVE, NULL, gettext_noop("Assume we are the only DHCP server on the local network."), NULL },
  { 'l', ARG_ONE, "path", gettext_noop("Specify where to store DHCP leases (defaults to %s)."), LEASEFILE },
  { 'L', OPT_LOCALMX, NULL, gettext_noop("Return MX records for local hosts."), NULL },
  { 'm', ARG_DUP, "host_name,target,pref", gettext_noop("Specify an MX record."), NULL },
  { 'M', ARG_DUP, "<bootp opts>", gettext_noop("Specify BOOTP options to DHCP server."), NULL },
  { 'n', OPT_NO_POLL, NULL, gettext_noop("Do NOT poll %s file, reload only on SIGHUP."), RESOLVFILE }, 
  { 'N', OPT_NO_NEG, NULL, gettext_noop("Do NOT cache failed search results."), NULL },
  { 'o', OPT_ORDER, NULL, gettext_noop("Use nameservers strictly in the order given in %s."), RESOLVFILE },
  { 'O', ARG_DUP, "<optspec>", gettext_noop("Specify options to be sent to DHCP clients."), NULL },
  { LOPT_FORCE, ARG_DUP, "<optspec>", gettext_noop("DHCP option sent even if the client does not request it."), NULL},
  { 'p', ARG_ONE, "number", gettext_noop("Specify port to listen for DNS requests on (defaults to 53)."), NULL },
  { 'P', ARG_ONE, "<size>", gettext_noop("Maximum supported UDP packet size for EDNS.0 (defaults to %s)."), "*" },
  { 'q', OPT_LOG, NULL, gettext_noop("Log DNS queries."), NULL },
  { 'Q', ARG_ONE, "number", gettext_noop("Force the originating port for upstream DNS queries."), NULL },
  { 'R', OPT_NO_RESOLV, NULL, gettext_noop("Do NOT read resolv.conf."), NULL },
  { 'r', ARG_DUP, "path", gettext_noop("Specify path to resolv.conf (defaults to %s)."), RESOLVFILE }, 
  { 'S', ARG_DUP, "/domain/ipaddr", gettext_noop("Specify address(es) of upstream servers with optional domains."), NULL },
  { LOPT_LOCAL, ARG_DUP, "/domain/", gettext_noop("Never forward queries to specified domains."), NULL },
  { 's', ARG_DUP, "<domain>[,<range>]", gettext_noop("Specify the domain to be assigned in DHCP leases."), NULL },
  { 't', ARG_ONE, "host_name", gettext_noop("Specify default target in an MX record."), NULL },
  { 'T', ARG_ONE, "time", gettext_noop("Specify time-to-live in seconds for replies from /etc/hosts."), NULL },
  { LOPT_NEGTTL, ARG_ONE, "time", gettext_noop("Specify time-to-live in seconds for negative caching."), NULL },
  { 'u', ARG_ONE, "username", gettext_noop("Change to this user after startup. (defaults to %s)."), CHUSER }, 
  { 'U', ARG_DUP, "<id>,<class>", gettext_noop("Map DHCP vendor class to tag."), NULL },
  { 'v', 0, NULL, gettext_noop("Display dnsmasq version and copyright information."), NULL },
  { 'V', ARG_DUP, "addr,addr,mask", gettext_noop("Translate IPv4 addresses from upstream servers."), NULL },
  { 'W', ARG_DUP, "name,target,...", gettext_noop("Specify a SRV record."), NULL },
  { 'w', 0, NULL, gettext_noop("Display this message. Use --help dhcp for known DHCP options."), NULL },
  { 'x', ARG_ONE, "path", gettext_noop("Specify path of PID file (defaults to %s)."), RUNFILE },
  { 'X', ARG_ONE, "number", gettext_noop("Specify maximum number of DHCP leases (defaults to %s)."), "&" },
  { 'y', OPT_LOCALISE, NULL, gettext_noop("Answer DNS queries based on the interface a query was sent to."), NULL },
  { 'Y', ARG_DUP, "name,txt....", gettext_noop("Specify TXT DNS record."), NULL },
  { LOPT_PTR, ARG_DUP, "name,target", gettext_noop("Specify PTR DNS record."), NULL },
  { LOPT_INTNAME, ARG_DUP, "name,interface", gettext_noop("Give DNS name to IPv4 address of interface."), NULL },
  { 'z', OPT_NOWILD, NULL, gettext_noop("Bind only to interfaces in use."), NULL },
  { 'Z', OPT_ETHERS, NULL, gettext_noop("Read DHCP static host information from %s."), ETHERSFILE },
  { '1', OPT_DBUS, NULL, gettext_noop("Enable the DBus interface for setting upstream servers, etc."), NULL },
  { '2', ARG_DUP, "interface", gettext_noop("Do not provide DHCP on this interface, only provide DNS."), NULL },
  { '3', ARG_DUP, "[=<id>[,<id>]]", gettext_noop("Enable dynamic address allocation for bootp."), NULL },
  { '4', ARG_DUP, "<id>,<mac address>", gettext_noop("Map MAC address (with wildcards) to option set."), NULL },
  { LOPT_BRIDGE, ARG_DUP, "iface,alias,..", gettext_noop("Treat DHCP requests on aliases as arriving from interface."), NULL },
  { '5', OPT_NO_PING, NULL, gettext_noop("Disable ICMP echo address checking in the DHCP server."), NULL },
  { '6', ARG_ONE, "path", gettext_noop("Script to run on DHCP lease creation and destruction."), NULL },
  { '7', ARG_DUP, "path", gettext_noop("Read configuration from all the files in this directory."), NULL },
  { '8', ARG_ONE, "<facilty>|<file>", gettext_noop("Log to this syslog facility or file. (defaults to DAEMON)"), NULL },
  { '9', OPT_LEASE_RO, NULL, gettext_noop("Do not use leasefile."), NULL },
  { '0', ARG_ONE, "<queries>", gettext_noop("Maximum number of concurrent DNS queries. (defaults to %s)"), "!" }, 
  { LOPT_RELOAD, OPT_RELOAD, NULL, gettext_noop("Clear DNS cache when reloading %s."), RESOLVFILE },
  { LOPT_NO_NAMES, ARG_DUP, "[=<id>[,<id>]]", gettext_noop("Ignore hostnames provided by DHCP clients."), NULL },
  { LOPT_OVERRIDE, OPT_NO_OVERRIDE, NULL, gettext_noop("Do NOT reuse filename and server fields for extra DHCP options."), NULL },
  { LOPT_TFTP, OPT_TFTP, NULL, gettext_noop("Enable integrated read-only TFTP server."), NULL },
  { LOPT_PREFIX, ARG_ONE, "<directory>", gettext_noop("Export files by TFTP only from the specified subtree."), NULL },
  { LOPT_APREF, OPT_TFTP_APREF, NULL, gettext_noop("Add client IP address to tftp-root."), NULL },
  { LOPT_SECURE, OPT_TFTP_SECURE, NULL, gettext_noop("Allow access only to files owned by the user running dnsmasq."), NULL },
  { LOPT_TFTP_MAX, ARG_ONE, "<connections>", gettext_noop("Maximum number of conncurrent TFTP transfers (defaults to %s)."), "#" },
  { LOPT_NOBLOCK, OPT_TFTP_NOBLOCK, NULL, gettext_noop("Disable the TFTP blocksize extension."), NULL },
  { LOPT_TFTPPORTS, ARG_ONE, "<start>,<end>", gettext_noop("Ephemeral port range for use by TFTP transfers."), NULL },
  { LOPT_LOG_OPTS, OPT_LOG_OPTS, NULL, gettext_noop("Extra logging for DHCP."), NULL },
  { LOPT_MAX_LOGS, ARG_ONE, "[=<log lines>]", gettext_noop("Enable async. logging; optionally set queue length."), NULL },
  { LOPT_REBIND, OPT_NO_REBIND, NULL, gettext_noop("Stop DNS rebinding. Filter private IP ranges when resolving."), NULL },
  { LOPT_NOLAST, OPT_ALL_SERVERS, NULL, gettext_noop("Always perform DNS queries to all servers."), NULL },
  { LOPT_MATCH, ARG_DUP, "<netid>,<optspec>", gettext_noop("Set tag if client includes matching option in request."), NULL },
  { LOPT_ALTPORT, ARG_ONE, "[=<ports>]", gettext_noop("Use alternative ports for DHCP."), NULL },
  { LOPT_SCRIPTUSR, ARG_ONE, "<username>", gettext_noop("Run lease-change script as this user."), NULL },
  { LOPT_NAPTR, ARG_DUP, "<name>,<naptr>", gettext_noop("Specify NAPTR DNS record."), NULL },
  { LOPT_MINPORT, ARG_ONE, "<port>", gettext_noop("Specify lowest port available for DNS query transmission."), NULL },
  { LOPT_DHCP_FQDN, OPT_DHCP_FQDN, NULL, gettext_noop("Use only fully qualified domain names for DHCP clients."), NULL },
  { LOPT_CNAME, ARG_DUP, "<alias>,<target>", gettext_noop("Specify alias name for LOCAL DNS name."), NULL },
  { LOPT_PXE_PROMT, ARG_DUP, "<prompt>,[<timeout>]", gettext_noop("Prompt to send to PXE clients."), NULL },
  { LOPT_PXE_SERV, ARG_DUP, "<service>", gettext_noop("Boot service for PXE menu."), NULL },
  { LOPT_TEST, 0, NULL, gettext_noop("Check configuration syntax."), NULL },
  { 0, 0, NULL, NULL, NULL }
}; 

#ifdef HAVE_DHCP
/* makes options which take a list of addresses */
#define OT_ADDR_LIST 0x80
/* DHCP-internal options, for logging. not valid in config file */
#define OT_INTERNAL 0x40
#define OT_NAME 0x20

static const struct {
  char *name;
  unsigned char val, size;
} opttab[] = {
  { "netmask", 1, OT_ADDR_LIST },
  { "time-offset", 2, 4 },
  { "router", 3, OT_ADDR_LIST  },
  { "dns-server", 6, OT_ADDR_LIST },
  { "log-server", 7, OT_ADDR_LIST },
  { "lpr-server", 9, OT_ADDR_LIST },
  { "hostname", 12, OT_INTERNAL | OT_NAME },
  { "boot-file-size", 13, 2 },
  { "domain-name", 15, OT_NAME },
  { "swap-server", 16, OT_ADDR_LIST },
  { "root-path", 17, 0 },
  { "extension-path", 18, 0 },
  { "ip-forward-enable", 19, 1 },
  { "non-local-source-routing", 20, 1 },
  { "policy-filter", 21, OT_ADDR_LIST },
  { "max-datagram-reassembly", 22, 2 },
  { "default-ttl", 23, 1 },
  { "mtu", 26, 2 },
  { "all-subnets-local", 27, 1 },
  { "broadcast", 28, OT_INTERNAL | OT_ADDR_LIST },
  { "router-discovery", 31, 1 },
  { "router-solicitation", 32, OT_ADDR_LIST },
  { "static-route", 33, OT_ADDR_LIST },
  { "trailer-encapsulation", 34, 1 },
  { "arp-timeout", 35, 4 },
  { "ethernet-encap", 36, 1 },
  { "tcp-ttl", 37, 1 },
  { "tcp-keepalive", 38, 4 },
  { "nis-domain", 40, 0 },
  { "nis-server", 41, OT_ADDR_LIST },
  { "ntp-server", 42, OT_ADDR_LIST },
  { "vendor-encap", 43, OT_INTERNAL },
  { "netbios-ns", 44, OT_ADDR_LIST },
  { "netbios-dd", 45, OT_ADDR_LIST },
  { "netbios-nodetype", 46, 1 },
  { "netbios-scope", 47, 0 },
  { "x-windows-fs", 48, OT_ADDR_LIST },
  { "x-windows-dm", 49, OT_ADDR_LIST },
  { "requested-address", 50, OT_INTERNAL | OT_ADDR_LIST },
  { "lease-time", 51, OT_INTERNAL },
  { "option-overload", 52, OT_INTERNAL },
  { "message-type", 53, OT_INTERNAL, },
  { "server-identifier", 54, OT_INTERNAL | OT_ADDR_LIST },
  { "parameter-request", 55, OT_INTERNAL },
  { "message", 56, OT_INTERNAL },
  { "max-message-size", 57, OT_INTERNAL },
  { "T1", 58, OT_INTERNAL },
  { "T2", 59, OT_INTERNAL },
  { "vendor-class", 60, 0 },
  { "client-id", 61,OT_INTERNAL },
  { "nis+-domain", 64, 0 },
  { "nis+-server", 65, OT_ADDR_LIST },
  { "tftp-server", 66, 0 },
  { "bootfile-name", 67, 0 },
  { "mobile-ip-home", 68, OT_ADDR_LIST }, 
  { "smtp-server", 69, OT_ADDR_LIST }, 
  { "pop3-server", 70, OT_ADDR_LIST }, 
  { "nntp-server", 71, OT_ADDR_LIST }, 
  { "irc-server", 74, OT_ADDR_LIST }, 
  { "user-class", 77, 0 },
  { "FQDN", 81, OT_INTERNAL },
  { "agent-id", 82, OT_INTERNAL },
  { "client-arch", 93, 2 },
  { "client-interface-id", 94, 0 },
  { "client-machine-id", 97, 0 },
  { "subnet-select", 118, OT_INTERNAL },
  { "domain-search", 119, 0 },
  { "sip-server", 120, 0 },
  { "classless-static-route", 121, 0 },
  { "server-ip-address", 255, OT_ADDR_LIST }, /* special, internal only, sets siaddr */
  { NULL, 0, 0 }
};

char *option_string(unsigned char opt, int *is_ip, int *is_name)
{
  int i;

  for (i = 0; opttab[i].name; i++)
    if (opttab[i].val == opt)
      {
	if (is_ip)
	  *is_ip = !!(opttab[i].size & OT_ADDR_LIST);
	if (is_name)
	  *is_name = !!(opttab[i].size & OT_NAME);
	return opttab[i].name;
      }

  return NULL;
}

#endif

/* We hide metacharaters in quoted strings by mapping them into the ASCII control
   character space. Note that the \0, \t \b \r \033 and \n characters are carefully placed in the
   following sequence so that they map to themselves: it is therefore possible to call
   unhide_metas repeatedly on string without breaking things.
   The transformation gets undone by opt_canonicalise, atoi_check and opt_string_alloc, and a 
   couple of other places. 
   Note that space is included here so that
   --dhcp-option=3, string
   has five characters, whilst
   --dhcp-option=3," string"
   has six.
*/

static const char meta[] = "\000123456 \b\t\n78\r90abcdefABCDE\033F:,.";

static char hide_meta(char c)
{
  unsigned int i;

  for (i = 0; i < (sizeof(meta) - 1); i++)
    if (c == meta[i])
      return (char)i;
  
  return c;
}

static char unhide_meta(char cr)
{ 
  unsigned int c = cr;
  
  if (c < (sizeof(meta) - 1))
    cr = meta[c];
  
  return cr;
}

static void unhide_metas(char *cp)
{
  if (cp)
    for(; *cp; cp++)
      *cp = unhide_meta(*cp);
}

static void *opt_malloc(size_t size)
{
  void *ret;

  if (mem_recover)
    {
      ret = whine_malloc(size);
      if (!ret)
	longjmp(mem_jmp, 1);
    }
  else
    ret = safe_malloc(size);
  
  return ret;
}

static char *opt_string_alloc(char *cp)
{
  char *ret = NULL;
  
  if (cp && strlen(cp) != 0)
    {
      ret = opt_malloc(strlen(cp)+1);
      strcpy(ret, cp); 
      
      /* restore hidden metachars */
      unhide_metas(ret);
    }
    
  return ret;
}


/* find next comma, split string with zero and eliminate spaces.
   return start of string following comma */

static char *split_chr(char *s, char c)
{
  char *comma, *p;

  if (!s || !(comma = strchr(s, c)))
    return NULL;
  
  p = comma;
  *comma = ' ';
  
  for (; isspace((int)*comma); comma++);
 
  for (; (p >= s) && isspace((int)*p); p--)
    *p = 0;
    
  return comma;
}

static char *split(char *s)
{
  return split_chr(s, ',');
}

static char *canonicalise_opt(char *s)
{
  char *ret;
  int nomem;

  if (!s)
    return 0;

  unhide_metas(s);
  if (!(ret = canonicalise(s, &nomem)) && nomem)
    {
      if (mem_recover)
	longjmp(mem_jmp, 1);
      else
	die(_("could not get memory"), NULL, EC_NOMEM);
    }

  return ret;
}

static int atoi_check(char *a, int *res)
{
  char *p;

  if (!a)
    return 0;

  unhide_metas(a);
  
  for (p = a; *p; p++)
     if (*p < '0' || *p > '9')
       return 0;

  *res = atoi(a);
  return 1;
}

static int atoi_check16(char *a, int *res)
{
  if (!(atoi_check(a, res)) ||
      *res < 0 ||
      *res > 0xffff)
    return 0;

  return 1;
}
	
static void add_txt(char *name, char *txt)
{
  size_t len = strlen(txt);
  struct txt_record *r = opt_malloc(sizeof(struct txt_record));
  
  r->name = opt_string_alloc(name);
  r->next = daemon->txt;
  daemon->txt = r;
  r->class = C_CHAOS;
  r->txt = opt_malloc(len+1);
  r->len = len+1;
  *(r->txt) = len;
  memcpy((r->txt)+1, txt, len);
}

static void do_usage(void)
{
  char buff[100];
  int i, j;

  struct {
    char handle;
    int val;
  } tab[] = {
    { '$', CACHESIZ },
    { '*', EDNS_PKTSZ },
    { '&', MAXLEASES },
    { '!', FTABSIZ },
    { '#', TFTP_MAX_CONNECTIONS },
    { '\0', 0 }
  };

  printf(_("Usage: dnsmasq [options]\n\n"));
#ifndef HAVE_GETOPT_LONG
  printf(_("Use short options only on the command line.\n"));
#endif
  printf(_("Valid options are:\n"));
  
  for (i = 0; usage[i].opt != 0; i++)
    {
      char *desc = usage[i].flagdesc; 
      char *eq = "=";
      
      if (!desc || *desc == '[')
	eq = "";
      
      if (!desc)
	desc = "";

      for ( j = 0; opts[j].name; j++)
	if (opts[j].val == usage[i].opt)
	  break;
      if (usage[i].opt < 256)
	sprintf(buff, "-%c, ", usage[i].opt);
      else
	sprintf(buff, "    ");
      
      sprintf(buff+4, "--%s%s%s", opts[j].name, eq, desc);
      printf("%-36.36s", buff);
	     
      if (usage[i].arg)
	{
	  strcpy(buff, usage[i].arg);
	  for (j = 0; tab[j].handle; j++)
	    if (tab[j].handle == *(usage[i].arg))
	      sprintf(buff, "%d", tab[j].val);
	}
      printf(_(usage[i].desc), buff);
      printf("\n");
    }
}

#ifdef HAVE_DHCP
static void display_opts(void)
{
  int i;
  
  printf(_("Known DHCP options:\n"));
  
  for (i = 0; opttab[i].name; i++)
    if (!(opttab[i].size & OT_INTERNAL))
      printf("%3d %s\n", opttab[i].val, opttab[i].name);
}

/* This is too insanely large to keep in-line in the switch */
static char *parse_dhcp_opt(char *arg, int flags)
{
  struct dhcp_opt *new = opt_malloc(sizeof(struct dhcp_opt));
  char lenchar = 0, *cp;
  int i, addrs, digs, is_addr, is_hex, is_dec, is_string, dots;
  char *comma = NULL, *problem = NULL;
  struct dhcp_netid *np = NULL;
  unsigned char opt_len = 0;

  new->len = 0;
  new->flags = flags;
  new->netid = NULL;
  new->val = NULL;
  new->opt = 0;
  
  while (arg)
    {
      comma = split(arg);      

      for (cp = arg; *cp; cp++)
	if (*cp < '0' || *cp > '9')
	  break;
      
      if (!*cp)
	{
	  new->opt = atoi(arg);
	  opt_len = 0;
	  break;
	}
      
      if (strstr(arg, "option:") == arg)
	{
	  for (i = 0; opttab[i].name; i++)
	    if (!(opttab[i].size & OT_INTERNAL) &&
		strcasecmp(opttab[i].name, arg+7) == 0)
	      {
		new->opt = opttab[i].val;
		opt_len = opttab[i].size;
		break;
	      }
	  /* option:<optname> must follow tag and vendor string. */
	  break;
	}
      else if (strstr(arg, "vendor:") == arg)
	{
	  new->u.vendor_class = (unsigned char *)opt_string_alloc(arg+7);
	  new->flags |= DHOPT_VENDOR;
	}
      else if (strstr(arg, "encap:") == arg)
	{
	  new->u.encap = atoi(arg+6);
	  new->flags |= DHOPT_ENCAPSULATE;
	}
      else
	{
	  new->netid = opt_malloc(sizeof (struct dhcp_netid));
	  /* allow optional "net:" for consistency */
	  if (strstr(arg, "net:") == arg)
	    new->netid->net = opt_string_alloc(arg+4);
	  else
	    new->netid->net = opt_string_alloc(arg);
	  new->netid->next = np;
	  np = new->netid;
	}
      
      arg = comma; 
    }
  
  if (new->opt == 0)
    problem = _("bad dhcp-option");
  else if (comma)
    {
      /* characterise the value */
      char c;
      is_addr = is_hex = is_dec = is_string = 1;
      addrs = digs = 1;
      dots = 0;
      for (cp = comma; (c = *cp); cp++)
	if (c == ',')
	  {
	    addrs++;
	    is_dec = is_hex = 0;
	  }
	else if (c == ':')
	  {
	    digs++;
	    is_dec = is_addr = 0;
	  }
	else if (c == '/') 
	  {
	    is_dec = is_hex = 0;
	    if (cp == comma) /* leading / means a pathname */
	      is_addr = 0;
	  } 
	else if (c == '.')	
	  {
	    is_dec = is_hex = 0;
	    dots++;
	  }
	else if (c == '-')
	  is_hex = is_addr = 0;
	else if (c == ' ')
	  is_dec = is_hex = 0;
	else if (!(c >='0' && c <= '9'))
	  {
	    is_addr = 0;
	    if (cp[1] == 0 && is_dec &&
		(c == 'b' || c == 's' || c == 'i'))
	      {
		lenchar = c;
		*cp = 0;
	      }
	    else
	      is_dec = 0;
	    if (!((c >='A' && c <= 'F') ||
		  (c >='a' && c <= 'f') || 
		  (c == '*' && (flags & DHOPT_MATCH))))
	      is_hex = 0;
	  }
     
      /* We know that some options take addresses */

      if (opt_len & OT_ADDR_LIST)
	{
	  is_string = is_dec = is_hex = 0;
	  if (!is_addr || dots == 0)
	    problem = _("bad IP address");
	}
	  
      if (is_hex && digs > 1)
	{
	  new->len = digs;
	  new->val = opt_malloc(new->len);
	  parse_hex(comma, new->val, digs, (flags & DHOPT_MATCH) ? &new->u.wildcard_mask : NULL, NULL);
	  new->flags |= DHOPT_HEX;
	}
      else if (is_dec)
	{
	  int i, val = atoi(comma);
	  /* assume numeric arg is 1 byte except for
	     options where it is known otherwise.
	     For vendor class option, we have to hack. */
	  if (opt_len != 0)
	    new->len = opt_len;
	  else if (val & 0xffff0000)
	    new->len = 4;
	  else if (val & 0xff00)
	    new->len = 2;
	  else
	    new->len = 1;

	  if (lenchar == 'b')
	    new->len = 1;
	  else if (lenchar == 's')
	    new->len = 2;
	  else if (lenchar == 'i')
	    new->len = 4;
	  
	  new->val = opt_malloc(new->len);
	  for (i=0; i<new->len; i++)
	    new->val[i] = val>>((new->len - i - 1)*8);
	}
      else if (is_addr)	
	{
	  struct in_addr in;
	  unsigned char *op;
	  char *slash;
	  /* max length of address/subnet descriptor is five bytes,
	     add one for the option 120 enc byte too */
	  new->val = op = opt_malloc((5 * addrs) + 1);
	  new->flags |= DHOPT_ADDR;

	  if (!(new->flags & DHOPT_ENCAPSULATE) && new->opt == 120)
	    {
	      *(op++) = 1; /* RFC 3361 "enc byte" */
	      new->flags &= ~DHOPT_ADDR;
	    }
	  while (addrs--) 
	    {
	      cp = comma;
	      comma = split(cp);
	      slash = split_chr(cp, '/');
	      in.s_addr = inet_addr(cp);
	      if (!slash)
		{
		  memcpy(op, &in, INADDRSZ);
		  op += INADDRSZ;
		}
	      else
		{
		  unsigned char *p = (unsigned char *)&in;
		  int netsize = atoi(slash);
		  *op++ = netsize;
		  if (netsize > 0)
		    *op++ = *p++;
		  if (netsize > 8)
		    *op++ = *p++;
		  if (netsize > 16)
		    *op++ = *p++;
		  if (netsize > 24)
		    *op++ = *p++;
		  new->flags &= ~DHOPT_ADDR; /* cannot re-write descriptor format */
		} 
	    }
	  new->len = op - new->val;
	}
      else if (is_string)
	{
	  /* text arg */
	  if ((new->opt == 119 || new->opt == 120) && !(new->flags & DHOPT_ENCAPSULATE))
	    {
	      /* dns search, RFC 3397, or SIP, RFC 3361 */
	      unsigned char *q, *r, *tail;
	      unsigned char *p, *m = NULL, *newp;
	      size_t newlen, len = 0;
	      int header_size = (new->opt == 119) ? 0 : 1;
	      
	      arg = comma;
	      comma = split(arg);
	      
	      while (arg && *arg)
		{
		  char *dom;
		  if (!(dom = arg = canonicalise_opt(arg)))
		    {
		      problem = _("bad domain in dhcp-option");
		      break;
		    }
		  
		  newp = opt_malloc(len + strlen(arg) + 2 + header_size);
		  if (m)
		    memcpy(newp, m, header_size + len);
		  m = newp;
		  p = m + header_size;
		  q = p + len;
		  
		  /* add string on the end in RFC1035 format */
		  while (*arg) 
		    {
		      unsigned char *cp = q++;
		      int j;
		      for (j = 0; *arg && (*arg != '.'); arg++, j++)
			*q++ = *arg;
		      *cp = j;
		      if (*arg)
			arg++;
		    }
		  *q++ = 0;
		  free(dom);

		  /* Now tail-compress using earlier names. */
		  newlen = q - p;
		  for (tail = p + len; *tail; tail += (*tail) + 1)
		    for (r = p; r - p < (int)len; r += (*r) + 1)
		      if (strcmp((char *)r, (char *)tail) == 0)
			{
			  PUTSHORT((r - p) | 0xc000, tail); 
			  newlen = tail - p;
			  goto end;
			}
		end:
		  len = newlen;
		  
		  arg = comma;
		  comma = split(arg);
		}
      
	      /* RFC 3361, enc byte is zero for names */
	      if (new->opt == 120)
		m[0] = 0;
	      new->len = (int) len + header_size;
	      new->val = m;
	    }
	  else
	    {
	      new->len = strlen(comma);
	      /* keep terminating zero on string */
	      new->val = (unsigned char *)opt_string_alloc(comma);
	      new->flags |= DHOPT_STRING;
	    }
	}
    }

  if ((new->len > 255) || (new->len > 253 && (new->flags & (DHOPT_VENDOR | DHOPT_ENCAPSULATE))))
    problem = _("dhcp-option too long");
  
  if (!problem)
    {
      if (flags == DHOPT_MATCH)
	{
	  if ((new->flags & (DHOPT_ENCAPSULATE | DHOPT_VENDOR)) ||
	      !new->netid ||
	      new->netid->next)
	    problem = _("illegal dhcp-match");
	  else
	    {
	      new->next = daemon->dhcp_match;
	      daemon->dhcp_match = new;
	    }
	}
      else     
	{
	  new->next = daemon->dhcp_opts;
	  daemon->dhcp_opts = new;
	}
    }

  return problem;
}

#endif

static char *one_opt(int option, char *arg, char *gen_prob, int nest)
{      
  int i;
  char *comma, *problem = NULL;;

  if (option == '?')
    return gen_prob;
  
  for (i=0; usage[i].opt != 0; i++)
    if (usage[i].opt == option)
      {
	 int rept = usage[i].rept;
	 
	 if (nest == 0)
	   {
	     /* command line */
	     if (rept == ARG_USED_CL)
	       return _("illegal repeated flag");
	     if (rept == ARG_ONE)
	       usage[i].rept = ARG_USED_CL;
	   }
	 else
	   {
	     /* allow file to override command line */
	     if (rept == ARG_USED_FILE)
	       return _("illegal repeated keyword");
	     if (rept == ARG_USED_CL || rept == ARG_ONE)
	       usage[i].rept = ARG_USED_FILE;
	   }

	 if (rept != ARG_DUP && rept != ARG_ONE && rept != ARG_USED_CL) 
	   {
	     daemon->options |= rept;
	     return NULL;
	   }
       
	 break;
      }
  
  switch (option)
    { 
    case 'C': /* --conf-file */
      {
	char *file = opt_string_alloc(arg);
	if (file)
	  {
	    one_file(file, nest, 0);
	    free(file);
	  }
	break;
      }

    case '7': /* --conf-dir */	      
      {
	DIR *dir_stream;
	struct dirent *ent;
	char *directory, *path;
	struct list {
	  char *suffix;
	  struct list *next;
	} *ignore_suffix = NULL, *li;
	
	comma = split(arg);
	if (!(directory = opt_string_alloc(arg)))
	  break;
	
	for (arg = comma; arg; arg = comma) 
	  {
	    comma = split(arg);
	    li = opt_malloc(sizeof(struct list));
	    li->next = ignore_suffix;
	    ignore_suffix = li;
	    /* Have to copy: buffer is overwritten */
	    li->suffix = opt_string_alloc(arg);
	  };
	
	if (!(dir_stream = opendir(directory)))
	  die(_("cannot access directory %s: %s"), directory, EC_FILE);
	
	while ((ent = readdir(dir_stream)))
	  {
	    size_t len = strlen(ent->d_name);
	    struct stat buf;
	    
	    /* ignore emacs backups and dotfiles */
	    if (len == 0 ||
		ent->d_name[len - 1] == '~' ||
		(ent->d_name[0] == '#' && ent->d_name[len - 1] == '#') ||
		ent->d_name[0] == '.')
	      continue;

	    for (li = ignore_suffix; li; li = li->next)
	      {
		/* check for proscribed suffices */
		size_t ls = strlen(li->suffix);
		if (len > ls &&
		    strcmp(li->suffix, &ent->d_name[len - ls]) == 0)
		  break;
	      }
	    if (li)
	      continue;
	    
	    path = opt_malloc(strlen(directory) + len + 2);
	    strcpy(path, directory);
	    strcat(path, "/");
	    strcat(path, ent->d_name);

	    if (stat(path, &buf) == -1)
	      die(_("cannot access %s: %s"), path, EC_FILE);
	    /* only reg files allowed. */
	    if (!S_ISREG(buf.st_mode))
	      continue;
	    
	    /* dir is one level, so files must be readable */
	    one_file(path, nest + 1, 0);
	    free(path);
	  }
     
	closedir(dir_stream);
	free(directory);
	for(; ignore_suffix; ignore_suffix = li)
	  {
	    li = ignore_suffix->next;
	    free(ignore_suffix->suffix);
	    free(ignore_suffix);
	  }
	      
	break;
      }

    case '8': /* --log-facility */
      /* may be a filename */
      if (strchr(arg, '/'))
	daemon->log_file = opt_string_alloc(arg);
      else
	{
#ifdef __ANDROID__
	    problem = "Android does not support log facilities";
#else	  
	  for (i = 0; facilitynames[i].c_name; i++)
	    if (hostname_isequal((char *)facilitynames[i].c_name, arg))
	      break;
	  
	  if (facilitynames[i].c_name)
	    daemon->log_fac = facilitynames[i].c_val;
	  else
	    problem = "bad log facility";
#endif
	}
      break;
      
    case 'x': /* --pid-file */
      daemon->runfile = opt_string_alloc(arg);
      break;

    case LOPT_DHCP_HOST: /* --dhcp-hostfile */
      if (daemon->dhcp_hosts_file)
	problem = _("only one dhcp-hostsfile allowed");
      else
	daemon->dhcp_hosts_file = opt_string_alloc(arg);
      break;
     
    case LOPT_DHCP_OPTS: /* --dhcp-optsfile */
      if (daemon->dhcp_opts_file)
	problem = _("only one dhcp-optsfile allowed");
      else
	daemon->dhcp_opts_file = opt_string_alloc(arg);
      break; 
      
    case 'r': /* --resolv-file */
      {
	char *name = opt_string_alloc(arg);
	struct resolvc *new, *list = daemon->resolv_files;
	
	if (list && list->is_default)
	  {
	    /* replace default resolv file - possibly with nothing */
	    if (name)
	      {
		list->is_default = 0;
		list->name = name;
	      }
	    else
	      list = NULL;
	  }
	else if (name)
	  {
	    new = opt_malloc(sizeof(struct resolvc));
	    new->next = list;
	    new->name = name;
	    new->is_default = 0;
	    new->mtime = 0;
	    new->logged = 0;
	    list = new;
	  }
	daemon->resolv_files = list;
	break;
      }
      
    case 'm':  /* --mx-host */
      {
	int pref = 1;
	struct mx_srv_record *new;
	char *name, *target = NULL;

	if ((comma = split(arg)))
	  {
	    char *prefstr;
	    if ((prefstr = split(comma)) && !atoi_check16(prefstr, &pref))
	      problem = _("bad MX preference");
	  }
	
	if (!(name = canonicalise_opt(arg)) || 
	    (comma && !(target = canonicalise_opt(comma))))
	  problem = _("bad MX name");
	
	new = opt_malloc(sizeof(struct mx_srv_record));
	new->next = daemon->mxnames;
	daemon->mxnames = new;
	new->issrv = 0;
	new->name = name;
	new->target = target; /* may be NULL */
	new->weight = pref;
	break;
      }
      
    case 't': /*  --mx-target */
      if (!(daemon->mxtarget = canonicalise_opt(arg)))
	problem = _("bad MX target");
      break;

#ifdef HAVE_DHCP      
    case 'l':  /* --dhcp-leasefile */
      daemon->lease_file = opt_string_alloc(arg);
      break;
      
    case '6': /* --dhcp-script */
#  if defined(NO_FORK)
      problem = _("cannot run scripts under uClinux");
#  elif !defined(HAVE_SCRIPT)
      problem = _("recompile with HAVE_SCRIPT defined to enable lease-change scripts");
#  else
      daemon->lease_change_command = opt_string_alloc(arg);
#  endif
      break;
#endif

    case 'H': /* --addn-hosts */
      {
	struct hostsfile *new = opt_malloc(sizeof(struct hostsfile));
	static int hosts_index = 1;
	new->fname = opt_string_alloc(arg);
	new->index = hosts_index++;
	new->flags = 0;
	new->next = daemon->addn_hosts;
	daemon->addn_hosts = new;
	break;
      }
      
    case 's': /* --domain */
      if (strcmp (arg, "#") == 0)
	daemon->options |= OPT_RESOLV_DOMAIN;
      else
	{
	  char *d;
	  comma = split(arg);
	  if (!(d = canonicalise_opt(arg)))
	    option = '?';
	  else
	    {
	      if (comma)
		{
		  struct cond_domain *new = safe_malloc(sizeof(struct cond_domain));
		  unhide_metas(comma);
		  if ((arg = split_chr(comma, '/')))
		    {
		      int mask;
		      if ((new->start.s_addr = inet_addr(comma)) == (in_addr_t)-1 ||
			  !atoi_check(arg, &mask))
			option = '?';
		      else
			{
			  mask = (1 << (32 - mask)) - 1;
			  new->start.s_addr = ntohl(htonl(new->start.s_addr) & ~mask);
			  new->end.s_addr = new->start.s_addr | htonl(mask);
			}
		    }
		  else if ((arg = split(comma)))
		    {
		      if ((new->start.s_addr = inet_addr(comma)) == (in_addr_t)-1 ||
			  (new->end.s_addr = inet_addr(arg)) == (in_addr_t)-1)
			option = '?';
		    }
		  else if ((new->start.s_addr = new->end.s_addr = inet_addr(comma)) == (in_addr_t)-1)
		    option = '?';

		  new->domain = d;
		  new->next = daemon->cond_domain;
		  daemon->cond_domain = new;
		}
	      else
		daemon->domain_suffix = d;
	    }
	}
      break;
      
    case 'u':  /* --user */
      daemon->username = opt_string_alloc(arg);
      break;
      
    case 'g':  /* --group */
      daemon->groupname = opt_string_alloc(arg);
      daemon->group_set = 1;
      break;

#ifdef HAVE_DHCP
    case LOPT_SCRIPTUSR: /* --scriptuser */
      daemon->scriptuser = opt_string_alloc(arg);
      break;
#endif
      
    case 'i':  /* --interface */
      do {
	struct iname *new = opt_malloc(sizeof(struct iname));
	comma = split(arg);
	new->next = daemon->if_names;
	daemon->if_names = new;
	/* new->name may be NULL if someone does
	   "interface=" to disable all interfaces except loop. */
	new->name = opt_string_alloc(arg);
	new->isloop = new->used = 0;
	arg = comma;
      } while (arg);
      break;
      
    case 'I':  /* --except-interface */
    case '2':  /* --no-dhcp-interface */
      do {
	struct iname *new = opt_malloc(sizeof(struct iname));
	comma = split(arg);
	new->name = opt_string_alloc(arg);
	if (option == 'I')
	  {
	    new->next = daemon->if_except;
	    daemon->if_except = new;
	  }
	else
	  {
	    new->next = daemon->dhcp_except;
	    daemon->dhcp_except = new;
	  }
	arg = comma;
      } while (arg);
      break;
      
    case 'B':  /* --bogus-nxdomain */
      {
	struct in_addr addr;
	unhide_metas(arg);
	if (arg && (addr.s_addr = inet_addr(arg)) != (in_addr_t)-1)
	  {
	    struct bogus_addr *baddr = opt_malloc(sizeof(struct bogus_addr));
	    baddr->next = daemon->bogus_addr;
	    daemon->bogus_addr = baddr;
	    baddr->addr = addr;
	  }
	else
	  option = '?'; /* error */
	break;	
      }
      
    case 'a':  /* --listen-address */
      do {
	struct iname *new = opt_malloc(sizeof(struct iname));
	comma = split(arg);
	unhide_metas(arg);
	new->next = daemon->if_addrs;
	if (arg && (new->addr.in.sin_addr.s_addr = inet_addr(arg)) != (in_addr_t)-1)
	  {
	    new->addr.sa.sa_family = AF_INET;
#ifdef HAVE_SOCKADDR_SA_LEN
	    new->addr.in.sin_len = sizeof(new->addr.in);
#endif
	  }
#ifdef HAVE_IPV6
	else if (arg && inet_pton(AF_INET6, arg, &new->addr.in6.sin6_addr) > 0)
	  {
	    new->addr.sa.sa_family = AF_INET6;
	    new->addr.in6.sin6_flowinfo = 0;
	    new->addr.in6.sin6_scope_id = 0;
#ifdef HAVE_SOCKADDR_SA_LEN
	    new->addr.in6.sin6_len = sizeof(new->addr.in6);
#endif
	  }
#endif
	else
	  {
	    option = '?'; /* error */
	    break;
	  }
	
	daemon->if_addrs = new;
	arg = comma;
      } while (arg);
      break;
      
    case 'S':        /*  --server */
    case LOPT_LOCAL: /*  --local */
    case 'A':        /*  --address */
      {
	struct server *serv, *newlist = NULL;
	
	unhide_metas(arg);
	
	if (arg && *arg == '/')
	  {
	    char *end;
	    arg++;
	    while ((end = split_chr(arg, '/')))
	      {
		char *domain = NULL;
		/* # matches everything and becomes a zero length domain string */
		if (strcmp(arg, "#") == 0)
		  domain = "";
		else if (strlen (arg) != 0 && !(domain = canonicalise_opt(arg)))
		  option = '?';
		serv = opt_malloc(sizeof(struct server));
		memset(serv, 0, sizeof(struct server));
		serv->next = newlist;
		newlist = serv;
		serv->domain = domain;
		serv->flags = domain ? SERV_HAS_DOMAIN : SERV_FOR_NODOTS;
		arg = end;
	      }
	    if (!newlist)
	      {
		option = '?';
		break;
	      }
	    
	  }
	else
	  {
	    newlist = opt_malloc(sizeof(struct server));
	    memset(newlist, 0, sizeof(struct server));
	  }
	
	if (option == 'A')
	  {
	    newlist->flags |= SERV_LITERAL_ADDRESS;
	    if (!(newlist->flags & SERV_TYPE))
	      option = '?';
	  }
	
	if (!arg || !*arg)
	  {
	    newlist->flags |= SERV_NO_ADDR; /* no server */
	    if (newlist->flags & SERV_LITERAL_ADDRESS)
	      option = '?';
	  }
	else
	  {
	    int source_port = 0, serv_port = NAMESERVER_PORT;
	    char *portno, *source;
	    
	    if ((source = split_chr(arg, '@')) && /* is there a source. */
		(portno = split_chr(source, '#')) &&
		!atoi_check16(portno, &source_port))
	      problem = _("bad port");
	       	    
	    if ((portno = split_chr(arg, '#')) && /* is there a port no. */
		!atoi_check16(portno, &serv_port))
	      problem = _("bad port");
	    
	    if ((newlist->addr.in.sin_addr.s_addr = inet_addr(arg)) != (in_addr_t) -1)
	      {
		newlist->addr.in.sin_port = htons(serv_port);	
		newlist->source_addr.in.sin_port = htons(source_port); 
		newlist->addr.sa.sa_family = newlist->source_addr.sa.sa_family = AF_INET;
#ifdef HAVE_SOCKADDR_SA_LEN
		newlist->source_addr.in.sin_len = newlist->addr.in.sin_len = sizeof(struct sockaddr_in);
#endif
		if (source)
		  {
		    newlist->flags |= SERV_HAS_SOURCE;
		    if ((newlist->source_addr.in.sin_addr.s_addr = inet_addr(source)) == (in_addr_t) -1)
		      {
#if defined(SO_BINDTODEVICE)
			newlist->source_addr.in.sin_addr.s_addr = INADDR_ANY;
			strncpy(newlist->interface, source, IF_NAMESIZE);
#else
			problem = _("interface binding not supported");
#endif
		      }
		  }
		else
		  newlist->source_addr.in.sin_addr.s_addr = INADDR_ANY;
	      }
#ifdef HAVE_IPV6
	    else if (inet_pton(AF_INET6, arg, &newlist->addr.in6.sin6_addr) > 0)
	      {
		newlist->addr.in6.sin6_port = htons(serv_port);
		newlist->source_addr.in6.sin6_port = htons(source_port);
		newlist->addr.sa.sa_family = newlist->source_addr.sa.sa_family = AF_INET6;
#ifdef HAVE_SOCKADDR_SA_LEN
		newlist->addr.in6.sin6_len = newlist->source_addr.in6.sin6_len = sizeof(newlist->addr.in6);
#endif
		if (source)
		  {
		     newlist->flags |= SERV_HAS_SOURCE;
		     if (inet_pton(AF_INET6, source, &newlist->source_addr.in6.sin6_addr) == 0)
		      {
#if defined(SO_BINDTODEVICE)
			newlist->source_addr.in6.sin6_addr = in6addr_any; 
			strncpy(newlist->interface, source, IF_NAMESIZE);
#else
			problem = _("interface binding not supported");
#endif
		      }
		  }
		else
		  newlist->source_addr.in6.sin6_addr = in6addr_any; 
	      }
#endif
	    else
	      option = '?'; /* error */
	    
	  }
	
	serv = newlist;
	while (serv->next)
	  {
	    serv->next->flags = serv->flags;
	    serv->next->addr = serv->addr;
	    serv->next->source_addr = serv->source_addr;
	    serv = serv->next;
	  }
	serv->next = daemon->servers;
	daemon->servers = newlist;
	break;
      }
      
    case 'c':  /* --cache-size */
      {
	int size;
	
	if (!atoi_check(arg, &size))
	  option = '?';
	else
	  {
	    /* zero is OK, and means no caching. */
	    
	    if (size < 0)
	      size = 0;
	    else if (size > 10000)
	      size = 10000;
	    
	    daemon->cachesize = size;
	  }
	break;
      }
      
    case 'p':  /* --port */
      if (!atoi_check16(arg, &daemon->port))
	option = '?';
      break;
    
    case LOPT_MINPORT:  /* --min-port */
      if (!atoi_check16(arg, &daemon->min_port))
	option = '?';
      break;

    case '0':  /* --dns-forward-max */
      if (!atoi_check(arg, &daemon->ftabsize))
	option = '?';
      break;  
    
    case LOPT_MAX_LOGS:  /* --log-async */
      daemon->max_logs = LOG_MAX; /* default */
      if (arg && !atoi_check(arg, &daemon->max_logs))
	option = '?';
      else if (daemon->max_logs > 100)
	daemon->max_logs = 100;
      break;  

    case 'P': /* --edns-packet-max */
      {
	int i;
	if (!atoi_check(arg, &i))
	  option = '?';
	daemon->edns_pktsz = (unsigned short)i;	
	break;
      }
      
    case 'Q':  /* --query-port */
      if (!atoi_check16(arg, &daemon->query_port))
	option = '?';
      /* if explicitly set to zero, use single OS ephemeral port
	 and disable random ports */
      if (daemon->query_port == 0)
	daemon->osport = 1;
      break;
      
    case 'T':         /* --local-ttl */
    case LOPT_NEGTTL: /* --neg-ttl */
      {
	int ttl;
	if (!atoi_check(arg, &ttl))
	  option = '?';
	else if (option == LOPT_NEGTTL)
	  daemon->neg_ttl = (unsigned long)ttl;
	else
	  daemon->local_ttl = (unsigned long)ttl;
	break;
      }
      
#ifdef HAVE_DHCP
    case 'X': /* --dhcp-lease-max */
      if (!atoi_check(arg, &daemon->dhcp_max))
	option = '?';
      break;
#endif
      
#ifdef HAVE_TFTP
    case LOPT_TFTP_MAX:  /*  --tftp-max */
      if (!atoi_check(arg, &daemon->tftp_max))
	option = '?';
      break;  

    case LOPT_PREFIX: /* --tftp-prefix */
      daemon->tftp_prefix = opt_string_alloc(arg);
      break;

    case LOPT_TFTPPORTS: /* --tftp-port-range */
      if (!(comma = split(arg)) || 
	  !atoi_check16(arg, &daemon->start_tftp_port) ||
	  !atoi_check16(comma, &daemon->end_tftp_port))
	problem = _("bad port range");
      
      if (daemon->start_tftp_port > daemon->end_tftp_port)
	{
	  int tmp = daemon->start_tftp_port;
	  daemon->start_tftp_port = daemon->end_tftp_port;
	  daemon->end_tftp_port = tmp;
	} 
      
      break;
#endif
	      
    case LOPT_BRIDGE:   /* --bridge-interface */
      {
	struct dhcp_bridge *new = opt_malloc(sizeof(struct dhcp_bridge));
	if (!(comma = split(arg)))
	  {
	    problem = _("bad bridge-interface");
	    break;
	  }
	
	strncpy(new->iface, arg, IF_NAMESIZE);
	new->alias = NULL;
	new->next = daemon->bridges;
	daemon->bridges = new;

	do {
	  arg = comma;
	  comma = split(arg);
	  if (strlen(arg) != 0)
	    {
	      struct dhcp_bridge *b = opt_malloc(sizeof(struct dhcp_bridge)); 
	      b->next = new->alias;
	      new->alias = b;
	      strncpy(b->iface, arg, IF_NAMESIZE);
	    }
	} while (comma);
	
	break;
      }

#ifdef HAVE_DHCP
    case 'F':  /* --dhcp-range */
      {
	int k, leasepos = 2;
	char *cp, *a[5] = { NULL, NULL, NULL, NULL, NULL };
	struct dhcp_context *new = opt_malloc(sizeof(struct dhcp_context));
	
	new->next = daemon->dhcp;
	new->lease_time = DEFLEASE;
	new->addr_epoch = 0;
	new->netmask.s_addr = 0;
	new->broadcast.s_addr = 0;
	new->router.s_addr = 0;
	new->netid.net = NULL;
	new->filter = NULL;
	new->flags = 0;
	
	gen_prob = _("bad dhcp-range");
	
	if (!arg)
	  {
	    option = '?';
	    break;
	  }
	
	while(1)
	  {
	    for (cp = arg; *cp; cp++)
	      if (!(*cp == ' ' || *cp == '.' ||  (*cp >='0' && *cp <= '9')))
		break;
	    
	    if (*cp != ',' && (comma = split(arg)))
	      {
		if (strstr(arg, "net:") == arg)
		  {
		    struct dhcp_netid *tt = opt_malloc(sizeof (struct dhcp_netid));
		    tt->net = opt_string_alloc(arg+4);
		    tt->next = new->filter;
		    new->filter = tt;
		  }
		else
		  {
		    if (new->netid.net)
		      problem = _("only one netid tag allowed");
		    else
		      new->netid.net = opt_string_alloc(arg);
		  }
		arg = comma;
	      }
	    else
	      {
		a[0] = arg;
		break;
	      }
	  }
	
	for (k = 1; k < 5; k++)
	  if (!(a[k] = split(a[k-1])))
	    break;
	
	if ((k < 2) || ((new->start.s_addr = inet_addr(a[0])) == (in_addr_t)-1))
	  option = '?';
	else if (strcmp(a[1], "static") == 0)
	  {
	    new->end = new->start;
	    new->flags |= CONTEXT_STATIC;
	  }
	else if (strcmp(a[1], "proxy") == 0)
	  {
	    new->end = new->start;
	    new->flags |= CONTEXT_PROXY;
	  }
	else if ((new->end.s_addr = inet_addr(a[1])) == (in_addr_t)-1)
	  option = '?';
	
	if (ntohl(new->start.s_addr) > ntohl(new->end.s_addr))
	  {
	    struct in_addr tmp = new->start;
	    new->start = new->end;
	    new->end = tmp;
	  }
	
	if (option != '?' && k >= 3 && strchr(a[2], '.') &&  
	    ((new->netmask.s_addr = inet_addr(a[2])) != (in_addr_t)-1))
	  {
	    new->flags |= CONTEXT_NETMASK;
	    leasepos = 3;
	    if (!is_same_net(new->start, new->end, new->netmask))
	      problem = _("inconsistent DHCP range");
	  }
	daemon->dhcp = new;
	
	if (k >= 4 && strchr(a[3], '.') &&  
	    ((new->broadcast.s_addr = inet_addr(a[3])) != (in_addr_t)-1))
	  {
	    new->flags |= CONTEXT_BRDCAST;
	    leasepos = 4;
	  }
	
	if (k >= leasepos+1)
	  {
	    if (strcmp(a[leasepos], "infinite") == 0)
	      new->lease_time = 0xffffffff;
	    else
	      {
		int fac = 1;
		if (strlen(a[leasepos]) > 0)
		  {
		    switch (a[leasepos][strlen(a[leasepos]) - 1])
		      {
		      case 'd':
		      case 'D':
			fac *= 24;
			/* fall though */
		      case 'h':
		      case 'H':
			fac *= 60;
			/* fall through */
		      case 'm':
		      case 'M':
			fac *= 60;
			/* fall through */
		      case 's':
		      case 'S':
			a[leasepos][strlen(a[leasepos]) - 1] = 0;
		      }
		    
		    new->lease_time = atoi(a[leasepos]) * fac;
		    /* Leases of a minute or less confuse
		       some clients, notably Apple's */
		    if (new->lease_time < 120)
		      new->lease_time = 120;
		  }
	      }
	  }
	break;
      }

    case LOPT_BANK:
    case 'G':  /* --dhcp-host */
      {
	int j, k = 0;
	char *a[6] = { NULL, NULL, NULL, NULL, NULL, NULL };
	struct dhcp_config *new;
	struct in_addr in;
	
	new = opt_malloc(sizeof(struct dhcp_config));
	
	new->next = daemon->dhcp_conf;
	new->flags = (option == LOPT_BANK) ? CONFIG_BANK : 0;
	new->hwaddr = NULL;
	
	if ((a[0] = arg))
	  for (k = 1; k < 6; k++)
	    if (!(a[k] = split(a[k-1])))
	      break;
	
	for (j = 0; j < k; j++)
	  if (strchr(a[j], ':')) /* ethernet address, netid or binary CLID */
	    {
	      char *arg = a[j];
	      
	      if ((arg[0] == 'i' || arg[0] == 'I') &&
		  (arg[1] == 'd' || arg[1] == 'D') &&
		  arg[2] == ':')
		{
		  if (arg[3] == '*')
		    new->flags |= CONFIG_NOCLID;
		  else
		    {
		      int len;
		      arg += 3; /* dump id: */
		      if (strchr(arg, ':'))
			len = parse_hex(arg, (unsigned char *)arg, -1, NULL, NULL);
		      else
			{
			  unhide_metas(arg);
			  len = (int) strlen(arg);
			}

		      if ((new->clid = opt_malloc(len)))
			{
			  new->flags |= CONFIG_CLID;
			  new->clid_len = len;
			  memcpy(new->clid, arg, len);
			}
		    }
		}
	      else if (strstr(arg, "net:") == arg)
		{
		  int len = strlen(arg + 4) + 1;
		  if ((new->netid.net = opt_malloc(len)))
		    {
		      new->flags |= CONFIG_NETID;
		      strcpy(new->netid.net, arg+4);
		      unhide_metas(new->netid.net);
		    }
		}
	      else 
		{
		  struct hwaddr_config *newhw = opt_malloc(sizeof(struct hwaddr_config));
		  newhw->next = new->hwaddr;
		  new->hwaddr = newhw;
		  newhw->hwaddr_len = parse_hex(a[j], newhw->hwaddr, DHCP_CHADDR_MAX, 
						&newhw->wildcard_mask, &newhw->hwaddr_type);
		}
	    }
	  else if (strchr(a[j], '.') && (in.s_addr = inet_addr(a[j])) != (in_addr_t)-1)
	    {
	      new->addr = in;
	      new->flags |= CONFIG_ADDR;
	    }
	  else
	    {
	      char *cp, *lastp = NULL, last = 0;
	      int fac = 1;
	      
	      if (strlen(a[j]) > 1)
		{
		  lastp = a[j] + strlen(a[j]) - 1;
		  last = *lastp;
		  switch (last)
		    {
		    case 'd':
		    case 'D':
		      fac *= 24;
		      /* fall through */
		    case 'h':
		    case 'H':
		      fac *= 60;
		      /* fall through */
		    case 'm':
		    case 'M':
		      fac *= 60;
		      /* fall through */
		    case 's':
		    case 'S':
		      *lastp = 0;
		    }
		}
	      
	      for (cp = a[j]; *cp; cp++)
		if (!isdigit((int)*cp) && *cp != ' ')
		  break;
	      
	      if (*cp)
		{
		  if (lastp)
		    *lastp = last;
		  if (strcmp(a[j], "infinite") == 0)
		    {
		      new->lease_time = 0xffffffff;
		      new->flags |= CONFIG_TIME;
		    }
		  else if (strcmp(a[j], "ignore") == 0)
		    new->flags |= CONFIG_DISABLE;
		  else
		    {
		      if (!(new->hostname = canonicalise_opt(a[j])) ||
			  !legal_hostname(new->hostname))
			problem = _("bad DHCP host name");
		      else
			new->flags |= CONFIG_NAME;
		      new->domain = NULL;			
		    }
		}
	      else
		{
		  new->lease_time = atoi(a[j]) * fac; 
		  /* Leases of a minute or less confuse
		     some clients, notably Apple's */
		  if (new->lease_time < 120)
		    new->lease_time = 120;
		  new->flags |= CONFIG_TIME;
		}
	    }
	
	daemon->dhcp_conf = new;
	break;
      }
      
    case 'O':           /* --dhcp-option */
    case LOPT_FORCE:    /* --dhcp-option-force */
    case LOPT_OPTS:
    case LOPT_MATCH:    /* --dhcp-match */
      problem = parse_dhcp_opt(arg, 
			       option == LOPT_FORCE ? DHOPT_FORCE : 
			       (option == LOPT_MATCH ? DHOPT_MATCH :
			       (option == LOPT_OPTS ? DHOPT_BANK : 0)));
      break;
      
    case 'M': /* --dhcp-boot */
      {
	struct dhcp_netid *id = NULL;
	while (arg && strstr(arg, "net:") == arg)
	  {
	    struct dhcp_netid *newid = opt_malloc(sizeof(struct dhcp_netid));
	    newid->next = id;
	    id = newid;
	    comma = split(arg);
	    newid->net = opt_string_alloc(arg+4);
	    arg = comma;
	  };
	
	if (!arg)
	  option = '?';
	else 
	  {
	    char *dhcp_file, *dhcp_sname = NULL;
	    struct in_addr dhcp_next_server;
	    comma = split(arg);
	    dhcp_file = opt_string_alloc(arg);
	    dhcp_next_server.s_addr = 0;
	    if (comma)
	      {
		arg = comma;
		comma = split(arg);
		dhcp_sname = opt_string_alloc(arg);
		if (comma)
		  {
		    unhide_metas(comma);
		    if ((dhcp_next_server.s_addr = inet_addr(comma)) == (in_addr_t)-1)
		      option = '?';
		  }
	      }
	    if (option != '?')
	      {
		struct dhcp_boot *new = opt_malloc(sizeof(struct dhcp_boot));
		new->file = dhcp_file;
		new->sname = dhcp_sname;
		new->next_server = dhcp_next_server;
		new->netid = id;
		new->next = daemon->boot_config;
		daemon->boot_config = new;
	      }
	  }
	
	break;
      }

    case LOPT_PXE_PROMT:  /* --pxe-prompt */
       {
	 struct dhcp_opt *new = opt_malloc(sizeof(struct dhcp_opt));
	 int timeout;

	 new->netid = NULL;
	 new->opt = 10; /* PXE_MENU_PROMPT */

	 while (arg && strstr(arg, "net:") == arg)
	   {
	     struct dhcp_netid *nn = opt_malloc(sizeof (struct dhcp_netid));
	     comma = split(arg);
	     nn->next = new->netid;
	     new->netid = nn;
	     nn->net = opt_string_alloc(arg+4);
	     arg = comma;
	   }
	 
	 if (!arg)
	   option = '?';
	 else
	   {
	     comma = split(arg);
	     unhide_metas(arg);
	     new->len = strlen(arg) + 1;
	     new->val = opt_malloc(new->len);
	     memcpy(new->val + 1, arg, new->len - 1);
	     
	     new->u.vendor_class = (unsigned char *)"PXEClient";
	     new->flags = DHOPT_VENDOR;
	     
	     if (comma && atoi_check(comma, &timeout))
	       *(new->val) = timeout;
	     else
	       *(new->val) = 255;

	     new->next = daemon->dhcp_opts;
	     daemon->dhcp_opts = new;
	     daemon->enable_pxe = 1;
	   }
	 
	 break;
       }
       
    case LOPT_PXE_SERV:  /* --pxe-service */
       {
	 struct pxe_service *new = opt_malloc(sizeof(struct pxe_service));
	 char *CSA[] = { "x86PC", "PC98", "IA64_EFI", "Alpha", "Arc_x86", "Intel_Lean_Client",
			 "IA32_EFI", "BC_EFI", "Xscale_EFI", "x86-64_EFI", NULL };  
	 static int boottype = 32768;
	 
	 new->netid = NULL;
	 new->server.s_addr = 0;

	 while (arg && strstr(arg, "net:") == arg)
	   {
	     struct dhcp_netid *nn = opt_malloc(sizeof (struct dhcp_netid));
	     comma = split(arg);
	     nn->next = new->netid;
	     new->netid = nn;
	     nn->net = opt_string_alloc(arg+4);
	     arg = comma;
	   }
       
	 if (arg && (comma = split(arg)))
	   {
	     for (i = 0; CSA[i]; i++)
	       if (strcasecmp(CSA[i], arg) == 0)
		 break;
	     
	     if (CSA[i] || atoi_check(arg, &i))
	       {
		 arg = comma;
		 comma = split(arg);
		 
		 new->CSA = i;
		 new->menu = opt_string_alloc(arg);
		 
		 if (comma)
		   {
		     arg = comma;
		     comma = split(arg);
		     if (atoi_check(arg, &i))
		       {
			 new->type = i;
			 new->basename = NULL;
		       }
		     else
		       {
			 new->type = boottype++;
			 new->basename = opt_string_alloc(arg);
		       }
		     
		     if (comma && (new->server.s_addr = inet_addr(comma)) == (in_addr_t)-1)
		       option = '?';
		     
		     /* Order matters */
		     new->next = NULL;
		     if (!daemon->pxe_services)
		       daemon->pxe_services = new; 
		     else
		       {
			 struct pxe_service *s;
			 for (s = daemon->pxe_services; s->next; s = s->next);
			 s->next = new;
		       }
		     
		     daemon->enable_pxe = 1;
		     break;
		   }
	       }
	   }
	 
	 option = '?';
	 break;
       }
	 
    case '4':  /* --dhcp-mac */
      {
	if (!(comma = split(arg)))
	  option = '?';
	else
	  {
	    struct dhcp_mac *new = opt_malloc(sizeof(struct dhcp_mac));
	    if (strstr(arg, "net:") == arg)
	      new->netid.net = opt_string_alloc(arg+4);
	    else
	      new->netid.net = opt_string_alloc(arg);
	    unhide_metas(comma);
	    new->hwaddr_len = parse_hex(comma, new->hwaddr, DHCP_CHADDR_MAX, &new->mask, &new->hwaddr_type);
	    new->next = daemon->dhcp_macs;
	    daemon->dhcp_macs = new;
	  }
      }
      break;
      
    case 'U':           /* --dhcp-vendorclass */
    case 'j':           /* --dhcp-userclass */
    case LOPT_CIRCUIT:  /* --dhcp-circuitid */
    case LOPT_REMOTE:   /* --dhcp-remoteid */
    case LOPT_SUBSCR:   /* --dhcp-subscrid */
      {
	if (!(comma = split(arg)))
	  option = '?';
	else
	  {
	    char *p;
	    int dig = 0;
	    struct dhcp_vendor *new = opt_malloc(sizeof(struct dhcp_vendor));
	    if (strstr(arg, "net:") == arg)
	      new->netid.net = opt_string_alloc(arg+4);
	    else
	      new->netid.net = opt_string_alloc(arg);
	    /* check for hex string - must digits may include : must not have nothing else, 
	       only allowed for agent-options. */
	    for (p = comma; *p; p++)
	      if (isxdigit((int)*p))
		dig = 1;
	      else if (*p != ':')
		break;
	    unhide_metas(comma);
	    if (option == 'U' || option == 'j' || *p || !dig)
	      {
		new->len = strlen(comma);  
		new->data = opt_malloc(new->len);
		memcpy(new->data, comma, new->len);
	      }
	    else
	      {
		new->len = parse_hex(comma, (unsigned char *)comma, strlen(comma), NULL, NULL);
		new->data = opt_malloc(new->len);
		memcpy(new->data, comma, new->len);
	      }

	    switch (option)
	      {
	      case 'j':
		new->match_type = MATCH_USER;
		break;
	      case 'U':
		new->match_type = MATCH_VENDOR;
		break; 
	      case LOPT_CIRCUIT:
		new->match_type = MATCH_CIRCUIT;
		break;
	      case LOPT_REMOTE:
		new->match_type = MATCH_REMOTE;
		break;
	      case LOPT_SUBSCR:
		new->match_type = MATCH_SUBSCRIBER;
		break;
	      }
	    new->next = daemon->dhcp_vendors;
	    daemon->dhcp_vendors = new;
	  }
	break;
      }
      
    case LOPT_ALTPORT:   /* --dhcp-alternate-port */
      if (!arg)
	{
	  daemon->dhcp_server_port = DHCP_SERVER_ALTPORT;
	  daemon->dhcp_client_port = DHCP_CLIENT_ALTPORT;
	}
      else
	{
	  comma = split(arg);
	  if (!atoi_check16(arg, &daemon->dhcp_server_port) || 
	      (comma && !atoi_check16(comma, &daemon->dhcp_client_port)))
	    problem = _("invalid port number");
	  if (!comma)
	    daemon->dhcp_client_port = daemon->dhcp_server_port+1; 
	}
      break;

    case 'J':            /* --dhcp-ignore */
    case LOPT_NO_NAMES:  /* --dhcp-ignore-names */
    case LOPT_BROADCAST: /* --dhcp-broadcast */
    case '3':            /* --bootp-dynamic */ 
      {
	struct dhcp_netid_list *new = opt_malloc(sizeof(struct dhcp_netid_list));
	struct dhcp_netid *list = NULL;
	if (option == 'J')
	  {
	    new->next = daemon->dhcp_ignore;
	    daemon->dhcp_ignore = new;
	  }
	else if (option == LOPT_BROADCAST)
	  {
	    new->next = daemon->force_broadcast;
	    daemon->force_broadcast = new;
	  }
	else if (option == '3')
	  {
	    new->next = daemon->bootp_dynamic;
	    daemon->bootp_dynamic = new;
	  }
	else
	  {
	    new->next = daemon->dhcp_ignore_names;
	    daemon->dhcp_ignore_names = new;
	  }
	
	while (arg) {
	  struct dhcp_netid *member = opt_malloc(sizeof(struct dhcp_netid));
	  comma = split(arg);
	  member->next = list;
	  list = member;
	  if (strstr(arg, "net:") == arg)
	    member->net = opt_string_alloc(arg+4);
	  else
	    member->net = opt_string_alloc(arg);
	  arg = comma;
	}
	
	new->list = list;
	break;
      }
#endif
      
    case 'V':  /* --alias */
      {
	char *dash, *a[3] = { NULL, NULL, NULL };
	int k = 0;
	struct doctor *new = opt_malloc(sizeof(struct doctor));
	new->next = daemon->doctors;
	daemon->doctors = new;
	new->mask.s_addr = 0xffffffff;
	new->end.s_addr = 0;

	if ((a[0] = arg))
	  for (k = 1; k < 3; k++)
	    {
	      if (!(a[k] = split(a[k-1])))
		break;
	      unhide_metas(a[k]);
	    }
	
	dash = split_chr(a[0], '-');

	if ((k < 2) || 
	    ((new->in.s_addr = inet_addr(a[0])) == (in_addr_t)-1) ||
	    ((new->out.s_addr = inet_addr(a[1])) == (in_addr_t)-1))
	  option = '?';
	
	if (k == 3)
	  new->mask.s_addr = inet_addr(a[2]);
	
	if (dash && 
	    ((new->end.s_addr = inet_addr(dash)) == (in_addr_t)-1 ||
	     !is_same_net(new->in, new->end, new->mask) ||
	     ntohl(new->in.s_addr) > ntohl(new->end.s_addr)))
	  problem = _("invalid alias range");
	
	break;
      }
      
    case LOPT_INTNAME:  /* --interface-name */
      {
	struct interface_name *new, **up;
	char *domain = NULL;

	comma = split(arg);
	
	if (!comma || !(domain = canonicalise_opt(arg)))
	  problem = _("bad interface name");
	
	new = opt_malloc(sizeof(struct interface_name));
	new->next = NULL;
	/* Add to the end of the list, so that first name
	   of an interface is used for PTR lookups. */
	for (up = &daemon->int_names; *up; up = &((*up)->next));
	*up = new;
	new->name = domain;
	new->intr = opt_string_alloc(comma);
	break;
      }
      
    case LOPT_CNAME: /* --cname */
      {
	struct cname *new;
	
	if (!(comma = split(arg)))
	  option = '?';
	else
	  {
	    char *alias = canonicalise_opt(arg);
	    char *target = canonicalise_opt(comma);
	    
	    if (!alias || !target)
	      problem = _("bad CNAME");
	    else
	      {
		for (new = daemon->cnames; new; new = new->next)
		  if (hostname_isequal(new->alias, arg))
		    problem = _("duplicate CNAME");
		new = opt_malloc(sizeof(struct cname));
		new->next = daemon->cnames;
		daemon->cnames = new;
		new->alias = alias;
		new->target = target;
	      }
	  }
	break;
      }

    case LOPT_PTR:  /* --ptr-record */
      {
	struct ptr_record *new;
	char *dom, *target = NULL;

	comma = split(arg);
	
	if (!(dom = canonicalise_opt(arg)) ||
	    (comma && !(target = canonicalise_opt(comma))))
	  problem = _("bad PTR record");
	else
	  {
	    new = opt_malloc(sizeof(struct ptr_record));
	    new->next = daemon->ptr;
	    daemon->ptr = new;
	    new->name = dom;
	    new->ptr = target;
	  }
	break;
      }

    case LOPT_NAPTR: /* --naptr-record */
      {
	char *a[7] = { NULL, NULL, NULL, NULL, NULL, NULL, NULL };
	int k = 0;
	struct naptr *new;
	int order, pref;
	char *name, *replace = NULL;

	if ((a[0] = arg))
	  for (k = 1; k < 7; k++)
	    if (!(a[k] = split(a[k-1])))
	      break;
	
	
	if (k < 6 || 
	    !(name = canonicalise_opt(a[0])) ||
	    !atoi_check16(a[1], &order) || 
	    !atoi_check16(a[2], &pref) ||
	    (k == 7 && !(replace = canonicalise_opt(a[6]))))
	  problem = _("bad NAPTR record");
	else
	  {
	    new = opt_malloc(sizeof(struct naptr));
	    new->next = daemon->naptr;
	    daemon->naptr = new;
	    new->name = name;
	    new->flags = opt_string_alloc(a[3]);
	    new->services = opt_string_alloc(a[4]);
	    new->regexp = opt_string_alloc(a[5]);
	    new->replace = replace;
	    new->order = order;
	    new->pref = pref;
	  }
	break;
      }
       
    case 'Y':  /* --txt-record */
      {
	struct txt_record *new;
	unsigned char *p, *q;
	
	if ((comma = split(arg)))
	  comma--;
	
	gen_prob = _("TXT record string too long");
	
	if ((q = (unsigned char *)comma))
	  while (1)
	    {
	      size_t len;
	      if ((p = (unsigned char *)strchr((char*)q+1, ',')))
		{
		  if ((len = p - q - 1) > 255)
		    option = '?';
		  *q = len;
		  for (q = q+1; q < p; q++)
		    *q = unhide_meta(*q);
		}
	      else
		{
		  if ((len = strlen((char *)q+1)) > 255)
		    option = '?';
		  *q = len;
		  for (q = q+1; *q; q++)
		    *q = unhide_meta(*q);
		  break;
		}
	    }
	
	new = opt_malloc(sizeof(struct txt_record));
	new->next = daemon->txt;
	daemon->txt = new;
	new->class = C_IN;
	if (comma)
	  {
	    new->len = q - ((unsigned char *)comma);
	    new->txt = opt_malloc(new->len);
	    memcpy(new->txt, comma, new->len);
	  }
	else
	  {
	    static char empty[] = "";
	    new->len = 1;
	    new->txt = empty;
	  }
	
	/* ensure arg is terminated */
	if (comma)
	  *comma = 0;

	if (!(new->name = canonicalise_opt(arg)))
	  {
	    problem = _("bad TXT record");
	    break;
	  }

	break;
      }
      
    case 'W':  /* --srv-host */
      {
	int port = 1, priority = 0, weight = 0;
	char *name, *target = NULL;
	struct mx_srv_record *new;
	
	comma = split(arg);
	
	if (!(name = canonicalise_opt(arg)))
	  problem = _("bad SRV record");
	  
	if (comma)
	  {
	    arg = comma;
	    comma = split(arg);
	    if (!(target = canonicalise_opt(arg))
)	      problem = _("bad SRV target");
		
	    if (comma)
	      {
		arg = comma;
		comma = split(arg);
		if (!atoi_check16(arg, &port))
		  problem = _("invalid port number");
		
		if (comma)
		  {
		    arg = comma;
		    comma = split(arg);
		    if (!atoi_check16(arg, &priority))
		      problem = _("invalid priority");
			
		    if (comma)
		      {
			arg = comma;
			comma = split(arg);
			if (!atoi_check16(arg, &weight))
			  problem = _("invalid weight");
		      }
		  }
	      }
	  }
	
	new = opt_malloc(sizeof(struct mx_srv_record));
	new->next = daemon->mxnames;
	daemon->mxnames = new;
	new->issrv = 1;
	new->name = name;
	new->target = target;
	new->srvport = port;
	new->priority = priority;
	new->weight = weight;
	break;
      }
      
    default:
      return _("unsupported option (check that dnsmasq was compiled with DHCP/TFTP/DBus support)");

    }

  if (problem)
    return problem;
  
  if (option == '?')
    return gen_prob;

  return NULL;
}

static void one_file(char *file, int nest, int hard_opt)	
{
  volatile int lineno = 0;
  int i, option; 
  FILE *f;
  char *p, *arg, *start, *buff = daemon->namebuff;
  static struct fileread {
    dev_t dev;
    ino_t ino;
    struct fileread *next;
  } *filesread = NULL;
  struct stat statbuf;
  
  /* ignore repeated files. */
  if (hard_opt == 0 && stat(file, &statbuf) == 0)
    {
      struct fileread *r;

      for (r = filesread; r; r = r->next)
	if (r->dev == statbuf.st_dev && r->ino == statbuf.st_ino)
	  return;

      r = safe_malloc(sizeof(struct fileread));
      r->next = filesread;
      filesread = r;
      r->dev = statbuf.st_dev;
      r->ino = statbuf.st_ino;
    }

  if (nest > 20)
    die(_("files nested too deep in %s"), file, EC_BADCONF);

  if (!(f = fopen(file, "r")))
    {   
      if (errno == ENOENT && nest == 0)
	return; /* No conffile, all done. */
      else
	{
	  char *str = _("cannot read %s: %s");
	  if (hard_opt != 0)
	    {
	      my_syslog(LOG_ERR, str, file, strerror(errno));
	      return;
	    }
	  else
	    die(str, file, EC_FILE);
	}
    } 
  
  while (fgets(buff, MAXDNAME, f))
    {
      int white;
      unsigned int lastquote;
      char *errmess;

      /* Memory allocation failure longjmps here if mem_recover == 1 */ 
      if (hard_opt)
	{
	  if (setjmp(mem_jmp))
	    continue;
	  mem_recover = 1;
	}
      
      lineno++;
      errmess = NULL;
      
      /* Implement quotes, inside quotes we allow \\ \" \n and \t 
	 metacharacters get hidden also strip comments */
      
      for (white = 1, lastquote = 0, p = buff; *p; p++)
	{
	  if (*p == '"')
	    {
	      memmove(p, p+1, strlen(p+1)+1);
	      for(; *p && *p != '"'; p++)
		{
		  if (*p == '\\' && strchr("\"tnebr\\", p[1]))
		    {
		      if (p[1] == 't')
			p[1] = '\t';
		      else if (p[1] == 'n')
			p[1] = '\n';
		      else if (p[1] == 'b')
			p[1] = '\b';
		      else if (p[1] == 'r')
			p[1] = '\r';
		      else if (p[1] == 'e') /* escape */
			p[1] = '\033';
		      memmove(p, p+1, strlen(p+1)+1);
		    }
		  *p = hide_meta(*p);
		}
	      if (*p == '"') 
		{
		  memmove(p, p+1, strlen(p+1)+1);
		  lastquote = p - buff;
		}
	      else
		{
		  errmess = _("missing \"");
		  goto oops; 
		}
	    }

	  if (white && *p == '#')
	    { 
	      *p = 0;
	      break;
	    }
	  white = isspace((int)unhide_meta(*p)); 
	}

      /* fgets gets end of line char too. */
      while (strlen(buff) > lastquote && isspace((int)unhide_meta(buff[strlen(buff)-1])))
	buff[strlen(buff)-1] = 0;

      if (*buff == 0)
	continue; 

      if (hard_opt != 0)
	arg = buff;
      else if ((p=strchr(buff, '=')))
	{
	  /* allow spaces around "=" */
	  arg = p+1;
	  for (; p >= buff && (isspace((int)*p) || *p == '='); p--)
	    *p = 0;
	}
      else
	arg = NULL;

      if (hard_opt != 0)
	option = hard_opt;
      else
	{
	  /* skip leading space */
	  for (start = buff; *start && isspace((int)*start); start++);
	  
	  for (option = 0, i = 0; opts[i].name; i++) 
	    if (strcmp(opts[i].name, start) == 0)
	      {
		option = opts[i].val;
		break;
	      }
	  
	  if (!option)
	    errmess = _("bad option");
	  else if (opts[i].has_arg == 0 && arg)
	    errmess = _("extraneous parameter");
	  else if (opts[i].has_arg == 1 && !arg)
	    errmess = _("missing parameter");
	}
	  
      if (!errmess)
	{
	  if (arg)
	    for (; isspace((int)*arg); arg++);
	  
	  errmess = one_opt(option, arg, _("error"), nest + 1);
	}
      
      if (errmess)
	{
	oops:
	  sprintf(buff, _("%s at line %d of %%s"), errmess, lineno);
	  if (hard_opt != 0)
	    my_syslog(LOG_ERR, buff, file);
	  else
	    die(buff, file, EC_BADCONF);
	}
    }

  mem_recover = 1;
  fclose(f);
}

#ifdef HAVE_DHCP
void reread_dhcp(void)
{
  if (daemon->dhcp_hosts_file)
    {
      struct dhcp_config *configs, *cp, **up;
      
      /* remove existing... */
      for (up = &daemon->dhcp_conf, configs = daemon->dhcp_conf; configs; configs = cp)
	{
	  cp = configs->next;
	  
	  if (configs->flags & CONFIG_BANK)
	    {
	      struct hwaddr_config *mac, *tmp;
	      
	      for (mac = configs->hwaddr; mac; mac = tmp)
		{
		  tmp = mac->next;
		  free(mac);
		}
	      if (configs->flags & CONFIG_CLID)
		free(configs->clid);
	      if (configs->flags & CONFIG_NETID)
		free(configs->netid.net);
	      if (configs->flags & CONFIG_NAME)
		free(configs->hostname);
	      
     
	      *up = configs->next;
	      free(configs);
	    }
	  else
	    up = &configs->next;
	}
      
      one_file(daemon->dhcp_hosts_file, 1, LOPT_BANK);  
      my_syslog(MS_DHCP | LOG_INFO, _("read %s"), daemon->dhcp_hosts_file);
    }

  if (daemon->dhcp_opts_file)
    {
      struct dhcp_opt *opts, *cp, **up;
      struct dhcp_netid *id, *next;

      for (up = &daemon->dhcp_opts, opts = daemon->dhcp_opts; opts; opts = cp)
	{
	  cp = opts->next;
	  
	  if (opts->flags & DHOPT_BANK)
	    {
	      if ((opts->flags & DHOPT_VENDOR))
		free(opts->u.vendor_class);
	      free(opts->val);
	      for (id = opts->netid; id; id = next)
		{
		  next = id->next;
		  free(id->net);
		  free(id);
		}
	      *up = opts->next;
	      free(opts);
	    }
	  else
	    up = &opts->next;
	}
      
      one_file(daemon->dhcp_opts_file, 1, LOPT_OPTS);  
      my_syslog(MS_DHCP | LOG_INFO, _("read %s"), daemon->dhcp_opts_file);
    }
}
#endif
    
void read_opts(int argc, char **argv, char *compile_opts)
{
  char *buff = opt_malloc(MAXDNAME);
  int option, nest = 0, testmode = 0;
  char *errmess, *arg, *conffile = CONFFILE;
      
  opterr = 0;

  daemon = opt_malloc(sizeof(struct daemon));
  memset(daemon, 0, sizeof(struct daemon));
  daemon->namebuff = buff;

  /* Set defaults - everything else is zero or NULL */
  daemon->cachesize = CACHESIZ;
  daemon->ftabsize = FTABSIZ;
  daemon->port = NAMESERVER_PORT;
  daemon->dhcp_client_port = DHCP_CLIENT_PORT;
  daemon->dhcp_server_port = DHCP_SERVER_PORT;
  daemon->default_resolv.is_default = 1;
  daemon->default_resolv.name = RESOLVFILE;
  daemon->resolv_files = &daemon->default_resolv;
  daemon->username = CHUSER;
  daemon->runfile =  RUNFILE;
  daemon->dhcp_max = MAXLEASES;
  daemon->tftp_max = TFTP_MAX_CONNECTIONS;
  daemon->edns_pktsz = EDNS_PKTSZ;
  daemon->log_fac = -1;
  add_txt("version.bind", "dnsmasq-" VERSION );
  add_txt("authors.bind", "Simon Kelley");
  add_txt("copyright.bind", COPYRIGHT);

  while (1) 
    {
#ifdef HAVE_GETOPT_LONG
      option = getopt_long(argc, argv, OPTSTRING, opts, NULL);
#else
      option = getopt(argc, argv, OPTSTRING);
#endif
      
      if (option == -1)
	break;
      
      /* Copy optarg so that argv doesn't get changed */
      if (optarg)
	{
	  strncpy(buff, optarg, MAXDNAME);
	  buff[MAXDNAME-1] = 0;
	  arg = buff;
	}
      else
	arg = NULL;
      
      /* command-line only stuff */
      if (option == LOPT_TEST)
	testmode = 1;
      else if (option == 'w')
	{
	  if (argc != 3 ||  strcmp(argv[2], "dhcp") != 0)
	    do_usage();
#ifdef HAVE_DHCP
	  else
	    display_opts();
#endif
	  exit(0);
	}
      else if (option == 'v')
	{
	  printf(_("Dnsmasq version %s  %s\n"), VERSION, COPYRIGHT);
	  printf(_("Compile time options %s\n\n"), compile_opts); 
	  printf(_("This software comes with ABSOLUTELY NO WARRANTY.\n"));
	  printf(_("Dnsmasq is free software, and you are welcome to redistribute it\n"));
	  printf(_("under the terms of the GNU General Public License, version 2 or 3.\n"));
          exit(0);
        }
      else if (option == 'C')
	{
	  conffile = opt_string_alloc(arg);
	  nest++;
	}
      else
	{
#ifdef HAVE_GETOPT_LONG
	  errmess = one_opt(option, arg, _("try --help"), 0);
#else 
	  errmess = one_opt(option, arg, _("try -w"), 0); 
#endif  
	  if (errmess)
	    die(_("bad command line options: %s"), errmess, EC_BADCONF);
	}
    }

  if (conffile)
    one_file(conffile, nest, 0);

  /* port might not be known when the address is parsed - fill in here */
  if (daemon->servers)
    {
      struct server *tmp;
      for (tmp = daemon->servers; tmp; tmp = tmp->next)
	if (!(tmp->flags & SERV_HAS_SOURCE))
	  {
	    if (tmp->source_addr.sa.sa_family == AF_INET)
	      tmp->source_addr.in.sin_port = htons(daemon->query_port);
#ifdef HAVE_IPV6
	    else if (tmp->source_addr.sa.sa_family == AF_INET6)
	      tmp->source_addr.in6.sin6_port = htons(daemon->query_port);
#endif 
	  } 
    }
  
  if (daemon->if_addrs)
    {  
      struct iname *tmp;
      for(tmp = daemon->if_addrs; tmp; tmp = tmp->next)
	if (tmp->addr.sa.sa_family == AF_INET)
	  tmp->addr.in.sin_port = htons(daemon->port);
#ifdef HAVE_IPV6
	else if (tmp->addr.sa.sa_family == AF_INET6)
	  tmp->addr.in6.sin6_port = htons(daemon->port);
#endif /* IPv6 */
    }
		      
  /* only one of these need be specified: the other defaults to the host-name */
  if ((daemon->options & OPT_LOCALMX) || daemon->mxnames || daemon->mxtarget)
    {
      struct mx_srv_record *mx;
      
      if (gethostname(buff, MAXDNAME) == -1)
	die(_("cannot get host-name: %s"), NULL, EC_MISC);
      
      for (mx = daemon->mxnames; mx; mx = mx->next)
	if (!mx->issrv && hostname_isequal(mx->name, buff))
	  break;
      
      if ((daemon->mxtarget || (daemon->options & OPT_LOCALMX)) && !mx)
	{
	  mx = opt_malloc(sizeof(struct mx_srv_record));
	  mx->next = daemon->mxnames;
	  mx->issrv = 0;
	  mx->target = NULL;
	  mx->name = opt_string_alloc(buff);
	  daemon->mxnames = mx;
	}
      
      if (!daemon->mxtarget)
	daemon->mxtarget = opt_string_alloc(buff);

      for (mx = daemon->mxnames; mx; mx = mx->next)
	if (!mx->issrv && !mx->target)
	  mx->target = daemon->mxtarget;
    }

  if (!(daemon->options & OPT_NO_RESOLV) &&
      daemon->resolv_files && 
      daemon->resolv_files->next && 
      (daemon->options & OPT_NO_POLL))
    die(_("only one resolv.conf file allowed in no-poll mode."), NULL, EC_BADCONF);
  
  if (daemon->options & OPT_RESOLV_DOMAIN)
    {
      char *line;
      FILE *f;

      if ((daemon->options & OPT_NO_RESOLV) ||
	  !daemon->resolv_files || 
	  (daemon->resolv_files)->next)
	die(_("must have exactly one resolv.conf to read domain from."), NULL, EC_BADCONF);
      
      if (!(f = fopen((daemon->resolv_files)->name, "r")))
	die(_("failed to read %s: %s"), (daemon->resolv_files)->name, EC_FILE);
      
      while ((line = fgets(buff, MAXDNAME, f)))
	{
	  char *token = strtok(line, " \t\n\r");
	  
	  if (!token || strcmp(token, "search") != 0)
	    continue;
	  
	  if ((token = strtok(NULL, " \t\n\r")) &&  
	      (daemon->domain_suffix = canonicalise_opt(token)))
	    break;
	}

      fclose(f);

      if (!daemon->domain_suffix)
	die(_("no search directive found in %s"), (daemon->resolv_files)->name, EC_MISC);
    }

  if (daemon->domain_suffix)
    {
       /* add domain for any srv record without one. */
      struct mx_srv_record *srv;
      
      for (srv = daemon->mxnames; srv; srv = srv->next)
	if (srv->issrv &&
	    strchr(srv->name, '.') && 
	    strchr(srv->name, '.') == strrchr(srv->name, '.'))
	  {
	    strcpy(buff, srv->name);
	    strcat(buff, ".");
	    strcat(buff, daemon->domain_suffix);
	    free(srv->name);
	    srv->name = opt_string_alloc(buff);
	  }
    }
  else if (daemon->options & OPT_DHCP_FQDN)
    die(_("there must be a default domain when --dhcp-fqdn is set"), NULL, EC_BADCONF);

  if (testmode)
    {
      fprintf(stderr, "dnsmasq: %s.\n", _("syntax check OK"));
      exit(0);
    }
}  
