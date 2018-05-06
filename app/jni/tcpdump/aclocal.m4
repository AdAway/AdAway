dnl Copyright (c) 1995, 1996, 1997, 1998
dnl	The Regents of the University of California.  All rights reserved.
dnl
dnl Redistribution and use in source and binary forms, with or without
dnl modification, are permitted provided that: (1) source code distributions
dnl retain the above copyright notice and this paragraph in its entirety, (2)
dnl distributions including binary code include the above copyright notice and
dnl this paragraph in its entirety in the documentation or other materials
dnl provided with the distribution, and (3) all advertising materials mentioning
dnl features or use of this software display the following acknowledgement:
dnl ``This product includes software developed by the University of California,
dnl Lawrence Berkeley Laboratory and its contributors.'' Neither the name of
dnl the University nor the names of its contributors may be used to endorse
dnl or promote products derived from this software without specific prior
dnl written permission.
dnl THIS SOFTWARE IS PROVIDED ``AS IS'' AND WITHOUT ANY EXPRESS OR IMPLIED
dnl WARRANTIES, INCLUDING, WITHOUT LIMITATION, THE IMPLIED WARRANTIES OF
dnl MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE.
dnl
dnl LBL autoconf macros
dnl

dnl
dnl Do whatever AC_LBL_C_INIT work is necessary before using AC_PROG_CC.
dnl
dnl It appears that newer versions of autoconf (2.64 and later) will,
dnl if you use AC_TRY_COMPILE in a macro, stick AC_PROG_CC at the
dnl beginning of the macro, even if the macro itself calls AC_PROG_CC.
dnl See the "Prerequisite Macros" and "Expanded Before Required" sections
dnl in the Autoconf documentation.
dnl
dnl This causes a steaming heap of fail in our case, as we were, in
dnl AC_LBL_C_INIT, doing the tests we now do in AC_LBL_C_INIT_BEFORE_CC,
dnl calling AC_PROG_CC, and then doing the tests we now do in
dnl AC_LBL_C_INIT.  Now, we run AC_LBL_C_INIT_BEFORE_CC, AC_PROG_CC,
dnl and AC_LBL_C_INIT at the top level.
dnl
AC_DEFUN(AC_LBL_C_INIT_BEFORE_CC,
[
    AC_BEFORE([$0], [AC_LBL_C_INIT])
    AC_BEFORE([$0], [AC_PROG_CC])
    AC_BEFORE([$0], [AC_LBL_FIXINCLUDES])
    AC_BEFORE([$0], [AC_LBL_DEVEL])
    AC_ARG_WITH(gcc, [  --without-gcc           don't use gcc])
    $1=""
    if test "${srcdir}" != "." ; then
	    $1="-I$srcdir"
    fi
    if test "${CFLAGS+set}" = set; then
	    LBL_CFLAGS="$CFLAGS"
    fi
    if test -z "$CC" ; then
	    case "$host_os" in

	    bsdi*)
		    AC_CHECK_PROG(SHLICC2, shlicc2, yes, no)
		    if test $SHLICC2 = yes ; then
			    CC=shlicc2
			    export CC
		    fi
		    ;;
	    esac
    fi
    if test -z "$CC" -a "$with_gcc" = no ; then
	    CC=cc
	    export CC
    fi
])

dnl
dnl Determine which compiler we're using (cc or gcc)
dnl If using gcc, determine the version number
dnl If using cc:
dnl     require that it support ansi prototypes
dnl     use -O (AC_PROG_CC will use -g -O2 on gcc, so we don't need to
dnl     do that ourselves for gcc)
dnl     add -g flags, as appropriate
dnl     explicitly specify /usr/local/include
dnl
dnl NOTE WELL: with newer versions of autoconf, "gcc" means any compiler
dnl that defines __GNUC__, which means clang, for example, counts as "gcc".
dnl
dnl usage:
dnl
dnl	AC_LBL_C_INIT(copt, incls)
dnl
dnl results:
dnl
dnl	$1 (copt set)
dnl	$2 (incls set)
dnl	CC
dnl	LDFLAGS
dnl	LBL_CFLAGS
dnl
AC_DEFUN(AC_LBL_C_INIT,
[
    AC_BEFORE([$0], [AC_LBL_FIXINCLUDES])
    AC_BEFORE([$0], [AC_LBL_DEVEL])
    AC_BEFORE([$0], [AC_LBL_SHLIBS_INIT])
    if test "$GCC" = yes ; then
	    #
	    # -Werror forces warnings to be errors.
	    #
	    ac_lbl_cc_force_warning_errors=-Werror

	    #
	    # Use -ffloat-store so that, on 32-bit x86, we don't
	    # do 80-bit arithmetic with the FPU; that way we should
	    # get the same results for floating-point calculations
	    # on x86-32 and x86-64.
	    #
	    AC_LBL_CHECK_COMPILER_OPT($1, -ffloat-store)
    else
	    $2="$$2 -I/usr/local/include"
	    LDFLAGS="$LDFLAGS -L/usr/local/lib"

	    case "$host_os" in

	    darwin*)
		    #
		    # This is assumed either to be GCC or clang, both
		    # of which use -Werror to force warnings to be errors.
		    #
		    ac_lbl_cc_force_warning_errors=-Werror
		    ;;

	    hpux*)
		    #
		    # HP C, which is what we presume we're using, doesn't
		    # exit with a non-zero exit status if we hand it an
		    # invalid -W flag, can't be forced to do so even with
		    # +We, and doesn't handle GCC-style -W flags, so we
		    # don't want to try using GCC-style -W flags.
		    #
		    ac_lbl_cc_dont_try_gcc_dashW=yes
		    ;;

	    irix*)
		    #
		    # MIPS C, which is what we presume we're using, doesn't
		    # necessarily exit with a non-zero exit status if we
		    # hand it an invalid -W flag, can't be forced to do
		    # so, and doesn't handle GCC-style -W flags, so we
		    # don't want to try using GCC-style -W flags.
		    #
		    ac_lbl_cc_dont_try_gcc_dashW=yes
		    #
		    # It also, apparently, defaults to "char" being
		    # unsigned, unlike most other C implementations;
		    # I suppose we could say "signed char" whenever
		    # we want to guarantee a signed "char", but let's
		    # just force signed chars.
		    #
		    # -xansi is normally the default, but the
		    # configure script was setting it; perhaps -cckr
		    # was the default in the Old Days.  (Then again,
		    # that would probably be for backwards compatibility
		    # in the days when ANSI C was Shiny and New, i.e.
		    # 1989 and the early '90's, so maybe we can just
		    # drop support for those compilers.)
		    #
		    # -g is equivalent to -g2, which turns off
		    # optimization; we choose -g3, which generates
		    # debugging information but doesn't turn off
		    # optimization (even if the optimization would
		    # cause inaccuracies in debugging).
		    #
		    $1="$$1 -xansi -signed -g3"
		    ;;

	    osf*)
	    	    #
		    # Presumed to be DEC OSF/1, Digital UNIX, or
		    # Tru64 UNIX.
		    #
		    # The DEC C compiler, which is what we presume we're
		    # using, doesn't exit with a non-zero exit status if we
		    # hand it an invalid -W flag, can't be forced to do
		    # so, and doesn't handle GCC-style -W flags, so we
		    # don't want to try using GCC-style -W flags.
		    #
		    ac_lbl_cc_dont_try_gcc_dashW=yes
		    #
		    # -g is equivalent to -g2, which turns off
		    # optimization; we choose -g3, which generates
		    # debugging information but doesn't turn off
		    # optimization (even if the optimization would
		    # cause inaccuracies in debugging).
		    #
		    $1="$$1 -g3"
		    ;;

	    solaris*)
		    #
		    # Assumed to be Sun C, which requires -errwarn to force
		    # warnings to be treated as errors.
		    #
		    ac_lbl_cc_force_warning_errors=-errwarn
		    ;;

	    ultrix*)
		    AC_MSG_CHECKING(that Ultrix $CC hacks const in prototypes)
		    AC_CACHE_VAL(ac_cv_lbl_cc_const_proto,
			AC_TRY_COMPILE(
			    [#include <sys/types.h>],
			    [struct a { int b; };
			    void c(const struct a *)],
			    ac_cv_lbl_cc_const_proto=yes,
			    ac_cv_lbl_cc_const_proto=no))
		    AC_MSG_RESULT($ac_cv_lbl_cc_const_proto)
		    if test $ac_cv_lbl_cc_const_proto = no ; then
			    AC_DEFINE(const,[],
			        [to handle Ultrix compilers that don't support const in prototypes])
		    fi
		    ;;
	    esac
	    $1="$$1 -O"
    fi
])

dnl
dnl Check whether the compiler option specified as the second argument
dnl is supported by the compiler and, if so, add it to the macro
dnl specified as the first argument
dnl
AC_DEFUN(AC_LBL_CHECK_COMPILER_OPT,
    [
	AC_MSG_CHECKING([whether the compiler supports the $2 option])
	save_CFLAGS="$CFLAGS"
	CFLAGS="$CFLAGS $ac_lbl_cc_force_warning_errors $2"
	AC_TRY_COMPILE(
	    [],
	    [return 0],
	    [
		AC_MSG_RESULT([yes])
		CFLAGS="$save_CFLAGS"
		$1="$$1 $2"
	    ],
	    [
		AC_MSG_RESULT([no])
		CFLAGS="$save_CFLAGS"
	    ])
    ])

dnl
dnl Check whether the compiler supports an option to generate
dnl Makefile-style dependency lines
dnl
dnl GCC uses -M for this.  Non-GCC compilers that support this
dnl use a variety of flags, including but not limited to -M.
dnl
dnl We test whether the flag in question is supported, as older
dnl versions of compilers might not support it.
dnl
dnl We don't try all the possible flags, just in case some flag means
dnl "generate dependencies" on one compiler but means something else
dnl on another compiler.
dnl
dnl Most compilers that support this send the output to the standard
dnl output by default.  IBM's XLC, however, supports -M but sends
dnl the output to {sourcefile-basename}.u, and AIX has no /dev/stdout
dnl to work around that, so we don't bother with XLC.
dnl
AC_DEFUN(AC_LBL_CHECK_DEPENDENCY_GENERATION_OPT,
    [
	AC_MSG_CHECKING([whether the compiler supports generating dependencies])
	if test "$GCC" = yes ; then
		#
		# GCC, or a compiler deemed to be GCC by AC_PROG_CC (even
		# though it's not); we assume that, in this case, the flag
		# would be -M.
		#
		ac_lbl_dependency_flag="-M"
	else
		#
		# Not GCC or a compiler deemed to be GCC; what platform is
		# this?  (We're assuming that if the compiler isn't GCC
		# it's the compiler from the vendor of the OS; that won't
		# necessarily be true for x86 platforms, where it might be
		# the Intel C compiler.)
		#
		case "$host_os" in

		irix*|osf*|darwin*)
			#
			# MIPS C for IRIX, DEC C, and clang all use -M.
			#
			ac_lbl_dependency_flag="-M"
			;;

		solaris*)
			#
			# Sun C uses -xM.
			#
			ac_lbl_dependency_flag="-xM"
			;;

		hpux*)
			#
			# HP's older C compilers don't support this.
			# HP's newer C compilers support this with
			# either +M or +Make; the older compilers
			# interpret +M as something completely
			# different, so we use +Make so we don't
			# think it works with the older compilers.
			#
			ac_lbl_dependency_flag="+Make"
			;;

		*)
			#
			# Not one of the above; assume no support for
			# generating dependencies.
			#
			ac_lbl_dependency_flag=""
			;;
		esac
	fi

	#
	# Is ac_lbl_dependency_flag defined and, if so, does the compiler
	# complain about it?
	#
	# Note: clang doesn't seem to exit with an error status when handed
	# an unknown non-warning error, even if you pass it
	# -Werror=unknown-warning-option.  However, it always supports
	# -M, so the fact that this test always succeeds with clang
	# isn't an issue.
	#
	if test ! -z "$ac_lbl_dependency_flag"; then
		AC_LANG_CONFTEST(
		    [AC_LANG_SOURCE([[int main(void) { return 0; }]])])
		echo "$CC" $ac_lbl_dependency_flag conftest.c >&5
		if "$CC" $ac_lbl_dependency_flag conftest.c >/dev/null 2>&1; then
			AC_MSG_RESULT([yes, with $ac_lbl_dependency_flag])
			DEPENDENCY_CFLAG="$ac_lbl_dependency_flag"
			MKDEP='${srcdir}/mkdep'
		else
			AC_MSG_RESULT([no])
			#
			# We can't run mkdep, so have "make depend" do
			# nothing.
			#
			MKDEP=:
		fi
		rm -rf conftest*
	else
		AC_MSG_RESULT([no])
		#
		# We can't run mkdep, so have "make depend" do
		# nothing.
		#
		MKDEP=:
	fi
	AC_SUBST(DEPENDENCY_CFLAG)
	AC_SUBST(MKDEP)
    ])

