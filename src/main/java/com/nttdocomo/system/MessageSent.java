package com.nttdocomo.system;

import com.nttdocomo.lang.XString;
import com.nttdocomo.lang._XStringSupport;

import java.time.Instant;
import java.time.ZoneId;

/**
 * Defines a sent or unsent native message-i-appli message.
 */
public final class MessageSent extends Message {
    private final String[] recipients;
    private final XString xRecipient;
    private final XString[] xRecipients;

    MessageSent(int type, int id, Instant timestamp, ZoneId zone, String subject, String body, byte[] data,
                String[] recipients, XString xRecipient, XString[] xRecipients) {
        super(type, id, timestamp, zone, subject, body, data);
        this.recipients = _SystemSupport.copyStrings(recipients);
        this.xRecipient = xRecipient;
        this.xRecipients = _SystemSupport.copyXStrings(xRecipients);
    }

    /**
     * Gets the subject.
     *
     * @return the subject, or an empty string if it is not set
     */
    @Override
    public String getSubject() {
        return subject;
    }

    /**
     * Gets the ordinary-string recipient addresses.
     *
     * @return the recipient addresses, or {@code null}
     */
    public String[] getRecipients() {
        return _SystemSupport.copyStrings(recipients);
    }

    /**
     * Gets the recipient address represented by an {@link XString}.
     *
     * @param part the part of the address to obtain
     * @return the recipient address, or {@code null}
     */
    public XString getXRecipient(int part) {
        return xRecipient == null ? null : _SystemSupport.xAddressPart(_XStringSupport.value(xRecipient, "xRecipient"), part);
    }

    /**
     * Gets the recipient addresses represented by {@link XString} objects.
     *
     * @param part the part of the addresses to obtain
     * @return the recipient addresses, or {@code null}
     */
    public XString[] getXRecipients(int part) {
        if (xRecipients == null) {
            return null;
        }
        String[] addresses = new String[xRecipients.length];
        for (int i = 0; i < xRecipients.length; i++) {
            addresses[i] = _XStringSupport.valueOrNull(xRecipients[i]);
        }
        return _SystemSupport.xAddressParts(addresses, part, false);
    }

    MessageSent copy() {
        return new MessageSent(type, id, timestamp, defaultZone, subject, body, data, recipients, xRecipient, xRecipients);
    }
}
