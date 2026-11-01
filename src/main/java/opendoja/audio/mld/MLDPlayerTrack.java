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
 * Event list state
 */
public class MLDPlayerTrack
{
    public int getIndex() { return this.index; }

    /**
     * Starting cuepoint
     */
    int cuepoint;

    /**
     * Track has no more events
     */
    boolean finished;

    /**
     * Index within sequencer
     */
    int index;

    /**
     * Runtime lane-to-channel assignment for the 4 local track lanes.
     */
    final int[] channelMap = new int[4];

    /**
     * Event list
     */
    MLDTrack mld;

    /**
     * Current event offset
     */
    int offset;

    /**
     * Event ticks until next event
     */
    int ticks;

    /**
     * Hold the current event position and reuse {@link #ticks} as the next retry
     * delay instead of rescheduling from the current event's raw delta byte.
     */
    boolean trackControlSkipReschedule;

    /**
     * Replay-cursor byte used by backend-specific track-control engines.
     */
    int trackControlReplayByte;

    /**
     * Raw source offset of the current event's delta byte.
     */
    int trackControlRawOffset;

    /**
     * Raw source offset immediately after the current event payload.
     */
    int trackControlRawEndOffset;

    /**
     * Track-local mode byte used by backend-specific track-control engines.
     */
    int trackControlModeByte;
}
