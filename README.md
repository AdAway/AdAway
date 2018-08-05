# AdAway [![Build Status](https://travis-ci.com/AdAway/AdAway.svg?branch=master)](https://travis-ci.org/AdAway/AdAway) [![Sonarcloud Status](https://sonarcloud.io/api/project_badges/measure?project=org.adaway&metric=security_rating)](https://sonarcloud.io/dashboard?id=org.adaway) [![License: GPL v3](https://img.shields.io/badge/License-GPL%20v3-blue.svg)](blob/master/LICENSE)

AdAway is an open source ad blocker for Android using the hosts file.

For more information visit http://adaway.org

## Preview Build
For users with bugs, there may be preview builds available from the [AdAway XDA Thread](http://forum.xda-developers.com/showthread.php?t=2190753).
It is recommended to try those builds to see if your issue is resolved before creating an issue.
The preview builds may contain bug fixes or new features for new android versions.
You can also obtain Preview or Stable builds from the [XDA Labs](https://labs.xda-developers.com/store/app/org.adaway) application.

## Stable Build
After preview builds have been tested a bit by the more technical or responsive community within the forums, we will then post the stable build to F-Droid.

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

Fork AdAway and do a Pull Request. I will merge your changes back into the main project.

## Development

I am using the newest [Android Studio](http://developer.android.com/sdk/installing/studio.html) for development.
Development with Eclipse is currently not possible because I am using the new [project structure](http://developer.android.com/sdk/installing/studio-tips.html).

1. Clone the project from GitHub
2. From Android Studio: File -> Import Project -> Select the cloned top folder
3. Import project from external model -> choose Gradle

# Translations

Translations are managed via **Transifex** - and Transifex alone. (Sorry, but this is due to some mayor synchronization issues if not followed.)
1. Please go to https://www.transifex.com/free-software-for-android/adaway/
1. Login or create a new account (you can conveniently login via Github as well)
1. Enroll into the language you want to contribute to or even submit a request for a new language.
   * Please keep in mind that we want to stick to the basic languages wehere possible (e.g. `sr` for Serbian). Please refrain to request regional localizations (like `sr_RS`).
1. In your language section, you can browse all available resources and start translating strings right in your browser.
   * You don't have to donwload anything. Just click "Translate". The downloads are meant only for more advanced use cases.
1. Make sure to have an eye on the "Suggestions", "History" and "Context" tab on every entry.
1. Some strings contain placeholders - like for HTML tags or numbers. You can click on them to add them or use keyboard shortcuts (see the settings for an overview).
1. Don't forget to save your work! 
1. For more information about how to use Transifex, see https://docs.transifex.com/

We will add all Transifex translations from time to time to the app.  
Translation managers can use the CLI tool for this to retrieve translated resources from the Transifex server and add them to the Github repo. (See http://docs.transifex.com/client/ for more details about the client tool.)

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

# Licenses
AdAway is licensed under the GPLv3+.  
The file LICENSE includes the full license text.

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

* Android Donations Lib  
  https://github.com/dschuermann/android-donations-lib  
  Apache License v2

* RootCommands  
  https://github.com/dschuermann/root-commands  
  Apache License v2

* Mongoose Webserver  
  https://github.com/cesanta/mongoose  
  GPLv2 License

* Tcpdump/Libpcap  
  http://www.tcpdump.org/  
  BSD 3-Clause License

* HtmlTextView  
  https://github.com/dschuermann/html-textview  
  Apache License v2

* Trove  
  http://trove.starlight-systems.com/  
  [multiple licenses](http://trove.starlight-systems.com/license)


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
  New version by Alin Ţoţea-Radu  
  GPLv3

* Menu Icons  
  Original Android Icons
