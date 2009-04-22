/* libs/graphics/images/SkStream.cpp
**
** Copyright 2006, The Android Open Source Project
**
** Licensed under the Apache License, Version 2.0 (the "License"); 
** you may not use this file except in compliance with the License. 
** You may obtain a copy of the License at 
**
**     http://www.apache.org/licenses/LICENSE-2.0 
**
** Unless required by applicable law or agreed to in writing, software 
** distributed under the License is distributed on an "AS IS" BASIS, 
** WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. 
** See the License for the specific language governing permissions and 
** limitations under the License.
*/

#include "SkStream.h"
#include "SkFixed.h"
#include "SkString.h"
#include "SkOSFile.h"

#include <unistd.h>

SkStream::~SkStream() {}

const char* SkStream::getFileName()
{
    // override in subclass if you represent a file
    return NULL;
}

const void* SkStream::getMemoryBase()
{
    // override in subclass if you represent a memory block
    return NULL;
}

size_t SkStream::skip(size_t size)
{
    /*  Check for size == 0, and just return 0. If we passed that
        to read(), it would interpret it as a request for the entire
        size of the stream.
    */
    return size ? this->read(NULL, size) : 0;
}

int8_t SkStream::readS8() {
    int8_t value;
    size_t len = this->read(&value, 1);
    SkASSERT(1 == len);
    return value;
}

int16_t SkStream::readS16() {
    int16_t value;
    size_t len = this->read(&value, 2);
    SkASSERT(2 == len);
    return value;
}

int32_t SkStream::readS32() {
    int32_t value;
    size_t len = this->read(&value, 4);
    SkASSERT(4 == len);
    return value;
}

SkScalar SkStream::readScalar() {
    SkScalar value;
    size_t len = this->read(&value, sizeof(SkScalar));
    SkASSERT(sizeof(SkScalar) == len);
    return value;
}

size_t SkStream::readPackedUInt() {
    uint8_t byte;    
    if (!this->read(&byte, 1)) {
        return 0;
    }
    if (byte != 0xFF) {
        return byte;
    }
    
    uint16_t word;
    if (!this->read(&word, 2)) {
        return 0;
    }
    if (word != 0xFFFF) {
        return word;
    }
    
    uint32_t quad;
    if (!this->read(&quad, 4)) {
        return 0;
    }
    return quad;
}

//////////////////////////////////////////////////////////////////////////////////////

SkWStream::~SkWStream()
{
}

void SkWStream::newline()
{
    this->write("\n", 1);
}

void SkWStream::flush()
{
}

bool SkWStream::writeText(const char text[])
{
    SkASSERT(text);
    return this->write(text, strlen(text));
}

bool SkWStream::writeDecAsText(int32_t dec)
{
    SkString    tmp;
    tmp.appendS32(dec);
    return this->write(tmp.c_str(), tmp.size());
}

bool SkWStream::writeHexAsText(uint32_t hex, int digits)
{
    SkString    tmp;
    tmp.appendHex(hex, digits);
    return this->write(tmp.c_str(), tmp.size());
}

bool SkWStream::writeScalarAsText(SkScalar value)
{
    SkString    tmp;
    tmp.appendScalar(value);
    return this->write(tmp.c_str(), tmp.size());
}

bool SkWStream::write8(U8CPU value) {
    uint8_t v = SkToU8(value);
    return this->write(&v, 1);
}

bool SkWStream::write16(U16CPU value) {
    uint16_t v = SkToU16(value);
    return this->write(&v, 2);
}

bool SkWStream::write32(uint32_t value) {
    return this->write(&value, 4);
}

bool SkWStream::writeScalar(SkScalar value) {
    return this->write(&value, sizeof(value));
}

bool SkWStream::writePackedUInt(size_t value) {
    if (value < 0xFF) {
        return this->write8(value);
    } else if (value < 0xFFFF) {
        return this->write8(0xFF) && this->write16(value);
    } else {
        return this->write16(0xFFFF) && this->write32(value);
    }
}

bool SkWStream::writeStream(SkStream* stream, size_t length) {
    char scratch[1024];
    const size_t MAX = sizeof(scratch);
    
    while (length != 0) {
        size_t n = length;
        if (n > MAX) {
            n = MAX;
        }
        stream->read(scratch, n);
        if (!this->write(scratch, n)) {
            return false;
        }
        length -= n;
    }
    return true;
}