#
# Try compiling a sample of the type of code that appears in
# gencode.c with "inline", "__inline__", and "__inline".
#
# Autoconf's AC_C_INLINE, at least in autoconf 2.13, isn't good enough,
# as it just tests whether a function returning "int" can be inlined;
# at least some versions of HP's C compiler can inline that, but can't
# inline a function that returns a struct pointer.
#
# Make sure we use the V_CCOPT flags, because some of those might
# disable inlining.
#
AC_DEFUN(AC_LBL_C_INLINE,
    [AC_MSG_CHECKING(for inline)
    save_CFLAGS="$CFLAGS"
    CFLAGS="$V_CCOPT"
    AC_CACHE_VAL(ac_cv_lbl_inline, [
	ac_cv_lbl_inline=""
	ac_lbl_cc_inline=no
	for ac_lbl_inline in inline __inline__ __inline
	do
	    AC_TRY_COMPILE(
		[#define inline $ac_lbl_inline
		static inline struct iltest *foo(void);
		struct iltest {
		    int iltest1;
		    int iltest2;
		};

		static inline struct iltest *
		foo()
		{
		    static struct iltest xxx;

		    return &xxx;
		}],,ac_lbl_cc_inline=yes,)
	    if test "$ac_lbl_cc_inline" = yes ; then
		break;
	    fi
	done
	if test "$ac_lbl_cc_inline" = yes ; then
	    ac_cv_lbl_inline=$ac_lbl_inline
	fi])
    CFLAGS="$save_CFLAGS"
    if test ! -z "$ac_cv_lbl_inline" ; then
	AC_MSG_RESULT($ac_cv_lbl_inline)
    else
	AC_MSG_RESULT(no)
    fi
    AC_DEFINE_UNQUOTED(inline, $ac_cv_lbl_inline, [Define as token for inline if inlining supported])])

