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

#ifdef HAVE_LINUX_NETWORK

int indextoname(int fd, int index, char *name)
{
  struct ifreq ifr;
  
  if (index == 0)
    return 0;

  ifr.ifr_ifindex = index;
  if (ioctl(fd, SIOCGIFNAME, &ifr) == -1)
    return 0;

  strncpy(name, ifr.ifr_name, IF_NAMESIZE);

  return 1;
}

#else

int indextoname(int fd, int index, char *name)
{ 
  if (index == 0 || !if_indextoname(index, name))
    return 0;

  return 1;
}

#endif

int iface_check(int family, struct all_addr *addr, char *name, int *indexp)
{
  struct iname *tmp;
  int ret = 1;

  /* Note: have to check all and not bail out early, so that we set the
     "used" flags. */

  if (indexp)
    {
      /* One form of bridging on BSD has the property that packets
	 can be recieved on bridge interfaces which do not have an IP address.
	 We allow these to be treated as aliases of another interface which does have
	 an IP address with --dhcp-bridge=interface,alias,alias */
      struct dhcp_bridge *bridge, *alias;
      for (bridge = daemon->bridges; bridge; bridge = bridge->next)
	{
	  for (alias = bridge->alias; alias; alias = alias->next)
	    if (strncmp(name, alias->iface, IF_NAMESIZE) == 0)
	      {
		int newindex;
		
		if (!(newindex = if_nametoindex(bridge->iface)))
		  {
		    my_syslog(LOG_WARNING, _("unknown interface %s in bridge-interface"), name);
		    return 0;
		  }
		else 
		  {
		    *indexp = newindex;
		    strncpy(name,  bridge->iface, IF_NAMESIZE);
		    break;
		  }
	      }
	  if (alias)
	    break;
	}
    }
  
  if (daemon->if_names || (addr && daemon->if_addrs))
    {
      ret = 0;

      for (tmp = daemon->if_names; tmp; tmp = tmp->next)
	if (tmp->name && (strcmp(tmp->name, name) == 0))
	  ret = tmp->used = 1;
	        
      for (tmp = daemon->if_addrs; tmp; tmp = tmp->next)
	if (addr && tmp->addr.sa.sa_family == family)
	  {
	    if (family == AF_INET &&
		tmp->addr.in.sin_addr.s_addr == addr->addr.addr4.s_addr)
	      ret = tmp->used = 1;
#ifdef HAVE_IPV6
	    else if (family == AF_INET6 &&
		     IN6_ARE_ADDR_EQUAL(&tmp->addr.in6.sin6_addr, 
					&addr->addr.addr6))
	      ret = tmp->used = 1;
#endif
	  }          
    }
  
  for (tmp = daemon->if_except; tmp; tmp = tmp->next)
    if (tmp->name && (strcmp(tmp->name, name) == 0))
      ret = 0;
  
  return ret; 
}
      
static int iface_allowed(struct irec **irecp, int if_index, 
			 union mysockaddr *addr, struct in_addr netmask) 
{
  struct irec *iface;
  int fd, mtu = 0, loopback;
  struct ifreq ifr;
  int dhcp_ok = 1;
  struct iname *tmp;
  
  /* check whether the interface IP has been added already 
     we call this routine multiple times. */
  for (iface = *irecp; iface; iface = iface->next) 
    if (sockaddr_isequal(&iface->addr, addr))
      return 1;
  
  if ((fd = socket(PF_INET, SOCK_DGRAM, 0)) == -1 ||
      !indextoname(fd, if_index, ifr.ifr_name) ||
      ioctl(fd, SIOCGIFFLAGS, &ifr) == -1)
    {
      if (fd != -1)
	{
	  int errsave = errno;
	  close(fd);
	  errno = errsave;
	}
      return 0;
    }
   
  loopback = ifr.ifr_flags & IFF_LOOPBACK;

  if (ioctl(fd, SIOCGIFMTU, &ifr) != -1)
    mtu = ifr.ifr_mtu;
  
  close(fd);
  
