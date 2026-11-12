package opendoja.probes;

import com.docomostar.lang.XString;
import com.docomostar.lang._XStringSupport;
import com.docomostar.opt.device.Pedometer;
import com.docomostar.system.InvitationParam;
import com.docomostar.system.PhoneBook;
import com.docomostar.system.StoreException;
import com.nttdocomo.system.PhoneBookParam;
import com.nttdocomo.ui.IApplication;
import opendoja.host.DesktopLauncher;
import opendoja.host.LaunchConfig;
import opendoja.host.OpenDoJaLaunchArgs;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

/** Exercises shared managed data, real rendered pixels, cancellation and STAR exception types. */
public final class StarManagedApisProbe {
    private static int checks;

    public static void main(String[] args) throws Exception {
        System.setProperty("java.awt.headless", "true");
        Map<String, String> previous = new LinkedHashMap<>();
        for (String key : new String[]{OpenDoJaLaunchArgs.PEDOMETER_OFF,
                OpenDoJaLaunchArgs.PEDOMETER_TODAY_STEPS, OpenDoJaLaunchArgs.PEDOMETER_HISTORY_DAYS,
                OpenDoJaLaunchArgs.PHONEBOOK_SELECTION_INDEX}) previous.put(key, System.getProperty(key));
        try {
            pedometer();
            stringsAndGraphics();
            phoneBook();
            invitation();
            System.out.println("star-managed-apis-probe-ok (" + checks + " checks)");
        } finally {
            previous.forEach((key, value) -> {
                if (value == null) System.clearProperty(key); else System.setProperty(key, value);
            });
        }
    }

    private static void pedometer() {
        System.setProperty(OpenDoJaLaunchArgs.PEDOMETER_OFF, "false");
        System.setProperty(OpenDoJaLaunchArgs.PEDOMETER_TODAY_STEPS, "42");
        System.setProperty(OpenDoJaLaunchArgs.PEDOMETER_HISTORY_DAYS, "1");
        Pedometer star = Pedometer.getPedometer();
        var nativePedometer = com.nttdocomo.opt.device.Pedometer.getPedometer();
        check(star == Pedometer.getPedometer(), "STAR pedometer factory identity");
        check(star.getAttribute(Pedometer.DEV_PEDOMETER) == Pedometer.ATTR_PEDOMETER_ON, "measurement on");
        check(star.getAttribute(-1) == nativePedometer.getAttribute(-1), "unknown attribute parity");
        check(star.getCount() == 1 && star.getTotalSteps() == 42, "configured measurement");
        check(star.getTotalDistance() == nativePedometer.getTotalDistance(), "shared distance calculation");
        check(star.getData().length == 1 && star.getData(0, 1)[0].getNumberOfSteps() == 42, "typed STAR history");
        var today = star.getTodayData();
        var nativeToday = nativePedometer.getTodayData();
        check(today.getDistance() == nativeToday.getDistance()
                && today.getNumberOfAerobicsSteps() == nativeToday.getNumberOfAerobicsSteps()
                && today.getAerobicsTime() == nativeToday.getAerobicsTime()
                && today.getConsumptionCalorie() == nativeToday.getConsumptionCalorie()
                && today.getAmountOfCombustionFat() == nativeToday.getAmountOfCombustionFat(), "history units and values");
        long timestamp = today.getDate().getTime();
        today.getDate().setTime(0);
        check(today.getDate().getTime() == timestamp, "history date is defensive");
        expect(IllegalArgumentException.class, () -> star.getData(-1, 1));
        System.setProperty(OpenDoJaLaunchArgs.PEDOMETER_TODAY_STEPS, "142");
        check(star.getTotalSteps() == 142 && nativePedometer.getTotalSteps() == 142, "both profiles see updated data");
        System.setProperty(OpenDoJaLaunchArgs.PEDOMETER_OFF, "true");
        check(star.getAttribute(0) == Pedometer.ATTR_PEDOMETER_OFF
                && star.getCount() == 0 && star.getTotalSteps() == 0 && star.getTotalDistance() == 0, "disabled measurement");
        expect(SecurityException.class, star::getTodayData);
        expect(SecurityException.class, nativePedometer::getTodayData);
    }

