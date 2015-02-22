# Microsoft Developer Studio Project File - Name="WinDump" - Package Owner=<4>
# Microsoft Developer Studio Generated Build File, Format Version 6.00
# ** DO NOT EDIT **

# TARGTYPE "Win32 (x86) Console Application" 0x0103

CFG=WinDump - Win32 Debug
!MESSAGE This is not a valid makefile. To build this project using NMAKE,
!MESSAGE use the Export Makefile command and run
!MESSAGE 
!MESSAGE NMAKE /f "WinDump.mak".
!MESSAGE 
!MESSAGE You can specify a configuration when running NMAKE
!MESSAGE by defining the macro CFG on the command line. For example:
!MESSAGE 
!MESSAGE NMAKE /f "WinDump.mak" CFG="WinDump - Win32 Debug"
!MESSAGE 
!MESSAGE Possible choices for configuration are:
!MESSAGE 
!MESSAGE "WinDump - Win32 Release" (based on "Win32 (x86) Console Application")
!MESSAGE "WinDump - Win32 Debug" (based on "Win32 (x86) Console Application")
!MESSAGE 

# Begin Project
# PROP AllowPerConfigDependencies 1
# PROP Scc_ProjName ""
# PROP Scc_LocalPath ""
CPP=cl.exe
RSC=rc.exe

!IF  "$(CFG)" == "WinDump - Win32 Release"

# PROP BASE Use_MFC 0
# PROP BASE Use_Debug_Libraries 0
# PROP BASE Output_Dir "Release"
# PROP BASE Intermediate_Dir "Release"
# PROP BASE Target_Dir ""
# PROP Use_MFC 0
# PROP Use_Debug_Libraries 0
# PROP Output_Dir "../../"
# PROP Intermediate_Dir "Release"
# PROP Ignore_Export_Lib 0
# PROP Target_Dir ""
# ADD BASE CPP /nologo /W3 /GX /O2 /D "WIN32" /D "NDEBUG" /D "_CONSOLE" /D "_MBCS" /YX /FD /c
# ADD CPP /nologo /W3 /GX /O2 /I "../../../winpcap/wpcap/libpcap/bpf" /I "../../../winpcap/wpcap/libpcap" /I "../../../winpcap/wpcap/libpcap/Win32/Include" /I "../../../winpcap/wpcap/libpcap/Win32/Include/net" /I "../../Win32/Include" /I "../../linux-Include" /I "../../lbl" /I "../../" /I "../../../winpcap/wpcap/win32-extensions" /D "NDEBUG" /D "INET6" /D "WIN32" /D "_MBCS" /D "_CONSOLE" /D "__STDC__" /D "WPCAP" /D HAVE_ADDRINFO=1 /D HAVE_SOCKADDR_STORAGE=1 /D HAVE_PCAP_LIST_DATALINKS=1 /D HAVE_PCAP_SET_DATALINK=1 /D HAVE_PCAP_DATALINK_NAME_TO_VAL=1 /D HAVE_PCAP_DATALINK_VAL_TO_DESCRIPTION=1 /D HAVE_PCAP_DUMP_FTELL=1 /D HAVE_BPF_DUMP=1 /D HAVE_PCAP_DUMP_FLUSH=1 /D HAVE_PCAP_FINDALLDEVS=1 /D HAVE_PCAP_IF_T=1 /D HAVE_PCAP_LIB_VERSION=1 /D "HAVE_REMOTE" /D _U_= /YX /FD /c
# ADD BASE RSC /l 0x410 /d "NDEBUG"
# ADD RSC /l 0x410 /d "NDEBUG"
BSC32=bscmake.exe
# ADD BASE BSC32 /nologo
# ADD BSC32 /nologo
LINK32=link.exe
# ADD BASE LINK32 kernel32.lib user32.lib gdi32.lib winspool.lib comdlg32.lib advapi32.lib shell32.lib ole32.lib oleaut32.lib uuid.lib odbc32.lib odbccp32.lib /nologo /subsystem:console /machine:I386
# ADD LINK32 kernel32.lib user32.lib gdi32.lib winspool.lib comdlg32.lib advapi32.lib shell32.lib ole32.lib oleaut32.lib uuid.lib odbc32.lib odbccp32.lib wsock32.lib wpcap.lib /nologo /subsystem:console /machine:I386 /out:"release/WinDump.exe" /libpath:"../../../winpcap/wpcap/lib"

!ELSEIF  "$(CFG)" == "WinDump - Win32 Debug"

