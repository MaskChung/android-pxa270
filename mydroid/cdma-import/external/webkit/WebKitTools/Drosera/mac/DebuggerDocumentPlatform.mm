/*
 * Copyright (C) 2006, 2007 Apple Inc.  All rights reserved.
 * Copyright (C) 2006, 2007 Vladimir Olexa (vladimir.olexa@gmail.com)
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

#import "config.h"
#import "DebuggerDocument.h"

#import "DebuggerClient.h"
#import "ServerConnection.h"

#import <JavaScriptCore/JSRetainPtr.h>
#import <JavaScriptCore/JSStringRefCF.h>
#import <JavaScriptCore/RetainPtr.h>

// Converting string types
NSString* NSStringCreateWithJSStringRef(JSStringRef jsString)
{
    CFStringRef cfString = JSStringCopyCFString(kCFAllocatorDefault, jsString);
    return (NSString *)cfString;
}

JSValueRef JSValueRefCreateWithNSString(JSContextRef context, NSString* nsString)
{
    JSRetainPtr<JSStringRef> jsString(Adopt, JSStringCreateWithCFString((CFStringRef)nsString));
    return JSValueMakeString(context, jsString.get());
}

@interface NSString (WebScriptStringExtras)
+ (NSString *)stringOrNilFromWebScriptResult:(id)scriptResult;
@end

@implementation NSString (WebScriptStringExtras)
+ (NSString *)stringOrNilFromWebScriptResult:(id)scriptResult
{
    NSString *ret = nil;

   if ([scriptResult isKindOfClass:NSClassFromString(@"WebScriptObject")])
       ret = [scriptResult callWebScriptMethod:@"toString" withArguments:nil];
   else if (scriptResult && ![scriptResult isKindOfClass:[NSString class]])
       ret = [scriptResult description];
   else if (scriptResult)
       ret = scriptResult;

    return ret;
}
@end

@interface WebScriptObject (WebScriptObjectExtras)
+ (NSArray *)webScriptAttributeKeys:(WebScriptObject *)object;
@end

@implementation WebScriptObject (WebScriptObjectExtras)
+ (NSArray *)webScriptAttributeKeys:(WebScriptObject *)object;
{
    WebScriptObject *enumerateAttributes = [object evaluateWebScript:@"(function () { var result = new Array(); for (var x in this) { result.push(x); } return result; })"];

    NSMutableArray *result = [[NSMutableArray alloc] init];
    WebScriptObject *variables = [enumerateAttributes callWebScriptMethod:@"call" withArguments:[NSArray arrayWithObject:object]];
    unsigned length = [[variables valueForKey:@"length"] intValue];
    for (unsigned i = 0; i < length; i++) {
        NSString *key = [variables webScriptValueAtIndex:i];
        [result addObject:key];
    }

    [result sortUsingSelector:@selector(compare:)];
    return [result autorelease];
}
@end

// DebuggerDocument platform specific implementations

void DebuggerDocument::platformPause()
{
    [m_server.get() pause];
}

void DebuggerDocument::platformResume()
{
    [m_server.get() resume];
}

void DebuggerDocument::platformStepInto()
{
    [m_server.get() stepInto];
}

JSValueRef DebuggerDocument::platformEvaluateScript(JSContextRef context, JSStringRef script, int callFrame)
{
    WebScriptCallFrame *cframe = [m_server.get() currentFrame];
    for (unsigned count = 0; count < callFrame; count++)
        cframe = [cframe caller];

    if (!cframe)
        return JSValueMakeUndefined(context);
    
    RetainPtr<NSString> scriptNS(AdoptNS, NSStringCreateWithJSStringRef(script));
    id value = [cframe evaluateWebScript:scriptNS.get()];
    NSString *result = [NSString stringOrNilFromWebScriptResult:value];
    if (!result)
        return JSValueMakeNull(context);

    return JSValueRefCreateWithNSString(context, result);
}

void DebuggerDocument::getPlatformCurrentFunctionStack(JSContextRef context, Vector<JSValueRef>& currentStack)
{
    for (WebScriptCallFrame *frame = [m_server.get() currentFrame]; frame;) {
        CFStringRef function;
        if ([frame functionName])
            function = (CFStringRef)[frame functionName];
        else if ([frame caller])
            function = CFSTR("(anonymous function)");
        else
            function = CFSTR("(global scope)");
        frame = [frame caller];

        currentStack.append(JSValueRefCreateWithNSString(context, (NSString *)function));
    }
}

void DebuggerDocument::getPlatformLocalScopeVariableNamesForCallFrame(JSContextRef context, int callFrame, Vector<JSValueRef>& variableNames)
{
    WebScriptCallFrame *cframe = [m_server.get() currentFrame];
    for (unsigned count = 0; count < callFrame; count++)
        cframe = [cframe caller];

    if (!cframe)
        return;
    if (![[cframe scopeChain] count])
        return;

    WebScriptObject *scope = [[cframe scopeChain] objectAtIndex:0]; // local is always first
    NSArray *localScopeVariableNames = [WebScriptObject webScriptAttributeKeys:scope];

    for (int i = 0; i < [localScopeVariableNames count]; ++i) {
        variableNames.append(JSValueRefCreateWithNSString(context, [localScopeVariableNames objectAtIndex:i]));
    }
}

JSValueRef DebuggerDocument::platformValueForScopeVariableNamed(JSContextRef context, JSStringRef key, int callFrame)
{
    WebScriptCallFrame *cframe = [m_server.get() currentFrame];
    for (unsigned count = 0; count < callFrame; count++)
        cframe = [cframe caller];

    if (!cframe)
        return JSValueMakeUndefined(context);

    unsigned scopeCount = [[cframe scopeChain] count];
    
    if (!scopeCount)
        return JSValueMakeUndefined(context);

    NSString *resultString = nil;
    
    for (unsigned i = 0; i < scopeCount && resultString == nil; i++) {
        WebScriptObject *scope = [[cframe scopeChain] objectAtIndex:i];
        
        id value = nil;
        @try {
            RetainPtr<NSString> keyNS(AdoptNS, NSStringCreateWithJSStringRef(key));
            value = [scope valueForKey:keyNS.get()];
        } @catch(NSException* localException) { // The value wasn't found.
        }

        resultString = [NSString stringOrNilFromWebScriptResult:value];
    }

    if (!resultString)
        return JSValueMakeUndefined(context);

    return JSValueRefCreateWithNSString(context, resultString);
}

void DebuggerDocument::platformLog(JSStringRef msg)
{
    RetainPtr<NSString> msgNS(AdoptNS, NSStringCreateWithJSStringRef(msg));
    NSLog(@"%@", msgNS.get());
}

