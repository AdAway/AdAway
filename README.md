# AdAway

AdAway is an open source ad blocker for Android using the hosts file. 

For more information visit http://code.google.com/p/ad-away/


# Build using Ant

1. Add a file named ``local.properties`` in the folder ``AdAway`` with the following lines:
``sdk.dir=/opt/android-sdk`` and ``ndk.dir=/opt/android-ndk``. Alter these lines to your locations of the Android SDK and NDK!
2. Add a file named ``local.properties`` in the folder ``AdAway/android-libs/Donations`` and ``AdAway/android-libs/ActionBarSherlock`` with the following line:
``sdk.dir=/opt/android-sdk``
3. Execute ```ant clear```
4. Execute ```ant debug -Dtemplates=other```
5. To disable Flattr and PayPal (not allowed in Google Play), execute ```ant debug -Dtemplates=google```

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