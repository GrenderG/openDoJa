// -*- Mode: Java; indent-tabs-mode: t; tab-width: 4 -*-
// ---------------------------------------------------------------------------
// Multi-Phasic Applications: SquirrelJME
//     Copyright (C) Stephanie Gawroriski <xer@multiphasicapps.net>
// ---------------------------------------------------------------------------
// Adapted for openDoJa from a SquirrelJME support utility.
// SquirrelJME is under the Mozilla Public License Version 2.0.
// See https://github.com/SquirrelJME/SquirrelJME/blob/trunk/LICENSE for licensing and copyright information.
// ---------------------------------------------------------------------------

package opendoja.audio.mld.support;

public final class ObjectShelf {
    private ObjectShelf() {
    }

    public static void arrayCopy(Object source, int sourcePos, Object destination, int destinationPos, int length) {
        System.arraycopy(source, sourcePos, destination, destinationPos, length);
    }
}