  /* If we are restricting the set of interfaces to use, make
     sure that loopback interfaces are in that set. */
  if (daemon->if_names && loopback)
    {
      struct iname *lo;
      for (lo = daemon->if_names; lo; lo = lo->next)
	if (lo->name && strcmp(lo->name, ifr.ifr_name) == 0)
	  {
	    lo->isloop = 1;
	    break;
	  }
      
      if (!lo && 
	  (lo = whine_malloc(sizeof(struct iname))) &&
	  (lo->name = whine_malloc(strlen(ifr.ifr_name)+1)))
	{
	  strcpy(lo->name, ifr.ifr_name);
	  lo->isloop = lo->used = 1;
	  lo->next = daemon->if_names;
	  daemon->if_names = lo;
	}
    }
  
  if (addr->sa.sa_family == AF_INET &&
      !iface_check(AF_INET, (struct all_addr *)&addr->in.sin_addr, ifr.ifr_name, NULL))
    return 1;
  
  for (tmp = daemon->dhcp_except; tmp; tmp = tmp->next)
    if (tmp->name && (strcmp(tmp->name, ifr.ifr_name) == 0))
      dhcp_ok = 0;
  
#ifdef HAVE_IPV6
  if (addr->sa.sa_family == AF_INET6 &&
      !iface_check(AF_INET6, (struct all_addr *)&addr->in6.sin6_addr, ifr.ifr_name, NULL))
    return 1;
#endif

  /* add to list */
  if ((iface = whine_malloc(sizeof(struct irec))))
    {
      iface->addr = *addr;
      iface->netmask = netmask;
      iface->dhcp_ok = dhcp_ok;
      iface->mtu = mtu;
      iface->next = *irecp;
      *irecp = iface;
      return 1;
    }
  
  errno = ENOMEM; 
  return 0;
}

#ifdef HAVE_IPV6
static int iface_allowed_v6(struct in6_addr *local, 
			    int scope, int if_index, void *vparam)
{
  union mysockaddr addr;
  struct in_addr netmask; /* dummy */
  
  netmask.s_addr = 0;
  
  memset(&addr, 0, sizeof(addr));
#ifdef HAVE_SOCKADDR_SA_LEN
  addr.in6.sin6_len = sizeof(addr.in6);
#endif
  addr.in6.sin6_family = AF_INET6;
  addr.in6.sin6_addr = *local;
  addr.in6.sin6_port = htons(daemon->port);
  addr.in6.sin6_scope_id = scope;
  
  return iface_allowed((struct irec **)vparam, if_index, &addr, netmask);
}
#endif

static int iface_allowed_v4(struct in_addr local, int if_index, 
			    struct in_addr netmask, struct in_addr broadcast, void *vparam)
{
  union mysockaddr addr;

  memset(&addr, 0, sizeof(addr));
#ifdef HAVE_SOCKADDR_SA_LEN
  addr.in.sin_len = sizeof(addr.in);
#endif
  addr.in.sin_family = AF_INET;
  addr.in.sin_addr = broadcast; /* warning */
  addr.in.sin_addr = local;
  addr.in.sin_port = htons(daemon->port);

  return iface_allowed((struct irec **)vparam, if_index, &addr, netmask);
}
   
int enumerate_interfaces(void)
{
#ifdef HAVE_IPV6
  return iface_enumerate(&daemon->interfaces, iface_allowed_v4, iface_allowed_v6);
#else
  return iface_enumerate(&daemon->interfaces, iface_allowed_v4, NULL);
#endif
}

/* set NONBLOCK bit on fd: See Stevens 16.6 */
int fix_fd(int fd)
{
  int flags;

  if ((flags = fcntl(fd, F_GETFL)) == -1 ||
      fcntl(fd, F_SETFL, flags | O_NONBLOCK) == -1)
    return 0;
  
  return 1;
}

