ndk-build clean
ndk-build
echo ""
echo "Remove shared openssl libs, tcpdump will use system libs..."
rm -Rf libs/armeabi/*.so
echo "Moving all executables from libs/armeabi directory to raw resources to install at runtime..."
cp -f libs/armeabi/* res/raw
echo "Remove libs/armeabi directory..."
rm -Rf libs/armeabi