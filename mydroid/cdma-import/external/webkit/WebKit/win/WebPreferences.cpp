/*
 * Copyright (C) 2006, 2007 Apple Inc.  All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 * 1. Redistributions of source code must retain the above copyright
 *    notice, this list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright
 *    notice, this list of conditions and the following disclaimer in the
 *    documentation and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY APPLE COMPUTER, INC. ``AS IS'' AND ANY
 * EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR
 * PURPOSE ARE DISCLAIMED.  IN NO EVENT SHALL APPLE COMPUTER, INC. OR
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
 * PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR
 * PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY
 * OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE. 
 */

#include "config.h"
#include "WebKit.h"
#include "WebKitDLL.h"
#include "WebPreferences.h"
#include "WebKit.h"

#include "WebNotificationCenter.h"
#include "WebPreferenceKeysPrivate.h"

#pragma warning( push, 0 )
#include <WebCore/CString.h>
#include <WebCore/FileSystem.h>
#include <WebCore/Font.h>
#include <WebCore/PlatformString.h>
#include <WebCore/StringHash.h>
#include "WebLocalizableStrings.h"
#pragma warning( pop )

#include <CoreFoundation/CoreFoundation.h>
#include <CoreGraphics/CoreGraphics.h>
#include <shlobj.h>
#include <shfolder.h>
#include <tchar.h>
#include <WebKitSystemInterface/WebKitSystemInterface.h>
#include <wtf/HashMap.h>
#include <wtf/OwnPtr.h>
#include <wtf/Vector.h>

using namespace WebCore;

static String preferencesPath()
{
    static String path = pathByAppendingComponent(roamingUserSpecificStorageDirectory(), "WebKitPreferences.plist");
    return path;
}

// WebPreferences ----------------------------------------------------------------

CFDictionaryRef WebPreferences::s_defaultSettings = 0;

static HashMap<WebCore::String, WebPreferences*> webPreferencesInstances;

WebPreferences* WebPreferences::sharedStandardPreferences()
{
    static WebPreferences* standardPreferences;
    if (!standardPreferences) {
        standardPreferences = WebPreferences::createInstance();
        standardPreferences->load();
        standardPreferences->setAutosaves(TRUE);
    }

    return standardPreferences;
}

WebPreferences::WebPreferences()
    : m_refCount(0)
    , m_autoSaves(0)
    , m_automaticallyDetectsCacheModel(true)
    , m_numWebViews(0)
{
    gClassCount++;
}

WebPreferences::~WebPreferences()
{
    gClassCount--;
}

WebPreferences* WebPreferences::createInstance()
{
    WebPreferences* instance = new WebPreferences();
    instance->AddRef();
    return instance;
}

HRESULT WebPreferences::postPreferencesChangesNotification()
{
    IWebNotificationCenter* nc = WebNotificationCenter::defaultCenterInternal();
    HRESULT hr = nc->postNotificationName(webPreferencesChangedNotification(), static_cast<IWebPreferences*>(this), 0);
    if (FAILED(hr))
        return hr;

    return S_OK;
}

WebPreferences* WebPreferences::getInstanceForIdentifier(BSTR identifier)
{
    if (!identifier)
        return sharedStandardPreferences();

    WebCore::String identifierString(identifier, SysStringLen(identifier));
    return webPreferencesInstances.get(identifierString);
}

void WebPreferences::setInstance(WebPreferences* instance, BSTR identifier)
{
    if (!identifier || !instance)
        return;
    WebCore::String identifierString(identifier, SysStringLen(identifier));
    webPreferencesInstances.add(identifierString, instance);
}

void WebPreferences::removeReferenceForIdentifier(BSTR identifier)
{
    if (!identifier || webPreferencesInstances.isEmpty())
        return;

    WebCore::String identifierString(identifier, SysStringLen(identifier));
    WebPreferences* webPreference = webPreferencesInstances.get(identifierString);
    if (webPreference && webPreference->m_refCount == 1)
        webPreferencesInstances.remove(identifierString);
}

