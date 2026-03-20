package opendoja.demo;

import com.nttdocomo.ui.Canvas;
import com.nttdocomo.ui.Display;
import com.nttdocomo.ui.Frame;
import com.nttdocomo.ui.Graphics;
import com.nttdocomo.ui.IApplication;

public final class HelloDoJaDemo extends IApplication {
    @Override
    public void start() {
        Display.setCurrent((Frame) new HelloCanvas());
    }

    static final class HelloCanvas extends Canvas {
        private int x = 8;
        private int dx = 4;

        HelloCanvas() {
            setSoftLabel(SOFT_KEY_1, "END");
            setBackground(Graphics.getColorOfName(Graphics.NAVY));
        }

        @Override
        public void paint(Graphics g) {
            g.lock();
            g.clearRect(0, 0, Display.getWidth(), Display.getHeight());
            g.setColor(Graphics.getColorOfName(Graphics.WHITE));
            g.drawString("openDoJa demo", 8, 8);
            g.setColor(Graphics.getColorOfName(Graphics.YELLOW));
            g.fillRect(x, 40, 32, 16);
            g.unlock(true);
        }

        @Override
        public void processEvent(int type, int param) {
            if (type == Display.KEY_RELEASED_EVENT && param == Display.KEY_SOFT1) {
                IApplication.getCurrentApp().terminate();
                return;
            }
            if (type == Display.KEY_PRESSED_EVENT) {
                if (param == Display.KEY_LEFT) {
                    x = Math.max(0, x - dx);
                    repaint();
                } else if (param == Display.KEY_RIGHT) {
                    x = Math.min(Display.getWidth() - 32, x + dx);
                    repaint();
                }
            }
        }
    }
}