////////////////////////////////////////////////////////////////////////////

SkFILEStream::SkFILEStream(const char file[]) : fName(file)
{
#ifdef SK_BUILD_FOR_BREW
    if (SkStrEndsWith(fName.c_str(), ".xml"))
        fName.writable_str()[fName.size()-3] = 'b';
#endif

    fFILE = file ? sk_fopen(fName.c_str(), kRead_SkFILE_Flag) : NULL;
}

SkFILEStream::~SkFILEStream()
{
    if (fFILE)
        sk_fclose(fFILE);
}

void SkFILEStream::setPath(const char path[])
{
    fName.set(path);
#ifdef SK_BUILD_FOR_BREW
    if (SkStrEndsWith(fName.c_str(), ".xml"))
        fName.writable_str()[fName.size()-3] = 'b';
#endif

    if (fFILE)
    {
        sk_fclose(fFILE);
        fFILE = NULL;
    }
    if (path)
        fFILE = sk_fopen(fName.c_str(), kRead_SkFILE_Flag);
}

const char* SkFILEStream::getFileName()
{
    return fName.c_str();
}

bool SkFILEStream::rewind()
{
    if (fFILE)
    {
        if (sk_frewind(fFILE))
            return true;
        // we hit an error
        sk_fclose(fFILE);
        fFILE = NULL;
    }
    return false;
}

size_t SkFILEStream::read(void* buffer, size_t size)
{
    if (fFILE)
    {
        if (buffer == NULL && size == 0)    // special signature, they want the total size
            return sk_fgetsize(fFILE);
        else
            return sk_fread(buffer, size, fFILE);
    }
    return 0;
}

////////////////////////////////////////////////////////////////////////////

//#define TRACE_FDSTREAM

SkFDStream::SkFDStream(int fileDesc, bool closeWhenDone)
    : fFD(fileDesc), fCloseWhenDone(closeWhenDone) {
}

SkFDStream::~SkFDStream() {
    if (fFD >= 0 && fCloseWhenDone) {
        ::close(fFD);
    }
}

bool SkFDStream::rewind() {
    if (fFD >= 0) {
        off_t value = ::lseek(fFD, 0, SEEK_SET);
#ifdef TRACE_FDSTREAM
        if (value) {
            SkDebugf("xxxxxxxxxxxxxx rewind failed %d\n", value);
        }
#endif
        return value == 0;
    }
    return false;
}

size_t SkFDStream::read(void* buffer, size_t size) {
    if (fFD >= 0) {
        if (buffer == NULL && size == 0) {  // request total size
            off_t curr = ::lseek(fFD, 0, SEEK_CUR);
            if (curr < 0) {
#ifdef TRACE_FDSTREAM
                SkDebugf("xxxxxxxxxxxxx lseek failed 0 CURR\n");
#endif
                return 0;   // error
            }
            off_t size = ::lseek(fFD, 0, SEEK_END);
            if (size < 0) {
#ifdef TRACE_FDSTREAM
                SkDebugf("xxxxxxxxxxxxx lseek failed 0 END\n");
#endif
                size = 0;   // error
            }
            if (::lseek(fFD, curr, SEEK_SET) != curr) {
                // can't restore, error
#ifdef TRACE_FDSTREAM
                SkDebugf("xxxxxxxxxxxxx lseek failed %d SET\n", curr);
#endif
                return 0;
            }
            return size;
        } else if (NULL == buffer) {        // skip
            off_t oldCurr = ::lseek(fFD, 0, SEEK_CUR);
            if (oldCurr < 0) {
#ifdef TRACE_FDSTREAM
                SkDebugf("xxxxxxxxxxxxx lseek1 failed %d CUR\n", oldCurr);
#endif
                return 0;   // error;
            }
            off_t newCurr = ::lseek(fFD, size, SEEK_CUR);
            if (newCurr < 0) {
#ifdef TRACE_FDSTREAM
                SkDebugf("xxxxxxxxxxxxx lseek2 failed %d CUR\n", newCurr);
#endif
                return 0;   // error;
            }
            // return the actual amount we skipped
            return newCurr - oldCurr;
        } else {                            // read
            ssize_t actual = ::read(fFD, buffer, size);
            // our API can't return an error, so we return 0
            if (actual < 0) {
#ifdef TRACE_FDSTREAM
                SkDebugf("xxxxxxxxxxxxx read failed %d actual %d\n", size, actual);
#endif
                actual = 0;
            }
            return actual;
        }
    }
    return 0;
}

