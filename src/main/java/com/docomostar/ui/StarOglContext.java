package com.docomostar.ui;

import opendoja.host.DoJaRuntime;
import opendoja.host.ogl.OglRenderer.FragmentState;

import java.util.Map;
import java.util.WeakHashMap;

/** Fragment settings belong to the STAR application, even when it changes drawing surfaces. */
final class StarOglContext {
    private static final Map<DoJaRuntime, StarOglContext> CONTEXTS = new WeakHashMap<>();
    private static final ThreadLocal<StarOglContext> STANDALONE = ThreadLocal.withInitial(StarOglContext::new);
    private FragmentState fragments;
    private long revision;

    static synchronized StarOglContext current() {
        DoJaRuntime runtime = DoJaRuntime.current();
        return runtime == null ? STANDALONE.get() : CONTEXTS.computeIfAbsent(runtime, ignored -> new StarOglContext());
    }

    synchronized long bind(com.nttdocomo.ui.Graphics graphics, long previousRevision) {
        if (fragments == null) fragments = graphics.captureOglFragmentState();
        if (previousRevision != revision) graphics.applyOglFragmentState(fragments);
        return revision;
    }

    synchronized long remember(com.nttdocomo.ui.Graphics graphics) {
        // Read the validated backend state: invalid GL arguments must not affect other views.
        FragmentState next = graphics.captureOglFragmentState();
        if (!next.equals(fragments)) {
            fragments = next;
            revision++;
        }
        return revision;
    }
}
