#if defined(SK_BUILD_FOR_MAC) && !defined(SK_USE_WXWIDGETS)

#include "SkWindow.h"
#include "SkCanvas.h"
#include "SkOSMenu.h"
#include "SkTime.h"

#include "SkGraphics.h"
#include <new.h>

static void (*gPrevNewHandler)();

extern "C" {
	static void sk_new_handler()
	{
		if (SkGraphics::SetFontCacheUsed(0))
			return;
		if (gPrevNewHandler)
			gPrevNewHandler();
		else
			sk_throw();
	}
}

static SkOSWindow* gCurrOSWin;
static EventTargetRef gEventTarget;
static EventQueueRef gCurrEventQ;

#define SK_MacEventClass			FOUR_CHAR_CODE('SKec')
#define SK_MacEventKind				FOUR_CHAR_CODE('SKek')
#define SK_MacEventParamName		FOUR_CHAR_CODE('SKev')
#define SK_MacEventSinkIDParamName	FOUR_CHAR_CODE('SKes')

SkOSWindow::SkOSWindow(void* hWnd) : fHWND(hWnd)
{	
	static const EventTypeSpec  gTypes[] = {
		{ kEventClassKeyboard,  kEventRawKeyDown			},
        { kEventClassKeyboard,  kEventRawKeyUp              },
		{ kEventClassMouse,		kEventMouseDown				},
		{ kEventClassMouse,		kEventMouseDragged			},
		{ kEventClassMouse,		kEventMouseUp				},
		{ kEventClassTextInput, kEventTextInputUnicodeForKeyEvent   },
		{ kEventClassWindow,	kEventWindowBoundsChanged	},
		{ kEventClassWindow,	kEventWindowDrawContent		},
		{ SK_MacEventClass,		SK_MacEventKind				}
	};

	EventHandlerUPP handlerUPP = NewEventHandlerUPP(SkOSWindow::EventHandler);
	int				count = SK_ARRAY_COUNT(gTypes);
	OSStatus		result;

	result = InstallEventHandler(GetWindowEventTarget((WindowRef)hWnd), handlerUPP,
						count, gTypes, this, nil);
	SkASSERT(result == noErr);

	gCurrOSWin = this;
	gCurrEventQ = GetCurrentEventQueue();
	gEventTarget = GetWindowEventTarget((WindowRef)hWnd);

	static bool gOnce = true;
	if (gOnce) {
		gOnce = false;
		gPrevNewHandler = set_new_handler(sk_new_handler);
	}
}

void SkOSWindow::doPaint(void* ctx)
{
	this->update(NULL);

	this->getBitmap().drawToPort((WindowRef)fHWND, (CGContextRef)ctx);
}

void SkOSWindow::updateSize()
{
	Rect	r;
	
	GetWindowBounds((WindowRef)fHWND, kWindowContentRgn, &r);
	this->resize(r.right - r.left, r.bottom - r.top);
}

void SkOSWindow::onHandleInval(const SkIRect& r)
{
	Rect	rect;
	
	rect.left   = r.fLeft;
	rect.top	= r.fTop;
	rect.right  = r.fRight;
	rect.bottom = r.fBottom;
	InvalWindowRect((WindowRef)fHWND, &rect);
}

void SkOSWindow::onSetTitle(const char title[])
{
    CFStringRef str = CFStringCreateWithCString(NULL, title, kCFStringEncodingUTF8);
    SetWindowTitleWithCFString((WindowRef)fHWND, str);
    CFRelease(str);
}

void SkOSWindow::onAddMenu(const SkOSMenu* sk_menu)
{
}

static void getparam(EventRef inEvent, OSType name, OSType type, UInt32 size, void* data)
{
	EventParamType  actualType;
	UInt32			actualSize;
	OSStatus		status;

	status = GetEventParameter(inEvent, name, type, &actualType, size, &actualSize, data);
	SkASSERT(status == noErr);
	SkASSERT(actualType == type);
	SkASSERT(actualSize == size);
}

