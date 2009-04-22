
LOCAL_PATH:= $(call my-dir)
include $(CLEAR_VARS)

copy_from :=                     \
    baseline11k.par              \
    baseline8k.par               \
    baseline.par                 \
    dictionary/basic.ok          \
    dictionary/enroll.ok         \
    dictionary/cmu6plus.ok.zip   \
    g2p/en-US-ttp.data           \
    models/generic.swiarb        \
    models/generic11.lda         \
    models/generic11_f.swimdl    \
    models/generic11_m.swimdl    \
    models/generic8.lda          \
    models/generic8_f.swimdl     \
    models/generic8_m.swimdl     \

copy_to := $(addprefix $(TARGET_OUT)/usr/srec/config/en.us/,$(copy_from))

$(copy_to) : $(TARGET_OUT)/usr/srec/config/en.us/% : $(LOCAL_PATH)/% | $(ACP)
	$(transform-prebuilt-to-target)

ALL_PREBUILT += $(copy_to)


# define paths to some grammar tools
GRXML=$(HOST_OUT_EXECUTABLES)/grxmlcompile
MAKE_G2G=$(HOST_OUT_EXECUTABLES)/make_g2g
DEFAULT_PAR=$(ASR_ROOT_DIR)/config/en.us/baseline11k.par
G2G_INSTALL_PATH=$(TARGET_OUT)/usr/srec/config/en.us/grammars

srec_grammars : \
	$(G2G_INSTALL_PATH)/enroll.g2g \
	$(G2G_INSTALL_PATH)/bothtags5.g2g \
	$(G2G_INSTALL_PATH)/dynamic-test.g2g \
	$(G2G_INSTALL_PATH)/digits.g2g \
	$(G2G_INSTALL_PATH)/boolean.g2g \
	$(G2G_INSTALL_PATH)/homonym_test1.g2g \
	$(G2G_INSTALL_PATH)/homonym_test2.g2g \
	$(G2G_INSTALL_PATH)/homonym_test3.g2g \
	$(G2G_INSTALL_PATH)/homonym_test4.g2g \
	$(G2G_INSTALL_PATH)/ipaq_commands.g2g \
	$(G2G_INSTALL_PATH)/lookup.g2g \

ALL_PREBUILT += $(G2G_INSTALL_PATH)/VoiceDialer.g2g

#---------------------------------------------------------------------------------
# Explicit rules.
# Those without explicit rules are subject to the rule at the end of this makefile
#---------------------------------------------------------------------------------

# This needs an explicit rule to specify the vocabulary (dictionary)
$(G2G_INSTALL_PATH)/enroll.g2g: $(LOCAL_PATH)/grammars/enroll.grxml $(GRXML) $(MAKE_G2G) dictionary/enroll.ok
	mkdir -p $(G2G_INSTALL_PATH)
	$(GRXML) -par $(DEFAULT_PAR) -grxml $< -vocab dictionary/enroll.ok -outdir $(G2G_INSTALL_PATH)
	$(MAKE_G2G) -base $(G2G_INSTALL_PATH)/enroll,addWords=0 -out $@
	(cd $(G2G_INSTALL_PATH); rm -f $*.Grev2.det.txt $*.map $*.omap $*.P.txt $*.params $*.PCLG.txt $*.script)


#---------------------------------------------------------------------------------
# Those without explicit rules are subject to the rule below
#---------------------------------------------------------------------------------

$(G2G_INSTALL_PATH)/%.g2g: $(LOCAL_PATH)/grammars/%.grxml $(GRXML) $(MAKE_G2G)
	mkdir -p $(G2G_INSTALL_PATH)
	$(GRXML) -par $(DEFAULT_PAR) -grxml $< -outdir $(G2G_INSTALL_PATH)
	$(MAKE_G2G) -base $(G2G_INSTALL_PATH)/$*,addWords=0 -out $@
	(cd $(G2G_INSTALL_PATH); rm -f $*.Grev2.det.txt $*.map $*.omap $*.P.txt $*.params $*.PCLG.txt $*.script)


#-----------------------------------------------------------------
# build cmu2nuance dictionary conversion program, to prevent bitrot
#-----------------------------------------------------------------

LOCAL_SRC_FILES:= dictionary/cmu2nuance.cpp

LOCAL_MODULE:= cmu2nuance

include $(BUILD_HOST_EXECUTABLE)