    private static void stringsAndGraphics() {
        XString text = new XString("STAR あいう");
        check(text instanceof com.docomostar.lang.XObject, "STAR XObject hierarchy");
        check(text.equals(text) && !text.equals(new XString("STAR あいう")), "identity equality");
        check(!text.toString().contains("あいう") && text.toString().startsWith(XString.class.getName() + "@"),
                "stringification keeps managed contents private");
        XString joined = text.concat(new XString(" 123"));
        check(joined.length() == "STAR あいう 123".length() && joined != text, "managed concatenation");
        check(_XStringSupport.unwrap(_XStringSupport.wrap(_XStringSupport.unwrap(text))) == _XStringSupport.unwrap(text),
                "bridge retains native managed object");
        check(_XStringSupport.wrap((com.nttdocomo.lang.XString) null) == null
                && _XStringSupport.wrap((com.nttdocomo.lang.XString[]) null) == null, "nullable native results");
        expect(NullPointerException.class, () -> new XString((String) null));
        expect(NullPointerException.class, () -> text.concat(null));

        var font = com.docomostar.ui.Font.getDefaultFont();
        var nativeFont = com.nttdocomo.ui.Font.getDefaultFont();
        var nativeText = _XStringSupport.unwrap(joined);
        check(font.stringWidth(joined) == nativeFont.stringWidth("STAR あいう 123"), "managed font width uses contents");
        check(font.stringWidth(joined, 2, 4) == nativeFont.stringWidth(nativeText, 2, 4)
                && font.getBBoxWidth(joined, 2, 4) == nativeFont.getBBoxWidth(nativeText, 2, 4), "substring font metrics");
        check(font.getBBoxWidth(joined) == nativeFont.getBBoxWidth(nativeText)
                && font.getBBoxHeight(joined) == nativeFont.getBBoxHeight(nativeText), "managed bounding box");
        check(font.getLineBreak(joined, 0, joined.length(), 40)
                == nativeFont.getLineBreak(nativeText, 0, joined.length(), 40), "managed line breaking");
        expect(NullPointerException.class, () -> font.stringWidth((XString) null));
        expect(StringIndexOutOfBoundsException.class, () -> font.stringWidth(joined, -1, 1));

        var nativeImage = com.nttdocomo.ui.Image.createImage(320, 96);
        var starImage = com.docomostar.ui.Image.createImage(320, 96);
        var nativeGraphics = nativeImage.getGraphics();
        var starGraphics = starImage.getGraphics();
        try {
            nativeGraphics.setOrigin(3, 2); starGraphics.setOrigin(3, 2);
            nativeGraphics.setClip(1, 1, 300, 84); starGraphics.setClip(1, 1, 300, 84);
            nativeGraphics.setColor(0xffffff); starGraphics.setColor(0xffffff);
            nativeGraphics.drawString(nativeText, 2, 28); starGraphics.drawString(joined, 2, 28);
            nativeGraphics.drawString(nativeText, 2, 60, 2, 4); starGraphics.drawString(joined, 2, 60, 2, 4);
            nativeGraphics.drawString((com.nttdocomo.lang.XString) null, 0, 0);
            starGraphics.drawString((XString) null, 0, 0);
            int[] pixels = {0, 0x00112233, 0x7f445566, 0xff778899, 0x00010203, 0x7f040506, 0xff070809};
            nativeGraphics.setPixels(0, 1, 3, 2, pixels, 1); starGraphics.setPixels(0, 1, 3, 2, pixels, 1);
            nativeGraphics.setRGBPixel(10, 10, 0x00123456); starGraphics.setRGBPixel(10, 10, 0x00123456);
            nativeGraphics.setRGBPixel(310, 10, 0x00abcdef); starGraphics.setRGBPixel(310, 10, 0x00abcdef);
            var nativeRead = nativeImage.getGraphics();
            var starRead = com.docomostar.ui.Image.unwrap(starImage).getGraphics();
            try {
                check(Arrays.equals(nativeRead.getRGBPixels(0, 0, 320, 96, null, 0),
                        starRead.getRGBPixels(0, 0, 320, 96, null, 0)), "text, substring, origin, clipping and RGB pixels match DoJa including alpha");
                check(starRead.getRGBPixel(13, 12) == 0xff123456, "RGB write remains opaque");
            } finally { nativeRead.dispose(); starRead.dispose(); }
        } finally {
            nativeGraphics.dispose();
            ((com.nttdocomo.ui.Graphics) starGraphics.hostGraphicsOGL()).dispose();
            nativeImage.dispose(); starImage.dispose();
        }
    }