////////////////////////////////////////////////////////////////////////

SkMemoryStream::SkMemoryStream()
{
    fWeOwnTheData = false;
    this->setMemory(NULL, 0);
}

SkMemoryStream::SkMemoryStream(size_t size) {
    fWeOwnTheData = true;
    fOffset = 0;
    fSize = size;
    fSrc = sk_malloc_throw(size);
}

SkMemoryStream::SkMemoryStream(const void* src, size_t size, bool copyData)
{
    fWeOwnTheData = false;
    this->setMemory(src, size, copyData);
}

SkMemoryStream::~SkMemoryStream()
{
    if (fWeOwnTheData)
        sk_free((void*)fSrc);
}

void SkMemoryStream::setMemory(const void* src, size_t size, bool copyData)
{
    if (fWeOwnTheData)
        sk_free((void*)fSrc);

    fSize = size;
    fOffset = 0;
    fWeOwnTheData = copyData;

    if (copyData)
    {
        void* copy = sk_malloc_throw(size);
        memcpy(copy, src, size);
        src = copy;
    }
    fSrc = src;
}

void SkMemoryStream::skipToAlign4()
{
    // cast to remove unary-minus warning
    fOffset += -(int)fOffset & 0x03;
}

bool SkMemoryStream::rewind()
{
    fOffset = 0;
    return true;
}

size_t SkMemoryStream::read(void* buffer, size_t size)
{
    if (buffer == NULL && size == 0)    // special signature, they want the total size
        return fSize;

    // if buffer is NULL, seek ahead by size

    if (size == 0)
        return 0;
    if (size > fSize - fOffset)
        size = fSize - fOffset;
    if (buffer) {
        memcpy(buffer, (const char*)fSrc + fOffset, size);
    }
    fOffset += size;
    return size;
}

const void* SkMemoryStream::getMemoryBase()
{
    return fSrc;
}

const void* SkMemoryStream::getAtPos()
{
    return (const char*)fSrc + fOffset;
}

size_t SkMemoryStream::seek(size_t offset)
{
    if (offset > fSize)
        offset = fSize;
    fOffset = offset;
    return offset;
}

/////////////////////////////////////////////////////////////////////////////////////////////////////////

SkBufferStream::SkBufferStream(SkStream* proxy, size_t bufferSize)
    : fProxy(proxy)
{
    SkASSERT(proxy != NULL);
    proxy->ref();
    this->init(NULL, bufferSize);
}

SkBufferStream::SkBufferStream(SkStream* proxy, void* buffer, size_t bufferSize)
    : fProxy(proxy)
{
    SkASSERT(proxy != NULL);
    SkASSERT(buffer == NULL || bufferSize != 0);    // init(addr, 0) makes no sense, we must know how big their buffer is
    proxy->ref();
    this->init(buffer, bufferSize);
}

void SkBufferStream::init(void* buffer, size_t bufferSize)
{
    if (bufferSize == 0)
        bufferSize = kDefaultBufferSize;

    fOrigBufferSize = bufferSize;
    fBufferSize = bufferSize;
    fBufferOffset = bufferSize; // to trigger a reload on the first read()

    if (buffer == NULL)
    {
        fBuffer = (char*)sk_malloc_throw(fBufferSize);
        fWeOwnTheBuffer = true;
    }
    else
    {
        fBuffer = (char*)buffer;
        fWeOwnTheBuffer = false;
    }
}

SkBufferStream::~SkBufferStream()
{
    fProxy->unref();
    if (fWeOwnTheBuffer)
        sk_free(fBuffer);
}

bool SkBufferStream::rewind()
{
    fBufferOffset = fBufferSize = fOrigBufferSize;
    return fProxy->rewind();
}

const char* SkBufferStream::getFileName()
{
    return fProxy->getFileName();
}

#ifdef SK_DEBUG
//  #define SK_TRACE_BUFFERSTREAM
#endif

