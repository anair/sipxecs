/*
 *
 *
 * Copyright (C) 2007 Pingtel Corp., certain elements licensed under a Contributor Agreement.
 * Contributors retain copyright to elements licensed under a Contributor Agreement.
 * Licensed to the User under the LGPL license.
 *
 * $
 */
package org.sipfoundry.sipxconfig.device;

import java.util.Calendar;
import java.util.TimeZone;

import org.apache.commons.lang.time.DateUtils;
import org.sipfoundry.sipxconfig.common.BeanWithId;

/**
 * Only supports relative days for now: you can specify which day of which month the DST is
 * changed, but you cannot specify which day of the month it's going to happen.
 */
public class DeviceTimeZone extends BeanWithId {

    public static final String DST_US = "US";

    public static final String DST_EU = "EU";

    public static final int DST_LASTWEEK = -1;

    private static final int SECONDS_PER_HOUR = 3600;

    private static final int MINUTES_PER_HOUR = 60;

    private int m_offset;
    private int m_dstSavings;
    private boolean m_useDaylight;

    private int m_startMonth;
    private int m_startWeek;
    private int m_startDayOfWeek;
    private int m_startTime;

    private int m_stopMonth;
    private int m_stopDayOfWeek;
    private int m_stopWeek;
    private int m_stopTime;

    public DeviceTimeZone() {
    }

    public DeviceTimeZone(TimeZone tz) {
        String tzn = tz.getID();
        m_useDaylight = tz.useDaylightTime();
        m_offset = tz.getRawOffset() / (int) DateUtils.MILLIS_PER_MINUTE;

        m_dstSavings = tz.getDSTSavings() / (int) DateUtils.MILLIS_PER_MINUTE;

        // Until there is a setting for DST rule, it must be guessed here based on the timezone
        // name
        //
        // XCF-977 - Doing string compares on ID is not predicable! On my gentoo system, I get
        // US/Eastern but on a FC4 machine I get America/New_York. Both running java1.5.0.06
        // We'll wait for XCF-874 to address this properly. Until then, do not break whatever
        // seems to work for some systems in Europe, but default everyone else to US. Otherwise
        // the default of zero is not very helpful
        if (tzn.matches("^Europe/.*")) {
            setDstParameters(DST_EU);
        } else {
            setDstParameters(DST_US);
        }
    }

    public boolean getUseDaylight() {
        return m_useDaylight;
    }

    public void setUseDaylight(boolean bool) {
        m_useDaylight = bool;
    }

    private void setDstParameters(String dstRule) {
        if (m_useDaylight) {
            if (dstRule.equals(DST_US)) {
                m_startDayOfWeek = Calendar.SUNDAY;
                m_startTime = 2 * MINUTES_PER_HOUR;
                m_stopDayOfWeek = Calendar.SUNDAY;
                m_stopTime = 2 * MINUTES_PER_HOUR;

                m_startMonth = Calendar.MARCH;
                m_startWeek = 2;
                m_stopMonth = Calendar.NOVEMBER;
                m_stopWeek = 1;
            } else if (dstRule.equals(DST_EU)) {
                m_startDayOfWeek = Calendar.SUNDAY;
                m_startMonth = Calendar.MARCH;
                m_startTime = MINUTES_PER_HOUR + getOffset();
                m_startWeek = DST_LASTWEEK;

                m_stopDayOfWeek = Calendar.SUNDAY;
                m_stopMonth = Calendar.OCTOBER;
                m_stopTime = MINUTES_PER_HOUR + getOffset() + getDstSavings();
                m_stopWeek = DST_LASTWEEK;
            }
        } else {
            // if we don't use DST, set the parameters to 0 to not confuse the phones
            m_startMonth = 0;
            m_startWeek = 0;
            m_startDayOfWeek = 0;
            m_startTime = 0;

            m_stopMonth = 0;
            m_stopDayOfWeek = 0;
            m_stopWeek = 0;
            m_stopTime = 0;
        }
    }

    public int getOffset() {
        return m_offset;
    }

    public void setOffset(int offsetInMinutes) {
        m_offset = offsetInMinutes;
    }

    public int getOffsetInHours() {
        return m_offset / MINUTES_PER_HOUR;
    }

    public int getOffsetInSeconds() {
        return m_offset * MINUTES_PER_HOUR;
    }

    public int getDstSavings() {
        return m_dstSavings;
    }

    public int getDstSavingsInMilliseconds() {
        return m_dstSavings * (int) DateUtils.MILLIS_PER_MINUTE;
    }

    public int getDstSavingsInSeconds() {
        return m_dstSavings * 60;
    }

    public void setDstSavings(int dstSavings) {
        m_dstSavings = dstSavings;
    }

    public int getOffsetWithDst() {
        return m_offset + m_dstSavings;
    }

    public void setStartDayOfWeek(int startDayOfWeek) {
        m_startDayOfWeek = startDayOfWeek;
    }

    public int getStartDayOfWeek() {
        return m_startDayOfWeek;
    }

    public void setStartMonth(int startMonth) {
        m_startMonth = startMonth;
    }

    public int getStartMonth() {
        return m_startMonth;
    }

    public void setStartTime(int startTime) {
        m_startTime = startTime;
    }

    public int getStartTime() {
        return m_startTime;
    }

    public int getStartTimeInHours() {
        return m_startTime / SECONDS_PER_HOUR;
    }

    public void setStartWeek(int startWeek) {
        m_startWeek = startWeek;
    }

    public int getStartWeek() {
        return m_startWeek;
    }

    public void setStopDayOfWeek(int stopDayOfWeek) {
        m_stopDayOfWeek = stopDayOfWeek;
    }

    public int getStopDayOfWeek() {
        return m_stopDayOfWeek;
    }

    public void setStopMonth(int stopMonth) {
        m_stopMonth = stopMonth;
    }

    public int getStopMonth() {
        return m_stopMonth;
    }

    public void setStopTime(int stopTime) {
        m_stopTime = stopTime;
    }

    public int getStopTime() {
        return m_stopTime;
    }

    public int getStopTimeInHours() {
        return m_stopTime / SECONDS_PER_HOUR;
    }

    public void setStopWeek(int stopWeek) {
        m_stopWeek = stopWeek;
    }

    public int getStopWeek() {
        return m_stopWeek;
    }
}
