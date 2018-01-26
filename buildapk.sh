#This script will build the executables and the apk
#Build Executables
cd AdAway
ndk-build
#Build APK
cd ..
./gradlew renameExecutables
./gradlew build
#Now to sign the apks
java -jar ~/uber-apk-signer-0.8.3.jar --apks /media/joshndroid/scratch/AdAway/AdAway/build/outputs/apk
exit