size_t SkBufferStream::read(void* buffer, size_t size)
{
#ifdef SK_TRACE_BUFFERSTREAM
    SkDebugf("Request %d", size);
#endif

    if (buffer == NULL && size == 0)
        return fProxy->read(buffer, size);    // requesting total size

    if (buffer == NULL || size == 0)
    {
        fBufferOffset += size;
        return fProxy->read(buffer, size);
    }

    size_t s = size;
    size_t actuallyRead = 0;

    // flush what we can from our fBuffer
    if (fBufferOffset < fBufferSize)
    {
        if (s > fBufferSize - fBufferOffset)
            s = fBufferSize - fBufferOffset;
        memcpy(buffer, fBuffer + fBufferOffset, s);
#ifdef SK_TRACE_BUFFERSTREAM
        SkDebugf(" flush %d", s);
#endif
        size -= s;
        fBufferOffset += s;
        buffer = (char*)buffer + s;
        actuallyRead = s;
    }

    // check if there is more to read
    if (size)
    {
        SkASSERT(fBufferOffset >= fBufferSize); // need to refill our fBuffer

        if (size < fBufferSize) // lets try to read more than the request
        {
            s = fProxy->read(fBuffer, fBufferSize);
#ifdef SK_TRACE_BUFFERSTREAM
            SkDebugf(" read %d into fBuffer", s);
#endif
            if (size > s)   // they asked for too much
                size = s;
            if (size)
            {
                memcpy(buffer, fBuffer, size);
                actuallyRead += size;
#ifdef SK_TRACE_BUFFERSTREAM
                SkDebugf(" memcpy %d into dst", size);
#endif
            }

            fBufferOffset = size;
            fBufferSize = s;        // record the (possibly smaller) size for the buffer
        }
        else    // just do a direct read
        {
            actuallyRead += fProxy->read(buffer, size);
#ifdef SK_TRACE_BUFFERSTREAM
            SkDebugf(" direct read %d", size);
#endif
        }
    }
#ifdef SK_TRACE_BUFFERSTREAM
    SkDebugf("\n");
#endif
    return actuallyRead;
}

const void* SkBufferStream::getMemoryBase()
{
    return fProxy->getMemoryBase();
}

/////////////////////////////////////////////////////////////////////////////////////////////////////////
/////////////////////////////////////////////////////////////////////////////////////////////////////////

SkFILEWStream::SkFILEWStream(const char path[])
{
    fFILE = sk_fopen(path, kWrite_SkFILE_Flag);
}

SkFILEWStream::~SkFILEWStream()
{
    if (fFILE)
        sk_fclose(fFILE);
}

bool SkFILEWStream::write(const void* buffer, size_t size)
{
    if (fFILE == NULL)
        return false;

    if (sk_fwrite(buffer, size, fFILE) != size)
    {
        SkDEBUGCODE(SkDebugf("SkFILEWStream failed writing %d bytes\n", size);)
        sk_fclose(fFILE);
        fFILE = NULL;
        return false;
    }
    return true;
}

void SkFILEWStream::flush()
{
    if (fFILE)
        sk_fflush(fFILE);
}

////////////////////////////////////////////////////////////////////////

SkMemoryWStream::SkMemoryWStream(void* buffer, size_t size)
    : fBuffer((char*)buffer), fMaxLength(size), fBytesWritten(0)
{
}

bool SkMemoryWStream::write(const void* buffer, size_t size)
{
    size = SkMin32(size, fMaxLength - fBytesWritten);
    if (size > 0)
    {
        memcpy(fBuffer + fBytesWritten, buffer, size);
        fBytesWritten += size;
        return true;
    }
    return false;
}

////////////////////////////////////////////////////////////////////////

#define SkDynamicMemoryWStream_MinBlockSize   256

struct SkDynamicMemoryWStream::Block {
    Block*  fNext;
    char*   fCurr;
    char*   fStop;

    const char* start() const { return (const char*)(this + 1); }
    char*   start() { return (char*)(this + 1); }
    size_t  avail() const { return fStop - fCurr; }
    size_t  written() const { return fCurr - this->start(); }
    
    void init(size_t size)
    {
        fNext = NULL;
        fCurr = this->start();
        fStop = this->start() + size;
    }
    
    const void* append(const void* data, size_t size)
    {
        SkASSERT((size_t)(fStop - fCurr) >= size);
        memcpy(fCurr, data, size);
        fCurr += size;
        return (const void*)((const char*)data + size);
    }
};

SkDynamicMemoryWStream::SkDynamicMemoryWStream() : fHead(NULL), fTail(NULL), fBytesWritten(0), fCopyToCache(NULL)
{
}

