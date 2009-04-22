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
#include "pvmf_amrffparser_node.h"
#include "pvmf_amrffparser_factory.h"
#include "oscl_error_codes.h"
#include "oscl_exception.h"

// Use default DLL entry point for Symbian
#include "oscl_dll.h"

OSCL_DLL_ENTRY_POINT_DEFAULT()

OSCL_EXPORT_REF PVMFNodeInterface* PVMFAMRFFParserNodeFactory::CreatePVMFAMRFFParserNode(int32 aPriority)
{
    PVMFNodeInterface* node = NULL;
    node = OSCL_NEW(PVMFAMRFFParserNode, (aPriority));
    if (node == NULL)
    {
        OSCL_LEAVE(OsclErrNoMemory);
    }
    return node;
}

OSCL_EXPORT_REF bool PVMFAMRFFParserNodeFactory::DeletePVMFAMRFFParserNode(PVMFNodeInterface* aNode)
{
    if (aNode)
    {
        OSCL_DELETE(aNode);
        return true;
    }
    return false;
}


