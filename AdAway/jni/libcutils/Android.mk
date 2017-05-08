LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)
LOCAL_MODULE := libcutils
LOCAL_SRC_FILES := libcutils.so
include $(PREBUILT_SHARED_LIBRARY)
