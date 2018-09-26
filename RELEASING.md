# Making a release

### Table of contents

* Checking bugs and technical debt
* Updating the application version
* Building the application
* Rename apk name
* Upload to AFH
* Make GitHub tag

## Checking bugs and technical debt

# Lint checks
Android development tool provide linter to check common errors.
Use `gradlew :app:lint` to run the linter and produce report.
It will generate an HTML (humean readable) file at `app/build/reports/lint-results.html`.
Ensure no new 

### SonarCloud
The AdAway application source code is [monitored by SonarCloud](https://sonarcloud.io/dashboard?id=org.adaway).
The current version of the source code is analysed to find bugs, code smells and compute technical debt.
The overall score may be not perfect, each new release should not increase it.
So before making a release, ensure that no new bug or debt has been introduced.

