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

#ifdef HAVE_DBUS

#include <dbus/dbus.h>

const char* introspection_xml =
"<!DOCTYPE node PUBLIC \"-//freedesktop//DTD D-BUS Object Introspection 1.0//EN\"\n"
"\"http://www.freedesktop.org/standards/dbus/1.0/introspect.dtd\">\n"
"<node name=\"" DNSMASQ_PATH "\">\n"
"  <interface name=\"org.freedesktop.DBus.Introspectable\">\n"
"    <method name=\"Introspect\">\n"
"      <arg name=\"data\" direction=\"out\" type=\"s\"/>\n"
"    </method>\n"
"  </interface>\n"
"  <interface name=\"" DNSMASQ_SERVICE "\">\n"
"    <method name=\"ClearCache\">\n"
"    </method>\n"
"    <method name=\"GetVersion\">\n"
"      <arg name=\"version\" direction=\"out\" type=\"s\"/>\n"
"    </method>\n"
"    <method name=\"SetServers\">\n"
"      <arg name=\"servers\" direction=\"in\" type=\"av\"/>\n"
"    </method>\n"
"    <signal name=\"DhcpLeaseAdded\">\n"
"      <arg name=\"ipaddr\" type=\"s\"/>\n"
"      <arg name=\"hwaddr\" type=\"s\"/>\n"
"      <arg name=\"hostname\" type=\"s\"/>\n"
"    </signal>\n"
"    <signal name=\"DhcpLeaseDeleted\">\n"
"      <arg name=\"ipaddr\" type=\"s\"/>\n"
"      <arg name=\"hwaddr\" type=\"s\"/>\n"
"      <arg name=\"hostname\" type=\"s\"/>\n"
"    </signal>\n"
"    <signal name=\"DhcpLeaseUpdated\">\n"
"      <arg name=\"ipaddr\" type=\"s\"/>\n"
"      <arg name=\"hwaddr\" type=\"s\"/>\n"
"      <arg name=\"hostname\" type=\"s\"/>\n"
"    </signal>\n"
"  </interface>\n"
"</node>\n";

struct watch {
  DBusWatch *watch;      
  struct watch *next;
};


static dbus_bool_t add_watch(DBusWatch *watch, void *data)
{
  struct watch *w;

  for (w = daemon->watches; w; w = w->next)
    if (w->watch == watch)
      return TRUE;

  if (!(w = whine_malloc(sizeof(struct watch))))
    return FALSE;

  w->watch = watch;
  w->next = daemon->watches;
  daemon->watches = w;

  w = data; /* no warning */
  return TRUE;
}

static void remove_watch(DBusWatch *watch, void *data)
{
  struct watch **up, *w;  
  
  for (up = &(daemon->watches), w = daemon->watches; w; w = w->next)
    if (w->watch == watch)
      {
        *up = w->next;
        free(w);
      }
    else
      up = &(w->next);

  w = data; /* no warning */
}

