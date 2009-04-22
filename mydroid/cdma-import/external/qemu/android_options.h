/* this header file can be included several times by the same source code.
 * it contains the list of support command-line options for the Android
 * emulator program
 */
#ifndef OPT_PARAM
#error OPT_PARAM is not defined
#endif
#ifndef OPT_FLAG
#error OPT_FLAG is not defined
#endif
#ifndef CFG_PARAM
#define CFG_PARAM  OPT_PARAM
#endif
#ifndef CFG_FLAG
#define CFG_FLAG   OPT_FLAG
#endif

/* required to ensure that the CONFIG_XXX macros are properly defined */
#include "config.h"

/* some options acts like flags, while others must be followed by a parameter
 * string. nothing really new here.
 *
 * some options correspond to AVM (Android Virtual Machine) configuration
 * and will be ignored if you start the emulator with the -avm <name> flag.
 *
 * however, if you use them with -avm-create <name>, these options will be
 * recorded into the new AVM directory. once an AVM is created, there is no
 * way to change these options.
 *
 * several macros are used to define options:
 *
 *    OPT_FLAG( name, "description" )
 *       used to define a non-config flag option.
 *       * 'name' is the option suffix that must follow the dash (-)
 *          as well as the name of an integer variable whose value will
 *          be 1 if the flag is used, or 0 otherwise
 *       * "description" is a short description string that will be
 *         displayed by 'emulator -help'
 *
 *    OPT_PARAM( name, "<param>", "description" )
 *       used to define a non-config parameter option
 *        * 'name' will point to a char* variable (NULL if option is unused)
 *        * "<param>" is a template for the parameter displayed by the help
 *        * 'varname' is the name of a char* variable that will point
 *          to the parameter string, if any, or will be NULL otherwise.
 *
 *    CFG_FLAG( name, "description" )
 *        used to define a configuration-specific flag option
 *
 *    CFG_PARAM( name, "<param>", "description" )
 *        used to define a configuration-specific parameter option
 *
 * NOTE: keep in mind that optio names are converted by translating
 *       dashes into underscore.
 *
 *       this means that '-some-option' is equivalent to '-some_option'
 *       and will be backed by a variable name 'some_option'
 *
 */

CFG_PARAM( system,  "<dir>",  "read system image from <dir>" )
CFG_PARAM( datadir, "<dir>",  "write user data into <dir>" )
CFG_PARAM( kernel,  "<file>", "use specific emulated kernel" )
CFG_PARAM( ramdisk, "<file>", "ramdisk image (default <system>/ramdisk.img" )
CFG_PARAM( image,   "<file>", "system image (default <system>/system.img" )
CFG_PARAM( initdata, "<file>", "initial data image (default <system>/userdata.img" )
CFG_PARAM( data,     "<file>", "data image (default <datadir>/userdata-qemu.img" )
CFG_PARAM( cache,    "<file>", "cache partition image (default is temporary file)" )
CFG_FLAG ( nocache,  "disable the cache partition" )
OPT_PARAM( sdcard, "<file>", "SD card image (default <system>/sdcard.img")
OPT_FLAG ( wipe_data, "reset the use data image (copy it from initdata)" )

CFG_PARAM( skindir, "<dir>", "search skins in <dir> (default <system>/skins)" )
CFG_PARAM( skin, "<file>", "select a given skin" )
CFG_FLAG ( noskin, "don't use any emulator skin" )

OPT_PARAM( netspeed, "<speed>", "maximum network download/upload speeds" )
OPT_PARAM( netdelay, "<delay>", "network latency emulation" )
OPT_FLAG ( netfast, "disable network shaping" )

OPT_PARAM( trace, "<name>", "enable code profiling (F9 to start)" )
OPT_FLAG ( show_kernel, "display kernel messages" )
OPT_FLAG ( shell, "enable root shell on current terminal" )
OPT_FLAG ( nojni, "disable JNI checks in the Dalvik runtime" )
OPT_PARAM( logcat, "<tags>", "enable logcat output with given tags" )

OPT_FLAG ( noaudio,  "disable audio support" )
OPT_PARAM( audio,    "<backend>", "use specific audio backend" )
OPT_PARAM( audio_in, "<backend>", "use specific audio input backend" )
OPT_PARAM( audio_out,"<backend>", "use specific audio output backend" )

OPT_FLAG ( raw_keys, "disable Unicode keyboard reverse-mapping" )
OPT_PARAM( radio, "<device>", "redirect radio modem interface to character device" )
OPT_PARAM( port, "<port>", "TCP port that will be used for the console" )
OPT_PARAM( onion, "<image>", "use overlay PNG image over screen" )
OPT_PARAM( onion_alpha, "<%age>", "specify onion-skin translucency" )
OPT_PARAM( onion_rotation, "0|1|2|3", "specify onion-skin rotation" )

OPT_PARAM( scale, "<scale>", "scale emulator window" )
OPT_PARAM( dpi_device, "<dpi>", "specify device's resolution in dpi (default "
            STRINGIFY(DEFAULT_DEVICE_DPI) ")" )

OPT_PARAM( http_proxy, "<proxy>", "make TCP connections through a HTTP/HTTPS proxy" )
OPT_PARAM( timezone, "<timezone>", "use this timezone instead of the host's default" )
OPT_PARAM( dns_server, "<servers>", "use this DNS server(s) in the emulated system" )
OPT_PARAM( cpu_delay, "<cpudelay>", "throttle CPU emulation" )
OPT_FLAG ( no_boot_anim, "disable animation for faster boot" )

OPT_FLAG( no_window, "disable graphical window display" )
OPT_FLAG( version, "display emulator version number" )

OPT_PARAM( report_console, "<socket>", "report console port to remote socket" )
OPT_PARAM( gps, "<device>", "redirect NMEA GPS to character device" )
OPT_PARAM( keyset, "<name>", "specify keyset file name" )
OPT_PARAM( shell_serial, "<device>", "specific character device for root shell" )
OPT_FLAG ( old_system, "support old (pre 1.4) system images" )

#ifdef CONFIG_NAND_LIMITS
OPT_PARAM( nand_limits, "<nlimits>", "enforce NAND/Flash read/write thresholds" )
#endif

#undef CFG_FLAG
#undef CFG_PARAM
#undef OPT_FLAG
#undef OPT_PARAM
