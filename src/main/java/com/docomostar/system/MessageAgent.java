package com.docomostar.system;

import com.docomostar.lang.XString;

/** STAR entry points for the existing local message store. */
public final class MessageAgent implements MailConstants {
    private MessageAgent() { }
    public static int size(int type, boolean unseen) { return com.nttdocomo.system.MessageAgent.size(type, unseen); }
    public static int[] getIds(int type, boolean unseen) { return com.nttdocomo.system.MessageAgent.getIds(type, unseen); }
    public static int getRemainingBytes(MessageDraft message) {
        return com.nttdocomo.system.MessageAgent.getRemainingBytes(message.delegate);
    }
    public static boolean send(String subject, String[] recipients, String body, byte[] data)
            throws MailException, InterruptedOperationException, StoreException {
        return send(new MessageDraft(subject, recipients, body, data));
    }
    public static boolean send(String subject, XString recipient, String body, byte[] data)
            throws MailException, InterruptedOperationException, StoreException {
        return send(new MessageDraft(subject, recipient, body, data));
    }
    public static boolean send(MessageDraft message) throws MailException, InterruptedOperationException, StoreException {
        try { return com.nttdocomo.system.MessageAgent.send(message.delegate); }
        catch (com.nttdocomo.system.MailException failure) {
            var translated = new MailException(failure.getMessage()); translated.initCause(failure); throw translated;
        } catch (com.nttdocomo.system.InterruptedOperationException failure) {
            var translated = new InterruptedOperationException(failure.getMessage()); translated.initCause(failure); throw translated;
        } catch (com.nttdocomo.system.StoreException failure) { throw StoreException.from(failure); }
    }
    public static void delete(int type, int id) { com.nttdocomo.system.MessageAgent.delete(type, id); }
    public static void setSeen(int id, boolean seen) { com.nttdocomo.system.MessageAgent.setSeen(id, seen); }
    public static boolean isSeen(int id) { return com.nttdocomo.system.MessageAgent.isSeen(id); }
}