static void dbus_read_servers(DBusMessage *message)
{
  struct server *serv, *tmp, **up;
  DBusMessageIter iter;
  union  mysockaddr addr, source_addr;
  char *domain;
  
  dbus_message_iter_init(message, &iter);
  
  /* mark everything from DBUS */
  for (serv = daemon->servers; serv; serv = serv->next)
    if (serv->flags & SERV_FROM_DBUS)
      serv->flags |= SERV_MARK;
  
  while (1)
    {
      int skip = 0;

      if (dbus_message_iter_get_arg_type(&iter) == DBUS_TYPE_UINT32)
	{
	  u32 a;
	  
	  dbus_message_iter_get_basic(&iter, &a);
	  dbus_message_iter_next (&iter);
	  
#ifdef HAVE_SOCKADDR_SA_LEN
	  source_addr.in.sin_len = addr.in.sin_len = sizeof(struct sockaddr_in);
#endif
	  addr.in.sin_addr.s_addr = ntohl(a);
	  source_addr.in.sin_family = addr.in.sin_family = AF_INET;
	  addr.in.sin_port = htons(NAMESERVER_PORT);
	  source_addr.in.sin_addr.s_addr = INADDR_ANY;
	  source_addr.in.sin_port = htons(daemon->query_port);
	}
      else if (dbus_message_iter_get_arg_type(&iter) == DBUS_TYPE_BYTE)
	{
	  unsigned char p[sizeof(struct in6_addr)];
	  unsigned int i;

	  skip = 1;

	  for(i = 0; i < sizeof(struct in6_addr); i++)
	    {
	      dbus_message_iter_get_basic(&iter, &p[i]);
	      dbus_message_iter_next (&iter);
	      if (dbus_message_iter_get_arg_type(&iter) != DBUS_TYPE_BYTE)
		break;
	    }

#ifndef HAVE_IPV6
	  my_syslog(LOG_WARNING, _("attempt to set an IPv6 server address via DBus - no IPv6 support"));
#else
	  if (i == sizeof(struct in6_addr)-1)
	    {
	      memcpy(&addr.in6.sin6_addr, p, sizeof(struct in6_addr));
#ifdef HAVE_SOCKADDR_SA_LEN
              source_addr.in6.sin6_len = addr.in6.sin6_len = sizeof(struct sockaddr_in6);
#endif
              source_addr.in6.sin6_family = addr.in6.sin6_family = AF_INET6;
              addr.in6.sin6_port = htons(NAMESERVER_PORT);
              source_addr.in6.sin6_flowinfo = addr.in6.sin6_flowinfo = 0;
	      source_addr.in6.sin6_scope_id = addr.in6.sin6_scope_id = 0;
              source_addr.in6.sin6_addr = in6addr_any;
              source_addr.in6.sin6_port = htons(daemon->query_port);
	      skip = 0;
	    }
#endif
	}
      else
	/* At the end */
	break;
      
      do {
	if (dbus_message_iter_get_arg_type(&iter) == DBUS_TYPE_STRING)
	  {
	    dbus_message_iter_get_basic(&iter, &domain);
	    dbus_message_iter_next (&iter);
	  }
	else
	  domain = NULL;
	
	if (!skip)
	  {
	    /* See if this is already there, and unmark */
	    for (serv = daemon->servers; serv; serv = serv->next)
	      if ((serv->flags & SERV_FROM_DBUS) &&
		  (serv->flags & SERV_MARK))
		{
		  if (!(serv->flags & SERV_HAS_DOMAIN) && !domain)
		    {
		      serv->flags &= ~SERV_MARK;
		      break;
		    }
		  if ((serv->flags & SERV_HAS_DOMAIN) && 
		      domain &&
		      hostname_isequal(domain, serv->domain))
		    {
		      serv->flags &= ~SERV_MARK;
		      break;
		    }
		}
	    
	    if (!serv && (serv = whine_malloc(sizeof (struct server))))
	      {
		/* Not found, create a new one. */
		memset(serv, 0, sizeof(struct server));
		
		if (domain)
		  serv->domain = whine_malloc(strlen(domain)+1);
		
		if (domain && !serv->domain)
		  {
		    free(serv);
		    serv = NULL;
		  }
		else
		  {
		    serv->next = daemon->servers;
		    daemon->servers = serv;
		    serv->flags = SERV_FROM_DBUS;
		    if (domain)
		      {
			strcpy(serv->domain, domain);
			serv->flags |= SERV_HAS_DOMAIN;
		      }
		  }
	      }

	    if (serv)
	      {
		if (source_addr.in.sin_family == AF_INET &&
		    addr.in.sin_addr.s_addr == 0 &&
		    serv->domain)
		  serv->flags |= SERV_NO_ADDR;
		else
		  {
		    serv->flags &= ~SERV_NO_ADDR;
		    serv->addr = addr;
		    serv->source_addr = source_addr;
		  }
	      }
	  }
	} while (dbus_message_iter_get_arg_type(&iter) == DBUS_TYPE_STRING);
    }
  
  /* unlink and free anything still marked. */
  for (serv = daemon->servers, up = &daemon->servers; serv; serv = tmp) 
    {
      tmp = serv->next;
      if (serv->flags & SERV_MARK)
	{
	  server_gone(serv);
	  *up = serv->next;
	  free(serv);
	}
      else 
	up = &serv->next;
    }

}

DBusHandlerResult message_handler(DBusConnection *connection, 
				  DBusMessage *message, 
				  void *user_data)
{
  char *method = (char *)dbus_message_get_member(message);
   
  if (dbus_message_is_method_call(message, DBUS_INTERFACE_INTROSPECTABLE, "Introspect"))
    {
      DBusMessage *reply = dbus_message_new_method_return(message);

      dbus_message_append_args(reply, DBUS_TYPE_STRING, &introspection_xml, DBUS_TYPE_INVALID);
      dbus_connection_send (connection, reply, NULL);
      dbus_message_unref (reply);
    }
  else if (strcmp(method, "GetVersion") == 0)
    {
      char *v = VERSION;
      DBusMessage *reply = dbus_message_new_method_return(message);
      
      dbus_message_append_args(reply, DBUS_TYPE_STRING, &v, DBUS_TYPE_INVALID);
      dbus_connection_send (connection, reply, NULL);
      dbus_message_unref (reply);
    }
  else if (strcmp(method, "SetServers") == 0)
    {
      my_syslog(LOG_INFO, _("setting upstream servers from DBus"));
      dbus_read_servers(message);
      check_servers();
    }
  else if (strcmp(method, "ClearCache") == 0)
    clear_cache_and_reload(dnsmasq_time());
  else
    return (DBUS_HANDLER_RESULT_NOT_YET_HANDLED);
  
  method = user_data; /* no warning */

  return (DBUS_HANDLER_RESULT_HANDLED);
 
}
 

