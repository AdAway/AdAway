# ![AdAway logo](https://raw.githubusercontent.com/AdAway/AdAway/master/app/src/main/res/mipmap-mdpi/icon.png) AdAway

[![Build Status](https://travis-ci.com/AdAway/AdAway.svg?branch=master)](https://travis-ci.org/AdAway/AdAway) [![Sonarcloud Status](https://sonarcloud.io/api/project_badges/measure?project=org.adaway&metric=security_rating)](https://sonarcloud.io/dashboard?id=org.adaway) [![License: GPL v3](https://img.shields.io/badge/License-GPL%20v3-blue.svg)](blob/master/LICENSE)

AdAway is an open source ad blocker for Android using the hosts file.

For more information visit https://adaway.org

## Installing

This is two kinds of release:
* The preview builds: on the bleeding edge of development - for testers or adventurous
* The stable builds: ready for every day usage - for end users

### Preview builds
For users with bugs, there may be preview builds available from the [XDA development thread](https://forum.xda-developers.com/showthread.php?t=2190753) or the related [Android File Host folder](https://androidfilehost.com/?w=files&flid=249276).  
It is recommended to try those builds to see if your issue is resolved before creating an issue.
The preview builds may contain bug fixes or new features for new android versions.

### Stable builds
After preview builds have been tested by the more technical or responsive community within the forums, we will then post the stable build to F-Droid.

[<img src="https://f-droid.org/badge/get-it-on.png"
      alt="Get it on F-Droid"
      height="80">](https://f-droid.org/app/org.adaway)

## Support

You can post [Issues](https://github.com/AdAway/AdAway/issues) here or obtain more detailed community support via the [XDA Thread](http://forum.xda-developers.com/showthread.php?t=2190753).

## Authors

AdAway is currently maintained by:
* [@0-kaladin](https://github.com/0-kaladin)
* Sanjay Govind ([@sanjay900](https://github.com/sanjay900))
* Bruce Bujon ([@PerfectSlayer](https://github.com/PerfectSlayer))

The original author is Dominik Schürmann ([@dschuermann](https://github.com/dschuermann)) and it was previously maintained by Dāvis Mošenkovs ([@DavisNT](https://github.com/DavisNT)).

# Build with Gradle

1. Ensure you have Android SDK (Software Development Kit) and NDK (Native Development Kit) installed. If not:
    * Option 1: [Install Android Studio](https://developer.android.com/studio/index.html) or,
    * Option 2: Install command line tools and install build tools and ndk bundle with sdk manager (`tools/bin/sdkmanager "build-tools;27.0.3" ndk-bundle`)
2. Export ANDROID_HOME environment variable pointing to your Android SDK (`export ANDROID_HOME=/path/to/your/sdk`)
3. Execute `./gradlew build`

# Contribute
## Development

Fork AdAway and do a Pull Request. I will merge your changes back into the main project.

I am using the newest [Android Studio](http://developer.android.com/sdk/installing/studio.html) for development.
Development with Eclipse is currently not possible because I am using the new [project structure](http://developer.android.com/sdk/installing/studio-tips.html).

1. Clone the project from GitHub
2. From Android Studio: File -> Import Project -> Select the cloned top folder
3. Import project from external model -> choose Gradle

## Translating to your Language

You can help us with your translation efforts!  

Please have a look at ticket No. [AdAway/AdAway#1050](https://github.com/AdAway/AdAway/issues/1050) for more detailed information!
We apreciate new contributors and translations are perfect for getting started with contributing at Github in general.

Here is the gist:
Translations are managed via the **transifex.com website**! (and Transifex' website alone, that is)

Unfortunately, we cannot merge translations via Github directly. Please follow the steps below instead.  
Sorry, but this causes some major synchronization issues if not followed.  We have to consolidate many contributions by translators and sync them up to the latest state. That is just not possible via Github.  

1. Please go to **https://www.transifex.com/free-software-for-android/adaway/**
1. Login or create a new account (you can conveniently login via Github as well)
1. Enroll into the language you want to contribute to or even submit a request for a new language.
   * Please keep in mind that we want to stick to the basic languages where possible (e.g. `sr` for Serbian).  
   Please refrain to request regional localizations (like `sr_RS`).
1. In your language section, you can browse all available resources and start **translating strings right in your browser**.
   * You don't have to download anything. Just click "Translate". The downloads are meant for more advanced use cases.
1. Make sure to have an eye on the **"Suggestions", "History", "Context" and "Glossary" tabs** on every entry. 
   * Mind translation efforts that were already done or suggested.  
   Basically, »*stand on the shoulders of giants*« where possible.
   * Sometimes source strings change only marginally, but their translations get cleared anyway.  
   You can easily **recover their previous translations** by looking at the "Suggestions" tab. Just make sure they really fit the new source text.
1. Help us validate translations by **reviewing others'**. 
1. Some strings contain **placeholders** - like for HTML tags or numbers.  
You can click on them to add them or use keyboard shortcuts (see the page settings for an overview).
1. You can point out issues to the Translation Organizers via the **"Comments" tab** or just start a discussion.
1. Don't forget to save your work!
1. Please don't create any Translation Pull Request here on Github.  
   * We will integrate your contributions from time to time into the code by exporting from Transifex directly. No need to provide any files from your side. ;-) 
1. For more information about how to use Transifex, see https://docs.transifex.com/


We will add all Transifex translations from time to time to the app.  
For our Translation Organizers: You can use the CLI tool for this to retrieve translated resources from the Transifex server and add them to the Github repo. (See https://docs.transifex.com/client/ for more details about the client tool.)


# `tcpdump` and `webserver` binary modules

Forked from the following sources and slightly modified to compile!

* dnsmasq: https://github.com/CyanogenMod/android_external_dnsmasq
* libpcap: https://github.com/the-tcpdump-group/libpcap/tree/libpcap-1.7.4
* tcpdump: https://github.com/the-tcpdump-group/tcpdump/tree/tcpdump-4.7.4

## Changes

Please review the following commits for the changes made to the sources above in order for them to compile in this project:

* Commit: https://github.com/AdAway/AdAway/commit/1f4ccb3cec3758757341ad90813506fc2a8fdf7b
* Commit: https://github.com/AdAway/AdAway/commit/289df896c0ac4f96bd862e8a5054f1011ec07cac
* Commit: https://github.com/AdAway/AdAway/commit/08da0745b0732b94221c0f5746160fef8126fd99

## Licenses
AdAway is licensed under the GPLv3+.  
The file LICENSE includes the full license text.
For more details, check [the license notes](LICENSE.md).
