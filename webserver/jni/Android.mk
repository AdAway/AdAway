
LOCAL_PATH := $(call my-dir)


#
# Build stub library in order to declare shared library to include to AAR
#

include $(CLEAR_VARS)

LOCAL_MODULE           := webserver_exec
LOCAL_SRC_FILES        := stub.c

include $(BUILD_SHARED_LIBRARY)


#
# Build webserver executable
#

include $(CLEAR_VARS)

LOCAL_CFLAGS	       := -D MG_ENABLE_IPV6 -D MG_ENABLE_OPENSSL -std=c99 -O2 -W -Wall -lcrypto -lssl -pthread -pipe $(COPT)
LOCAL_MODULE           := webserver
LOCAL_SRC_FILES        := webserver.c mongoose/mongoose.c
LOCAL_SHARED_LIBRARIES := ssl crypto
LOCAL_LDLIBS           := -llog

include $(BUILD_EXECUTABLE)

#
# NOTE: Stub library binary is replaced by webserver executable at the end of ndkbuild by gradle build script
#


#
# Import OpenSSL (ssl and crypto) libraries from prefab
#
$(call import-module,prefab/openssl)