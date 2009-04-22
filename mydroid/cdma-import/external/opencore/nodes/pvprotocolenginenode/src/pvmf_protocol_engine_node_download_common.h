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

#ifndef PVMF_PROTOCOLENGINE_NODE_DOWNLOAD_COMMON_H_INCLUDED
#define PVMF_PROTOCOLENGINE_NODE_DOWNLOAD_COMMON_H_INCLUDED

#ifndef PVMF_PROTOCOLENGINE_NODE_COMMON_H_INCLUDED
#include "pvmf_protocol_engine_node_common.h"
#endif

////////////////////////////////////////////////////////////////////////////////////
//////	DownloadContainer
////////////////////////////////////////////////////////////////////////////////////
class DownloadContainer : public ProtocolContainer
{
    public:
        // constructor
        DownloadContainer(PVMFProtocolEngineNode *aNode = NULL);
        virtual ~DownloadContainer()
        {
            ;
        }

        virtual void deleteProtocolObjects();
        virtual int32 doPreStart();
        virtual bool doPause();
        virtual void doClear(const bool aNeedDelete = false);
        virtual void doCancelClear();
        virtual bool doInfoUpdate(const uint32 downloadStatus);
        virtual bool addSourceData(OsclAny* aSourceData);
        virtual bool createCfgFile(OSCL_String& aUri);
        virtual bool getProxy(OSCL_String& aProxyName, uint32 &aProxyPort);
        virtual void setHttpVersion(const uint32 aHttpVersion);
        virtual void setHttpExtensionHeaderField(OSCL_String &aFieldKey,
                OSCL_String &aFieldValue,
                const HttpMethod aMethod,
                const bool aPurgeOnRedirect);

        virtual bool handleContentRangeUnmatch();
        virtual bool downloadUpdateForHttpHeaderAvailable();
        virtual bool isStreamingPlayback();
        virtual bool handleProtocolStateComplete(PVProtocolEngineNodeInternalEvent &aEvent, PVProtocolEngineNodeInternalEventHandler *aEventHandler);

    protected:
        virtual int32 initNodeOutput();
        virtual bool initProtocol_SetConfigInfo();
        virtual void initDownloadControl();
        virtual void updateDownloadControl(const bool isDownloadComplete = false);
        virtual bool isDownloadComplete(const uint32 downloadStatus) const
        {
            return (downloadStatus == PROCESS_SUCCESS_END_OF_MESSAGE ||
                    downloadStatus == PROCESS_SUCCESS_END_OF_MESSAGE_TRUNCATED ||
                    downloadStatus == PROCESS_SUCCESS_END_OF_MESSAGE_WITH_EXTRA_DATA ||
                    downloadStatus == PROCESS_SUCCESS_END_OF_MESSAGE_BY_SERVER_DISCONNECT);
        }

    private:
        //called by createProtocolObjects()
        bool createNetworkTimer();
        bool createEventHandlers();
};

////////////////////////////////////////////////////////////////////////////////////
//////	pvHttpDownloadOutput
////////////////////////////////////////////////////////////////////////////////////

// This derived class adds data stream output
struct DownloadOutputConfig
{
    bool isResumeDownload;
    bool isRangeSupport;
    bool isNeedOpenDataStream;

    // constructor
    DownloadOutputConfig() : isResumeDownload(false),
            isRangeSupport(true),
            isNeedOpenDataStream(true)
    {
        ;
    }
};

class pvHttpDownloadOutput : public PVMFProtocolEngineNodeOutput
{
    public:
        void setOutputObject(OsclAny* aOutputObject, const uint32 aObjectType = NodeOutputType_InputPortForData);
        int32 initialize(OsclAny* aInitInfo = NULL);
        virtual int32 flushData(const uint32 aOutputType = NodeOutputType_InputPortForData);
        virtual void discardData(const bool aNeedReopen = false);
        uint32 getAvailableOutputSize();

        // constructor and destructor
        pvHttpDownloadOutput(PVMFProtocolEngineNodeOutputObserver *aObserver = NULL);
        virtual ~pvHttpDownloadOutput();