#if defined(HAVE_IPV6)
static int create_ipv6_listener(struct listener **link, int port)
{
  union mysockaddr addr;
  int tcpfd, fd;
  struct listener *l;
  int opt = 1;

  memset(&addr, 0, sizeof(addr));
  addr.in6.sin6_family = AF_INET6;
  addr.in6.sin6_addr = in6addr_any;
  addr.in6.sin6_port = htons(port);
#ifdef HAVE_SOCKADDR_SA_LEN
  addr.in6.sin6_len = sizeof(addr.in6);
#endif

  /* No error of the kernel doesn't support IPv6 */
  if ((fd = socket(AF_INET6, SOCK_DGRAM, 0)) == -1)
    return (errno == EPROTONOSUPPORT ||
	    errno == EAFNOSUPPORT ||
	    errno == EINVAL);
  
  if ((tcpfd = socket(AF_INET6, SOCK_STREAM, 0)) == -1)
    return 0;
      
  if (setsockopt(fd, SOL_SOCKET, SO_REUSEADDR, &opt, sizeof(opt)) == -1 ||
      setsockopt(tcpfd, SOL_SOCKET, SO_REUSEADDR, &opt, sizeof(opt)) == -1 ||
      setsockopt(fd, IPV6_LEVEL, IPV6_V6ONLY, &opt, sizeof(opt)) == -1 ||
      setsockopt(tcpfd, IPV6_LEVEL, IPV6_V6ONLY, &opt, sizeof(opt)) == -1 ||
      !fix_fd(fd) ||
      !fix_fd(tcpfd) ||
#ifdef IPV6_RECVPKTINFO
      setsockopt(fd, IPV6_LEVEL, IPV6_RECVPKTINFO, &opt, sizeof(opt)) == -1 ||
#else
      setsockopt(fd, IPV6_LEVEL, IPV6_PKTINFO, &opt, sizeof(opt)) == -1 ||
#endif
      bind(tcpfd, (struct sockaddr *)&addr, sa_len(&addr)) == -1 ||
      listen(tcpfd, 5) == -1 ||
      bind(fd, (struct sockaddr *)&addr, sa_len(&addr)) == -1) 
    return 0;
      
  l = safe_malloc(sizeof(struct listener));
  l->fd = fd;
  l->tcpfd = tcpfd;
  l->tftpfd = -1;
  l->family = AF_INET6;
  l->next = NULL;
  *link = l;
  
  return 1;
}
#endif

struct listener *create_wildcard_listeners(void)
{
  union mysockaddr addr;
  int opt = 1;
  struct listener *l, *l6 = NULL;
  int tcpfd = -1, fd = -1, tftpfd = -1;

  memset(&addr, 0, sizeof(addr));
  addr.in.sin_family = AF_INET;
  addr.in.sin_addr.s_addr = INADDR_ANY;
  addr.in.sin_port = htons(daemon->port);
#ifdef HAVE_SOCKADDR_SA_LEN
  addr.in.sin_len = sizeof(struct sockaddr_in);
#endif

  if (daemon->port != 0)
    {
      
      if ((fd = socket(AF_INET, SOCK_DGRAM, 0)) == -1 ||
	  (tcpfd = socket(AF_INET, SOCK_STREAM, 0)) == -1)
	return NULL;
      
      if (setsockopt(tcpfd, SOL_SOCKET, SO_REUSEADDR, &opt, sizeof(opt)) == -1 ||
	  bind(tcpfd, (struct sockaddr *)&addr, sa_len(&addr)) == -1 ||
	  listen(tcpfd, 5) == -1 ||
	  !fix_fd(tcpfd) ||
#ifdef HAVE_IPV6
	  !create_ipv6_listener(&l6, daemon->port) ||
#endif
	  setsockopt(fd, SOL_SOCKET, SO_REUSEADDR, &opt, sizeof(opt)) == -1 ||
	  !fix_fd(fd) ||
#if defined(HAVE_LINUX_NETWORK) 
	  setsockopt(fd, SOL_IP, IP_PKTINFO, &opt, sizeof(opt)) == -1 ||
#elif defined(IP_RECVDSTADDR) && defined(IP_RECVIF)
	  setsockopt(fd, IPPROTO_IP, IP_RECVDSTADDR, &opt, sizeof(opt)) == -1 ||
	  setsockopt(fd, IPPROTO_IP, IP_RECVIF, &opt, sizeof(opt)) == -1 ||
#endif 
	  bind(fd, (struct sockaddr *)&addr, sa_len(&addr)) == -1)
	return NULL;
    }
  
#ifdef HAVE_TFTP
  if (daemon->options & OPT_TFTP)
    {
      addr.in.sin_port = htons(TFTP_PORT);
      if ((tftpfd = socket(AF_INET, SOCK_DGRAM, 0)) == -1)
	return NULL;
      
      if (!fix_fd(tftpfd) ||
#if defined(HAVE_LINUX_NETWORK) 
	  setsockopt(tftpfd, SOL_IP, IP_PKTINFO, &opt, sizeof(opt)) == -1 ||
#elif defined(IP_RECVDSTADDR) && defined(IP_RECVIF)
	  setsockopt(tftpfd, IPPROTO_IP, IP_RECVDSTADDR, &opt, sizeof(opt)) == -1 ||
	  setsockopt(tftpfd, IPPROTO_IP, IP_RECVIF, &opt, sizeof(opt)) == -1 ||
#endif 
	  bind(tftpfd, (struct sockaddr *)&addr, sa_len(&addr)) == -1)
	return NULL;
    }
#endif
  