    private static void phoneBook() throws Exception {
        var app = DesktopLauncher.launch(LaunchConfig.builder(ProbeApp.class).parameter("AccessUserInfo", "true").build());
        try {
            System.setProperty(OpenDoJaLaunchArgs.PHONEBOOK_SELECTION_INDEX, "1");
            PhoneBook selected = PhoneBook.selectEntry();
            check(selected != null && selected.getId() == 2, "shared phone-book selection");
            check("Contact 2".equals(value(selected.getName())) && "CONTACT 2".equals(value(selected.getKana())), "managed contact text");
            check(selected.getName(PhoneBook.GIVEN_NAME) == null && selected.getKana(PhoneBook.GIVEN_NAME) == null,
                    "absent name parts stay null");
            check("Stub Contacts".equals(value(selected.getGroupName())) && selected.getGroupId()
                    == com.nttdocomo.system.PhoneBook.getEntry(2).getGroupId(), "shared group");
            System.setProperty(OpenDoJaLaunchArgs.PHONEBOOK_SELECTION_INDEX, "-1");
            check(PhoneBook.selectEntry() == null, "canceled selection stays null");

            PhoneBookParam added = new PhoneBookParam();
            added.setName("Shared native contact"); added.setKana("SHARED");
            added.addPhoneNumber("09012345678"); added.addPhoneNumber("08012345678");
            added.addMailAddress("player@example.com");
            int id = com.nttdocomo.system.PhoneBook.addEntry(added)[0];
            PhoneBook entry = PhoneBook.getEntry(id);
            check("Shared native contact".equals(value(entry.getName())), "STAR sees DoJa-created records");
            XString[] numbers = entry.getPhoneNumbers();
            check(numbers.length == 2 && "09012345678".equals(value(numbers[0]))
                    && "08012345678".equals(value(entry.getPhoneNumber(1))), "STAR typed phone-number array");
            numbers[0] = null;
            check(entry.getPhoneNumbers()[0] != null, "returned array does not mutate shared contact");
            check("player@example.com".equals(value(entry.getMailAddresses(PhoneBook.ADDRESS_FULL)[0]))
                    && "player".equals(value(entry.getMailAddress(0, PhoneBook.ADDRESS_USER)))
                    && "example.com".equals(value(entry.getMailAddress(0, PhoneBook.ADDRESS_DOMAIN))), "shared mail address splitting");
            try { PhoneBook.getEntry(Integer.MAX_VALUE); throw new AssertionError("Missing entry accepted"); }
            catch (StoreException failure) {
                check(failure.getStatus() == StoreException.NOT_FOUND
                        && failure.getCause() instanceof com.nttdocomo.system.StoreException, "STAR store status and cause");
            }
            expect(IllegalArgumentException.class, () -> entry.getName(-1));
        } finally { app.terminate(); }
        var unprivileged = DesktopLauncher.launch(LaunchConfig.builder(ProbeApp.class).build());
        try {
            expect(SecurityException.class, () -> PhoneBook.getEntry(1));
            expect(SecurityException.class, () -> com.nttdocomo.system.PhoneBook.getEntry(1));
        } finally { unprivileged.terminate(); }
    }

    private static void invitation() {
        XString recipient = new XString("09012345678");
        InvitationParam param = new InvitationParam("bomberwide", recipient, 2592000, "launch text", 2);
        check(param.getRecipient() == recipient && "bomberwide".equals(param.getLauncherAppName()), "invitation app and recipient");
        check(param.getExpiration() == 2592000 && param.getDownloadBehavior() == 2, "expiration and behavior are distinct");
        check("launch text".equals(param.getLaunchParameter()), "launch parameter is not the recipient");
        XString replacement = new XString("08012345678");
        param.setRecipient(replacement); param.setLauncherAppName("lostplanet");
        param.setExpiration(60); param.setDownloadBehavior(1); param.setLaunchParameter("updated");
        check(param.getRecipient() == replacement && "lostplanet".equals(param.getLauncherAppName())
                && param.getExpiration() == 60 && param.getDownloadBehavior() == 1
                && "updated".equals(param.getLaunchParameter()), "game setter descriptors update separate fields");
        param.setRecipient(null); param.setLaunchParameter(null);
        check(param.getRecipient() == null && param.getLaunchParameter() == null, "nullable invitation data");
    }

    private static String value(XString text) {
        return com.nttdocomo.lang._XStringSupport.value(_XStringSupport.unwrap(text), "text");
    }

    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }

    private static void expect(Class<? extends Throwable> type, CheckedRunnable action) {
        try { action.run(); } catch (Throwable failure) {
            check(type.isInstance(failure), "Expected " + type.getName() + ", got " + failure);
            return;
        }
        throw new AssertionError("Expected " + type.getName());
    }

    private interface CheckedRunnable { void run() throws Exception; }
    public static final class ProbeApp extends IApplication {
        @Override public void start() { }
    }
}
