LOCAL_PATH := $(call my-dir)
include $(CLEAR_VARS)

LOCAL_MODULE    := mongoose
LOCAL_SRC_FILES := main.c mongoose.c

# disables execution prevention
LOCAL_DISABLE_NO_EXECUTE := true

include $(BUILD_EXECUTABLE)