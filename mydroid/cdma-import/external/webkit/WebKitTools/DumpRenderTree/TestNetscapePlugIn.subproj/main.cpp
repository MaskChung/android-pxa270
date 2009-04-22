/*
 IMPORTANT:  This Apple software is supplied to you by Apple Computer, Inc. ("Apple") in
 consideration of your agreement to the following terms, and your use, installation, 
 modification or redistribution of this Apple software constitutes acceptance of these 
 terms.  If you do not agree with these terms, please do not use, install, modify or 
 redistribute this Apple software.
 
 In consideration of your agreement to abide by the following terms, and subject to these 
 terms, Apple grants you a personal, non-exclusive license, under Apple’s copyrights in 
 this original Apple software (the "Apple Software"), to use, reproduce, modify and 
 redistribute the Apple Software, with or without modifications, in source and/or binary 
 forms; provided that if you redistribute the Apple Software in its entirety and without 
 modifications, you must retain this notice and the following text and disclaimers in all 
 such redistributions of the Apple Software.  Neither the name, trademarks, service marks 
 or logos of Apple Computer, Inc. may be used to endorse or promote products derived from 
 the Apple Software without specific prior written permission from Apple. Except as expressly
 stated in this notice, no other rights or licenses, express or implied, are granted by Apple
 herein, including but not limited to any patent rights that may be infringed by your 
 derivative works or by other works in which the Apple Software may be incorporated.
 
 The Apple Software is provided by Apple on an "AS IS" basis.  APPLE MAKES NO WARRANTIES, 
 EXPRESS OR IMPLIED, INCLUDING WITHOUT LIMITATION THE IMPLIED WARRANTIES OF NON-INFRINGEMENT, 
 MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE, REGARDING THE APPLE SOFTWARE OR ITS 
 USE AND OPERATION ALONE OR IN COMBINATION WITH YOUR PRODUCTS.
 
 IN NO EVENT SHALL APPLE BE LIABLE FOR ANY SPECIAL, INDIRECT, INCIDENTAL OR CONSEQUENTIAL 
 DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS 
          OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) ARISING IN ANY WAY OUT OF THE USE, 
 REPRODUCTION, MODIFICATION AND/OR DISTRIBUTION OF THE APPLE SOFTWARE, HOWEVER CAUSED AND 
 WHETHER UNDER THEORY OF CONTRACT, TORT (INCLUDING NEGLIGENCE), STRICT LIABILITY OR 
 OTHERWISE, EVEN IF APPLE HAS BEEN ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

#import "PluginObject.h"

// Mach-o entry points
extern "C" {
    NPError NP_Initialize(NPNetscapeFuncs *browserFuncs);
    NPError NP_GetEntryPoints(NPPluginFuncs *pluginFuncs);
    void NP_Shutdown(void);
}

// Mach-o entry points
NPError NP_Initialize(NPNetscapeFuncs *browserFuncs)
{
    browser = browserFuncs;
    return NPERR_NO_ERROR;
}

NPError NP_GetEntryPoints(NPPluginFuncs *pluginFuncs)
{
    pluginFuncs->version = 11;
    pluginFuncs->size = sizeof(pluginFuncs);
    pluginFuncs->newp = NPP_New;
    pluginFuncs->destroy = NPP_Destroy;
    pluginFuncs->setwindow = NPP_SetWindow;
    pluginFuncs->newstream = NPP_NewStream;
    pluginFuncs->destroystream = NPP_DestroyStream;
    pluginFuncs->asfile = NPP_StreamAsFile;
    pluginFuncs->writeready = NPP_WriteReady;
    pluginFuncs->write = (NPP_WriteProcPtr)NPP_Write;
    pluginFuncs->print = NPP_Print;
    pluginFuncs->event = NPP_HandleEvent;
    pluginFuncs->urlnotify = NPP_URLNotify;
    pluginFuncs->getvalue = NPP_GetValue;
    pluginFuncs->setvalue = NPP_SetValue;
    
    return NPERR_NO_ERROR;
}

void NP_Shutdown(void)
{
}

NPError NPP_New(NPMIMEType pluginType, NPP instance, uint16 mode, int16 argc, char *argn[], char *argv[], NPSavedData *saved)
{
    if (browser->version >= 14) {
        PluginObject* obj = (PluginObject*)browser->createobject(instance, getPluginClass());
    
        obj->onStreamLoad = NULL;
        
        for (int i = 0; i < argc; i++) {
            if (strcasecmp(argn[i], "onstreamload") == 0 && !obj->onStreamLoad)
                obj->onStreamLoad = strdup(argv[i]);
            else if (strcasecmp(argn[i], "src") == 0 &&
                     strcasecmp(argv[i], "data:application/x-webkit-test-netscape,returnerrorfromnewstream") == 0)
                obj->returnErrorFromNewStream = TRUE;
            else if (strcasecmp(argn[i], "logfirstsetwindow") == 0)
                obj->logSetWindow = TRUE;
        }
        
        instance->pdata = obj;
    }
    
    return NPERR_NO_ERROR;
}

NPError NPP_Destroy(NPP instance, NPSavedData **save)
{
    PluginObject* obj = static_cast<PluginObject*>(instance->pdata);
    if (obj) {
        if (obj->onStreamLoad)
            free(obj->onStreamLoad);
        
        if (obj->logDestroy)
            printf("PLUGIN: NPP_Destroy\n");

        browser->releaseobject(&obj->header);
    }
    return NPERR_NO_ERROR;
}

NPError NPP_SetWindow(NPP instance, NPWindow *window)
{
    PluginObject* obj = static_cast<PluginObject*>(instance->pdata);

    if (obj) {
        if (obj->logSetWindow) {
            printf("PLUGIN: NPP_SetWindow: %d %d\n", (int)window->width, (int)window->height);
            obj->logSetWindow = false;
        }
    }
    
    return NPERR_NO_ERROR;
}

NPError NPP_NewStream(NPP instance, NPMIMEType type, NPStream *stream, NPBool seekable, uint16 *stype)
{
    PluginObject* obj = static_cast<PluginObject*>(instance->pdata);
    obj->stream = stream;
    *stype = NP_ASFILEONLY;

    if (obj->returnErrorFromNewStream)
        return NPERR_GENERIC_ERROR;
    
    if (browser->version >= NPVERS_HAS_RESPONSE_HEADERS)
        notifyStream(obj, stream->url, stream->headers);

    if (obj->onStreamLoad) {
        NPObject *windowScriptObject;
        browser->getvalue(obj->npp, NPNVWindowNPObject, &windowScriptObject);
                
        NPString script;
        script.UTF8Characters = obj->onStreamLoad;
        script.UTF8Length = strlen(obj->onStreamLoad);
        
        NPVariant browserResult;
        browser->evaluate(obj->npp, windowScriptObject, &script, &browserResult);
        browser->releasevariantvalue(&browserResult);
    }
    
    return NPERR_NO_ERROR;
}

NPError NPP_DestroyStream(NPP instance, NPStream *stream, NPReason reason)
{
    return NPERR_NO_ERROR;
}

int32 NPP_WriteReady(NPP instance, NPStream *stream)
{
    return 0;
}

int32 NPP_Write(NPP instance, NPStream *stream, int32 offset, int32 len, void *buffer)
{
    return 0;
}

void NPP_StreamAsFile(NPP instance, NPStream *stream, const char *fname)
{
}

void NPP_Print(NPP instance, NPPrint *platformPrint)
{
}

int16 NPP_HandleEvent(NPP instance, void *event)
{
    PluginObject* obj = static_cast<PluginObject*>(instance->pdata);
    if (!obj->eventLogging)
        return 0;
    
    EventRecord* evt = static_cast<EventRecord*>(event);
    Point pt = { evt->where.v, evt->where.h };
    switch (evt->what) {
        case nullEvent:
            // these are delivered non-deterministically, don't log.
            break;
        case mouseDown:
            GlobalToLocal(&pt);
            printf("PLUGIN: mouseDown at (%d, %d)\n", pt.h, pt.v);
            break;
        case mouseUp:
            GlobalToLocal(&pt);
            printf("PLUGIN: mouseUp at (%d, %d)\n", pt.h, pt.v);
            break;
        case keyDown:
            printf("PLUGIN: keyDown '%c'\n", (char)(evt->message & 0xFF));
            break;
        case keyUp:
            printf("PLUGIN: keyUp '%c'\n", (char)(evt->message & 0xFF));
            break;
        case autoKey:
            printf("PLUGIN: autoKey '%c'\n", (char)(evt->message & 0xFF));
            break;
        case updateEvt:
            printf("PLUGIN: updateEvt\n");
            break;
        case diskEvt:
            printf("PLUGIN: diskEvt\n");
            break;
        case activateEvt:
            printf("PLUGIN: activateEvt\n");
            break;
        case osEvt:
            printf("PLUGIN: osEvt - ");
            switch ((evt->message & 0xFF000000) >> 24) {
                case suspendResumeMessage:
                    printf("%s\n", (evt->message & 0x1) ? "resume" : "suspend");
                    break;
                case mouseMovedMessage:
                    printf("mouseMoved\n");
                    break;
                default:
                    printf("%08lX\n", evt->message);
            }
            break;
        case kHighLevelEvent:
            printf("PLUGIN: kHighLevelEvent\n");
            break;
        // NPAPI events
        case getFocusEvent:
            printf("PLUGIN: getFocusEvent\n");
            break;
        case loseFocusEvent:
            printf("PLUGIN: loseFocusEvent\n");
            break;
        case adjustCursorEvent:
            printf("PLUGIN: adjustCursorEvent\n");
            break;
        default:
            printf("PLUGIN: event %d\n", evt->what);
    }
    
    return 0;
}

void NPP_URLNotify(NPP instance, const char *url, NPReason reason, void *notifyData)
{
    PluginObject* obj = static_cast<PluginObject*>(instance->pdata);
        
    handleCallback(obj, url, reason, notifyData);
}

NPError NPP_GetValue(NPP instance, NPPVariable variable, void *value)
{
    if (variable == NPPVpluginScriptableNPObject) {
        void **v = (void **)value;
        PluginObject* obj = static_cast<PluginObject*>(instance->pdata);
        // Return value is expected to be retained
        browser->retainobject((NPObject *)obj);
        *v = obj;
        return NPERR_NO_ERROR;
    }
    return NPERR_GENERIC_ERROR;
}

NPError NPP_SetValue(NPP instance, NPNVariable variable, void *value)
{
    return NPERR_GENERIC_ERROR;
}
