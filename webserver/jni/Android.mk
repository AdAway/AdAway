# Build stub library in order to declare shared library to include to AAR

LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)

LOCAL_MODULE           := blank_webserver_exec
LOCAL_SRC_FILES        := stub.c

include $(BUILD_SHARED_LIBRARY)


# Build webserver executable

LOCAL_PATH := $(LOCAL_PATH)/mongoose

include $(CLEAR_VARS)

LOCAL_CFLAGS	       := -D MG_ENABLE_IPV6 -std=c99 -O2 -W -Wall -pthread -pipe $(COPT)
LOCAL_MODULE           := blank_webserver
LOCAL_SRC_FILES        := ../blank_webserver.c mongoose.c
LOCAL_LDLIBS           := -llog

include $(BUILD_EXECUTABLE)

# Stub library binary is replace by webserver executable at the end of ndkbuild by gradle build script
