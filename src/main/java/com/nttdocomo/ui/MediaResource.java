package com.nttdocomo.ui;

import com.nttdocomo.io.ConnectionException;

/**
 * Defines the media Resource type.
 */
public interface MediaResource {
    int UNUSE = 0;
    int USE = 1;
    int DISPOSE = 2;
    int JK_MEDIARESOURCE_REDISTRIBUTABLE = 0;
    int JK_MEDIARESOURCE_NOT_REDISTRIBUTABLE = 1;
    int JK_MEDIARESOURCE_REDISTRIBUTABLE_CANNOT_SET_EXCLUSIVE = -1;
    int JK_MEDIARESOURCE_NOT_REDISTRIBUTABLE_CANNOT_SET_EXCLUSIVE = -2;
    int JK_MEDIARESOURCE_NOT_SET = -3;
    String X_DCM_MOVE = "X-DCM-MOVE";
    String X_DCM_ADDITIONAL = "X-DCM-ADDITIONAL";

    void use() throws ConnectionException;

    void use(MediaResource other, boolean exclusive) throws ConnectionException;

    void unuse();

    void dispose();

    String getProperty(String key);

    void setProperty(String key, String value);

    boolean isRedistributable();

    boolean setRedistributable(boolean redistributable);
}
