Please report bugs on the following website instead of writing bad reviews:
https://github.com/AdAway/AdAway/issues

## [4.1.0] - 2018-12-13

- Add [telemetry feature](https://github.com/AdAway/AdAway/wiki/Telemetry)
- Add snackbar notification to update host when editing hosts sources or lists
- Update translations and fix english locale issues

## [4.0.12] - 2018-11-21

- Fix issue when getting last modified date on file:// hosts source
- Fix excluded hostnames from source due to parser failure

Special thanks to DiamondJohn and Vankog for theirs helpful bug reports.

## [4.0.11] - 2018.11.06

- Update translations from Transifex
- Fix crash using file:// protocol for hosts source
- Fix redirect list import

Special thanks to ipdev, ktmom and shaqman89 for theirs helpful bug reports and Vankog for the locales update.

## [4.0.10] - 2018.10.14

- Last update time now works with GitHub hosted files (on https://raw.githubusercontent.com/ domain)
- Fix infinite "update available" status when at least one host source failed to download
- Fix hosts not installed by the background update service
- Fix hosts source update time when reverting to default hosts file
- Fix "download failed" status when no host source enabled
- Fix a bunch of translation issues

Special thanks to Alain-Olivier and Vankog for theirs contributions and MarcAnt01 for its helpful bug report.

## [4.0.9] - 2018.09.26

- Fix missing reboot and error dialog when installing and checking for hosts update
- Block http hosts source for security
- Add project and support links to the menu
- Fix missing notification channel for Oreo and later
- Fix host name validation to add more complex domain name in black/white lists
- Improve HTTP client connection pool
- Add new internal architecture for hosts installation
- Fix Indonesian locale code
- Clean up a lot a unused resource (texts and graphics)
- Clean up help from unrelated help elements
- Update gradle itself, plugin, ndk and dependencies versions

Special thanks to adem4ik TacoTheDank and @Vankog for theirs contributions and towlie, ipdev for theirs helpful bug reports.

## [4.0.8] - 2018.08.22

- Add option to dismiss welcome card
- Improve hosts update status
- Change background job dependency from Evernote Android Job to Jetpack Work manager
- Update translations

## [4.0.7] - 2018.08.08

- Fix host lists import
- Fix default DNS requests when no log entry
- Update translations from Transifex

Special thanks to Vankog for the translation update and GuardianUSMC and DiamondJohn for the bug reports.

## [4.0.6] - 2018.08.05

- Add web server status (icon and text) in the home card UI
- Fix black and while list inversion bug

## [4.0.5] - 2018.07.02

- Change database to room:
  - Add migration from previous database
- Update hosts source UI:
    - New animations
    - Dialogs validate user data so you do no more loose your input if format is wrong
- New DNS logging UI:
    - Add new sort feature:
      - by name (old behavior)
      - by top level domain name (group entries by DNS so google.com, ads.google.com and www.google.com appears next to each other)
    - New controls and animations:
      - Block, allow or redirect from the directly from DNS logs
      - Currently set up domains (in your lists UI) will be displayed accordingly
      - Swipe to refresh!
- Update build tools, target SDK (28) and dependencies

## [4.0.4] - 2018.06.03

- A new light (white) theme
- A new adware UI (using LiveData and ViewModel for the 1st time!)
- A fix for the overlapping status texts in the home screen

## [4.0.3] - 2018.05.20

- Fix tcpdump failed to start after being stopped

## [4.0.2] - 2018.05.09

- Add adaptive launcher icons (8+)
- Add adaptive app shortcut (7.1+)
- Add new hosts content screen
- Fix application title not restored on configuration change
- Fix screen change when opening hosts file

## [4.0.1] - 2018.05.07

- Fix redirection IP and custom target dialogs in preferences
- Fix multiple lines in home card hedear.

## [4.0.0] - 2018.05.06

This version is a major update.
First, There is a new design:

- The home is now card based (webserver card is added if enabled)
- The navigation menu is now an humbugger menu
- The hosts source UI is updated (floating add button, actionbar edition)
- Your lists UI is updated (bottom navigation bar, same controls add, edit, remove like hosts sources)
- Permission request at runtime to access storage to import or export your list
- All other views are updated to use the latest support libraries

And a lot of changes under the hood including:

- Oreo support
- Battery improvement and host update fix
- Better support of root and systemless mode

## [3.3] - 2018.03.05
- Add support for ChainFire's SuperSU "bind sbin" systemless mode - by PerfectSlayer
- Improve systemless activation error handling - by PerfectSlayer
- Improve su location detection - by tstaylor7
- Update Mongoose webserver - by MrRobinson
- Replace CyanogenMod references by LineageOS - by experience7
- Update translations - by Mattter, mission712, ThomasSmallert and muzena
- Fix translation attributes - by Vankog
- Fix Magisk 15.x support - by pec0ra

## [3.2]
- Systemless root support - extensive work by PerfectSlayer
- Improvements to root mounter - by sanjay900
- Translations updates
- Various updates for support on Android 7.x

## [3.1.2]
- Update hosts-file.net source to use https
- Translations updates

## [3.1.1]
- Minor bugfixes
- Update mongoose internal webserver to 6.4 release
- Translations updates
- Update pgl.yoyo.org default source to use https
- Add generated timestamp into header of hosts file

## [3.1]
- Add 64bit arch which allows tcpdump to run on those devices
- Adjust scan adware list and stop it from crashing
- Update libpcap to 1.7.4 release
- Update tcpdump to 4.7.4 release
- Update mongoose internal webserver to 6.0 release and add IPv6 support
- Add enable IPv6 option which adds ::1 to all hosts entries
- Improve building hosts file speed by up to 2.5x
- RevertService uses Target hosts file preference
- Updates to support Android 6.0 SDK (23)

## [3.0.2]
- Disable autocorrect/autocomplete for hosts input on whilelist and redirection inputs
- Allow two pane layout as long as min of 600dp width available regardless of orientation
- Add material colored status icons
- Adjust asset layout per latest development guidelines
- Minor other adjustments

## [3.0.1]
- Make some text fields single line - by Phoenix09
- Make daily update time randomized within a range
- Minor fixes 
- Adjust hosts-file.net default source URL

## [3.0]
This release has mainly been done by 0-kaladin.

- Min Android version increased to Android 4.1 to support Position Independent Executables (PIE)
- Material design

## [2.9.2]
This release has mainly been done by Dāvis Mošenkovs.

- Added Trove library for high performance collections
- Adware scan improvements
- Separate whitelisting and redirections options - "Allow whitelisting" defaults to checked; "Allow redirections" defaults to state of "Allow redirections and whitelisting" (or unchecked on new installations)
- Fix tcpdump logging upon file deletion
- Fix possible crash on DB update
- Fix AdAway's default hosts source

## [2.9.1]
- Fixing regression bugs

## [2.9]
This release has mainly been done by Dāvis Mošenkovs, thanks!

- Workaround for Android 4.4 (see Help)
- Fixed Hide reboot dialog setting being ignored after symlink creation
- Fix crashes
- Change AdAway's default hosts source to https

## [2.8.1]
- Fix mobile hosts source

## [2.8]
- Higher timeout for root commands
- New version of RootCommands library (If you experience problems install busybox, AdAway will then use it!)
- Remove unused billing permission

## [2.7]
- Reduce apk size by switching from HtmlSpanner to HtmlTextView library

## [2.6]
- Improve build for F-Droid

## [2.5]
- Fix URLs in-app

## [2.4]
- Fix Remounter for Android 4.3
- Fix auto update on ethernet connection

## [2.3]
- AdAway was removed from Google Play!
- Because http://www.ismeh.com/HOSTS is down, a alternative source has been added

## [2.2]
- Introduce SUPERUSER permission for new Superuser app
- Allow backups of AdAway (for Carbon)
- Logo reworked thanks to Alin Ţoţea-Radu

## [2.1]
- Allow whitelist entries from hosts sources if enabled in preferences
- Fixed missing menu entry for hosts sources on tablets
- Webserver on boot should work more reliable
- Webserver should not be killed on low memory

## [2.0]
IF YOU HAVE PROBLEMS WITH 2.0: Please uninstall and then reinstall AdAway!
- New library for root access: RootCommands
- Tcpdump and Webserver now included for ARM, x86, MIPS (Please test and report problems!)
- Google forced me to remove the possibility to donate via Flattr and PayPal
- Hopefully fixes DNS logging for Android 4.1

## [1.37]
- Skip unreachable hosts sources and show number of successful sources
- Fix for crash on donations screen (Android 4.1)
- Fix crash on help screen (Android 4.1)
- Bind local webserver https only to localhost (thanks to Stebalien for finding that bug)

## [1.36]
- Help and About screens reworked
- Simple scanner for bad Adware apps like Airpush Notifications (derived from open source Airpush-Detector, thanks!)

## [1.35]
- New method for background update checking
- AdAway will now reschedule the update check to execute it when the Internet connection is established
- New preference to update only when on Wifi
- AdAway now allows empty hosts sources for people who only want to maintain their own lists

## [1.34]
- In-app PayPal donations are now possible
- Disabled hardware acceleration on ICS, caused black screens and glitches on some custom roms

## [1.33]
- Fixed rare problems on some devices regarding remounting /system as read/write

## [1.32]
- Fixes crashes with 1.31

## [1.31]
- Check for APN proxy

## [1.30]
- Design improvements for Android 4
- Layout fixes for "Your Lists"

## [1.29]
- Languages updated
- Fixed copying when no cp command is available

## [1.28]
- Now works on devices without cp command
- New debug setting

## [1.27]
- No longer needs busybox
- Fixed problems with reverting
- Method to restart Android changed

## [1.26]
- Usability improvements: Buttons will now be disabled while applying, reverting is improved
- Faster due to improved hosts parsing
- Now including tcpdump

## [1.25]
- Better tcpdump integration
- Wildcard characters * and ? can be used in your whitelist
- Updated translations
- Fixes stuck on applying hopefully

## [1.24]
- Tcpdump DNS request logging
- Preference to allow redirection rules from Hosts Sources
- New hosts source for mobile ads: http://www.ismeh.com/HOSTS
- Webserver binary now updates correctly from old AdAway versions

## [1.23]
- Fix for import/export

## [1.22]
- More notification and status bug fixes
- Fixed preference bug causing automatic update to be enabled
- Fixed color of notifications
- Fixed a crash under Android 3.2

## [1.21]
- Fixed staying notification when update was failing
- Hopefully fixes webserver crashes

## [1.20]
- Fixed staying notification

## [1.19]
- Automatic updating in background can be enabled in preferences
- New method for checking for symlink, should fix some problems
- Fixes for exporting entries
- Fixes for donation screen, when no Google Android Market is available

## [1.18]
- Local webserver now answers with blank page instead of 404 error page
- Import and Export of Your Lists
- Bug fix for symlink check
- Last entry in hosts file is now working (missed new line at end of hosts file)
- Fixes for daily update check
- Allow hostnames without TLD ending

## [1.17]
- Fix for translation problems

## [1.16]
- Translations: German, French, Spanish
  Thanks to all contributors!
- Better check for symlink
- Newer version of web server mongoose
- Better handling of AdAway database
- Fixed "Not enough space available" bug
- Fixed problem with applying

## [1.15]
- Added permission for Google Android Market donations

## [1.14]
- Force close on open hosts file fixed
- Donations with Google Android Market added

## [1.13]
- Webserver now hears on all local IP address (0.0.0.0)
- Custom target can be set in preferences, for example to /data/etc/hosts
- Hosts file can be opened from menu

## [1.12]
- Delayed starting of webserver on boot
- Disabled debug logging

## [1.11]
- Fixed bugs in layout

## [1.10]
- Webserver is now a preference, disabled by default
- Fixed daily update again. Should schedule now correctly
- Fixed crash on Android 3.0 and 3.1 in Your Lists
- Fixed preferences on Android 3.x
- Fixed rotation bug on Android 3.x

## [1.09]
- New design for tablet sizes
- New hosts source: http://pgl.yoyo.org/adservers
- fixed crash on Android 3 Honeycomb
- Fix for daily update check
- Removed hosts source sysctl.org because of false positives

## [1.08]
- AdAway got a redesign
- AdAway ships with a webserver, that listens on localhost
- Dates of all hosts sources are saved and can be seen in Hosts sources
- Preference to hide reboot question dialog
- Help page with information about AdAway
- Added new hosts source sysctl.org
- Daily update check can be enabled in preferences
- Should now work on roms which doesn't symlink busybox commands

## [1.07]
- AdAway can now create a symlink
- better error handling
- No update check on orientation change

## [1.06]
- hosts file target can be choosen
- Donation button
- Fixed SQLite bug occuring on Android 2.1

## [1.05]
- Update Check implemented
- Fixed bug when changing orientation of device while downloading
- better error handling when downloading
- Fixed localhost entry again

## [1.04]
- Implemented Redirection List
- Fixed Layout bugs

## [1.03]
- Implemented Blacklist and Whitelist

## [1.02]
- Fixed localhost entry

## [1.01]
- Fixed permissions on /system/etc/hosts