# PROP BASE Use_MFC 0
# PROP BASE Use_Debug_Libraries 1
# PROP BASE Output_Dir "WinDump_"
# PROP BASE Intermediate_Dir "WinDump_"
# PROP BASE Target_Dir ""
# PROP Use_MFC 0
# PROP Use_Debug_Libraries 1
# PROP Output_Dir "../../"
# PROP Intermediate_Dir "Debug"
# PROP Ignore_Export_Lib 0
# PROP Target_Dir ""
# ADD BASE CPP /nologo /W3 /Gm /GX /Zi /Od /D "WIN32" /D "_DEBUG" /D "_CONSOLE" /D "_MBCS" /YX /FD /c
# ADD CPP /nologo /W3 /Gm /Gi /GX /ZI /I "../../../winpcap/wpcap/libpcap/bpf" /I "../../../winpcap/wpcap/libpcap" /I "../../../winpcap/wpcap/libpcap/Win32/Include" /I "../../../winpcap/wpcap/libpcap/Win32/Include/net" /I "../../Win32/Include" /I "../../linux-Include" /I "../../lbl" /I "../../" /I "../../../winpcap/wpcap/win32-extensions" /D "_DEBUG" /D "_WINDOWS" /D "INET6" /D "WIN32" /D "_MBCS" /D "_CONSOLE" /D "__STDC__" /D "WPCAP" /D HAVE_ADDRINFO=1 /D HAVE_SOCKADDR_STORAGE=1 /D HAVE_PCAP_LIST_DATALINKS=1 /D HAVE_PCAP_SET_DATALINK=1 /D HAVE_PCAP_DATALINK_NAME_TO_VAL=1 /D HAVE_PCAP_DATALINK_VAL_TO_DESCRIPTION=1 /D HAVE_PCAP_DUMP_FTELL=1 /D HAVE_BPF_DUMP=1 /D HAVE_PCAP_DUMP_FLUSH=1 /D HAVE_PCAP_FINDALLDEVS=1 /D HAVE_PCAP_IF_T=1 /D HAVE_PCAP_LIB_VERSION=1 /D "HAVE_REMOTE" /D _U_= /FR /YX /FD /c
# ADD BASE RSC /l 0x410 /d "_DEBUG"
# ADD RSC /l 0x410 /d "_DEBUG"
BSC32=bscmake.exe
# ADD BASE BSC32 /nologo
# ADD BSC32 /nologo
LINK32=link.exe
# ADD BASE LINK32 kernel32.lib user32.lib gdi32.lib winspool.lib comdlg32.lib advapi32.lib shell32.lib ole32.lib oleaut32.lib uuid.lib odbc32.lib odbccp32.lib /nologo /subsystem:console /debug /machine:I386 /pdbtype:sept
# ADD LINK32 wpcap.lib kernel32.lib user32.lib gdi32.lib winspool.lib comdlg32.lib advapi32.lib shell32.lib ole32.lib oleaut32.lib uuid.lib odbc32.lib odbccp32.lib wsock32.lib /nologo /subsystem:console /map /debug /debugtype:both /machine:I386 /out:"debug/WinDump.exe" /pdbtype:sept /libpath:"../../../winpcap/wpcap/lib"
# SUBTRACT LINK32 /pdb:none

!ENDIF 

# Begin Target

# Name "WinDump - Win32 Release"
# Name "WinDump - Win32 Debug"
# Begin Source File

SOURCE=..\..\addrtoname.c
# End Source File
# Begin Source File

SOURCE=..\..\af.c
# End Source File
# Begin Source File

SOURCE=..\..\bpf_dump.c
# End Source File
# Begin Source File

SOURCE=..\..\checksum.c
# End Source File
# Begin Source File

SOURCE=..\..\cpack.c
# End Source File
# Begin Source File

SOURCE=..\..\missing\datalinks.c
# End Source File
# Begin Source File

SOURCE=..\..\missing\dlnames.c
# End Source File
# Begin Source File

SOURCE=..\Src\getopt.c
# End Source File
# Begin Source File

SOURCE=..\..\gmpls.c
# End Source File
# Begin Source File

SOURCE=..\..\gmt2local.c
# End Source File
# Begin Source File

SOURCE=..\..\missing\inet_aton.c
# End Source File
# Begin Source File

SOURCE=..\..\missing\inet_ntop.c
# End Source File
# Begin Source File

SOURCE=..\..\missing\inet_pton.c
# End Source File
# Begin Source File

SOURCE=..\..\ipproto.c
# End Source File
# Begin Source File

SOURCE=..\..\l2vpn.c
# End Source File
# Begin Source File

SOURCE=..\..\machdep.c
# End Source File
# Begin Source File

SOURCE=..\..\nlpid.c
# End Source File
# Begin Source File

SOURCE=..\..\oui.c
# End Source File
# Begin Source File

SOURCE=..\..\parsenfsfh.c
# End Source File
# Begin Source File

SOURCE="..\..\print-802_11.c"
# End Source File
# Begin Source File

SOURCE="..\..\print-ah.c"
# End Source File
# Begin Source File

SOURCE="..\..\print-aodv.c"
# End Source File
# Begin Source File