/* returns NULL or error message, may fail silently if dbus daemon not yet up. */
char *dbus_init(void)
{
  DBusConnection *connection = NULL;
  DBusObjectPathVTable dnsmasq_vtable = {NULL, &message_handler, NULL, NULL, NULL, NULL };
  DBusError dbus_error;
  DBusMessage *message;

  dbus_error_init (&dbus_error);
  if (!(connection = dbus_bus_get (DBUS_BUS_SYSTEM, &dbus_error)))
    return NULL;
    
  dbus_connection_set_exit_on_disconnect(connection, FALSE);
  dbus_connection_set_watch_functions(connection, add_watch, remove_watch, 
				      NULL, NULL, NULL);
  dbus_error_init (&dbus_error);
  dbus_bus_request_name (connection, DNSMASQ_SERVICE, 0, &dbus_error);
  if (dbus_error_is_set (&dbus_error))
    return (char *)dbus_error.message;
  
  if (!dbus_connection_register_object_path(connection,  DNSMASQ_PATH, 
					    &dnsmasq_vtable, NULL))
    return _("could not register a DBus message handler");
  
  daemon->dbus = connection; 
  
  if ((message = dbus_message_new_signal(DNSMASQ_PATH, DNSMASQ_SERVICE, "Up")))
    {
      dbus_connection_send(connection, message, NULL);
      dbus_message_unref(message);
    }

  return NULL;
}
 

void set_dbus_listeners(int *maxfdp,
			fd_set *rset, fd_set *wset, fd_set *eset)
{
  struct watch *w;
  
  for (w = daemon->watches; w; w = w->next)
    if (dbus_watch_get_enabled(w->watch))
      {
	unsigned int flags = dbus_watch_get_flags(w->watch);
	int fd = dbus_watch_get_unix_fd(w->watch);
	
	bump_maxfd(fd, maxfdp);
	
	if (flags & DBUS_WATCH_READABLE)
	  FD_SET(fd, rset);
	
	if (flags & DBUS_WATCH_WRITABLE)
	  FD_SET(fd, wset);
	
	FD_SET(fd, eset);
      }
}

void check_dbus_listeners(fd_set *rset, fd_set *wset, fd_set *eset)
{
  DBusConnection *connection = (DBusConnection *)daemon->dbus;
  struct watch *w;

  for (w = daemon->watches; w; w = w->next)
    if (dbus_watch_get_enabled(w->watch))
      {
	unsigned int flags = 0;
	int fd = dbus_watch_get_unix_fd(w->watch);
	
	if (FD_ISSET(fd, rset))
	  flags |= DBUS_WATCH_READABLE;
	
	if (FD_ISSET(fd, wset))
	  flags |= DBUS_WATCH_WRITABLE;
	
	if (FD_ISSET(fd, eset))
	  flags |= DBUS_WATCH_ERROR;

	if (flags != 0)
	  dbus_watch_handle(w->watch, flags);
      }

  if (connection)
    {
      dbus_connection_ref (connection);
      while (dbus_connection_dispatch (connection) == DBUS_DISPATCH_DATA_REMAINS);
      dbus_connection_unref (connection);
    }
}

void emit_dbus_signal(int action, struct dhcp_lease *lease, char *hostname)
{
  DBusConnection *connection = (DBusConnection *)daemon->dbus;
  DBusMessage* message = NULL;
  DBusMessageIter args;
  char *action_str, *addr, *mac = daemon->namebuff;
  unsigned char *p;
  int i;

  if (!connection)
    return;
  
  if (!hostname)
    hostname = "";
  
  p = extended_hwaddr(lease->hwaddr_type, lease->hwaddr_len,
		      lease->hwaddr, lease->clid_len, lease->clid, &i);
  print_mac(mac, p, i);
  
  if (action == ACTION_DEL)
    action_str = "DhcpLeaseDeleted";
  else if (action == ACTION_ADD)
    action_str = "DhcpLeaseAdded";
  else if (action == ACTION_OLD)
    action_str = "DhcpLeaseUpdated";
  else
    return;

  addr = inet_ntoa(lease->addr);

  if (!(message = dbus_message_new_signal(DNSMASQ_PATH, DNSMASQ_SERVICE, action_str)))
    return;
  
  dbus_message_iter_init_append(message, &args);

  if (dbus_message_iter_append_basic(&args, DBUS_TYPE_STRING, &addr) &&
      dbus_message_iter_append_basic(&args, DBUS_TYPE_STRING, &mac) &&
      dbus_message_iter_append_basic(&args, DBUS_TYPE_STRING, &hostname))
    dbus_connection_send(connection, message, NULL);
  
  dbus_message_unref(message);
}

#endif