  l = safe_malloc(sizeof(struct listener));
  l->family = AF_INET;
  l->fd = fd;
  l->tcpfd = tcpfd;
  l->tftpfd = tftpfd;
  l->next = l6;

  return l;
}

struct listener *create_bound_listeners(void)
{
  struct listener *listeners = NULL;
  struct irec *iface;
  int rc, opt = 1;
#ifdef HAVE_IPV6
  static int dad_count = 0;
#endif

  for (iface = daemon->interfaces; iface; iface = iface->next)
    {
      struct listener *new = safe_malloc(sizeof(struct listener));
      new->family = iface->addr.sa.sa_family;
      new->iface = iface;
      new->next = listeners;
      new->tftpfd = -1;
      new->tcpfd = -1;
      new->fd = -1;
      listeners = new;

      if (daemon->port != 0)
	{
	  if ((new->tcpfd = socket(iface->addr.sa.sa_family, SOCK_STREAM, 0)) == -1 ||
	      (new->fd = socket(iface->addr.sa.sa_family, SOCK_DGRAM, 0)) == -1 ||
	      setsockopt(new->fd, SOL_SOCKET, SO_REUSEADDR, &opt, sizeof(opt)) == -1 ||
	      setsockopt(new->tcpfd, SOL_SOCKET, SO_REUSEADDR, &opt, sizeof(opt)) == -1 ||
	      !fix_fd(new->tcpfd) ||
	      !fix_fd(new->fd))
	    die(_("failed to create listening socket: %s"), NULL, EC_BADNET);
	  
#ifdef HAVE_IPV6
	  if (iface->addr.sa.sa_family == AF_INET6)
	    {
	      if (setsockopt(new->fd, IPV6_LEVEL, IPV6_V6ONLY, &opt, sizeof(opt)) == -1 ||
		  setsockopt(new->tcpfd, IPV6_LEVEL, IPV6_V6ONLY, &opt, sizeof(opt)) == -1)
		die(_("failed to set IPV6 options on listening socket: %s"), NULL, EC_BADNET);
	    }
#endif

	  while(1)
	    {
	      if ((rc = bind(new->fd, &iface->addr.sa, sa_len(&iface->addr))) != -1)
		break;
	      
#ifdef HAVE_IPV6
	      /* An interface may have an IPv6 address which is still undergoing DAD. 
		 If so, the bind will fail until the DAD completes, so we try over 20 seconds
		 before failing. */
	      if (iface->addr.sa.sa_family == AF_INET6 && (errno == ENODEV || errno == EADDRNOTAVAIL) && 
		  dad_count++ < DAD_WAIT)
		{
		  sleep(1);
		  continue;
		}
#endif
	      break;
	    }
	  
	  if (rc == -1 || bind(new->tcpfd, &iface->addr.sa, sa_len(&iface->addr)) == -1)
	    {
	      prettyprint_addr(&iface->addr, daemon->namebuff);
	      die(_("failed to bind listening socket for %s: %s"), 
		  daemon->namebuff, EC_BADNET);
	    }
	    
	  if (listen(new->tcpfd, 5) == -1)
	    die(_("failed to listen on socket: %s"), NULL, EC_BADNET);
	}

#ifdef HAVE_TFTP
      if ((daemon->options & OPT_TFTP) && iface->addr.sa.sa_family == AF_INET && iface->dhcp_ok)
	{
	  short save = iface->addr.in.sin_port;
	  iface->addr.in.sin_port = htons(TFTP_PORT);
	  if ((new->tftpfd = socket(AF_INET, SOCK_DGRAM, 0)) == -1 ||
	      setsockopt(new->tftpfd, SOL_SOCKET, SO_REUSEADDR, &opt, sizeof(opt)) == -1 ||
	      !fix_fd(new->tftpfd) ||
	      bind(new->tftpfd, &iface->addr.sa, sa_len(&iface->addr)) == -1)
	    die(_("failed to create TFTP socket: %s"), NULL, EC_BADNET);
	  iface->addr.in.sin_port = save;
	}
#endif

    }

