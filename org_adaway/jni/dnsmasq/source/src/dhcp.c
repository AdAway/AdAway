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

#include "dnsmasq.h"

#ifdef HAVE_DHCP

struct iface_param {
  struct in_addr relay, primary;
  struct dhcp_context *current;
  int ind;
};

static int complete_context(struct in_addr local, int if_index, 
			    struct in_addr netmask, struct in_addr broadcast, void *vparam);

void dhcp_init(void)
{
  int fd = socket(PF_INET, SOCK_DGRAM, IPPROTO_UDP);
  struct sockaddr_in saddr;
  int oneopt = 1;
#if defined(IP_MTU_DISCOVER) && defined(IP_PMTUDISC_DONT)
  int mtu = IP_PMTUDISC_DONT;
#endif

  if (fd == -1)
    die (_("cannot create DHCP socket: %s"), NULL, EC_BADNET);
  
  if (!fix_fd(fd) ||
#if defined(IP_MTU_DISCOVER) && defined(IP_PMTUDISC_DONT)
      setsockopt(fd, SOL_IP, IP_MTU_DISCOVER, &mtu, sizeof(mtu)) == -1 ||
#endif
#if defined(HAVE_LINUX_NETWORK)
      setsockopt(fd, SOL_IP, IP_PKTINFO, &oneopt, sizeof(oneopt)) == -1 ||
#else
      setsockopt(fd, IPPROTO_IP, IP_RECVIF, &oneopt, sizeof(oneopt)) == -1 ||
#endif
      setsockopt(fd, SOL_SOCKET, SO_BROADCAST, &oneopt, sizeof(oneopt)) == -1)  
    die(_("failed to set options on DHCP socket: %s"), NULL, EC_BADNET);
  
  /* When bind-interfaces is set, there might be more than one dnmsasq
     instance binding port 67. That's OK if they serve different networks.
     Need to set REUSEADDR to make this posible, or REUSEPORT on *BSD. */
  if (daemon->options & OPT_NOWILD)
    {
#ifdef SO_REUSEPORT
      int rc = setsockopt(fd, SOL_SOCKET, SO_REUSEPORT, &oneopt, sizeof(oneopt));
#else
      int rc = setsockopt(fd, SOL_SOCKET, SO_REUSEADDR, &oneopt, sizeof(oneopt));
#endif
      if (rc == -1)
	die(_("failed to set SO_REUSE{ADDR|PORT} on DHCP socket: %s"), NULL, EC_BADNET);
    }
  
  memset(&saddr, 0, sizeof(saddr));
  saddr.sin_family = AF_INET;
  saddr.sin_port = htons(daemon->dhcp_server_port);
  saddr.sin_addr.s_addr = INADDR_ANY;
#ifdef HAVE_SOCKADDR_SA_LEN
  saddr.sin_len = sizeof(struct sockaddr_in);
#endif

  if (bind(fd, (struct sockaddr *)&saddr, sizeof(struct sockaddr_in)))
    die(_("failed to bind DHCP server socket: %s"), NULL, EC_BADNET);

  daemon->dhcpfd = fd;

#if defined(HAVE_BSD_NETWORK)
  /* When we're not using capabilities, we need to do this here before
     we drop root. Also, set buffer size small, to avoid wasting
     kernel buffers */
  
  if (daemon->options & OPT_NO_PING)
    daemon->dhcp_icmp_fd = -1;
  else if ((daemon->dhcp_icmp_fd = make_icmp_sock()) == -1 ||
	   setsockopt(daemon->dhcp_icmp_fd, SOL_SOCKET, SO_RCVBUF, &oneopt, sizeof(oneopt)) == -1 )
    die(_("cannot create ICMP raw socket: %s."), NULL, EC_BADNET);
  
  /* Make BPF raw send socket */
  init_bpf();
#endif
  
  check_dhcp_hosts(1);
    
  daemon->dhcp_packet.iov_len = sizeof(struct dhcp_packet); 
  daemon->dhcp_packet.iov_base = safe_malloc(daemon->dhcp_packet.iov_len);
}
  
