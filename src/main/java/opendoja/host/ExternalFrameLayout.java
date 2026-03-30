package opendoja.host;

import java.awt.Dimension;
import java.awt.Rectangle;

record ExternalFrameLayout(
        boolean enabled,
        int scale,
        Rectangle screenArea,
        Rectangle drawArea,
        Rectangle topBar,
        Rectangle bottomBar,
        Rectangle leftConnector,
        Rectangle rightConnector,
        Rectangle statusArea,
        Rectangle softKeyArea,
        Dimension preferredSize
) {
}
