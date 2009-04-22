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


#include "bitstreamparser.h"
#include "oscl_assert.h"
#include "oscl_byte_order.h"
#include "oscl_dll.h"

OSCL_EXPORT_REF BitStreamParser::BitStreamParser(uint8* stream, uint32 size)
{
    this->size = size;
    start   = (uint8*)stream;
    bytepos = (uint8*)start;
    bitpos  = MOST_SIG_BIT;
}


OSCL_EXPORT_REF uint32 BitStreamParser::ReadBits(uint8 numberOfBits)
{
    //Initialize output to zero before shifting out bits.
    uint32 output = 0;

    OSCL_ASSERT(numberOfBits <= BITS_PER_UINT32);
    //In case OSCL_ASSERT is defined to do nothing, set the max size.
    if (numberOfBits > BITS_PER_UINT32) numberOfBits = BITS_PER_UINT32;

    //Note: Using the host's native shift operator will automatically
    //convert from big endian to host's endianness.
    while (numberOfBits)
    {
        //Optimize reads for special cases such as byte-aligned reads and
        //processing multiple bits at a time.
        if ((numberOfBits >= BITS_PER_UINT8) && (bitpos == MOST_SIG_BIT))
        {
            //This is the special case where a read is a whole byte (aligned).
            //Shift the output over 8 bits.
            output <<= BITS_PER_UINT8;
            //OR the current byte from the stream into the output.
            output |= *bytepos;
            //Advance the stream byte pointer.
            bytepos++;
            //Decrement the number of bits left to read.
            numberOfBits -= BITS_PER_UINT8;
        }
        else	//Read one or more bits at a time.
        {
            //Define the bitmask corresponding to the number of bits
            //to read from the current byte.
            //This is implemented as a static look-up table for efficiency.
            static const uint8 bitmask[] =
            {
                0x00, 0x01, 0x03, 0x07, 0x0f, 0x1f, 0x3f, 0x7f, 0xff
            };

            //Process the bits in the current byte...

            //Determine the number of bits remaining in the current byte of the stream.
            //This is the upper limit on the number of bits we will read
            //this through the loop.
            uint8 bitsFromThisByte = bitpos + 1;
            //If more bits remain then we need, only take what we need.
            if (bitsFromThisByte > numberOfBits) bitsFromThisByte = numberOfBits;

            //Shift the output value over to make room for the new bits to read.
            output <<= bitsFromThisByte;
            //OR in the bits from the stream.
            //This reads the current byte from the stream,
            //shifts it over so the bits we want are at the LS end,
            //and masks off all of the bits except the ones we want.
            output |= ((*bytepos) >> (bitpos - (bitsFromThisByte - 1))) & bitmask[bitsFromThisByte];

            //Decrement the numberOfBits remaining...
            numberOfBits -= bitsFromThisByte;
            //...and advance the bitpos and bytepos pointers.
            NextBits(bitsFromThisByte);
        }
    }

    return output;
}


OSCL_EXPORT_REF uint8 BitStreamParser::ReadUInt8(void)
{
    //If bitpos is not on a byte boundary, have to use ReadBits...
    if (bitpos != MOST_SIG_BIT) return ReadBits(BITS_PER_UINT8);

    //Otherwise, this is faster.
    OSCL_ASSERT(bytepos < (start + size));
    uint8 read = *bytepos;
    bytepos++;

    return read;
}


OSCL_EXPORT_REF uint16 BitStreamParser::ReadUInt16(void)
{
    uint16 read;
    ((uint8*)&read)[0] = ReadUInt8();
    ((uint8*)&read)[1] = ReadUInt8();
    big_endian_to_host((char*)&read, sizeof(read));
    return read;
}


OSCL_EXPORT_REF uint32 BitStreamParser::ReadUInt32(void)
{
    uint32 read;
    ((uint8*)&read)[0] = ReadUInt8();
    ((uint8*)&read)[1] = ReadUInt8();
    ((uint8*)&read)[2] = ReadUInt8();
    ((uint8*)&read)[3] = ReadUInt8();
    big_endian_to_host((char*)&read, sizeof(read));
    return read;
}


OSCL_EXPORT_REF void BitStreamParser::WriteBits(uint8 numberOfBits, const uint8* data)
{
    //This is not the most efficient algorithm, but it is the least complex.
    //Treat "data" as an input stream.
    BitStreamParser input(const_cast<uint8*>(data), BITS_TO_BYTES(numberOfBits));
    //Skip over the unused bits.
    input.NextBits(input.BitsLeft() - numberOfBits);
    //Loop through each bit to process...
    while (numberOfBits)
    {
        uint8 bitmask = 1 << bitpos;
        //READ
        uint8 byte = *bytepos;
        //MODIFY
        byte &= ~(bitmask);			             //Clear the bit being written.
        byte |= (input.ReadBits(1) << bitpos); //Write the bit.
        //WRITE
        *bytepos = byte;
        //Advance the bit pointer
        NextBit();
        numberOfBits--;
    }
}


OSCL_EXPORT_REF void BitStreamParser::WriteUInt8(uint8 data)
{
    if (bitpos != MOST_SIG_BIT)
    {
        WriteBits(BITS_PER_BYTE, &data);
    }
    else
    {
        OSCL_ASSERT(bytepos < (start + size));
        *bytepos = data;
        bytepos++;
    }
}


OSCL_EXPORT_REF void BitStreamParser::WriteUInt16(uint16 data)
{
    uint16 be = data;
    host_to_big_endian((char*)&be, sizeof(be));
    WriteUInt8(((uint8*)&be)[0]);
    WriteUInt8(((uint8*)&be)[1]);
}


OSCL_EXPORT_REF void BitStreamParser::WriteUInt32(uint32 data)
{
    uint32 be = data;
    host_to_big_endian((char*)&be, sizeof(be));
    WriteUInt8(((uint8*)&be)[0]);
    WriteUInt8(((uint8*)&be)[1]);
    WriteUInt8(((uint8*)&be)[2]);
    WriteUInt8(((uint8*)&be)[3]);
}


OSCL_EXPORT_REF void BitStreamParser::NextBits(uint32 numberOfBits)
{
    //Check if we read past the end of the stream.
    OSCL_ASSERT(bytepos < (start + size));

    //bitpos counts down from 7 to 0, so subtract it from 7 to get the ascending position.
    uint32 newbitpos = numberOfBits  + (MOST_SIG_BIT - bitpos);
    //Convert the ascending bit position to a descending position using only the least-significant bits.
    bitpos = MOST_SIG_BIT - (newbitpos & LEAST_SIG_3_BITS_MASK);
    //Calculate the number of bytes advanced.
    bytepos += (newbitpos / BITS_PER_BYTE);
}

