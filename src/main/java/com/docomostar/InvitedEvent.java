package com.docomostar;

/** Parameters supplied by a STAR invitation notification. */
public class InvitedEvent extends StarEventObject {
    private final String launchParameter;

    public InvitedEvent(String parameter) {
        super(STAR_INVITED);
        launchParameter = parameter;
    }
    public String getLaunchParameter() { return launchParameter; }
}
