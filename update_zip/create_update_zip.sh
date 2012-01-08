#!/bin/bash
set +v

function createZip {
  echo "creating the zip..."
  zip -r "$name".zip "META-INF"
  echo -n "update.zip created at "
  echo "$name".zip

  echo "signing the zip..."
  java -classpath testsign.jar testsign "$name".zip "$name"-signed.zip
  echo -n "signed update.zip created at "
  echo "$name"-signed.zip
  echo -n "md5sum for signed zip: "
  set -v
  md5sum "$name"-signed.zip
}


folder="META-INF/com/google/android/"
mkdir -p $folder

cp -f update-script_amend $folder/update-script
name="symlink_amend"
createZip

cp -f update-script_edify $folder/update-script
name="symlink_edify"
createZip

echo "update.zip created"

rm -R "META-INF"
exit 0