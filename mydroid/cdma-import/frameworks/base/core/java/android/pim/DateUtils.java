/*
 * Copyright (C) 2006 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package android.pim;

import android.content.Context;
import android.content.res.Configuration;
import android.content.res.Resources;

import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.TimeZone;

import com.android.internal.R;

/**
 */
public class DateUtils
{
    private static final String TAG = "DateUtils";

    private static final Object sLock = new Object();
    private static final int[] sDaysLong = new int[] {
            com.android.internal.R.string.day_of_week_long_sunday,
            com.android.internal.R.string.day_of_week_long_monday,
            com.android.internal.R.string.day_of_week_long_tuesday,
            com.android.internal.R.string.day_of_week_long_wednesday,
            com.android.internal.R.string.day_of_week_long_thursday,
            com.android.internal.R.string.day_of_week_long_friday,
            com.android.internal.R.string.day_of_week_long_saturday,
        };
    private static final int[] sDaysMedium = new int[] {
            com.android.internal.R.string.day_of_week_medium_sunday,
            com.android.internal.R.string.day_of_week_medium_monday,
            com.android.internal.R.string.day_of_week_medium_tuesday,
            com.android.internal.R.string.day_of_week_medium_wednesday,
            com.android.internal.R.string.day_of_week_medium_thursday,
            com.android.internal.R.string.day_of_week_medium_friday,
            com.android.internal.R.string.day_of_week_medium_saturday,
        };
    private static final int[] sDaysShort = new int[] {
            com.android.internal.R.string.day_of_week_short_sunday,
            com.android.internal.R.string.day_of_week_short_monday,
            com.android.internal.R.string.day_of_week_short_tuesday,
            com.android.internal.R.string.day_of_week_short_wednesday,
            com.android.internal.R.string.day_of_week_short_thursday,
            com.android.internal.R.string.day_of_week_short_friday,
            com.android.internal.R.string.day_of_week_short_saturday,
        };
    private static final int[] sDaysShorter = new int[] {
            com.android.internal.R.string.day_of_week_shorter_sunday,
            com.android.internal.R.string.day_of_week_shorter_monday,
            com.android.internal.R.string.day_of_week_shorter_tuesday,
            com.android.internal.R.string.day_of_week_shorter_wednesday,
            com.android.internal.R.string.day_of_week_shorter_thursday,
            com.android.internal.R.string.day_of_week_shorter_friday,
            com.android.internal.R.string.day_of_week_shorter_saturday,
        };
    private static final int[] sDaysShortest = new int[] {
            com.android.internal.R.string.day_of_week_shortest_sunday,
            com.android.internal.R.string.day_of_week_shortest_monday,
            com.android.internal.R.string.day_of_week_shortest_tuesday,
            com.android.internal.R.string.day_of_week_shortest_wednesday,
            com.android.internal.R.string.day_of_week_shortest_thursday,
            com.android.internal.R.string.day_of_week_shortest_friday,
            com.android.internal.R.string.day_of_week_shortest_saturday,
        };
    private static final int[] sMonthsLong = new int [] {
            com.android.internal.R.string.month_long_january,
            com.android.internal.R.string.month_long_february,
            com.android.internal.R.string.month_long_march,
            com.android.internal.R.string.month_long_april,
            com.android.internal.R.string.month_long_may,
            com.android.internal.R.string.month_long_june,
            com.android.internal.R.string.month_long_july,
            com.android.internal.R.string.month_long_august,
            com.android.internal.R.string.month_long_september,
            com.android.internal.R.string.month_long_october,
            com.android.internal.R.string.month_long_november,
            com.android.internal.R.string.month_long_december,
        };
    private static final int[] sMonthsMedium = new int [] {
            com.android.internal.R.string.month_medium_january,
            com.android.internal.R.string.month_medium_february,
            com.android.internal.R.string.month_medium_march,
            com.android.internal.R.string.month_medium_april,
            com.android.internal.R.string.month_medium_may,
            com.android.internal.R.string.month_medium_june,
            com.android.internal.R.string.month_medium_july,
            com.android.internal.R.string.month_medium_august,
            com.android.internal.R.string.month_medium_september,
            com.android.internal.R.string.month_medium_october,
            com.android.internal.R.string.month_medium_november,
            com.android.internal.R.string.month_medium_december,
        };
    private static final int[] sMonthsShortest = new int [] {
            com.android.internal.R.string.month_shortest_january,
            com.android.internal.R.string.month_shortest_february,
            com.android.internal.R.string.month_shortest_march,
            com.android.internal.R.string.month_shortest_april,
            com.android.internal.R.string.month_shortest_may,
            com.android.internal.R.string.month_shortest_june,
            com.android.internal.R.string.month_shortest_july,
            com.android.internal.R.string.month_shortest_august,
            com.android.internal.R.string.month_shortest_september,
            com.android.internal.R.string.month_shortest_october,
            com.android.internal.R.string.month_shortest_november,
            com.android.internal.R.string.month_shortest_december,
        };
    private static final int[] sAmPm = new int[] {
            com.android.internal.R.string.am,
            com.android.internal.R.string.pm,
        };
    private static int sFirstDay;
    private static Configuration sLastConfig;
    private static String sStatusDateFormat;
    private static String sStatusTimeFormat;
    private static String sElapsedFormatMMSS;
    private static String sElapsedFormatHMMSS;
    
    private static final String FAST_FORMAT_HMMSS = "%1$d:%2$02d:%3$02d";
    private static final String FAST_FORMAT_MMSS = "%1$02d:%2$02d";
    private static final char TIME_PADDING = '0';
    private static final char TIME_SEPARATOR = ':';
    

    public static final long SECOND_IN_MILLIS = 1000;
    public static final long MINUTE_IN_MILLIS = SECOND_IN_MILLIS * 60;
    public static final long HOUR_IN_MILLIS = MINUTE_IN_MILLIS * 60;
    public static final long DAY_IN_MILLIS = HOUR_IN_MILLIS * 24;
    public static final long WEEK_IN_MILLIS = DAY_IN_MILLIS * 7;
    public static final long YEAR_IN_MILLIS = WEEK_IN_MILLIS * 52;

