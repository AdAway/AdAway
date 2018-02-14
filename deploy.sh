#!/bin/sh
# Get gsutil software
wget https://storage.googleapis.com/pub/gsutil.tar.gz
# Install gsutil
tar xfz gsutil.tar.gz -C $HOME
# Define configuration file
export BOTO_CONFIG=.boto
# Copy debug APK to bucket
~/gsutil/gsutil cp AdAway/build/outputs/apk/debug/AdAway-debug.apk gs://build-repository/
# Copy release APK to bucket
~/gsutil/gsutil cp AdAway/build/outputs/apk/release/AdAway-release.apk gs://build-repository/
# Allow everyone to read APKs
~/gsutil/gsutil acl ch -u AllUsers:R gs://build-repository/AdAway-debug.apk
~/gsutil/gsutil acl ch -u AllUsers:R gs://build-repository/AdAway-release.apk

