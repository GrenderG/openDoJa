package opendoja.host.ogl;

import com.nttdocomo.ui.ogl.GraphicsOGL;
import java.util.ArrayDeque;

/** Eruption commands use the Graphics object that opened the current drawing block. */
public final class EruptionGraphicsContext {
    private static final ThreadLocal<ArrayDeque<GraphicsOGL>> CONTEXTS=ThreadLocal.withInitial(ArrayDeque::new);
    private EruptionGraphicsContext() { }
    public static void begin(GraphicsOGL graphics) { CONTEXTS.get().push(graphics); }
    public static void end(GraphicsOGL graphics) {
        ArrayDeque<GraphicsOGL> contexts=CONTEXTS.get();
        if(contexts.isEmpty()||contexts.peek()!=graphics) throw new IllegalStateException("Unmatched endEruption");
        try { graphics.glFlush(); }
        finally { contexts.pop();if(contexts.isEmpty()) CONTEXTS.remove(); }
    }
    public static GraphicsOGL current(GraphicsOGL fallback) {
        GraphicsOGL current=CONTEXTS.get().peek();return current==null?fallback:current;
    }
}