SOURCE="..\..\print-ap1394.c"
# End Source File
# Begin Source File

SOURCE="..\..\print-arcnet.c"
# End Source File
# Begin Source File

SOURCE="..\..\print-arp.c"
# End Source File
# Begin Source File

SOURCE="..\..\print-ascii.c"
# End Source File
# Begin Source File

SOURCE="..\..\print-atalk.c"
# End Source File
# Begin Source File

SOURCE="..\..\print-atm.c"
# End Source File
# Begin Source File

SOURCE="..\..\print-beep.c"
# End Source File
# Begin Source File

SOURCE="..\..\print-bfd.c"
# End Source File
# Begin Source File

SOURCE="..\..\print-bgp.c"
# End Source File
# Begin Source File

SOURCE="..\..\print-bootp.c"
# End Source File
# Begin Source File

SOURCE="..\..\print-bt.c"
# End Source File
# Begin Source File

SOURCE="..\..\print-cdp.c"
# End Source File
# Begin Source File

SOURCE="..\..\print-cfm.c"
# End Source File
# Begin Source File

SOURCE="..\..\print-chdlc.c"
# End Source File
# Begin Source File

SOURCE="..\..\print-cip.c"
# End Source File
# Begin Source File

SOURCE="..\..\print-cnfp.c"
# End Source File
# Begin Source File

SOURCE="..\..\print-dccp.c"
# End Source File
# Begin Source File

SOURCE="..\..\print-decnet.c"
# End Source File
# Begin Source File

SOURCE="..\..\print-dhcp6.c"
# End Source File
# Begin Source File

SOURCE="..\..\print-domain.c"
# End Source File
# Begin Source File

SOURCE="..\..\print-dtp.c"
# End Source File
# Begin Source File

SOURCE="..\..\print-dvmrp.c"
# End Source File
# Begin Source File

SOURCE="..\..\print-eap.c"
# End Source File
# Begin Source File

SOURCE="..\..\print-egp.c"
# End Source File
# Begin Source File

SOURCE="..\..\print-eigrp.c"
# End Source File
# Begin Source File

SOURCE="..\..\print-enc.c"
# End Source File
# Begin Source File

SOURCE="..\..\print-esp.c"
# End Source File
# Begin Source File

SOURCE="..\..\print-ether.c"
# End Source File
# Begin Source File

SOURCE="..\..\print-fddi.c"
# End Source File
# Begin Source File

SOURCE="..\..\print-fr.c"
# End Source File
# Begin Source File

SOURCE="..\..\print-frag6.c"
# End Source File
# Begin Source File

SOURCE="..\..\print-gre.c"
# End Source File
# Begin Source File

SOURCE="..\..\print-hsrp.c"
# End Source File
# Begin Source File

SOURCE="..\..\print-icmp.c"
# End Source File
# Begin Source File

SOURCE="..\..\print-icmp6.c"
# End Source File
# Begin Source File

SOURCE="..\..\print-igmp.c"
# End Source File
# Begin Source File

SOURCE="..\..\print-igrp.c"
# End Source File
# Begin Source File

SOURCE="..\..\print-ip.c"
# End Source File
# Begin Source File

SOURCE="..\..\print-ip6.c"
# End Source File
# Begin Source File

SOURCE="..\..\print-ip6opts.c"
# End Source File
# Begin Source File

SOURCE="..\..\print-ipcomp.c"
# End Source File
# Begin Source File

SOURCE="..\..\print-ipfc.c"
# End Source File
# Begin Source File

SOURCE="..\..\print-ipx.c"
# End Source File
# Begin Source File

SOURCE="..\..\print-isakmp.c"
# End Source File
# Begin Source File

SOURCE="..\..\print-isoclns.c"
# End Source File
# Begin Source File

SOURCE="..\..\print-juniper.c"
# End Source File
# Begin Source File

SOURCE="..\..\print-krb.c"
# End Source File
# Begin Source File

SOURCE="..\..\print-l2tp.c"
# End Source File
# Begin Source File

SOURCE="..\..\print-lane.c"
# End Source File
# Begin Source File

SOURCE="..\..\print-ldp.c"
# End Source File
# Begin Source File

SOURCE="..\..\print-llc.c"
# End Source File
# Begin Source File

SOURCE="..\..\print-lldp.c"
# End Source File
# Begin Source File

SOURCE="..\..\print-lmp.c"
# End Source File
# Begin Source File

SOURCE="..\..\print-lspping.c"
# End Source File
# Begin Source File

SOURCE="..\..\print-lwapp.c"
# End Source File
# Begin Source File

SOURCE="..\..\print-lwres.c"
# End Source File
# Begin Source File

SOURCE="..\..\print-mobile.c"
# End Source File
# Begin Source File

SOURCE="..\..\print-mobility.c"
# End Source File
# Begin Source File

