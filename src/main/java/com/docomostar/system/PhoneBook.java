package com.docomostar.system;

import com.docomostar.lang.XString;
import com.docomostar.lang._XStringSupport;

/** STAR view of the shared phone book, including its permission checks and selection UI. */
public final class PhoneBook implements MailConstants, PhoneBookConstants {
    private final com.nttdocomo.system.PhoneBook delegate;

    private PhoneBook(com.nttdocomo.system.PhoneBook delegate) {
        this.delegate = delegate;
    }

    public static PhoneBook selectEntry() throws InterruptedOperationException {
        try {
            var selected = com.nttdocomo.system.PhoneBook.selectEntry();
            return selected == null ? null : new PhoneBook(selected);
        } catch (com.nttdocomo.system.InterruptedOperationException failure) {
            InterruptedOperationException translated = new InterruptedOperationException(failure.getMessage());
            translated.initCause(failure);
            throw translated;
        }
    }

    public static PhoneBook getEntry(int id) throws StoreException {
        try {
            return new PhoneBook(com.nttdocomo.system.PhoneBook.getEntry(id));
        } catch (com.nttdocomo.system.StoreException failure) {
            throw StoreException.from(failure);
        }
    }

    public int getId() { return delegate.getId(); }
    public XString getName() { return _XStringSupport.wrap(delegate.getName()); }
    public XString getName(int part) { return _XStringSupport.wrap(delegate.getName(part)); }
    public XString getKana() { return _XStringSupport.wrap(delegate.getKana()); }
    public XString getKana(int part) { return _XStringSupport.wrap(delegate.getKana(part)); }
    public XString[] getPhoneNumbers() { return _XStringSupport.wrap(delegate.getPhoneNumbers()); }
    public XString getPhoneNumber(int index) { return _XStringSupport.wrap(delegate.getPhoneNumber(index)); }
    public XString[] getMailAddresses(int part) { return _XStringSupport.wrap(delegate.getMailAddresses(part)); }
    public XString getMailAddress(int index, int part) { return _XStringSupport.wrap(delegate.getMailAddress(index, part)); }
    public XString getGroupName() { return _XStringSupport.wrap(delegate.getGroupName()); }
    public int getGroupId() { return delegate.getGroupId(); }
}
