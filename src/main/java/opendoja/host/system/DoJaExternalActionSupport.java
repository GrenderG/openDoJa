package opendoja.host.system;

import com.nttdocomo.lang.XString;
import com.nttdocomo.lang._XStringSupport;
import com.nttdocomo.ui.IApplication;
import opendoja.host.DoJaEncoding;
import opendoja.host.OpenDoJaLog;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Arrays;

/**
 * Host-side validation and tracing for external launch/call APIs whose native
 * handset behavior cannot be reproduced directly on the desktop host.
 */
public final class DoJaExternalActionSupport {
    private static final int MAX_BROWSER_URL_BYTES = 255;
    private static final int MAX_PHONE_NUMBER_BYTES = 50;
    private static final int MAX_IAPPLI_PARAMS = 16;
    private static final int MAX_IAPPLI_PARAM_BYTES = 20480;

    private static volatile LaunchRequest lastLaunch;
    private static volatile CallRequest lastCall;

    private DoJaExternalActionSupport() {
    }

    public static void launch(int target, String[] args, String sourceUrl) {
        String[] copiedArgs = args == null ? null : args.clone();
        switch (target) {
            case IApplication.LAUNCH_BROWSER, IApplication.LAUNCH_BROWSER_SUSPEND ->
                    validateBrowserArgs(copiedArgs);
            case IApplication.LAUNCH_IAPPLI -> validateIappliArgs(copiedArgs, sourceUrl);
            case IApplication.LAUNCH_VERSIONUP,
                    IApplication.LAUNCH_AS_LAUNCHER,
                    IApplication.LAUNCH_MAILMENU,
                    IApplication.LAUNCH_SCHEDULER,
                    IApplication.LAUNCH_MAIL_RECEIVED,
                    IApplication.LAUNCH_MAIL_SENT,
                    IApplication.LAUNCH_MAIL_UNSENT,
                    IApplication.LAUNCH_MAIL_LAST_INCOMING,
                    IApplication.LAUNCH_DTV -> validateNullableArgs(copiedArgs);
            default -> throw new IllegalArgumentException("Unsupported launch target: " + target);
        }
        lastLaunch = new LaunchRequest(target, copiedArgs);
        OpenDoJaLog.info(DoJaExternalActionSupport.class, () ->
                "Simulated DoJa external launch target=" + target + " args=" + Arrays.toString(copiedArgs));
    }

    public static void call(String telType, XString phoneNumber) {
        if (telType == null || phoneNumber == null) {
            throw new NullPointerException(telType == null ? "telType" : "phoneNumber");
        }
        if (!com.nttdocomo.util.Phone.TEL_AV.equals(telType)) {
            throw new IllegalArgumentException("telType");
        }
        String number = _XStringSupport.value(phoneNumber, "phoneNumber");
        validatePhoneNumber(number);
        lastCall = new CallRequest(telType, number);
        OpenDoJaLog.info(DoJaExternalActionSupport.class, () ->
                "Simulated DoJa phone call type=" + telType + " number=" + number);
    }

    public static void call(String destination) {
        if (destination == null) {
            throw new NullPointerException("destination");
        }
        if (destination.startsWith(com.nttdocomo.util.Phone.TEL_AV)) {
            call(com.nttdocomo.util.Phone.TEL_AV, new XString(destination.substring(com.nttdocomo.util.Phone.TEL_AV.length())));
            return;
        }
        validatePhoneNumber(destination);
        lastCall = new CallRequest(null, destination);
        OpenDoJaLog.info(DoJaExternalActionSupport.class, () ->
                "Simulated DoJa phone call number=" + destination);
    }

    public static LaunchRequest lastLaunch() {
        return lastLaunch;
    }

    public static CallRequest lastCall() {
        return lastCall;
    }

    private static void validateBrowserArgs(String[] args) {
        if (args == null || args.length == 0 || args[0] == null) {
            throw new NullPointerException("args");
        }
        validateHttpUrl(args[0], MAX_BROWSER_URL_BYTES);
    }

    private static void validateIappliArgs(String[] args, String sourceUrl) {
        if (args == null || args.length == 0 || args[0] == null) {
            throw new NullPointerException("args");
        }
        validateHttpUrl(args[0], MAX_BROWSER_URL_BYTES);
        if (sourceUrl != null && args[0].equals(sourceUrl)) {
            throw new IllegalArgumentException("Cannot launch the same i-appli URL");
        }
        if (sourceUrl != null) {
            String sourceHost = hostOf(sourceUrl);
            String targetHost = hostOf(args[0]);
            if (sourceHost != null && targetHost != null && !sourceHost.equalsIgnoreCase(targetHost)) {
                throw new IllegalArgumentException("i-appli linked launch requires the same host");
            }
        }
        if ((args.length - 1) % 2 != 0) {
            throw new IllegalArgumentException("i-appli parameters must be supplied in name/value pairs");
        }
        int pairCount = (args.length - 1) / 2;
        if (pairCount > MAX_IAPPLI_PARAMS) {
            throw new IllegalArgumentException("Too many i-appli parameters");
        }
        int bytes = 0;
        for (int i = 1; i < args.length; i++) {
            if (args[i] == null) {
                throw new NullPointerException("args[" + i + "]");
            }
            bytes += args[i].getBytes(DoJaEncoding.DEFAULT_CHARSET).length;
        }
        if (bytes > MAX_IAPPLI_PARAM_BYTES) {
            throw new IllegalArgumentException("i-appli parameters exceed the supported size");
        }
    }

    private static void validateNullableArgs(String[] args) {
        if (args == null) {
            return;
        }
        for (int i = 0; i < args.length; i++) {
            if (args[i] == null) {
                throw new NullPointerException("args[" + i + "]");
            }
        }
    }

    private static void validateHttpUrl(String url, int maxBytes) {
        if (url.getBytes(DoJaEncoding.DEFAULT_CHARSET).length > maxBytes) {
            throw new IllegalArgumentException("URL exceeds supported size");
        }
        try {
            URI uri = new URI(url);
            String scheme = uri.getScheme();
            if (scheme == null
                    || (!scheme.equalsIgnoreCase("http") && !scheme.equalsIgnoreCase("https"))) {
                throw new IllegalArgumentException("Unsupported URL scheme: " + url);
            }
        } catch (URISyntaxException exception) {
            throw new IllegalArgumentException("Illegal URL: " + url, exception);
        }
    }

    private static void validatePhoneNumber(String phoneNumber) {
        if (phoneNumber.getBytes(DoJaEncoding.DEFAULT_CHARSET).length > MAX_PHONE_NUMBER_BYTES) {
            throw new IllegalArgumentException("Phone number exceeds supported size");
        }
        for (int i = 0; i < phoneNumber.length(); i++) {
            char ch = phoneNumber.charAt(i);
            if (Character.isDigit(ch)
                    || ch == '#'
                    || ch == '*'
                    || ch == '+'
                    || ch == ','
                    || ch == '/'
                    || ch == 'p'
                    || ch == '('
                    || ch == ')'
                    || ch == '-'
                    || ch == '.'
                    || ch == ' ') {
                continue;
            }
            throw new IllegalArgumentException("Illegal phone number: " + phoneNumber);
        }
    }

    private static String hostOf(String url) {
        try {
            return new URI(url).getHost();
        } catch (URISyntaxException exception) {
            throw new IllegalArgumentException("Illegal URL: " + url, exception);
        }
    }

    public record LaunchRequest(int target, String[] args) {
    }

    public record CallRequest(String telType, String phoneNumber) {
    }
}