void WebPreferences::initializeDefaultSettings()
{
    if (s_defaultSettings)
        return;

    CFMutableDictionaryRef defaults = CFDictionaryCreateMutable(0, 0, &kCFTypeDictionaryKeyCallBacks, &kCFTypeDictionaryValueCallBacks);

    CFDictionaryAddValue(defaults, CFSTR(WebKitStandardFontPreferenceKey), CFSTR("Times New Roman"));
    CFDictionaryAddValue(defaults, CFSTR(WebKitFixedFontPreferenceKey), CFSTR("Courier New"));
    CFDictionaryAddValue(defaults, CFSTR(WebKitSerifFontPreferenceKey), CFSTR("Times New Roman"));
    CFDictionaryAddValue(defaults, CFSTR(WebKitSansSerifFontPreferenceKey), CFSTR("Arial"));
    CFDictionaryAddValue(defaults, CFSTR(WebKitCursiveFontPreferenceKey), CFSTR("Comic Sans MS"));
    CFDictionaryAddValue(defaults, CFSTR(WebKitFantasyFontPreferenceKey), CFSTR("Comic Sans MS"));
    CFDictionaryAddValue(defaults, CFSTR(WebKitMinimumFontSizePreferenceKey), CFSTR("1"));
    CFDictionaryAddValue(defaults, CFSTR(WebKitMinimumLogicalFontSizePreferenceKey), CFSTR("9"));
    CFDictionaryAddValue(defaults, CFSTR(WebKitDefaultFontSizePreferenceKey), CFSTR("16"));
    CFDictionaryAddValue(defaults, CFSTR(WebKitDefaultFixedFontSizePreferenceKey), CFSTR("13"));
    WebCore::String defaultDefaultEncoding(LPCTSTR_UI_STRING("ISO-8859-1", "The default, default character encoding"));
    CFDictionaryAddValue(defaults, CFSTR(WebKitDefaultTextEncodingNamePreferenceKey), defaultDefaultEncoding.createCFString());

    CFDictionaryAddValue(defaults, CFSTR(WebKitUserStyleSheetEnabledPreferenceKey), kCFBooleanFalse);
    CFDictionaryAddValue(defaults, CFSTR(WebKitUserStyleSheetLocationPreferenceKey), CFSTR(""));
    CFDictionaryAddValue(defaults, CFSTR(WebKitShouldPrintBackgroundsPreferenceKey), kCFBooleanFalse);
    CFDictionaryAddValue(defaults, CFSTR(WebKitTextAreasAreResizablePreferenceKey), kCFBooleanFalse);
    CFDictionaryAddValue(defaults, CFSTR(WebKitJavaEnabledPreferenceKey), kCFBooleanTrue);
    CFDictionaryAddValue(defaults, CFSTR(WebKitJavaScriptEnabledPreferenceKey), kCFBooleanTrue);
    CFDictionaryAddValue(defaults, CFSTR(WebKitJavaScriptCanOpenWindowsAutomaticallyPreferenceKey), kCFBooleanTrue);
    CFDictionaryAddValue(defaults, CFSTR(WebKitPluginsEnabledPreferenceKey), kCFBooleanTrue);
    CFDictionaryAddValue(defaults, CFSTR(WebKitAllowAnimatedImagesPreferenceKey), kCFBooleanTrue);
    CFDictionaryAddValue(defaults, CFSTR(WebKitAllowAnimatedImageLoopingPreferenceKey), kCFBooleanTrue);
    CFDictionaryAddValue(defaults, CFSTR(WebKitDisplayImagesKey), kCFBooleanTrue);
    CFDictionaryAddValue(defaults, CFSTR(WebKitBackForwardCacheExpirationIntervalKey), CFSTR("1800"));
    CFDictionaryAddValue(defaults, CFSTR(WebKitTabToLinksPreferenceKey), kCFBooleanFalse);
    CFDictionaryAddValue(defaults, CFSTR(WebKitPrivateBrowsingEnabledPreferenceKey), kCFBooleanFalse);
    CFDictionaryAddValue(defaults, CFSTR(WebKitRespectStandardStyleKeyEquivalentsPreferenceKey), kCFBooleanFalse);
    CFDictionaryAddValue(defaults, CFSTR(WebKitShowsURLsInToolTipsPreferenceKey), kCFBooleanFalse);
    CFDictionaryAddValue(defaults, CFSTR(WebKitPDFDisplayModePreferenceKey), CFSTR("1"));
    CFDictionaryAddValue(defaults, CFSTR(WebKitPDFScaleFactorPreferenceKey), CFSTR("0"));

    RetainPtr<CFStringRef> linkBehaviorStringRef(AdoptCF, CFStringCreateWithFormat(0, 0, CFSTR("%d"), WebKitEditableLinkDefaultBehavior));
    CFDictionaryAddValue(defaults, CFSTR(WebKitEditableLinkBehaviorPreferenceKey), linkBehaviorStringRef.get());

    CFDictionaryAddValue(defaults, CFSTR(WebKitHistoryItemLimitKey), CFSTR("1000"));
    CFDictionaryAddValue(defaults, CFSTR(WebKitHistoryAgeInDaysLimitKey), CFSTR("7"));
    CFDictionaryAddValue(defaults, CFSTR(WebKitIconDatabaseLocationKey), CFSTR(""));
    CFDictionaryAddValue(defaults, CFSTR(WebKitIconDatabaseEnabledPreferenceKey), kCFBooleanTrue);
    CFDictionaryAddValue(defaults, CFSTR(WebKitFontSmoothingTypePreferenceKey), CFSTR("2"));
    CFDictionaryAddValue(defaults, CFSTR(WebKitCookieStorageAcceptPolicyPreferenceKey), CFSTR("2"));
    CFDictionaryAddValue(defaults, CFSTR(WebContinuousSpellCheckingEnabledPreferenceKey), kCFBooleanFalse);
    CFDictionaryAddValue(defaults, CFSTR(WebGrammarCheckingEnabledPreferenceKey), kCFBooleanFalse);
    CFDictionaryAddValue(defaults, CFSTR(AllowContinuousSpellCheckingPreferenceKey), kCFBooleanTrue);
    CFDictionaryAddValue(defaults, CFSTR(WebKitUsesPageCachePreferenceKey), kCFBooleanTrue);

    RetainPtr<CFStringRef> cacheModelRef(AdoptCF, CFStringCreateWithFormat(0, 0, CFSTR("%d"), WebCacheModelDocumentViewer));
    CFDictionaryAddValue(defaults, CFSTR(WebKitCacheModelPreferenceKey), cacheModelRef.get());

    CFDictionaryAddValue(defaults, CFSTR(WebKitAuthorAndUserStylesEnabledPreferenceKey), kCFBooleanTrue);

    s_defaultSettings = defaults;
}

const void* WebPreferences::valueForKey(CFStringRef key)
{
    const void* value = CFDictionaryGetValue(m_privatePrefs.get(), key);
    if (!value)
        value = CFDictionaryGetValue(s_defaultSettings, key);

    return value;
}

BSTR WebPreferences::stringValueForKey(CFStringRef key)
{
    CFStringRef str = (CFStringRef)valueForKey(key);
    
    if (!str || (CFGetTypeID(str) != CFStringGetTypeID()))
        return 0;

    CFIndex length = CFStringGetLength(str);
    const UniChar* uniChars = CFStringGetCharactersPtr(str);
    if (uniChars)
        return SysAllocStringLen((LPCTSTR)uniChars, length);

    BSTR bstr = SysAllocStringLen(0, length);
    if (!bstr)
        return 0;

    if (!CFStringGetCString(str, (char*)bstr, (length+1)*sizeof(WCHAR), kCFStringEncodingUTF16)) {
        SysFreeString(bstr);
        return 0;
    }
        
    bstr[length] = 0;
    return bstr;
}

