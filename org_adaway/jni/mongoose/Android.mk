LOCAL_PATH := $(call my-dir)/source
include $(CLEAR_VARS)

LOCAL_SRC_FILES := main.c mongoose.c

LOCAL_MODULE    := mongoose

include $(BUILD_EXECUTABLE)