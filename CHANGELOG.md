## [5.12.0] - 2022-02-28

- Implement key-value pairs backup service support
- Improve VPN application exclusion UI
- Update AndroidX libraries
- Update mongoose web server
- Update translations
- Update gradle
- Update build tools

## [5.11.0] - 2021-12-20

- Improve home screen with icon color and decoration
- Improve settings UI elements
- Improve search filter performance in hosts list
- Improve resource clean up after parsing hosts source
- Improve logs with Timber
- Improve button descriptions
- Update Android gradle plugin
- Update AndroidX libraries 
- Update third party libraries
- Update mongoose web server
- Update translations

Special thanks to brijrajparmar27 for its contribution.

## [5.10.0] - 2021-12-01

- Fix connectivity change detection in VPN mode
- Update mongoose web server
- Update target SDK to Android 12
- Update dependencies
- Update translations

Special thanks to 8asuj6m2 for its contribution.

## [5.9.0] - 2021-11-05

- Improve update activity to add support links and open from notification
- Update web server certificate to comply with maximum validity time
- Update dependencies
- Update Android gradle plugin
- Update NDK

Special thanks to rany2 for its bug report.

## [5.8.0] - 2021-08-08

- Improve command receiver for task automation
- Update mongoose web server
- Update dependencies
- Update Android gradle plugin
- Update translations

Special thanks to Faedelity for its bug report.

## [5.7.0] - 2021-06-27

- Add quick settings tile to toggle ad-blocking
- Fix crash on TLS and timeout issue during source update
- Fix backup not listed for restoration on older devices
- Update dependencies
- Update translations

Special thanks to gwolf2u, opusforlife2, Vstory for their bug reports.

## [5.6.0] - 2021-04-30

- Improve navigation by moving DNS logs to home screen
- Improve DNS logs usage by explaining usage and limitation
- Update dependencies
- Update translations

## [5.5.1] - 2021-04-02

- Add redirection validation
- Improve application update screen
- Update Android gradle plugin
- Update NDK
- Update dependencies
- Fix VPN crash when the only system DNS server available uses IPv6 and IPv6 is disabled from settings 
- Remove html-textview dependency and jcenter repository

Special thanks to FrostbiterTy, SapphireExile, zgfg for their bug reports.

## [5.5.0] - 2021-03-17

- Add allow list support
- Improve source edition UI
- Improve source update check
- Improve animations
- Update mongoose web server
- Update dependencies
- Fix web server TLS issue

Special thanks to gallegonovato, jawz101 and zgfg for their bug reports.

## [5.4.0] - 2021-02-28

- Add VPN monitor option to prevent disconnection
- Update source update status indicator
- Update Android gradle plugin
- Update Sentry DSN to support older TLS versions
- Update mongoose web server
- Update dependencies
- Fix welcome screen telemetry preference
- Fix VPN authorization check at startup

Special thanks to BearTM, elvissteinjr, ipdev99, patkarmandar, RobBeyer for their bug reports.

## [5.3.0] - 2021-01-17

- Add new unique source parser with parallel processing
- Add an option to disable app update check at startup
- Fix crash with source file when SAF permission is removed
- Fix metered status vpn on Android 11 

Special thanks to andy356, fusionneur and sr1canskhsia for their bug reports.

## [5.2.1] - 2021-01-06

- Fix F-Droid store detection

Special thanks to TheLonelyGhost, bdtipsntricks, faraz-b bege10, Nathan-Nesbitt and gallegonovato for their bug reports.

## [5.2.0] - 2021-01-03

- Add beta channel opt-in preferences
- Update mongoose to latest stable version and rewrite web server
- Update source parser to prevent stack overflow 
- Update dependencies
- Update build tools
- Update NDK
- Update translations

## [5.1.0] - 2020-11-12

- Add application update UI
- Add F-Droid apk support to built-in updater
- Add custom hosts file parser for big hosts file (1M+ entries, way slower but memory friendly)
- Fix VPN app exclusion on Android 11
- Update AndroidX and Sentry dependencies
- Update translations