int WebPreferences::integerValueForKey(CFStringRef key)
{
    CFTypeRef cfVal = (CFStringRef)valueForKey(key);
    if (!cfVal)
        return 0;

    CFTypeID cfType = CFGetTypeID(cfVal);
    if (cfType == CFStringGetTypeID())
        return CFStringGetIntValue((CFStringRef)cfVal);
    else if (cfType == CFBooleanGetTypeID()) {
        Boolean boolVal = CFBooleanGetValue((CFBooleanRef)cfVal);
        return (boolVal) ? 1 : 0;
    }
    else if (cfType == CFNumberGetTypeID()) {
        int val = 0;
        CFNumberGetValue((CFNumberRef)cfVal, kCFNumberSInt32Type, &val);
        return val;
    }

    return 0;
}

BOOL WebPreferences::boolValueForKey(CFStringRef key)
{
    return (integerValueForKey(key) != 0);
}

float WebPreferences::floatValueForKey(CFStringRef key)
{
    CFTypeRef cfVal = (CFStringRef)valueForKey(key);
    if (!cfVal)
        return 0.0;

    CFTypeID cfType = CFGetTypeID(cfVal);
    if (cfType == CFStringGetTypeID())
        return (float)CFStringGetDoubleValue((CFStringRef)cfVal);
    else if (cfType == CFBooleanGetTypeID()) {
        Boolean boolVal = CFBooleanGetValue((CFBooleanRef)cfVal);
        return (boolVal) ? 1.0f : 0.0f;
    }
    else if (cfType == CFNumberGetTypeID()) {
        float val = 0.0;
        CFNumberGetValue((CFNumberRef)cfVal, kCFNumberFloatType, &val);
        return val;
    }

    return 0.0;
}

LONGLONG WebPreferences::longlongValueForKey(CFStringRef key)
{
    CFTypeRef cfVal = (CFTypeRef) valueForKey(key);
    if (!cfVal)
        return 0;

    CFTypeID cfType = CFGetTypeID(cfVal);
    if (cfType == CFStringGetTypeID())
        return (LONGLONG) CFStringGetIntValue((CFStringRef)cfVal);
    else if (cfType == CFBooleanGetTypeID()) {
        Boolean boolVal = CFBooleanGetValue((CFBooleanRef)cfVal);
        return (boolVal) ? 1 : 0;
    }
    else if (cfType == CFNumberGetTypeID()) {
        LONGLONG val = 0;
        CFNumberGetValue((CFNumberRef)cfVal, kCFNumberLongLongType, &val);
        return val;
    }

    return 0;
}

void WebPreferences::setStringValue(CFStringRef key, LPCTSTR value)
{
    BSTR val = stringValueForKey(key);
    if (val && !_tcscmp(val, value))
        return;
    SysFreeString(val);
    
    RetainPtr<CFStringRef> valueRef(AdoptCF,
        CFStringCreateWithCharactersNoCopy(0, (UniChar*)_wcsdup(value), (CFIndex)_tcslen(value), kCFAllocatorMalloc));
    CFDictionarySetValue(m_privatePrefs.get(), key, valueRef.get());
    if (m_autoSaves)
        save();

    postPreferencesChangesNotification();
}

void WebPreferences::setIntegerValue(CFStringRef key, int value)
{
    if (integerValueForKey(key) == value)
        return;

    RetainPtr<CFNumberRef> valueRef(AdoptCF, CFNumberCreate(0, kCFNumberSInt32Type, &value));
    CFDictionarySetValue(m_privatePrefs.get(), key, valueRef.get());
    if (m_autoSaves)
        save();

    postPreferencesChangesNotification();
}

void WebPreferences::setBoolValue(CFStringRef key, BOOL value)
{
    if (boolValueForKey(key) == value)
        return;

    CFDictionarySetValue(m_privatePrefs.get(), key, value ? kCFBooleanTrue : kCFBooleanFalse);
    if (m_autoSaves)
        save();

    postPreferencesChangesNotification();
}

void WebPreferences::setLongLongValue(CFStringRef key, LONGLONG value)
{
    if (longlongValueForKey(key) == value)
        return;

    RetainPtr<CFNumberRef> valueRef(AdoptCF, CFNumberCreate(0, kCFNumberLongLongType, &value));
    CFDictionarySetValue(m_privatePrefs.get(), key, valueRef.get());
    if (m_autoSaves)
        save();

    postPreferencesChangesNotification();
}

BSTR WebPreferences::webPreferencesChangedNotification()
{
    static BSTR webPreferencesChangedNotification = SysAllocString(WebPreferencesChangedNotification);
    return webPreferencesChangedNotification;
}

BSTR WebPreferences::webPreferencesRemovedNotification()
{
    static BSTR webPreferencesRemovedNotification = SysAllocString(WebPreferencesRemovedNotification);
    return webPreferencesRemovedNotification;
}
void WebPreferences::save()
{
    RetainPtr<CFWriteStreamRef> stream(AdoptCF,
        CFWriteStreamCreateWithAllocatedBuffers(kCFAllocatorDefault, kCFAllocatorDefault)); 
    if (!stream)
        return;

    if (!CFWriteStreamOpen(stream.get()))
        return;

    if (!CFPropertyListWriteToStream(m_privatePrefs.get(), stream.get(), kCFPropertyListXMLFormat_v1_0, 0)) {
        CFWriteStreamClose(stream.get());
        return;
    }
    CFWriteStreamClose(stream.get());

    RetainPtr<CFDataRef> dataRef(AdoptCF,
        (CFDataRef)CFWriteStreamCopyProperty(stream.get(), kCFStreamPropertyDataWritten));
    if (!dataRef)
        return;

    safeCreateFile(preferencesPath(), dataRef.get());
}