    protected:
        // write data to data stream object
        // return~0=0xffffffff for error.
        uint32 writeToDataStream(OUTPUT_DATA_QUEUE &aOutputQueue);
        bool writeToDataStream(uint8 *aBuffer, uint32 aBufferLen);
        virtual int32 openDataStream(OsclAny* aInitInfo);
        void discardDataBody(const bool aNeedReopen, const uint32 aSeekOffset = 0);
        // reset
        virtual void reset();

    protected:
        PVMFDataStreamFactory *iDataStreamFactory;
        PVMIDataStreamSyncInterface *iDataStream;
        PvmiDataStreamSession iSessionID; // PvmiDataStreamSession = int32
        bool isOpenDataStream;
        uint32 iCounter; // for debugging purpose
};

////////////////////////////////////////////////////////////////////////////////////
//////	pvDownloadControl
////////////////////////////////////////////////////////////////////////////////////

// This class does auto-resume control and download progress update for event report
class DownloadProgressInterface;
class pvDownloadControl : public DownloadControlInterface
{
    public:
        // constructor, may leave for creating download clock
        pvDownloadControl();
        virtual ~pvDownloadControl()
        {
            clear();
        }


        // set download control supporting objects:
        //		PVMFFormatProgDownloadSupportInterface object,
        //		PVMFDownloadProgressInterface object,
        //		engine playback clock object,
        //		protocol engine object,
        //		DownloadProgressInterface object,	(to get the clip duraton)
        //		PVMFProtocolEngineNodeOutput object
        void setSupportObject(OsclAny *aDLSupportObject, DownloadControlSupportObjectType aType);

        // From PVMFDownloadProgressInterface API pass down
        virtual void requestResumeNotification(const uint32 currentNPTReadPosition, bool& aDownloadComplete, bool& aNeedSendUnderflowEvent);
        void cancelResumeNotification();

        // check whether to make resume notification; if needed, then make resume notification
        // Return value: 1 means making resume notification normally (underflow->auto resume),
        //				 2 means making resume notification for download complete
        //				 0 means anything else
        virtual int32 checkResumeNotification(const bool aDownloadComplete = true);

        // From PVMFDownloadProgressInterface API
        virtual void getDownloadClock(OsclSharedPtr<OsclClock> &aClock)
        {
            OSCL_UNUSED_ARG(aClock);
        }
        // From PVMFDownloadProgressInterface API
        void setClipDuration(const uint32 aClipDurationMsec)
        {
            iClipDurationMsec = aClipDurationMsec;
        }

        void setPrevDownloadSize(uint32 aPrevDownloadSize = 0)
        {
            iPrevDownloadSize = aPrevDownloadSize;
        }

        void clear();

        // clear several fields for progressive playback repositioning
        virtual void clearPerRequest()
        {
            ;
        }
        // checks if download has completed
        bool isDownloadCompletedPerRequest()
        {
            return iDownloadComplete;
        }

    protected:

        // simple routine to focus on sending resume notification only
        virtual void sendResumeNotification(const bool aDownloadComplete);
        void sendDownloadCompleteNotification();

        // auto-resume playback decision
        bool isResumePlayback(const uint32 aDownloadRate, const uint32 aCurrDownloadSize, const uint32 aFileSize);

        // create iDlProgressClock, will leave when memory allocation fails
        void createDownloadClock();
        virtual bool updateDownloadClock() = 0;

        // ready means, download clock has been created, and all the objects have passed down
        bool isInfoReady()
        {
            return !(iDlProgressClock.GetRep() == NULL ||
                     iProgDownloadSI == NULL			 ||
                     iProtocol == NULL				 ||
                     iDownloadProgress == NULL		 ||
                     iNodeOutput == NULL);
        };

        // called by isResumePlayback()
        virtual bool isDlAlgoPreConditionMet(const uint32 aDownloadRate,
                                             const uint32 aDurationMsec,
                                             const uint32 aCurrDownloadSize,
                                             const uint32 aFileSize);

        // update duration by new playback rate, called by checkAutoResumeAlgoWithConstraint
        virtual bool checkNewDuration(const uint32 aCurrDurationMsec, uint32 &aNewDurationMsec)
        {
            aNewDurationMsec = aCurrDurationMsec;
            return true;
        }

