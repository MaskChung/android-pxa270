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
#ifndef PVMI_MEDIA_IO_FILEOUTPUT_H_INCLUDED
#define PVMI_MEDIA_IO_FILEOUTPUT_H_INCLUDED

#ifndef PVMI_MIO_CONTROL_H_INCLUDED
#include "pvmi_mio_control.h"
#endif
#ifndef PVMI_MEDIA_TRANSFER_H_INCLUDED
#include "pvmi_media_transfer.h"
#endif
#ifndef OSCL_SCHEDULER_AO_H_INCLUDED
#include "oscl_scheduler_ao.h"
#endif
#ifndef PVMI_MEDIA_IO_OBSERVER_H_INCLUDED
#include "pvmi_media_io_observer.h"
#endif
#ifndef OSCL_FILE_IO_H_INCLUDED
#include "oscl_file_io.h"
#endif
#ifndef PVMI_CONFIG_AND_CAPABILITY_H_INCLUDED
#include "pvmi_config_and_capability.h"
#endif
#ifndef OSCL_STRING_CONTAINERS_H_INCLUDED
#include "oscl_string_containers.h"
#endif
#ifndef PVMI_MEDIA_IO_CLOCK_EXTENSION_H_INCLUDED
#include "pvmi_media_io_clock_extension.h"
#endif

#include "avi_write.h"

class PVLogger;
class PVRefFileOutputTestObserver;
class OsclClock;
class PVRefFileOutput;

#define DEFAULT_NUM_DECODED_FRAMES_CAPABILITY	6

typedef struct
{
    uint32      chunkID;
    uint32      chunkSize;
    uint32      format;
} RIFFChunk;

typedef struct
{
    uint32      subchunk1ID;
    uint32      subchunk1Size;
    uint16     audioFormat;
    uint16     numChannels;
    uint32      sampleRate;
    uint32      byteRate;
    uint16     blockAlign;
    uint16     bitsPerSample;
} fmtSubchunk;

typedef struct
{
    uint32      subchunk2ID;
    uint32      subchunk2Size;
} dataSubchunk;

// A test feature for simulating a component with active timing.
class PVRefFileOutputActiveTimingSupport: public PvmiClockExtensionInterface
{
    public:

        PVRefFileOutputActiveTimingSupport(uint32 limit)
                : iLastTimestampValid(false)
                , iDelay(0)
                , iQueueLimit(limit)
                , iClock(NULL)
                , iLogger(NULL)
        {}
        virtual ~PVRefFileOutputActiveTimingSupport()
        {}

        //from PvmiClockExtensionInterface
        OSCL_IMPORT_REF PVMFStatus SetClock(OsclClock *clockVal) ;

        //from PVInterface
        OSCL_IMPORT_REF void addRef() ;
        OSCL_IMPORT_REF void removeRef() ;
        OSCL_IMPORT_REF bool queryInterface(const PVUuid& uuid, PVInterface*& iface) ;

        void queryUuid(PVUuid& uuid);

        uint32 GetDelayMsec(PVMFTimestamp&);
        void AdjustClock(PVMFTimestamp& aTs);

        PVMFTimestamp iLastTimestamp;
        bool iLastTimestampValid;
        uint32 iDelay;

        uint32 iQueueLimit;

        OsclClock* iClock;
        PVLogger* iLogger;

        //query for whether playback clock is in frame-step mode.
        bool FrameStepMode();

};


// This class implements the reference media IO for file output.
// This class constitutes the Media IO component

class PVRefFileOutput :	public OsclTimerObject
            , public PvmiMIOControl
            , public PvmiMediaTransfer
            , public PvmiCapabilityAndConfig

