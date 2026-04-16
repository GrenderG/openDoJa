package com.nttdocomo.system;

import com.nttdocomo.lang.XString;
import com.nttdocomo.lang._XStringSupport;

import java.time.Instant;
import java.time.ZoneId;
import java.util.TimeZone;

/**
 * Defines mail obtained from the native mailer.
 */
public final class Mail implements MailConstants {
    private final Instant timestamp;
    private final ZoneId zone;
    private final XString subject;
    private final XString from;
    private final int[][] phoneBookIds;
    private final String[] recipients;
    private final boolean[] canReply;

    Mail(Instant timestamp, ZoneId zone, XString subject, XString from, int[][] phoneBookIds,
         String[] recipients, boolean[] canReply) {
        this.timestamp = timestamp;
        this.zone = zone == null ? ZoneId.systemDefault() : zone;
        this.subject = subject == null ? new XString("") : subject;
        this.from = from == null ? new XString("") : from;
        this.phoneBookIds = _SystemSupport.copyIntMatrix(phoneBookIds);
        this.recipients = _SystemSupport.copyStrings(recipients);
        this.canReply = _SystemSupport.copyBooleans(canReply);
    }

    /**
     * Gets the send date and time.
     *
     * @param pattern the format pattern
     * @return the formatted date and time, or {@code null}
     */
    public XString getDateString(String pattern) {
        String value = _SystemSupport.formatInstant(timestamp, zone, pattern);
        return value == null ? null : new XString(value);
    }

    /**
     * Gets the send date and time using the specified time zone.
     *
     * @param pattern the format pattern
     * @param zone the time zone to use, or {@code null}
     * @return the formatted date and time, or {@code null}
     */
    public XString getDateString(String pattern, TimeZone zone) {
        String value = _SystemSupport.formatInstant(timestamp, zone == null ? this.zone : zone.toZoneId(), pattern);
        return value == null ? null : new XString(value);
    }

    /**
     * Gets the subject.
     *
     * @return the subject
     */
    public XString getSubject() {
        return subject;
    }

    /**
     * Gets the sender address.
     *
     * @param part the address part to obtain
     * @return the sender address
     */
    public XString getFrom(int part) {
        return _SystemSupport.xAddressPart(_XStringSupport.value(from, "from"), part);
    }

    /**
     * Gets the sender phone-book IDs.
     *
     * @return a copy of the matching IDs, or {@code null}
     */
    public int[][] getPhoneBookID() {
        return _SystemSupport.copyIntMatrix(phoneBookIds);
    }

    /**
     * Gets recipient addresses other than the owner.
     *
     * @param part the address part to obtain
     * @return the recipient addresses, or {@code null}
     */
    public XString[] getRecipients(int part) {
        return _SystemSupport.xAddressParts(recipients, part, false);
    }

    /**
     * Gets reply availability for the contained addresses.
     *
     * @return a copy of the replyability flags
     */
    public boolean[] canReply() {
        return _SystemSupport.copyBooleans(canReply);
    }
}