void WebPreferences::load()
{
    initializeDefaultSettings();

    CString path = preferencesPath().utf8();

    RetainPtr<CFURLRef> urlRef(AdoptCF, CFURLCreateFromFileSystemRepresentation(0, reinterpret_cast<const UInt8*>(path.data()), path.length(), false));
    if (!urlRef)
        return;

    RetainPtr<CFReadStreamRef> stream(AdoptCF, CFReadStreamCreateWithFile(0, urlRef.get()));
    if (!stream) 
        return;

    if (CFReadStreamOpen(stream.get())) {
        CFPropertyListFormat format = kCFPropertyListBinaryFormat_v1_0 | kCFPropertyListXMLFormat_v1_0;
        RetainPtr<CFPropertyListRef> plist(AdoptCF, CFPropertyListCreateFromStream(0, stream.get(), 0, kCFPropertyListMutableContainersAndLeaves, &format, 0));
        CFReadStreamClose(stream.get());

        if (CFGetTypeID(plist.get()) == CFDictionaryGetTypeID())
            m_privatePrefs.adoptCF(const_cast<CFMutableDictionaryRef>(reinterpret_cast<CFDictionaryRef>(plist.releaseRef())));
    }

    if (!m_privatePrefs)
        m_privatePrefs.adoptCF(CFDictionaryCreateMutable(0, 0, &kCFTypeDictionaryKeyCallBacks, &kCFTypeDictionaryValueCallBacks));

    migrateDefaultSettingsFromSafari3Beta();
}

void WebPreferences::migrateDefaultSettingsFromSafari3Beta()
{
    // The "migration" happening here is a one-time removal of any default values
    // that were stored in the user's preferences due to <rdar://problem/5214504>.

    ASSERT(s_defaultSettings);
    if (!m_privatePrefs)
        return;

    CFStringRef didMigrateKey = CFSTR(WebKitDidMigrateDefaultSettingsFromSafari3BetaPreferenceKey);
    if (boolValueForKey(didMigrateKey))
        return;

    removeValuesMatchingDefaultSettings();

    bool oldValue = m_autoSaves;
    m_autoSaves = true;
    setBoolValue(didMigrateKey, TRUE);
    m_autoSaves = oldValue;
}

void WebPreferences::removeValuesMatchingDefaultSettings()
{
    ASSERT(m_privatePrefs);

    int count = CFDictionaryGetCount(m_privatePrefs.get());
    if (count <= 0)
        return;

    Vector<CFTypeRef> keys(count);
    Vector<CFTypeRef> values(count);
    CFDictionaryGetKeysAndValues(m_privatePrefs.get(), keys.data(), values.data());

    for (int i = 0; i < count; ++i) {
        if (!values[i])
            continue;

        CFTypeRef defaultValue = CFDictionaryGetValue(s_defaultSettings, keys[i]);
        if (!defaultValue)
            continue;

        if (CFEqual(values[i], defaultValue))
            CFDictionaryRemoveValue(m_privatePrefs.get(), keys[i]);
    }
}

// IUnknown -------------------------------------------------------------------

HRESULT STDMETHODCALLTYPE WebPreferences::QueryInterface(REFIID riid, void** ppvObject)
{
    *ppvObject = 0;
    if (IsEqualGUID(riid, IID_IUnknown))
        *ppvObject = static_cast<IWebPreferences*>(this);
    else if (IsEqualGUID(riid, IID_IWebPreferences))
        *ppvObject = static_cast<IWebPreferences*>(this);
    else if (IsEqualGUID(riid, IID_IWebPreferencesPrivate))
        *ppvObject = static_cast<IWebPreferencesPrivate*>(this);
    else if (IsEqualGUID(riid, CLSID_WebPreferences))
        *ppvObject = this;
    else
        return E_NOINTERFACE;

    AddRef();
    return S_OK;
}

ULONG STDMETHODCALLTYPE WebPreferences::AddRef(void)
{
    return ++m_refCount;
}

ULONG STDMETHODCALLTYPE WebPreferences::Release(void)
{
    ULONG newRef = --m_refCount;
    if (!newRef)
        delete(this);

    return newRef;
}

// IWebPreferences ------------------------------------------------------------

HRESULT STDMETHODCALLTYPE WebPreferences::standardPreferences( 
    /* [retval][out] */ IWebPreferences** standardPreferences)
{
    if (!standardPreferences)
        return E_POINTER;
    *standardPreferences = sharedStandardPreferences();
    (*standardPreferences)->AddRef();
    return S_OK;
}

HRESULT STDMETHODCALLTYPE WebPreferences::initWithIdentifier( 
        /* [in] */ BSTR anIdentifier,
        /* [retval][out] */ IWebPreferences** preferences)
{
    WebPreferences *instance = getInstanceForIdentifier(anIdentifier);
    if (instance) {
        *preferences = instance;
        instance->AddRef();
        return S_OK;
    }

    load();

    *preferences = this;
    AddRef();

    if (anIdentifier) {
        m_identifier = anIdentifier;
        setInstance(this, m_identifier);
    }

    this->postPreferencesChangesNotification();

    return S_OK;
}

HRESULT STDMETHODCALLTYPE WebPreferences::identifier( 
    /* [retval][out] */ BSTR* ident)
{
    if (!ident)
        return E_POINTER;
    *ident = m_identifier ? SysAllocString(m_identifier) : 0;
    return S_OK;
}

HRESULT STDMETHODCALLTYPE WebPreferences::standardFontFamily( 
    /* [retval][out] */ BSTR* family)
{
    *family = stringValueForKey(CFSTR(WebKitStandardFontPreferenceKey));
    return (*family) ? S_OK : E_FAIL;
}