void dhcp_packet(time_t now)
{
  struct dhcp_packet *mess;
  struct dhcp_context *context;
  struct iname *tmp;
  struct ifreq ifr;
  struct msghdr msg;
  struct sockaddr_in dest;
  struct cmsghdr *cmptr;
  struct iovec iov;
  ssize_t sz; 
  int iface_index = 0, unicast_dest = 0, is_inform = 0;
  struct in_addr iface_addr, *addrp = NULL;
  struct iface_param parm;

  union {
    struct cmsghdr align; /* this ensures alignment */
#if defined(HAVE_LINUX_NETWORK)
    char control[CMSG_SPACE(sizeof(struct in_pktinfo))];
#elif defined(HAVE_SOLARIS_NETWORK)
    char control[CMSG_SPACE(sizeof(unsigned int))];
#elif defined(HAVE_BSD_NETWORK) 
    char control[CMSG_SPACE(sizeof(struct sockaddr_dl))];
#endif
  } control_u;
  
  msg.msg_control = NULL;
  msg.msg_controllen = 0;
  msg.msg_name = NULL;
  msg.msg_namelen = 0;
  msg.msg_iov = &daemon->dhcp_packet;
  msg.msg_iovlen = 1;
  
  while (1)
    {
      msg.msg_flags = 0;
      while ((sz = recvmsg(daemon->dhcpfd, &msg, MSG_PEEK | MSG_TRUNC)) == -1 && errno == EINTR);
      
      if (sz == -1)
	return;
      
      if (!(msg.msg_flags & MSG_TRUNC))
	break;

      /* Very new Linux kernels return the actual size needed, 
	 older ones always return truncated size */
      if ((size_t)sz == daemon->dhcp_packet.iov_len)
	{
	  if (!expand_buf(&daemon->dhcp_packet, sz + 100))
	    return;
	}
      else
	{
	  expand_buf(&daemon->dhcp_packet, sz);
	  break;
	}
    }
  
  /* expand_buf may have moved buffer */
  mess = (struct dhcp_packet *)daemon->dhcp_packet.iov_base;
  msg.msg_controllen = sizeof(control_u);
  msg.msg_control = control_u.control;
  msg.msg_flags = 0;
  msg.msg_name = &dest;
  msg.msg_namelen = sizeof(dest);

  while ((sz = recvmsg(daemon->dhcpfd, &msg, 0)) == -1 && errno == EINTR);
 
  if ((msg.msg_flags & MSG_TRUNC) || sz < (ssize_t)(sizeof(*mess) - sizeof(mess->options)))
    return;
  
#if defined (HAVE_LINUX_NETWORK)
  if (msg.msg_controllen >= sizeof(struct cmsghdr))
    for (cmptr = CMSG_FIRSTHDR(&msg); cmptr; cmptr = CMSG_NXTHDR(&msg, cmptr))
      if (cmptr->cmsg_level == SOL_IP && cmptr->cmsg_type == IP_PKTINFO)
	{
	  iface_index = ((struct in_pktinfo *)CMSG_DATA(cmptr))->ipi_ifindex;
	  if (((struct in_pktinfo *)CMSG_DATA(cmptr))->ipi_addr.s_addr != INADDR_BROADCAST)
	    unicast_dest = 1;
	}

#elif defined(HAVE_BSD_NETWORK) 
  if (msg.msg_controllen >= sizeof(struct cmsghdr))
    for (cmptr = CMSG_FIRSTHDR(&msg); cmptr; cmptr = CMSG_NXTHDR(&msg, cmptr))
      if (cmptr->cmsg_level == IPPROTO_IP && cmptr->cmsg_type == IP_RECVIF)
        iface_index = ((struct sockaddr_dl *)CMSG_DATA(cmptr))->sdl_index;

  
#elif defined(HAVE_SOLARIS_NETWORK) 
  if (msg.msg_controllen >= sizeof(struct cmsghdr))
    for (cmptr = CMSG_FIRSTHDR(&msg); cmptr; cmptr = CMSG_NXTHDR(&msg, cmptr))
      if (cmptr->cmsg_level == IPPROTO_IP && cmptr->cmsg_type == IP_RECVIF)
	iface_index = *((unsigned int *)CMSG_DATA(cmptr));
	  
#endif
	
  if (!indextoname(daemon->dhcpfd, iface_index, ifr.ifr_name))
    return;

#ifdef MSG_BCAST
  /* OpenBSD tells us when a packet was broadcast */
  if (!(msg.msg_flags & MSG_BCAST))
    unicast_dest = 1;
#endif

  ifr.ifr_addr.sa_family = AF_INET;
  if (ioctl(daemon->dhcpfd, SIOCGIFADDR, &ifr) != -1 )
    {
      addrp = &iface_addr;
      iface_addr = ((struct sockaddr_in *) &ifr.ifr_addr)->sin_addr;
    }

  if (!iface_check(AF_INET, (struct all_addr *)addrp, ifr.ifr_name, &iface_index))
    return;
  
  for (tmp = daemon->dhcp_except; tmp; tmp = tmp->next)
    if (tmp->name && (strcmp(tmp->name, ifr.ifr_name) == 0))
      return;
  
  /* interface may have been changed by alias in iface_check */
  if (!addrp)
    {
      if (ioctl(daemon->dhcpfd, SIOCGIFADDR, &ifr) == -1)
	{
	  my_syslog(MS_DHCP | LOG_WARNING, _("DHCP packet received on %s which has no address"), ifr.ifr_name);
	  return;
	}
      else
	iface_addr = ((struct sockaddr_in *) &ifr.ifr_addr)->sin_addr;
    }
  
  /* unlinked contexts are marked by context->current == context */
  for (context = daemon->dhcp; context; context = context->next)
    context->current = context;
  
  parm.relay = mess->giaddr;
  parm.primary = iface_addr;
  parm.current = NULL;
  parm.ind = iface_index;

  if (!iface_enumerate(&parm, complete_context, NULL))
    return;
  lease_prune(NULL, now); /* lose any expired leases */
  iov.iov_len = dhcp_reply(parm.current, ifr.ifr_name, iface_index, (size_t)sz, 
			   now, unicast_dest, &is_inform);
  lease_update_file(now);
  lease_update_dns();
    
  if (iov.iov_len == 0)
    return;
  
  msg.msg_name = &dest;
  msg.msg_namelen = sizeof(dest);
  msg.msg_control = NULL;
  msg.msg_controllen = 0;
  msg.msg_iov = &iov;
  iov.iov_base = daemon->dhcp_packet.iov_base;
  
  /* packet buffer may have moved */
  mess = (struct dhcp_packet *)daemon->dhcp_packet.iov_base;
  
#ifdef HAVE_SOCKADDR_SA_LEN
  dest.sin_len = sizeof(struct sockaddr_in);
#endif
     
  if (mess->giaddr.s_addr)
    {
      /* Send to BOOTP relay  */
      dest.sin_port = htons(daemon->dhcp_server_port);
      dest.sin_addr = mess->giaddr; 
    }
  else if (mess->ciaddr.s_addr)
    {
      /* If the client's idea of its own address tallys with
	 the source address in the request packet, we believe the
	 source port too, and send back to that.  If we're replying 
	 to a DHCPINFORM, trust the source address always. */
      if ((!is_inform && dest.sin_addr.s_addr != mess->ciaddr.s_addr) ||
	  dest.sin_port == 0 || dest.sin_addr.s_addr == 0)
	{
	  dest.sin_port = htons(daemon->dhcp_client_port); 
	  dest.sin_addr = mess->ciaddr;
	}
    } 
#if defined(HAVE_LINUX_NETWORK)
  else if ((ntohs(mess->flags) & 0x8000) || mess->hlen == 0 ||
	   mess->hlen > sizeof(ifr.ifr_addr.sa_data) || mess->htype == 0)
    {
      /* broadcast to 255.255.255.255 (or mac address invalid) */
      struct in_pktinfo *pkt;
      msg.msg_control = control_u.control;
      msg.msg_controllen = sizeof(control_u);
      cmptr = CMSG_FIRSTHDR(&msg);
      pkt = (struct in_pktinfo *)CMSG_DATA(cmptr);
      pkt->ipi_ifindex = iface_index;
      pkt->ipi_spec_dst.s_addr = 0;
      msg.msg_controllen = cmptr->cmsg_len = CMSG_LEN(sizeof(struct in_pktinfo));
      cmptr->cmsg_level = SOL_IP;
      cmptr->cmsg_type = IP_PKTINFO;  
      dest.sin_addr.s_addr = INADDR_BROADCAST;
      dest.sin_port = htons(daemon->dhcp_client_port);
    }
  else
    {
      /* unicast to unconfigured client. Inject mac address direct into ARP cache. 
	 struct sockaddr limits size to 14 bytes. */
      struct arpreq req;
      dest.sin_addr = mess->yiaddr;
      dest.sin_port = htons(daemon->dhcp_client_port);
      *((struct sockaddr_in *)&req.arp_pa) = dest;
      req.arp_ha.sa_family = mess->htype;
      memcpy(req.arp_ha.sa_data, mess->chaddr, mess->hlen);
      strncpy(req.arp_dev, ifr.ifr_name, 16);
      req.arp_flags = ATF_COM;
      ioctl(daemon->dhcpfd, SIOCSARP, &req);
    }
#elif defined(HAVE_SOLARIS_NETWORK)
  else if ((ntohs(mess->flags) & 0x8000) || mess->hlen != ETHER_ADDR_LEN || mess->htype != ARPHRD_ETHER)
    {
      /* broadcast to 255.255.255.255 (or mac address invalid) */
      dest.sin_addr.s_addr = INADDR_BROADCAST;
      dest.sin_port = htons(daemon->dhcp_client_port);
      /* note that we don't specify the interface here: that's done by the
	 IP_BOUND_IF sockopt lower down. */
    }
  else
    {
      /* unicast to unconfigured client. Inject mac address direct into ARP cache. 
	 Note that this only works for ethernet on solaris, because we use SIOCSARP
	 and not SIOCSXARP, which would be perfect, except that it returns ENXIO 
	 mysteriously. Bah. Fall back to broadcast for other net types. */
      struct arpreq req;
      dest.sin_addr = mess->yiaddr;
      dest.sin_port = htons(daemon->dhcp_client_port);
      *((struct sockaddr_in *)&req.arp_pa) = dest;
      req.arp_ha.sa_family = AF_UNSPEC;
      memcpy(req.arp_ha.sa_data, mess->chaddr, mess->hlen);
      req.arp_flags = ATF_COM;
      ioctl(daemon->dhcpfd, SIOCSARP, &req);
    }
#elif defined(HAVE_BSD_NETWORK)
  else 
    {
      send_via_bpf(mess, iov.iov_len, iface_addr, &ifr);
      return;
    }
#endif
   
#ifdef HAVE_SOLARIS_NETWORK
  setsockopt(daemon->dhcpfd, IPPROTO_IP, IP_BOUND_IF, &iface_index, sizeof(iface_index));
#endif
  
  while(sendmsg(daemon->dhcpfd, &msg, 0) == -1 && retry_send());
}
 