  return listeners;
}


/* return a UDP socket bound to a random port, have to cope with straying into
   occupied port nos and reserved ones. */
int random_sock(int family)
{
  int fd;

  if ((fd = socket(family, SOCK_DGRAM, 0)) != -1)
    {
      union mysockaddr addr;
      unsigned int ports_avail = 65536u - (unsigned short)daemon->min_port;
      int tries = ports_avail < 30 ? 3 * ports_avail : 100;

      memset(&addr, 0, sizeof(addr));
      addr.sa.sa_family = family;

      /* don't loop forever if all ports in use. */

      if (fix_fd(fd))
	while(tries--)
	  {
	    unsigned short port = rand16();
	    
	    if (daemon->min_port != 0)
	      port = htons(daemon->min_port + (port % ((unsigned short)ports_avail)));
	    
	    if (family == AF_INET) 
	      {
		addr.in.sin_addr.s_addr = INADDR_ANY;
		addr.in.sin_port = port;
#ifdef HAVE_SOCKADDR_SA_LEN
		addr.in.sin_len = sizeof(struct sockaddr_in);
#endif
	      }
#ifdef HAVE_IPV6
	    else
	      {
		addr.in6.sin6_addr = in6addr_any; 
		addr.in6.sin6_port = port;
#ifdef HAVE_SOCKADDR_SA_LEN
		addr.in6.sin6_len = sizeof(struct sockaddr_in6);
#endif
	      }
#endif
	    
	    if (bind(fd, (struct sockaddr *)&addr, sa_len(&addr)) == 0)
	      return fd;
	    
	    if (errno != EADDRINUSE && errno != EACCES)
	      break;
	  }

      close(fd);
    }

  return -1; 
}
  

int local_bind(int fd, union mysockaddr *addr, char *intname, int is_tcp)
{
  union mysockaddr addr_copy = *addr;

  /* cannot set source _port_ for TCP connections. */
  if (is_tcp)
    {
      if (addr_copy.sa.sa_family == AF_INET)
	addr_copy.in.sin_port = 0;
#ifdef HAVE_IPV6
      else
	addr_copy.in6.sin6_port = 0;
#endif
    }
  
  if (bind(fd, (struct sockaddr *)&addr_copy, sa_len(&addr_copy)) == -1)
    return 0;
    
#if defined(SO_BINDTODEVICE)
  if (intname[0] != 0 &&
      setsockopt(fd, SOL_SOCKET, SO_BINDTODEVICE, intname, strlen(intname)) == -1)
    return 0;
#endif

  return 1;
}

static struct serverfd *allocate_sfd(union mysockaddr *addr, char *intname)
{
  struct serverfd *sfd;
  int errsave;

  /* when using random ports, servers which would otherwise use
     the INADDR_ANY/port0 socket have sfd set to NULL */
  if (!daemon->osport && intname[0] == 0)
    {
      errno = 0;
      
      if (addr->sa.sa_family == AF_INET &&
	  addr->in.sin_addr.s_addr == INADDR_ANY &&
	  addr->in.sin_port == htons(0)) 
	return NULL;

#ifdef HAVE_IPV6
      if (addr->sa.sa_family == AF_INET6 &&
	  memcmp(&addr->in6.sin6_addr, &in6addr_any, sizeof(in6addr_any)) == 0 &&
	  addr->in6.sin6_port == htons(0)) 
	return NULL;
#endif
    }
      
  /* may have a suitable one already */
  for (sfd = daemon->sfds; sfd; sfd = sfd->next )
    if (sockaddr_isequal(&sfd->source_addr, addr) &&
	strcmp(intname, sfd->interface) == 0)
      return sfd;
  
  /* need to make a new one. */
  errno = ENOMEM; /* in case malloc fails. */
  if (!(sfd = whine_malloc(sizeof(struct serverfd))))
    return NULL;
  