HRESULT STDMETHODCALLTYPE WebPreferences::setStandardFontFamily( 
    /* [in] */ BSTR family)
{
    setStringValue(CFSTR(WebKitStandardFontPreferenceKey), family);
    return S_OK;
}

HRESULT STDMETHODCALLTYPE WebPreferences::fixedFontFamily( 
    /* [retval][out] */ BSTR* family)
{
    *family = stringValueForKey(CFSTR(WebKitFixedFontPreferenceKey));
    return (*family) ? S_OK : E_FAIL;
}

HRESULT STDMETHODCALLTYPE WebPreferences::setFixedFontFamily( 
    /* [in] */ BSTR family)
{
    setStringValue(CFSTR(WebKitFixedFontPreferenceKey), family);
    return S_OK;
}

HRESULT STDMETHODCALLTYPE WebPreferences::serifFontFamily( 
    /* [retval][out] */ BSTR* fontFamily)
{
    *fontFamily = stringValueForKey(CFSTR(WebKitSerifFontPreferenceKey));
    return (*fontFamily) ? S_OK : E_FAIL;
}

HRESULT STDMETHODCALLTYPE WebPreferences::setSerifFontFamily( 
    /* [in] */ BSTR family)
{
    setStringValue(CFSTR(WebKitSerifFontPreferenceKey), family);
    return S_OK;
}

HRESULT STDMETHODCALLTYPE WebPreferences::sansSerifFontFamily( 
    /* [retval][out] */ BSTR* family)
{
    *family = stringValueForKey(CFSTR(WebKitSansSerifFontPreferenceKey));
    return (*family) ? S_OK : E_FAIL;
}

HRESULT STDMETHODCALLTYPE WebPreferences::setSansSerifFontFamily( 
    /* [in] */ BSTR family)
{
    setStringValue(CFSTR(WebKitSansSerifFontPreferenceKey), family);
    return S_OK;
}

HRESULT STDMETHODCALLTYPE WebPreferences::cursiveFontFamily( 
    /* [retval][out] */ BSTR* family)
{
    *family = stringValueForKey(CFSTR(WebKitCursiveFontPreferenceKey));
    return (*family) ? S_OK : E_FAIL;
}

HRESULT STDMETHODCALLTYPE WebPreferences::setCursiveFontFamily( 
    /* [in] */ BSTR family)
{
    setStringValue(CFSTR(WebKitCursiveFontPreferenceKey), family);
    return S_OK;
}

HRESULT STDMETHODCALLTYPE WebPreferences::fantasyFontFamily( 
    /* [retval][out] */ BSTR* family)
{
    *family = stringValueForKey(CFSTR(WebKitFantasyFontPreferenceKey));
    return (*family) ? S_OK : E_FAIL;
}

HRESULT STDMETHODCALLTYPE WebPreferences::setFantasyFontFamily( 
    /* [in] */ BSTR family)
{
    setStringValue(CFSTR(WebKitFantasyFontPreferenceKey), family);
    return S_OK;
}

HRESULT STDMETHODCALLTYPE WebPreferences::defaultFontSize( 
    /* [retval][out] */ int* fontSize)
{
    *fontSize = integerValueForKey(CFSTR(WebKitDefaultFontSizePreferenceKey));
    return S_OK;
}

HRESULT STDMETHODCALLTYPE WebPreferences::setDefaultFontSize( 
    /* [in] */ int fontSize)
{
    setIntegerValue(CFSTR(WebKitDefaultFontSizePreferenceKey), fontSize);
    return S_OK;
}

HRESULT STDMETHODCALLTYPE WebPreferences::defaultFixedFontSize( 
    /* [retval][out] */ int* fontSize)
{
    *fontSize = integerValueForKey(CFSTR(WebKitDefaultFixedFontSizePreferenceKey));
    return S_OK;
}

HRESULT STDMETHODCALLTYPE WebPreferences::setDefaultFixedFontSize( 
    /* [in] */ int fontSize)
{
    setIntegerValue(CFSTR(WebKitDefaultFixedFontSizePreferenceKey), fontSize);
    return S_OK;
}

HRESULT STDMETHODCALLTYPE WebPreferences::minimumFontSize( 
    /* [retval][out] */ int* fontSize)
{
    *fontSize = integerValueForKey(CFSTR(WebKitMinimumFontSizePreferenceKey));
    return S_OK;
}

HRESULT STDMETHODCALLTYPE WebPreferences::setMinimumFontSize( 
    /* [in] */ int fontSize)
{
    setIntegerValue(CFSTR(WebKitMinimumFontSizePreferenceKey), fontSize);
    return S_OK;
}

HRESULT STDMETHODCALLTYPE WebPreferences::minimumLogicalFontSize( 
    /* [retval][out] */ int* fontSize)
{
    *fontSize = integerValueForKey(CFSTR(WebKitMinimumLogicalFontSizePreferenceKey));
    return S_OK;
}

HRESULT STDMETHODCALLTYPE WebPreferences::setMinimumLogicalFontSize( 
    /* [in] */ int fontSize)
{
    setIntegerValue(CFSTR(WebKitMinimumLogicalFontSizePreferenceKey), fontSize);
    return S_OK;
}

HRESULT STDMETHODCALLTYPE WebPreferences::defaultTextEncodingName( 
    /* [retval][out] */ BSTR* name)
{
    *name = stringValueForKey(CFSTR(WebKitDefaultTextEncodingNamePreferenceKey));
    return (*name) ? S_OK : E_FAIL;
}

HRESULT STDMETHODCALLTYPE WebPreferences::setDefaultTextEncodingName( 
    /* [in] */ BSTR name)
{
    setStringValue(CFSTR(WebKitDefaultTextEncodingNamePreferenceKey), name);
    return S_OK;
}

HRESULT STDMETHODCALLTYPE WebPreferences::userStyleSheetEnabled( 
    /* [retval][out] */ BOOL* enabled)
{
    *enabled = boolValueForKey(CFSTR(WebKitUserStyleSheetEnabledPreferenceKey));
    return S_OK;
}

