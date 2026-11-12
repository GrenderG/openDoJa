package com.docomostar.fs.sd;

import com.docomostar.fs.EncryptionAttribute;

public class SDBindingEncryptionAttribute extends EncryptionAttribute {
    private final com.nttdocomo.fs.sd.SDBindingEncryptionAttribute delegate =
            new com.nttdocomo.fs.sd.SDBindingEncryptionAttribute();

    public void setBlockSize(int blockSize) { delegate.setBlockSize(blockSize); }
}