    // The following FORMAT_* symbols are used for specifying the format of
    // dates and times in the formatDateRange method.
    public static final int FORMAT_SHOW_TIME      = 0x00001;
    public static final int FORMAT_SHOW_WEEKDAY   = 0x00002;
    public static final int FORMAT_SHOW_YEAR      = 0x00004;
    public static final int FORMAT_NO_YEAR        = 0x00008;
    public static final int FORMAT_SHOW_DATE      = 0x00010;
    public static final int FORMAT_NO_MONTH_DAY   = 0x00020;
    public static final int FORMAT_24HOUR         = 0x00040;
    public static final int FORMAT_CAP_AMPM       = 0x00080;
    public static final int FORMAT_NO_NOON        = 0x00100;
    public static final int FORMAT_CAP_NOON       = 0x00200;
    public static final int FORMAT_NO_MIDNIGHT    = 0x00400;
    public static final int FORMAT_CAP_MIDNIGHT   = 0x00800;
    public static final int FORMAT_UTC            = 0x01000;
    public static final int FORMAT_ABBREV_TIME    = 0x02000;
    public static final int FORMAT_ABBREV_WEEKDAY = 0x04000;
    public static final int FORMAT_ABBREV_MONTH   = 0x08000;
    public static final int FORMAT_NUMERIC_DATE   = 0x10000;
    public static final int FORMAT_ABBREV_ALL     = (FORMAT_ABBREV_TIME
            | FORMAT_ABBREV_WEEKDAY | FORMAT_ABBREV_MONTH);
    public static final int FORMAT_CAP_NOON_MIDNIGHT = (FORMAT_CAP_NOON | FORMAT_CAP_MIDNIGHT);
    public static final int FORMAT_NO_NOON_MIDNIGHT = (FORMAT_NO_NOON | FORMAT_NO_MIDNIGHT);

    // Date and time format strings that are constant and don't need to be
    // translated.
    public static final String HOUR_MINUTE_24 = "%H:%M";
    public static final String HOUR_MINUTE_AMPM = "%-l:%M%P";
    public static final String HOUR_MINUTE_CAP_AMPM = "%-l:%M%p";
    public static final String HOUR_AMPM = "%-l%P";
    public static final String HOUR_CAP_AMPM = "%-l%p";
    public static final String MONTH_FORMAT = "%B";
    public static final String ABBREV_MONTH_FORMAT = "%b";
    public static final String NUMERIC_MONTH_FORMAT = "%m";
    public static final String MONTH_DAY_FORMAT = "%-d";
    public static final String YEAR_FORMAT = "%Y";
    public static final String YEAR_FORMAT_TWO_DIGITS = "%g";
    public static final String WEEKDAY_FORMAT = "%A";
    public static final String ABBREV_WEEKDAY_FORMAT = "%a";
    
    // This table is used to lookup the resource string id of a format string
    // used for formatting a start and end date that fall in the same year.
    // The index is constructed from a bit-wise OR of the boolean values:
    // {showTime, showYear, showWeekDay}.  For example, if showYear and
    // showWeekDay are both true, then the index would be 3.
    public static final int sameYearTable[] = {
        com.android.internal.R.string.same_year_md1_md2,
        com.android.internal.R.string.same_year_wday1_md1_wday2_md2,
        com.android.internal.R.string.same_year_mdy1_mdy2,
        com.android.internal.R.string.same_year_wday1_mdy1_wday2_mdy2,
        com.android.internal.R.string.same_year_md1_time1_md2_time2,
        com.android.internal.R.string.same_year_wday1_md1_time1_wday2_md2_time2,
        com.android.internal.R.string.same_year_mdy1_time1_mdy2_time2,
        com.android.internal.R.string.same_year_wday1_mdy1_time1_wday2_mdy2_time2,

        // Numeric date strings
        com.android.internal.R.string.numeric_md1_md2,
        com.android.internal.R.string.numeric_wday1_md1_wday2_md2,
        com.android.internal.R.string.numeric_mdy1_mdy2,
        com.android.internal.R.string.numeric_wday1_mdy1_wday2_mdy2,
        com.android.internal.R.string.numeric_md1_time1_md2_time2,
        com.android.internal.R.string.numeric_wday1_md1_time1_wday2_md2_time2,
        com.android.internal.R.string.numeric_mdy1_time1_mdy2_time2,
        com.android.internal.R.string.numeric_wday1_mdy1_time1_wday2_mdy2_time2,
    };
    
    // This table is used to lookup the resource string id of a format string
    // used for formatting a start and end date that fall in the same month.
    // The index is constructed from a bit-wise OR of the boolean values:
    // {showTime, showYear, showWeekDay}.  For example, if showYear and
    // showWeekDay are both true, then the index would be 3.
    public static final int sameMonthTable[] = {
        com.android.internal.R.string.same_month_md1_md2,
        com.android.internal.R.string.same_month_wday1_md1_wday2_md2,
        com.android.internal.R.string.same_month_mdy1_mdy2,
        com.android.internal.R.string.same_month_wday1_mdy1_wday2_mdy2,
        com.android.internal.R.string.same_month_md1_time1_md2_time2,
        com.android.internal.R.string.same_month_wday1_md1_time1_wday2_md2_time2,
        com.android.internal.R.string.same_month_mdy1_time1_mdy2_time2,
        com.android.internal.R.string.same_month_wday1_mdy1_time1_wday2_mdy2_time2,

        com.android.internal.R.string.numeric_md1_md2,
        com.android.internal.R.string.numeric_wday1_md1_wday2_md2,
        com.android.internal.R.string.numeric_mdy1_mdy2,
        com.android.internal.R.string.numeric_wday1_mdy1_wday2_mdy2,
        com.android.internal.R.string.numeric_md1_time1_md2_time2,
        com.android.internal.R.string.numeric_wday1_md1_time1_wday2_md2_time2,
        com.android.internal.R.string.numeric_mdy1_time1_mdy2_time2,
        com.android.internal.R.string.numeric_wday1_mdy1_time1_wday2_mdy2_time2,
    };

    /**
     * Request the full spelled-out name.
     * For use with the 'abbrev' parameter of {@link #getDayOfWeekStr} and {@link #getMonthStr}.
     * @more
     * <p>e.g. "Sunday" or "January"
     */
    public static final int LENGTH_LONG = 10;

    /**
     * Request an abbreviated version of the name.
     * For use with the 'abbrev' parameter of {@link #getDayOfWeekStr} and {@link #getMonthStr}.
     * @more
     * <p>e.g. "Sun" or "Jan"
     */
    public static final int LENGTH_MEDIUM = 20;

    /**
     * Request a shorter abbreviated version of the name.
     * For use with the 'abbrev' parameter of {@link #getDayOfWeekStr} and {@link #getMonthStr}.
     * @more
     * <p>e.g. "Su" or "Jan"
     * <p>In some languages, the results returned for LENGTH_SHORT may be the same as
     * return for {@link #LENGTH_MEDIUM}.
     */
    public static final int LENGTH_SHORT = 30;

    /**
     * Request an even shorter abbreviated version of the name.
     * For use with the 'abbrev' parameter of {@link #getDayOfWeekStr} and {@link #getMonthStr}.
     * @more
     * <p>e.g. "M", "Tu", "Th" or "J"
     * <p>In some languages, the results returned for LENGTH_SHORTEST may be the same as
     * return for {@link #LENGTH_SHORTER}.
     */
    public static final int LENGTH_SHORTER = 40;

    /**
     * Request an even shorter abbreviated version of the name.
     * For use with the 'abbrev' parameter of {@link #getDayOfWeekStr} and {@link #getMonthStr}.
     * @more
     * <p>e.g. "S", "T", "T" or "J"
     * <p>In some languages, the results returned for LENGTH_SHORTEST may be the same as
     * return for {@link #LENGTH_SHORTER}.
     */
    public static final int LENGTH_SHORTEST = 50;


