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

package android.util;

import android.content.res.Resources;
import android.content.res.XmlResourceParser;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;
import java.util.TimeZone;
import java.util.Date;

import com.android.internal.util.XmlUtils;

public class TimeUtils {
    /**
     * Tries to return a time zone that would have had the specified offset
     * and DST value at the specified moment in the specified country.
     * Returns null if no suitable zone could be found.
     */
    public static TimeZone getTimeZone(int offset, boolean dst, long when,
                                       String country) {
        if (country == null) {
            return null;
        }

        TimeZone best = null;

        Resources r = Resources.getSystem();
        XmlResourceParser parser = r.getXml(com.android.internal.R.xml.time_zones_by_country);
        Date d = new Date(when);

        TimeZone current = TimeZone.getDefault();
        String currentName = current.getID();
        int currentOffset = current.getOffset(when);
        boolean currentDst = current.inDaylightTime(d);
        
        try {
            XmlUtils.beginDocument(parser, "timezones");
            
            while (true) {
                XmlUtils.nextElement(parser);
                
                String element = parser.getName();
                if (element == null || !(element.equals("timezone"))) {
                    break;
                }
                
                String code = parser.getAttributeValue(null, "code");

                if (country.equals(code)) {
                    if (parser.next() == XmlPullParser.TEXT) {
                        String maybe = parser.getText();

                        // If the current time zone is from the right country
                        // and meets the other known properties, keep it
                        // instead of changing to another one.

                        if (maybe.equals(currentName)) {
                            if (currentOffset == offset && currentDst == dst) {
                                return current;
                            }
                        }

                        // Otherwise, take the first zone from the right
                        // country that has the correct current offset and DST.
                        // (Keep iterating instead of returning in case we
                        // haven't encountered the current time zone yet.)

                        if (best == null) {
                            TimeZone tz = TimeZone.getTimeZone(maybe);

                            if (tz.getOffset(when) == offset &&
                                tz.inDaylightTime(d) == dst) {
                                best = tz;
                            }
                        }
                    }
                }
            }
        } catch (XmlPullParserException e) {
            Log.e("TimeUtils",
                  "Got exception while getting preferred time zone.", e);
        } catch (IOException e) {
            Log.e("TimeUtils",
                  "Got exception while getting preferred time zone.", e);
        } finally {
            parser.close();
        }
        
        return best;
    }
}
