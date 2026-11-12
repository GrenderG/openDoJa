package com.docomostar.media;

/** STAR resource ownership; implementations delegate preparation to the DoJa host. */
public interface MediaResource {
    void use() throws com.docomostar.io.ConnectionException;
    void use(MediaResource overwritten, boolean useOnce) throws com.docomostar.io.ConnectionException;
    void unuse();
    void dispose();
    String getProperty(String key);
    void setProperty(String key, String value);
    boolean isRedistributable();
    boolean setRedistributable(boolean redistributable);
}
