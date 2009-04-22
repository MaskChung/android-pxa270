/* ------------------------------------------------------------------
 * Copyright (C) 2008 PacketVideo
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * express or implied.
 * See the License for the specific language governing permissions
 * and limitations under the License.
 * -------------------------------------------------------------------
 */

//#define LOG_NDEBUG 0
#define LOG_TAG "VideoMIO"
#include <utils/Log.h>

#include "android_surface_output.h"
#include "pvlogger.h"
#include "pv_mime_string_utils.h"
#include "oscl_snprintf.h"

#include "oscl_dll.h"

#define PLATFORM_PRIVATE_PMEM 1

// Define entry point for this DLL
OSCL_DLL_ENTRY_POINT_DEFAULT()

//The factory functions.
#include "oscl_mem.h"

#include <cutils/properties.h>

#if HAVE_ANDROID_OS
#include <linux/android_pmem.h>
#endif

using namespace android;

static const char* pmem_adsp = "/dev/pmem_adsp";
static const char* pmem = "/dev/pmem";

// This class implements the reference media IO for file output
// This class constitutes the Media IO component

OSCL_EXPORT_REF AndroidSurfaceOutput::AndroidSurfaceOutput(const sp<ISurface>& surface)
    : OsclTimerObject(OsclActiveObject::EPriorityNominal, "androidsurfaceoutput"),
        mSurface(surface)
{
    LOGV("AndroidAudioSurfaceOutput surface=%p", surface.get());
    initData();

    iColorConverter = NULL;
    mInitialized = false;
    mEmulation = false;
    mHardwareCodec = false;

    // running in emulation?
    char value[PROPERTY_VALUE_MAX];
    if (property_get("ro.kernel.qemu", value, 0)) {
        LOGV("Running in emulation - fallback to software codecs");
        mEmulation = true;
    }
}

void AndroidSurfaceOutput::initData()
{
    iVideoFormat=PVMF_FORMAT_UNKNOWN;
    iVideoHeightValid=false;
    iVideoWidthValid=false;
    iVideoDisplayHeightValid=false;
    iVideoDisplayWidthValid=false;

    // hardware specific information
    iVideoSubFormat = PVMF_FORMAT_UNKNOWN;
    iVideoSubFormatValid = false;

    iCommandCounter=0;
    iLogger=NULL;
    iCommandResponseQueue.reserve(5);
    iWriteResponseQueue.reserve(5);
    iObserver=NULL;
    iLogger=NULL;
    iPeer=NULL;
    iState=STATE_IDLE;
}

void AndroidSurfaceOutput::ResetData()
    //reset all data from this session.
{
    Cleanup();

    //reset all the received media parameters.
    iVideoFormatString="";
    iVideoFormat=PVMF_FORMAT_UNKNOWN;
    iVideoHeightValid=false;
    iVideoWidthValid=false;
    iVideoDisplayHeightValid=false;
    iVideoDisplayWidthValid=false;
}

void AndroidSurfaceOutput::Cleanup()
//cleanup all allocated memory and release resources.
{
    while (!iCommandResponseQueue.empty())
    {
        if (iObserver)
            iObserver->RequestCompleted(PVMFCmdResp(iCommandResponseQueue[0].iCmdId, iCommandResponseQueue[0].iContext, iCommandResponseQueue[0].iStatus));
        iCommandResponseQueue.erase(&iCommandResponseQueue[0]);
    }
    while (!iWriteResponseQueue.empty())
    {
        if (iPeer)
            iPeer->writeComplete(iWriteResponseQueue[0].iStatus,iWriteResponseQueue[0].iCmdId,(OsclAny*)iWriteResponseQueue[0].iContext);
        iWriteResponseQueue.erase(&iWriteResponseQueue[0]);
    }

    // We'll close frame buf and delete here for now.
    CloseFrameBuf();
 }

OSCL_EXPORT_REF AndroidSurfaceOutput::~AndroidSurfaceOutput()
{
    Cleanup();
}


PVMFStatus AndroidSurfaceOutput::connect(PvmiMIOSession& aSession, PvmiMIOObserver* aObserver)
{
    PVLOGGER_LOGMSG(PVLOGMSG_INST_LLDBG, iLogger, PVLOGMSG_STACK_TRACE, (0,"AndroidSurfaceOutput::connect() called"));
    // Each Session could have its own set of Configuration parameters
    //in an array of structures and the session ID could be an index to that array.

    //currently supports only one session
    if (iObserver)
        return PVMFFailure;

    iObserver=aObserver;
    return PVMFSuccess;
}


PVMFStatus AndroidSurfaceOutput::disconnect(PvmiMIOSession aSession)
{

    PVLOGGER_LOGMSG(PVLOGMSG_INST_LLDBG, iLogger, PVLOGMSG_STACK_TRACE, (0,"AndroidSurfaceOutput::disconnect() called"));
    //currently supports only one session
    iObserver=NULL;
    return PVMFSuccess;
}


PvmiMediaTransfer* AndroidSurfaceOutput::createMediaTransfer(PvmiMIOSession& aSession, 
                                                        PvmiKvp* read_formats, int32 read_flags,
                                                        PvmiKvp* write_formats, int32 write_flags)
{
    PVLOGGER_LOGMSG(PVLOGMSG_INST_LLDBG, iLogger, PVLOGMSG_STACK_TRACE, (0,"AndroidSurfaceOutput::createMediaTransfer() called"));
    return (PvmiMediaTransfer*)this;
}

void AndroidSurfaceOutput::QueueCommandResponse(CommandResponse& aResp)
{
    //queue a command response and schedule processing.

    iCommandResponseQueue.push_back(aResp);

    //cancel any timer delay so the command response will happen ASAP.
    if (IsBusy())
        Cancel();

    RunIfNotReady();
}

