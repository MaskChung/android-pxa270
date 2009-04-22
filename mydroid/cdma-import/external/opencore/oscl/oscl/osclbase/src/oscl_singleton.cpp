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


#include "oscl_base.h"
#if (OSCL_HAS_SINGLETON_SUPPORT)

#include "oscl_singleton.h"
#include "oscl_assert.h"
#include "oscl_lock_base.h"
#include "oscl_base_alloc.h"


OsclSingletonRegistry::SingletonTable* OsclSingletonRegistry::iSingletonTable = NULL;


OSCL_EXPORT_REF void OsclSingletonRegistry::initialize(Oscl_DefAlloc &alloc, int32 &aError)
{
    aError = 0;
    //Allocate the registry on the first init call
    //Note: there's some chance of thread contention here, since
    //thread lock isn't available until after this step.
    if (!iSingletonTable)
    {
        OsclAny* table = alloc.allocate(sizeof(SingletonTable));
        if (table)
            iSingletonTable = new(table) SingletonTable();
        else
        {
            aError = EPVErrorBaseOutOfMemory;
            return;
        }
    }

    //increment the ref count on each init.
    iSingletonTable->iLock.Lock();
    iSingletonTable->iRefCount++;
    iSingletonTable->iLock.Unlock();
}

OSCL_EXPORT_REF void OsclSingletonRegistry::cleanup(Oscl_DefAlloc &alloc, int32 &aError)
{
    aError = 0;
    if (!iSingletonTable)
    {
        aError = EPVErrorBaseNotInstalled;//no table!
        return;
    }

    //decrement the ref count and cleanup when it reaches zero.
    iSingletonTable->iLock.Lock();
    iSingletonTable->iRefCount--;
    if (iSingletonTable->iRefCount == 0)
    {
        //cleanup
        iSingletonTable->iLock.Unlock();
        iSingletonTable->~SingletonTable();
        alloc.deallocate(iSingletonTable);
        iSingletonTable = NULL;
    }
    else
    {
        iSingletonTable->iLock.Unlock();
    }
}

OSCL_EXPORT_REF OsclAny* OsclSingletonRegistry::getInstance(uint32 ID, int32 &aError)
{
    OSCL_ASSERT(ID < OSCL_SINGLETON_ID_LAST);

    aError = 0;
    if (!iSingletonTable)
    {
        aError = EPVErrorBaseNotInstalled;//no table!
        return NULL;
    }

    iSingletonTable->iLock.Lock();
    OsclAny* value = iSingletonTable->iSingletons[ID];
    iSingletonTable->iLock.Unlock();

    return value;
}

OSCL_EXPORT_REF void OsclSingletonRegistry::registerInstance(OsclAny* ptr, uint32 ID, int32 &aError)
{
    OSCL_ASSERT(ID < OSCL_SINGLETON_ID_LAST);

    aError = 0;
    if (!iSingletonTable)
    {
        aError = EPVErrorBaseNotInstalled;//no table!
        return;
    }

    iSingletonTable->iLock.Lock();
    iSingletonTable->iSingletons[ID] = ptr;
    iSingletonTable->iLock.Unlock();
}

#endif //OSCL_HAS_SINGLETON_SUPPORT

