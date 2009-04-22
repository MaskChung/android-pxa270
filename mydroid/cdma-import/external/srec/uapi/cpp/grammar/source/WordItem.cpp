/*---------------------------------------------------------------------------*
 *  WordItem.cpp                                                             *
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


//Memory leak detection
#if defined(_DEBUG) && defined(_WIN32)
#include "crtdbg.h"
#define new new( _CLIENT_BLOCK, __FILE__, __LINE__)
#endif
#include "WordItem.h"
#include "WordItemImpl.h"

using namespace android::speech::recognition;

DEFINE_SMARTPROXY(android::speech::recognition, WordItemProxy, SlotItemProxy, WordItem)

WordItemProxy WordItem::create(const char* word, const char** pronunciations,
                               ARRAY_LIMIT pronunciationCount, ReturnCode::Type& returnCode)
{
  return impl::WordItemImpl::create(word, pronunciations, pronunciationCount, returnCode);
}

WordItemProxy WordItem::create(const char* word, const char* pronunciation,
                               ReturnCode::Type& returnCode)
{
  const char** pronunciations = new const char*[1];
  pronunciations[0] = pronunciation;
  WordItemProxy result = impl::WordItemImpl::create(word, pronunciations, 1, returnCode);
  delete [] pronunciations;
  return result;
}

WordItem::WordItem()
{}

WordItem::~WordItem()
{}

bool WordItem::isWord() const
{
  return true;
}

bool WordItem::isVoicetag() const
{
  return false;
}