/* This is a complex routine: it gets called with each (address,netmask,broadcast) triple 
   of each interface (and any relay address) and does the  following things:

   1) Discards stuff for interfaces other than the one on which a DHCP packet just arrived.
   2) Fills in any netmask and broadcast addresses which have not been explicitly configured.
   3) Fills in local (this host) and router (this host or relay) addresses.
   4) Links contexts which are valid for hosts directly connected to the arrival interface on ->current.

   Note that the current chain may be superceded later for configured hosts or those coming via gateways. */

static int complete_context(struct in_addr local, int if_index, 
			    struct in_addr netmask, struct in_addr broadcast, void *vparam)
{
  struct dhcp_context *context;
  struct iface_param *param = vparam;
  
  for (context = daemon->dhcp; context; context = context->next)
    {
      if (!(context->flags & CONTEXT_NETMASK) &&
	  (is_same_net(local, context->start, netmask) ||
	   is_same_net(local, context->end, netmask)))
      { 
	if (context->netmask.s_addr != netmask.s_addr &&
	    !(is_same_net(local, context->start, netmask) &&
	      is_same_net(local, context->end, netmask)))
	  {
	    strcpy(daemon->dhcp_buff, inet_ntoa(context->start));
	    strcpy(daemon->dhcp_buff2, inet_ntoa(context->end));
	    my_syslog(MS_DHCP | LOG_WARNING, _("DHCP range %s -- %s is not consistent with netmask %s"),
		      daemon->dhcp_buff, daemon->dhcp_buff2, inet_ntoa(netmask));
	  }	
 	context->netmask = netmask;
      }
      
      if (context->netmask.s_addr)
	{
	  if (is_same_net(local, context->start, context->netmask) &&
	      is_same_net(local, context->end, context->netmask))
	    {
	      /* link it onto the current chain if we've not seen it before */
	      if (if_index == param->ind && context->current == context)
		{
		  context->router = local;
		  context->local = local;
		  context->current = param->current;
		  param->current = context;
		}
	      
	      if (!(context->flags & CONTEXT_BRDCAST))
		{
		  if (is_same_net(broadcast, context->start, context->netmask))
		    context->broadcast = broadcast;
		  else 
		    context->broadcast.s_addr  = context->start.s_addr | ~context->netmask.s_addr;
		}
	    }	
	  else if (param->relay.s_addr && is_same_net(param->relay, context->start, context->netmask))
	    {
	      context->router = param->relay;
	      context->local = param->primary;
	      /* fill in missing broadcast addresses for relayed ranges */
	      if (!(context->flags & CONTEXT_BRDCAST))
		context->broadcast.s_addr  = context->start.s_addr | ~context->netmask.s_addr;
	    }

	}
    }

