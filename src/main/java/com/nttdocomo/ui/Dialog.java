package com.nttdocomo.ui;

import javax.swing.JOptionPane;

public final class Dialog extends Frame {
    public static final int DIALOG_INFO = 0;
    public static final int DIALOG_WARNING = 1;
    public static final int DIALOG_ERROR = 2;
    public static final int DIALOG_YESNO = 3;
    public static final int DIALOG_YESNOCANCEL = 4;
    public static final int BUTTON_OK = 1;
    public static final int BUTTON_CANCEL = 2;
    public static final int BUTTON_YES = 4;
    public static final int BUTTON_NO = 8;

    private final int type;
    private String title;
    private String text = "";
    private Font font = Font.getDefaultFont();

    public Dialog(int type, String title) {
        this.type = type;
        this.title = title;
    }

    @Override
    public void setBackground(int color) {
        super.setBackground(color);
    }

    public void setFont(Font font) {
        this.font = font == null ? Font.getDefaultFont() : font;
    }

    public void setText(String text) {
        this.text = text == null ? "" : text;
    }

    public int show() {
        if (type == DIALOG_YESNO) {
            int result = JOptionPane.showConfirmDialog(null, text, title, JOptionPane.YES_NO_OPTION);
            return result == JOptionPane.YES_OPTION ? BUTTON_YES : BUTTON_NO;
        }
        if (type == DIALOG_YESNOCANCEL) {
            int result = JOptionPane.showConfirmDialog(null, text, title, JOptionPane.YES_NO_CANCEL_OPTION);
            if (result == JOptionPane.YES_OPTION) {
                return BUTTON_YES;
            }
            if (result == JOptionPane.NO_OPTION) {
                return BUTTON_NO;
            }
            return BUTTON_CANCEL;
        }
        JOptionPane.showMessageDialog(null, text, title, switch (type) {
            case DIALOG_WARNING -> JOptionPane.WARNING_MESSAGE;
            case DIALOG_ERROR -> JOptionPane.ERROR_MESSAGE;
            default -> JOptionPane.INFORMATION_MESSAGE;
        });
        return BUTTON_OK;
    }

    @Override
    public void setSoftLabel(int key, String caption) {
        super.setSoftLabel(key, caption);
    }

    @Override
    public void setSoftLabelVisible(boolean visible) {
        super.setSoftLabelVisible(visible);
    }
}
