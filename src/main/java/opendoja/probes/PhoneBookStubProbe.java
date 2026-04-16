package opendoja.probes;

import com.nttdocomo.system.MailConstants;
import com.nttdocomo.system.PhoneBook;
import com.nttdocomo.system.PhoneBookGroup;
import com.nttdocomo.system.PhoneBookParam;
import com.nttdocomo.lang._XStringSupport;
import com.nttdocomo.ui.IApplication;
import opendoja.host.DesktopLauncher;
import opendoja.host.LaunchConfig;
import opendoja.host.OpenDoJaLaunchArgs;

/**
 * Verifies that a fresh runtime exposes deterministic stub phone-book entries.
 */
public final class PhoneBookStubProbe {
    private PhoneBookStubProbe() {
    }

    public static void main(String[] args) throws Exception {
        String previousSelection = System.getProperty(OpenDoJaLaunchArgs.PHONEBOOK_SELECTION_INDEX);
        ProbeApp app = (ProbeApp) DesktopLauncher.launch(LaunchConfig.builder(ProbeApp.class)
                .parameter("AccessUserInfo", "true")
                .build());
        try {
            System.clearProperty(OpenDoJaLaunchArgs.PHONEBOOK_SELECTION_INDEX);
            PhoneBook selected = PhoneBook.selectEntry();
            check(selected != null, "phone-book select returned null");
            check(selected.getId() == 1, "default headless selected entry id");
            check("Contact 1".equals(_XStringSupport.value(selected.getName(), "name")), "default selected name");
            check("CONTACT 1".equals(_XStringSupport.value(selected.getKana(), "kana")), "default selected kana");
            check("09000000001".equals(_XStringSupport.value(selected.getPhoneNumber(0), "phone")), "default selected phone");
            check("contact1@example.com".equals(_XStringSupport.value(selected.getMailAddress(0, MailConstants.ADDRESS_FULL), "mail")),
                    "default selected mail");
            check("Stub Contacts".equals(_XStringSupport.value(selected.getGroupName(), "group")), "default selected group");
            check(selected.getLocation() != null, "default selected location");

            System.setProperty(OpenDoJaLaunchArgs.PHONEBOOK_SELECTION_INDEX, "1");
            PhoneBook second = PhoneBook.selectEntry();
            check(second != null && second.getId() == 2, "automated selected entry id");
            check("Contact 2".equals(_XStringSupport.value(second.getName(), "name")), "automated selected name");

            System.setProperty(OpenDoJaLaunchArgs.PHONEBOOK_SELECTION_INDEX, "-1");
            check(PhoneBook.selectEntry() == null, "canceled selection should return null");

            for (int id = 1; id <= 3; id++) {
                PhoneBook entry = PhoneBook.getEntry(id);
                check(("Contact " + id).equals(_XStringSupport.value(entry.getName(), "name")), "default contact " + id);
                check(PhoneBookGroup.getEntry(entry.getGroupId()) != null, "default contact group " + id);
            }

            PhoneBookParam param = new PhoneBookParam();
            param.setName("Runtime Contact");
            param.setKana("RUNTIME CONTACT");
            param.addPhoneNumber("09099999999");
            param.addMailAddress("runtime@example.com");
            int[] added = PhoneBook.addEntry(param);
            System.setProperty(OpenDoJaLaunchArgs.PHONEBOOK_SELECTION_INDEX, "3");
            check(PhoneBook.selectEntry().getId() == added[0], "runtime-added entry should remain selectable");

            System.out.println("phonebook-stub-probe-ok");
        } finally {
            restoreProperty(OpenDoJaLaunchArgs.PHONEBOOK_SELECTION_INDEX, previousSelection);
            app.terminate();
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }

    private static void restoreProperty(String key, String value) {
        if (value == null) {
            System.clearProperty(key);
        } else {
            System.setProperty(key, value);
        }
    }

    public static final class ProbeApp extends IApplication {
        @Override
        public void start() {
        }
    }
}
