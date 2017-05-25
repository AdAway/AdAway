# Optimizations
APP_OPTIM := release

# Build target
APP_ABI := armeabi arm64-v8a x86 x86_64 mips mips64

# If APP_MODULES is not set, all modules are compiled!
APP_MODULES := blank_webserver tcpdump

# Allow undefined modules (cutils log) with latest NDK toolchain
APP_ALLOW_MISSING_DEPS=true