  return 1;
}
	  
struct dhcp_context *address_available(struct dhcp_context *context, 
				       struct in_addr taddr,
				       struct dhcp_netid *netids)
{
  /* Check is an address is OK for this network, check all
     possible ranges. Make sure that the address isn't in use
     by the server itself. */
  
  unsigned int start, end, addr = ntohl(taddr.s_addr);
  struct dhcp_context *tmp;

  for (tmp = context; tmp; tmp = tmp->current)
    if (taddr.s_addr == context->router.s_addr)
      return NULL;
  
  for (tmp = context; tmp; tmp = tmp->current)
    {
      start = ntohl(tmp->start.s_addr);
      end = ntohl(tmp->end.s_addr);

      if (!(tmp->flags & CONTEXT_STATIC) &&
	  addr >= start &&
	  addr <= end &&
	  match_netid(tmp->filter, netids, 1))
	return tmp;
    }

  return NULL;
}

struct dhcp_context *narrow_context(struct dhcp_context *context, 
				    struct in_addr taddr,
				    struct dhcp_netid *netids)
{
  /* We start of with a set of possible contexts, all on the current physical interface.
     These are chained on ->current.
     Here we have an address, and return the actual context correponding to that
     address. Note that none may fit, if the address came a dhcp-host and is outside
     any dhcp-range. In that case we return a static range if possible, or failing that,
     any context on the correct subnet. (If there's more than one, this is a dodgy 
     configuration: maybe there should be a warning.) */
  
