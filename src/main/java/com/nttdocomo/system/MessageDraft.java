package com.nttdocomo.system;

import com.nttdocomo.lang.XString;
import com.nttdocomo.lang._XStringSupport;

import java.util.Arrays;
import java.util.Objects;

/**
 * Defines a message-i-appli message for sending.
 */
public final class MessageDraft implements MailConstants {
    String subject;
    String[] recipients;
    XString xRecipient;
    XString[] xRecipients;
    String body;
    byte[] data;
    int sourceFolderType = -1;
    int sourceMessageId = -1;

    /**
     * Creates a message draft.
     * This is equivalent to {@code MessageDraft(null, null, null, null)}.
     */
    public MessageDraft() {
        this(null, (String[]) null, null, null);
    }

    /**
     * Creates a message draft.
     *
     * @param subject the subject
     * @param addresses the destination addresses
     * @param body the body
     * @param data the message-specific binary data
     */
    public MessageDraft(String subject, String[] addresses, String body, byte[] data) {
        setSubject(subject);
        setRecipients(addresses);
        setBody(body);
        setData(data);
    }

    /**
     * Creates a message draft.
     *
     * @param subject the subject
     * @param address the destination address
     * @param body the body
     * @param data the message-specific binary data
     */
    public MessageDraft(String subject, XString address, String body, byte[] data) {
        setSubject(subject);
        setRecipient(address);
        setBody(body);
        setData(data);
    }

    /**
     * Creates a draft for reply or resend.
     *
     * @param message the source message
     * @param all {@code true} to include all recipients when replying
     */
    public MessageDraft(Message message, boolean all) {
        Objects.requireNonNull(message, "message");
        setSubject(message.getSubject());
        setBody(message.getBody());
        setData(message.getData());
        if (message instanceof MessageReceived received) {
            if (all) {
                XString from = received.getFrom(ADDRESS_FULL);
                XString[] others = received.getRecipients(ADDRESS_FULL);
                if (others == null || others.length == 0) {
                    xRecipients = from == null ? null : new XString[]{from};
                } else {
                    xRecipients = new XString[others.length + (from == null ? 0 : 1)];
                    int index = 0;
                    if (from != null) {
                        xRecipients[index++] = from;
                    }
                    System.arraycopy(others, 0, xRecipients, index, others.length);
                }
            } else {
                xRecipient = received.getFrom(ADDRESS_FULL);
            }
        } else if (message instanceof MessageSent sent) {
            recipients = sent.getRecipients();
            xRecipient = sent.getXRecipient(ADDRESS_FULL);
            xRecipients = sent.getXRecipients(ADDRESS_FULL);
            sourceFolderType = sent.getType();
            sourceMessageId = sent.getId();
        }
    }

    /**
     * Gets the subject.
     *
     * @return the subject, or {@code null}
     */
    public String getSubject() {
        return subject;
    }

    /**
     * Sets the subject.
     *
     * @param subject the subject, or {@code null} to clear it
     */
    public void setSubject(String subject) {
        _SystemSupport.validateMailSubject(subject);
        this.subject = subject;
    }

    /**
     * Gets the ordinary-string recipients.
     *
     * @return the ordinary-string recipients, or {@code null}
     */
    public String[] getRecipients() {
        return _SystemSupport.copyStrings(recipients);
    }

    /**
     * Sets the recipients as ordinary strings.
     *
     * @param addresses the recipient addresses, or {@code null} to clear them
     */
    public void setRecipients(String[] addresses) {
        if (addresses != null) {
            _SystemSupport.validateMessageAddressArray(addresses);
        }
        recipients = addresses == null || addresses.length == 0 ? null : _SystemSupport.copyStrings(addresses);
        xRecipient = null;
        xRecipients = null;
    }

    /**
     * Adds one ordinary-string recipient.
     *
     * @param address the recipient address
     */
    public void addRecipient(String address) {
        Objects.requireNonNull(address, "address");
        if (xRecipient != null || xRecipients != null) {
            throw new IllegalStateException("Non-string recipient state is already set");
        }
        _SystemSupport.validateMailAddress(address);
        int current = recipients == null ? 0 : recipients.length;
        if (current >= _SystemSupport.RECIPIENT_MAX) {
            throw new IllegalStateException("Too many recipients");
        }
        String[] updated = Arrays.copyOf(recipients == null ? new String[0] : recipients, current + 1);
        updated[current] = address;
        recipients = updated;
    }

    /**
     * Gets the single {@link XString} recipient.
     *
     * @return the recipient, or {@code null}
     */
    public XString getXRecipient() {
        return xRecipient;
    }

    /**
     * Sets the single {@link XString} recipient.
     *
     * @param address the recipient, or {@code null} to clear it
     */
    public void setRecipient(XString address) {
        if (address != null) {
            _SystemSupport.validateMailAddress(_XStringSupport.value(address, "address"));
        }
        xRecipient = address;
        recipients = null;
        xRecipients = null;
    }

    /**
     * Gets the {@link XString}-array recipients.
     *
     * @return a shallow copy of the recipient array, or {@code null}
     */
    public XString[] getXRecipients() {
        return _SystemSupport.copyXStrings(xRecipients);
    }

    /**
     * Removes one recipient from the {@link XString}-array state.
     *
     * @param address the recipient object to remove
     */
    public void removeXRecipient(XString address) {
        if (address == null || xRecipients == null) {
            return;
        }
        int match = -1;
        for (int i = 0; i < xRecipients.length; i++) {
            if (xRecipients[i] == address) {
                match = i;
                break;
            }
        }
        if (match < 0) {
            return;
        }
        XString[] updated = new XString[xRecipients.length - 1];
        System.arraycopy(xRecipients, 0, updated, 0, match);
        System.arraycopy(xRecipients, match + 1, updated, match, xRecipients.length - match - 1);
        xRecipients = updated.length == 0 ? null : updated;
    }

    /**
     * Gets the body.
     *
     * @return the body, or {@code null}
     */
    public String getBody() {
        return body;
    }

    /**
     * Sets the body.
     *
     * @param body the body, or {@code null} to clear it
     */
    public void setBody(String body) {
        _SystemSupport.validateMailText(body, true);
        this.body = body;
    }

    /**
     * Gets the message-specific binary data.
     *
     * @return a copy of the binary data, or {@code null}
     */
    public byte[] getData() {
        return _SystemSupport.copyBytes(data);
    }

    /**
     * Sets the message-specific binary data.
     *
     * @param data the binary data, or {@code null} to clear it
     */
    public void setData(byte[] data) {
        _SystemSupport.validateMessageData(data);
        this.data = _SystemSupport.copyBytes(data);
    }
}
