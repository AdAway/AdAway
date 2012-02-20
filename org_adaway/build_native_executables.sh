ndk-build clean
ndk-build
echo ""
echo "Moving all executables from libs/armeabi directory to raw resources to install at runtime..."
cp -f libs/armeabi/* res/raw
echo "Remove libs/armeabi directory..."
rm -Rf libs/armeabi