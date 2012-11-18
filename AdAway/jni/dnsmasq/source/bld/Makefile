CFLAGS = -Wall -W -O2

OBJS = cache.o rfc1035.o util.o option.o forward.o network.o \
       dnsmasq.o dhcp.o lease.o rfc2131.o netlink.o dbus.o bpf.o \
       helper.o tftp.o log.o

.c.o:
	$(CC) $(CFLAGS) $(COPTS) $(I18N) $(DNSMASQ_CFLAGS) $(RPM_OPT_FLAGS) -c $<

dnsmasq : $(OBJS)
	$(CC) $(LDFLAGS) -o $@  $(OBJS) $(DNSMASQ_LIBS) $(LIBS) 
 
dnsmasq.pot : $(OBJS:.o=.c) dnsmasq.h config.h
	$(XGETTEXT) -d dnsmasq --foreign-user --omit-header --keyword=_ -o $@ -i $(OBJS:.o=.c)

%.mo : ../po/%.po dnsmasq.pot
	$(MSGMERGE) -o - ../po/$*.po dnsmasq.pot | $(MSGFMT) -o $*.mo -
