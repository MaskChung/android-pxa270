/*
 * Copyright (C) 2006, 2007 Apple Inc.  All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 * 1.  Redistributions of source code must retain the above copyright
 *     notice, this list of conditions and the following disclaimer. 
 * 2.  Redistributions in binary form must reproduce the above copyright
 *     notice, this list of conditions and the following disclaimer in the
 *     documentation and/or other materials provided with the distribution. 
 * 3.  Neither the name of Apple Computer, Inc. ("Apple") nor the names of
 *     its contributors may be used to endorse or promote products derived
 *     from this software without specific prior written permission. 
 *
 * THIS SOFTWARE IS PROVIDED BY APPLE AND ITS CONTRIBUTORS "AS IS" AND ANY
 * EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL APPLE OR ITS CONTRIBUTORS BE LIABLE FOR ANY
 * DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF
 * THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

#import <Cocoa/Cocoa.h>
#import <CoreFoundation/CoreFoundation.h>

void displayErrorAndQuit(NSString *title, NSString *message)
{
    NSApplicationLoad();
    NSRunCriticalAlertPanel(title, message, @"Quit", nil, nil);
    exit(0);
}

void checkMacOSXVersion()
{
    long versionNumber = 0;
    OSErr error = Gestalt(gestaltSystemVersion, &versionNumber);
    if (error != noErr || versionNumber < 0x1040)
        displayErrorAndQuit(@"Mac OS X 10.4 is Required", @"Nightly builds of WebKit require Mac OS X 10.4 or newer.");
}

int getLastVersionShown()
{
    [[NSUserDefaults standardUserDefaults] registerDefaults:[NSDictionary dictionaryWithObject:@"-1" forKey:@"StartPageShownInVersion"]];
    return [[NSUserDefaults standardUserDefaults] integerForKey:@"StartPageShownInVersion"];
}

void saveLastVersionShown(int lastVersion)
{
    [[NSUserDefaults standardUserDefaults] setInteger:lastVersion forKey:@"StartPageShownInVersion"];
    [[NSUserDefaults standardUserDefaults] synchronize];
}

NSString *getPathForStartPage()
{
    return [[[NSBundle mainBundle] resourcePath] stringByAppendingPathComponent:@"start.html"];
}

int getShowStartPageVersion()
{
    return getCurrentVersion() + 1;
}

int getCurrentVersion()
{
    return [[[[NSBundle mainBundle] infoDictionary] valueForKey:(NSString *)kCFBundleVersionKey] intValue];
}

BOOL startPageDisabled()
{
    return [[NSUserDefaults standardUserDefaults] boolForKey:@"StartPageDisabled"];
}

void addStartPageToArgumentsIfNeeded(NSMutableArray *arguments)
{
    if (startPageDisabled())
        return;

    if (getLastVersionShown() < getShowStartPageVersion()) {
        saveLastVersionShown(getCurrentVersion());
        NSString *startPagePath = getPathForStartPage();
        if (startPagePath)
            [arguments addObject:startPagePath];
    }
}

static void myExecve(NSString *executable, NSArray *args, NSDictionary *environment)
{
    char **argv = (char **)calloc(sizeof(char *), [args count] + 1);
    char **env = (char **)calloc(sizeof(char *), [environment count] + 1);
    
    NSEnumerator *e = [args objectEnumerator];
    NSString *s;
    int i = 0;
    while (s = [e nextObject])
        argv[i++] = (char *) [s UTF8String];
    
    e = [environment keyEnumerator];
    i = 0;
    while (s = [e nextObject])
        env[i++] = (char *) [[NSString stringWithFormat:@"%@=%@", s, [environment objectForKey:s]] UTF8String];
   
    execve([executable fileSystemRepresentation], argv, env);
}

NSBundle *locateSafariBundle()
{
    NSArray *applicationDirectories = NSSearchPathForDirectoriesInDomains(NSApplicationDirectory, NSAllDomainsMask, YES);
    NSEnumerator *e = [applicationDirectories objectEnumerator];
    NSString *applicationDirectory;
    while (applicationDirectory = [e nextObject]) {
        NSString *possibleSafariPath = [applicationDirectory stringByAppendingPathComponent:@"Safari.app"];
        NSBundle *possibleSafariBundle = [NSBundle bundleWithPath:possibleSafariPath];
        if ([[possibleSafariBundle bundleIdentifier] isEqualToString:@"com.apple.Safari"])
            return possibleSafariBundle;
    }

    CFURLRef safariURL = nil;
    OSStatus err = LSFindApplicationForInfo(kLSUnknownCreator, CFSTR("com.apple.Safari"), nil, nil, &safariURL);
    if (err != noErr)
        displayErrorAndQuit(@"Unable to locate Safari", @"Nightly builds of WebKit require Safari to run.  Please check that it is available and then try again.");

    NSBundle *safariBundle = [NSBundle bundleWithPath:[(NSURL *)safariURL path]];
    CFRelease(safariURL);
    return safariBundle;
}

int main(int argc, char *argv[])
{
    NSAutoreleasePool *pool = [[NSAutoreleasePool alloc] init];
    checkMacOSXVersion();

    NSBundle *safariBundle = locateSafariBundle();
    NSString *executablePath = [safariBundle executablePath];
    NSString *frameworkPath = [[NSBundle mainBundle] resourcePath];
    NSString *pathToEnablerLib = [[NSBundle mainBundle] pathForResource:@"WebKitNightlyEnabler" ofType:@"dylib"];

    if ([frameworkPath rangeOfString:@":"].location != NSNotFound ||
        [pathToEnablerLib rangeOfString:@":"].location != NSNotFound)
        displayErrorAndQuit(@"Unable to launch Safari",
                            @"WebKit is located at a path containing an unsupported character.  Please move WebKit to a different location and try again.");
    
    NSMutableArray *arguments = [NSMutableArray arrayWithObjects:executablePath, @"-WebKitDeveloperExtras", @"YES", @"-WebKitScriptDebuggerEnabled", @"YES", nil];
    NSMutableDictionary *environment = [NSDictionary dictionaryWithObjectsAndKeys:frameworkPath, @"DYLD_FRAMEWORK_PATH", @"YES", @"WEBKIT_UNSET_DYLD_FRAMEWORK_PATH",
                                                                                  pathToEnablerLib, @"DYLD_INSERT_LIBRARIES", [[NSBundle mainBundle] executablePath], @"WebKitAppPath", nil];
    addStartPageToArgumentsIfNeeded(arguments);

    while (*++argv)
        [arguments addObject:[NSString stringWithUTF8String:*argv]];

    myExecve(executablePath, arguments, environment);

    char *error = strerror(errno);
    NSString *errorMessage = [NSString stringWithFormat:@"Launching Safari at %@ failed with the error '%s' (%d)", [safariBundle bundlePath], error, errno];
    displayErrorAndQuit(@"Unable to launch Safari", errorMessage);

    [pool release];
    return 0;
}
