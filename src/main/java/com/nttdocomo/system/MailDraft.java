package com.nttdocomo.system;

import com.nttdocomo.lang.XString;
import com.nttdocomo.lang._XStringSupport;
import java.util.Arrays;
import java.util.Objects;

/**
 * Defines mail to be sent or saved.
 */
public class MailDraft implements MailConstants {
    String subject;
    String[] recipients;
    XString xRecipient;
    String body;

    /**
     * Creates a mail draft for sending.
     * This is equivalent to {@code MailDraft(null, null, null)}.
     */
    public MailDraft() {
        this(null, (String[]) null, null);
    }

    /**
     * Creates a mail draft for sending.
     *
     * @param subject the subject
     * @param addresses the destination addresses
     * @param body the body text
     */
    public MailDraft(String subject, String[] addresses, String body) {
        setSubject(subject);
        setRecipients(addresses);
        setBody(body);
    }

    /**
     * Creates a mail draft for sending.
     *
     * @param subject the subject
     * @param address the destination address
     * @param body the body text
     */
    public MailDraft(String subject, XString address, String body) {
        setSubject(subject);
        setRecipient(address);
        setBody(body);
    }

    /**
     * Gets the subject.
     *
     * @return the subject, or {@code null} if it is not set
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
     * Gets the addresses specified as ordinary strings.
     *
     * @return the ordinary-string addresses, or {@code null}
     */
    public String[] getRecipients() {
        return _SystemSupport.copyStrings(recipients);
    }

    /**
     * Sets the addresses as ordinary strings.
     *
     * @param addresses the addresses, or {@code null} to clear them
     */
    public void setRecipients(String[] addresses) {
        _SystemSupport.validateMailAddressArray(addresses);
        recipients = addresses == null || addresses.length == 0 ? null : _SystemSupport.copyStrings(addresses);
        xRecipient = null;
    }

    /**
     * Adds one ordinary-string address.
     *
     * @param address the address to add
     */
    public void addRecipient(String address) {
        if (xRecipient != null) {
            throw new IllegalStateException("An XString recipient is already set");
        }
        Objects.requireNonNull(address, "address");
        _SystemSupport.validateMailAddress(address);
        int current = recipients == null ? 0 : recipients.length;
        if (current >= _SystemSupport.RECIPIENT_MAX) {
            throw new IllegalStateException("Too many addresses");
        }
        String[] updated = Arrays.copyOf(recipients == null ? new String[0] : recipients, current + 1);
        updated[current] = address;
        recipients = updated;
    }

    /**
     * Gets the address specified as an {@link XString}.
     *
     * @return the address, or {@code null}
     */
    public XString getXRecipient() {
        return xRecipient;
    }

    /**
     * Sets the address as an {@link XString}.
     *
     * @param address the address, or {@code null} to clear it
     */
    public void setRecipient(XString address) {
        if (address != null) {
            _SystemSupport.validateMailAddress(_XStringSupport.value(address, "address"));
        }
        this.xRecipient = address;
        this.recipients = null;
    }

    /**
     * Gets the body text.
     *
     * @return the body, or {@code null}
     */
    public String getBody() {
        return body;
    }

    /**
     * Sets the body text.
     *
     * @param body the body, or {@code null} to clear it
     */
    public void setBody(String body) {
        _SystemSupport.validateMailText(body, true);
        this.body = body;
    }
}