        // called by checkAutoResumeAlgoWithConstraint()
        virtual bool approveAutoResumeDecisionShortCut(const uint32 aCurrDownloadSize,
                const uint32 aDurationMsec,
                const uint32 aPlaybackTimeMsec,
                uint32 &aPlaybackRemainingTimeMsec)
        {
            OSCL_UNUSED_ARG(aCurrDownloadSize);
            OSCL_UNUSED_ARG(aDurationMsec);
            OSCL_UNUSED_ARG(aPlaybackTimeMsec);
            OSCL_UNUSED_ARG(aPlaybackRemainingTimeMsec);
            return false;
        }

        // No constraint: for file size/clip duration/clip bitrate(i.e. playback rate), one of them must be unavailable, except
        // file size and clip duration are available, but clip bitrate is unavailable. This only applies on PDL
        virtual bool checkAutoResumeAlgoNoConstraint(const uint32 aCurrDownloadSize,
                const uint32 aFileSize,
                uint32 &aDurationMsec)
        {
            OSCL_UNUSED_ARG(aCurrDownloadSize);
            OSCL_UNUSED_ARG(aFileSize);
            OSCL_UNUSED_ARG(aDurationMsec);
            return false;
        }

        // adding buffer constraint for the algo, i.e. if buffer constraint meets (or buffer overflows), auto-resume should kick off.
        virtual bool isOutputBufferOverflow()
        {
            return false;
        }

        // handle overflow issue: // result = x*1000/y
        uint32 divisionInMilliSec(const uint32 x, const uint32 y);

    protected:
        // called by checkResumeNotification()
        bool checkDownloadCompleteForResumeNotification(const bool aDownloadComplete);

        // called by isResumePlayback()
        // with contraint: file size and clip duration are both available
        bool checkAutoResumeAlgoWithConstraint(const uint32 aDownloadRate,
                                               const uint32 aRemainingDownloadSize,
                                               const uint32 aDurationMsec,
                                               const uint32 aFileSize);

        // use fixed-point calculation to replace the float-point calculation: aRemainingDLSize<0.0009*aDownloadRate*aRemainingPlaybackTime
        bool approveAutoResumeDecision(const uint32 aRemainingDLSize,
                                       const uint32 aDownloadRate,
                                       const uint32 aRemainingPlaybackTime);

        // old algorithm
        bool isResumePlaybackWithOldAlg(const uint32 aDownloadRate,
                                        const uint32 aRemainingDownloadSize);

        // set file size to parser node for the new API, setFileSize()
        void setFileSize(const uint32 aFileSize);
        bool getPlaybackTimeFromEngineClock(uint32 &aPlaybackTime);

    protected:
        // download control
        OsclTimebase_Tickcount iEstimatedServerClockTimeBase;
        OsclSharedPtr<OsclClock> iDlProgressClock;
        OsclClock* iCurrentPlaybackClock;
        PVMFFormatProgDownloadSupportInterface *iProgDownloadSI;
        HttpBasedProtocol *iProtocol;
        DownloadProgressInterface *iDownloadProgress;
        PVMFProtocolEngineNodeOutput *iNodeOutput;

        bool iPlaybackUnderflow;
        bool iDownloadComplete;
        bool iRequestResumeNotification;
        uint32 iCurrentNPTReadPosition;
        uint32 iClipDurationMsec;
        uint32 iPlaybackByteRate;
        uint32 iPrevDownloadSize;

        bool iDlAlgoPreConditionMet;
        bool iSetFileSize;
        bool iSendDownloadCompleteNotification;
        uint32 iClipByterate;

        PVLogger* iDataPathLogger;
};

////////////////////////////////////////////////////////////////////////////////////
//////	DownloadProgress
////////////////////////////////////////////////////////////////////////////////////
class DownloadProgress : public DownloadProgressInterface
{
    public:

        // cosntructor and destructor
        DownloadProgress();
        virtual ~DownloadProgress()
        {
            reset();
        }

