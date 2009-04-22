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
#include "pvmf_node_shared_lib_interface.h"

#include "pvmf_downloadmanager_factory.h"

#include "osclconfig.h"

class DownloadNodesInterface: public OsclSharedLibraryInterface,
            public NodeSharedLibraryInterface
{
    public:
        // From NodeSharedLibraryInterface
        OsclAny* QueryNodeInterface(const PVUuid& aNodeUuid, const OsclUuid& aInterfaceId)
        {
            if (KPVMFDownloadManagerNodeUuid == aNodeUuid)
            {
                if (PV_CREATE_NODE_INTERFACE == aInterfaceId)
                {
                    return ((OsclAny*)(PVMFDownloadManagerNodeFactory::CreatePVMFDownloadManagerNode));
                }
                else if (PV_RELEASE_NODE_INTERFACE == aInterfaceId)
                {
                    return ((OsclAny*)(PVMFDownloadManagerNodeFactory::DeletePVMFDownloadManagerNode));
                }
            }

            return NULL;
        };

        // From OsclSharedLibraryInterface
        OsclAny* SharedLibraryLookup(const OsclUuid& aInterfaceId)
        {
            if (aInterfaceId == PV_NODE_INTERFACE)
            {
                return OSCL_STATIC_CAST(NodeSharedLibraryInterface*, this);
            }
            return NULL;
        };

        static DownloadNodesInterface* Instance()
        {
            static DownloadNodesInterface nodeInterface;
            return &nodeInterface;
        };

    private:

        DownloadNodesInterface() {};

};


extern "C"
{
    OsclAny *GetInterface(void)
    {
        return DownloadNodesInterface::Instance();
    }
}