Special thanks to spiou, drothenberger and gallegonovato for their bug reports.

## [5.0.10] - 2020-10-10

*This version is a pre-release*

- Fix online modification date for unavailable source
- Prevent missing url at source creation 
- Prevent invalid source url to be restored
- Fix web server certificate install on Android 11

Special thanks to zgfg, ipdev99 and ingenium13 for their bug reports.

## [5.0.9] - 2020-09-13

*This version is a pre-release*

- Fix no hosts to block from 4.x to 5.x migration
- Fix wrong source type in source edition UI
- Update AndroidX dependencies
- Update mongoose web server

Special thanks to auanasgheps, lukjod, ridobe, sacrificialpawn for their bug reports.

## [5.0.8] - 2020-08-30

*This version is a pre-release*

- Fix source not updated on automatic update
- Fix user list not sync until source update
- Update hosts source creation to disable allowed hosts by default
- Update AndroidX dependencies

Special thanks to jeanrivera for its bug report.

## [5.0.7] - 2020-08-16

*This version is a pre-release*

- Add hosts sources Storage Access Framework support
- Add hosts sources label and host counter
- Improve host sources list and edition UI
- Improve allowed and redirected hosts settings by applying them per source
- Fix file based hosts sources not installed due to missing permission
- Update AndroidX dependencies

Special thanks to zgfg for its bug report.

## [5.0.6] - 2020-08-02

*This version is a pre-release*

- Fix crash on domain enable/disable action
- Fix source not applied if disabled and enabled back
- Update target SDK to Android 11

Special thanks to ipdev99 and zgfg for their bug reports.

## [5.0.5] - 2020-06-28

*This version is a pre-release*

- Improve hosts source server handling with future time
- Improve hosts update by skipping already up-to-date sources
- Fix hosts source disable action
- Fix hosts list apply notification from non-user changes 
- Fix user excluded application settings
- Remove navigation bar color customization
- Update AndroidX dependencies and NDK version

Special thanks to CobalTitan, ipdev99, zgfg and sunmybun for their bug reports.

## [4.3.5] - 2020-06-27

- Fix project Fastlane description for F-Droid store

Special thanks to linsui and IzzySoft for their bug reports and Vankog for translations.

## [5.0.4] - 2020-05-24

*This version is a pre-release*

- Improve overall host list computation
- Add host redirected feature in VPN ad blocking
- Remove WRITE_EXTERNAL_STORAGE permission (use Storage Access Framework instead)
- Fix duplicate entries in generated hosts file
- Fix allowed hosts settings in VPN ad blocking
- Fix backup not exported as sdcard not writable
- Fix source update period task preference
- Fix host list paging

Special thanks to holysnipz, ipdev99, QingKongBaiYu, and zgfg for their bug reports.

## [5.0.3] - 2020-05-10

*This version is a pre-release*

- Add TLS support for web server
- Add web server status in preferences
- Add option to install self signed certificate
- Add option to display app icon instead of blank page
- Add full timezone support for source date
- Add workaround for negative source update time when server time is not accurate
- Add follow system dark theme mode
- Fix user host list lost on source update
- Fix import failed toast
- Fix web server not start on install
- Fix duplicate host entry on backup import
- Update mongoose server
- Update translations

Special thanks to saltylemondrops, zgfg, ipdev99 and mickrussom for their bug reports.

## [5.0.2] - 2020-04-25

*This version is a pre-release*

- Fix timezone issues with source modification date
- Fix domain not removed when sources are disabled
- Fix inverted host and ip while generating hosts file
- Fix periodical hosts update check initialization 
- Improve overall search feature in list UI
- Improve last online modification date after retrieval
- Fix install snackbar not hiding
- Fix potential deadlock in VPN
- Add missing text on successful VPN update
- Update translations

Special thanks to damoasda for its contribution, Vankog for all translations he merged and Ps24u and dhacke for theirs bug reports.

## [4.3.4] - 2020-04-25

