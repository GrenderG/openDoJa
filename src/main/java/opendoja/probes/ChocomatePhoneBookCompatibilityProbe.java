package opendoja.probes;

import com.nttdocomo.lang.XString;
import com.nttdocomo.lang._XStringSupport;
import com.nttdocomo.ui.IApplication;
import opendoja.host.DoJaRuntime;
import opendoja.host.LaunchConfig;
import opendoja.host.OpenDoJaLaunchArgs;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

/**
 * Verifies Chocomate's private phone-book address-select path no longer receives null.
 *
 * <p>Run with `resources/sample_games/Chocomate/chocomate.jar` on the classpath.</p>
 */
public final class ChocomatePhoneBookCompatibilityProbe {
    private ChocomatePhoneBookCompatibilityProbe() {
    }

    public static void main(String[] args) throws Exception {
        String previousSelection = System.getProperty(OpenDoJaLaunchArgs.PHONEBOOK_SELECTION_INDEX);
        System.setProperty(OpenDoJaLaunchArgs.PHONEBOOK_SELECTION_INDEX, "2");
        DoJaRuntime runtime = DoJaRuntime.bootstrap(LaunchConfig.builder(ProbeApp.class)
                .parameter("AccessUserInfo", "true")
                .build());
        try {
            Object canvas = newChocomateCanvas();
            int selectedId = invokePhoneBookIdSelect(canvas);
            if (selectedId <= 0) {
                throw new IllegalStateException("Chocomate phone-book id select returned " + selectedId);
            }
            XString selectedMail = invokePhoneBookMailSelect(canvas);
            if (selectedMail == null || !"contact3@example.com".equals(_XStringSupport.value(selectedMail, "selectedMail"))) {
                throw new IllegalStateException("Chocomate phone-book mail select returned unexpected value");
            }
            System.out.println("chocomate-phonebook-compatibility-probe-ok");
        } finally {
            runtime.shutdown();
            if (previousSelection == null) {
                System.clearProperty(OpenDoJaLaunchArgs.PHONEBOOK_SELECTION_INDEX);
            } else {
                System.setProperty(OpenDoJaLaunchArgs.PHONEBOOK_SELECTION_INDEX, previousSelection);
            }
        }
    }

    private static Object newChocomateCanvas() throws Exception {
        Class<?> cmClass = Class.forName("chocomate.Cm");
        Constructor<?> cmConstructor = cmClass.getDeclaredConstructor();
        cmConstructor.setAccessible(true);
        Object cm = cmConstructor.newInstance();

        Class<?> dmClass = Class.forName("chocomate.Dm");
        Constructor<?> dmConstructor = dmClass.getDeclaredConstructor(cmClass, int.class);
        dmConstructor.setAccessible(true);
        return dmConstructor.newInstance(cm, 0);
    }

    private static int invokePhoneBookIdSelect(Object canvas) throws Exception {
        Method f2 = canvas.getClass().getDeclaredMethod("f2");
        f2.setAccessible(true);
        return (Integer) f2.invoke(canvas);
    }

    private static XString invokePhoneBookMailSelect(Object canvas) throws Exception {
        Method f5 = canvas.getClass().getDeclaredMethod("f5");
        f5.setAccessible(true);
        return (XString) f5.invoke(canvas);
    }

    public static final class ProbeApp extends IApplication {
        @Override
        public void start() {
        }
    }
}