PVMFCommandId AndroidSurfaceOutput::QueryUUID(const PvmfMimeString& aMimeType, 
                                        Oscl_Vector<PVUuid, OsclMemAllocator>& aUuids,
                                        bool aExactUuidsOnly, const OsclAny* aContext)
{
    PVLOGGER_LOGMSG(PVLOGMSG_INST_LLDBG, iLogger, PVLOGMSG_STACK_TRACE, (0,"AndroidSurfaceOutput::QueryUUID() called"));

    OSCL_UNUSED_ARG(aMimeType);
    OSCL_UNUSED_ARG(aExactUuidsOnly);

    PVMFCommandId cmdid=iCommandCounter++;

    PVMFStatus status=PVMFFailure;
    int32 err ;
    OSCL_TRY(err, aUuids.push_back(PVMI_CAPABILITY_AND_CONFIG_PVUUID););
    if (err==OsclErrNone)
        status= PVMFSuccess;

    CommandResponse resp(status,cmdid,aContext);
    QueueCommandResponse(resp);
    return cmdid;
}


PVMFCommandId AndroidSurfaceOutput::QueryInterface(const PVUuid& aUuid, PVInterface*& aInterfacePtr, const OsclAny* aContext)
{
    PVLOGGER_LOGMSG(PVLOGMSG_INST_LLDBG, iLogger, PVLOGMSG_STACK_TRACE, (0,"AndroidSurfaceOutput::QueryInterface() called"));

    PVMFCommandId cmdid=iCommandCounter++;

    PVMFStatus status=PVMFFailure;
    if(aUuid == PVMI_CAPABILITY_AND_CONFIG_PVUUID)
    {
        PvmiCapabilityAndConfig* myInterface = OSCL_STATIC_CAST(PvmiCapabilityAndConfig*,this);
        aInterfacePtr = OSCL_STATIC_CAST(PVInterface*, myInterface);
        status= PVMFSuccess;
    }
    else
    {
        status=PVMFFailure;
    }

    CommandResponse resp(status,cmdid,aContext);
    QueueCommandResponse(resp);
    return cmdid;
}


void AndroidSurfaceOutput::deleteMediaTransfer(PvmiMIOSession& aSession, PvmiMediaTransfer* media_transfer)
{
    PVLOGGER_LOGMSG(PVLOGMSG_INST_LLDBG, iLogger, PVLOGMSG_STACK_TRACE, (0,"AndroidSurfaceOutput::deleteMediaTransfer() called"));
    // This class is implementing the media transfer, so no cleanup is needed
}


PVMFCommandId AndroidSurfaceOutput:: Init(const OsclAny* aContext)
{
    PVLOGGER_LOGMSG(PVLOGMSG_INST_LLDBG, iLogger, PVLOGMSG_STACK_TRACE, (0,"AndroidSurfaceOutput::Init() called"));

    PVMFCommandId cmdid=iCommandCounter++;

    PVMFStatus status=PVMFFailure;

    switch(iState)
    {
    case STATE_LOGGED_ON:

        status=PVMFSuccess;

        if (status==PVMFSuccess)
            iState=STATE_INITIALIZED;

        break;

    default:
        PVLOGGER_LOGMSG(PVLOGMSG_INST_LLDBG, iLogger, PVLOGMSG_STACK_TRACE, (0,"AndroidSurfaceOutput::Invalid State"));
        status=PVMFErrInvalidState;
        break;
    }

    CommandResponse resp(status,cmdid,aContext);
    QueueCommandResponse(resp);
    return cmdid;
}

PVMFCommandId AndroidSurfaceOutput:: Reset(const OsclAny* aContext)
{
    // Do nothing for now.
    PVMFCommandId cmdid=iCommandCounter++;
    return cmdid;
}


PVMFCommandId AndroidSurfaceOutput::Start(const OsclAny* aContext)
{
    PVLOGGER_LOGMSG(PVLOGMSG_INST_LLDBG, iLogger, PVLOGMSG_STACK_TRACE, (0,"AndroidSurfaceOutput::Start() called"));

    PVMFCommandId cmdid=iCommandCounter++;

    PVMFStatus status=PVMFFailure;

    switch(iState)
    {
    case STATE_INITIALIZED:
    case STATE_PAUSED:

        iState=STATE_STARTED;
        status=PVMFSuccess;
        break;

    default:
        status=PVMFErrInvalidState;
        break;
    }

    CommandResponse resp(status,cmdid,aContext);
    QueueCommandResponse(resp);
    return cmdid;
}


PVMFCommandId AndroidSurfaceOutput::Pause(const OsclAny* aContext)
{
    PVLOGGER_LOGMSG(PVLOGMSG_INST_LLDBG, iLogger, PVLOGMSG_STACK_TRACE, (0,"AndroidSurfaceOutput::Pause() called"));

    PVMFCommandId cmdid=iCommandCounter++;

    PVMFStatus status=PVMFFailure;

    switch(iState)
    {
    case STATE_STARTED:

        iState=STATE_PAUSED;
        status=PVMFSuccess;

        // post last buffer to prevent stale data
        if (mHardwareCodec) {
            mSurface->postBuffer(mOffset);
        } else {
            mSurface->postBuffer(mFrameBuffers[mFrameBufferIndex]);
        }
        break;

    default:
        status=PVMFErrInvalidState;
        break;
    }

    CommandResponse resp(status,cmdid,aContext);
    QueueCommandResponse(resp);
    return cmdid;
}


PVMFCommandId AndroidSurfaceOutput::Flush(const OsclAny* aContext)
{
    PVLOGGER_LOGMSG(PVLOGMSG_INST_LLDBG, iLogger, PVLOGMSG_STACK_TRACE, (0,"AndroidSurfaceOutput::Flush() called"));

    PVMFCommandId cmdid=iCommandCounter++;

    PVMFStatus status=PVMFFailure;

    switch(iState)
    {
    case STATE_STARTED:

        iState=STATE_INITIALIZED;
        status=PVMFSuccess;
        break;

    default:
        status=PVMFErrInvalidState;
        break;
    }

    CommandResponse resp(status,cmdid,aContext);
    QueueCommandResponse(resp);
    return cmdid;
}

