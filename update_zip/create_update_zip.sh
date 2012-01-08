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

rm -rf $folder/*
cp -f script_amend $folder/update-script
name="symlink_amend"
createZip

rm -rf $folder/*
cp -f script_edify $folder/update-binary
name="symlink_edify"
createZip

echo "done"

#rm -R "META-INF"
exit 0