# BUILD MONGOOSE

LOCAL_PATH := $(call my-dir)/mongoose

include $(CLEAR_VARS)

LOCAL_MODULE           := mongoose
LOCAL_SRC_FILES        := main.c mongoose.c
LOCAL_CFLAGS += -pie -fPIE
LOCAL_LDFLAGS += -pie -fPIE

include $(BUILD_STATIC_LIBRARY)


# BUILD BLANK WEBSERVER

include $(CLEAR_VARS)

LOCAL_PATH := $(LOCAL_PATH)/..

LOCAL_MODULE := blank_webserver

LOCAL_SRC_FILES := blank_webserver.c
LOCAL_CFLAGS += -pie -fPIE
LOCAL_LDFLAGS += -pie -fPIE
LOCAL_C_INCLUDES := $(LOCAL_PATH)/mongoose
LOCAL_STATIC_LIBRARIES := mongoose

LOCAL_LDLIBS := -llog

include $(BUILD_EXECUTABLE)