PVMFCommandId AndroidSurfaceOutput::DiscardData(const OsclAny* aContext)
{
    PVLOGGER_LOGMSG(PVLOGMSG_INST_LLDBG, iLogger, PVLOGMSG_STACK_TRACE, (0,"AndroidSurfaceOutput::DiscardData() called"));

    PVMFCommandId cmdid=iCommandCounter++;

    //this component doesn't buffer data, so there's nothing
    //needed here.

    PVMFStatus status=PVMFSuccess;

    CommandResponse resp(status,cmdid,aContext);
    QueueCommandResponse(resp);
    return cmdid;
}

PVMFCommandId AndroidSurfaceOutput::DiscardData(PVMFTimestamp aTimestamp, const OsclAny* aContext)
{
    PVLOGGER_LOGMSG(PVLOGMSG_INST_LLDBG, iLogger, PVLOGMSG_STACK_TRACE, (0,"AndroidSurfaceOutput::DiscardData() called"));

    PVMFCommandId cmdid=iCommandCounter++;

    aTimestamp = 0;

    //this component doesn't buffer data, so there's nothing
    //needed here.

    PVMFStatus status=PVMFSuccess;

    CommandResponse resp(status,cmdid,aContext);
    QueueCommandResponse(resp);
    return cmdid;
}

PVMFCommandId AndroidSurfaceOutput::Stop(const OsclAny* aContext)
{
    PVLOGGER_LOGMSG(PVLOGMSG_INST_LLDBG, iLogger, PVLOGMSG_STACK_TRACE, (0,"AndroidSurfaceOutput::Stop() called"));

            printf("AndroidSurfaceOutput::Stop Received %x\n", 0);

    PVMFCommandId cmdid=iCommandCounter++;

    PVMFStatus status=PVMFFailure;

    switch(iState)
    {
    case STATE_STARTED:
    case STATE_PAUSED:

#ifdef PERFORMANCE_MEASUREMENTS_ENABLED
    PVOmapVideoProfile.MarkEndTime();
    PVOmapVideoProfile.PrintStats();
    PVOmapVideoProfile.Reset();
#endif

        iState=STATE_INITIALIZED;
        status=PVMFSuccess;
        break;

    default:
        status=PVMFErrInvalidState;
        break;
    }

    CommandResponse resp(status,cmdid,aContext);
    QueueCommandResponse(resp);
    return cmdid;
}

PVMFCommandId AndroidSurfaceOutput::CancelAllCommands(const OsclAny* aContext)
{
    PVLOGGER_LOGMSG(PVLOGMSG_INST_LLDBG, iLogger, PVLOGMSG_STACK_TRACE, (0,"AndroidSurfaceOutput::CancelAllCommands() called"));

    PVMFCommandId cmdid=iCommandCounter++;

    //commands are executed immediately upon being received, so
    //it isn't really possible to cancel them.

    PVMFStatus status=PVMFSuccess;

    CommandResponse resp(status,cmdid,aContext);
    QueueCommandResponse(resp);
    return cmdid;
}

PVMFCommandId AndroidSurfaceOutput::CancelCommand(PVMFCommandId aCmdId, const OsclAny* aContext)
{
    PVLOGGER_LOGMSG(PVLOGMSG_INST_LLDBG, iLogger, PVLOGMSG_STACK_TRACE, (0,"AndroidSurfaceOutput::CancelCommand() called"));

    PVMFCommandId cmdid=iCommandCounter++;

    //commands are executed immediately upon being received, so
    //it isn't really possible to cancel them.

    //see if the response is still queued.
    PVMFStatus status=PVMFFailure;
    for (uint32 i=0;i<iCommandResponseQueue.size();i++)
    {
        if (iCommandResponseQueue[i].iCmdId==aCmdId)
        {
            status=PVMFSuccess;
            break;
        }
    }

    CommandResponse resp(status,cmdid,aContext);
    QueueCommandResponse(resp);
    return cmdid;
}

void AndroidSurfaceOutput::ThreadLogon()
{
    if(iState==STATE_IDLE)
    {
        iLogger = PVLogger::GetLoggerObject("PVOmapVideo");
        PVLOGGER_LOGMSG(PVLOGMSG_INST_LLDBG, iLogger, PVLOGMSG_STACK_TRACE, (0,"AndroidSurfaceOutput::ThreadLogon() called"));
        AddToScheduler();
        iState=STATE_LOGGED_ON;
    }
}


void AndroidSurfaceOutput::ThreadLogoff()
{   
    PVLOGGER_LOGMSG(PVLOGMSG_INST_LLDBG, iLogger, PVLOGMSG_STACK_TRACE, (0,"AndroidSurfaceOutput::ThreadLogoff() called"));
    
    if(iState!=STATE_IDLE)
    {
        RemoveFromScheduler();
        iLogger=NULL;
        iState=STATE_IDLE;
        //Reset all data from this session
        ResetData();
    }
}


void AndroidSurfaceOutput::setPeer(PvmiMediaTransfer* aPeer)
{
    PVLOGGER_LOGMSG(PVLOGMSG_INST_LLDBG, iLogger, PVLOGMSG_STACK_TRACE, (0,"AndroidSurfaceOutput::setPeer() called"));
    // Set the observer 
    iPeer = aPeer;
}


void AndroidSurfaceOutput::useMemoryAllocators(OsclMemAllocator* write_alloc)
{
    PVLOGGER_LOGMSG(PVLOGMSG_INST_LLDBG, iLogger, PVLOGMSG_STACK_TRACE, (0,"AndroidSurfaceOutput::useMemoryAllocators() called"));
    //not supported.
}

