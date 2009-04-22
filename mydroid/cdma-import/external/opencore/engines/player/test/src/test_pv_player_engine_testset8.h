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
#ifndef TEST_PV_PLAYER_ENGINE_TESTSET8_H_INCLUDED
#define TEST_PV_PLAYER_ENGINE_TESTSET8_H_INCLUDED

/**
 *  @file test_pv_player_engine_testset8.h
 *  @brief This file contains the class definitions for the eighth set of
 *         test cases for PVPlayerEngine which are not fully automated (requires human verification)
 *
 */

#ifndef TEST_PV_PLAYER_ENGINE_H_INCLUDED
#include "test_pv_player_engine.h"
#endif

#ifndef PV_PLAYER_DATASOURCEURL_H_INCLUDED
#include "pv_player_datasourceurl.h"
#endif

#ifndef PVMF_NODE_INTERFACE_H_INCLUDED
#include "pvmf_node_interface.h"
#endif

#ifndef PV_ENGINE_TYPES_H_INCLUDED
#include "pv_engine_types.h"
#endif

#ifndef TEST_PV_PLAYER_ENGINE_CONFIG_H_INCLUDED
#include "test_pv_player_engine_config.h"
#endif

#ifndef OSCL_FILE_IO_H_INCLUDED
#include "oscl_file_io.h"
#endif

#ifndef PVMI_CONFIG_AND_CAPABILITY_OBSERVER_H_INCLUDED
#include "pvmi_config_and_capability_observer.h"
#endif

class PVPlayerDataSink;
class PVPlayerDataSinkFilename;
class PvmfFileOutputNodeConfigInterface;
class PvmiCapabilityAndConfig;


/*!
 *  A test case to query and print out metadata from specified source file using the player engine
 *  - Data Source: Specified by user of test case
 *  - Data Sink(s): Video[File Output MediaIO Interface Node-test_player_printmetadata_video.dat]\n
 *                  Audio[File Output MediaIO Interface Node-test_player_printmetadata_audio.dat]\n
 *                  Text[File Output MediaIO Interface Node-test_player_printmetadata_text.dat]
 *  - Sequence:
 *             -# CreatePlayer()
 *             -# AddDataSource()
 *             -# Init()
 *             -# GetMetadataKeys()
 *             -# GetMetadataValues()
 *             -# Print out the metadata list
 *             -# AddDataSink() (video)
 *             -# AddDataSink() (audio)
 *             -# AddDataSink() (text)
 *             -# Prepare()
 *             -# Start()
 *             -# WAIT 2 sec
 *             -# GetMetadataKeys()
 *             -# GetMetadataValues()
 *             -# Print out the metadata list
 *             -# Stop()
 *             -# GetMetadataKeys()
 *             -# GetMetadataValues()
 *             -# Print out the metadata list
 *             -# RemoveDataSink() (video)
 *             -# RemoveDataSink() (audio)
 *             -# RemoveDataSink() (text)
 *             -# Reset()
 *             -# RemoveDataSource()
 *             -# DeletePlayer()
 *
 */
class pvplayer_async_test_printmetadata : public pvplayer_async_test_base
{
    public:
        pvplayer_async_test_printmetadata(PVPlayerAsyncTestParam aTestParam):
                pvplayer_async_test_base(aTestParam)
                , iPlayer(NULL)
                , iDataSource(NULL)
                , iDataSinkVideo(NULL)
                , iDataSinkAudio(NULL)
                , iDataSinkText(NULL)
                , iIONodeVideo(NULL)
                , iIONodeAudio(NULL)
                , iIONodeText(NULL)
                , iMIOFileOutVideo(NULL)
                , iMIOFileOutAudio(NULL)
                , iMIOFileOutText(NULL)
                , iCurrentCmdId(0)
        {
            iTestCaseName = _STRLIT_CHAR("Print Metadata");
        }

        ~pvplayer_async_test_printmetadata() {}

        void StartTest();
        void Run();

        void CommandCompleted(const PVCmdResponse& aResponse);
        void HandleErrorEvent(const PVAsyncErrorEvent& aEvent);
        void HandleInformationalEvent(const PVAsyncInformationalEvent& aEvent);

        void PrintMetadataInfo();

        enum PVTestState
        {
            STATE_CREATE,
            STATE_ADDDATASOURCE,
            STATE_INIT,
            STATE_GETMETADATAKEYLIST1,
            STATE_GETMETADATAVALUELIST1,
            STATE_ADDDATASINK_VIDEO,
            STATE_ADDDATASINK_AUDIO,
            STATE_ADDDATASINK_TEXT,
            STATE_PREPARE,
            STATE_START,
            STATE_GETMETADATAKEYLIST2,
            STATE_GETMETADATAVALUELIST2,
            STATE_STOP,
            STATE_GETMETADATAKEYLIST3,
            STATE_GETMETADATAVALUELIST3,
            STATE_REMOVEDATASINK_VIDEO,
            STATE_REMOVEDATASINK_AUDIO,
            STATE_REMOVEDATASINK_TEXT,
            STATE_RESET,
            STATE_REMOVEDATASOURCE,
            STATE_WAIT_FOR_ERROR_HANDLING,
            STATE_CLEANUPANDCOMPLETE
        };

