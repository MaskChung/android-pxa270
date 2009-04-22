/*
 * Copyright (C) 2007 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package android.ddm;

import org.apache.harmony.dalvik.ddmc.Chunk;
import org.apache.harmony.dalvik.ddmc.ChunkHandler;
import org.apache.harmony.dalvik.ddmc.DdmServer;
import org.apache.harmony.dalvik.ddmc.DdmVmInternal;
import android.util.Config;
import android.util.Log;
import java.nio.ByteBuffer;

/**
 * Handle thread-related traffic.
 */
public class DdmHandleHeap extends ChunkHandler {

    public static final int CHUNK_HPIF = type("HPIF");
    public static final int CHUNK_HPSG = type("HPSG");
    public static final int CHUNK_NHSG = type("NHSG");
    public static final int CHUNK_HPGC = type("HPGC");
    public static final int CHUNK_REAE = type("REAE");
    public static final int CHUNK_REAQ = type("REAQ");
    public static final int CHUNK_REAL = type("REAL");

    private static DdmHandleHeap mInstance = new DdmHandleHeap();


    /* singleton, do not instantiate */
    private DdmHandleHeap() {}

    /**
     * Register for the messages we're interested in.
     */
    public static void register() {
        DdmServer.registerHandler(CHUNK_HPIF, mInstance);
        DdmServer.registerHandler(CHUNK_HPSG, mInstance);
        DdmServer.registerHandler(CHUNK_NHSG, mInstance);
        DdmServer.registerHandler(CHUNK_HPGC, mInstance);
        DdmServer.registerHandler(CHUNK_REAE, mInstance);
        DdmServer.registerHandler(CHUNK_REAQ, mInstance);
        DdmServer.registerHandler(CHUNK_REAL, mInstance);
    }

    /**
     * Called when the DDM server connects.  The handler is allowed to
     * send messages to the server.
     */
    public void connected() {}

    /**
     * Called when the DDM server disconnects.  Can be used to disable
     * periodic transmissions or clean up saved state.
     */
    public void disconnected() {}

    /**
     * Handle a chunk of data.
     */
    public Chunk handleChunk(Chunk request) {
        if (Config.LOGV)
            Log.v("ddm-heap", "Handling " + name(request.type) + " chunk");
        int type = request.type;

        if (type == CHUNK_HPIF) {
            return handleHPIF(request);
        } else if (type == CHUNK_HPSG) {
            return handleHPSGNHSG(request, false);
        } else if (type == CHUNK_NHSG) {
            return handleHPSGNHSG(request, true);
        } else if (type == CHUNK_HPGC) {
            return handleHPGC(request);
        } else if (type == CHUNK_REAE) {
            return handleREAE(request);
        } else if (type == CHUNK_REAQ) {
            return handleREAQ(request);
        } else if (type == CHUNK_REAL) {
            return handleREAL(request);
        } else {
            throw new RuntimeException("Unknown packet "
                + ChunkHandler.name(type));
        }
    }

    /*
     * Handle a "HeaP InFo request".
     */
    private Chunk handleHPIF(Chunk request) {
        ByteBuffer in = wrapChunk(request);

        int when = in.get();
        if (Config.LOGV)
            Log.v("ddm-heap", "Heap segment enable: when=" + when);

        boolean ok = DdmVmInternal.heapInfoNotify(when);
        if (!ok) {
            return createFailChunk(1, "Unsupported HPIF what");
        } else {
            return null;        // empty response
        }
    }

    /*
     * Handle a "HeaP SeGment" or "Native Heap SeGment" request.
     */
    private Chunk handleHPSGNHSG(Chunk request, boolean isNative) {
        ByteBuffer in = wrapChunk(request);

        int when = in.get();
        int what = in.get();
        if (Config.LOGV)
            Log.v("ddm-heap", "Heap segment enable: when=" + when
                + ", what=" + what + ", isNative=" + isNative);

        boolean ok = DdmVmInternal.heapSegmentNotify(when, what, isNative);
        if (!ok) {
            return createFailChunk(1, "Unsupported HPSG what/when");
        } else {
            // TODO: if "when" is non-zero and we want to see a dump
            //       right away, initiate a GC.
            return null;        // empty response
        }
    }

    /*
     * Handle a "HeaP Garbage Collection" request.
     */
    private Chunk handleHPGC(Chunk request) {
        //ByteBuffer in = wrapChunk(request);

        if (Config.LOGD)
            Log.d("ddm-heap", "Heap GC request");
        System.gc();

        return null;        // empty response
    }

    /*
     * Handle a "REcent Allocation Enable" request.
     */
    private Chunk handleREAE(Chunk request) {
        ByteBuffer in = wrapChunk(request);
        boolean enable;

        enable = (in.get() != 0);

        if (Config.LOGD)
            Log.d("ddm-heap", "Recent allocation enable request: " + enable);

        DdmVmInternal.enableRecentAllocations(enable);

        return null;        // empty response
    }

    /*
     * Handle a "REcent Allocation Query" request.
     */
    private Chunk handleREAQ(Chunk request) {
        //ByteBuffer in = wrapChunk(request);

        byte[] reply = new byte[1];
        reply[0] = DdmVmInternal.getRecentAllocationStatus() ? (byte)1 :(byte)0;
        return new Chunk(CHUNK_REAQ, reply, 0, reply.length);
    }

    /*
     * Handle a "REcent ALlocations" request.
     */
    private Chunk handleREAL(Chunk request) {
        //ByteBuffer in = wrapChunk(request);

        if (Config.LOGD)
            Log.d("ddm-heap", "Recent allocations request");

        /* generate the reply in a ready-to-go format */
        byte[] reply = DdmVmInternal.getRecentAllocations();
        return new Chunk(CHUNK_REAL, reply, 0, reply.length);
    }
}