// This routine will determine whether data can be accepted in a writeAsync
// call and if not, will return true;
bool AndroidSurfaceOutput::CheckWriteBusy(uint32 aSeqNum)
{
    // for all other cases, accept data now.
    return false;
}

PVMFCommandId AndroidSurfaceOutput::writeAsync(uint8 aFormatType, int32 aFormatIndex, uint8* aData, uint32 aDataLen,
                                        const PvmiMediaXferHeader& data_header_info, OsclAny* aContext)
{
    uint32 aSeqNum=data_header_info.seq_num;
    PVMFTimestamp aTimestamp=data_header_info.timestamp;
    uint32 flags=data_header_info.flags;

    if (aSeqNum < 6)
    {
        PVLOGGER_LOGMSG(PVLOGMSG_INST_LLDBG, iLogger, PVLOGMSG_STACK_TRACE,
            (0,"AndroidSurfaceOutput::writeAsync() seqnum %d ts %d context %d",aSeqNum,aTimestamp,aContext));

        PVLOGGER_LOGMSG(PVLOGMSG_INST_LLDBG, iLogger, PVLOGMSG_STACK_TRACE,
            (0,"AndroidSurfaceOutput::writeAsync() Format Type %d Format Index %d length %d",aFormatType,aFormatIndex,aDataLen));
    }

    PVMFStatus status=PVMFFailure;

    switch(aFormatType)
    {
    case PVMI_MEDIAXFER_FMT_TYPE_COMMAND :
        PVLOGGER_LOGMSG(PVLOGMSG_INST_LLDBG, iLogger, PVLOGMSG_STACK_TRACE,
            (0,"AndroidSurfaceOutput::writeAsync() called with Command info."));
        //ignore
        status= PVMFSuccess;
        break;

    case PVMI_MEDIAXFER_FMT_TYPE_NOTIFICATION :
        PVLOGGER_LOGMSG(PVLOGMSG_INST_LLDBG, iLogger, PVLOGMSG_STACK_TRACE,
            (0,"AndroidSurfaceOutput::writeAsync() called with Notification info."));
        switch(aFormatIndex)
        {
        case PVMI_MEDIAXFER_FMT_INDEX_END_OF_STREAM:
            break;
        default:
            break;
        }
        //ignore
        status= PVMFSuccess;
        break;

    case PVMI_MEDIAXFER_FMT_TYPE_DATA :
        switch(aFormatIndex)
        {
        case PVMI_MEDIAXFER_FMT_INDEX_FMT_SPECIFIC_INFO:
            //format-specific info contains codec headers.
            PVLOGGER_LOGMSG(PVLOGMSG_INST_LLDBG, iLogger, PVLOGMSG_STACK_TRACE,
                (0,"AndroidSurfaceOutput::writeAsync() called with format-specific info."));

            if (iState<STATE_INITIALIZED)
            {
                PVLOGGER_LOGMSG(PVLOGMSG_INST_REL, iLogger, PVLOGMSG_ERR,
                    (0,"AndroidSurfaceOutput::writeAsync: Error - Invalid state"));
                status=PVMFErrInvalidState;
            }
            else
            {
                status= PVMFSuccess;
            }

            break;

        case PVMI_MEDIAXFER_FMT_INDEX_DATA:
            //data contains the media bitstream.

            //Verify the state
            if (iState!=STATE_STARTED)
            {
                PVLOGGER_LOGMSG(PVLOGMSG_INST_REL, iLogger, PVLOGMSG_ERR,
                    (0,"AndroidSurfaceOutput::writeAsync: Error - Invalid state"));
                status=PVMFErrInvalidState;
            }
            else
            {

                //printf("V WriteAsync { seq=%d, ts=%d }\n", data_header_info.seq_num, data_header_info.timestamp);

                // Call playback to send data to IVA for Color Convert
                status = WriteFrameBuf(aData, aDataLen, data_header_info);

                PVLOGGER_LOGMSG(PVLOGMSG_INST_REL, iLogger, PVLOGMSG_ERR,
                   (0,"AndroidSurfaceOutput::writeAsync: Playback Progress - frame %d",iFrameNumber++));
            }
            break;

        default:
            PVLOGGER_LOGMSG(PVLOGMSG_INST_REL, iLogger, PVLOGMSG_ERR,
                (0,"AndroidSurfaceOutput::writeAsync: Error - unrecognized format index"));
            status= PVMFFailure;
            break;
        }
        break;

    default:
        PVLOGGER_LOGMSG(PVLOGMSG_INST_REL, iLogger, PVLOGMSG_ERR,
            (0,"AndroidSurfaceOutput::writeAsync: Error - unrecognized format type"));
        status= PVMFFailure;
        break;
    }

    //Schedule asynchronous response
    PVMFCommandId cmdid=iCommandCounter++;
    WriteResponse resp(status,cmdid,aContext,aTimestamp);
    iWriteResponseQueue.push_back(resp);
    RunIfNotReady();

    return cmdid;
}

void AndroidSurfaceOutput::writeComplete(PVMFStatus aStatus, PVMFCommandId  write_cmd_id, OsclAny* aContext)
{
    PVLOGGER_LOGMSG(PVLOGMSG_INST_LLDBG, iLogger, PVLOGMSG_STACK_TRACE, (0,"AndroidSurfaceOutput::writeComplete() called"));
    //won't be called since this component is a sink.
}


PVMFCommandId  AndroidSurfaceOutput::readAsync(uint8* data, uint32 max_data_len, OsclAny* aContext,
                                            int32* formats, uint16 num_formats)
{
    PVLOGGER_LOGMSG(PVLOGMSG_INST_LLDBG, iLogger, PVLOGMSG_STACK_TRACE, (0,"AndroidSurfaceOutput::readAsync() called"));
    //read not supported.
    OsclError::Leave(OsclErrNotSupported);
    return -1;
}