        // set download progress supporting objects:
        //		PVMFFormatProgDownloadSupportInterface object,
        //		protocol engine object,
        //		config file object,			(for progressive download only)
        //		track selction container	(for fastrack download only)
        //		PVMFProtocolEngineNodeOutput object (for fasttrack download only)
        virtual void setSupportObject(OsclAny *aDLSupportObject, DownloadControlSupportObjectType aType);

        // updata download progress
        bool update(const bool aDownloadComplete = false);

        // return true for the new download progress
        bool getNewProgressPercent(uint32 &aProgressPercent);

        // return duration regardless of the difference between progressive download and fasttrack download
        void setClipDuration(const uint32 aClipDurationMsec)
        {
            iDurationMsec = aClipDurationMsec;
        }

        virtual	void setDownloadProgressMode(DownloadProgressMode aMode = DownloadProgressMode_TimeBased)
        {
            OSCL_UNUSED_ARG(aMode);
        }


    protected:
        virtual uint32 getClipDuration();
        virtual bool updateDownloadClock() = 0;
        virtual bool calculateDownloadPercent(uint32 &aDownloadProgressPercent);
        virtual void reset();

    protected:
        HttpBasedProtocol *iProtocol;
        PVMFFormatProgDownloadSupportInterface *iProgDownloadSI;
        PVMFProtocolEngineNodeOutput *iNodeOutput;

        //for progress reports
        uint32 iCurrProgressPercent;
        uint32 iPrevProgressPercent;
        uint32 iDownloadNPTTime;
        uint32 iDurationMsec;
};



////////////////////////////////////////////////////////////////////////////////////
//////	PVMFDownloadDataSourceContainer
////////////////////////////////////////////////////////////////////////////////////

// This container class wraps the data from all the download source data classes, i.e.,
// PVMFDownloadDataSourceHTTP, PVMFDownloadDataSourcePVX, PVMFSourceContextDataDownloadHTTP and PVMFSourceContextDataDownloadPVX

class CPVXInfo;
class PVMFDownloadDataSourceContainer
{
    public:
        bool iHasDataSource;									// true means the constainer is already filled in the data source
        bool iIsNewSession;										// true if the downloading a new file, false if keep downloading a partial downloading file
        uint32 iMaxFileSize;									// the max size of the file.
        uint32 iPlaybackControl;								// correspond to PVMFDownloadDataSourceHTTP::TPVPlaybackControl, PVMFSourceContextDataDownloadHTTP::TPVPlaybackControl
        OSCL_wHeapString<OsclMemAllocator> iConfigFileName;		// download config file
        OSCL_wHeapString<OsclMemAllocator> iDownloadFileName;	// local file name of the downloaded clip
        OSCL_HeapString<OsclMemAllocator>  iProxyName;			// HTTP proxy name, either ip or dns
        uint32 iProxyPort;										// HTTP proxy port
        OSCL_HeapString<OsclMemAllocator> iUserID;				// UserID string used for HTTP basic/digest authentication
        OSCL_HeapString<OsclMemAllocator> iUserPasswd;			// password string used for HTTP basic/digest authentication


        CPVXInfo *iPvxInfo;										// Fasttrack only, contains all the info in the .pvx file except the URL

    public:
        // default constructor
        PVMFDownloadDataSourceContainer()
        {
            clear();
        }

        bool isEmpty()
        {
            return !iHasDataSource;
        }

        // major copy constructor to do type conversion
        PVMFDownloadDataSourceContainer(OsclAny* aSourceData);

        // add source data
        bool addSource(OsclAny* aSourceData);

        void clear()
        {
            iHasDataSource	 = false;
            iIsNewSession	 = true;
            iMaxFileSize	 = 0;
            iPlaybackControl = 0;
            iProxyPort		 = 0;
            iPvxInfo		 = NULL;
        }

    private:
        // type conversion routine for each download source data class
        void copy(const PVMFDownloadDataSourceHTTP& aSourceData);
        void copy(const PVMFDownloadDataSourcePVX& aSourceData);
        void copy(const PVMFSourceContextDataDownloadHTTP& aSourceData);
        void copy(const PVMFSourceContextDataDownloadPVX& aSourceData);
        PVMFSourceContextDataDownloadHTTP::TPVPlaybackControl convert(const PVMFDownloadDataSourceHTTP::TPVPlaybackControl aPlaybackControl);
};