- Fix crash in tcpdump log view on Lollipop
- Fix timezone issues with source modification date
- Fix NDK version

Special thanks to Ps24u and Indranil012 for their bug reports.

## [5.0.1] - 2020-04-15

*This version is a pre-release*

- Fix redirect label from home screen
- Fix preference screen duplication on screen orientation change
- Improve hosts list readability
- Update dependencies
- Fix ndk configuration

Special thanks to TacoTheDank for its contribution and rmw98 and Luniz2k1 for its bug reports.

## [4.3.3] - 2020-04-10

hpHosts service is down.
If you are looking for a replacement, give a try at [StevenBlack's one](https://github.com/StevenBlack/hosts).

- Replace hpHosts default hosts file by StevenBlack hosts file
- Improve tcpdump icons
- Update translations

Special thanks to damoasda its contribution and gallegonovato for its bug report.

## [5.0.0] - 2020-04-07

*This version is a pre-release*

- Add new home screen
  - Provides all main controls from one screen
  - Displays currently blocked, allowed and redirected domains
  - Displays current hosts sources status and control to force apply
- Add non root ad-blocking feature
  - Uses a builtin local VPN to filter DNS request to blocked domains
  - Based on the work of [dns66 by julian-klode](https://github.com/julian-klode/dns66/issues/39)
  - Allows to excluded system applications and per user applications
- Add builtin updater with changelog display
- Add feature to quickly pause and resume ad-blocking
- Add wizard screen for first run setup
- Add feature to display and filter all blocked, allowed, redirect domains
- Improve preferences screen
- Add broadcast receiver to control ad-blocking from third party applications
- Update Android target to Android 10
- Improve root and shell support
- Split translation files to easier understand their context
- Add GitHub action test and build tasks

## [4.3.2] - 2019-12-29

- Fix GitLab source hosting
- Update translations

## [4.3.1] - 2019-12-21

- Update help to include Magisk systemless module for read-only system partition
- Update translations

## [4.3.0] - 2019-11-01

- Fix root not requested
- Improve support for systemless hosts Magisk module
- Update translations

## [4.2.9] - 2019-08-31

- Improve hosts file parsing
- Improve hosts file install error message (add more details than _not enough space_)
- Fix menu drawer translation issue
- Update translations
- Remove the start (opt-in only) telemetry messages

## [4.2.8] - 2019-07-28

- Fix TravisCI build issues
- Update translations

## [4.2.7] - 2019-07-04

- Revert Android gradle plugin to fix F-Droid build issue

## [4.2.6] - 2019-06-23

- Improve backup feature (user lists and hosts source using JSON format)
- Fix F-Droid build issue
- Update translations

Special thanks to RichyHBM its contribution and andy356 for its bug report.

## [4.2.5] - 2019-06-06

- Add Gist and GitLab hosting support for hosts file
- Add option to set default IPv6 redirection
- Improve reboot command
- Improve UI for overlays
- Update translations

Special thanks to MSF-Jarvis and Ralayax for their contributions

## [4.2.4] - 2019-03-23

- Add dedicated no root error message 
- Fix connection requirement for automatic update
- Fix crash on TCP dump views when root access is denied
- Fix icon resources and colors
- Improve exception reporting
- Update translations
- Update Android X dependencies
- Update Android Gradle plugin and NDK versions 

## [4.2.3] - 2019-03-02

- Fix update check on disabled sources
- Fix cropped label on home screen
- Prevent app installation on external storage (can't launch tcpdump or web server binary)
- Update work manager and material dependencies

## [4.2.2] - 2019-02-16

- Improve Material Theming
- Update build tools

## [4.2.1] - 2019-02-03

- Fix two buttons line when text too long

## [4.2.0] - 2019-02-02

- Add hosts source download cache
- Add snackbar notification to update host from DNS request listing
- Update UI from Material Design to Material Theming
- Update gradle, plugins and dependencies
- Fix crash parsing not defined host source last modified date
- Fix native modules build script (required for F-Droid build server)
- Fix Transifex issues

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
