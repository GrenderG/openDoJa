package com.docomostar.fs;

public class StarAccessToken implements AccessToken {
    public static final int ACCESS_UIM = 1;
    public static final int ACCESS_PLATFORM = 2;
    public static final int ACCESS_SERIES = 4;
    private final com.nttdocomo.fs.DoJaAccessToken delegate;

    StarAccessToken(com.nttdocomo.fs.DoJaAccessToken delegate) { this.delegate = delegate; }
    public int getAccess() { return delegate.getAccess(); }
    public int getShare() { return delegate.getShare(); }

    /** Internal bridge: preserve the host's validation of system-issued tokens. */
    public static com.nttdocomo.fs.DoJaAccessToken unwrap(AccessToken token) {
        if (token == null) throw new NullPointerException("accessToken");
        if (token.getClass() != StarAccessToken.class) throw new IllegalArgumentException("Unsupported access token");
        return ((StarAccessToken) token).delegate;
    }
}