  if ((sfd->fd = socket(addr->sa.sa_family, SOCK_DGRAM, 0)) == -1)
    {
      free(sfd);
      return NULL;
    }
  
  if (!local_bind(sfd->fd, addr, intname, 0) || !fix_fd(sfd->fd))
    { 
      errsave = errno; /* save error from bind. */
      close(sfd->fd);
      free(sfd);
      errno = errsave;
      return NULL;
    }
    
  strcpy(sfd->interface, intname); 
  sfd->source_addr = *addr;
  sfd->next = daemon->sfds;
  daemon->sfds = sfd;
  return sfd; 
}

/* create upstream sockets during startup, before root is dropped which may be needed
   this allows query_port to be a low port and interface binding */
void pre_allocate_sfds(void)
{
  struct server *srv;
  
  if (daemon->query_port != 0)
    {
      union  mysockaddr addr;
      memset(&addr, 0, sizeof(addr));
      addr.in.sin_family = AF_INET;
      addr.in.sin_addr.s_addr = INADDR_ANY;
      addr.in.sin_port = htons(daemon->query_port);
#ifdef HAVE_SOCKADDR_SA_LEN
      addr.in.sin_len = sizeof(struct sockaddr_in);
#endif
      allocate_sfd(&addr, "");
#ifdef HAVE_IPV6
      memset(&addr, 0, sizeof(addr));
      addr.in6.sin6_family = AF_INET6;
      addr.in6.sin6_addr = in6addr_any;
      addr.in6.sin6_port = htons(daemon->query_port);
#ifdef HAVE_SOCKADDR_SA_LEN
      addr.in6.sin6_len = sizeof(struct sockaddr_in6);
#endif
      allocate_sfd(&addr, "");
#endif
    }
  
  for (srv = daemon->servers; srv; srv = srv->next)
    if (!(srv->flags & (SERV_LITERAL_ADDRESS | SERV_NO_ADDR)) &&
	!allocate_sfd(&srv->source_addr, srv->interface) &&
	errno != 0 &&
	(daemon->options & OPT_NOWILD))
      {
	prettyprint_addr(&srv->addr, daemon->namebuff);
	if (srv->interface[0] != 0)
	  {
	    strcat(daemon->namebuff, " ");
	    strcat(daemon->namebuff, srv->interface);
	  }
	die(_("failed to bind server socket for %s: %s"),
	    daemon->namebuff, EC_BADNET);
      }  
}


void check_servers(void)
{
  struct irec *iface;
  struct server *new, *tmp, *ret = NULL;
  int port = 0;

  for (new = daemon->servers; new; new = tmp)
    {
      tmp = new->next;
      
      if (!(new->flags & (SERV_LITERAL_ADDRESS | SERV_NO_ADDR)))
	{
	  port = prettyprint_addr(&new->addr, daemon->namebuff);

	  /* 0.0.0.0 is nothing, the stack treats it like 127.0.0.1 */
	  if (new->addr.sa.sa_family == AF_INET &&
	      new->addr.in.sin_addr.s_addr == 0)
	    {
	      free(new);
	      continue;
	    }

	  for (iface = daemon->interfaces; iface; iface = iface->next)
	    if (sockaddr_isequal(&new->addr, &iface->addr))
	      break;
	  if (iface)
	    {
	      my_syslog(LOG_WARNING, _("ignoring nameserver %s - local interface"), daemon->namebuff);
	      free(new);
	      continue;
	    }
	  
	  /* Do we need a socket set? */
	  if (!new->sfd && 
	      !(new->sfd = allocate_sfd(&new->source_addr, new->interface)) &&
	      errno != 0)
	    {
	      my_syslog(LOG_WARNING, 
			_("ignoring nameserver %s - cannot make/bind socket: %s"),
			daemon->namebuff, strerror(errno));
	      free(new);
	      continue;
	    }
	}
      
      /* reverse order - gets it right. */
      new->next = ret;
      ret = new;
      
      if (new->flags & (SERV_HAS_DOMAIN | SERV_FOR_NODOTS))
	{
	  char *s1, *s2;
	  if (!(new->flags & SERV_HAS_DOMAIN))
	    s1 = _("unqualified"), s2 = _("names");
	  else if (strlen(new->domain) == 0)
	    s1 = _("default"), s2 = "";
	  else
	    s1 = _("domain"), s2 = new->domain;
	  
	  if (new->flags & SERV_NO_ADDR)
	    my_syslog(LOG_INFO, _("using local addresses only for %s %s"), s1, s2);
	  else if (!(new->flags & SERV_LITERAL_ADDRESS))
	    my_syslog(LOG_INFO, _("using nameserver %s#%d for %s %s"), daemon->namebuff, port, s1, s2);
	}
      else if (new->interface[0] != 0)
	my_syslog(LOG_INFO, _("using nameserver %s#%d(via %s)"), daemon->namebuff, port, new->interface); 
      else
	my_syslog(LOG_INFO, _("using nameserver %s#%d"), daemon->namebuff, port); 
    }
  
  daemon->servers = ret;
}