  struct dhcp_context *tmp;

  if (!(tmp = address_available(context, taddr, netids)))
    {
      for (tmp = context; tmp; tmp = tmp->current)
	if (is_same_net(taddr, tmp->start, tmp->netmask) && 
	    (tmp->flags & CONTEXT_STATIC))
	  break;
      
      if (!tmp)
	for (tmp = context; tmp; tmp = tmp->current)
	  if (is_same_net(taddr, tmp->start, tmp->netmask))
	    break;
    }
  
  /* Only one context allowed now */
  if (tmp)
    tmp->current = NULL;
  
  return tmp;
}

struct dhcp_config *config_find_by_address(struct dhcp_config *configs, struct in_addr addr)
{
  struct dhcp_config *config;
  
  for (config = configs; config; config = config->next)
    if ((config->flags & CONFIG_ADDR) && config->addr.s_addr == addr.s_addr)
      return config;

  return NULL;
}

/* Is every member of check matched by a member of pool? 
   If tagnotneeded, untagged is OK */
int match_netid(struct dhcp_netid *check, struct dhcp_netid *pool, int tagnotneeded)
{
  struct dhcp_netid *tmp1;
  
  if (!check && !tagnotneeded)
    return 0;

  for (; check; check = check->next)
    {
      if (check->net[0] != '#')
	{
	  for (tmp1 = pool; tmp1; tmp1 = tmp1->next)
	    if (strcmp(check->net, tmp1->net) == 0)
	      break;
	  if (!tmp1)
	    return 0;
	}
      else
	for (tmp1 = pool; tmp1; tmp1 = tmp1->next)
	  if (strcmp((check->net)+1, tmp1->net) == 0)
	    return 0;
    }
  return 1;
}

int address_allocate(struct dhcp_context *context,
		     struct in_addr *addrp, unsigned char *hwaddr, int hw_len, 
		     struct dhcp_netid *netids, time_t now)   
{
  /* Find a free address: exclude anything in use and anything allocated to
     a particular hwaddr/clientid/hostname in our configuration.
     Try to return from contexts which match netids first. */

  struct in_addr start, addr;
  struct dhcp_context *c, *d;
  int i, pass;
  unsigned int j; 

  /* hash hwaddr */
  for (j = 0, i = 0; i < hw_len; i++)
    j += hwaddr[i] + (hwaddr[i] << 8) + (hwaddr[i] << 16);
  
  for (pass = 0; pass <= 1; pass++)
    for (c = context; c; c = c->current)
      if (c->flags & CONTEXT_STATIC)
	continue;
      else if (!match_netid(c->filter, netids, pass))
	continue;
      else
	{
	  /* pick a seed based on hwaddr then iterate until we find a free address. */
	  start.s_addr = addr.s_addr = 
	    htonl(ntohl(c->start.s_addr) + 
		  ((j + c->addr_epoch) % (1 + ntohl(c->end.s_addr) - ntohl(c->start.s_addr))));
	  
	  do {
	    /* eliminate addresses in use by the server. */
	    for (d = context; d; d = d->current)
	      if (addr.s_addr == d->router.s_addr)
		break;

	    /* Addresses which end in .255 and .0 are broken in Windows even when using 
	       supernetting. ie dhcp-range=192.168.0.1,192.168.1.254,255,255,254.0
	       then 192.168.0.255 is a valid IP address, but not for Windows as it's
	       in the class C range. See  KB281579. We therefore don't allocate these 
	       addresses to avoid hard-to-diagnose problems. Thanks Bill. */	    
	    if (!d &&
		!lease_find_by_addr(addr) && 
		!config_find_by_address(daemon->dhcp_conf, addr) &&
		(!IN_CLASSC(ntohl(addr.s_addr)) || 
		 ((ntohl(addr.s_addr) & 0xff) != 0xff && ((ntohl(addr.s_addr) & 0xff) != 0x0))))
	      {
		struct ping_result *r, *victim = NULL;
		int count, max = (int)(0.6 * (((float)PING_CACHE_TIME)/
					      ((float)PING_WAIT)));
		
		*addrp = addr;

		if (daemon->options & OPT_NO_PING)
		  return 1;
		
		/* check if we failed to ping addr sometime in the last
		   PING_CACHE_TIME seconds. If so, assume the same situation still exists.
		   This avoids problems when a stupid client bangs
		   on us repeatedly. As a final check, if we did more
		   than 60% of the possible ping checks in the last 
		   PING_CACHE_TIME, we are in high-load mode, so don't do any more. */
		for (count = 0, r = daemon->ping_results; r; r = r->next)
		  if (difftime(now, r->time) >  (float)PING_CACHE_TIME)
		    victim = r; /* old record */
		  else if (++count == max || r->addr.s_addr == addr.s_addr)
		    return 1;
		    
		if (icmp_ping(addr))
		  /* address in use: perturb address selection so that we are
		     less likely to try this address again. */
		  c->addr_epoch++;
		else
		  {
		    /* at this point victim may hold an expired record */
		    if (!victim)
		      {
			if ((victim = whine_malloc(sizeof(struct ping_result))))
			  {
			    victim->next = daemon->ping_results;
			    daemon->ping_results = victim;
			  }
		      }
		    
		    /* record that this address is OK for 30s 
		       without more ping checks */
		    if (victim)
		      {
			victim->addr = addr;
			victim->time = now;
		      }
		    return 1;
		  }
	      }

	    addr.s_addr = htonl(ntohl(addr.s_addr) + 1);
	    
	    if (addr.s_addr == htonl(ntohl(c->end.s_addr) + 1))
	      addr = c->start;
	    
	  } while (addr.s_addr != start.s_addr);
	}
  return 0;
}