dnl
dnl Use pfopen.c if available and pfopen() not in standard libraries
dnl Require libpcap
dnl Look for libpcap in ..
dnl Use the installed libpcap if there is no local version
dnl
dnl usage:
dnl
dnl	AC_LBL_LIBPCAP(pcapdep, incls)
dnl
dnl results:
dnl
dnl	$1 (pcapdep set)
dnl	$2 (incls appended)
dnl	LIBS
dnl	LBL_LIBS
dnl
AC_DEFUN(AC_LBL_LIBPCAP,
    [AC_REQUIRE([AC_LBL_LIBRARY_NET])
    dnl
    dnl save a copy before locating libpcap.a
    dnl
    LBL_LIBS="$LIBS"
    pfopen=/usr/examples/packetfilter/pfopen.c
    if test -f $pfopen ; then
	    AC_CHECK_FUNCS(pfopen)
	    if test $ac_cv_func_pfopen = "no" ; then
		    AC_MSG_RESULT(Using $pfopen)
		    LIBS="$LIBS $pfopen"
	    fi
    fi
	libpcap=FAIL
	AC_MSG_CHECKING(for local pcap library)
	AC_ARG_WITH([system-libpcap],
		[AS_HELP_STRING([--with-system-libpcap], [don't use local pcap library])])
	if test "x$with_system_libpcap" != xyes ; then
		lastdir=FAIL
    	places=`ls $srcdir/.. | sed -e 's,/$,,' -e "s,^,$srcdir/../," | \
		egrep '/libpcap-[[0-9]]+\.[[0-9]]+(\.[[0-9]]*)?([[ab]][[0-9]]*|-PRE-GIT)?$'`
    	places2=`ls .. | sed -e 's,/$,,' -e "s,^,../," | \
		egrep '/libpcap-[[0-9]]+\.[[0-9]]+(\.[[0-9]]*)?([[ab]][[0-9]]*|-PRE-GIT)?$'`
    	for dir in $places $srcdir/../libpcap ../libpcap $srcdir/libpcap $places2 ; do
	    	basedir=`echo $dir | sed -e 's/[[ab]][[0-9]]*$//' | \
	        	sed -e 's/-PRE-GIT$//' `
	    	if test $lastdir = $basedir ; then
		    	dnl skip alphas when an actual release is present
		    	continue;
	    	fi
	    	lastdir=$dir
	    	if test -r $dir/libpcap.a ; then
		    	libpcap=$dir/libpcap.a
		    	d=$dir
		    	dnl continue and select the last one that exists
	    	fi
		done
	fi
    if test $libpcap = FAIL ; then
	    AC_MSG_RESULT(not found)

	    #
	    # Look for pcap-config.
	    #
	    AC_PATH_TOOL(PCAP_CONFIG, pcap-config)
	    if test -n "$PCAP_CONFIG" ; then
		#
		# Found - use it to get the include flags for
		# libpcap and the flags to link with libpcap.
		#
		# Please read section 11.6 "Shell Substitutions"
		# in the autoconf manual before doing anything
		# to this that involves quoting.  Especially note
		# the statement "There is just no portable way to use
		# double-quoted strings inside double-quoted back-quoted
		# expressions (pfew!)."
		#
		cflags=`"$PCAP_CONFIG" --cflags`
		$2="$cflags $$2"
		libpcap=`"$PCAP_CONFIG" --libs`
	    else
		#
		# Not found; look for pcap.
		#
		AC_CHECK_LIB(pcap, main, libpcap="-lpcap")
		if test $libpcap = FAIL ; then
		    AC_MSG_ERROR(see the INSTALL doc for more info)
		fi
		dnl
		dnl Some versions of Red Hat Linux put "pcap.h" in
		dnl "/usr/include/pcap"; had the LBL folks done so,
		dnl that would have been a good idea, but for
		dnl the Red Hat folks to do so just breaks source
		dnl compatibility with other systems.
		dnl
		dnl We work around this by assuming that, as we didn't
		dnl find a local libpcap, libpcap is in /usr/lib or
		dnl /usr/local/lib and that the corresponding header
		dnl file is under one of those directories; if we don't
		dnl find it in either of those directories, we check to
		dnl see if it's in a "pcap" subdirectory of them and,
		dnl if so, add that subdirectory to the "-I" list.
		dnl
		dnl (We now also put pcap.h in /usr/include/pcap, but we
		dnl leave behind a /usr/include/pcap.h that includes it,
		dnl so you can still just include <pcap.h>.)
		dnl
		AC_MSG_CHECKING(for extraneous pcap header directories)
		if test \( ! -r /usr/local/include/pcap.h \) -a \
			\( ! -r /usr/include/pcap.h \); then
		    if test -r /usr/local/include/pcap/pcap.h; then
			d="/usr/local/include/pcap"
		    elif test -r /usr/include/pcap/pcap.h; then
			d="/usr/include/pcap"
		    fi
		fi
		if test -z "$d" ; then
		    AC_MSG_RESULT(not found)
		else
		    $2="-I$d $$2"
		    AC_MSG_RESULT(found -- -I$d added)
		fi
	    fi
    else
	    $1=$libpcap
	    places=`ls $srcdir/.. | sed -e 's,/$,,' -e "s,^,$srcdir/../," | \
    	 		egrep '/libpcap-[[0-9]]*.[[0-9]]*(.[[0-9]]*)?([[ab]][[0-9]]*)?$'`
	    places2=`ls .. | sed -e 's,/$,,' -e "s,^,../," | \
    	 		egrep '/libpcap-[[0-9]]*.[[0-9]]*(.[[0-9]]*)?([[ab]][[0-9]]*)?$'`
            pcapH=FAIL
	    if test -r $d/pcap.h; then
                    pcapH=$d
	    else
                for dir in $places $srcdir/../libpcap ../libpcap $srcdir/libpcap $places2 ; do
                   if test -r $dir/pcap.h ; then
                       pcapH=$dir
                   fi
                done
            fi

            if test $pcapH = FAIL ; then
                    AC_MSG_ERROR(cannot find pcap.h: see INSTALL)
 	    fi
            $2="-I$pcapH $$2"
	    AC_MSG_RESULT($libpcap)
	    AC_PATH_PROG(PCAP_CONFIG, pcap-config,, $d)
	    if test -n "$PCAP_CONFIG"; then
		#
		# The libpcap directory has a pcap-config script.
		# Use it to get any additioal libraries needed
		# to link with the libpcap archive library in
		# that directory.
		#
		# Please read section 11.6 "Shell Substitutions"
		# in the autoconf manual before doing anything
		# to this that involves quoting.  Especially note
		# the statement "There is just no portable way to use
		# double-quoted strings inside double-quoted back-quoted
		# expressions (pfew!)."
		#
		additional_libs=`"$PCAP_CONFIG" --additional-libs --static`
		libpcap="$libpcap $additional_libs"
	    fi
    fi
    LIBS="$libpcap $LIBS"
    if ! test -n "$PCAP_CONFIG" ; then
	#
	# We don't have pcap-config; find out any additional link flags
	# we need.  (If we have pcap-config, we assume it tells us what
	# we need.)
	#
	case "$host_os" in

	aix*)
	    #
	    # If libpcap is DLPI-based, we have to use /lib/pse.exp if
	    # present, as we use the STREAMS routines.
	    #
	    # (XXX - true only if we're linking with a static libpcap?)
	    #
	    pseexe="/lib/pse.exp"
	    AC_MSG_CHECKING(for $pseexe)
	    if test -f $pseexe ; then
		    AC_MSG_RESULT(yes)
		    LIBS="$LIBS -I:$pseexe"
	    fi

	    #
	    # If libpcap is BPF-based, we need "-lodm" and "-lcfg", as
	    # we use them to load the BPF module.
	    #
	    # (XXX - true only if we're linking with a static libpcap?)
	    #
	    LIBS="$LIBS -lodm -lcfg"
	    ;;
	esac
    fi

    dnl
    dnl Check for "pcap_loop()", to make sure we found a working
    dnl libpcap and have all the right other libraries with which
    dnl to link.  (Otherwise, the checks below will fail, not
    dnl because the routines are missing from the library, but
    dnl because we aren't linking properly with libpcap, and
    dnl that will cause confusing errors at build time.)
    dnl
    AC_CHECK_FUNC(pcap_loop,,
	[
	    AC_MSG_ERROR(
[Report this to tcpdump-workers@lists.tcpdump.org, and include the
config.log file in your report.  If you have downloaded libpcap from
tcpdump.org, and built it yourself, please also include the config.log
file from the libpcap source directory, the Makefile from the libpcap
source directory, and the output of the make process for libpcap, as
this could be a problem with the libpcap that was built, and we will
not be able to determine why this is happening, and thus will not be
able to fix it, without that information, as we have not been able to
reproduce this problem ourselves.])
	])
])

dnl
dnl Define RETSIGTYPE and RETSIGVAL
dnl
dnl usage:
dnl
dnl	AC_LBL_TYPE_SIGNAL
dnl
dnl results:
dnl
dnl	RETSIGTYPE (defined)
dnl	RETSIGVAL (defined)
dnl
AC_DEFUN(AC_LBL_TYPE_SIGNAL,
    [AC_BEFORE([$0], [AC_LBL_LIBPCAP])
    AC_TYPE_SIGNAL
    if test "$ac_cv_type_signal" = void ; then
	    AC_DEFINE(RETSIGVAL,[],[return value of signal handlers])
    else
	    AC_DEFINE(RETSIGVAL,(0),[return value of signal handlers])
    fi
    case "$host_os" in

    irix*)
	    AC_DEFINE(_BSD_SIGNALS,1,[get BSD semantics on Irix])
	    ;;

    *)
	    dnl prefer sigaction() to sigset()
	    AC_CHECK_FUNCS(sigaction)
	    if test $ac_cv_func_sigaction = no ; then
		    AC_CHECK_FUNCS(sigset)
	    fi
	    ;;
    esac])

dnl
dnl If using gcc, make sure we have ANSI ioctl definitions
dnl
dnl usage:
dnl
dnl	AC_LBL_FIXINCLUDES
dnl
AC_DEFUN(AC_LBL_FIXINCLUDES,
    [if test "$GCC" = yes ; then
	    AC_MSG_CHECKING(for ANSI ioctl definitions)
	    AC_CACHE_VAL(ac_cv_lbl_gcc_fixincludes,
		AC_TRY_COMPILE(
		    [/*
		     * This generates a "duplicate case value" when fixincludes
		     * has not be run.
		     */
#		include <sys/types.h>
#		include <sys/time.h>
#		include <sys/ioctl.h>
#		ifdef HAVE_SYS_IOCCOM_H
#		include <sys/ioccom.h>
#		endif],
		    [switch (0) {
		    case _IO('A', 1):;
		    case _IO('B', 1):;
		    }],
		    ac_cv_lbl_gcc_fixincludes=yes,
		    ac_cv_lbl_gcc_fixincludes=no))
	    AC_MSG_RESULT($ac_cv_lbl_gcc_fixincludes)
	    if test $ac_cv_lbl_gcc_fixincludes = no ; then
		    # Don't cache failure
		    unset ac_cv_lbl_gcc_fixincludes
		    AC_MSG_ERROR(see the INSTALL for more info)
	    fi
    fi])

dnl
dnl Checks to see if union wait is used with WEXITSTATUS()
dnl
dnl usage:
dnl
dnl	AC_LBL_UNION_WAIT
dnl
dnl results:
dnl
dnl	DECLWAITSTATUS (defined)
dnl
AC_DEFUN(AC_LBL_UNION_WAIT,
    [AC_MSG_CHECKING(if union wait is used)
    AC_CACHE_VAL(ac_cv_lbl_union_wait,
	AC_TRY_COMPILE([
#	include <sys/types.h>
#	include <sys/wait.h>],
	    [int status;
	    u_int i = WEXITSTATUS(status);
	    u_int j = waitpid(0, &status, 0);],
	    ac_cv_lbl_union_wait=no,
	    ac_cv_lbl_union_wait=yes))
    AC_MSG_RESULT($ac_cv_lbl_union_wait)
    if test $ac_cv_lbl_union_wait = yes ; then
	    AC_DEFINE(DECLWAITSTATUS,union wait,[type for wait])
    else
	    AC_DEFINE(DECLWAITSTATUS,int,[type for wait])
    fi])

dnl
dnl Checks to see if the sockaddr struct has the 4.4 BSD sa_len member
dnl
dnl usage:
dnl
dnl	AC_LBL_SOCKADDR_SA_LEN
dnl
dnl results:
dnl
dnl	HAVE_SOCKADDR_SA_LEN (defined)
dnl
AC_DEFUN(AC_LBL_SOCKADDR_SA_LEN,
    [AC_MSG_CHECKING(if sockaddr struct has the sa_len member)
    AC_CACHE_VAL(ac_cv_lbl_sockaddr_has_sa_len,
	AC_TRY_COMPILE([
#	include <sys/types.h>
#	include <sys/socket.h>],
	[u_int i = sizeof(((struct sockaddr *)0)->sa_len)],
	ac_cv_lbl_sockaddr_has_sa_len=yes,
	ac_cv_lbl_sockaddr_has_sa_len=no))
    AC_MSG_RESULT($ac_cv_lbl_sockaddr_has_sa_len)
    if test $ac_cv_lbl_sockaddr_has_sa_len = yes ; then
	    AC_DEFINE(HAVE_SOCKADDR_SA_LEN,1,[if struct sockaddr has the sa_len member])
    fi])

dnl
dnl Checks to see if -R is used
dnl
dnl usage:
dnl
dnl	AC_LBL_HAVE_RUN_PATH
dnl
dnl results:
dnl
dnl	ac_cv_lbl_have_run_path (yes or no)
dnl
AC_DEFUN(AC_LBL_HAVE_RUN_PATH,
    [AC_MSG_CHECKING(for ${CC-cc} -R)
    AC_CACHE_VAL(ac_cv_lbl_have_run_path,
	[echo 'main(){}' > conftest.c
	${CC-cc} -o conftest conftest.c -R/a1/b2/c3 >conftest.out 2>&1
	if test ! -s conftest.out ; then
		ac_cv_lbl_have_run_path=yes
	else
		ac_cv_lbl_have_run_path=no
	fi
	rm -f -r conftest*])
    AC_MSG_RESULT($ac_cv_lbl_have_run_path)
    ])

dnl
dnl Check whether a given format can be used to print 64-bit integers
dnl
AC_DEFUN(AC_LBL_CHECK_64BIT_FORMAT,
  [
    AC_MSG_CHECKING([whether %$1x can be used to format 64-bit integers])
    AC_RUN_IFELSE(
      [
	AC_LANG_SOURCE(
	  [[
#	    ifdef HAVE_INTTYPES_H
	    #include <inttypes.h>
#	    endif
	    #include <stdio.h>
	    #include <sys/types.h>

	    main()
	    {
	      uint64_t t = 1;
	      char strbuf[16+1];
	      sprintf(strbuf, "%016$1x", t << 32);
	      if (strcmp(strbuf, "0000000100000000") == 0)
		exit(0);
	      else
		exit(1);
	    }
	  ]])
      ],
      [
	AC_DEFINE(PRId64, "$1d", [define if the platform doesn't define PRId64])
	AC_DEFINE(PRIo64, "$1o", [define if the platform doesn't define PRIo64])
	AC_DEFINE(PRIx64, "$1x", [define if the platform doesn't define PRIu64])
	AC_DEFINE(PRIu64, "$1u", [define if the platform doesn't define PRIx64])
	AC_MSG_RESULT(yes)
      ],
      [
	AC_MSG_RESULT(no)
	$2
      ])
  ])

dnl
dnl Checks to see if unaligned memory accesses fail
dnl
dnl usage:
dnl
dnl	AC_LBL_UNALIGNED_ACCESS
dnl
dnl results:
dnl
dnl	LBL_ALIGN (DEFINED)
dnl
AC_DEFUN(AC_LBL_UNALIGNED_ACCESS,
    [AC_MSG_CHECKING(if unaligned accesses fail)
    AC_CACHE_VAL(ac_cv_lbl_unaligned_fail,
	[case "$host_cpu" in

	#
	# These are CPU types where:
	#
	#	the CPU faults on an unaligned access, but at least some
	#	OSes that support that CPU catch the fault and simulate
	#	the unaligned access (e.g., Alpha/{Digital,Tru64} UNIX) -
	#	the simulation is slow, so we don't want to use it;
	#
	#	the CPU, I infer (from the old
	#
	# XXX: should also check that they don't do weird things (like on arm)
	#
	#	comment) doesn't fault on unaligned accesses, but doesn't
	#	do a normal unaligned fetch, either (e.g., presumably, ARM);
	#
	#	for whatever reason, the test program doesn't work
	#	(this has been claimed to be the case for several of those
	#	CPUs - I don't know what the problem is; the problem
	#	was reported as "the test program dumps core" for SuperH,
	#	but that's what the test program is *supposed* to do -
	#	it dumps core before it writes anything, so the test
	#	for an empty output file should find an empty output
	#	file and conclude that unaligned accesses don't work).
	#
	# This run-time test won't work if you're cross-compiling, so
	# in order to support cross-compiling for a particular CPU,
	# we have to wire in the list of CPU types anyway, as far as
	# I know, so perhaps we should just have a set of CPUs on
	# which we know it doesn't work, a set of CPUs on which we
	# know it does work, and have the script just fail on other
	# cpu types and update it when such a failure occurs.
	#
	alpha*|arm*|bfin*|hp*|mips*|sh*|sparc*|ia64|nv1)
		ac_cv_lbl_unaligned_fail=yes
		;;

	*)
		cat >conftest.c <<EOF
#		include <sys/types.h>
#		include <sys/wait.h>
#		include <stdio.h>
		unsigned char a[[5]] = { 1, 2, 3, 4, 5 };
		main() {
		unsigned int i;
		pid_t pid;
		int status;
		/* avoid "core dumped" message */
		pid = fork();
		if (pid <  0)
			exit(2);
		if (pid > 0) {
			/* parent */
			pid = waitpid(pid, &status, 0);
			if (pid < 0)
				exit(3);
			exit(!WIFEXITED(status));
		}
		/* child */
		i = *(unsigned int *)&a[[1]];
		printf("%d\n", i);
		exit(0);
		}
EOF
		${CC-cc} -o conftest $CFLAGS $CPPFLAGS $LDFLAGS \
		    conftest.c $LIBS >/dev/null 2>&1
		if test ! -x conftest ; then
			dnl failed to compile for some reason
			ac_cv_lbl_unaligned_fail=yes
		else
			./conftest >conftest.out
			if test ! -s conftest.out ; then
				ac_cv_lbl_unaligned_fail=yes
			else
				ac_cv_lbl_unaligned_fail=no
			fi
		fi
		rm -f -r conftest* core core.conftest
		;;
	esac])
    AC_MSG_RESULT($ac_cv_lbl_unaligned_fail)
    if test $ac_cv_lbl_unaligned_fail = yes ; then
	    AC_DEFINE(LBL_ALIGN,1,[if unaligned access fails])
    fi])

dnl
dnl If the file .devel exists:
dnl	Add some warning flags if the compiler supports them
dnl	If an os prototype include exists, symlink os-proto.h to it
dnl
dnl usage:
dnl
dnl	AC_LBL_DEVEL(copt)
dnl
dnl results:
dnl
dnl	$1 (copt appended)
dnl	HAVE_OS_PROTO_H (defined)
dnl	os-proto.h (symlinked)
dnl
AC_DEFUN(AC_LBL_DEVEL,
    [rm -f os-proto.h
    if test "${LBL_CFLAGS+set}" = set; then
	    $1="$$1 ${LBL_CFLAGS}"
    fi
    if test -f .devel ; then
	    #
	    # Skip all the warning option stuff on some compilers.
	    #
	    if test "$ac_lbl_cc_dont_try_gcc_dashW" != yes; then
		    AC_LBL_CHECK_COMPILER_OPT($1, -Wall)
		    AC_LBL_CHECK_COMPILER_OPT($1, -Wmissing-prototypes)
		    AC_LBL_CHECK_COMPILER_OPT($1, -Wstrict-prototypes)
		    AC_LBL_CHECK_COMPILER_OPT($1, -Wwrite-strings)
		    AC_LBL_CHECK_COMPILER_OPT($1, -Wpointer-arith)
		    AC_LBL_CHECK_COMPILER_OPT($1, -W)
	    fi
	    AC_LBL_CHECK_DEPENDENCY_GENERATION_OPT()
	    #
	    # We used to set -n32 for IRIX 6 when not using GCC (presumed
	    # to mean that we're using MIPS C or MIPSpro C); it specified
	    # the "new" faster 32-bit ABI, introduced in IRIX 6.2.  I'm
	    # not sure why that would be something to do *only* with a
	    # .devel file; why should the ABI for which we produce code
	    # depend on .devel?
	    #
	    os=`echo $host_os | sed -e 's/\([[0-9]][[0-9]]*\)[[^0-9]].*$/\1/'`
	    name="lbl/os-$os.h"
	    if test -f $name ; then
		    ln -s $name os-proto.h
		    AC_DEFINE(HAVE_OS_PROTO_H, 1,
			[if there's an os_proto.h for this platform, to use additional prototypes])
	    else
		    AC_MSG_WARN(can't find $name)
	    fi
    fi])

dnl
dnl Improved version of AC_CHECK_LIB
dnl
dnl Thanks to John Hawkinson (jhawk@mit.edu)
dnl
dnl usage:
dnl
dnl	AC_LBL_CHECK_LIB(LIBRARY, FUNCTION [, ACTION-IF-FOUND [,
dnl	    ACTION-IF-NOT-FOUND [, OTHER-LIBRARIES]]])
dnl
dnl results:
dnl
dnl	LIBS
dnl
dnl XXX - "AC_LBL_LIBRARY_NET" was redone to use "AC_SEARCH_LIBS"
dnl rather than "AC_LBL_CHECK_LIB", so this isn't used any more.
dnl We keep it around for reference purposes in case it's ever
dnl useful in the future.
dnl

define(AC_LBL_CHECK_LIB,
[AC_MSG_CHECKING([for $2 in -l$1])
dnl Use a cache variable name containing the library, function
dnl name, and extra libraries to link with, because the test really is
dnl for library $1 defining function $2, when linked with potinal
dnl library $5, not just for library $1.  Separate tests with the same
dnl $1 and different $2's or $5's may have different results.
ac_lib_var=`echo $1['_']$2['_']$5 | sed 'y%./+- %__p__%'`
AC_CACHE_VAL(ac_cv_lbl_lib_$ac_lib_var,
[ac_save_LIBS="$LIBS"
LIBS="-l$1 $5 $LIBS"
AC_TRY_LINK(dnl
ifelse([$2], [main], , dnl Avoid conflicting decl of main.
[/* Override any gcc2 internal prototype to avoid an error.  */
]ifelse(AC_LANG, CPLUSPLUS, [#ifdef __cplusplus
extern "C"
#endif
])dnl
[/* We use char because int might match the return type of a gcc2
    builtin and then its argument prototype would still apply.  */
char $2();
]),
	    [$2()],
	    eval "ac_cv_lbl_lib_$ac_lib_var=yes",
	    eval "ac_cv_lbl_lib_$ac_lib_var=no")
LIBS="$ac_save_LIBS"
])dnl
if eval "test \"`echo '$ac_cv_lbl_lib_'$ac_lib_var`\" = yes"; then
  AC_MSG_RESULT(yes)
  ifelse([$3], ,
[changequote(, )dnl
  ac_tr_lib=HAVE_LIB`echo $1 | sed -e 's/[^a-zA-Z0-9_]/_/g' \
    -e 'y/abcdefghijklmnopqrstuvwxyz/ABCDEFGHIJKLMNOPQRSTUVWXYZ/'`
changequote([, ])dnl
  AC_DEFINE_UNQUOTED($ac_tr_lib)
  LIBS="-l$1 $LIBS"
], [$3])
else
  AC_MSG_RESULT(no)
ifelse([$4], , , [$4
])dnl
fi
])

dnl
dnl AC_LBL_LIBRARY_NET
dnl
dnl This test is for network applications that need socket() and
dnl gethostbyname() -ish functions.  Under Solaris, those applications
dnl need to link with "-lsocket -lnsl".  Under IRIX, they need to link
dnl with "-lnsl" but should *not* link with "-lsocket" because
dnl libsocket.a breaks a number of things (for instance:
dnl gethostbyname() under IRIX 5.2, and snoop sockets under most
dnl versions of IRIX).
dnl
dnl Unfortunately, many application developers are not aware of this,
dnl and mistakenly write tests that cause -lsocket to be used under
dnl IRIX.  It is also easy to write tests that cause -lnsl to be used
dnl under operating systems where neither are necessary (or useful),
dnl such as SunOS 4.1.4, which uses -lnsl for TLI.
dnl
dnl This test exists so that every application developer does not test
dnl this in a different, and subtly broken fashion.

dnl It has been argued that this test should be broken up into two
dnl seperate tests, one for the resolver libraries, and one for the
dnl libraries necessary for using Sockets API. Unfortunately, the two
dnl are carefully intertwined and allowing the autoconf user to use
dnl them independantly potentially results in unfortunate ordering
dnl dependancies -- as such, such component macros would have to
dnl carefully use indirection and be aware if the other components were
dnl executed. Since other autoconf macros do not go to this trouble,
dnl and almost no applications use sockets without the resolver, this
dnl complexity has not been implemented.
dnl
dnl The check for libresolv is in case you are attempting to link
dnl statically and happen to have a libresolv.a lying around (and no
dnl libnsl.a).
dnl
AC_DEFUN(AC_LBL_LIBRARY_NET, [
    # Most operating systems have gethostbyname() in the default searched
    # libraries (i.e. libc):
    # Some OSes (eg. Solaris) place it in libnsl
    # Some strange OSes (SINIX) have it in libsocket:
    AC_SEARCH_LIBS(gethostbyname, nsl socket resolv)
    # Unfortunately libsocket sometimes depends on libnsl and
    # AC_SEARCH_LIBS isn't up to the task of handling dependencies like this.
    if test "$ac_cv_search_gethostbyname" = "no"
    then
	AC_CHECK_LIB(socket, gethostbyname,
                     LIBS="-lsocket -lnsl $LIBS", , -lnsl)
    fi
    AC_SEARCH_LIBS(socket, socket, ,
	AC_CHECK_LIB(socket, socket, LIBS="-lsocket -lnsl $LIBS", , -lnsl))
    # DLPI needs putmsg under HPUX so test for -lstr while we're at it
    AC_SEARCH_LIBS(putmsg, str)
    ])

dnl Copyright (c) 1999 WIDE Project. All rights reserved.
dnl
dnl Redistribution and use in source and binary forms, with or without
dnl modification, are permitted provided that the following conditions
dnl are met:
dnl 1. Redistributions of source code must retain the above copyright
dnl    notice, this list of conditions and the following disclaimer.
dnl 2. Redistributions in binary form must reproduce the above copyright
dnl    notice, this list of conditions and the following disclaimer in the
dnl    documentation and/or other materials provided with the distribution.
dnl 3. Neither the name of the project nor the names of its contributors
dnl    may be used to endorse or promote products derived from this software
dnl    without specific prior written permission.
dnl
dnl THIS SOFTWARE IS PROVIDED BY THE PROJECT AND CONTRIBUTORS ``AS IS'' AND
dnl ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
dnl IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
dnl ARE DISCLAIMED.  IN NO EVENT SHALL THE PROJECT OR CONTRIBUTORS BE LIABLE
dnl FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
dnl DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS
dnl OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION)
dnl HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT
dnl LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY
dnl OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF
dnl SUCH DAMAGE.

dnl
dnl Checks to see if AF_INET6 is defined
AC_DEFUN(AC_CHECK_AF_INET6, [
	AC_MSG_CHECKING(for AF_INET6)
	AC_CACHE_VAL($1,
	AC_TRY_COMPILE([
#		include <sys/types.h>
#		include <sys/socket.h>],
		[int a = AF_INET6],
		$1=yes,
		$1=no))
	AC_MSG_RESULT($$1)
		if test $$1 = yes ; then
			AC_DEFINE(HAVE_AF_INET6)
	fi
])

dnl
dnl Checks to see if the sockaddr struct has the 4.4 BSD sa_len member
dnl borrowed from LBL libpcap
AC_DEFUN(AC_CHECK_SA_LEN, [
	AC_MSG_CHECKING(if sockaddr struct has sa_len member)
	AC_CACHE_VAL($1,
	AC_TRY_COMPILE([
#		include <sys/types.h>
#		include <sys/socket.h>],
		[u_int i = sizeof(((struct sockaddr *)0)->sa_len)],
		$1=yes,
		$1=no))
	AC_MSG_RESULT($$1)
		if test $$1 = yes ; then
			AC_DEFINE(HAVE_SOCKADDR_SA_LEN)
	fi
])

dnl
dnl Checks for addrinfo structure
AC_DEFUN(AC_STRUCT_ADDRINFO, [
	AC_MSG_CHECKING(for addrinfo)
	AC_CACHE_VAL($1,
	AC_TRY_COMPILE([
#		include <netdb.h>],
		[struct addrinfo a],
		$1=yes,
		$1=no))
	AC_MSG_RESULT($$1)
	if test $$1 = yes; then
		AC_DEFINE(HAVE_ADDRINFO, 1,
		    [define if you have the addrinfo function])
	else
		AC_DEFINE(NEED_ADDRINFO_H, 1,
		    [define if you need to include missing/addrinfo.h])
	fi
])

dnl
dnl Checks for NI_MAXSERV
AC_DEFUN(AC_NI_MAXSERV, [
	AC_MSG_CHECKING(for NI_MAXSERV)
	AC_CACHE_VAL($1,
	AC_EGREP_CPP(yes, [#include <netdb.h>
#ifdef NI_MAXSERV
yes
#endif],
		$1=yes,
		$1=no))
	AC_MSG_RESULT($$1)
	if test $$1 != yes; then
		AC_DEFINE(NEED_ADDRINFO_H)
	fi
])

dnl
dnl Checks for NI_NAMEREQD
AC_DEFUN(AC_NI_NAMEREQD, [
	AC_MSG_CHECKING(for NI_NAMEREQD)
	AC_CACHE_VAL($1,
	AC_EGREP_CPP(yes, [#include <netdb.h>
#ifdef NI_NOFQDN
yes
#endif],
		$1=yes,
		$1=no))
	AC_MSG_RESULT($$1)
	if test $$1 != yes; then
		AC_DEFINE(NEED_ADDRINFO_H)
	fi
])

dnl
dnl Checks for sockaddr_storage structure
AC_DEFUN(AC_STRUCT_SA_STORAGE, [
	AC_MSG_CHECKING(for sockaddr_storage)
	AC_CACHE_VAL($1,
	AC_TRY_COMPILE([
#		include <sys/types.h>
#		include <sys/socket.h>],
		[struct sockaddr_storage s],
		$1=yes,
		$1=no))
	AC_MSG_RESULT($$1)
	if test $$1 = yes; then
		AC_DEFINE(HAVE_SOCKADDR_STORAGE, 1,
		    [define if you have struct sockaddr_storage])
	fi
])

dnl
dnl check for h_errno
AC_DEFUN(AC_VAR_H_ERRNO, [
	AC_MSG_CHECKING(for h_errno)
	AC_CACHE_VAL(ac_cv_var_h_errno,
	AC_TRY_COMPILE([
#		include <sys/types.h>
#		include <netdb.h>],
		[int foo = h_errno;],
		ac_cv_var_h_errno=yes,
		ac_cv_var_h_errno=no))
	AC_MSG_RESULT($ac_cv_var_h_errno)
	if test "$ac_cv_var_h_errno" = "yes"; then
		AC_DEFINE(HAVE_H_ERRNO, 1,
		    [define if you have the h_errno variable])
	fi
])

dnl
dnl Test for __attribute__
dnl

AC_DEFUN(AC_C___ATTRIBUTE__, [
AC_MSG_CHECKING(for __attribute__)
AC_CACHE_VAL(ac_cv___attribute__, [
AC_COMPILE_IFELSE([
  AC_LANG_SOURCE([[
#include <stdlib.h>

static void foo(void) __attribute__ ((noreturn));

static void
foo(void)
{
  exit(1);
}

int
main(int argc, char **argv)
{
  foo();
}
  ]])],
ac_cv___attribute__=yes,
ac_cv___attribute__=no)])
if test "$ac_cv___attribute__" = "yes"; then
  AC_DEFINE(HAVE___ATTRIBUTE__, 1, [define if your compiler has __attribute__])
else
  #
  # We can't use __attribute__, so we can't use __attribute__((unused)),
  # so we define _U_ to an empty string.
  #
  V_DEFS="$V_DEFS -D_U_=\"\""
fi
AC_MSG_RESULT($ac_cv___attribute__)
])


dnl
dnl Test whether __attribute__((unused)) can be used without warnings
dnl

AC_DEFUN(AC_C___ATTRIBUTE___UNUSED, [
AC_MSG_CHECKING([whether __attribute__((unused)) can be used without warnings])
AC_CACHE_VAL(ac_cv___attribute___unused, [
save_CFLAGS="$CFLAGS"
CFLAGS="$CFLAGS $ac_lbl_cc_force_warning_errors"
AC_COMPILE_IFELSE([
  AC_LANG_SOURCE([[
#include <stdlib.h>
#include <stdio.h>

int
main(int argc  __attribute((unused)), char **argv __attribute((unused)))
{
  printf("Hello, world!\n");
  return 0;
}
  ]])],
ac_cv___attribute___unused=yes,
ac_cv___attribute___unused=no)])
CFLAGS="$save_CFLAGS"
if test "$ac_cv___attribute___unused" = "yes"; then
  V_DEFS="$V_DEFS -D_U_=\"__attribute__((unused))\""
else
  V_DEFS="$V_DEFS -D_U_=\"\""
fi
AC_MSG_RESULT($ac_cv___attribute___unused)
])

dnl
dnl Test whether __attribute__((format)) can be used without warnings
dnl

AC_DEFUN(AC_C___ATTRIBUTE___FORMAT, [
AC_MSG_CHECKING([whether __attribute__((format)) can be used without warnings])
AC_CACHE_VAL(ac_cv___attribute___format, [
save_CFLAGS="$CFLAGS"
CFLAGS="$CFLAGS $ac_lbl_cc_force_warning_errors"
AC_COMPILE_IFELSE([
  AC_LANG_SOURCE([[
#include <stdlib.h>

extern int foo(const char *fmt, ...)
		  __attribute__ ((format (printf, 1, 2)));

int
main(int argc, char **argv)
{
  foo("%s", "test");
}
  ]])],
ac_cv___attribute___format=yes,
ac_cv___attribute___format=no)])
CFLAGS="$save_CFLAGS"
if test "$ac_cv___attribute___format" = "yes"; then
  AC_DEFINE(__ATTRIBUTE___FORMAT_OK, 1,
    [define if your compiler allows __attribute__((format)) without a warning])
fi
AC_MSG_RESULT($ac_cv___attribute___format)
])

dnl
dnl Test whether __attribute__((format)) can be applied to function
dnl pointers
dnl

AC_DEFUN(AC_C___ATTRIBUTE___FORMAT_FUNCTION_POINTER, [
AC_MSG_CHECKING([whether __attribute__((format)) can be applied to function pointers])
AC_CACHE_VAL(ac_cv___attribute___format_function_pointer, [
AC_COMPILE_IFELSE([
  AC_LANG_SOURCE([[
#include <stdlib.h>

extern int (*foo)(const char *fmt, ...)
		  __attribute__ ((format (printf, 1, 2)));

int
main(int argc, char **argv)
{
  (*foo)("%s", "test");
}
  ]])],
ac_cv___attribute___format_function_pointer=yes,
ac_cv___attribute___format_function_pointer=no)])
if test "$ac_cv___attribute___format_function_pointer" = "yes"; then
  AC_DEFINE(__ATTRIBUTE___FORMAT_OK_FOR_FUNCTION_POINTERS, 1,
    [define if your compiler allows __attribute__((format)) to be applied to function pointers])
fi
AC_MSG_RESULT($ac_cv___attribute___format_function_pointer)
])

AC_DEFUN(AC_C___ATTRIBUTE___NORETURN_FUNCTION_POINTER, [
AC_MSG_CHECKING([whether __attribute__((noreturn)) can be applied to function pointers without warnings])
AC_CACHE_VAL(ac_cv___attribute___noreturn_function_pointer, [
save_CFLAGS="$CFLAGS"
CFLAGS="$CFLAGS $ac_lbl_cc_force_warning_errors"
AC_COMPILE_IFELSE([
  AC_LANG_SOURCE([[
#include <stdlib.h>

extern int (*foo)(int i)
		  __attribute__ ((noreturn));

int
main(int argc, char **argv)
{
  (*foo)(1);
}
  ]])],
ac_cv___attribute___noreturn_function_pointer=yes,
ac_cv___attribute___noreturn_function_pointer=no)])
CFLAGS="$save_CFLAGS"
if test "$ac_cv___attribute___noreturn_function_pointer" = "yes"; then
  AC_DEFINE(__ATTRIBUTE___NORETURN_OK_FOR_FUNCTION_POINTERS, 1,
    [define if your compiler allows __attribute__((noreturn)) to be applied to function pointers])
fi
AC_MSG_RESULT($ac_cv___attribute___noreturn_function_pointer)
])

AC_DEFUN(AC_LBL_SSLEAY,
    [
	#
	# Find the last component of $libdir; it's not necessarily
	# "lib" - it might be "lib64" on, for example, x86-64
	# Linux systems.
	#
	# We assume the directory in which we're looking for
	# libcrypto has a subdirectory with that as its name.
	#
	tmplib=`echo "$libdir" | sed 's,.*/,,'`

	#
	# XXX - is there a better way to check if a given library is
	# in a given directory than checking each of the possible
	# shared library suffixes?
	#
	# Are there any other suffixes we need to look for?  Do we
	# have to worry about ".so.{version}"?
	#
	# Or should we just look for "libcrypto.*"?
	#
	if test -d "$1/$tmplib" -a \( -f "$1/$tmplib/libcrypto.a" -o \
		          	    -f "$1/$tmplib/libcrypto.so" -o \
		          	    -f "$1/$tmplib/libcrypto.sl" -o \
			  	    -f "$1/$tmplib/libcrypto.dylib" \); then
		ac_cv_ssleay_path="$1"
	fi

	#
	# Make sure we have the headers as well.
	#
	if test -d "$1/include/openssl" -a -f "$1/include/openssl/des.h"; then
		incdir="-I$1/include"
	fi
])
