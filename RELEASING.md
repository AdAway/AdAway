# Making a release

### Table of contents

1. [Checking bugs and technical debt](#1---checking-bugs-and-technical-debt)
2. [Updating application version](#2---updating-application-version)
3. [Updating the changelog](#3---updating-the-changelog)
3. [Building release apk](#4---building-release-apk)
4. [Distributing release](#5---distributing-release)

## 1 - Checking bugs and technical debt

### Lint checks
Android development tool provide linter to check common errors.    
Use `./gradlew :app:lint` to run the linter and produce report.
It will generate an HTML (human readable) file at `app/build/reports/lint-results.html`.    
So before making a release, **ensure no new warning has been introduced**.

### SonarCloud analysis
The AdAway application source code is [monitored by SonarCloud](https://sonarcloud.io/dashboard?id=org.adaway).    
The current version of the source code is analysed to find bugs, code smells and compute technical debt.
The overall score may be not perfect, each new release should not increase it.    
So before making a release, **ensure that no new bug or debt has been introduced**.

## 2 - Updating application version

Each version has each own version number.
It follows the [Semantic Versioning](https://semver.org/) principle (_once the first 4.x.y stable release is published_).    
**It must be declared a 3 different locations**:

* The version name `android.defaultConfig.versionName` in `src/app/build.gradle`: '4.1.2', '5.12.3'
* The version code `android.defaultConfig.versionCode` in `src/app/build.gradle`: 41020, 51203
* The version name for SonarCloud `sonarqube.properties.property.sonar.projectVersion` in `src/build.gradle`: same as version name

## 3 - Updating the changelog

The AdAway project provides [a global changelog](CHANGELOG.md).  
Before releasing any new version, be sure to update the changelog to let users know what is inside each new version.

## 4 - Building release APK

The release apk must be built with release flavor (not debug). Check [contributing guide for building instructions](CONTRIBUTING.md#building-the-project).    
**The apk name follows the following format: `AdAway-<version_name>-<yymmdd>.apk`**.
Example: _AdAway-4.0.8-180822.apk_ for the version 4.0.8 built the 08/22/18.

## 5 - Distributing release

Before sharing the any release, remember to test it.
Release variant apk does not behave like debug variant.
Same goes for real device versus emulator.  
**Final test should be done with release apk variant on real device.**

Once tested, releases are posted on XDA development thread using the following template:
```
Hi all,

<welcoming message about the new version>

[U][SIZE="4"]Changelog:[/SIZE][/U]
[LIST]
[*] Item 1
[*] Item 2
[*] ...
[*] Item n
[/LIST]

[U][SIZE="4"]Thanks:[/SIZE][/U]

Special thanks to <contributors> for theirs contributions and <bug reporters> for theirs helpful bug reports.

[U][SIZE="4"]Download:[/SIZE][/U]

[URL="https://app.adaway.org/adaway.apk"]AdAway <application version>[/URL]
```

### Beta releases

The beta releases are only announced in the XDA development thread.

### Stable releases

The stable releases are distributed through [GitHub releases](https://github.com/AdAway/AdAway/releases) and [F-Droid store](https://f-droid.org/packages/org.adaway/) and are posted of the first post of XDA development thread.
Once ready, create and push a tag on GitHub repository using  `vX.Y.Z` format.
To publish the application in GitHub:

* Create a new version based on this tag,
* Copy the changelog part related to the version as description of the release,
* Upload apk binary to the release.

To publish the application in F-Droid store, only a pushed tag is needed.
