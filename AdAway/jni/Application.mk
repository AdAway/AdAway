# Optimizations
APP_OPTIM := release

# Build target
APP_ABI := all

# If APP_MODULES is not set, all modules are compiled!
APP_MODULES := blank_webserver tcpdump

# Allow undefined modules (cutils log) with latest NDK toolchain
APP_ALLOW_MISSING_DEPS=true