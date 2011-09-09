LOCAL_PATH := $(call my-dir)
include $(CLEAR_VARS)

LOCAL_MODULE    := mongoose
LOCAL_SRC_FILES := main.c mongoose.c

include $(BUILD_EXECUTABLE)
