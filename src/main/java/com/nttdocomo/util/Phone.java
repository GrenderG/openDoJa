package com.nttdocomo.util;

import com.nttdocomo.lang.XString;
import opendoja.host.DoJaRuntime;
import opendoja.host.OpenDoJaIdentity;
import opendoja.host.system.DoJaExternalActionSupport;

/**
 * Provides the means to access the handset-native calling functions.
 * From DoJa-2.1 onward, it also provides the means to obtain handset and UIM
 * individual-identification numbers.
 *
 * <p>Introduced in DoJa-2.0.</p>
 */
public final class Phone {
    /**
     * String that represents the handset individual-identification number
     * (= {@code "terminal-id"}).
     */
    public static final String TERMINAL_ID = "terminal-id";
    /**
     * String that represents the UIM individual-identification number
     * (= {@code "user-id"}).
     */
    public static final String USER_ID = "user-id";
    /**
     * String that represents a videophone call (= {@code "tel-av:"}).
     * Specify this as the first argument to
     * {@link #call(String, XString)} for videophone calling.
     */
    public static final String TEL_AV = "tel-av:";
    /**
     * String that represents the UIM version number
     * (= {@code "uim-version"}).
     */
    public static final String UIM_VERSION = "uim-version";

    /**
     * Creates a {@code Phone} instance.
     *
     * <p>The original DoJa API exposes a public no-arg constructor, and some
     * titles instantiate the class before using only its static methods. Keep
     * the constructor public for binary compatibility.</p>
     */
    public Phone() {
    }

    /**
     * Calls the voice-calling, PTT outgoing-calling, or videophone-calling
     * function.
     * To call the videophone-calling function, specify a string beginning with
     * {@code "tel-av:"}.
     *
     * @param destination the destination telephone number
     */
    public static void call(String destination) {
        DoJaExternalActionSupport.call(destination);
    }

    /**
     * Calls the voice-calling, PTT outgoing-calling, or videophone-calling
     * function.
     * This method is the same as {@link #call(String)} except that the
     * telephone number is specified as an {@link XString}.
     *
     * @param destination the telephone number as an {@link XString}
     */
    public static void call(XString destination) {
        if (destination == null) {
            throw new NullPointerException("destination");
        }
        DoJaExternalActionSupport.call(destination.toString());
    }

    /**
     * Calls the voice-calling, PTT outgoing-calling, or videophone-calling
     * function.
     * This method is the same as {@link #call(String)} except that
     * {@code telType} must be {@code "tel-av:"} and the telephone number is
     * specified as an {@link XString}.
     *
     * @param destination the string {@code "tel-av:"} that indicates a
     *                    videophone call
     * @param subAddress the telephone number as an {@link XString}
     */
    public static void call(String destination, XString subAddress) {
        DoJaExternalActionSupport.call(destination, subAddress);
    }

    /**
     * Gets the property value corresponding to the specified key.
     * The supported property keys are {@code "terminal-id"} and
     * {@code "user-id"}. From DoJa-4.1 onward, {@code "uim-version"} is also
     * supported. If an invalid key is specified, {@code null} is returned.
     *
     * @param key the property key
     * @return the property value as a string
     * @throws NullPointerException if {@code key} is {@code null}
     * @throws IllegalArgumentException if {@code key} is the empty string
     */
    public static String getProperty(String key) {
        if (key == null) {
            throw new NullPointerException("key");
        }
        if (key.isEmpty()) {
            throw new IllegalArgumentException("key");
        }
        DoJaRuntime runtime = DoJaRuntime.current();
        if (TERMINAL_ID.equals(key)) {
            return OpenDoJaIdentity.terminalId();
        }
        if (USER_ID.equals(key)) {
            return OpenDoJaIdentity.userId();
        }
        if (UIM_VERSION.equals(key)) {
            return "2";
        }
        return runtime == null ? null : runtime.parameters().get(key);
    }
}
