# AdAway

AdAway is an open source ad blocker for Android using the hosts file. 

For more information visit http://code.google.com/p/ad-away/

# Build AdAway

1. Have Android SDK and NDK in your PATH
2. Execute ``android update project -p .`` in ``AdAway`` and ``AdAway/android-libs/ActionBarSherlock`` and ``AdAway/android-libs/Donations``
3. Execute ``ndk-build`` in ``AdAway`` to compile native binaries.
4. Execute ``ant clear``
5. Execute ``ant debug -Ddonations=all`` or ``ant release -Ddonations=all``

## More build information

* It is necessary to build AdAway with Ant, because we use custom_rules.xml (For more information see https://github.com/dschuermann/root-commands#binaries)
* To disable Flattr and PayPal (not allowed in Google Play), execute ``ant debug -Ddonations=google``
* To disable Google (only working when the apk is signed with my private key), execute ``ant debug -Ddonations=other``

# Contribute

Fork AdAway and do a Pull Request. I will merge your changes back into the main project.

# Libraries

All JAR-Libraries are provided in this repository under ``libs``, all Android Library projects are under ``android-libs``.

# Translations

Translations are hosted on Transifex, which is configured by ``.tx/config``

1. To pull newest translations install transifex client (e.g. aptitude install transifex-client)
2. Config Transifex client with ``~/.transifexrc``
3. Go into root folder of git repo
4. execute ```tx pull``` (```tx pull -a``` to get all languages)

see http://help.transifex.net/features/client/index.html#user-client

# Coding Style

## Code
* Indentation: 4 spaces, no tabs
* Maximum line width for code and comments: 100
* Opening braces don't go on their own line
* Field names: Non-public, non-static fields start with m.
* Acronyms are words: Treat acronyms as words in names, yielding !XmlHttpRequest, getUrl(), etc.

See http://source.android.com/source/code-style.html

## XML
* XML Maximum line width 999
* XML: Split multiple attributes each on a new line (Eclipse: Properties -> XML -> XML Files -> Editor)
* XML: Indent using spaces with Indention size 4 (Eclipse: Properties -> XML -> XML Files -> Editor)

See http://www.androidpolice.com/2009/11/04/auto-formatting-android-xml-files-with-eclipse/


# AdAway/jni

Forked from the following sources and slightly modified to compile!

* dnsmasq:  https://github.com/CyanogenMod/android_external_dnsmasq
* libpcap: https://github.com/CyanogenMod/android_external_libpcap
* tcpdump: https://github.com/CyanogenMod/android_external_tcpdump

## Changes

``dnsmasq/source/src/Android.mk`` add following line:
```
LOCAL_LDLIBS := -llog
```

``tcpdump/Android.mk``:
```
LOCAL_C_INCLUDES += \
	$(LOCAL_PATH)/missing\
	$(LOCAL_PATH)/../libpcap

# disabled crypo libs, not needed in AdAway
#	$(LOCAL_PATH)/../openssl/include\

#LOCAL_SHARED_LIBRARIES += libssl libcrypto
```

``tcpdump/config.h``:
```
/* Whether or not to include the possibly-buggy SMB printer */
/* #undef TCPDUMP_DO_SMB */

/* Define to 1 if you have the <openssl/evp.h> header file. */
/* #undef HAVE_OPENSSL_EVP_H */

/* Define to 1 if you have the `crypto' library (-lcrypto). */
/* #undef HAVE_LIBCRYPTO */
```

# Update.zip

THIS IS CURRENTLY NOT WORKING!

Execute ./create_update_zip.sh to create update.zip files to work around S-ON.

This works by creating a symlink from /system/etc/hosts to /data/data/hosts

Info:
Clockwordmod Recovery < 3 uses Amend Scripting language, > 3 uses Edify

Resources:
* http://tjworld.net/wiki/Android/UpdaterScriptEdifyFunctions
* http://www.freeyourandroid.com/guide/introdution_to_edify
* http://forum.xda-developers.com/showthread.php?t=1265120

# Licenses
AdAway is licensed under the GPLv3+.  
The file COPYING includes the full license text.

## Details
AdAway is free software: you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.

AdAway is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with AdAway.  If not, see <http://www.gnu.org/licenses/>.

## Libraries
* ActionBarSherlock  
  http://actionbarsherlock.com/  
  Apache License v2

* Android Donations Lib  
  https://github.com/dschuermann/android-donations-lib  
  Apache License v2

* RootCommands  
  https://github.com/dschuermann/root-commands  
  Apache License v2

* Mongoose Webserver  
  https://github.com/valenok/mongoose  
  MIT License

* Tcpdump/Libpcap  
  http://www.tcpdump.org/  
  BSD 3-Clause License

* HTMLCleaner  
  http://htmlcleaner.sourceforge.net/  
  BSD License

* HtmlSpanner  
  Apache License v2

## Images
* status_enabled.svg, status_disabled.svg, status_update.svg  
  Dropbox Emblems Tango by Charles A.  
  http://forums.dropbox.com/topic.php?id=7818&replies=19  
  Creative Commons Attribution 3.0 Unported License

* status_fail.svg  
  Faenza Icons  
  GPLv3

* icon.svg, banner.svg  
  AdAway by Dominik Schürmann  
  GPLv3

* Menu Icons  
  Original Android Icons