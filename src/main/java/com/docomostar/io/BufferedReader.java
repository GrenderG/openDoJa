package com.docomostar.io;

import java.io.Reader;

/** Uses the DoJa reader, including its scratchpad-aware readiness behavior. */
public class BufferedReader extends com.nttdocomo.io.BufferedReader {
    public BufferedReader(Reader in) { super(in); }
    public BufferedReader(Reader in, int size) { super(in, size); }
}