SkDynamicMemoryWStream::~SkDynamicMemoryWStream()
{
    reset();
}

const char* SkDynamicMemoryWStream::detach()
{
    const char* result = getStream();
    fCopyToCache = NULL;
    return result;
}

void SkDynamicMemoryWStream::reset()
{
    sk_free(fCopyToCache);
    Block*  block = fHead;
    
    while (block != NULL) {
        Block*  next = block->fNext;
        sk_free(block);
        block = next;
    }
    fHead = fTail = NULL;
    fBytesWritten = 0;
    fCopyToCache = NULL;
}

bool SkDynamicMemoryWStream::write(const void* buffer, size_t count)
{
    if (count > 0) {

        if (fCopyToCache) {
            sk_free(fCopyToCache);
            fCopyToCache = NULL;
        }
        fBytesWritten += count;
        
        size_t  size;
        
        if (fTail != NULL && fTail->avail() > 0) {
            size = SkMin32(fTail->avail(), count);
            buffer = fTail->append(buffer, size);
            SkASSERT(count >= size);
            count -= size;        
            if (count == 0)
                return true;
        }
            
        size = SkMax32(count, SkDynamicMemoryWStream_MinBlockSize);
        Block* block = (Block*)sk_malloc_throw(sizeof(Block) + size);
        block->init(size);
        block->append(buffer, count);
        
        if (fTail != NULL)
            fTail->fNext = block;
        else
            fHead = fTail = block;
        fTail = block;
    }
    return true;
}

bool SkDynamicMemoryWStream::write(const void* buffer, size_t offset, size_t count)
{
    if (offset + count > fBytesWritten)
        return false; // test does not partially modify
    Block* block = fHead;
    while (block != NULL) {
        size_t size = block->written();
        if (offset < size) {
            size_t part = offset + count > size ? size - offset : count;
            memcpy(block->start() + offset, buffer, part);
            if (count <= part)
                return true;
            count -= part;
            buffer = (const void*) ((char* ) buffer + part);
        }
        offset = offset > size ? offset - size : 0;
        block = block->fNext;
    }
    return false;
}

bool SkDynamicMemoryWStream::read(void* buffer, size_t offset, size_t count)
{
    if (offset + count > fBytesWritten)
        return false; // test does not partially modify
    Block* block = fHead;
    while (block != NULL) {
        size_t size = block->written();
        if (offset < size) {
            size_t part = offset + count > size ? size - offset : count;
            memcpy(buffer, block->start() + offset, part);
            if (count <= part)
                return true;
            count -= part;
            buffer = (void*) ((char* ) buffer + part);
        }
        offset = offset > size ? offset - size : 0;
        block = block->fNext;
    }
    return false;
}

void SkDynamicMemoryWStream::copyTo(void* dst) const
{
    Block* block = fHead;
    
    while (block != NULL) {
        size_t size = block->written();
        memcpy(dst, block->start(), size);
        dst = (void*)((char*)dst + size);
        block = block->fNext;
    }
}

const char* SkDynamicMemoryWStream::getStream() const
{
    if (fCopyToCache == NULL) {
        fCopyToCache = (char*)sk_malloc_throw(fBytesWritten);
        this->copyTo(fCopyToCache);
    }
    return fCopyToCache;
}

void SkDynamicMemoryWStream::padToAlign4()
{
    // cast to remove unary-minus warning
    int padBytes = -(int)fBytesWritten & 0x03;
    if (padBytes == 0)
        return;
    int zero = 0;
    write(&zero, padBytes);
}

/////////////////////////////////////////////////////////////////////////////////////////////////////////

void SkDebugWStream::newline()
{
#ifdef SK_DEBUG
    SkDebugf("\n");
#endif
}

bool SkDebugWStream::write(const void* buffer, size_t size)
{
#ifdef SK_DEBUG
    char* s = new char[size+1];
    memcpy(s, buffer, size);
    s[size] = 0;
    SkDebugf("%s", s);
    delete[] s;
#endif
    return true;
}

/////////////////////////////////////////////////////////////////////////////////////////////////////////
/////////////////////////////////////////////////////////////////////////////////////////////////////////

#ifdef SK_DEBUG

#include "SkRandom.h"

#ifdef SK_SUPPORT_UNITTEST
#define MAX_SIZE    (256 * 1024)

