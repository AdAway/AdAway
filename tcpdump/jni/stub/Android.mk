# Build stub library in order to declare shared library to include to AAR

LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)

LOCAL_MODULE           := tcpdump_exec
LOCAL_SRC_FILES        := stub.c

include $(BUILD_SHARED_LIBRARY)