void AndroidSurfaceOutput::readComplete(PVMFStatus aStatus, PVMFCommandId  read_cmd_id, int32 format_index,
                                    const PvmiMediaXferHeader& data_header_info, OsclAny* aContext)
{
    PVLOGGER_LOGMSG(PVLOGMSG_INST_LLDBG, iLogger, PVLOGMSG_STACK_TRACE, (0,"AndroidSurfaceOutput::readComplete() called"));
    //won't be called since this component is a sink.
}


void AndroidSurfaceOutput::statusUpdate(uint32 status_flags)
{
    PVLOGGER_LOGMSG(PVLOGMSG_INST_LLDBG, iLogger, PVLOGMSG_STACK_TRACE, (0,"AndroidSurfaceOutput::statusUpdate() called"));
    //won't be called since this component is a sink.
}


void AndroidSurfaceOutput::cancelCommand(PVMFCommandId  command_id)
{
    PVLOGGER_LOGMSG(PVLOGMSG_INST_LLDBG, iLogger, PVLOGMSG_STACK_TRACE, (0,"AndroidSurfaceOutput::cancelCommand() called"));

    //the purpose of this API is to cancel a writeAsync command and report
    //completion ASAP.

    //in this implementation, the write commands are executed immediately
    //when received so it isn't really possible to cancel.
    //just report completion immediately.

    for (uint32 i=0;i<iWriteResponseQueue.size();i++)
    {
        if (iWriteResponseQueue[i].iCmdId==command_id)
        {
            //report completion
            if (iPeer)
                iPeer->writeComplete(iWriteResponseQueue[i].iStatus,iWriteResponseQueue[i].iCmdId,(OsclAny*)iWriteResponseQueue[i].iContext);
            iWriteResponseQueue.erase(&iWriteResponseQueue[i]);
            break;
        }
    }
}

void AndroidSurfaceOutput::cancelAllCommands()
{
    PVLOGGER_LOGMSG(PVLOGMSG_INST_LLDBG, iLogger, PVLOGMSG_STACK_TRACE, (0,"AndroidSurfaceOutput::cancelAllCommands() called"));

    //the purpose of this API is to cancel all writeAsync commands and report
    //completion ASAP.

    //in this implementaiton, the write commands are executed immediately 
    //when received so it isn't really possible to cancel.
    //just report completion immediately.

    for (uint32 i=0;i<iWriteResponseQueue.size();i++)
    {
        //report completion
        if (iPeer)
            iPeer->writeComplete(iWriteResponseQueue[i].iStatus,iWriteResponseQueue[i].iCmdId,(OsclAny*)iWriteResponseQueue[i].iContext);
        iWriteResponseQueue.erase(&iWriteResponseQueue[i]);
    }
}

void AndroidSurfaceOutput::setObserver (PvmiConfigAndCapabilityCmdObserver* aObserver)
{
    OSCL_UNUSED_ARG(aObserver);
    //not needed since this component only supports synchronous capability & config
    //APIs.
}

PVMFStatus AndroidSurfaceOutput::getParametersSync(PvmiMIOSession aSession, PvmiKeyType aIdentifier,
                                              PvmiKvp*& aParameters, int& num_parameter_elements,
                                              PvmiCapabilityContext aContext)
{
    OSCL_UNUSED_ARG(aSession);
    OSCL_UNUSED_ARG(aContext);
    aParameters=NULL;

    // This is a query for the list of supported formats.
    if(pv_mime_strcmp(aIdentifier, INPUT_FORMATS_CAP_QUERY) == 0)
    {
        aParameters=(PvmiKvp*)oscl_malloc(sizeof(PvmiKvp));
        if (aParameters == NULL) return PVMFErrNoMemory;
        aParameters[num_parameter_elements++].value.uint32_value=(uint32) PVMF_YUV420;
            return PVMFSuccess;
        }

    //unrecognized key.
    return PVMFFailure;
}

PVMFStatus AndroidSurfaceOutput::releaseParameters(PvmiMIOSession aSession, PvmiKvp* aParameters, int num_elements)
{
    //release parameters that were allocated by this component.
    if (aParameters)
    {
        oscl_free(aParameters);
        return PVMFSuccess;
    }
    return PVMFFailure;
}

void AndroidSurfaceOutput ::createContext(PvmiMIOSession aSession, PvmiCapabilityContext& aContext)
{
    OsclError::Leave(OsclErrNotSupported);
}

void AndroidSurfaceOutput::setContextParameters(PvmiMIOSession aSession, PvmiCapabilityContext& aContext,
                                           PvmiKvp* aParameters, int num_parameter_elements)
{
    OsclError::Leave(OsclErrNotSupported);
}

void AndroidSurfaceOutput::DeleteContext(PvmiMIOSession aSession, PvmiCapabilityContext& aContext)
{
    OsclError::Leave(OsclErrNotSupported);
}


