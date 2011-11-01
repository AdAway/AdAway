ifneq ($(TARGET_SIMULATOR),true)
include $(call all-subdir-makefiles)
endif
