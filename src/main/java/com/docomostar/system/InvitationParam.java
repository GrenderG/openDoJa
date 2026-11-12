package com.docomostar.system;

import com.docomostar.lang.XString;

/** Parameters for a STAR launch invitation; recipient and launch text are separate values. */
public final class InvitationParam {
    private String launcherAppName;
    private XString recipient;
    private int expiration;
    private String launchParameter;
    private int downloadBehavior;

    public InvitationParam(String launcherAppName, XString recipient,
                           int expiration, String launchParameter, int downloadBehavior) {
        this.launcherAppName = launcherAppName;
        this.recipient = recipient;
        this.expiration = expiration;
        this.launchParameter = launchParameter;
        this.downloadBehavior = downloadBehavior;
    }

    public int getDownloadBehavior() {
        return downloadBehavior;
    }

    public int getExpiration() {
        return expiration;
    }

    public String getLauncherAppName() {
        return launcherAppName;
    }

    public String getLaunchParameter() {
        return launchParameter;
    }

    public String getExpirationText() {
        // Retain the old host accessor for the fourth constructor argument.
        return launchParameter;
    }

    public XString getRecipient() { return recipient; }
    public void setLauncherAppName(String launcherAppName) { this.launcherAppName = launcherAppName; }
    public void setRecipient(XString recipient) { this.recipient = recipient; }
    public void setExpiration(int expiration) { this.expiration = expiration; }
    public void setLaunchParameter(String launchParameter) { this.launchParameter = launchParameter; }
    public void setDownloadBehavior(int downloadBehavior) { this.downloadBehavior = downloadBehavior; }
}
