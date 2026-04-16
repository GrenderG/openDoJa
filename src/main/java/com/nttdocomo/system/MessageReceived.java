package com.nttdocomo.system;

import com.nttdocomo.lang.XString;
import com.nttdocomo.lang._XStringSupport;

import java.time.Instant;
import java.time.ZoneId;

/**
 * Defines a received native message-i-appli message.
 */
public final class MessageReceived extends Message {
    private final XString from;
    private final int[][] phoneBookIds;
    private final String[] recipients;
    private final boolean[] canReply;
    private boolean seen;

    MessageReceived(int type, int id, Instant timestamp, ZoneId zone, String subject, String body, byte[] data,
                    XString from, int[][] phoneBookIds, String[] recipients, boolean seen, boolean[] canReply) {
        super(type, id, timestamp, zone, subject, body, data);
        this.from = from;
        this.phoneBookIds = _SystemSupport.copyIntMatrix(phoneBookIds);
        this.recipients = _SystemSupport.copyStrings(recipients);
        this.seen = seen;
        this.canReply = _SystemSupport.copyBooleans(canReply);
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
     * Gets the sender address.
     *
     * @param part the part of the address to obtain
     * @return the sender address, or {@code null}
     */
    public XString getFrom(int part) {
        return from == null ? null : _SystemSupport.xAddressPart(_XStringSupport.value(from, "from"), part);
    }

    /**
     * Gets the phone-book IDs that match the sender address.
     *
     * @return a copy of the matching IDs, or {@code null}
     */
    public int[][] getPhoneBookID() {
        return _SystemSupport.copyIntMatrix(phoneBookIds);
    }

    /**
     * Gets recipient addresses other than the terminal owner.
     *
     * @param part the part of the address to obtain
     * @return the recipient addresses, or {@code null}
     */
    public XString[] getRecipients(int part) {
        return _SystemSupport.xAddressParts(recipients, part, false);
    }

    /**
     * Gets whether the message has been seen.
     *
     * @return {@code true} if the message is seen
     */
    public boolean isSeen() {
        return seen;
    }

    void setSeen(boolean seen) {
        this.seen = seen;
    }

    /**
     * Gets reply availability for the addresses contained in this message.
     *
     * @return a copy of the replyability flags
     */
    public boolean[] canReply() {
        return _SystemSupport.copyBooleans(canReply);
    }

    MessageReceived copy() {
        return new MessageReceived(type, id, timestamp, defaultZone, subject, body, data, from,
                phoneBookIds, recipients, seen, canReply);
    }
}
