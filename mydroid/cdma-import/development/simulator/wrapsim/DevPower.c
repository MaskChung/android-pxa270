/*
 * Copyright 2007 The Android Open Source Project
 *
 * Magic entries in /sys/android_power/.
 */
#include "Common.h"

#include <stdlib.h>
#include <string.h>
#include <ctype.h>

#include <fcntl.h>
#include <sys/ioctl.h>

#if 0
/*
 * Set of entries found in /sys/android_power.
 */
typedef enum DeviceIndex {
    kPowerUnknown = 0,

    kPowerAutoOffTimeout,
    kPowerBatteryLevel,
    kPowerBatteryLevelLow,
    kPowerBatteryLevelRaw,
    kPowerBatteryLevelScale,
    kPowerBatteryLowLevel,
    kPowerBatteryShutdownLevel,
    kPowerChargingState,
    kPowerRequestState,
    kPowerState,

    kPowerAcquireFullWakeLock,
    kPowerAcquirePartialWakeLock,
    kPowerReleaseWakeLock,
} DeviceIndex;
#endif

/*
 * Map filename to device index.
 *
 * [ not using DeviceIndex -- would be useful if we need to return something
 * other than a static string ]
 */
static const struct {
    const char*     name;
    //DeviceIndex     idx;
    const char*     data;
} gDeviceMap[] = {
    { "auto_off_timeout",           //kPowerAutoOffTimeout,
        "\n" },
    { "battery_level",              //kPowerBatteryLevel,
        "9\n" },
    { "battery_level_low",          //kPowerBatteryLevelLow,
        "0\n" },
    { "battery_level_raw",          //kPowerBatteryLevelRaw,
        "100\n" },
    { "battery_level_scale",        //kPowerBatteryLevelScale,
        "9\n" },
    { "battery_low_level",          //kPowerBatteryLowLevel,
        "10\n" },
    { "battery_shutdown_level",     //kPowerBatteryShutdownLevel,
        "5\n", },
    { "charging_state",             //kPowerChargingState,
        "Maintaining\n" },
    { "request_state",              //kPowerRequestState,
        "wake\n" },
    { "state",                      //kPowerState,
        "0-1-0\n" },

    { "acquire_full_wake_lock",     //kPowerAcquireFullWakeLock,
        "\n" },
    { "acquire_partial_wake_lock",  //kPowerAcquirePartialWakeLock,
        "\n" },
    { "release_wake_lock",          //kPowerReleaseWakeLock,
        "radio-interface PowerManagerService KeyEvents\n" },
    { "wait_for_fb_sleep",          //kSleepFileName,
        "" },                       // this means "block forever on read"
    { "wait_for_fb_wake",           //kWakeFileName,
        "0" },

};

/*
 * Power driver state.
 *
 * Right now we just ignore everything written.
 */
typedef struct PowerState {
    int         which;
} PowerState;


/*
 * Figure out who we are, based on "pathName".
 */
static void configureInitialState(const char* pathName, PowerState* powerState)
{
    const char* cp = pathName + strlen("/sys/android_power/");
    int i;

    powerState->which = -1;
    for (i = 0; i < (int) (sizeof(gDeviceMap) / sizeof(gDeviceMap[0])); i++) {
        if (strcmp(cp, gDeviceMap[i].name) == 0) {
            powerState->which = i;
            break;
        }
    }

    if (powerState->which == -1) {
        wsLog("Warning: access to unknown power device '%s'\n", pathName);
        return;
    }
}

/*
 * Free up the state structure.
 */
static void freeState(PowerState* powerState)
{
    free(powerState);
}

/*
 * Read data from the device.
 *
 * We don't try to keep track of how much was read -- existing clients just
 * try to read into a large buffer.
 */
static ssize_t readPower(FakeDev* dev, int fd, void* buf, size_t count)
{
    PowerState* state = (PowerState*) dev->state;
    int dataLen;

    wsLog("%s: read %d\n", dev->debugName, count);

    if (state->which < 0 || state->which >= sizeof(gDeviceMap)/sizeof(gDeviceMap[0]))
        return 0;

    const char* data = gDeviceMap[state->which].data;
    size_t strLen = strlen(data);

    while(strLen == 0)
        sleep(10); // block forever

    ssize_t copyCount = (strLen < count) ? strLen : count;
    memcpy(buf, data, copyCount);
    return copyCount;
}

/*
 * Ignore the request.
 */
static ssize_t writePower(FakeDev* dev, int fd, const void* buf, size_t count)
{
    wsLog("%s: write %d bytes\n", dev->debugName, count);
    return count;
}

/*
 * Our Java classes want to be able to do ioctl(FIONREAD) on files.  The
 * battery power manager is blowing up if we get an error other than
 * ENOTTY (meaning a device that doesn't understand buffering).
 */
static int ioctlPower(FakeDev* dev, int fd, int request, void* argp)
{
    if (request == FIONREAD) {
        wsLog("%s: ioctl(FIONREAD, %p)\n", dev->debugName, argp);
        errno = ENOTTY;
        return -1;
    } else {
        wsLog("%s: ioctl(0x%08x, %p) ??\n", dev->debugName, request, argp);
        errno = EINVAL;
        return -1;
    }
}

/*
 * Free up our state before closing down the fake descriptor.
 */
static int closePower(FakeDev* dev, int fd)
{
    freeState((PowerState*)dev->state);
    dev->state = NULL;
    return 0;
}

/*
 * Open a power device.
 */
FakeDev* wsOpenDevPower(const char* pathName, int flags)
{
    FakeDev* newDev = wsCreateFakeDev(pathName);
    if (newDev != NULL) {
        newDev->read = readPower;
        newDev->write = writePower;
        newDev->ioctl = ioctlPower;
        newDev->close = closePower;

        PowerState* powerState = calloc(1, sizeof(PowerState));

        configureInitialState(pathName, powerState);
        newDev->state = powerState;
    }

    return newDev;
}