void AndroidSurfaceOutput::setParametersSync(PvmiMIOSession aSession, PvmiKvp* aParameters,
                                        int num_elements, PvmiKvp * & aRet_kvp)
{
    OSCL_UNUSED_ARG(aSession);

    aRet_kvp = NULL;

    LOGV("setParametersSync");
    for (int32 i=0;i<num_elements;i++)
    {
        //Check against known video parameter keys...
        if (pv_mime_strcmp(aParameters[i].key, MOUT_VIDEO_FORMAT_KEY) == 0)
        {
            iVideoFormatString=aParameters[i].value.pChar_value;
            iVideoFormat=GetFormatIndex(iVideoFormatString.get_str());
            PVLOGGER_LOGMSG(PVLOGMSG_INST_LLDBG, iLogger, PVLOGMSG_STACK_TRACE,
                (0,"AndroidSurfaceOutput::setParametersSync() Video Format Key, Value %s",iVideoFormatString.get_str()));
        }
        else if (pv_mime_strcmp(aParameters[i].key, MOUT_VIDEO_WIDTH_KEY) == 0)
        {
            iVideoWidth=(int32)aParameters[i].value.uint32_value;
            iVideoWidthValid=true;
            LOGV("iVideoWidth=%d", iVideoWidth);
            PVLOGGER_LOGMSG(PVLOGMSG_INST_LLDBG, iLogger, PVLOGMSG_STACK_TRACE,
                (0,"AndroidSurfaceOutput::setParametersSync() Video Width Key, Value %d",iVideoWidth));
        }
        else if (pv_mime_strcmp(aParameters[i].key, MOUT_VIDEO_HEIGHT_KEY) == 0)
        {
            iVideoHeight=(int32)aParameters[i].value.uint32_value;
            iVideoHeightValid=true;
            LOGV("iVideoHeight=%d", iVideoHeight);
            PVLOGGER_LOGMSG(PVLOGMSG_INST_LLDBG, iLogger, PVLOGMSG_STACK_TRACE,
                (0,"AndroidSurfaceOutput::setParametersSync() Video Height Key, Value %d",iVideoHeight));
        }
        else if (pv_mime_strcmp(aParameters[i].key, MOUT_VIDEO_DISPLAY_HEIGHT_KEY) == 0)
        {
            iVideoDisplayHeight=(int32)aParameters[i].value.uint32_value;
            iVideoDisplayHeightValid=true;
            LOGV("iVideoDisplayHeight=%d", iVideoDisplayHeight);
            PVLOGGER_LOGMSG(PVLOGMSG_INST_LLDBG, iLogger, PVLOGMSG_STACK_TRACE,
                (0,"AndroidSurfaceOutput::setParametersSync() Video Display Height Key, Value %d",iVideoDisplayHeight));
        }
        else if (pv_mime_strcmp(aParameters[i].key, MOUT_VIDEO_DISPLAY_WIDTH_KEY) == 0)
        {
            iVideoDisplayWidth=(int32)aParameters[i].value.uint32_value;
            iVideoDisplayWidthValid=true;
            LOGV("iVideoDisplayWidth=%d", iVideoDisplayWidth);
            PVLOGGER_LOGMSG(PVLOGMSG_INST_LLDBG, iLogger, PVLOGMSG_STACK_TRACE,
                (0,"AndroidSurfaceOutput::setParametersSync() Video Display Width Key, Value %d",iVideoDisplayWidth));
        }
        else if (pv_mime_strcmp(aParameters[i].key, MOUT_VIDEO_SUBFORMAT_KEY) == 0)
        {
            iVideoSubFormat=(PVMFFormatType) aParameters[i].value.uint32_value;
            iVideoSubFormatValid = true;
            PVLOGGER_LOGMSG(PVLOGMSG_INST_LLDBG, iLogger, PVLOGMSG_STACK_TRACE,
                    (0,"AndroidSurfaceOutput::setParametersSync() Video SubFormat Key, Value %d",iVideoSubFormat));
LOGV("VIDEO SUBFORMAT SET TO %d\n",iVideoSubFormat);
        }
        else
        {
            //if we get here the key is unrecognized.

            PVLOGGER_LOGMSG(PVLOGMSG_INST_LLDBG, iLogger, PVLOGMSG_STACK_TRACE,
                (0,"AndroidSurfaceOutput::setParametersSync() Error, unrecognized key = %s", aParameters[i].key));

            //set the return value to indicate the unrecognized key
            //and return.
            aRet_kvp = &aParameters[i];
            return;
        }
    }
    initCheck();
}

PVMFCommandId AndroidSurfaceOutput::setParametersAsync(PvmiMIOSession aSession, PvmiKvp* aParameters,
                                                  int num_elements, PvmiKvp*& aRet_kvp, OsclAny* context)
{
    OsclError::Leave(OsclErrNotSupported);
    return -1;
}

uint32 AndroidSurfaceOutput::getCapabilityMetric (PvmiMIOSession aSession)
{
    return 0;
}

PVMFStatus AndroidSurfaceOutput::verifyParametersSync (PvmiMIOSession aSession, PvmiKvp* aParameters, int num_elements)
{
    return PVMFSuccess;
}

//
// For active timing support
//
OSCL_EXPORT_REF PVMFStatus AndroidSurfaceOutput_ActiveTimingSupport::SetClock(OsclClock *clockVal)
{
    iClock=clockVal;
    return PVMFSuccess;
}

OSCL_EXPORT_REF void AndroidSurfaceOutput_ActiveTimingSupport::addRef()
{
}

OSCL_EXPORT_REF void AndroidSurfaceOutput_ActiveTimingSupport::removeRef()
{
}

OSCL_EXPORT_REF bool AndroidSurfaceOutput_ActiveTimingSupport::queryInterface(const PVUuid& aUuid, PVInterface*& aInterface)
{
    aInterface=NULL;
    PVUuid uuid;
    queryUuid(uuid);
    if (uuid==aUuid)
    {
        PvmiClockExtensionInterface* myInterface = OSCL_STATIC_CAST(PvmiClockExtensionInterface*,this);
        aInterface = OSCL_STATIC_CAST(PVInterface*, myInterface);
        return true;
    }
    return false;
}

void AndroidSurfaceOutput_ActiveTimingSupport::queryUuid(PVUuid& uuid)
{
    uuid=PvmiClockExtensionInterfaceUuid;
}

