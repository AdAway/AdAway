LOCAL_PATH := $(call my-dir)

# build openssl binary
OPENSSL_BUILD_BINARY := 0

# build tests
OPENSSL_BUILD_TESTS := 0

# Export vars
OPENSSL_ROOT := $(LOCAL_PATH)
OPENSSL_INCLUDE := $(LOCAL_PATH)/include

include $(OPENSSL_ROOT)/crypto/Android.mk
include $(OPENSSL_ROOT)/ssl/Android.mk

ifeq ($(OPENSSL_BUILD_BINARY), 1)
	include $(OPENSSL_ROOT)/apps/Android.mk
endif
