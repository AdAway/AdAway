LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)
LOCAL_MODULE := liblog
LOCAL_SRC_FILES := liblog.so
include $(PREBUILT_SHARED_LIBRARY)
