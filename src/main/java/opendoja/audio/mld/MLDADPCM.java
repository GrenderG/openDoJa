// -*- Mode: Java; indent-tabs-mode: nil; tab-width: 4 -*-
// ---------------------------------------------------------------------------
// Keitai Wiki Community Music Implementation
//     Originally written and contributed by Guy Perfect
//     Continued maintenance and upkeep by SquirrelJME/Stephanie Gawroriski
// ---------------------------------------------------------------------------
// This specific file is under the given license:
// This is free and unencumbered software released into the public domain.
//
// Anyone is free to copy, modify, publish, use, compile, sell, or
// distribute this software, either in source code form or as a compiled
// binary, for any purpose, commercial or non-commercial, and by any
// means.
//
// In jurisdictions that recognize copyright laws, the author or authors
// of this software dedicate any and all copyright interest in the
// software to the public domain. We make this dedication for the benefit
// of the public at large and to the detriment of our heirs and
// successors. We intend this dedication to be an overt act of
// relinquishment in perpetuity of all present and future rights to this
// software under copyright law.
//
// THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
// EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
// MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT.
// IN NO EVENT SHALL THE AUTHORS BE LIABLE FOR ANY CLAIM, DAMAGES OR
// OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE,
// ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR
// OTHER DEALINGS IN THE SOFTWARE.
//
// For more information, please refer to <https://unlicense.org/>
// ---------------------------------------------------------------------------

package opendoja.audio.mld;



/**
 * ADPCM data class
 */
public final class MLDADPCM
{
    byte[] data;

    int selectorHeaderLength;

    int selectorId = -1;

    int selectorFlags;

    int sampleRateHz;

    int codedBits;

    int channelCount;

    boolean variantBit;

    byte[] payload = new byte[0];

    public int channelCount()
    {
        return this.channelCount;
    }

    public int codedBits()
    {
        return this.codedBits;
    }

    public byte[] data()
    {
        return this.data;
    }

    public byte[] payload()
    {
        return this.payload;
    }

    double durationSeconds()
    {
        if (this.sampleRateHz <= 0 || this.codedBits <= 0)
            return 0.0;
        int channels = Math.max(1, this.channelCount);
        double codedFrames = (this.payload.length * 8.0d) / this.codedBits;
        return codedFrames / (channels * this.sampleRateHz);
    }

    public int sampleRateHz()
    {
        return this.sampleRateHz;
    }

    public int selectorFlags()
    {
        return this.selectorFlags;
    }

    public int selectorHeaderLength()
    {
        return this.selectorHeaderLength;
    }

    public int selectorId()
    {
        return this.selectorId;
    }

    public boolean variantBit()
    {
        return this.variantBit;
    }
}