static int is_addr_in_context(struct dhcp_context *context, struct dhcp_config *config)
{
  if (!context) /* called via find_config() from lease_update_from_configs() */
    return 1; 
  if (!(config->flags & CONFIG_ADDR))
    return 1;
  for (; context; context = context->current)
    if (is_same_net(config->addr, context->start, context->netmask))
      return 1;
  
  return 0;
}

int config_has_mac(struct dhcp_config *config, unsigned char *hwaddr, int len, int type)
{
  struct hwaddr_config *conf_addr;
  
  for (conf_addr = config->hwaddr; conf_addr; conf_addr = conf_addr->next)
    if (conf_addr->wildcard_mask == 0 &&
	conf_addr->hwaddr_len == len &&
	(conf_addr->hwaddr_type == type || conf_addr->hwaddr_type == 0) &&
	memcmp(conf_addr->hwaddr, hwaddr, len) == 0)
      return 1;
  
  return 0;
}

struct dhcp_config *find_config(struct dhcp_config *configs,
				struct dhcp_context *context,
				unsigned char *clid, int clid_len,
				unsigned char *hwaddr, int hw_len, 
				int hw_type, char *hostname)
{
  int count, new;
  struct dhcp_config *config, *candidate; 
  struct hwaddr_config *conf_addr;

  if (clid)
    for (config = configs; config; config = config->next)
      if (config->flags & CONFIG_CLID)
	{
	  if (config->clid_len == clid_len && 
	      memcmp(config->clid, clid, clid_len) == 0 &&
	      is_addr_in_context(context, config))
	    return config;
	  
	  /* dhcpcd prefixes ASCII client IDs by zero which is wrong, but we try and
	     cope with that here */
	  if (*clid == 0 && config->clid_len == clid_len-1  &&
	      memcmp(config->clid, clid+1, clid_len-1) == 0 &&
	      is_addr_in_context(context, config))
	    return config;
	}
  

  for (config = configs; config; config = config->next)
    if (config_has_mac(config, hwaddr, hw_len, hw_type) &&
	is_addr_in_context(context, config))
      return config;
  
  if (hostname && context)
    for (config = configs; config; config = config->next)
      if ((config->flags & CONFIG_NAME) && 
	  hostname_isequal(config->hostname, hostname) &&
	  is_addr_in_context(context, config))
	return config;

  /* use match with fewest wildcast octets */
  for (candidate = NULL, count = 0, config = configs; config; config = config->next)
    if (is_addr_in_context(context, config))
      for (conf_addr = config->hwaddr; conf_addr; conf_addr = conf_addr->next)
	if (conf_addr->wildcard_mask != 0 &&
	    conf_addr->hwaddr_len == hw_len &&	
	    (conf_addr->hwaddr_type == hw_type || conf_addr->hwaddr_type == 0) &&
	    (new = memcmp_masked(conf_addr->hwaddr, hwaddr, hw_len, conf_addr->wildcard_mask)) > count)
	  {
	    count = new;
	    candidate = config;
	  }

  return candidate;
}

