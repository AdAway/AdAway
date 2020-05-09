
LOCAL_PATH := $(call my-dir)


#
# Import crypto prebuilt library
# Prebuilt from https://mvnrepository.com/artifact/com.android.ndk.thirdparty/openssl/1.1.1g-alpha-1
#

include $(CLEAR_VARS)

LOCAL_MODULE := crypto
LOCAL_SRC_FILES := crypto/libs/android.$(TARGET_ARCH_ABI)/libcrypto.so
LOCAL_EXPORT_C_INCLUDES := $(LOCAL_PATH)/crypto/include

include $(PREBUILT_SHARED_LIBRARY)


#
# Import openssl prebuilt library
# Prebuilt from https://mvnrepository.com/artifact/com.android.ndk.thirdparty/openssl/1.1.1g-alpha-1
#

include $(CLEAR_VARS)

LOCAL_MODULE := ssl
LOCAL_SRC_FILES := ssl/libs/android.$(TARGET_ARCH_ABI)/libssl.so
LOCAL_EXPORT_C_INCLUDES := $(LOCAL_PATH)/ssl/include

include $(PREBUILT_SHARED_LIBRARY)


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

LOCAL_CFLAGS	       := -D MG_ENABLE_IPV6 -D MG_ENABLE_SSL -std=c99 -O2 -W -Wall -lcrypto -lssl -pthread -pipe $(COPT)
LOCAL_MODULE           := webserver
LOCAL_SRC_FILES        := webserver.c mongoose/mongoose.c
LOCAL_SHARED_LIBRARIES := ssl crypto
LOCAL_LDLIBS           := -llog

include $(BUILD_EXECUTABLE)

#
# NOTE: Stub library binary is replaced by webserver executable at the end of ndkbuild by gradle build script
#