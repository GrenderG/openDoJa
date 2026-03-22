package opendoja.demo;

import com.nttdocomo.ui.Canvas;
import com.nttdocomo.ui.Graphics;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

public final class Ff7InitProbe {
    private Ff7InitProbe() {
    }

    public static void main(String[] args) throws Exception {
        DemoLog.enableInfoLogging();
        Class<?> canvasClass = Class.forName("j");
        Canvas canvas = (Canvas) canvasClass.getDeclaredConstructor().newInstance();
        Graphics graphics = canvas.getGraphics();

        Class<?> stateClass = Class.forName("ad");
        Object state = stateClass.getDeclaredConstructor().newInstance();

        Field hudField = stateClass.getField("c");
        Object hud = hudField.get(state);
        Method initHud = hud.getClass().getMethod("a", Graphics.class, int.class, int.class);
        DemoLog.info(Ff7InitProbe.class, "h.a(Graphics,240,240)=" + initHud.invoke(hud, graphics, 240, 240));

        Field canvasField = stateClass.getField("n");
        canvasField.set(state, canvas);

        for (String methodName : new String[]{"g", "j", "h", "i", "m", "k", "l"}) {
            Method method = stateClass.getDeclaredMethod(methodName);
            method.setAccessible(true);
            DemoLog.info(Ff7InitProbe.class, methodName + "()=" + method.invoke(state));
        }

        Object fullState = stateClass.getDeclaredConstructor().newInstance();
        Object fullHud = hudField.get(fullState);
        initHud.invoke(fullHud, graphics, 240, 240);
        Method init = stateClass.getMethod("a", canvasClass);
        DemoLog.info(Ff7InitProbe.class, "ad.a(j)=" + init.invoke(fullState, canvas));
        Method tick = stateClass.getMethod("a", int.class);
        DemoLog.info(Ff7InitProbe.class, "ad.a(0)=" + tick.invoke(fullState, 0));

        Field managerField = stateClass.getDeclaredField("o");
        managerField.setAccessible(true);
        Object manager = managerField.get(fullState);
        Class<?> managerClass = manager.getClass();
        Field statesField = managerClass.getDeclaredField("d");
        statesField.setAccessible(true);
        Object[] states = (Object[]) statesField.get(manager);
        Field indexField = managerClass.getField("a");
        int stateIndex = indexField.getInt(manager);
        Object currentState = states[stateIndex];
        Method render = currentState.getClass().getMethod("c", managerClass, stateClass);
        try {
            render.invoke(currentState, manager, fullState);
            DemoLog.info(Ff7InitProbe.class, "state.c(be,ad)=ok");
        } catch (Exception e) {
            Throwable cause = e.getCause() == null ? e : e.getCause();
            DemoLog.error(Ff7InitProbe.class, "state.c(be,ad) failed", cause);
        }
    }
}