    /**
     * Return a string for the day of the week.
     * @param dayOfWeek One of {@link #Calendar.SUNDAY Calendar.SUNDAY},
     *               {@link #Calendar.MONDAY Calendar.MONDAY}, etc.
     * @param abbrev One of {@link #LENGTH_LONG}, {@link #LENGTH_SHORT}, {@link #LENGTH_SHORTER}
     *               or {@link #LENGTH_SHORTEST}.  For forward compatibility, anything else
     *               will return the same as {#LENGTH_MEDIUM}.
     * @throws IndexOutOfBoundsException if the dayOfWeek is out of bounds.
     */
    public static String getDayOfWeekString(int dayOfWeek, int abbrev) {
        int[] list;
        switch (abbrev) {
            case LENGTH_LONG:       list = sDaysLong;       break;
            case LENGTH_MEDIUM:     list = sDaysMedium;     break;
            case LENGTH_SHORT:      list = sDaysShort;      break;
            case LENGTH_SHORTER:    list = sDaysShorter;    break;
            case LENGTH_SHORTEST:   list = sDaysShortest;   break;
            default:                list = sDaysMedium;     break;
        }

        Resources r = Resources.getSystem();
        return r.getString(list[dayOfWeek - Calendar.SUNDAY]);
    }

    /**
     * Return a string for AM or PM.
     * @param ampm Either {@link Calendar#AM Calendar.AM} or {@link Calendar#PM Calendar.PM}.
     * @throws IndexOutOfBoundsException if the ampm is out of bounds.
     */
    public static String getAMPMString(int ampm) {
        Resources r = Resources.getSystem();
        return r.getString(sAmPm[ampm - Calendar.AM]);
    }

    /**
     * Return a string for the day of the week.
     * @param month One of {@link #Calendar.JANUARY Calendar.JANUARY},
     *               {@link #Calendar.FEBRUARY Calendar.FEBRUARY}, etc.
     * @param abbrev One of {@link #LENGTH_LONG}, {@link #LENGTH_SHORT}, {@link #LENGTH_SHORTER}
     *               or {@link #LENGTH_SHORTEST}.  For forward compatibility, anything else
     *               will return the same as {#LENGTH_MEDIUM}.
     */
    public static String getMonthString(int month, int abbrev) {
        // Note that here we use sMonthsMedium for MEDIUM, SHORT and SHORTER. 
        // This is a shortcut to not spam the translators with too many variations
        // of the same string.  If we find that in a language the distinction
        // is necessary, we can can add more without changing this API.
        int[] list;
        switch (abbrev) {
            case LENGTH_LONG:       list = sMonthsLong;     break;
            case LENGTH_MEDIUM:     list = sMonthsMedium;   break;
            case LENGTH_SHORT:      list = sMonthsMedium;   break;
            case LENGTH_SHORTER:    list = sMonthsMedium;   break;
            case LENGTH_SHORTEST:   list = sMonthsShortest; break;
            default:                list = sMonthsMedium;   break;
        }

        Resources r = Resources.getSystem();
        return r.getString(list[month - Calendar.JANUARY]);
    }

    public static CharSequence getRelativeTimeSpanString(long startTime) {
        return getRelativeTimeSpanString(startTime, System.currentTimeMillis(), MINUTE_IN_MILLIS);
    }

    /**
     * Returns a string describing 'time' as a time relative to 'now'.
     * <p>
     * Time spans in the past are formatted like "42 minutes ago".
     * Time spans in the future are formatted like "in 42 minutes".
     *
     * @param time the time to describe, in milliseconds
     * @param now the current time in milliseconds
     * @param minResolution the minimum timespan to report. For example, a time 3 seconds in the
     *     past will be reported as "0 minutes ago" if this is set to MINUTE_IN_MILLIS. Pass one of
     *     0, MINUTE_IN_MILLIS, HOUR_IN_MILLIS, DAY_IN_MILLIS, WEEK_IN_MILLIS
     */
    public static CharSequence getRelativeTimeSpanString(long time, long now, long minResolution) {
        Resources r = Resources.getSystem();

        // TODO: Assembling strings by hand like this is bad style for i18n.
        boolean past = (now > time);
        String prefix = past ? null : r.getString(com.android.internal.R.string.in);
        String postfix = past ? r.getString(com.android.internal.R.string.ago) : null;
        return getRelativeTimeSpanString(time, now, minResolution, prefix, postfix);
    }

    public static CharSequence getRelativeTimeSpanString(long time, long now, long minResolution, 
            String prefix, String postfix) {
        Resources r = Resources.getSystem(); 
        
        long duration = Math.abs(now - time);
        
        if (duration < MINUTE_IN_MILLIS && minResolution < MINUTE_IN_MILLIS) {
            long count = duration / SECOND_IN_MILLIS;
            String singular = r.getString(com.android.internal.R.string.second);
            String plural = r.getString(com.android.internal.R.string.seconds);
            return pluralizedSpan(count, singular, plural, prefix, postfix);
        }

        if (duration < HOUR_IN_MILLIS && minResolution < HOUR_IN_MILLIS) {
            long count = duration / MINUTE_IN_MILLIS;
            String singular = r.getString(com.android.internal.R.string.minute);
            String plural = r.getString(com.android.internal.R.string.minutes);
            return pluralizedSpan(count, singular, plural, prefix, postfix);
        }

        if (duration < DAY_IN_MILLIS && minResolution < DAY_IN_MILLIS) {
            long count = duration / HOUR_IN_MILLIS;
            String singular = r.getString(com.android.internal.R.string.hour);
            String plural = r.getString(com.android.internal.R.string.hours);
            return pluralizedSpan(count, singular, plural, prefix, postfix);
        }

        if (duration < WEEK_IN_MILLIS && minResolution < WEEK_IN_MILLIS) {
            return getRelativeDayString(r, time, now);
        }
        
        return dateString(time);
    }
    

    private static final String pluralizedSpan(long count, String singular, String plural, 
            String prefix, String postfix) {
        StringBuilder s = new StringBuilder();

        if (prefix != null) {
            s.append(prefix);
            s.append(" ");
        }
        
        s.append(count);
        s.append(' ');
        s.append(count == 0 || count > 1 ? plural : singular);
        
        if (postfix != null) {
            s.append(" ");
            s.append(postfix);
        }

        return s.toString();
    }