uint32 AndroidSurfaceOutput_ActiveTimingSupport::GetDelayMsec(PVMFTimestamp& aTs)
{
    if (iClock)
    {
        uint32 currentTime=0;
        bool overflow=false;
        iClock->GetCurrentTime32(currentTime, overflow, OSCLCLOCK_MSEC);
        if(aTs>currentTime)
        {
            return aTs-currentTime;
        }
    }
    return 0;
}

//
// Private section
//

void AndroidSurfaceOutput::Run()
{
    //send async command responses
    while (!iCommandResponseQueue.empty())
    {
        if (iObserver)
            iObserver->RequestCompleted(PVMFCmdResp(iCommandResponseQueue[0].iCmdId, iCommandResponseQueue[0].iContext, iCommandResponseQueue[0].iStatus));
        iCommandResponseQueue.erase(&iCommandResponseQueue[0]);
    }

    //send async write completion
    while (!iWriteResponseQueue.empty())
    {
        //report write complete
        if (iPeer)
            iPeer->writeComplete(iWriteResponseQueue[0].iStatus,iWriteResponseQueue[0].iCmdId,(OsclAny*)iWriteResponseQueue[0].iContext);
        iWriteResponseQueue.erase(&iWriteResponseQueue[0]);
    }
}

// create a frame buffer for software codecs
OSCL_EXPORT_REF bool AndroidSurfaceOutput::initCheck()
{

    // emulator never uses subformat
    if (mEmulation) iVideoSubFormatValid = true;

    // initialize once, and only when we have all the required parameters
    if (mInitialized || !iVideoDisplayWidthValid || !iVideoDisplayHeightValid ||
            !iVideoWidthValid || !iVideoHeightValid || !iVideoSubFormatValid)
        return mInitialized;

    // color converter requires even height/width
    int displayWidth = iVideoDisplayWidth;
    int displayHeight = iVideoDisplayHeight;
    int frameWidth = iVideoWidth;
    int frameHeight = iVideoHeight;
    int frameSize;

    // FIXME: Need to move hardware specific code to partners directory

#if HAVE_ANDROID_OS
    // Dream hardware codec uses semi-planar format
    if (!mEmulation && iVideoSubFormat == PVMF_YUV420_SEMIPLANAR_YVU) {
        LOGV("using hardware codec");
        mHardwareCodec = true;
    } else
#endif

    // software codec
    {
        LOGV("using software codec");

#if HAVE_ANDROID_OS
        // emulation
        if (mEmulation)
#endif
        {
            // RGB-565 frames are 2 bytes/pixel
            displayWidth = (displayWidth + 1) & -2;
            displayHeight = (displayHeight + 1) & -2;
            frameWidth = (frameWidth + 1) & -2;
            frameHeight = (frameHeight + 1) & -2;
            frameSize = frameWidth * frameHeight * 2;

            // create frame buffer heap and register with surfaceflinger
            mFrameHeap = new MemoryHeapBase(frameSize * kBufferCount);
            if (mFrameHeap->heapID() < 0) {
                LOGE("Error creating frame buffer heap");
                return false;
            }
            mSurface->registerBuffers(displayWidth, displayHeight, frameWidth, frameHeight, PIXEL_FORMAT_RGB_565, mFrameHeap);

            // create frame buffers
            for (int i = 0; i < kBufferCount; i++) {
                mFrameBuffers[i] = i * frameSize;
            }

            // initialize software color converter
            iColorConverter = ColorConvert16::NewL();
            iColorConverter->Init(displayWidth, displayHeight, frameWidth, displayWidth, displayHeight, displayWidth, CCROTATE_NONE);
            iColorConverter->SetMemHeight(frameHeight);
            iColorConverter->SetMode(1);
        }

#if HAVE_ANDROID_OS
        // FIXME: hardware specific
        else {
            // YUV420 frames are 1.5 bytes/pixel
            frameSize = (frameWidth * frameHeight * 3) / 2;

            // create frame buffer heap
            sp<MemoryHeapBase> master = new MemoryHeapBase(pmem_adsp, frameSize * kBufferCount);
            if (master->heapID() < 0) {
                LOGE("Error creating frame buffer heap");
                return false;
            }
            master->setDevice(pmem);
            mHeapPmem = new MemoryHeapPmem(master, 0);
            mHeapPmem->slap();
            master.clear();
            mSurface->registerBuffers(displayWidth, displayHeight, frameWidth, frameHeight, PIXEL_FORMAT_YCbCr_420_SP, mHeapPmem);

            // create frame buffers
            for (int i = 0; i < kBufferCount; i++) {
                mFrameBuffers[i] = i * frameSize;
            }
        }
#endif

        LOGV("video = %d x %d", displayWidth, displayHeight);
        LOGV("frame = %d x %d", frameWidth, frameHeight);
        LOGV("frame #bytes = %d", frameSize);

        // register frame buffers with SurfaceFlinger
        mFrameBufferIndex = 0;
        mInitialized = true;
    }

    return mInitialized;
}

