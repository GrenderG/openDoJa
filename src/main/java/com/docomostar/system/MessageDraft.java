package com.docomostar.system;

import com.docomostar.lang.XString;
import com.docomostar.lang._XStringSupport;

/** Managed message draft stored by the shared DoJa message service. */
public final class MessageDraft implements MailConstants {
    final com.nttdocomo.system.MessageDraft delegate;
    public MessageDraft() { delegate = new com.nttdocomo.system.MessageDraft(); }
    public MessageDraft(String subject, String[] recipients, String body, byte[] data) {
        delegate = new com.nttdocomo.system.MessageDraft(subject, recipients, body, data);
    }
    public MessageDraft(String subject, XString recipient, String body, byte[] data) {
        delegate = new com.nttdocomo.system.MessageDraft(subject, _XStringSupport.unwrap(recipient), body, data);
    }
    public String getSubject() { return delegate.getSubject(); }
    public void setSubject(String subject) { delegate.setSubject(subject); }
    public String[] getRecipients() { return delegate.getRecipients(); }
    public void setRecipients(String[] recipients) { delegate.setRecipients(recipients); }
    public void addRecipient(String recipient) { delegate.addRecipient(recipient); }
    public XString getXRecipient() { return _XStringSupport.wrap(delegate.getXRecipient()); }
    public void setRecipient(XString recipient) { delegate.setRecipient(_XStringSupport.unwrap(recipient)); }
    public XString[] getXRecipients() { return _XStringSupport.wrap(delegate.getXRecipients()); }
    public void removeXRecipient(XString recipient) { delegate.removeXRecipient(_XStringSupport.unwrap(recipient)); }
    public String getBody() { return delegate.getBody(); }
    public void setBody(String body) { delegate.setBody(body); }
    public byte[] getData() { return delegate.getData(); }
    public void setData(byte[] data) { delegate.setData(data); }
}
