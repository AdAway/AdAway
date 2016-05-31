# AdAway

AdAway is an open source ad blocker for Android using the hosts file. 

For more information visit http://adaway.org

[<img src="https://f-droid.org/badge/get-it-on.png"
      alt="Get it on F-Droid"
      height="80">](https://f-droid.org/app/org.adaway)

## Support

You can post [Issues](https://github.com/Free-Software-for-Android/AdAway/issues) here or obtain more detailed community support via the [XDA Thread](http://forum.xda-developers.com/showthread.php?t=2190753).

## Authors

AdAway is currently maintained by 0-kaladin and Dāvis Mošenkovs.
The original author is Dominik Schürmann.

# Build with Gradle

## Build Executables

1. Have NDK directory in your PATH (http://developer.android.com/tools/sdk/ndk/index.html)
2. Change to "AdAway" directory with ``cd AdAway``
3. Execute ``ndk-build`` to compile native binaries.

## Build APK

1. Have Android SDK "tools", "platform-tools", and "build-tools" directories in your PATH (http://developer.android.com/sdk/index.html)
2. Open the Android SDK Manager (shell command: ``android``). Expand the Extras directory and install "Android Support Repository"
3. Export ANDROID_HOME pointing to your Android SDK
4. Execute ``./gradlew renameExecutables`` (IMPORTANT unusual step!)
5. Execute ``./gradlew build``

# Contribute

Fork AdAway and do a Pull Request. I will merge your changes back into the main project.

## Development

I am using the newest [Android Studio](http://developer.android.com/sdk/installing/studio.html) for development. Development with Eclipse is currently not possible because I am using the new [project structure](http://developer.android.com/sdk/installing/studio-tips.html).

1. Clone the project from github
2. From Android Studio: File -> Import Project -> Select the cloned top folder
3. Import project from external model -> choose Gradle

# Translations

Translations are hosted on Transifex, which is configured by ".tx/config".

1. To pull newest translations install transifex client (e.g. ``apt-get install transifex-client``)
2. Config Transifex client with "~/.transifexrc"
3. Go into root folder of git repo
4. execute ``tx pull`` (``tx pull -a`` to get all languages)

see http://docs.transifex.com/client/

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
* libpcap: https://github.com/the-tcpdump-group/libpcap/tree/libpcap-1.7.4
* tcpdump: https://github.com/the-tcpdump-group/tcpdump/tree/tcpdump-4.7.4

## Changes

Please review the following commits for the changes made to the sources above
in order for them to compile in this project:

* Commit: https://github.com/Free-Software-for-Android/AdAway/commit/1f4ccb3cec3758757341ad90813506fc2a8fdf7b
* Commit: https://github.com/Free-Software-for-Android/AdAway/commit/289df896c0ac4f96bd862e8a5054f1011ec07cac
* Commit: https://github.com/Free-Software-for-Android/AdAway/commit/08da0745b0732b94221c0f5746160fef8126fd99

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