SOURCE="..\..\print-mpcp.c"
# End Source File
# Begin Source File

SOURCE="..\..\print-mpls.c"
# End Source File
# Begin Source File

SOURCE="..\..\print-msdp.c"
# End Source File
# Begin Source File

SOURCE="..\..\print-netbios.c"
# End Source File
# Begin Source File

SOURCE="..\..\print-nfs.c"
# End Source File
# Begin Source File

SOURCE="..\..\print-ntp.c"
# End Source File
# Begin Source File

SOURCE="..\..\print-null.c"
# End Source File
# Begin Source File

SOURCE="..\..\print-olsr.c"
# End Source File
# Begin Source File

SOURCE="..\..\print-ospf.c"
# End Source File
# Begin Source File

SOURCE="..\..\print-ospf6.c"
# End Source File
# Begin Source File

SOURCE="..\..\print-pgm.c"
# End Source File
# Begin Source File

SOURCE="..\..\print-pim.c"
# End Source File
# Begin Source File

SOURCE="..\..\print-ppp.c"
# End Source File
# Begin Source File

SOURCE="..\..\print-pppoe.c"
# End Source File
# Begin Source File

SOURCE="..\..\print-pptp.c"
# End Source File
# Begin Source File

SOURCE="..\..\print-radius.c"
# End Source File
# Begin Source File

SOURCE="..\..\print-raw.c"
# End Source File
# Begin Source File

SOURCE="..\..\print-rrcp.c"
# End Source File
# Begin Source File

SOURCE="..\..\print-rip.c"
# End Source File
# Begin Source File

SOURCE="..\..\print-ripng.c"
# End Source File
# Begin Source File

SOURCE="..\..\print-rpki-rtr.c"
# End Source File
# Begin Source File

SOURCE="..\..\print-rsvp.c"
# End Source File
# Begin Source File

SOURCE="..\..\print-rt6.c"
# End Source File
# Begin Source File

SOURCE="..\..\print-rx.c"
# End Source File
# Begin Source File

SOURCE="..\..\print-sctp.c"
# End Source File
# Begin Source File

SOURCE="..\..\print-sflow.c"
# End Source File
# Begin Source File

SOURCE="..\..\print-sip.c"
# End Source File
# Begin Source File

SOURCE="..\..\print-sl.c"
# End Source File
# Begin Source File

SOURCE="..\..\print-sll.c"
# End Source File
# Begin Source File

SOURCE="..\..\print-slow.c"
# End Source File
# Begin Source File

SOURCE="..\..\print-smb.c"
# End Source File
# Begin Source File

SOURCE="..\..\print-snmp.c"
# End Source File
# Begin Source File

SOURCE="..\..\print-stp.c"
# End Source File
# Begin Source File

SOURCE="..\..\print-sunatm.c"
# End Source File
# Begin Source File

SOURCE="..\..\print-sunrpc.c"
# End Source File
# Begin Source File

SOURCE="..\..\print-symantec.c"
# End Source File
# Begin Source File

SOURCE="..\..\print-syslog.c"
# End Source File
# Begin Source File

SOURCE="..\..\print-tcp.c"
# End Source File
# Begin Source File

SOURCE="..\..\print-telnet.c"
# End Source File
# Begin Source File

SOURCE="..\..\print-tftp.c"
# End Source File
# Begin Source File

SOURCE="..\..\print-timed.c"
# End Source File
# Begin Source File

SOURCE="..\..\print-token.c"
# End Source File
# Begin Source File

SOURCE="..\..\print-udld.c"
# End Source File
# Begin Source File

SOURCE="..\..\print-udp.c"
# End Source File
# Begin Source File

SOURCE="..\..\print-vjc.c"
# End Source File
# Begin Source File

SOURCE="..\..\print-vqp.c"
# End Source File
# Begin Source File

SOURCE="..\..\print-vrrp.c"
# End Source File
# Begin Source File

SOURCE="..\..\print-vtp.c"
# End Source File
# Begin Source File

SOURCE="..\..\print-wb.c"
# End Source File
# Begin Source File

SOURCE="..\..\print-zephyr.c"
# End Source File
# Begin Source File

SOURCE=..\..\setsignal.c
# End Source File
# Begin Source File

SOURCE=..\..\smbutil.c
# End Source File
# Begin Source File

SOURCE=..\..\strcasecmp.c
# End Source File
# Begin Source File

SOURCE=..\..\missing\strlcat.c
# End Source File
# Begin Source File

SOURCE=..\..\missing\strlcpy.c
# End Source File
# Begin Source File

SOURCE=..\..\missing\strsep.c
# End Source File
# Begin Source File

SOURCE=..\..\Tcpdump.c
# End Source File
# Begin Source File

SOURCE=..\..\util.c
# End Source File
# End Target
# End Project