        PVTestState iState;

        PVPlayerInterface* iPlayer;
        PVPlayerDataSourceURL* iDataSource;
        PVPlayerDataSink* iDataSinkVideo;
        PVPlayerDataSink* iDataSinkAudio;
        PVPlayerDataSink* iDataSinkText;
        PVMFNodeInterface* iIONodeVideo;
        PVMFNodeInterface* iIONodeAudio;
        PVMFNodeInterface* iIONodeText;
        PvmiMIOControl* iMIOFileOutVideo;
        PvmiMIOControl* iMIOFileOutAudio;
        PvmiMIOControl* iMIOFileOutText;
        PVCommandId iCurrentCmdId;

        OSCL_wHeapString<OsclMemAllocator> iFileNameWStr;
        oscl_wchar iTmpWCharBuffer[512];

        PVPMetadataList iMetadataKeyList;
        Oscl_Vector<PvmiKvp, OsclMemAllocator> iMetadataValueList;
        int32 iNumValues;
};


/*!
 *  A test case to periodically print out memory usage while playing a specified source file using the player engine
 *  - Data Source: Specified by user of test case
 *  - Data Sink(s): Video[File Output MediaIO Interface Node-test_player_printmemstats_video.dat]\n
 *                  Audio[File Output MediaIO Interface Node-test_player_printmemstats_audio.dat]\n
 *                  Text[File Output MediaIO Interface Node-test_player_printmemstats_text.dat]
 *  - Sequence:
 *             -# CreatePlayer()
 *             -# AddDataSource()
 *             -# Init()
 *             -# GetMetadataKeys()
 *             -# GetMetadataValues()
 *             -# AddDataSink() (video)
 *             -# AddDataSink() (audio)
 *             -# AddDataSink() (text)
 *             -# Prepare()
 *             -# Start()
 *             -# WAIT 20 sec
 *             -# Stop()
 *             -# RemoveDataSink() (video)
 *             -# RemoveDataSink() (audio)
 *             -# RemoveDataSink() (text)
 *             -# Reset()
 *             -# RemoveDataSource()
 *             -# DeletePlayer()
 *
 */
class pvplayer_async_test_printmemstats : public pvplayer_async_test_base
{
    public:
        pvplayer_async_test_printmemstats(PVPlayerAsyncTestParam aTestParam):
                pvplayer_async_test_base(aTestParam)
                , iPlayer(NULL)
                , iDataSource(NULL)
                , iDataSinkVideo(NULL)
                , iDataSinkAudio(NULL)
                , iDataSinkText(NULL)
                , iIONodeVideo(NULL)
                , iIONodeAudio(NULL)
                , iIONodeText(NULL)
                , iMIOFileOutVideo(NULL)
                , iMIOFileOutAudio(NULL)
                , iMIOFileOutText(NULL)
                , iCurrentCmdId(0)
                , iPlayTimeCtr(0)
                , iInitialNumBytes(0)
                , iInitialNumAllocs(0)
        {
            iTestCaseName = _STRLIT_CHAR("Print Memory Statistics");
        }

        ~pvplayer_async_test_printmemstats() {}

        void StartTest();
        void Run();

        void CommandCompleted(const PVCmdResponse& aResponse);
        void HandleErrorEvent(const PVAsyncErrorEvent& aEvent);
        void HandleInformationalEvent(const PVAsyncInformationalEvent& aEvent);

        void PrintMemStats();

        enum PVTestState
        {
            STATE_CREATE,
            STATE_ADDDATASOURCE,
            STATE_INIT,
            STATE_GETMETADATAKEYLIST,
            STATE_GETMETADATAVALUELIST,
            STATE_ADDDATASINK_VIDEO,
            STATE_ADDDATASINK_AUDIO,
            STATE_ADDDATASINK_TEXT,
            STATE_PREPARE,
            STATE_START,
            STATE_PRINTMEMSTATS,
            STATE_STOP,
            STATE_REMOVEDATASINK_VIDEO,
            STATE_REMOVEDATASINK_AUDIO,
            STATE_REMOVEDATASINK_TEXT,
            STATE_RESET,
            STATE_REMOVEDATASOURCE,
            STATE_WAIT_FOR_ERROR_HANDLING,
            STATE_CLEANUPANDCOMPLETE
        };

        PVTestState iState;