{
    public:
        OSCL_IMPORT_REF PVRefFileOutput(const oscl_wchar* aFileName,
                                        bool aActiveTiming = false);

        OSCL_IMPORT_REF PVRefFileOutput(const OSCL_wString& aFileName, bool logStrings = false);

        // Expanded constructor with test features.
        OSCL_IMPORT_REF PVRefFileOutput(const OSCL_wString& aFileName
                                        , PVRefFileOutputTestObserver*aObserver
                                        , bool aActiveTiming, uint32 aQueueLimit
                                        , bool aSimFlowControl
                                        , bool logStrings = true);

        ~PVRefFileOutput();

        // APIs from PvmiMIOControl

        PVMFStatus connect(PvmiMIOSession& aSession, PvmiMIOObserver* aObserver);

        PVMFStatus disconnect(PvmiMIOSession aSession);

        PVMFCommandId QueryUUID(const PvmfMimeString& aMimeType, Oscl_Vector<PVUuid, OsclMemAllocator>& aUuids,
                                bool aExactUuidsOnly = false, const OsclAny* aContext = NULL);

        PVMFCommandId QueryInterface(const PVUuid& aUuid, PVInterface*& aInterfacePtr, const OsclAny* aContext = NULL);

        PvmiMediaTransfer* createMediaTransfer(PvmiMIOSession& aSession, PvmiKvp* read_formats = NULL, int32 read_flags = 0,
                                               PvmiKvp* write_formats = NULL, int32 write_flags = 0);

        void deleteMediaTransfer(PvmiMIOSession& aSession, PvmiMediaTransfer* media_transfer);

        PVMFCommandId Init(const OsclAny* aContext = NULL);

        PVMFCommandId Reset(const OsclAny* aContext = NULL);

        PVMFCommandId Start(const OsclAny* aContext = NULL);

        PVMFCommandId Pause(const OsclAny* aContext = NULL);

        PVMFCommandId Flush(const OsclAny* aContext = NULL);

        PVMFCommandId DiscardData(const OsclAny* aContext = NULL);

        PVMFCommandId DiscardData(PVMFTimestamp aTimestamp, const OsclAny* aContext = NULL);

        PVMFCommandId Stop(const OsclAny* aContext = NULL);

        PVMFCommandId CancelAllCommands(const OsclAny* aContext = NULL);

        PVMFCommandId CancelCommand(PVMFCommandId aCmdId, const OsclAny* aContext = NULL);

        void ThreadLogon();

        void ThreadLogoff();

        // APIs from PvmiMediaTransfer

        void setPeer(PvmiMediaTransfer* aPeer);

        void useMemoryAllocators(OsclMemAllocator* write_alloc = NULL);

        PVMFCommandId writeAsync(uint8 format_type, int32 format_index,
                                 uint8* data, uint32 data_len,
                                 const PvmiMediaXferHeader& data_header_info,
                                 OsclAny* aContext = NULL);

        void writeComplete(PVMFStatus aStatus,
                           PVMFCommandId  write_cmd_id,
                           OsclAny* aContext);

        PVMFCommandId  readAsync(uint8* data, uint32 max_data_len,
                                 OsclAny* aContext = NULL,
                                 int32* formats = NULL, uint16 num_formats = 0);

        void readComplete(PVMFStatus aStatus, PVMFCommandId  read_cmd_id, int32 format_index,
                          const PvmiMediaXferHeader& data_header_info, OsclAny* aContext);

        void statusUpdate(uint32 status_flags);

        void cancelCommand(PVMFCommandId  command_id);

        void cancelAllCommands();

        // Pure virtuals from PvmiCapabilityAndConfig

        void setObserver(PvmiConfigAndCapabilityCmdObserver* aObserver);

        PVMFStatus getParametersSync(PvmiMIOSession aSession, PvmiKeyType aIdentifier,
                                     PvmiKvp*& aParameters, int& num_parameter_elements, PvmiCapabilityContext aContext);

        PVMFStatus releaseParameters(PvmiMIOSession aSession, PvmiKvp* aParameters, int num_elements);

        void createContext(PvmiMIOSession aSession, PvmiCapabilityContext& aContext);

        void setContextParameters(PvmiMIOSession aSession, PvmiCapabilityContext& aContext,
                                  PvmiKvp* aParameters, int num_parameter_elements);

        void DeleteContext(PvmiMIOSession aSession, PvmiCapabilityContext& aContext);

        void setParametersSync(PvmiMIOSession aSession, PvmiKvp* aParameters,
                               int num_elements, PvmiKvp * & aRet_kvp);

        PVMFCommandId setParametersAsync(PvmiMIOSession aSession, PvmiKvp* aParameters,
                                         int num_elements, PvmiKvp*& aRet_kvp, OsclAny* context = NULL);

        uint32 getCapabilityMetric(PvmiMIOSession aSession);

        PVMFStatus verifyParametersSync(PvmiMIOSession aSession, PvmiKvp* aParameters, int num_elements);

        void setFormatMask(uint32 mask);

        void UpdateWaveChunkSize();

        void WriteHeaders();
        void InitializeAVI(int width, int height);
        void AddChunk(uint8* chunk, uint32 size, uint32 ckid);
        int32 yuv2rgb(uint8 * pBufRGBRev, uint8 * pBufYUV, int32 width, int32 height);
        void UpdateVideoChunkHeaderIdx();

    private:
        void initData();

        // From OsclTimerObject
        void Run();

        void Reschedule();

        void Cleanup();
        void ResetData();

        PvmiMediaTransfer* iPeer;

        // The PvmiMIOControl class observer.
        PvmiMIOObserver* iObserver;

        //for generating command IDs
        uint32 iCommandCounter;

        //State
        enum PVRefFOState
        {
            STATE_IDLE
            , STATE_LOGGED_ON
            , STATE_INITIALIZED
            , STATE_STARTED
            , STATE_PAUSED
        };
        PVRefFOState iState;

        //Control command handling.
        class CommandResponse
        {
            public:
                CommandResponse(PVMFStatus s, PVMFCommandId id, const OsclAny* ctx)
                        : iStatus(s), iCmdId(id), iContext(ctx)
                {}

                PVMFStatus iStatus;
                PVMFCommandId iCmdId;
                const OsclAny* iContext;
        };
        Oscl_Vector<CommandResponse, OsclMemAllocator> iCommandResponseQueue;
        void QueueCommandResponse(CommandResponse&);

        //Write command handling
        class WriteResponse
        {
            public:
                WriteResponse(PVMFStatus s, PVMFCommandId id, const OsclAny* ctx, const PVMFTimestamp& ts, bool discard)
                        : iStatus(s), iCmdId(id), iContext(ctx), iTimestamp(ts), iDiscard(discard)
                {}

                PVMFStatus iStatus;
                PVMFCommandId iCmdId;
                const OsclAny* iContext;
                PVMFTimestamp iTimestamp;
                bool iDiscard;
        };
        Oscl_Vector<WriteResponse, OsclMemAllocator> iWriteResponseQueue;

        // Output file parameters
        OSCL_wHeapString<OsclMemAllocator> iOutputFileName;
        Oscl_FileServer iFs;
        bool iFsConnected;
        Oscl_File iOutputFile;
        bool iFileOpened;

        // Audio parameters.
        OSCL_HeapString<OsclMemAllocator> iAudioFormatString;
        PVMFFormatType iAudioFormat;
        int32 iAudioNumChannels;
        bool iAudioNumChannelsValid;
        int32 iAudioSamplingRate;
        bool iAudioSamplingRateValid;

        // Video parameters
        OSCL_HeapString<OsclMemAllocator> iVideoFormatString;
        PVMFFormatType iVideoFormat;
        int32 iVideoHeight;
        bool iVideoHeightValid;
        int32 iVideoWidth;
        bool iVideoWidthValid;
        int32 iVideoDisplayHeight;
        bool iVideoDisplayHeightValid;
        int32 iVideoDisplayWidth;
        bool iVideoDisplayWidthValid;

        // Text parameters
        OSCL_HeapString<OsclMemAllocator> iTextFormatString;
        PVMFFormatType iTextFormat;

        //For logging
        PVLogger* iLogger;

        //A switch for selecting whether to log some strings along
        //with the media data in the output file.
        bool iLogStrings;
        bool iParametersLogged;
        void LogParameters();
        void LogFrame(uint32 aSeqNum, const PVMFTimestamp& aTimestamp, uint32);
        void LogEndOfStream(uint32 aSeqNum, const PVMFTimestamp& aTimestamp);
        void LogCodecHeader(uint32 aSeqNum, const PVMFTimestamp& aTimestamp, uint32);

        //For implementing the write flow control
        bool iWriteBusy;
        uint32 iWriteBusySeqNum;
        bool CheckWriteBusy(uint32);

        //a test feature for testing flow control in the peer.
        bool iSimFlowControl;

        //a test observer class
        PVRefFileOutputTestObserver* iTestObserver;

        //a test feature for simulating active timing.
        PVRefFileOutputActiveTimingSupport* iActiveTiming;

        //a test feature to allow the commands such as GetCapability
        //to return a distinctive format type
        uint32 iFormatMask;

        //called when re-config notification is recvd
        PVMFStatus HandleReConfig(uint32 aReconfigSeqNum);

        //if iUseClockExtension set to true, no data is dropped
        bool iUseClockExtension;

        // used to create the WAV file output.
        RIFFChunk    iRIFFChunk;
        fmtSubchunk  iFmtSubchunk;
        dataSubchunk iDataSubchunk;
        bool         iHeaderWritten;
        uint32       iAudioFormatType;
        uint32       iVideoFormatType;
        PVMFTimestamp iVideoLastTimeStamp;
        bool         iInitializeAVIDone;

        AVIMainHeader iAVIMainHeader;
        AVIStreamHeader iAVIStreamHeader;
        BitMapInfoHeader bi_hdr;
        AVIIndex         iAVIIndex;
        uint32        iAVIChunkSize;
        uint32        iVideoHeaderPosition;
        IndexBuffer   iIndexBuffer;
        uint32		  iAVIMainHeaderPosition;
        uint32		  iAVIStreamHeaderPosition;
        uint32        iVideoCount;
        uint32        iPreviousOffset;
};

//An observer class for test support.
class PVRefFileOutputTestObserver
{
    public:
        virtual ~PVRefFileOutputTestObserver() {}

        OSCL_IMPORT_REF virtual void Pos(PVMFTimestamp& aTimestamp) = 0;
};


#endif // PVMI_MEDIA_IO_FILEOUTPUT_H_INCLUDED

