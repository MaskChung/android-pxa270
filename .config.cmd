deps_config := \
	/export/home/mask/release-sourceforge/android-pxa270/scripts/Config.in

$(BR2_DEPENDS_DIR)/config/auto.conf: \
	$(deps_config)

$(deps_config): ;