OSCL_EXPORT_REF PVMFStatus AndroidSurfaceOutput::WriteFrameBuf(uint8* aData, uint32 aDataLen, const PvmiMediaXferHeader& data_header_info)
{
    if (mSurface != 0) {

        // initalized?
        if (!mInitialized) {
            LOGV("initializing for hardware");
            // FIXME: Check for hardware codec - move to partners directory
            if (iVideoSubFormat != PVMF_YUV420_SEMIPLANAR_YVU) return PVMFFailure;
            LOGV("got expected format");
            LOGV("private data pointer is 0%p\n", data_header_info.private_data_ptr);

            uint32 fd;
            if (!getPmemFd(data_header_info.private_data_ptr, &fd)) {
                LOGE("Error getting pmem heap from private_data_ptr");
                return PVMFFailure;
            }
            sp<MemoryHeapBase> master = (MemoryHeapBase *) fd;
            master->setDevice(pmem);
            mHeapPmem = new MemoryHeapPmem(master, 0);
            mHeapPmem->slap();
            master.clear();

            // register frame buffers with SurfaceFlinger
            mSurface->registerBuffers(iVideoDisplayWidth, iVideoDisplayHeight, iVideoWidth, iVideoHeight, PIXEL_FORMAT_YCbCr_420_SP, mHeapPmem);

            mInitialized = true;
        }

        // hardware codec
        if (mHardwareCodec) {
            // get pmem offset
            if (!getOffset(data_header_info.private_data_ptr, &mOffset)) {
                LOGE("Error getting pmem offset from private_data_ptr");
                return PVMFFailure;
            }
            // post to SurfaceFlinger
            mSurface->postBuffer(mOffset);
        }

        // software codec
        else {
            if (mEmulation) {
                iColorConverter->Convert(aData, static_cast<uint8*>(mFrameHeap->base()) + mFrameBuffers[mFrameBufferIndex]);
            } else {
                convertFrame(aData, static_cast<uint8*>(mHeapPmem->base()) + mFrameBuffers[mFrameBufferIndex], aDataLen);
            }
            // post to SurfaceFlinger
            if (++mFrameBufferIndex == kBufferCount) mFrameBufferIndex = 0;
            mSurface->postBuffer(mFrameBuffers[mFrameBufferIndex]);
        }
    }
    return PVMFSuccess;
}

OSCL_EXPORT_REF void AndroidSurfaceOutput::CloseFrameBuf()
{
    LOGV("CloseFrameBuf");
    if (!mInitialized) return;

    mInitialized = false;
    if (mSurface.get()) {
        LOGV("unregisterBuffers");
        mSurface->unregisterBuffers();
        mSurface.clear();
    }

    // free frame buffers
    LOGV("free frame buffers");
    for (int i = 0; i < kBufferCount; i++) {
        mFrameBuffers[i] = 0;
    }

    // free heaps
    LOGV("free mFrameHeap");
    mFrameHeap.clear();
    LOGV("free mHeapPmem");
    mHeapPmem.clear();

    // free color converter
    if (iColorConverter != 0)
    {
        LOGV("free color converter");
        delete iColorConverter;
        iColorConverter = 0;
    }
}

OSCL_EXPORT_REF bool AndroidSurfaceOutput::GetVideoSize(int *w, int *h) {

    if (iVideoDisplayHeightValid && iVideoDisplayWidthValid)
    {
        *w = iVideoDisplayWidth;
        *h = iVideoDisplayHeight;
        return true;
    }
    return false;
}

bool AndroidSurfaceOutput::getPmemFd(OsclAny *private_data_ptr, uint32 *pmemFD)
{
    PLATFORM_PRIVATE_LIST *listPtr = NULL;
    PLATFORM_PRIVATE_PMEM_INFO *pmemInfoPtr = NULL;
    bool returnType = false;
    LOGV("in getPmemfd - privatedataptr=%p\n",private_data_ptr);
    listPtr = (PLATFORM_PRIVATE_LIST*) private_data_ptr;

    for (uint32 i=0;i<listPtr->nEntries;i++)
    {
        if(listPtr->entryList->type == PLATFORM_PRIVATE_PMEM)
        {
            LOGV("in getPmemfd - entry type = %d\n",listPtr->entryList->type);
          pmemInfoPtr = (PLATFORM_PRIVATE_PMEM_INFO*) (listPtr->entryList->entry);
          returnType = true;
          if(pmemInfoPtr){
            *pmemFD = pmemInfoPtr->pmem_fd;
            LOGV("in getPmemfd - pmemFD = %d\n",*pmemFD);
          }
          break;
        }
    }
    return returnType;
}

bool AndroidSurfaceOutput::getOffset(OsclAny *private_data_ptr, uint32 *offset)
{
    PLATFORM_PRIVATE_LIST *listPtr = NULL;
    PLATFORM_PRIVATE_PMEM_INFO *pmemInfoPtr = NULL;
    bool returnType = false;

    listPtr = (PLATFORM_PRIVATE_LIST*) private_data_ptr;
    LOGV("in getOffset: listPtr = %p\n",listPtr);
    for (uint32 i=0;i<listPtr->nEntries;i++)
    {
        if(listPtr->entryList->type == PLATFORM_PRIVATE_PMEM)
        {
            LOGV(" in getOffset: entrytype = %d\n",listPtr->entryList->type);

          pmemInfoPtr = (PLATFORM_PRIVATE_PMEM_INFO*) (listPtr->entryList->entry);
          returnType = true;
          if(pmemInfoPtr){
            *offset = pmemInfoPtr->offset;
            LOGV("in getOffset: offset = %d\n",*offset);
          }
          break;
        }
    }
    return returnType;
}

static inline void* byteOffset(void* p, size_t offset) { return (void*)((uint8_t*)p + offset); }

void AndroidSurfaceOutput::convertFrame(void* src, void* dst, size_t len)
{
    // copy the Y plane
    size_t y_plane_size = iVideoWidth * iVideoHeight;
    //LOGV("len=%u, y_plane_size=%u", len, y_plane_size);
    memcpy(dst, src, y_plane_size + iVideoWidth);

    // re-arrange U's and V's
    uint16_t* pu = (uint16_t*)byteOffset(src, y_plane_size);
    uint16_t* pv = (uint16_t*)byteOffset(pu, y_plane_size / 4);
    uint32_t* p = (uint32_t*)byteOffset(dst, y_plane_size);

    int count = y_plane_size / 8;
    //LOGV("u = %p, v = %p, p = %p, count = %d", pu, pv, p, count);
    do {
        uint32_t u = *pu++;
        uint32_t v = *pv++;
        *p++ = ((u & 0xff) << 8) | ((u & 0xff00) << 16) | (v & 0xff) | ((v & 0xff00) << 8);
    } while (--count);
}

