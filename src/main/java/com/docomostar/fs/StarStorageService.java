package com.docomostar.fs;

import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

public class StarStorageService {
    public static final int SHARE_APPLICATION = 8;
    public static final int SHARE_CONTENTS_PROVIDER = 16;
    private static final Map<com.nttdocomo.fs.DoJaAccessToken, StarAccessToken> TOKENS = new ConcurrentHashMap<>();

    private StarStorageService() { }

    public static StarAccessToken getAccessToken(int access, int share) {
        // Check the current launch's permission on every request, including cached tokens.
        var token = com.nttdocomo.fs.DoJaStorageService.getAccessToken(access, share);
        return TOKENS.computeIfAbsent(token, StarAccessToken::new);
    }
}