    /**
     * Returns a string describing a day relative to the current day. For example if the day is
     * today this function returns "Today", if the day was a week ago it returns "7 days ago", and
     * if the day is in 2 weeks it returns "in 14 days".
     * 
     * @param r the resources to get the strings from
     * @param day the relative day to describe in UTC milliseconds
     * @param today the current time in UTC milliseconds
     * @return a formatting string
     */
    private static final String getRelativeDayString(Resources r, long day, long today) {
        Time startTime = new Time();
        startTime.set(day);
        Time currentTime = new Time();
        currentTime.set(today);

        int startDay = Time.getJulianDay(day, startTime.gmtoff);
        int currentDay = Time.getJulianDay(today, currentTime.gmtoff);

        int days = Math.abs(currentDay - startDay);
        boolean past = (today > day);
        
        if (days == 1) {
            if (past) {
                return r.getString(com.android.internal.R.string.yesterday);
            } else {
                return r.getString(com.android.internal.R.string.tomorrow);
            }
        } else if (days == 0) {
            return r.getString(com.android.internal.R.string.today);
        }
        
        if (!past) {
            return r.getString(com.android.internal.R.string.daysDurationFuturePlural, days);
        } else {
            return r.getString(com.android.internal.R.string.daysDurationPastPlural, days);
        }
    }

    private static void initFormatStrings() {
        synchronized (sLock) {
            Resources r = Resources.getSystem();
            Configuration cfg = r.getConfiguration();
            if (sLastConfig == null || !sLastConfig.equals(cfg)) {
                sLastConfig = cfg;
                sStatusTimeFormat = r.getString(com.android.internal.R.string.status_bar_time_format);
                sStatusDateFormat = r.getString(com.android.internal.R.string.status_bar_date_format);
                sElapsedFormatMMSS = r.getString(com.android.internal.R.string.elapsed_time_short_format_mm_ss);
                sElapsedFormatHMMSS = r.getString(com.android.internal.R.string.elapsed_time_short_format_h_mm_ss);
            }
        }
    }

    /**
     * Format a time so it appears like it would in the status bar clock.
     * @deprecated use {@link #DateFormat.getTimeFormat(Context)} instead.
     * @hide
     */
    public static final CharSequence timeString(long millis) {
        initFormatStrings();
        return DateFormat.format(sStatusTimeFormat, millis);
    }
    
    /**
     * Format a date so it appears like it would in the status bar clock.
     * @deprecated use {@link #DateFormat.getDateFormat(Context)} instead.
     * @hide
     */
    public static final CharSequence dateString(long startTime) {
        initFormatStrings();
        return DateFormat.format(sStatusDateFormat, startTime);
    }

    /**
     * Formats an elapsed time like MM:SS or H:MM:SS
     * for display on the call-in-progress screen.
     */
    public static String formatElapsedTime(long elapsedSeconds) {
        initFormatStrings();

        long hours = 0;
        long minutes = 0;
        long seconds = 0;

        if (elapsedSeconds >= 3600) {
            hours = elapsedSeconds / 3600;
            elapsedSeconds -= hours * 3600;
        }
        if (elapsedSeconds >= 60) {
            minutes = elapsedSeconds / 60;
            elapsedSeconds -= minutes * 60;
        }
        seconds = elapsedSeconds;

        String result;
        if (hours > 0) {
            return formatElapsedTime(sElapsedFormatHMMSS, hours, minutes, seconds);
        } else {
            return formatElapsedTime(sElapsedFormatMMSS, minutes, seconds);
        }
    }

    /**
     * Fast formatting of h:mm:ss
     */
    private static String formatElapsedTime(String format, long hours, long minutes, long seconds) {
        if (FAST_FORMAT_HMMSS.equals(format)) {
            StringBuffer sb = new StringBuffer(16);
            sb.append(hours);
            sb.append(TIME_SEPARATOR);
            if (minutes < 10) { 
                sb.append(TIME_PADDING);
            } else {
                sb.append(toDigitChar(minutes / 10));
            }
            sb.append(toDigitChar(minutes % 10));
            sb.append(TIME_SEPARATOR);
            if (seconds < 10) {
                sb.append(TIME_PADDING);
            } else {
                sb.append(toDigitChar(seconds / 10));
            }
            sb.append(toDigitChar(seconds % 10));
            return sb.toString();
        } else {
            return String.format(format, hours, minutes, seconds);
        }
    }

    /**
     * Fast formatting of m:ss
     */
    private static String formatElapsedTime(String format, long minutes, long seconds) {
        if (FAST_FORMAT_MMSS.equals(format)) {
            StringBuffer sb = new StringBuffer(16);
            if (minutes < 10) { 
                sb.append(TIME_PADDING);
            } else {
                sb.append(toDigitChar(minutes / 10));
            }
            sb.append(toDigitChar(minutes % 10));
            sb.append(TIME_SEPARATOR);
            if (seconds < 10) {
                sb.append(TIME_PADDING);
            } else {
                sb.append(toDigitChar(seconds / 10));
            }
            sb.append(toDigitChar(seconds % 10));
            return sb.toString();
        } else {
            return String.format(format, minutes, seconds);
        }
    }

    private static char toDigitChar(long digit) {
        return (char) (digit + '0');
    }
    
    /*
     * Format a date / time such that if the then is on the same day as now, it shows
     * just the time and if it's a different day, it shows just the date.
     * 
     * <p>The parameters dateFormat and timeFormat should each be one of
     * {@link java.text.DateFormat#DEFAULT},
     * {@link java.text.DateFormat#FULL},
     * {@link java.text.DateFormat#LONG},
     * {@link java.text.DateFormat#MEDIUM}
     * or
     * {@link java.text.DateFormat#SHORT}
     *
     * @param then the date to format
     * @param now the base time
     * @param dateStyle how to format the date portion.
     * @param timeStyle how to format the time portion.
     */
    public static final CharSequence formatSameDayTime(long then, long now,
            int dateStyle, int timeStyle) {
        Calendar thenCal = new GregorianCalendar();
        thenCal.setTimeInMillis(then);
        Date thenDate = thenCal.getTime();
        Calendar nowCal = new GregorianCalendar();
        nowCal.setTimeInMillis(now);

        java.text.DateFormat f;

        if (thenCal.get(Calendar.YEAR) == nowCal.get(Calendar.YEAR)
                && thenCal.get(Calendar.MONTH) == nowCal.get(Calendar.MONTH)
                && thenCal.get(Calendar.DAY_OF_MONTH) == nowCal.get(Calendar.DAY_OF_MONTH)) {
            f = java.text.DateFormat.getTimeInstance(timeStyle);
        } else {
            f = java.text.DateFormat.getDateInstance(dateStyle);
        }
        return f.format(thenDate);
    }

    /**
     * @hide
     * @deprecated use {@link android.pim.Time}
     */
    public static Calendar newCalendar(boolean zulu)
    {
        if (zulu)
            return Calendar.getInstance(TimeZone.getTimeZone("GMT"));

        return Calendar.getInstance();
    }

    /**
     * @return true if the supplied when is today else false
     */
    public static boolean isToday(long when) {
        Time time = new Time();
        time.set(when);
        
        int thenYear = time.year;
        int thenMonth = time.month;
        int thenMonthDay = time.monthDay;

        time.set(System.currentTimeMillis());
        return (thenYear == time.year)
                && (thenMonth == time.month) 
                && (thenMonthDay == time.monthDay);
    }
    
