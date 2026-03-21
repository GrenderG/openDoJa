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

public final class ExtraMath {
    private ExtraMath() {
    }

    public static double log(double value) {
        return Math.log(value);
    }

    public static double pow(double base, double exponent) {
        return Math.pow(base, exponent);
    }
}
