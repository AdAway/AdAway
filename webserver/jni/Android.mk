
LOCAL_PATH := $(call my-dir)/mongoose

include $(CLEAR_VARS)

LOCAL_CFLAGS	       := -D MG_ENABLE_IPV6 -std=c99 -O2 -W -Wall -pthread -pipe $(COPT)
LOCAL_MODULE           := blank_webserver
LOCAL_SRC_FILES        := ../blank_webserver.c mongoose.c
LOCAL_LDLIBS           := -llog

include $(BUILD_EXECUTABLE)
