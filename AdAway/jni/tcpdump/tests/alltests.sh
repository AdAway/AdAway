#!/bin/sh

for i in *.sh
do
  if [ "X$i" = "Xalltests.sh" ] 
  then 
  	continue;
  fi
  sh ./$i
done 


