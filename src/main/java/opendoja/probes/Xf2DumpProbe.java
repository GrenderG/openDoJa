package opendoja.probes;

import com.acrodea.xf3.def.xfeDefaultHWAcceleratedGameGraphFactory;
import com.acrodea.xf3.def.xfeDefaultXF2Loader;
import com.acrodea.xf3.xfeActor;
import com.acrodea.xf3.xfeCamera;
import com.acrodea.xf3.xfeGameGraphNodeIterator;
import com.acrodea.xf3.xfeGroup;
import com.acrodea.xf3.xfeNode;
import com.acrodea.xf3.xfeNodeList;
import com.acrodea.xf3.xfeRoot;
import com.acrodea.xf3.loader.xfeXF2Context;
import com.acrodea.xf3.loader.xfeXF2Reader;
import com.acrodea.xf3.math.xfMatrix4;

public final class Xf2DumpProbe {
    private Xf2DumpProbe() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 1) {
            throw new IllegalArgumentException("Usage: Xf2DumpProbe <xf2-resource>");
        }
        xfeRoot root = new xfeRoot(new xfeDefaultHWAcceleratedGameGraphFactory());
        xfeDefaultXF2Loader loader = new xfeDefaultXF2Loader();
        boolean loaded = loader.load(args[0], root, new xfeNodeList(), new xfeXF2Context() { }, new xfeXF2Reader() { });
        if (!loaded) {
            throw new IllegalStateException("Load failed: " + loader.getErrorMessage());
        }
        System.out.println("scene=" + root.getLoadedSceneName());
        xfeGameGraphNodeIterator iterator = root.getNodes();
        for (xfeNode node = iterator.getNext(); node != null; node = iterator.getNext()) {
            System.out.println(describe(node));
        }
    }

    private static String describe(xfeNode node) {
        StringBuilder out = new StringBuilder();
        out.append(node.getClass().getSimpleName()).append(' ');
        out.append(node.getName());
        out.append(" parent=");
        out.append(node.getParent() == null ? "<root>" : node.getParent().getName());
        if (node instanceof xfeCamera camera) {
            appendMatrix(out, camera.getTransformation().getMatrix());
            out.append(" fov=").append(camera.getPreferredView().getFOV());
        } else if (node instanceof xfeActor actor) {
            appendMatrix(out, actor.getTransformation().getMatrix());
        } else if (node instanceof xfeGroup group) {
            appendMatrix(out, group.getTransformation().getMatrix());
        }
        return out.toString();
    }

    private static void appendMatrix(StringBuilder out, xfMatrix4 matrix) {
        out.append(" m=");
        for (int row = 0; row < 4; row++) {
            if (row > 0) {
                out.append('|');
            }
            out.append('[');
            for (int column = 0; column < 4; column++) {
                if (column > 0) {
                    out.append(',');
                }
                out.append(matrix.m[row][column]);
            }
            out.append(']');
        }
    }
}