        PVPlayerInterface* iPlayer;
        PVPlayerDataSourceURL* iDataSource;
        PVPlayerDataSink* iDataSinkVideo;
        PVPlayerDataSink* iDataSinkAudio;
        PVPlayerDataSink* iDataSinkText;
        PVMFNodeInterface* iIONodeVideo;
        PVMFNodeInterface* iIONodeAudio;
        PVMFNodeInterface* iIONodeText;
        PvmiMIOControl* iMIOFileOutVideo;
        PvmiMIOControl* iMIOFileOutAudio;
        PvmiMIOControl* iMIOFileOutText;
        PVCommandId iCurrentCmdId;

        OSCL_wHeapString<OsclMemAllocator> iFileNameWStr;
        oscl_wchar iTmpWCharBuffer[512];

        PVPMetadataList iMetadataKeyList;
        Oscl_Vector<PvmiKvp, OsclMemAllocator> iMetadataValueList;
        int32 iNumValues;

        uint32 iPlayTimeCtr;

        uint32 iInitialNumBytes;
        uint32 iInitialNumAllocs;
};


/*!
 *  A test case to test playback of specified source with file output media IO node till EOS. Prints out the playback position
 *  player engine sends playback status events
 *  - Data Source: Specified by user of test case
 *  - Data Sink(s): Video[File Output MediaIO Interface Node-test_player_playuntileos_%SOURCEFILENAME%_video.dat]\n
 *                  Audio[File Output MediaIO Interface Node-test_player_playuntileos_%SOURCEFILENAME%_audio.dat]\n
 *                  Text[File Output MediaIO Interface Node-test_player_playuntileos_%SOURCEFILENAME%_text.dat]
 *  - Sequence:
 *             -# CreatePlayer()
 *             -# AddDataSource()
 *             -# Init()
 *             -# AddDataSink() (video)
 *             -# AddDataSink() (audio)
 *             -# AddDataSink() (text)
 *             -# Prepare()
 *             -# Start()
 *             -# WAIT FOR EOS EVENT
 *             -# Stop()
 *             -# RemoveDataSink() (video)
 *             -# RemoveDataSink() (audio)
 *             -# RemoveDataSink() (text)
 *             -# Reset()
 *             -# RemoveDataSource()
 *             -# DeletePlayer()
 *
 */
class pvplayer_async_test_playuntileos : public pvplayer_async_test_base
{
    public:
        pvplayer_async_test_playuntileos(PVPlayerAsyncTestParam aTestParam):
                pvplayer_async_test_base(aTestParam)
                , iPlayer(NULL)
                , iDataSource(NULL)
                , iDataSinkVideo(NULL)
                , iDataSinkAudio(NULL)
                , iDataSinkText(NULL)
                , iIONodeVideo(NULL)
                , iIONodeAudio(NULL)
                , iIONodeText(NULL)
                , iMIOFileOutVideo(NULL)
                , iMIOFileOutAudio(NULL)
                , iMIOFileOutText(NULL)
                , iCurrentCmdId(0)
        {
            iTestCaseName = _STRLIT_CHAR("Play Until EOS");
        }

        ~pvplayer_async_test_playuntileos() {}

        void StartTest();
        void Run();

        void CommandCompleted(const PVCmdResponse& aResponse);
        void HandleErrorEvent(const PVAsyncErrorEvent& aEvent);
        void HandleInformationalEvent(const PVAsyncInformationalEvent& aEvent);

        enum PVTestState
        {
            STATE_CREATE,
            STATE_ADDDATASOURCE,
            STATE_INIT,
            STATE_ADDDATASINK_VIDEO,
            STATE_ADDDATASINK_AUDIO,
            STATE_ADDDATASINK_TEXT,
            STATE_PREPARE,
            STATE_START,
            STATE_EOSNOTREACHED,
            STATE_STOP,
            STATE_REMOVEDATASINK_VIDEO,
            STATE_REMOVEDATASINK_AUDIO,
            STATE_REMOVEDATASINK_TEXT,
            STATE_RESET,
            STATE_REMOVEDATASOURCE,
            STATE_WAIT_FOR_ERROR_HANDLING,
            STATE_CLEANUPANDCOMPLETE
        };

        PVTestState iState;

        PVPlayerInterface* iPlayer;
        PVPlayerDataSourceURL* iDataSource;
        PVPlayerDataSink* iDataSinkVideo;
        PVPlayerDataSink* iDataSinkAudio;
        PVPlayerDataSink* iDataSinkText;
        PVMFNodeInterface* iIONodeVideo;
        PVMFNodeInterface* iIONodeAudio;
        PVMFNodeInterface* iIONodeText;
        PvmiMIOControl* iMIOFileOutVideo;
        PvmiMIOControl* iMIOFileOutAudio;
        PvmiMIOControl* iMIOFileOutText;
        PVCommandId iCurrentCmdId;

        OSCL_wHeapString<OsclMemAllocator> iFileNameWStr;
        oscl_wchar iTmpWCharBuffer[512];
};

#endif