void dhcp_read_ethers(void)
{
  FILE *f = fopen(ETHERSFILE, "r");
  unsigned int flags;
  char *buff = daemon->namebuff;
  char *ip, *cp;
  struct in_addr addr;
  unsigned char hwaddr[ETHER_ADDR_LEN];
  struct dhcp_config **up, *tmp;
  struct dhcp_config *config;
  int count = 0, lineno = 0;

  addr.s_addr = 0; /* eliminate warning */
  
  if (!f)
    {
      my_syslog(MS_DHCP | LOG_ERR, _("failed to read %s: %s"), ETHERSFILE, strerror(errno));
      return;
    }

  /* This can be called again on SIGHUP, so remove entries created last time round. */
  for (up = &daemon->dhcp_conf, config = daemon->dhcp_conf; config; config = tmp)
    {
      tmp = config->next;
      if (config->flags & CONFIG_FROM_ETHERS)
	{
	  *up = tmp;
	  /* cannot have a clid */
	  if (config->flags & CONFIG_NAME)
	    free(config->hostname);
	  free(config->hwaddr);
	  free(config);
	}
      else
	up = &config->next;
    }

  while (fgets(buff, MAXDNAME, f))
    {
      char *host = NULL;
      
      lineno++;
      
      while (strlen(buff) > 0 && isspace((int)buff[strlen(buff)-1]))
	buff[strlen(buff)-1] = 0;
      
      if ((*buff == '#') || (*buff == '+') || (*buff == 0))
	continue;
      
      for (ip = buff; *ip && !isspace((int)*ip); ip++);
      for(; *ip && isspace((int)*ip); ip++)
	*ip = 0;
      if (!*ip || parse_hex(buff, hwaddr, ETHER_ADDR_LEN, NULL, NULL) != ETHER_ADDR_LEN)
	{
	  my_syslog(MS_DHCP | LOG_ERR, _("bad line at %s line %d"), ETHERSFILE, lineno); 
	  continue;
	}
      
      /* check for name or dotted-quad */
      for (cp = ip; *cp; cp++)
	if (!(*cp == '.' || (*cp >='0' && *cp <= '9')))
	  break;
      
      if (!*cp)
	{
	  if ((addr.s_addr = inet_addr(ip)) == (in_addr_t)-1)
	    {
	      my_syslog(MS_DHCP | LOG_ERR, _("bad address at %s line %d"), ETHERSFILE, lineno); 
	      continue;
	    }

	  flags = CONFIG_ADDR;
	  
	  for (config = daemon->dhcp_conf; config; config = config->next)
	    if ((config->flags & CONFIG_ADDR) && config->addr.s_addr == addr.s_addr)
	      break;
	}
      else 
	{
	  int nomem;
	  if (!(host = canonicalise(ip, &nomem)) || !legal_hostname(host))
	    {
	      if (!nomem)
		my_syslog(MS_DHCP | LOG_ERR, _("bad name at %s line %d"), ETHERSFILE, lineno); 
	      free(host);
	      continue;
	    }
	      
	  flags = CONFIG_NAME;

	  for (config = daemon->dhcp_conf; config; config = config->next)
	    if ((config->flags & CONFIG_NAME) && hostname_isequal(config->hostname, host))
	      break;
	}

      if (config && (config->flags & CONFIG_FROM_ETHERS))
	{
	  my_syslog(MS_DHCP | LOG_ERR, _("ignoring %s line %d, duplicate name or IP address"), ETHERSFILE, lineno); 
	  continue;
	}
	
      if (!config)
	{ 
	  for (config = daemon->dhcp_conf; config; config = config->next)
	    {
	      struct hwaddr_config *conf_addr = config->hwaddr;
	      if (conf_addr && 
		  conf_addr->next == NULL && 
		  conf_addr->wildcard_mask == 0 &&
		  conf_addr->hwaddr_len == ETHER_ADDR_LEN &&
		  (conf_addr->hwaddr_type == ARPHRD_ETHER || conf_addr->hwaddr_type == 0) &&
		  memcmp(conf_addr->hwaddr, hwaddr, ETHER_ADDR_LEN) == 0)
		break;
	    }
	  
	  if (!config)
	    {
	      if (!(config = whine_malloc(sizeof(struct dhcp_config))))
		continue;
	      config->flags = CONFIG_FROM_ETHERS;
	      config->hwaddr = NULL;
	      config->domain = NULL;
	      config->next = daemon->dhcp_conf;
	      daemon->dhcp_conf = config;
	    }
	  
	  config->flags |= flags;
	  
	  if (flags & CONFIG_NAME)
	    {
	      config->hostname = host;
	      host = NULL;
	    }
	  
	  if (flags & CONFIG_ADDR)
	    config->addr = addr;
	}
      
      config->flags |= CONFIG_NOCLID;
      if (!config->hwaddr)
	config->hwaddr = whine_malloc(sizeof(struct hwaddr_config));
      if (config->hwaddr)
	{
	  memcpy(config->hwaddr->hwaddr, hwaddr, ETHER_ADDR_LEN);
	  config->hwaddr->hwaddr_len = ETHER_ADDR_LEN;
	  config->hwaddr->hwaddr_type = ARPHRD_ETHER;
	  config->hwaddr->wildcard_mask = 0;
	  config->hwaddr->next = NULL;
	}
      count++;
      
      free(host);

    }
  
  fclose(f);

  my_syslog(MS_DHCP | LOG_INFO, _("read %s - %d addresses"), ETHERSFILE, count);
}