    /**
     * @hide
     * @deprecated use {@link android.pim.Time}
     */
    private static final int ctoi(String str, int index)
                                                throws DateException
    {
        char c = str.charAt(index);
        if (c >= '0' && c <= '9') {
            return (int)(c - '0');
        }
        throw new DateException("Expected numeric character.  Got '" +
                                            c + "'");
    }

    /**
     * @hide
     * @deprecated use {@link android.pim.Time}
     */
    private static final int check(int lowerBound, int upperBound, int value)
                                                throws DateException
    {
        if (value >= lowerBound && value <= upperBound) {
            return value;
        }
        throw new DateException("field out of bounds.  max=" + upperBound
                                        + " value=" + value);
    }

    /**
     * @hide
     * @deprecated use {@link android.pim.Time}
     * Return true if this date string is local time
     */
    public static boolean isUTC(String s)
    {
        if (s.length() == 16 && s.charAt(15) == 'Z') {
            return true;
        }
        if (s.length() == 9 && s.charAt(8) == 'Z') {
            // XXX not sure if this case possible/valid
            return true;
        }
        return false;
    }


    // note that month in Calendar is 0 based and in all other human
    // representations, it's 1 based.
    // Returns if the Z was present, meaning that the time is in UTC
    /**
     * @hide
     * @deprecated use {@link android.pim.Time}
     */
    public static boolean parseDateTime(String str, Calendar cal)
                                                throws DateException
    {
        int len = str.length();
        boolean dateTime = (len == 15 || len == 16) && str.charAt(8) == 'T';
        boolean justDate = len == 8;
        if (dateTime || justDate) {
            cal.clear();
            cal.set(Calendar.YEAR, 
                            ctoi(str, 0)*1000 + ctoi(str, 1)*100
                            + ctoi(str, 2)*10 + ctoi(str, 3));
            cal.set(Calendar.MONTH,
                            check(0, 11, ctoi(str, 4)*10 + ctoi(str, 5) - 1));
            cal.set(Calendar.DAY_OF_MONTH,
                            check(1, 31, ctoi(str, 6)*10 + ctoi(str, 7)));
            if (dateTime) {
                cal.set(Calendar.HOUR_OF_DAY,
                            check(0, 23, ctoi(str, 9)*10 + ctoi(str, 10)));
                cal.set(Calendar.MINUTE,
                            check(0, 59, ctoi(str, 11)*10 + ctoi(str, 12)));
                cal.set(Calendar.SECOND,
                            check(0, 59, ctoi(str, 13)*10 + ctoi(str, 14)));
            }
            if (justDate) {
                cal.set(Calendar.HOUR_OF_DAY, 0);
                cal.set(Calendar.MINUTE, 0);
                cal.set(Calendar.SECOND, 0);
                return true;
            }
            if (len == 15) {
                return false;
            }
            if (str.charAt(15) == 'Z') {
                return true;
            }
        }
        throw new DateException("Invalid time (expected "
                                + "YYYYMMDDThhmmssZ? got '" + str + "').");
    }

    /**
     * Given a timezone string which can be null, and a dateTime string,
     * set that time into a calendar.
     * @hide
     * @deprecated use {@link android.pim.Time}
     */
    public static void parseDateTime(String tz, String dateTime, Calendar out)
                                                throws DateException
    {
        TimeZone timezone;
        if (DateUtils.isUTC(dateTime)) {
            timezone = TimeZone.getTimeZone("UTC");
        }
        else if (tz == null) {
            timezone = TimeZone.getDefault();
        }
        else {
            timezone = TimeZone.getTimeZone(tz);
        }

        Calendar local = new GregorianCalendar(timezone);
        DateUtils.parseDateTime(dateTime, local);

        out.setTimeInMillis(local.getTimeInMillis());
    }


    /**
     * Return a string containing the date and time in RFC2445 format.
     * Ensures that the time is written in UTC.  The Calendar class doesn't
     * really help out with this, so this is slower than it ought to be.
     *
     * @param cal the date and time to write
     * @hide
     * @deprecated use {@link android.pim.Time}
     */
    public static String writeDateTime(Calendar cal)
    {
        TimeZone tz = TimeZone.getTimeZone("GMT");
        GregorianCalendar c = new GregorianCalendar(tz);
        c.setTimeInMillis(cal.getTimeInMillis());
        return writeDateTime(c, true);
    }

    /**
     * Return a string containing the date and time in RFC2445 format.
     *
     * @param cal the date and time to write
     * @param zulu If the calendar is in UTC, pass true, and a Z will
     * be written at the end as per RFC2445.  Otherwise, the time is
     * considered in localtime.
     * @hide
     * @deprecated use {@link android.pim.Time}
     */
    public static String writeDateTime(Calendar cal, boolean zulu)
    {
        StringBuilder sb = new StringBuilder();
        sb.ensureCapacity(16);
        if (zulu) {
            sb.setLength(16);
            sb.setCharAt(15, 'Z');
        } else {
            sb.setLength(15);
        }
        return writeDateTime(cal, sb);
    }

    /**
     * Return a string containing the date and time in RFC2445 format.
     *
     * @param cal the date and time to write
     * @param sb a StringBuilder to use.  It is assumed that setLength
     *           has already been called on sb to the appropriate length
     *           which is sb.setLength(zulu ? 16 : 15)
     * @hide
     * @deprecated use {@link android.pim.Time}
     */
    public static String writeDateTime(Calendar cal, StringBuilder sb)
    {
        int n;
       
        n = cal.get(Calendar.YEAR);
        sb.setCharAt(3, (char)('0'+n%10));
        n /= 10;
        sb.setCharAt(2, (char)('0'+n%10));
        n /= 10;
        sb.setCharAt(1, (char)('0'+n%10));
        n /= 10;
        sb.setCharAt(0, (char)('0'+n%10));

        n = cal.get(Calendar.MONTH) + 1;
        sb.setCharAt(5, (char)('0'+n%10));
        n /= 10;
        sb.setCharAt(4, (char)('0'+n%10));

        n = cal.get(Calendar.DAY_OF_MONTH);
        sb.setCharAt(7, (char)('0'+n%10));
        n /= 10;
        sb.setCharAt(6, (char)('0'+n%10));

        sb.setCharAt(8, 'T');

        n = cal.get(Calendar.HOUR_OF_DAY);
        sb.setCharAt(10, (char)('0'+n%10));
        n /= 10;
        sb.setCharAt(9, (char)('0'+n%10));

        n = cal.get(Calendar.MINUTE);
        sb.setCharAt(12, (char)('0'+n%10));
        n /= 10;
        sb.setCharAt(11, (char)('0'+n%10));

        n = cal.get(Calendar.SECOND);
        sb.setCharAt(14, (char)('0'+n%10));
        n /= 10;
        sb.setCharAt(13, (char)('0'+n%10));

        return sb.toString();
    }

    /**
     * @hide
     * @deprecated use {@link android.pim.Time}
     */
    public static void assign(Calendar lval, Calendar rval)
    {
        // there should be a faster way.
        lval.clear();
        lval.setTimeInMillis(rval.getTimeInMillis());
    }

