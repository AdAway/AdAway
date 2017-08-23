#!/bin/sh
# Get gsutil software
wget https://storage.googleapis.com/pub/gsutil.tar.gz
# Install gsutil
tar xfz gsutil.tar.gz -C $HOME
# Copy APK to bucket
~/gsutil/gsutil cp AdAway/build/outputs/apk/AdAway-debug.apk gs://build-repository/
# Allow everyone to read APK
~/gsutil/gsutil acl ch -u AllUsers:R gs://build-repository/AdAway-debug.apk