HRESULT STDMETHODCALLTYPE WebPreferences::setUserStyleSheetEnabled( 
    /* [in] */ BOOL enabled)
{
    setBoolValue(CFSTR(WebKitUserStyleSheetEnabledPreferenceKey), enabled);
    return S_OK;
}

HRESULT STDMETHODCALLTYPE WebPreferences::userStyleSheetLocation( 
    /* [retval][out] */ BSTR* location)
{
    *location = stringValueForKey(CFSTR(WebKitUserStyleSheetLocationPreferenceKey));
    return (*location) ? S_OK : E_FAIL;
}

HRESULT STDMETHODCALLTYPE WebPreferences::setUserStyleSheetLocation( 
    /* [in] */ BSTR location)
{
    setStringValue(CFSTR(WebKitUserStyleSheetLocationPreferenceKey), location);
    return S_OK;
}

HRESULT STDMETHODCALLTYPE WebPreferences::isJavaEnabled( 
    /* [retval][out] */ BOOL* enabled)
{
    *enabled = boolValueForKey(CFSTR(WebKitJavaEnabledPreferenceKey));
    return S_OK;
}

HRESULT STDMETHODCALLTYPE WebPreferences::setJavaEnabled( 
    /* [in] */ BOOL enabled)
{
    setBoolValue(CFSTR(WebKitJavaEnabledPreferenceKey), enabled);
    return S_OK;
}

HRESULT STDMETHODCALLTYPE WebPreferences::isJavaScriptEnabled( 
    /* [retval][out] */ BOOL* enabled)
{
    *enabled = boolValueForKey(CFSTR(WebKitJavaScriptEnabledPreferenceKey));
    return S_OK;
}

HRESULT STDMETHODCALLTYPE WebPreferences::setJavaScriptEnabled( 
    /* [in] */ BOOL enabled)
{
    setBoolValue(CFSTR(WebKitJavaScriptEnabledPreferenceKey), enabled);
    return S_OK;
}

HRESULT STDMETHODCALLTYPE WebPreferences::javaScriptCanOpenWindowsAutomatically( 
    /* [retval][out] */ BOOL* enabled)
{
    *enabled = boolValueForKey(CFSTR(WebKitJavaScriptCanOpenWindowsAutomaticallyPreferenceKey));
    return S_OK;
}

HRESULT STDMETHODCALLTYPE WebPreferences::setJavaScriptCanOpenWindowsAutomatically( 
    /* [in] */ BOOL enabled)
{
    setBoolValue(CFSTR(WebKitJavaScriptCanOpenWindowsAutomaticallyPreferenceKey), enabled);
    return S_OK;
}

HRESULT STDMETHODCALLTYPE WebPreferences::arePlugInsEnabled( 
    /* [retval][out] */ BOOL* enabled)
{
    *enabled = boolValueForKey(CFSTR(WebKitPluginsEnabledPreferenceKey));
    return S_OK;
}

HRESULT STDMETHODCALLTYPE WebPreferences::setPlugInsEnabled( 
    /* [in] */ BOOL enabled)
{
    setBoolValue(CFSTR(WebKitPluginsEnabledPreferenceKey), enabled);
    return S_OK;
}

HRESULT STDMETHODCALLTYPE WebPreferences::allowsAnimatedImages( 
    /* [retval][out] */ BOOL* enabled)
{
    *enabled = boolValueForKey(CFSTR(WebKitAllowAnimatedImagesPreferenceKey));
    return S_OK;
}

HRESULT STDMETHODCALLTYPE WebPreferences::setAllowsAnimatedImages( 
    /* [in] */ BOOL enabled)
{
    setBoolValue(CFSTR(WebKitAllowAnimatedImagesPreferenceKey), enabled);
    return S_OK;
}

HRESULT STDMETHODCALLTYPE WebPreferences::allowAnimatedImageLooping( 
    /* [retval][out] */ BOOL* enabled)
{
    *enabled = boolValueForKey(CFSTR(WebKitAllowAnimatedImageLoopingPreferenceKey));
    return S_OK;
}

HRESULT STDMETHODCALLTYPE WebPreferences::setAllowAnimatedImageLooping( 
    /* [in] */ BOOL enabled)
{
    setBoolValue(CFSTR(WebKitAllowAnimatedImageLoopingPreferenceKey), enabled);
    return S_OK;
}

HRESULT STDMETHODCALLTYPE WebPreferences::setLoadsImagesAutomatically( 
    /* [in] */ BOOL enabled)
{
    setBoolValue(CFSTR(WebKitDisplayImagesKey), enabled);
    return S_OK;
}

HRESULT STDMETHODCALLTYPE WebPreferences::loadsImagesAutomatically( 
    /* [retval][out] */ BOOL* enabled)
{
    *enabled = boolValueForKey(CFSTR(WebKitDisplayImagesKey));
    return S_OK;
}

HRESULT STDMETHODCALLTYPE WebPreferences::setAutosaves( 
    /* [in] */ BOOL enabled)
{
    m_autoSaves = !!enabled;
    return S_OK;
}

HRESULT STDMETHODCALLTYPE WebPreferences::autosaves( 
    /* [retval][out] */ BOOL* enabled)
{
    *enabled = m_autoSaves ? TRUE : FALSE;
    return S_OK;
}

HRESULT STDMETHODCALLTYPE WebPreferences::setShouldPrintBackgrounds( 
    /* [in] */ BOOL enabled)
{
    setBoolValue(CFSTR(WebKitShouldPrintBackgroundsPreferenceKey), enabled);
    return S_OK;
}

HRESULT STDMETHODCALLTYPE WebPreferences::shouldPrintBackgrounds( 
    /* [retval][out] */ BOOL* enabled)
{
    *enabled = boolValueForKey(CFSTR(WebKitShouldPrintBackgroundsPreferenceKey));
    return S_OK;
}