    /**
     * Creates a string describing a date/time range.  The flags argument
     * is a bitmask of options from the following list:
     * 
     * <ul>
     *   <li>FORMAT_SHOW_TIME</li>
     *   <li>FORMAT_SHOW_WEEKDAY</li>
     *   <li>FORMAT_SHOW_YEAR</li>
     *   <li>FORMAT_NO_YEAR</li>
     *   <li>FORMAT_SHOW_DATE</li>
     *   <li>FORMAT_NO_MONTH_DAY</li>
     *   <li>FORMAT_24HOUR</li>
     *   <li>FORMAT_CAP_AMPM</li>
     *   <li>FORMAT_NO_NOON</li>
     *   <li>FORMAT_CAP_NOON</li>
     *   <li>FORMAT_NO_MIDNIGHT</li>
     *   <li>FORMAT_CAP_MIDNIGHT</li>
     *   <li>FORMAT_UTC</li>
     *   <li>FORMAT_ABBREV_TIME</li>
     *   <li>FORMAT_ABBREV_WEEKDAY</li>
     *   <li>FORMAT_ABBREV_MONTH</li>
     *   <li>FORMAT_ABBREV_ALL</li>
     *   <li>FORMAT_NUMERIC_DATE</li>
     * </ul>
     * 
     * <p>
     * If FORMAT_SHOW_TIME is set, the time is shown as part of the date range.
     * If the start and end time are the same, then just the start time is
     * shown.
     * 
     * <p>
     * If FORMAT_SHOW_WEEKDAY is set, then the weekday is shown.
     * 
     * <p>
     * If FORMAT_SHOW_YEAR is set, then the year is always shown.
     * If FORMAT_NO_YEAR is set, then the year is not shown.
     * If neither FORMAT_SHOW_YEAR nor FORMAT_NO_YEAR are set, then the year
     * is shown only if it is different from the current year, or if the start
     * and end dates fall on different years.
     * 
     * <p>
     * Normally the date is shown unless the start and end day are the same.
     * If FORMAT_SHOW_DATE is set, then the date is always shown, even for
     * same day ranges.
     * 
     * <p>
     * If FORMAT_NO_MONTH_DAY is set, then if the date is shown, just the
     * month name will be shown, not the day of the month.  For example,
     * "January, 2008" instead of "January 6 - 12, 2008".
     * 
     * <p>
     * If FORMAT_CAP_AMPM is set and 12-hour time is used, then the "AM"
     * and "PM" are capitalized.
     * 
     * <p>
     * If FORMAT_NO_NOON is set and 12-hour time is used, then "12pm" is
     * shown instead of "noon".
     * 
     * <p>
     * If FORMAT_CAP_NOON is set and 12-hour time is used, then "Noon" is
     * shown instead of "noon".
     * 
     * <p>
     * If FORMAT_NO_MIDNIGHT is set and 12-hour time is used, then "12am" is
     * shown instead of "midnight".
     * 
     * <p>
     * If FORMAT_CAP_NOON is set and 12-hour time is used, then "Midnight" is
     * shown instead of "midnight".
     * 
     * <p>
     * If FORMAT_24HOUR is set and the time is shown, then the time is
     * shown in the 24-hour time format.
     * 
     * <p>
     * If FORMAT_UTC is set, then the UTC timezone is used for the start
     * and end milliseconds.
     * 
     * <p>
     * If FORMAT_ABBREV_TIME is set and FORMAT_24HOUR is not set, then the
     * start and end times (if shown) are abbreviated by not showing the minutes
     * if they are zero.  For example, instead of "3:00pm" the time would be
     * abbreviated to "3pm".
     * 
     * <p>
     * If FORMAT_ABBREV_WEEKDAY is set, then the weekday (if shown) is
     * abbreviated to a 3-letter string.
     * 
     * <p>
     * If FORMAT_ABBREV_MONTH is set, then the month (if shown) is abbreviated
     * to a 3-letter string.
     * 
     * <p>
     * If FORMAT_ABBREV_ALL is set, then the weekday and the month (if shown)
     * are abbreviated to 3-letter strings.
     * 
     * <p>
     * If FORMAT_NUMERIC_DATE is set, then the date is shown in numeric format
     * instead of using the name of the month.  For example, "12/31/2008"
     * instead of "December 31, 2008".
     * 
     * <p>
     * Example output strings:
     * <ul>
     *   <li>10:15am</li>
     *   <li>3:00pm - 4:00pm</li>
     *   <li>3pm - 4pm</li>
     *   <li>3PM - 4PM</li>
     *   <li>08:00 - 17:00</li>
     *   <li>Oct 9</li>
     *   <li>Tue, Oct 9</li>
     *   <li>October 9, 2007</li>
     *   <li>Oct 9 - 10</li>
     *   <li>Oct 9 - 10, 2007</li>
     *   <li>Oct 28 - Nov 3, 2007</li>
     *   <li>Dec 31, 2007 - Jan 1, 2008</li>
     *   <li>Oct 9, 8:00am - Oct 10, 5:00pm</li>
     * </ul>
     * @param startMillis the start time in UTC milliseconds
     * @param endMillis the end time in UTC milliseconds
     * @param flags a bit mask of options
     *   
     * @return a string with the formatted date/time range.
     */
    public static String formatDateRange(long startMillis, long endMillis, int flags) {
        Resources res = Resources.getSystem();
        boolean showTime = (flags & FORMAT_SHOW_TIME) != 0;
        boolean showWeekDay = (flags & FORMAT_SHOW_WEEKDAY) != 0;
        boolean showYear = (flags & FORMAT_SHOW_YEAR) != 0;
        boolean noYear = (flags & FORMAT_NO_YEAR) != 0;
        boolean useUTC = (flags & FORMAT_UTC) != 0;
        boolean abbrevWeekDay = (flags & FORMAT_ABBREV_WEEKDAY) != 0;
        boolean abbrevMonth = (flags & FORMAT_ABBREV_MONTH) != 0;
        boolean use24Hour = (flags & FORMAT_24HOUR) != 0;
        boolean noMonthDay = (flags & FORMAT_NO_MONTH_DAY) != 0;
        boolean numericDate = (flags & FORMAT_NUMERIC_DATE) != 0;
    
        Time startDate;
        Time endDate;
        
        if (useUTC) {
            startDate = new Time(Time.TIMEZONE_UTC);
            endDate = new Time(Time.TIMEZONE_UTC);
        } else {
            startDate = new Time();
            endDate = new Time();
        }
        
        startDate.set(startMillis);
        endDate.set(endMillis);
        int startJulianDay = Time.getJulianDay(startMillis, startDate.gmtoff);
        int endJulianDay = Time.getJulianDay(endMillis, endDate.gmtoff);
        int dayDistance = endJulianDay - startJulianDay;
        
        // If the end date ends at 12am at the beginning of a day,
        // then modify it to make it look like it ends at midnight on
        // the previous day.  This will allow us to display "8pm - midnight",
        // for example, instead of "Nov 10, 8pm - Nov 11, 12am". But we only do
        // this if it is midnight of the same day as the start date because
        // for multiple-day events, an end time of "midnight on Nov 11" is
        // ambiguous and confusing (is that midnight the start of Nov 11, or
        // the end of Nov 11?).
        // If we are not showing the time then also adjust the end date
        // for multiple-day events.  This is to allow us to display, for
        // example, "Nov 10 -11" for an event with an start date of Nov 10
        // and an end date of Nov 12 at 00:00.
        // If the start and end time are the same, then skip this and don't
        // adjust the date.
        if ((endDate.hour | endDate.minute | endDate.second) == 0
                && (!showTime || dayDistance <= 1) && (startMillis != endMillis)) {
            endDate.monthDay -= 1;
            endDate.normalize(true /* ignore isDst */);
        }
        
        int startDay = startDate.monthDay;
        int startMonthNum = startDate.month;
        int startYear = startDate.year;
    
        int endDay = endDate.monthDay;
        int endMonthNum = endDate.month;
        int endYear = endDate.year;
    
        String startWeekDayString = "";
        String endWeekDayString = "";
        if (showWeekDay) {
            String weekDayFormat = "";
            if (abbrevWeekDay) {
                weekDayFormat = ABBREV_WEEKDAY_FORMAT;
            } else {
                weekDayFormat = WEEKDAY_FORMAT;
            }
            startWeekDayString = startDate.format(weekDayFormat);
            endWeekDayString = endDate.format(weekDayFormat);
        }
        
        String startTimeString = "";
        String endTimeString = "";
        if (showTime) {
            String startTimeFormat = "";
            String endTimeFormat = "";
            if (use24Hour) {
                startTimeFormat = HOUR_MINUTE_24;
                endTimeFormat = HOUR_MINUTE_24;
            } else {
                boolean abbrevTime = (flags & FORMAT_ABBREV_TIME) != 0;
                boolean capAMPM = (flags & FORMAT_CAP_AMPM) != 0;
                boolean noNoon = (flags & FORMAT_NO_NOON) != 0;
                boolean capNoon = (flags & FORMAT_CAP_NOON) != 0;
                boolean noMidnight = (flags & FORMAT_NO_MIDNIGHT) != 0;
                boolean capMidnight = (flags & FORMAT_CAP_MIDNIGHT) != 0;
    
                boolean startOnTheHour = startDate.minute == 0 && startDate.second == 0;
                boolean endOnTheHour = endDate.minute == 0 && endDate.second == 0;
                if (abbrevTime && startOnTheHour) {
                    if (capAMPM) {
                        startTimeFormat = HOUR_CAP_AMPM;
                    } else {
                        startTimeFormat = HOUR_AMPM;
                    }
                } else {
                    if (capAMPM) {
                        startTimeFormat = HOUR_MINUTE_CAP_AMPM;
                    } else {
                        startTimeFormat = HOUR_MINUTE_AMPM;
                    }
                }
                if (abbrevTime && endOnTheHour) {
                    if (capAMPM) {
                        endTimeFormat = HOUR_CAP_AMPM;
                    } else {
                        endTimeFormat = HOUR_AMPM;
                    }
                } else {
                    if (capAMPM) {
                        endTimeFormat = HOUR_MINUTE_CAP_AMPM;
                    } else {
                        endTimeFormat = HOUR_MINUTE_AMPM;
                    }
                }
                
                if (startDate.hour == 12 && startOnTheHour && !noNoon) {
                    if (capNoon) {
                        startTimeFormat = res.getString(com.android.internal.R.string.Noon);
                    } else {
                        startTimeFormat = res.getString(com.android.internal.R.string.noon);
                    }
                    // Don't show the start time starting at midnight.  Show
                    // 12am instead.
                }
                
                if (endDate.hour == 12 && endOnTheHour && !noNoon) {
                    if (capNoon) {
                        endTimeFormat = res.getString(com.android.internal.R.string.Noon);
                    } else {
                        endTimeFormat = res.getString(com.android.internal.R.string.noon);
                    }
                } else if (endDate.hour == 0 && endOnTheHour && !noMidnight) {
                    if (capMidnight) {
                        endTimeFormat = res.getString(com.android.internal.R.string.Midnight);
                    } else {
                        endTimeFormat = res.getString(com.android.internal.R.string.midnight);
                    }
                }
            }
            startTimeString = startDate.format(startTimeFormat);
            endTimeString = endDate.format(endTimeFormat);
        }
        
        // Get the current year
        long millis = System.currentTimeMillis();
        Time time = new Time();
        time.set(millis);
        int currentYear = time.year;
    
        // Show the year if the user specified FORMAT_SHOW_YEAR or if
        // the starting and end years are different from each other
        // or from the current year.  But don't show the year if the
        // user specified FORMAT_NO_YEAR;
        showYear = showYear || (!noYear && (startYear != endYear || startYear != currentYear));
        
        String defaultDateFormat, fullFormat, dateRange;
        if (numericDate) {
            defaultDateFormat = res.getString(com.android.internal.R.string.numeric_date);
        } else if (showYear) {
            if (abbrevMonth) {
                if (noMonthDay) {
                    defaultDateFormat = res.getString(com.android.internal.R.string.abbrev_month_year);
                } else {
                    defaultDateFormat = res.getString(com.android.internal.R.string.abbrev_month_day_year);
                }
            } else {
                if (noMonthDay) {
                    defaultDateFormat = res.getString(com.android.internal.R.string.month_year);
                } else {
                    defaultDateFormat = res.getString(com.android.internal.R.string.month_day_year);
                }
            }
        } else {
            if (abbrevMonth) {
                if (noMonthDay) {
                    defaultDateFormat = res.getString(com.android.internal.R.string.abbrev_month);
                } else {
                    defaultDateFormat = res.getString(com.android.internal.R.string.abbrev_month_day);
                }
            } else {
                if (noMonthDay) {
                    defaultDateFormat = res.getString(com.android.internal.R.string.month);
                } else {
                    defaultDateFormat = res.getString(com.android.internal.R.string.month_day);
                }
            }
        }
        
        if (showWeekDay) {
            if (showTime) {
                fullFormat = res.getString(com.android.internal.R.string.wday1_date1_time1_wday2_date2_time2);
            } else {
                fullFormat = res.getString(com.android.internal.R.string.wday1_date1_wday2_date2);
            }
        } else {
            if (showTime) {
                fullFormat = res.getString(com.android.internal.R.string.date1_time1_date2_time2);
            } else {
                fullFormat = res.getString(com.android.internal.R.string.date1_date2);
            }
        }
        
        if (noMonthDay && startMonthNum == endMonthNum) {
            // Example: "January, 2008"
            String startDateString = startDate.format(defaultDateFormat);
            return startDateString;
        }
    
        if (startYear != endYear || noMonthDay) {
            // Different year or we are not showing the month day number.
            // Example: "December 31, 2007 - January 1, 2008"
            // Or: "January - February, 2008"
            String startDateString = startDate.format(defaultDateFormat);
            String endDateString = endDate.format(defaultDateFormat);
    
            // The values that are used in a fullFormat string are specified
            // by position.
            dateRange = String.format(fullFormat,
                    startWeekDayString, startDateString, startTimeString,
                    endWeekDayString, endDateString, endTimeString);
            return dateRange;
        }
        
        // Get the month, day, and year strings for the start and end dates
        String monthFormat;
        if (numericDate) {
            monthFormat = NUMERIC_MONTH_FORMAT;
        } else if (abbrevMonth) {
            monthFormat = ABBREV_MONTH_FORMAT;
        } else {
            monthFormat = MONTH_FORMAT;
        }
        String startMonthString = startDate.format(monthFormat);
        String startMonthDayString = startDate.format(MONTH_DAY_FORMAT);
        String startYearString = startDate.format(YEAR_FORMAT);
        String endMonthString = endDate.format(monthFormat);
        String endMonthDayString = endDate.format(MONTH_DAY_FORMAT);
        String endYearString = endDate.format(YEAR_FORMAT);
        
        if (startMonthNum != endMonthNum) {
            // Same year, different month.
            // Example: "October 28 - November 3"
            // or: "Wed, Oct 31 - Sat, Nov 3, 2007"
            // or: "Oct 31, 8am - Sat, Nov 3, 2007, 5pm"
            
            int index = 0;
            if (showWeekDay) index = 1;
            if (showYear) index += 2;
            if (showTime) index += 4;
            if (numericDate) index += 8;
            int resId = sameYearTable[index];
            fullFormat = res.getString(resId);
            
            // The values that are used in a fullFormat string are specified
            // by position.
            dateRange = String.format(fullFormat,
                    startWeekDayString, startMonthString, startMonthDayString,
                    startYearString, startTimeString,
                    endWeekDayString, endMonthString, endMonthDayString,
                    endYearString, endTimeString);
            return dateRange;
        }
    
        if (startDay != endDay) {
            // Same month, different day.
            int index = 0;
            if (showWeekDay) index = 1;
            if (showYear) index += 2;
            if (showTime) index += 4;
            if (numericDate) index += 8;
            int resId = sameMonthTable[index];
            fullFormat = res.getString(resId);
            
            // The values that are used in a fullFormat string are specified
            // by position.
            dateRange = String.format(fullFormat,
                    startWeekDayString, startMonthString, startMonthDayString,
                    startYearString, startTimeString,
                    endWeekDayString, endMonthString, endMonthDayString,
                    endYearString, endTimeString);
            return dateRange;
        }
        
        // Same start and end day
        boolean showDate = (flags & FORMAT_SHOW_DATE) != 0;
        
        // If nothing was specified, then show the date.
        if (!showTime && !showDate && !showWeekDay) showDate = true;
        
        // Compute the time string (example: "10:00 - 11:00 am")
        String timeString = "";
        if (showTime) {
            // If the start and end time are the same, then just show the
            // start time.
            if (startMillis == endMillis) {
                // Same start and end time.
                // Example: "10:15 AM"
                timeString = startTimeString;
            } else {
                // Example: "10:00 - 11:00 am"
                String timeFormat = res.getString(com.android.internal.R.string.time1_time2);
                timeString = String.format(timeFormat, startTimeString, endTimeString);
            }
        }
    
        // Figure out which full format to use.
        fullFormat = "";
        String dateString = "";
        if (showDate) {
            dateString = startDate.format(defaultDateFormat);
            if (showWeekDay) {
                if (showTime) {
                    // Example: "10:00 - 11:00 am, Tue, Oct 9"
                    fullFormat = res.getString(com.android.internal.R.string.time_wday_date);
                } else {
                    // Example: "Tue, Oct 9"
                    fullFormat = res.getString(com.android.internal.R.string.wday_date);
                }
            } else {
                if (showTime) {
                    // Example: "10:00 - 11:00 am, Oct 9"
                    fullFormat = res.getString(com.android.internal.R.string.time_date);
                } else {
                    // Example: "Oct 9"
                    return dateString;
                }
            }
        } else if (showWeekDay) {
            if (showTime) {
                // Example: "10:00 - 11:00 am, Tue"
                fullFormat = res.getString(com.android.internal.R.string.time_wday);
            } else {
                // Example: "Tue"
                return startWeekDayString;
            }
        } else if (showTime) {
            return timeString;
        }
    
        // The values that are used in a fullFormat string are specified
        // by position.
        dateRange = String.format(fullFormat, timeString, startWeekDayString, dateString);
        return dateRange;
    }

