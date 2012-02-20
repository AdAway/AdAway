# Copyright 2006 The Android Open Source Project

LOCAL_PATH:= $(call my-dir)
include $(CLEAR_VARS)

LOCAL_SRC_FILES:= \
	app_rand.c \
	apps.c \
	asn1pars.c \
	ca.c \
	ciphers.c \
	crl.c \
	crl2p7.c \
	dgst.c \
	dh.c \
	dhparam.c \
	dsa.c \
	dsaparam.c \
	ecparam.c \
	ec.c \
	enc.c \
	engine.c \
	errstr.c \
	gendh.c \
	gendsa.c \
	genpkey.c \
	genrsa.c \
	nseq.c \
	ocsp.c \
	openssl.c \
	passwd.c \
	pkcs12.c \
	pkcs7.c \
	pkcs8.c \
	pkey.c \
	pkeyparam.c \
	pkeyutl.c \
	prime.c \
	rand.c \
	req.c \
	rsa.c \
	rsautl.c \
	s_cb.c \
	s_client.c \
	s_server.c \
	s_socket.c \
	s_time.c \
	sess_id.c \
	smime.c \
	speed.c \
	spkac.c \
	verify.c \
	version.c \
	x509.c

#   cms.c ec.c s_server.c

LOCAL_SHARED_LIBRARIES := \
	libssl \
	libcrypto 

LOCAL_C_INCLUDES := \
	external/openssl \
	external/openssl/include

LOCAL_CFLAGS := -DMONOLITH

include $(LOCAL_PATH)/../android-config.mk

# These flags omit whole features from the commandline "openssl".
# However, portions of these features are actually turned on.
LOCAL_CFLAGS += -DOPENSSL_NO_DTLS1


LOCAL_MODULE:= openssl

LOCAL_MODULE_TAGS := tests

include $(BUILD_EXECUTABLE)