HRESULT STDMETHODCALLTYPE WebPreferences::setPrivateBrowsingEnabled( 
    /* [in] */ BOOL enabled)
{
    setBoolValue(CFSTR(WebKitPrivateBrowsingEnabledPreferenceKey), enabled);
    return S_OK;
}

HRESULT STDMETHODCALLTYPE WebPreferences::privateBrowsingEnabled( 
    /* [retval][out] */ BOOL* enabled)
{
    *enabled = boolValueForKey(CFSTR(WebKitPrivateBrowsingEnabledPreferenceKey));
    return S_OK;
}

HRESULT STDMETHODCALLTYPE WebPreferences::setTabsToLinks( 
    /* [in] */ BOOL enabled)
{
    setBoolValue(CFSTR(WebKitTabToLinksPreferenceKey), enabled);
    return S_OK;
}

HRESULT STDMETHODCALLTYPE WebPreferences::tabsToLinks( 
    /* [retval][out] */ BOOL* enabled)
{
    *enabled = boolValueForKey(CFSTR(WebKitTabToLinksPreferenceKey));
    return S_OK;
}

HRESULT STDMETHODCALLTYPE WebPreferences::setUsesPageCache( 
        /* [in] */ BOOL usesPageCache)
{
    setBoolValue(CFSTR(WebKitUsesPageCachePreferenceKey), usesPageCache);
    return S_OK;
}

HRESULT STDMETHODCALLTYPE WebPreferences::usesPageCache( 
    /* [retval][out] */ BOOL* usesPageCache)
{
    *usesPageCache = boolValueForKey(CFSTR(WebKitUsesPageCachePreferenceKey));
    return S_OK;
}

HRESULT STDMETHODCALLTYPE WebPreferences::textAreasAreResizable( 
    /* [retval][out] */ BOOL* enabled)
{
    *enabled = boolValueForKey(CFSTR(WebKitTextAreasAreResizablePreferenceKey));
    return S_OK;
}

HRESULT STDMETHODCALLTYPE WebPreferences::setTextAreasAreResizable( 
    /* [in] */ BOOL enabled)
{
    setBoolValue(CFSTR(WebKitTextAreasAreResizablePreferenceKey), enabled);
    return S_OK;
}

HRESULT WebPreferences::historyItemLimit(int* limit)
{
    *limit = integerValueForKey(CFSTR(WebKitHistoryItemLimitKey));
    return S_OK;
}

HRESULT WebPreferences::setHistoryItemLimit(int limit)
{
    setIntegerValue(CFSTR(WebKitHistoryItemLimitKey), limit);
    return S_OK;
}

HRESULT WebPreferences::historyAgeInDaysLimit(int* limit)
{
    *limit = integerValueForKey(CFSTR(WebKitHistoryAgeInDaysLimitKey));
    return S_OK;
}

HRESULT WebPreferences::setHistoryAgeInDaysLimit(int limit)
{
    setIntegerValue(CFSTR(WebKitHistoryAgeInDaysLimitKey), limit);
    return S_OK;
}

HRESULT WebPreferences::unused1()
{
    ASSERT_NOT_REACHED();
    return E_FAIL;
}

HRESULT WebPreferences::unused2()
{
    ASSERT_NOT_REACHED();
    return E_FAIL;
}

HRESULT WebPreferences::iconDatabaseLocation(
    /* [out] */ BSTR* location)
{
    *location = stringValueForKey(CFSTR(WebKitIconDatabaseLocationKey));
    return (*location) ? S_OK : E_FAIL;
}

HRESULT WebPreferences::setIconDatabaseLocation(
    /* [in] */ BSTR location)
{
    setStringValue(CFSTR(WebKitIconDatabaseLocationKey), location);
    return S_OK;
}

HRESULT WebPreferences::iconDatabaseEnabled(BOOL* enabled)//location)
{
    *enabled = boolValueForKey(CFSTR(WebKitIconDatabaseEnabledPreferenceKey));
    return S_OK;
}

HRESULT WebPreferences::setIconDatabaseEnabled(BOOL enabled )//location)
{
    setBoolValue(CFSTR(WebKitIconDatabaseEnabledPreferenceKey), enabled);
    return S_OK;
}

HRESULT STDMETHODCALLTYPE WebPreferences::fontSmoothing( 
    /* [retval][out] */ FontSmoothingType* smoothingType)
{
    *smoothingType = (FontSmoothingType) integerValueForKey(CFSTR(WebKitFontSmoothingTypePreferenceKey));
    return S_OK;
}

HRESULT STDMETHODCALLTYPE WebPreferences::setFontSmoothing( 
    /* [in] */ FontSmoothingType smoothingType)
{
    setIntegerValue(CFSTR(WebKitFontSmoothingTypePreferenceKey), smoothingType);
    if (smoothingType == FontSmoothingTypeWindows)
        smoothingType = FontSmoothingTypeStandard;
    wkSetFontSmoothingLevel((int)smoothingType);
    return S_OK;
}

HRESULT STDMETHODCALLTYPE WebPreferences::editableLinkBehavior(
    /* [out, retval] */ WebKitEditableLinkBehavior* editableLinkBehavior)
{
    WebKitEditableLinkBehavior value = (WebKitEditableLinkBehavior) integerValueForKey(CFSTR(WebKitEditableLinkBehaviorPreferenceKey));
    switch (value) {
        case WebKitEditableLinkDefaultBehavior:
        case WebKitEditableLinkAlwaysLive:
        case WebKitEditableLinkOnlyLiveWithShiftKey:
        case WebKitEditableLinkLiveWhenNotFocused:
        case WebKitEditableLinkNeverLive:
            *editableLinkBehavior = value;
            break;
        default: // ensure that a valid result is returned
            *editableLinkBehavior = WebKitEditableLinkDefaultBehavior;
            break;
    }
    return S_OK;
}

