/*---------------------------------------------------------------------------*
 *  MediaFileReader.cpp                                                      *
 *                                                                           *
 *  Copyright 2007, 2008 Nuance Communciations, Inc.                               *
 *                                                                           *
 *  Licensed under the Apache License, Version 2.0 (the 'License');          *
 *  you may not use this file except in compliance with the License.         *
 *                                                                           *
 *  You may obtain a copy of the License at                                  *
 *      http://www.apache.org/licenses/LICENSE-2.0                           *
 *                                                                           *
 *  Unless required by applicable law or agreed to in writing, software      *
 *  distributed under the License is distributed on an 'AS IS' BASIS,        *
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. *
 *  See the License for the specific language governing permissions and      *
 *  limitations under the License.                                           *
 *                                                                           *
 *---------------------------------------------------------------------------*/

#include "MediaFileReader.h"
#include "SmartProxy.h"

using namespace android::speech::recognition;
using namespace android::speech::recognition::utilities;


DEFINE_SMARTPROXY(android::speech::recognition, MediaFileReaderProxy, AudioSourceProxy, MediaFileReader)

MediaFileReader::MediaFileReader()
{}

MediaFileReader::~MediaFileReader()
{}
