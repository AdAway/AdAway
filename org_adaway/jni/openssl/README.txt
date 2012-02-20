
This is a version of the official Android openssl sources, but it is meant to be built as a standalone library to be embedded into app.

To build:
cd openssl-android
/path/to/android-ndk-r5b/ndk-build


http://guardianproject.info

----------------------
Updating the upstream code
----------------------

This repository tracks the Android openssl repository:
 git://android.git.kernel.org/platform/external/openssl.git

To use this, add it as a remote called 'upstream'
 git remote add upstream git://android.git.kernel.org/platform/external/openssl.git

Then here's how you get the updated code:
 git checkout upstream (switch to upstream tracking branch)
 git pull upstream master (get newest code from Android, but don't merge)
 git checkout master
 git merge upstream (merge the changes from upstream)
 git push origin master
 git push origin upstream (update the upstream branch)