HRESULT STDMETHODCALLTYPE WebPreferences::setEditableLinkBehavior(
    /* [in] */ WebKitEditableLinkBehavior behavior)
{
    setIntegerValue(CFSTR(WebKitEditableLinkBehaviorPreferenceKey), behavior);
    return S_OK;
}

HRESULT STDMETHODCALLTYPE WebPreferences::cookieStorageAcceptPolicy( 
        /* [retval][out] */ WebKitCookieStorageAcceptPolicy *acceptPolicy )
{
    if (!acceptPolicy)
        return E_POINTER;

    *acceptPolicy = (WebKitCookieStorageAcceptPolicy)integerValueForKey(CFSTR(WebKitCookieStorageAcceptPolicyPreferenceKey));
    return S_OK;
}

HRESULT STDMETHODCALLTYPE WebPreferences::setCookieStorageAcceptPolicy( 
        /* [in] */ WebKitCookieStorageAcceptPolicy acceptPolicy)
{
    setIntegerValue(CFSTR(WebKitCookieStorageAcceptPolicyPreferenceKey), acceptPolicy);
    return S_OK;
}


HRESULT WebPreferences::continuousSpellCheckingEnabled(BOOL* enabled)
{
    *enabled = boolValueForKey(CFSTR(WebContinuousSpellCheckingEnabledPreferenceKey));
    return S_OK;
}

HRESULT WebPreferences::setContinuousSpellCheckingEnabled(BOOL enabled)
{
    setBoolValue(CFSTR(WebContinuousSpellCheckingEnabledPreferenceKey), enabled);
    return S_OK;
}

HRESULT WebPreferences::grammarCheckingEnabled(BOOL* enabled)
{
    *enabled = boolValueForKey(CFSTR(WebGrammarCheckingEnabledPreferenceKey));
    return S_OK;
}

HRESULT WebPreferences::setGrammarCheckingEnabled(BOOL enabled)
{
    setBoolValue(CFSTR(WebGrammarCheckingEnabledPreferenceKey), enabled);
    return S_OK;
}

HRESULT WebPreferences::allowContinuousSpellChecking(BOOL* enabled)
{
    *enabled = boolValueForKey(CFSTR(AllowContinuousSpellCheckingPreferenceKey));
    return S_OK;
}

HRESULT WebPreferences::setAllowContinuousSpellChecking(BOOL enabled)
{
    setBoolValue(CFSTR(AllowContinuousSpellCheckingPreferenceKey), enabled);
    return S_OK;
}

HRESULT WebPreferences::isDOMPasteAllowed(BOOL* enabled)
{
    *enabled = boolValueForKey(CFSTR(WebKitDOMPasteAllowedPreferenceKey));
    return S_OK;
}
    
HRESULT WebPreferences::setDOMPasteAllowed(BOOL enabled)
{
    setBoolValue(CFSTR(WebKitDOMPasteAllowedPreferenceKey), enabled);
    return S_OK;
}

HRESULT WebPreferences::cacheModel(WebCacheModel* cacheModel)
{
    if (!cacheModel)
        return E_POINTER;

    *cacheModel = (WebCacheModel)integerValueForKey(CFSTR(WebKitCacheModelPreferenceKey));
    return S_OK;
}

HRESULT WebPreferences::setCacheModel(WebCacheModel cacheModel)
{
    setIntegerValue(CFSTR(WebKitCacheModelPreferenceKey), cacheModel);
    return S_OK;
}

HRESULT WebPreferences::setDeveloperExtrasEnabled(BOOL enabled)
{
    setBoolValue(CFSTR(WebKitDeveloperExtrasEnabledPreferenceKey), enabled);
    return S_OK;
}

HRESULT WebPreferences::developerExtrasEnabled(BOOL* enabled)
{
    if (!enabled)
        return E_POINTER;

    *enabled = boolValueForKey(CFSTR(WebKitDeveloperExtrasEnabledPreferenceKey));
    return S_OK;
}

bool WebPreferences::developerExtrasDisabledByOverride()
{
    return !!boolValueForKey(CFSTR(DisableWebKitDeveloperExtrasPreferenceKey));
}

HRESULT WebPreferences::setAutomaticallyDetectsCacheModel(BOOL automaticallyDetectsCacheModel)
{
    m_automaticallyDetectsCacheModel = !!automaticallyDetectsCacheModel;
    return S_OK;
}

HRESULT WebPreferences::automaticallyDetectsCacheModel(BOOL* automaticallyDetectsCacheModel)
{
    if (!automaticallyDetectsCacheModel)
        return E_POINTER;

    *automaticallyDetectsCacheModel = m_automaticallyDetectsCacheModel;
    return S_OK;
}

HRESULT STDMETHODCALLTYPE WebPreferences::setAuthorAndUserStylesEnabled(BOOL enabled)
{
    setBoolValue(CFSTR(WebKitAuthorAndUserStylesEnabledPreferenceKey), enabled);
    return S_OK;
}

HRESULT STDMETHODCALLTYPE WebPreferences::authorAndUserStylesEnabled(BOOL* enabled)
{
    if (!enabled)
        return E_POINTER;

    *enabled = boolValueForKey(CFSTR(WebKitAuthorAndUserStylesEnabledPreferenceKey));
    return S_OK;
}

void WebPreferences::willAddToWebView()
{
    ++m_numWebViews;
}

void WebPreferences::didRemoveFromWebView()
{
    ASSERT(m_numWebViews);
    if (--m_numWebViews == 0) {
        IWebNotificationCenter* nc = WebNotificationCenter::defaultCenterInternal();
        nc->postNotificationName(webPreferencesRemovedNotification(), static_cast<IWebPreferences*>(this), 0);
    }
}