enum {
	SK_MacReturnKey		= 36,
	SK_MacDeleteKey		= 51,
	SK_MacEndKey		= 119,
	SK_MacLeftKey		= 123,
	SK_MacRightKey		= 124,
	SK_MacDownKey		= 125,
	SK_MacUpKey			= 126,
    
    SK_Mac0Key          = 0x52,
    SK_Mac1Key          = 0x53,
    SK_Mac2Key          = 0x54,
    SK_Mac3Key          = 0x55,
    SK_Mac4Key          = 0x56,
    SK_Mac5Key          = 0x57,
    SK_Mac6Key          = 0x58,
    SK_Mac7Key          = 0x59,
    SK_Mac8Key          = 0x5b,
    SK_Mac9Key          = 0x5c
};
	
static SkKey raw2key(UInt32 raw)
{
	static const struct {
		UInt32  fRaw;
		SkKey   fKey;
	} gKeys[] = {
		{ SK_MacUpKey,		kUp_SkKey		},
		{ SK_MacDownKey,	kDown_SkKey		},
		{ SK_MacLeftKey,	kLeft_SkKey		},
		{ SK_MacRightKey,   kRight_SkKey	},
		{ SK_MacReturnKey,  kOK_SkKey		},
		{ SK_MacDeleteKey,  kBack_SkKey		},
		{ SK_MacEndKey,		kEnd_SkKey		},
        { SK_Mac0Key,       k0_SkKey        },
        { SK_Mac1Key,       k1_SkKey        },
        { SK_Mac2Key,       k2_SkKey        },
        { SK_Mac3Key,       k3_SkKey        },
        { SK_Mac4Key,       k4_SkKey        },
        { SK_Mac5Key,       k5_SkKey        },
        { SK_Mac6Key,       k6_SkKey        },
        { SK_Mac7Key,       k7_SkKey        },
        { SK_Mac8Key,       k8_SkKey        },
        { SK_Mac9Key,       k9_SkKey        }
	};
	
	for (unsigned i = 0; i < SK_ARRAY_COUNT(gKeys); i++)
		if (gKeys[i].fRaw == raw)
			return gKeys[i].fKey;
	return kNONE_SkKey;
}

static void post_skmacevent()
{
	EventRef	ref;
	OSStatus	status = CreateEvent(nil, SK_MacEventClass, SK_MacEventKind, 0, 0, &ref);
	SkASSERT(status == noErr);
	
#if 0
	status = SetEventParameter(ref, SK_MacEventParamName, SK_MacEventParamName, sizeof(evt), &evt);
	SkASSERT(status == noErr);
	status = SetEventParameter(ref, SK_MacEventSinkIDParamName, SK_MacEventSinkIDParamName, sizeof(sinkID), &sinkID);
	SkASSERT(status == noErr);
#endif
	
	EventTargetRef target = gEventTarget;
	SetEventParameter(ref, kEventParamPostTarget, typeEventTargetRef, sizeof(target), &target);
	SkASSERT(status == noErr);
	
	status = PostEventToQueue(gCurrEventQ, ref, kEventPriorityStandard);
	SkASSERT(status == noErr);

	ReleaseEvent(ref);
}

