#!/bin/sh

mkdir -p NEW
mkdir -p DIFF
passed=0
failed=0
cat /dev/null > failure-outputs.txt

# first run any specific tests.
for i in *.sh
do
  case $i in TEST*.sh) continue;; esac

  if sh ./$i >DIFF/$i.result
  then
      echo $i: passed.
      rm -f DIFF/$i.result
      passed=`expr $passed + 1`
  else
      echo $i: failed.
      failed=`expr $failed + 1`
  fi          
done 

echo $passed >.passed
echo $failed >.failed

# now run typical tests
cat TESTLIST | while read name input output options
do
  case $name in
      \#*) continue;;
      '') continue;;
  esac

  if ./TESTonce $name $input $output "$options"
  then
      echo $name: passed.
      rm -f DIFF/$output.diff
      passed=`expr $passed + 1`
      echo $passed >.passed
  else
      echo $name: failed.
      failed=`expr $failed + 1`
      echo $failed >.failed
      echo "Failed test: $name" >> failure-outputs.txt
      echo >> failure-outputs.txt
      cat DIFF/$output.diff >> failure-outputs.txt
      echo >> failure-outputs.txt
  fi
done 

# I hate shells with their stupid, useless subshells.
passed=`cat .passed`
failed=`cat .failed`

# exit with number of failing tests.
echo 
echo
printf "%4u tests failed\n" $failed
printf "%4u tests passed\n" $passed
echo
echo
cat failure-outputs.txt
echo
echo
exit $failed      




