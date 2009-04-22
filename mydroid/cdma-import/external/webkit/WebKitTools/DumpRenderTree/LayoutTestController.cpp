/*
 * Copyright (C) 2007 Apple Inc. All rights reserved.
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

#include "DumpRenderTree.h"
#include "LayoutTestController.h"

#include <JavaScriptCore/JSObjectRef.h>
#include <JavaScriptCore/JSRetainPtr.h>
#include <wtf/Assertions.h>

LayoutTestController::LayoutTestController(bool testRepaintDefault, bool testRepaintSweepHorizontallyDefault)
    : m_dumpAsText(false)
    , m_dumpBackForwardList(false)
    , m_dumpChildFrameScrollPositions(false)
    , m_dumpChildFramesAsText(false)
    , m_dumpDOMAsWebArchive(false)
    , m_dumpSelectionRect(false)
    , m_dumpSourceAsWebArchive(false)
    , m_dumpTitleChanges(false)
    , m_dumpEditingCallbacks(false)
    , m_dumpResourceLoadCallbacks(false)
    , m_dumpFrameLoadCallbacks(false)
    , m_addFileToPasteboardOnDrag(false)
    , m_callCloseOnWebViews(true)
    , m_canOpenWindows(false)
    , m_closeRemainingWindowsWhenComplete(true)
    , m_testRepaint(testRepaintDefault)
    , m_testRepaintSweepHorizontally(testRepaintSweepHorizontallyDefault)
    , m_waitToDump(false)
    , m_windowIsKey(true)
    , m_globalFlag(false)
{
}

// Static Functions

static JSValueRef dumpAsTextCallback(JSContextRef context, JSObjectRef function, JSObjectRef thisObject, size_t argumentCount, const JSValueRef arguments[], JSValueRef* exception)
{
    LayoutTestController* controller = reinterpret_cast<LayoutTestController*>(JSObjectGetPrivate(thisObject));
    controller->setDumpAsText(true);
    return JSValueMakeUndefined(context);
}

static JSValueRef dumpBackForwardListCallback(JSContextRef context, JSObjectRef function, JSObjectRef thisObject, size_t argumentCount, const JSValueRef arguments[], JSValueRef* exception)
{
    LayoutTestController* controller = reinterpret_cast<LayoutTestController*>(JSObjectGetPrivate(thisObject));
    controller->setDumpBackForwardList(true);
    return JSValueMakeUndefined(context);
}

static JSValueRef dumpChildFramesAsTextCallback(JSContextRef context, JSObjectRef function, JSObjectRef thisObject, size_t argumentCount, const JSValueRef arguments[], JSValueRef* exception)
{
    LayoutTestController* controller = reinterpret_cast<LayoutTestController*>(JSObjectGetPrivate(thisObject));
    controller->setDumpChildFramesAsText(true);
    return JSValueMakeUndefined(context);
}

static JSValueRef dumpChildFrameScrollPositionsCallback(JSContextRef context, JSObjectRef function, JSObjectRef thisObject, size_t argumentCount, const JSValueRef arguments[], JSValueRef* exception)
{
    LayoutTestController* controller = reinterpret_cast<LayoutTestController*>(JSObjectGetPrivate(thisObject));
    controller->setDumpChildFrameScrollPositions(true);
    return JSValueMakeUndefined(context);
}

static JSValueRef dumpDOMAsWebArchiveCallback(JSContextRef context, JSObjectRef function, JSObjectRef thisObject, size_t argumentCount, const JSValueRef arguments[], JSValueRef* exception)
{
    LayoutTestController* controller = reinterpret_cast<LayoutTestController*>(JSObjectGetPrivate(thisObject));
    controller->setDumpDOMAsWebArchive(true);
    return JSValueMakeUndefined(context);
}

static JSValueRef dumpEditingCallbacksCallback(JSContextRef context, JSObjectRef function, JSObjectRef thisObject, size_t argumentCount, const JSValueRef arguments[], JSValueRef* exception)
{
    LayoutTestController* controller = reinterpret_cast<LayoutTestController*>(JSObjectGetPrivate(thisObject));
    controller->setDumpEditingCallbacks(true);
    return JSValueMakeUndefined(context);
}

static JSValueRef dumpFrameLoadCallbacksCallback(JSContextRef context, JSObjectRef function, JSObjectRef thisObject, size_t argumentCount, const JSValueRef arguments[], JSValueRef* exception)
{
    LayoutTestController* controller = reinterpret_cast<LayoutTestController*>(JSObjectGetPrivate(thisObject));
    controller->setDumpFrameLoadCallbacks(true);
    return JSValueMakeUndefined(context);
}

static JSValueRef dumpResourceLoadCallbacksCallback(JSContextRef context, JSObjectRef function, JSObjectRef thisObject, size_t argumentCount, const JSValueRef arguments[], JSValueRef* exception)
{
    LayoutTestController* controller = reinterpret_cast<LayoutTestController*>(JSObjectGetPrivate(thisObject));
    controller->setDumpResourceLoadCallbacks(true);
    return JSValueMakeUndefined(context);
}

static JSValueRef dumpSelectionRectCallback(JSContextRef context, JSObjectRef function, JSObjectRef thisObject, size_t argumentCount, const JSValueRef arguments[], JSValueRef* exception)
{
    LayoutTestController* controller = reinterpret_cast<LayoutTestController*>(JSObjectGetPrivate(thisObject));
    controller->setDumpSelectionRect(true);
    return JSValueMakeUndefined(context);
}

static JSValueRef dumpSourceAsWebArchiveCallback(JSContextRef context, JSObjectRef function, JSObjectRef thisObject, size_t argumentCount, const JSValueRef arguments[], JSValueRef* exception)
{
    LayoutTestController* controller = reinterpret_cast<LayoutTestController*>(JSObjectGetPrivate(thisObject));
    controller->setDumpSourceAsWebArchive(true);
    return JSValueMakeUndefined(context);
}

static JSValueRef dumpTitleChangesCallback(JSContextRef context, JSObjectRef function, JSObjectRef thisObject, size_t argumentCount, const JSValueRef arguments[], JSValueRef* exception)
{
    LayoutTestController* controller = reinterpret_cast<LayoutTestController*>(JSObjectGetPrivate(thisObject));
    controller->setDumpTitleChanges(true);
    return JSValueMakeUndefined(context);
}

static JSValueRef pathToLocalResourceCallback(JSContextRef context, JSObjectRef function, JSObjectRef thisObject, size_t argumentCount, const JSValueRef arguments[], JSValueRef* exception)
{
    if (argumentCount < 1)
        return JSValueMakeUndefined(context);

    LayoutTestController* controller = reinterpret_cast<LayoutTestController*>(JSObjectGetPrivate(thisObject));
    JSRetainPtr<JSStringRef> localPath(Adopt, JSValueToStringCopy(context, arguments[0], exception));
    ASSERT(!*exception);

    JSRetainPtr<JSStringRef> convertedPath(Adopt, controller->pathToLocalResource(context, localPath.get()));
    if (!convertedPath)
        return JSValueMakeUndefined(context);

    return JSValueMakeString(context, convertedPath.get());
}

static JSValueRef repaintSweepHorizontallyCallback(JSContextRef context, JSObjectRef function, JSObjectRef thisObject, size_t argumentCount, const JSValueRef arguments[], JSValueRef* exception)
{
    LayoutTestController* controller = reinterpret_cast<LayoutTestController*>(JSObjectGetPrivate(thisObject));
    controller->setTestRepaintSweepHorizontally(true);
    return JSValueMakeUndefined(context);
}

static JSValueRef setCallCloseOnWebViewsCallback(JSContextRef context, JSObjectRef function, JSObjectRef thisObject, size_t argumentCount, const JSValueRef arguments[], JSValueRef* exception)
{
    if (argumentCount < 1)
        return JSValueMakeUndefined(context);

    LayoutTestController* controller = reinterpret_cast<LayoutTestController*>(JSObjectGetPrivate(thisObject));
    controller->setCallCloseOnWebViews(JSValueToBoolean(context, arguments[0]));
    return JSValueMakeUndefined(context);
}

static JSValueRef setCanOpenWindowsCallback(JSContextRef context, JSObjectRef function, JSObjectRef thisObject, size_t argumentCount, const JSValueRef arguments[], JSValueRef* exception)
{
    LayoutTestController* controller = reinterpret_cast<LayoutTestController*>(JSObjectGetPrivate(thisObject));
    controller->setCanOpenWindows(true);
    return JSValueMakeUndefined(context);
}

static JSValueRef setCloseRemainingWindowsWhenCompleteCallback(JSContextRef context, JSObjectRef function, JSObjectRef thisObject, size_t argumentCount, const JSValueRef arguments[], JSValueRef* exception)
{
    if (argumentCount < 1)
        return JSValueMakeUndefined(context);

    LayoutTestController* controller = reinterpret_cast<LayoutTestController*>(JSObjectGetPrivate(thisObject));
    controller->setCloseRemainingWindowsWhenComplete(JSValueToBoolean(context, arguments[0]));
    return JSValueMakeUndefined(context);
}

static JSValueRef testRepaintCallback(JSContextRef context, JSObjectRef function, JSObjectRef thisObject, size_t argumentCount, const JSValueRef arguments[], JSValueRef* exception)
{
    LayoutTestController* controller = reinterpret_cast<LayoutTestController*>(JSObjectGetPrivate(thisObject));
    controller->setTestRepaint(true);
    return JSValueMakeUndefined(context);
}

static JSValueRef addFileToPasteboardOnDragCallback(JSContextRef context, JSObjectRef function, JSObjectRef thisObject, size_t argumentCount, const JSValueRef arguments[], JSValueRef* exception)
{
    LayoutTestController* controller = reinterpret_cast<LayoutTestController*>(JSObjectGetPrivate(thisObject));
    controller->setAddFileToPasteboardOnDrag(true);
    return JSValueMakeUndefined(context);
}

static JSValueRef addDisallowedURLCallback(JSContextRef context, JSObjectRef function, JSObjectRef thisObject, size_t argumentCount, const JSValueRef arguments[], JSValueRef* exception)
{
    // Has mac implementation
    if (argumentCount < 1)
        return JSValueMakeUndefined(context);

    JSRetainPtr<JSStringRef> url(Adopt, JSValueToStringCopy(context, arguments[0], exception));
    ASSERT(!*exception);

    LayoutTestController* controller = reinterpret_cast<LayoutTestController*>(JSObjectGetPrivate(thisObject));
    controller->addDisallowedURL(url.get());

    return JSValueMakeUndefined(context);
}

static JSValueRef clearBackForwardListCallback(JSContextRef context, JSObjectRef function, JSObjectRef thisObject, size_t argumentCount, const JSValueRef arguments[], JSValueRef* exception)
{
    // Has mac & windows implementation
    LayoutTestController* controller = reinterpret_cast<LayoutTestController*>(JSObjectGetPrivate(thisObject));
    controller->clearBackForwardList();

    return JSValueMakeUndefined(context);
}

static JSValueRef clearPersistentUserStyleSheetCallback(JSContextRef context, JSObjectRef function, JSObjectRef thisObject, size_t argumentCount, const JSValueRef arguments[], JSValueRef* exception)
{
    // Has mac & windows implementation
    LayoutTestController* controller = reinterpret_cast<LayoutTestController*>(JSObjectGetPrivate(thisObject));
    controller->clearPersistentUserStyleSheet();

    return JSValueMakeUndefined(context);
}

static JSValueRef decodeHostNameCallback(JSContextRef context, JSObjectRef function, JSObjectRef thisObject, size_t argumentCount, const JSValueRef arguments[], JSValueRef* exception)
{
    // Has mac implementation
    if (argumentCount < 1)
        return JSValueMakeUndefined(context);

    JSRetainPtr<JSStringRef> name(Adopt, JSValueToStringCopy(context, arguments[0], exception));
    ASSERT(!*exception);

    LayoutTestController* controller = reinterpret_cast<LayoutTestController*>(JSObjectGetPrivate(thisObject));
    JSRetainPtr<JSStringRef> decodedHostName(Adopt, controller->copyDecodedHostName(name.get()));
    return JSValueMakeString(context, decodedHostName.get());
}

static JSValueRef displayCallback(JSContextRef context, JSObjectRef function, JSObjectRef thisObject, size_t argumentCount, const JSValueRef arguments[], JSValueRef* exception)
{
    // Has mac & windows implementation
    LayoutTestController* controller = reinterpret_cast<LayoutTestController*>(JSObjectGetPrivate(thisObject));
    controller->display();

    return JSValueMakeUndefined(context);
}

static JSValueRef encodeHostNameCallback(JSContextRef context, JSObjectRef function, JSObjectRef thisObject, size_t argumentCount, const JSValueRef arguments[], JSValueRef* exception)
{
    // Has mac implementation
    if (argumentCount < 1)
        return JSValueMakeUndefined(context);

    JSRetainPtr<JSStringRef> name(Adopt, JSValueToStringCopy(context, arguments[0], exception));
    ASSERT(!*exception);

    LayoutTestController* controller = reinterpret_cast<LayoutTestController*>(JSObjectGetPrivate(thisObject));
    JSRetainPtr<JSStringRef> encodedHostName(Adopt, controller->copyEncodedHostName(name.get()));
    return JSValueMakeString(context, encodedHostName.get());
}

static JSValueRef execCommandCallback(JSContextRef context, JSObjectRef function, JSObjectRef thisObject, size_t argumentCount, const JSValueRef arguments[], JSValueRef* exception)
{
    // Has Mac & Windows implementations.
    if (argumentCount < 1)
        return JSValueMakeUndefined(context);

    JSRetainPtr<JSStringRef> name(Adopt, JSValueToStringCopy(context, arguments[0], exception));
    ASSERT(!*exception);

    // Ignoring the second parameter (userInterface), as this command emulates a manual action.

    JSRetainPtr<JSStringRef> value;
    if (argumentCount >= 3) {
        value.adopt(JSValueToStringCopy(context, arguments[2], exception));
        ASSERT(!*exception);
    } else
        value.adopt(JSStringCreateWithUTF8CString(""));


    LayoutTestController* controller = reinterpret_cast<LayoutTestController*>(JSObjectGetPrivate(thisObject));
    controller->execCommand(name.get(), value.get());

    return JSValueMakeUndefined(context);
}

static JSValueRef keepWebHistoryCallback(JSContextRef context, JSObjectRef function, JSObjectRef thisObject, size_t argumentCount, const JSValueRef arguments[], JSValueRef* exception)
{
    // Has mac implementation
    LayoutTestController* controller = reinterpret_cast<LayoutTestController*>(JSObjectGetPrivate(thisObject));
    controller->keepWebHistory();

    return JSValueMakeUndefined(context);
}

static JSValueRef notifyDoneCallback(JSContextRef context, JSObjectRef function, JSObjectRef thisObject, size_t argumentCount, const JSValueRef arguments[], JSValueRef* exception)
{
    // Has mac & windows implementation
    // May be able to be made platform independant by using shared WorkQueue
    LayoutTestController* controller = reinterpret_cast<LayoutTestController*>(JSObjectGetPrivate(thisObject));
    controller->notifyDone();

    return JSValueMakeUndefined(context);
}

static JSValueRef queueBackNavigationCallback(JSContextRef context, JSObjectRef function, JSObjectRef thisObject, size_t argumentCount, const JSValueRef arguments[], JSValueRef* exception)
{
    // Has mac & windows implementation
    // May be able to be made platform independant by using shared WorkQueue
    if (argumentCount < 1)
        return JSValueMakeUndefined(context);

    double howFarBackDouble = JSValueToNumber(context, arguments[0], exception);
    ASSERT(!*exception);

    LayoutTestController* controller = reinterpret_cast<LayoutTestController*>(JSObjectGetPrivate(thisObject));
    controller->queueBackNavigation(static_cast<int>(howFarBackDouble));

    return JSValueMakeUndefined(context);
}

static JSValueRef queueForwardNavigationCallback(JSContextRef context, JSObjectRef function, JSObjectRef thisObject, size_t argumentCount, const JSValueRef arguments[], JSValueRef* exception)
{
    // Has mac & windows implementation
    // May be able to be made platform independant by using shared WorkQueue
    if (argumentCount < 1)
        return JSValueMakeUndefined(context);

    double howFarForwardDouble = JSValueToNumber(context, arguments[0], exception);
    ASSERT(!*exception);

    LayoutTestController* controller = reinterpret_cast<LayoutTestController*>(JSObjectGetPrivate(thisObject));
    controller->queueForwardNavigation(static_cast<int>(howFarForwardDouble));

    return JSValueMakeUndefined(context);
}

static JSValueRef queueLoadCallback(JSContextRef context, JSObjectRef function, JSObjectRef thisObject, size_t argumentCount, const JSValueRef arguments[], JSValueRef* exception)
{
    // Has mac & windows implementation
    // May be able to be made platform independant by using shared WorkQueue
    if (argumentCount < 1)
        return JSValueMakeUndefined(context);

    JSRetainPtr<JSStringRef> url(Adopt, JSValueToStringCopy(context, arguments[0], exception));
    ASSERT(!*exception);

    JSRetainPtr<JSStringRef> target;
    if (argumentCount >= 2) {
        target.adopt(JSValueToStringCopy(context, arguments[1], exception));
        ASSERT(!*exception);
    } else
        target.adopt(JSStringCreateWithUTF8CString(""));

    LayoutTestController* controller = reinterpret_cast<LayoutTestController*>(JSObjectGetPrivate(thisObject));
    controller->queueLoad(url.get(), target.get());

    return JSValueMakeUndefined(context);
}

static JSValueRef queueReloadCallback(JSContextRef context, JSObjectRef function, JSObjectRef thisObject, size_t argumentCount, const JSValueRef arguments[], JSValueRef* exception)
{
    // Has mac & windows implementation
    // May be able to be made platform independant by using shared WorkQueue

    LayoutTestController* controller = reinterpret_cast<LayoutTestController*>(JSObjectGetPrivate(thisObject));
    controller->queueReload();

    return JSValueMakeUndefined(context);
}

static JSValueRef queueScriptCallback(JSContextRef context, JSObjectRef function, JSObjectRef thisObject, size_t argumentCount, const JSValueRef arguments[], JSValueRef* exception)
{
    // Has mac & windows implementation
    // May be able to be made platform independant by using shared WorkQueue
    if (argumentCount < 1)
        return JSValueMakeUndefined(context);

    JSRetainPtr<JSStringRef> script(Adopt, JSValueToStringCopy(context, arguments[0], exception));
    ASSERT(!*exception);

    LayoutTestController* controller = reinterpret_cast<LayoutTestController*>(JSObjectGetPrivate(thisObject));
    controller->queueScript(script.get());

    return JSValueMakeUndefined(context);
}

static JSValueRef setAcceptsEditingCallback(JSContextRef context, JSObjectRef function, JSObjectRef thisObject, size_t argumentCount, const JSValueRef arguments[], JSValueRef* exception)
{
    // Has mac & windows implementation
    if (argumentCount < 1)
        return JSValueMakeUndefined(context);

    LayoutTestController* controller = reinterpret_cast<LayoutTestController*>(JSObjectGetPrivate(thisObject));
    controller->setAcceptsEditing(JSValueToBoolean(context, arguments[0]));

    return JSValueMakeUndefined(context);
}

static JSValueRef setAuthorAndUserStylesEnabledCallback(JSContextRef context, JSObjectRef function, JSObjectRef thisObject, size_t argumentCount, const JSValueRef arguments[], JSValueRef* exception)
{
    // Has mac & windows implementation
    if (argumentCount < 1)
        return JSValueMakeUndefined(context);

    LayoutTestController* controller = reinterpret_cast<LayoutTestController*>(JSObjectGetPrivate(thisObject));
    controller->setAuthorAndUserStylesEnabled(JSValueToBoolean(context, arguments[0]));

    return JSValueMakeUndefined(context);
}

static JSValueRef setCustomPolicyDelegateCallback(JSContextRef context, JSObjectRef function, JSObjectRef thisObject, size_t argumentCount, const JSValueRef arguments[], JSValueRef* exception)
{
    // Has mac implementation
    if (argumentCount < 1)
        return JSValueMakeUndefined(context);

    LayoutTestController* controller = reinterpret_cast<LayoutTestController*>(JSObjectGetPrivate(thisObject));
    controller->setCustomPolicyDelegate(JSValueToBoolean(context, arguments[0]));

    return JSValueMakeUndefined(context);
}

static JSValueRef setMainFrameIsFirstResponderCallback(JSContextRef context, JSObjectRef function, JSObjectRef thisObject, size_t argumentCount, const JSValueRef arguments[], JSValueRef* exception)
{
    // Has mac implementation
    if (argumentCount < 1)
        return JSValueMakeUndefined(context);

    LayoutTestController* controller = reinterpret_cast<LayoutTestController*>(JSObjectGetPrivate(thisObject));
    controller->setMainFrameIsFirstResponder(JSValueToBoolean(context, arguments[0]));

    return JSValueMakeUndefined(context);
}

static JSValueRef setPersistentUserStyleSheetLocationCallback(JSContextRef context, JSObjectRef function, JSObjectRef thisObject, size_t argumentCount, const JSValueRef arguments[], JSValueRef* exception)
{
    // Has mac implementation
    if (argumentCount < 1)
        return JSValueMakeUndefined(context);

    JSRetainPtr<JSStringRef> path(Adopt, JSValueToStringCopy(context, arguments[0], exception));
    ASSERT(!*exception);

    LayoutTestController* controller = reinterpret_cast<LayoutTestController*>(JSObjectGetPrivate(thisObject));
    controller->setPersistentUserStyleSheetLocation(path.get());

    return JSValueMakeUndefined(context);
}

static JSValueRef setPrivateBrowsingEnabledCallback(JSContextRef context, JSObjectRef function, JSObjectRef thisObject, size_t argumentCount, const JSValueRef arguments[], JSValueRef* exception)
{
    // Has mac & windows implementation
    if (argumentCount < 1)
        return JSValueMakeUndefined(context);

    LayoutTestController* controller = reinterpret_cast<LayoutTestController*>(JSObjectGetPrivate(thisObject));
    controller->setPrivateBrowsingEnabled(JSValueToBoolean(context, arguments[0]));

    return JSValueMakeUndefined(context);
}

static JSValueRef setTabKeyCyclesThroughElementsCallback(JSContextRef context, JSObjectRef function, JSObjectRef thisObject, size_t argumentCount, const JSValueRef arguments[], JSValueRef* exception)
{
    // Has mac & windows implementation
    if (argumentCount < 1)
        return JSValueMakeUndefined(context);

    LayoutTestController* controller = reinterpret_cast<LayoutTestController*>(JSObjectGetPrivate(thisObject));
    controller->setTabKeyCyclesThroughElements(JSValueToBoolean(context, arguments[0]));

    return JSValueMakeUndefined(context);
}

static JSValueRef setUseDashboardCompatibilityModeCallback(JSContextRef context, JSObjectRef function, JSObjectRef thisObject, size_t argumentCount, const JSValueRef arguments[], JSValueRef* exception)
{
    // Has mac implementation
    if (argumentCount < 1)
        return JSValueMakeUndefined(context);

    LayoutTestController* controller = reinterpret_cast<LayoutTestController*>(JSObjectGetPrivate(thisObject));
    controller->setUseDashboardCompatibilityMode(JSValueToBoolean(context, arguments[0]));

    return JSValueMakeUndefined(context);
}

static JSValueRef setUserStyleSheetEnabledCallback(JSContextRef context, JSObjectRef function, JSObjectRef thisObject, size_t argumentCount, const JSValueRef arguments[], JSValueRef* exception)
{
    // Has mac implementation
    if (argumentCount < 1)
        return JSValueMakeUndefined(context);

    LayoutTestController* controller = reinterpret_cast<LayoutTestController*>(JSObjectGetPrivate(thisObject));
    controller->setUserStyleSheetEnabled(JSValueToBoolean(context, arguments[0]));

    return JSValueMakeUndefined(context);
}

static JSValueRef setUserStyleSheetLocationCallback(JSContextRef context, JSObjectRef function, JSObjectRef thisObject, size_t argumentCount, const JSValueRef arguments[], JSValueRef* exception)
{
    // Has mac implementation
    if (argumentCount < 1)
        return JSValueMakeUndefined(context);

    JSRetainPtr<JSStringRef> path(Adopt, JSValueToStringCopy(context, arguments[0], exception));
    ASSERT(!*exception);

    LayoutTestController* controller = reinterpret_cast<LayoutTestController*>(JSObjectGetPrivate(thisObject));
    controller->setUserStyleSheetLocation(path.get());

    return JSValueMakeUndefined(context);
}

static JSValueRef setWindowIsKeyCallback(JSContextRef context, JSObjectRef function, JSObjectRef thisObject, size_t argumentCount, const JSValueRef arguments[], JSValueRef* exception)
{
    // Has mac implementation
    if (argumentCount < 1)
        return JSValueMakeUndefined(context);

    LayoutTestController* controller = reinterpret_cast<LayoutTestController*>(JSObjectGetPrivate(thisObject));
    controller->setWindowIsKey(JSValueToBoolean(context, arguments[0]));

    return JSValueMakeUndefined(context);
}

static JSValueRef waitUntilDoneCallback(JSContextRef context, JSObjectRef function, JSObjectRef thisObject, size_t argumentCount, const JSValueRef arguments[], JSValueRef* exception)
{
    // Has mac & windows implementation
    LayoutTestController* controller = reinterpret_cast<LayoutTestController*>(JSObjectGetPrivate(thisObject));
    controller->setWaitToDump(true);

    return JSValueMakeUndefined(context);
}

static JSValueRef windowCountCallback(JSContextRef context, JSObjectRef function, JSObjectRef thisObject, size_t argumentCount, const JSValueRef arguments[], JSValueRef* exception)
{
    // Has mac implementation
    LayoutTestController* controller = reinterpret_cast<LayoutTestController*>(JSObjectGetPrivate(thisObject));
    int windows = controller->windowCount();
    return JSValueMakeNumber(context, windows);
}

static JSValueRef setPopupBlockingEnabledCallback(JSContextRef context, JSObjectRef function, JSObjectRef thisObject, size_t argumentCount, const JSValueRef arguments[], JSValueRef* exception)
{
    // Has mac & windows implementation
    if (argumentCount < 1)
        return JSValueMakeUndefined(context);

    LayoutTestController* controller = reinterpret_cast<LayoutTestController*>(JSObjectGetPrivate(thisObject));
    controller->setPopupBlockingEnabled(JSValueToBoolean(context, arguments[0]));

    return JSValueMakeUndefined(context);
}


// Static Values

static JSValueRef getGlobalFlagCallback(JSContextRef context, JSObjectRef thisObject, JSStringRef propertyName, JSValueRef* exception)
{
    LayoutTestController* controller = reinterpret_cast<LayoutTestController*>(JSObjectGetPrivate(thisObject));
    return JSValueMakeBoolean(context, controller->globalFlag());
}

static bool setGlobalFlagCallback(JSContextRef context, JSObjectRef thisObject, JSStringRef propertyName, JSValueRef value, JSValueRef* exception)
{
    LayoutTestController* controller = reinterpret_cast<LayoutTestController*>(JSObjectGetPrivate(thisObject));
    controller->setGlobalFlag(JSValueToBoolean(context, value));
    return true;
}

// Object Creation

void LayoutTestController::makeWindowObject(JSContextRef context, JSObjectRef windowObject, JSValueRef* exception)
{
    JSRetainPtr<JSStringRef> layoutTestContollerStr(Adopt, JSStringCreateWithUTF8CString("layoutTestController"));
    JSValueRef layoutTestContollerObject = JSObjectMake(context, getJSClass(), this);
    JSObjectSetProperty(context, windowObject, layoutTestContollerStr.get(), layoutTestContollerObject, kJSPropertyAttributeReadOnly | kJSPropertyAttributeDontDelete, exception);
}

JSClassRef LayoutTestController::getJSClass()
{
    static JSClassRef layoutTestControllerClass;

    if (!layoutTestControllerClass) {
        JSStaticValue* staticValues = LayoutTestController::staticValues();
        JSStaticFunction* staticFunctions = LayoutTestController::staticFunctions();
        JSClassDefinition classDefinition = {
            0, kJSClassAttributeNone, "LayoutTestController", 0, staticValues, staticFunctions,
            0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0
        };

        layoutTestControllerClass = JSClassCreate(&classDefinition);
    }

    return layoutTestControllerClass;
}

JSStaticValue* LayoutTestController::staticValues()
{
    static JSStaticValue staticValues[] = {
        { "globalFlag", getGlobalFlagCallback, setGlobalFlagCallback, kJSPropertyAttributeNone },
        { 0, 0, 0, 0 }
    };
    return staticValues;

}

JSStaticFunction* LayoutTestController::staticFunctions()
{
    static JSStaticFunction staticFunctions[] = {
        { "addDisallowedURL", addDisallowedURLCallback, kJSPropertyAttributeReadOnly | kJSPropertyAttributeDontDelete },
        { "addFileToPasteboardOnDrag", addFileToPasteboardOnDragCallback, kJSPropertyAttributeReadOnly | kJSPropertyAttributeDontDelete },
        { "clearBackForwardList", clearBackForwardListCallback, kJSPropertyAttributeReadOnly | kJSPropertyAttributeDontDelete },
        { "clearPersistentUserStyleSheet", clearPersistentUserStyleSheetCallback, kJSPropertyAttributeReadOnly | kJSPropertyAttributeDontDelete },
        { "decodeHostName", decodeHostNameCallback, kJSPropertyAttributeReadOnly | kJSPropertyAttributeDontDelete },
        { "display", displayCallback, kJSPropertyAttributeReadOnly | kJSPropertyAttributeDontDelete },
        { "dumpAsText", dumpAsTextCallback, kJSPropertyAttributeReadOnly | kJSPropertyAttributeDontDelete },
        { "dumpBackForwardList", dumpBackForwardListCallback, kJSPropertyAttributeReadOnly | kJSPropertyAttributeDontDelete },
        { "dumpChildFramesAsText", dumpChildFramesAsTextCallback, kJSPropertyAttributeReadOnly | kJSPropertyAttributeDontDelete },
        { "dumpChildFrameScrollPositions", dumpChildFrameScrollPositionsCallback, kJSPropertyAttributeReadOnly | kJSPropertyAttributeDontDelete },
        { "dumpDOMAsWebArchive", dumpDOMAsWebArchiveCallback, kJSPropertyAttributeReadOnly | kJSPropertyAttributeDontDelete },
        { "dumpEditingCallbacks", dumpEditingCallbacksCallback, kJSPropertyAttributeReadOnly | kJSPropertyAttributeDontDelete },
        { "dumpFrameLoadCallbacks", dumpFrameLoadCallbacksCallback, kJSPropertyAttributeReadOnly | kJSPropertyAttributeDontDelete },
        { "dumpResourceLoadCallbacks", dumpResourceLoadCallbacksCallback, kJSPropertyAttributeReadOnly | kJSPropertyAttributeDontDelete },
        { "dumpSelectionRect", dumpSelectionRectCallback, kJSPropertyAttributeReadOnly | kJSPropertyAttributeDontDelete },
        { "dumpSourceAsWebArchive", dumpSourceAsWebArchiveCallback, kJSPropertyAttributeReadOnly | kJSPropertyAttributeDontDelete },
        { "dumpTitleChanges", dumpTitleChangesCallback, kJSPropertyAttributeReadOnly | kJSPropertyAttributeDontDelete },
        { "encodeHostName", encodeHostNameCallback, kJSPropertyAttributeReadOnly | kJSPropertyAttributeDontDelete },
        { "execCommand", execCommandCallback, kJSPropertyAttributeReadOnly | kJSPropertyAttributeDontDelete },
        { "keepWebHistory", keepWebHistoryCallback, kJSPropertyAttributeReadOnly | kJSPropertyAttributeDontDelete },
        { "notifyDone", notifyDoneCallback, kJSPropertyAttributeReadOnly | kJSPropertyAttributeDontDelete },
        { "pathToLocalResource", pathToLocalResourceCallback, kJSPropertyAttributeReadOnly | kJSPropertyAttributeDontDelete },
        { "queueBackNavigation", queueBackNavigationCallback, kJSPropertyAttributeReadOnly | kJSPropertyAttributeDontDelete },
        { "queueForwardNavigation", queueForwardNavigationCallback, kJSPropertyAttributeReadOnly | kJSPropertyAttributeDontDelete },
        { "queueLoad", queueLoadCallback, kJSPropertyAttributeReadOnly | kJSPropertyAttributeDontDelete },
        { "queueReload", queueReloadCallback, kJSPropertyAttributeReadOnly | kJSPropertyAttributeDontDelete },
        { "queueScript", queueScriptCallback, kJSPropertyAttributeReadOnly | kJSPropertyAttributeDontDelete },
        { "repaintSweepHorizontally", repaintSweepHorizontallyCallback, kJSPropertyAttributeReadOnly | kJSPropertyAttributeDontDelete },
        { "setAcceptsEditing", setAcceptsEditingCallback, kJSPropertyAttributeReadOnly | kJSPropertyAttributeDontDelete },
        { "setAuthorAndUserStylesEnabled", setAuthorAndUserStylesEnabledCallback, kJSPropertyAttributeReadOnly | kJSPropertyAttributeDontDelete },
        { "setCallCloseOnWebViews", setCallCloseOnWebViewsCallback, kJSPropertyAttributeReadOnly | kJSPropertyAttributeDontDelete },
        { "setCanOpenWindows", setCanOpenWindowsCallback, kJSPropertyAttributeReadOnly | kJSPropertyAttributeDontDelete },
        { "setCloseRemainingWindowsWhenComplete", setCloseRemainingWindowsWhenCompleteCallback, kJSPropertyAttributeReadOnly | kJSPropertyAttributeDontDelete },
        { "setCustomPolicyDelegate", setCustomPolicyDelegateCallback, kJSPropertyAttributeReadOnly | kJSPropertyAttributeDontDelete },
        { "setMainFrameIsFirstResponder", setMainFrameIsFirstResponderCallback, kJSPropertyAttributeReadOnly | kJSPropertyAttributeDontDelete },
        { "setPersistentUserStyleSheetLocation", setPersistentUserStyleSheetLocationCallback, kJSPropertyAttributeReadOnly | kJSPropertyAttributeDontDelete },
        { "setPrivateBrowsingEnabled", setPrivateBrowsingEnabledCallback, kJSPropertyAttributeReadOnly | kJSPropertyAttributeDontDelete },
        { "setPopupBlockingEnabled", setPopupBlockingEnabledCallback, kJSPropertyAttributeReadOnly | kJSPropertyAttributeDontDelete },
        { "setTabKeyCyclesThroughElements", setTabKeyCyclesThroughElementsCallback, kJSPropertyAttributeReadOnly | kJSPropertyAttributeDontDelete },
        { "setUseDashboardCompatibilityMode", setUseDashboardCompatibilityModeCallback, kJSPropertyAttributeReadOnly | kJSPropertyAttributeDontDelete },
        { "setUserStyleSheetEnabled", setUserStyleSheetEnabledCallback, kJSPropertyAttributeReadOnly | kJSPropertyAttributeDontDelete },
        { "setUserStyleSheetLocation", setUserStyleSheetLocationCallback, kJSPropertyAttributeReadOnly | kJSPropertyAttributeDontDelete },
        { "setWindowIsKey", setWindowIsKeyCallback, kJSPropertyAttributeReadOnly | kJSPropertyAttributeDontDelete },
        { "testRepaint", testRepaintCallback, kJSPropertyAttributeReadOnly | kJSPropertyAttributeDontDelete },
        { "waitUntilDone", waitUntilDoneCallback, kJSPropertyAttributeReadOnly | kJSPropertyAttributeDontDelete },
        { "windowCount", windowCountCallback, kJSPropertyAttributeReadOnly | kJSPropertyAttributeDontDelete },
        { 0, 0, 0 }
    };

    return staticFunctions;
}


