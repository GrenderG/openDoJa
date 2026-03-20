package com.nttdocomo.util;

import java.util.Calendar;
import java.util.TimeZone;

public class ScheduleDate {
    public static final int ONETIME = 1;
    public static final int DAILY = 2;
    public static final int WEEKLY = 4;
    public static final int MONTHLY = 8;
    public static final int YEARLY = 16;

    private final int type;
    private final Calendar calendar;

    public ScheduleDate(int type) {
        this(type, TimeZone.getDefault());
    }

    public ScheduleDate(int type, TimeZone timeZone) {
        this.type = type;
        this.calendar = Calendar.getInstance(timeZone);
    }

    public int getType() {
        return type;
    }

    public int get(int field) {
        return calendar.get(field);
    }

    public void set(int field, int value) {
        calendar.set(field, value);
    }
}