static void random_fill(SkRandom& rand, void* buffer, size_t size) {
    char* p = (char*)buffer;
    char* stop = p + size;
    while (p < stop) {
        *p++ = (char)(rand.nextU() >> 8);
    }
}

static void test_buffer() {
    SkRandom rand;
    SkAutoMalloc am(MAX_SIZE * 2);
    char* storage = (char*)am.get();
    char* storage2 = storage + MAX_SIZE;

    random_fill(rand, storage, MAX_SIZE);

    for (int sizeTimes = 0; sizeTimes < 100; sizeTimes++) {
        int size = rand.nextU() % MAX_SIZE;
        if (size == 0) {
            size = MAX_SIZE;
        }
        for (int times = 0; times < 100; times++) {
            int bufferSize = 1 + (rand.nextU() & 0xFFFF);
            SkMemoryStream mstream(storage, size);
            SkBufferStream bstream(&mstream, bufferSize);
            
            int bytesRead = 0;
            while (bytesRead < size) {
                int s = 17 + (rand.nextU() & 0xFFFF);
                int ss = bstream.read(storage2, s);
                SkASSERT(ss > 0 && ss <= s);
                SkASSERT(bytesRead + ss <= size);
                SkASSERT(memcmp(storage + bytesRead, storage2, ss) == 0);
                bytesRead += ss;
            }
            SkASSERT(bytesRead == size);
        }
    }
}
#endif

void SkStream::UnitTest() {
#ifdef SK_SUPPORT_UNITTEST
    {
        static const char s[] = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";
        char            copy[sizeof(s)];
        SkRandom        rand;
        
        for (int i = 0; i < 65; i++)
        {
            char*           copyPtr = copy;
            SkMemoryStream  mem(s, sizeof(s));
            SkBufferStream  buff(&mem, i);
            
            do {
                copyPtr += buff.read(copyPtr, rand.nextU() & 15);
            } while (copyPtr < copy + sizeof(s));
            SkASSERT(copyPtr == copy + sizeof(s));
            SkASSERT(memcmp(s, copy, sizeof(s)) == 0);
        }
    }
    test_buffer();
#endif
}

void SkWStream::UnitTest()
{
#ifdef SK_SUPPORT_UNITTEST
    {
        SkDebugWStream  s;

        s.writeText("testing wstream helpers\n");
        s.writeText("compare: 0 ");         s.writeDecAsText(0);    s.newline();
        s.writeText("compare: 591 ");       s.writeDecAsText(591);  s.newline();
        s.writeText("compare: -9125 ");     s.writeDecAsText(-9125);    s.newline();
        s.writeText("compare: 0 ");         s.writeHexAsText(0, 0); s.newline();
        s.writeText("compare: 03FA ");      s.writeHexAsText(0x3FA, 4); s.newline();
        s.writeText("compare: DEADBEEF ");  s.writeHexAsText(0xDEADBEEF, 4);    s.newline();
        s.writeText("compare: 0 ");         s.writeScalarAsText(SkIntToScalar(0));  s.newline();
        s.writeText("compare: 27 ");        s.writeScalarAsText(SkIntToScalar(27)); s.newline();
        s.writeText("compare: -119 ");      s.writeScalarAsText(SkIntToScalar(-119));   s.newline();
        s.writeText("compare: 851.3333 ");  s.writeScalarAsText(SkIntToScalar(851) + SK_Scalar1/3); s.newline();
        s.writeText("compare: -0.08 ");     s.writeScalarAsText(-SK_Scalar1*8/100); s.newline();
    }

    {
        SkDynamicMemoryWStream  ds;
        const char s[] = "abcdefghijklmnopqrstuvwxyz";
        int i;
        for (i = 0; i < 100; i++) {
            bool result = ds.write(s, 26);
            SkASSERT(result);
        }
        SkASSERT(ds.getOffset() == 100 * 26);
        char* dst = new char[100 * 26 + 1];
        dst[100*26] = '*';
        ds.copyTo(dst);
        SkASSERT(dst[100*26] == '*');
   //     char* p = dst;
        for (i = 0; i < 100; i++)
            SkASSERT(memcmp(&dst[i * 26], s, 26) == 0);
        SkASSERT(memcmp(dst, ds.getStream(), 100*26) == 0);
        delete[] dst;
    }
#endif
}

#endif