////////////////////////////////////////////////////////////////////////////////////
//////	PVDlCfgFileContainer and its derived class definition
////////////////////////////////////////////////////////////////////////////////////
class PVDlCfgFileContainer
{
    public:
        virtual ~PVDlCfgFileContainer() {}

        PVDlCfgFileContainer(PVMFDownloadDataSourceContainer *aDataSource) :
                iPlaybackMode(PVMFDownloadDataSourceHTTP::EAsap),
                iDataSource(aDataSource)
        {
            iDataPathLogger = PVLogger::GetLoggerObject("datapath.sourcenode.protocolenginenode");
        }

        virtual PVMFStatus createCfgFile(OSCL_String &aUrl);
        void setDataSource(PVMFDownloadDataSourceContainer *aDataSource)
        {
            iDataSource = aDataSource;
        }

        // get API
        OsclSharedPtr<PVDlCfgFile> &getCfgFile()
        {
            return iCfgFileObj;
        }
        PVMFDownloadDataSourceHTTP::TPVPlaybackControl getPlaybackMode()
        {
            return iPlaybackMode;
        }
        bool isEmpty()
        {
            return (iCfgFileObj.GetRep() == NULL);
        }
        virtual void saveConfig()
        {
            if (!isEmpty()) iCfgFileObj->SaveConfig();
        }

    protected:
        virtual PVMFStatus configCfgFile(OSCL_String &aUrl);
        PVMFStatus loadOldConfig(); // utility function for configCfgFile()

    protected:
        OsclSharedPtr<PVDlCfgFile> iCfgFileObj;
        PVMFDownloadDataSourceHTTP::TPVPlaybackControl iPlaybackMode;
        PVMFDownloadDataSourceContainer *iDataSource;
        PVLogger* iDataPathLogger;
};

////////////////////////////////////////////////////////////////////////////////////
//////	downloadEventReporter
////////////////////////////////////////////////////////////////////////////////////

class downloadEventReporter : public EventReporter
{
    public:
        // constructor
        downloadEventReporter(PVMFProtocolEngineNode *aNode);

        virtual bool checkReportEvent(const uint32 downloadStatus);
        bool checkContentInfoEvent(const uint32 downloadStatus);
        void clear();

        // send data ready event when download control algorithm enables
        void sendDataReadyEvent();

    protected:
        virtual bool needToCheckContentInfoEvent()
        {
            return true;
        }
        virtual void checkUnexpectedDataAndServerDisconnectEvent(const uint32 downloadStatus);

    protected:
        // supporting function for checkReportEvent()
        bool checkBufferInfoEvent(const uint32 downloadStatus);
        // check and send buffer complete, data ready and unexpected data events
        void checkBufferCompleteEvent(const uint32 downloadStatus);
        void checkUnexpectedDataEvent(const uint32 downloadStatus);
        virtual void checkServerDisconnectEvent(const uint32 downloadStatus);
        // for checkContentInfoEvent()
        bool checkContentLengthOrTooLarge();
        bool checkContentTruncated(const uint32 downloadStatus);
        int32 isDownloadFileTruncated(const uint32 downloadStatus);
        bool isDownloadComplete(const uint32 downloadStatus) const
        {
            return (downloadStatus == PROCESS_SUCCESS_END_OF_MESSAGE ||
                    downloadStatus == PROCESS_SUCCESS_END_OF_MESSAGE_TRUNCATED ||
                    downloadStatus == PROCESS_SUCCESS_END_OF_MESSAGE_WITH_EXTRA_DATA ||
                    downloadStatus == PROCESS_SUCCESS_END_OF_MESSAGE_BY_SERVER_DISCONNECT);
        }

    protected:
        bool iSendBufferStartInfoEvent;
        bool iSendBufferCompleteInfoEvent;
        bool iSendMovieAtomCompleteInfoEvent;
        bool iSendInitialDataReadyEvent;
        bool iSendContentLengthEvent;
        bool iSendContentTruncateEvent;
        bool iSendContentTypeEvent;
        bool iSendUnexpectedDataEvent;
        bool iSendServerDisconnectEvent;
};

#endif