    /**
     * @return a relative time string to display the time expressed by millis.  Times
     * are counted starting at midnight, which means that assuming that the current
     * time is March 31st, 0:30:
     * "millis=0:10 today" will be displayed as "0:10" 
     * "millis=11:30pm the day before" will be displayed as "Mar 30"
     * A similar scheme is used to dates that are a week, a month or more than a year old. 
     * 
     * @param withPreposition If true, the string returned will include the correct 
     * preposition ("at 9:20am", "in 2008" or "on May 29").
     */
    public static CharSequence getRelativeTimeSpanString(Context c, long millis,
            boolean withPreposition) {

        long span = System.currentTimeMillis() - millis;

        Resources res = c.getResources();
        if (sNowTime == null) {
            sNowTime = new Time();
            sThenTime = new Time();
            sMonthDayFormat = res.getString(com.android.internal.R.string.abbrev_month_day);
        }

        sNowTime.setToNow();
        sThenTime.set(millis);

        if (span < DAY_IN_MILLIS && sNowTime.weekDay == sThenTime.weekDay) {
            // Same day
            return getPrepositionDate(res, sThenTime, R.string.preposition_for_time,
                    HOUR_MINUTE_CAP_AMPM, withPreposition);
        } else if (sNowTime.year != sThenTime.year) {
            // Different years
            // TODO: take locale into account so that the display will adjust correctly.
            return getPrepositionDate(res, sThenTime, R.string.preposition_for_year,
                    NUMERIC_MONTH_FORMAT + "/" + MONTH_DAY_FORMAT + "/" + YEAR_FORMAT_TWO_DIGITS,
                    withPreposition);
        } else {
            // Default
            return getPrepositionDate(res, sThenTime, R.string.preposition_for_date,
                sMonthDayFormat, withPreposition);
        }
    }
    
    /**
     * @return A date string suitable for display based on the format and including the
     * date preposition if withPreposition is true.
     */
    private static String getPrepositionDate(Resources res, Time thenTime, int id,
            String formatString, boolean withPreposition) {
        String result = thenTime.format(formatString);
        return withPreposition ? res.getString(id, result) : result;
    }
    
    public static CharSequence getRelativeTimeSpanString(Context c, long millis) {
        return getRelativeTimeSpanString(c, millis, false /* no preposition */);
    }
    
    private static Time sNowTime;
    private static Time sThenTime;
    private static String sMonthDayFormat;
}