pascal OSStatus SkOSWindow::EventHandler( EventHandlerCallRef inHandler, EventRef inEvent, void* userData )
{
	SkOSWindow* win = (SkOSWindow*)userData;
	OSStatus	result = eventNotHandledErr;
	UInt32		wClass = GetEventClass(inEvent);
	UInt32		wKind = GetEventKind(inEvent);

	gCurrOSWin = win;	// will need to be in TLS. Set this so PostEvent will work

	switch (wClass) {
        case kEventClassMouse: {
			Point   pt;
			getparam(inEvent, kEventParamMouseLocation, typeQDPoint, sizeof(pt), &pt);
			SetPortWindowPort((WindowRef)win->getHWND());
			GlobalToLocal(&pt);

			switch (wKind) {
			case kEventMouseDown:
				(void)win->handleClick(pt.h, pt.v, Click::kDown_State);
				break;
			case kEventMouseDragged:
				(void)win->handleClick(pt.h, pt.v, Click::kMoved_State);
				break;
			case kEventMouseUp:
				(void)win->handleClick(pt.h, pt.v, Click::kUp_State);
				break;
			default:
				break;
			}
            break;
		}
        case kEventClassKeyboard:
            if (wKind == kEventRawKeyDown) {
                UInt32  raw;
                getparam(inEvent, kEventParamKeyCode, typeUInt32, sizeof(raw), &raw);
                SkKey key = raw2key(raw);
                if (key != kNONE_SkKey)
                    (void)win->handleKey(key);
            } else if (wKind == kEventRawKeyUp) {
                UInt32 raw;
                getparam(inEvent, kEventParamKeyCode, typeUInt32, sizeof(raw), &raw);
                SkKey key = raw2key(raw);
                if (key != kNONE_SkKey)
                    (void)win->handleKeyUp(key);
            }
            break;
        case kEventClassTextInput:
            if (wKind == kEventTextInputUnicodeForKeyEvent) {
                UInt16  uni;
                getparam(inEvent, kEventParamTextInputSendText, typeUnicodeText, sizeof(uni), &uni);
                win->handleChar(uni);
            }
            break;
        case kEventClassWindow:
            switch (wKind) {
                case kEventWindowBoundsChanged:
                    win->updateSize();
                    break;
                case kEventWindowDrawContent: {
                    CGContextRef cg;
                    result = GetEventParameter(inEvent,
                                               kEventParamCGContextRef,
                                               typeCGContextRef,
                                               NULL,
                                               sizeof (CGContextRef),
                                               NULL,
                                               &cg);
                    if (result != 0) {
                        cg = NULL;
                    }
                    win->doPaint(cg);
                    break;
                }
                default:
                    break;
            }
            break;
        case SK_MacEventClass: {
            SkASSERT(wKind == SK_MacEventKind);
            if (SkEvent::ProcessEvent()) {
                    post_skmacevent();
            }
    #if 0
            SkEvent*		evt;
            SkEventSinkID	sinkID;
            getparam(inEvent, SK_MacEventParamName, SK_MacEventParamName, sizeof(evt), &evt);
            getparam(inEvent, SK_MacEventSinkIDParamName, SK_MacEventSinkIDParamName, sizeof(sinkID), &sinkID);
    #endif
            result = noErr;
            break;
        }
        default:
            break;
	}
	if (result == eventNotHandledErr) {
		result = CallNextEventHandler(inHandler, inEvent);
    }
	return result;
}

///////////////////////////////////////////////////////////////////////////////////////

void SkEvent::SignalNonEmptyQueue()
{
	post_skmacevent();
//	SkDebugf("signal nonempty\n");
}

static TMTask	gTMTaskRec;
static TMTask*	gTMTaskPtr;

static void sk_timer_proc(TMTask* rec)
{
	SkEvent::ServiceQueueTimer();
//	SkDebugf("timer task fired\n");
}

void SkEvent::SignalQueueTimer(SkMSec delay)
{
	if (gTMTaskPtr)
	{
		RemoveTimeTask((QElem*)gTMTaskPtr);
		DisposeTimerUPP(gTMTaskPtr->tmAddr);
		gTMTaskPtr = nil;
	}
	if (delay)
	{
		gTMTaskPtr = &gTMTaskRec;
		memset(gTMTaskPtr, 0, sizeof(gTMTaskRec));
		gTMTaskPtr->tmAddr = NewTimerUPP(sk_timer_proc);
		OSErr err = InstallTimeTask((QElem*)gTMTaskPtr);
//		SkDebugf("installtimetask of %d returned %d\n", delay, err);
		PrimeTimeTask((QElem*)gTMTaskPtr, delay);
	}
}

#endif