#ifdef __ANDROID__
/*
 * Takes a string in the format "1.2.3.4:1.2.3.4:..." - up to 1024 bytes in length
 */
int set_servers(const char *servers)
{
  char s[1024];
  struct server *old_servers = NULL;
  struct server *new_servers = NULL;
  struct server *serv;

  strncpy(s, servers, sizeof(s));

  /* move old servers to free list - we can reuse the memory
     and not risk malloc if there are the same or fewer new servers.
     Servers which were specced on the command line go to the new list. */
  for (serv = daemon->servers; serv;)
    {
      struct server *tmp = serv->next;
      if (serv->flags & SERV_FROM_RESOLV)
	{
	  serv->next = old_servers;
	  old_servers = serv;
	  /* forward table rules reference servers, so have to blow them away */
	  server_gone(serv);
	}
      else
	{
	  serv->next = new_servers;
	  new_servers = serv;
	}
      serv = tmp;
    }

    char *next = s;
    char *saddr;

 while ((saddr = strsep(&next, ":"))) {
      union mysockaddr addr, source_addr;
      memset(&addr, 0, sizeof(addr));
      memset(&source_addr, 0, sizeof(source_addr));

      if ((addr.in.sin_addr.s_addr = inet_addr(saddr)) != (in_addr_t) -1)
	{
#ifdef HAVE_SOCKADDR_SA_LEN
	  source_addr.in.sin_len = addr.in.sin_len = sizeof(source_addr.in);
#endif
	  source_addr.in.sin_family = addr.in.sin_family = AF_INET;
	  addr.in.sin_port = htons(NAMESERVER_PORT);
	  source_addr.in.sin_addr.s_addr = INADDR_ANY;
	  source_addr.in.sin_port = htons(daemon->query_port);
	}
#ifdef HAVE_IPV6
      else if (inet_pton(AF_INET6, saddr, &addr.in6.sin6_addr) > 0)
	{
#ifdef HAVE_SOCKADDR_SA_LEN
	  source_addr.in6.sin6_len = addr.in6.sin6_len = sizeof(source_addr.in6);
#endif
	  source_addr.in6.sin6_family = addr.in6.sin6_family = AF_INET6;
	  addr.in6.sin6_port = htons(NAMESERVER_PORT);
	  source_addr.in6.sin6_addr = in6addr_any;
	  source_addr.in6.sin6_port = htons(daemon->query_port);
	}
#endif /* IPV6 */
      else
	continue;

      if (old_servers)
	{
	  serv = old_servers;
	  old_servers = old_servers->next;
	}
      else if (!(serv = whine_malloc(sizeof (struct server))))
	continue;

      /* this list is reverse ordered:
	 it gets reversed again in check_servers */
      serv->next = new_servers;
      new_servers = serv;
      serv->addr = addr;
      serv->source_addr = source_addr;
      serv->domain = NULL;
      serv->interface[0] = 0;
      serv->sfd = NULL;
      serv->flags = SERV_FROM_RESOLV;
      serv->queries = serv->failed_queries = 0;
    }

  /* Free any memory not used. */
  while (old_servers)
    {
      struct server *tmp = old_servers->next;
      free(old_servers);
      old_servers = tmp;
    }

  daemon->servers = new_servers;
  return 0;
}
#endif

/* Return zero if no servers found, in that case we keep polling.
   This is a protection against an update-time/write race on resolv.conf */