void check_dhcp_hosts(int fatal)
{
  /* If the same IP appears in more than one host config, then DISCOVER
     for one of the hosts will get the address, but REQUEST will be NAKed,
     since the address is reserved by the other one -> protocol loop. 
     Also check that FQDNs match the domain we are using. */
  
  struct dhcp_config *configs, *cp;
 
  for (configs = daemon->dhcp_conf; configs; configs = configs->next)
    {
      char *domain;

      if ((configs->flags & DHOPT_BANK) || fatal)
       {
	 for (cp = configs->next; cp; cp = cp->next)
	   if ((configs->flags & cp->flags & CONFIG_ADDR) && configs->addr.s_addr == cp->addr.s_addr)
	     {
	       if (fatal)
		 die(_("duplicate IP address %s in dhcp-config directive."), 
		     inet_ntoa(cp->addr), EC_BADCONF);
	       else
		 my_syslog(MS_DHCP | LOG_ERR, _("duplicate IP address %s in %s."), 
			   inet_ntoa(cp->addr), daemon->dhcp_hosts_file);
	       configs->flags &= ~CONFIG_ADDR;
	     }
	 
	 /* split off domain part */
	 if ((configs->flags & CONFIG_NAME) && (domain = strip_hostname(configs->hostname)))
	   configs->domain = domain;
       }
    }
}

void dhcp_update_configs(struct dhcp_config *configs)
{
  /* Some people like to keep all static IP addresses in /etc/hosts.
     This goes through /etc/hosts and sets static addresses for any DHCP config
     records which don't have an address and whose name matches. 
     We take care to maintain the invariant that any IP address can appear
     in at most one dhcp-host. Since /etc/hosts can be re-read by SIGHUP, 
     restore the status-quo ante first. */
  
  struct dhcp_config *config;
  struct crec *crec;

  for (config = configs; config; config = config->next)
    if (config->flags & CONFIG_ADDR_HOSTS)
      config->flags &= ~(CONFIG_ADDR | CONFIG_ADDR_HOSTS);
  
  
  if (daemon->port != 0)
    for (config = configs; config; config = config->next)
      if (!(config->flags & CONFIG_ADDR) &&
	  (config->flags & CONFIG_NAME) && 
	  (crec = cache_find_by_name(NULL, config->hostname, 0, F_IPV4)) &&
	  (crec->flags & F_HOSTS))
	{
	  if (cache_find_by_name(crec, config->hostname, 0, F_IPV4))
	    {
	      /* use primary (first) address */
	      while (crec && !(crec->flags & F_REVERSE))
		crec = cache_find_by_name(crec, config->hostname, 0, F_IPV4);
	      if (!crec)
		continue; /* should be never */
	      my_syslog(MS_DHCP | LOG_WARNING, _("%s has more than one address in hostsfile, using %s for DHCP"), 
			config->hostname, inet_ntoa(crec->addr.addr.addr.addr4));
	    }

	  if (config_find_by_address(configs, crec->addr.addr.addr.addr4))
	    my_syslog(MS_DHCP | LOG_WARNING, _("duplicate IP address %s (%s) in dhcp-config directive"), 
		      inet_ntoa(crec->addr.addr.addr.addr4), config->hostname);
	  else 
	    {
	      config->addr = crec->addr.addr.addr.addr4;
	      config->flags |= CONFIG_ADDR | CONFIG_ADDR_HOSTS;
	    }
	}
}

/* If we've not found a hostname any other way, try and see if there's one in /etc/hosts
   for this address. If it has a domain part, that must match the set domain and
   it gets stripped. The set of legal domain names is bigger than the set of legal hostnames
   so check here that the domain name is legal as a hostname. */
char *host_from_dns(struct in_addr addr)
{
  struct crec *lookup;
  char *hostname = NULL;
  char *d1, *d2;

  if (daemon->port == 0)
    return NULL; /* DNS disabled. */
  
  lookup = cache_find_by_addr(NULL, (struct all_addr *)&addr, 0, F_IPV4);
  if (lookup && (lookup->flags & F_HOSTS))
    {
      hostname = daemon->dhcp_buff;
      strncpy(hostname, cache_get_name(lookup), 256);
      hostname[255] = 0;
      d1 = strip_hostname(hostname);
      d2 = get_domain(addr);
      if (!legal_hostname(hostname) || (d1 && (!d2 || !hostname_isequal(d1, d2))))
	hostname = NULL;
    }
  
  return hostname;
}

/* return domain or NULL if none. */
char *strip_hostname(char *hostname)
{
  char *dot = strchr(hostname, '.');
 
  if (!dot)
    return NULL;
  
  *dot = 0; /* truncate */
  if (strlen(dot+1) != 0)
    return dot+1;
  
  return NULL;
}

#endif