int reload_servers(char *fname)
{
  FILE *f;
  char *line;
  struct server *old_servers = NULL;
  struct server *new_servers = NULL;
  struct server *serv;
  int gotone = 0;

  /* buff happens to be MAXDNAME long... */
  if (!(f = fopen(fname, "r")))
    {
      my_syslog(LOG_ERR, _("failed to read %s: %s"), fname, strerror(errno));
      return 0;
    }
  
  /* move old servers to free list - we can reuse the memory 
     and not risk malloc if there are the same or fewer new servers. 
     Servers which were specced on the command line go to the new list. */
  for (serv = daemon->servers; serv;)
    {
      struct server *tmp = serv->next;
      if (serv->flags & SERV_FROM_RESOLV)
	{
	  serv->next = old_servers;
	  old_servers = serv; 
	  /* forward table rules reference servers, so have to blow them away */
	  server_gone(serv);
	}
      else
	{
	  serv->next = new_servers;
	  new_servers = serv;
	}
      serv = tmp;
    }
  
  while ((line = fgets(daemon->namebuff, MAXDNAME, f)))
    {
      union mysockaddr addr, source_addr;
      char *token = strtok(line, " \t\n\r");
      
      if (!token)
	continue;
      if (strcmp(token, "nameserver") != 0 && strcmp(token, "server") != 0)
	continue;
      if (!(token = strtok(NULL, " \t\n\r")))
	continue;
      
      memset(&addr, 0, sizeof(addr));
      memset(&source_addr, 0, sizeof(source_addr));
      
      if ((addr.in.sin_addr.s_addr = inet_addr(token)) != (in_addr_t) -1)
	{
#ifdef HAVE_SOCKADDR_SA_LEN
	  source_addr.in.sin_len = addr.in.sin_len = sizeof(source_addr.in);
#endif
	  source_addr.in.sin_family = addr.in.sin_family = AF_INET;
	  addr.in.sin_port = htons(NAMESERVER_PORT);
	  source_addr.in.sin_addr.s_addr = INADDR_ANY;
	  source_addr.in.sin_port = htons(daemon->query_port);
	}
#ifdef HAVE_IPV6
      else if (inet_pton(AF_INET6, token, &addr.in6.sin6_addr) > 0)
	{
#ifdef HAVE_SOCKADDR_SA_LEN
	  source_addr.in6.sin6_len = addr.in6.sin6_len = sizeof(source_addr.in6);
#endif
	  source_addr.in6.sin6_family = addr.in6.sin6_family = AF_INET6;
	  addr.in6.sin6_port = htons(NAMESERVER_PORT);
	  source_addr.in6.sin6_addr = in6addr_any;
	  source_addr.in6.sin6_port = htons(daemon->query_port);
	}
#endif /* IPV6 */
      else
	continue;
      
      if (old_servers)
	{
	  serv = old_servers;
	  old_servers = old_servers->next;
	}
      else if (!(serv = whine_malloc(sizeof (struct server))))
	continue;
      
      /* this list is reverse ordered: 
	 it gets reversed again in check_servers */
      serv->next = new_servers;
      new_servers = serv;
      serv->addr = addr;
      serv->source_addr = source_addr;
      serv->domain = NULL;
      serv->interface[0] = 0;
      serv->sfd = NULL;
      serv->flags = SERV_FROM_RESOLV;
      serv->queries = serv->failed_queries = 0;
      gotone = 1;
    }
  
  /* Free any memory not used. */
  while (old_servers)
    {
      struct server *tmp = old_servers->next;
      free(old_servers);
      old_servers = tmp;
    }

  daemon->servers = new_servers;
  fclose(f);

  return gotone;
}


/* Use an IPv4 listener socket for ioctling */
struct in_addr get_ifaddr(char *intr)
{
  struct listener *l;
  struct ifreq ifr;

  for (l = daemon->listeners; l && l->family != AF_INET; l = l->next);
  
  strncpy(ifr.ifr_name, intr, IF_NAMESIZE);
  ifr.ifr_addr.sa_family = AF_INET;
  
  if (!l || ioctl(l->fd, SIOCGIFADDR, &ifr) == -1)
    ((struct sockaddr_in *) &ifr.ifr_addr)->sin_addr.s_addr = -1;
  
  return ((struct sockaddr_in *) &ifr.ifr_addr)->sin_addr;
}



