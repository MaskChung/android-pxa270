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

package android.text;

import com.android.internal.R;

import android.content.res.ColorStateList;
import android.content.res.Resources;
import android.os.Parcel;
import android.os.Parcelable;
import android.text.style.AbsoluteSizeSpan;
import android.text.style.AlignmentSpan;
import android.text.style.BackgroundColorSpan;
import android.text.style.BulletSpan;
import android.text.style.CharacterStyle;
import android.text.style.ForegroundColorSpan;
import android.text.style.LeadingMarginSpan;
import android.text.style.MetricAffectingSpan;
import android.text.style.QuoteSpan;
import android.text.style.RelativeSizeSpan;
import android.text.style.ReplacementSpan;
import android.text.style.ScaleXSpan;
import android.text.style.StrikethroughSpan;
import android.text.style.StyleSpan;
import android.text.style.SubscriptSpan;
import android.text.style.SuperscriptSpan;
import android.text.style.TextAppearanceSpan;
import android.text.style.TypefaceSpan;
import android.text.style.URLSpan;
import android.text.style.UnderlineSpan;
import com.android.internal.util.ArrayUtils;

import java.util.regex.Pattern;
import java.util.Iterator;

public class TextUtils
{
    private TextUtils() { /* cannot be instantiated */ }

    private static String[] EMPTY_STRING_ARRAY = new String[]{};

    public static void getChars(CharSequence s, int start, int end,
                                char[] dest, int destoff) {
        Class c = s.getClass();

        if (c == String.class)
            ((String) s).getChars(start, end, dest, destoff);
        else if (c == StringBuffer.class)
            ((StringBuffer) s).getChars(start, end, dest, destoff);
        else if (c == StringBuilder.class)
            ((StringBuilder) s).getChars(start, end, dest, destoff);
        else if (s instanceof GetChars)
            ((GetChars) s).getChars(start, end, dest, destoff);
        else {
            for (int i = start; i < end; i++)
                dest[destoff++] = s.charAt(i);
        }
    }

    public static int indexOf(CharSequence s, char ch) {
        return indexOf(s, ch, 0);
    }

    public static int indexOf(CharSequence s, char ch, int start) {
        Class c = s.getClass();

        if (c == String.class)
            return ((String) s).indexOf(ch, start);

        return indexOf(s, ch, start, s.length());
    }

    public static int indexOf(CharSequence s, char ch, int start, int end) {
        Class c = s.getClass();

        if (s instanceof GetChars || c == StringBuffer.class ||
            c == StringBuilder.class || c == String.class) {
            final int INDEX_INCREMENT = 500;
            char[] temp = obtain(INDEX_INCREMENT);

            while (start < end) {
                int segend = start + INDEX_INCREMENT;
                if (segend > end)
                    segend = end;

                getChars(s, start, segend, temp, 0);

                int count = segend - start;
                for (int i = 0; i < count; i++) {
                    if (temp[i] == ch) {
                        recycle(temp);
                        return i + start;
                    }
                }

                start = segend;
            }

            recycle(temp);
            return -1;
        }

        for (int i = start; i < end; i++)
            if (s.charAt(i) == ch)
                return i;

        return -1;
    }

    public static int lastIndexOf(CharSequence s, char ch) {
        return lastIndexOf(s, ch, s.length() - 1);
    }

    public static int lastIndexOf(CharSequence s, char ch, int last) {
        Class c = s.getClass();

        if (c == String.class)
            return ((String) s).lastIndexOf(ch, last);

        return lastIndexOf(s, ch, 0, last);
    }

    public static int lastIndexOf(CharSequence s, char ch,
                                  int start, int last) {
        if (last < 0)
            return -1;
        if (last >= s.length())
            last = s.length() - 1;

        int end = last + 1;

        Class c = s.getClass();

        if (s instanceof GetChars || c == StringBuffer.class ||
            c == StringBuilder.class || c == String.class) {
            final int INDEX_INCREMENT = 500;
            char[] temp = obtain(INDEX_INCREMENT);

            while (start < end) {
                int segstart = end - INDEX_INCREMENT;
                if (segstart < start)
                    segstart = start;

                getChars(s, segstart, end, temp, 0);

                int count = end - segstart;
                for (int i = count - 1; i >= 0; i--) {
                    if (temp[i] == ch) {
                        recycle(temp);
                        return i + segstart;
                    }
                }

                end = segstart;
            }

            recycle(temp);
            return -1;
        }

        for (int i = end - 1; i >= start; i--)
            if (s.charAt(i) == ch)
                return i;

        return -1;
    }

    public static int indexOf(CharSequence s, CharSequence needle) {
        return indexOf(s, needle, 0, s.length());
    }

    public static int indexOf(CharSequence s, CharSequence needle, int start) {
        return indexOf(s, needle, start, s.length());
    }

    public static int indexOf(CharSequence s, CharSequence needle,
                              int start, int end) {
        int nlen = needle.length();
        if (nlen == 0)
            return start;

        char c = needle.charAt(0);

        for (;;) {
            start = indexOf(s, c, start);
            if (start > end - nlen) {
                break;
            }

            if (start < 0) {
                return -1;
            }

            if (regionMatches(s, start, needle, 0, nlen)) {
                return start;
            }

            start++;
        }
        return -1;
    }

    public static boolean regionMatches(CharSequence one, int toffset,
                                        CharSequence two, int ooffset,
                                        int len) {
        char[] temp = obtain(2 * len);

        getChars(one, toffset, toffset + len, temp, 0);
        getChars(two, ooffset, ooffset + len, temp, len);

        boolean match = true;
        for (int i = 0; i < len; i++) {
            if (temp[i] != temp[i + len]) {
                match = false;
                break;
            }
        }

        recycle(temp);
        return match;
    }

    public static String substring(CharSequence source, int start, int end) {
        if (source instanceof String)
            return ((String) source).substring(start, end);
        if (source instanceof StringBuilder)
            return ((StringBuilder) source).substring(start, end);
        if (source instanceof StringBuffer)
            return ((StringBuffer) source).substring(start, end);

        char[] temp = obtain(end - start);
        getChars(source, start, end, temp, 0);
        String ret = new String(temp, 0, end - start);
        recycle(temp);

        return ret;
    }

    /**
     * Returns a string containing the tokens joined by delimiters.
     * @param tokens an array objects to be joined. Strings will be formed from
     *     the objects by calling object.toString().
     */
    public static String join(CharSequence delimiter, Object[] tokens) {
        StringBuilder sb = new StringBuilder();
        boolean firstTime = true;
        for (Object token: tokens) {
            if (firstTime) {
                firstTime = false;
            } else {
                sb.append(delimiter);
            }
            sb.append(token);
        }
        return sb.toString();
    }

    /**
     * Returns a string containing the tokens joined by delimiters.
     * @param tokens an array objects to be joined. Strings will be formed from
     *     the objects by calling object.toString().
     */
    public static String join(CharSequence delimiter, Iterable tokens) {
        StringBuilder sb = new StringBuilder();
        boolean firstTime = true;
        for (Object token: tokens) {
            if (firstTime) {
                firstTime = false;
            } else {
                sb.append(delimiter);
            }
            sb.append(token);
        }
        return sb.toString();
    }

    /**
     * String.split() returns [''] when the string to be split is empty. This returns []. This does
     * not remove any empty strings from the result. For example split("a,", ","  ) returns {"a", ""}.
     *
     * @param text the string to split
     * @param expression the regular expression to match
     * @return an array of strings. The array will be empty if text is empty
     *
     * @throws NullPointerException if expression or text is null
     */
    public static String[] split(String text, String expression) {
        if (text.length() == 0) {
            return EMPTY_STRING_ARRAY;
        } else {
            return text.split(expression, -1);
        }
    }

    /**
     * Splits a string on a pattern. String.split() returns [''] when the string to be
     * split is empty. This returns []. This does not remove any empty strings from the result.
     * @param text the string to split
     * @param pattern the regular expression to match
     * @return an array of strings. The array will be empty if text is empty
     *
     * @throws NullPointerException if expression or text is null
     */
    public static String[] split(String text, Pattern pattern) {
        if (text.length() == 0) {
            return EMPTY_STRING_ARRAY;
        } else {
            return pattern.split(text, -1);
        }
    }

    /**
     * An interface for splitting strings according to rules that are opaque to the user of this
     * interface. This also has less overhead than split, which uses regular expressions and
     * allocates an array to hold the results.
     *
     * <p>The most efficient way to use this class is:
     *
     * <pre>
     * // Once
     * TextUtils.StringSplitter splitter = new TextUtils.SimpleStringSplitter(delimiter);
     *
     * // Once per string to split
     * splitter.setString(string);
     * for (String s : splitter) {
     *     ...
     * }
     * </pre>
     */
    public interface StringSplitter extends Iterable<String> {
        public void setString(String string);
    }

    /**
     * A simple string splitter.
     *
     * <p>If the final character in the string to split is the delimiter then no empty string will
     * be returned for the empty string after that delimeter. That is, splitting <tt>"a,b,"</tt> on
     * comma will return <tt>"a", "b"</tt>, not <tt>"a", "b", ""</tt>.
     */
    public static class SimpleStringSplitter implements StringSplitter, Iterator<String> {
        private String mString;
        private char mDelimiter;
        private int mPosition;
        private int mLength;

        /**
         * Initializes the splitter. setString may be called later.
         * @param delimiter the delimeter on which to split
         */
        public SimpleStringSplitter(char delimiter) {
            mDelimiter = delimiter;
        }

        /**
         * Sets the string to split
         * @param string the string to split
         */
        public void setString(String string) {
            mString = string;
            mPosition = 0;
            mLength = mString.length();
        }

        public Iterator<String> iterator() {
            return this;
        }

        public boolean hasNext() {
            return mPosition < mLength;
        }

        public String next() {
            int end = mString.indexOf(mDelimiter, mPosition);
            if (end == -1) {
                end = mLength;
            }
            String nextString = mString.substring(mPosition, end);
            mPosition = end + 1; // Skip the delimiter.
            return nextString;
        }

        public void remove() {
            throw new UnsupportedOperationException();
        }
    }

    public static CharSequence stringOrSpannedString(CharSequence source) {
        if (source == null)
            return null;
        if (source instanceof SpannedString)
            return source;
        if (source instanceof Spanned)
            return new SpannedString(source);

        return source.toString();
    }

    /**
     * Returns true if the string is null or 0-length.
     * @param str the string to be examined
     * @return true if str is null or zero length
     */
    public static boolean isEmpty(CharSequence str) {
        if (str == null || str.length() == 0)
            return true;
        else
            return false;
    }

    /**
     * Returns the length that the specified CharSequence would have if
     * spaces and control characters were trimmed from the start and end,
     * as by {@link String#trim}.
     */
    public static int getTrimmedLength(CharSequence s) {
        int len = s.length();

        int start = 0;
        while (start < len && s.charAt(start) <= ' ') {
            start++;
        }

        int end = len;
        while (end > start && s.charAt(end - 1) <= ' ') {
            end--;
        }

        return end - start;
    }

    /**
     * Returns true if a and b are equal, including if they are both null.
     *
     * @param a first CharSequence to check
     * @param b second CharSequence to check
     * @return true if a and b are equal
     */
    public static boolean equals(CharSequence a, CharSequence b) {
        return a == b || (a != null && a.equals(b));
    }

    // XXX currently this only reverses chars, not spans
    public static CharSequence getReverse(CharSequence source,
                                          int start, int end) {
        return new Reverser(source, start, end);
    }

    private static class Reverser
    implements CharSequence, GetChars
    {
        public Reverser(CharSequence source, int start, int end) {
            mSource = source;
            mStart = start;
            mEnd = end;
        }

        public int length() {
            return mEnd - mStart;
        }

        public CharSequence subSequence(int start, int end) {
            char[] buf = new char[end - start];

            getChars(start, end, buf, 0);
            return new String(buf);
        }

        public String toString() {
            return subSequence(0, length()).toString();
        }

        public char charAt(int off) {
            return AndroidCharacter.getMirror(mSource.charAt(mEnd - 1 - off));
        }

        public void getChars(int start, int end, char[] dest, int destoff) {
            TextUtils.getChars(mSource, start + mStart, end + mStart,
                               dest, destoff);
            AndroidCharacter.mirror(dest, 0, end - start);

            int len = end - start;
            int n = (end - start) / 2;
            for (int i = 0; i < n; i++) {
                char tmp = dest[destoff + i];

                dest[destoff + i] = dest[destoff + len - i - 1];
                dest[destoff + len - i - 1] = tmp;
            }
        }

        private CharSequence mSource;
        private int mStart;
        private int mEnd;
    }

    private static final int ALIGNMENT_SPAN = 1;
    private static final int FOREGROUND_COLOR_SPAN = 2;
    private static final int RELATIVE_SIZE_SPAN = 3;
    private static final int SCALE_X_SPAN = 4;
    private static final int STRIKETHROUGH_SPAN = 5;
    private static final int UNDERLINE_SPAN = 6;
    private static final int STYLE_SPAN = 7;
    private static final int BULLET_SPAN = 8;
    private static final int QUOTE_SPAN = 9;
    private static final int LEADING_MARGIN_SPAN = 10;
    private static final int URL_SPAN = 11;
    private static final int BACKGROUND_COLOR_SPAN = 12;
    private static final int TYPEFACE_SPAN = 13;
    private static final int SUPERSCRIPT_SPAN = 14;
    private static final int SUBSCRIPT_SPAN = 15;
    private static final int ABSOLUTE_SIZE_SPAN = 16;
    private static final int TEXT_APPEARANCE_SPAN = 17;
    private static final int ANNOTATION = 18;

    /**
     * Flatten a CharSequence and whatever styles can be copied across processes
     * into the parcel.
     */
    public static void writeToParcel(CharSequence cs, Parcel p,
            int parcelableFlags) {
        if (cs instanceof Spanned) {
            p.writeInt(0);
            p.writeString(cs.toString());

            Spanned sp = (Spanned) cs;
            Object[] os = sp.getSpans(0, cs.length(), Object.class);

            // note to people adding to this: check more specific types
            // before more generic types.  also notice that it uses
            // "if" instead of "else if" where there are interfaces
            // so one object can be several.

            for (int i = 0; i < os.length; i++) {
                Object o = os[i];
                Object prop = os[i];

                if (prop instanceof CharacterStyle) {
                    prop = ((CharacterStyle) prop).getUnderlying();
                }

                if (prop instanceof AlignmentSpan) {
                    p.writeInt(ALIGNMENT_SPAN);
                    p.writeString(((AlignmentSpan) prop).getAlignment().name());
                    writeWhere(p, sp, o);
                }

                if (prop instanceof ForegroundColorSpan) {
                    p.writeInt(FOREGROUND_COLOR_SPAN);
                    p.writeInt(((ForegroundColorSpan) prop).getForegroundColor());
                    writeWhere(p, sp, o);
                }

                if (prop instanceof RelativeSizeSpan) {
                    p.writeInt(RELATIVE_SIZE_SPAN);
                    p.writeFloat(((RelativeSizeSpan) prop).getSizeChange());
                    writeWhere(p, sp, o);
                }

                if (prop instanceof ScaleXSpan) {
                    p.writeInt(SCALE_X_SPAN);
                    p.writeFloat(((ScaleXSpan) prop).getScaleX());
                    writeWhere(p, sp, o);
                }

                if (prop instanceof StrikethroughSpan) {
                    p.writeInt(STRIKETHROUGH_SPAN);
                    writeWhere(p, sp, o);
                }

                if (prop instanceof UnderlineSpan) {
                    p.writeInt(UNDERLINE_SPAN);
                    writeWhere(p, sp, o);
                }

                if (prop instanceof StyleSpan) {
                    p.writeInt(STYLE_SPAN);
                    p.writeInt(((StyleSpan) prop).getStyle());
                    writeWhere(p, sp, o);
                }

                if (prop instanceof LeadingMarginSpan) {
                    if (prop instanceof BulletSpan) {
                        p.writeInt(BULLET_SPAN);
                        writeWhere(p, sp, o);
                    } else if (prop instanceof QuoteSpan) {
                        p.writeInt(QUOTE_SPAN);
                        p.writeInt(((QuoteSpan) prop).getColor());
                        writeWhere(p, sp, o);
                    } else {
                        p.writeInt(LEADING_MARGIN_SPAN);
                        p.writeInt(((LeadingMarginSpan) prop).
                                           getLeadingMargin(true));
                        p.writeInt(((LeadingMarginSpan) prop).
                                           getLeadingMargin(false));
                        writeWhere(p, sp, o);
                    }
                }

                if (prop instanceof URLSpan) {
                    p.writeInt(URL_SPAN);
                    p.writeString(((URLSpan) prop).getURL());
                    writeWhere(p, sp, o);
                }

                if (prop instanceof BackgroundColorSpan) {
                    p.writeInt(BACKGROUND_COLOR_SPAN);
                    p.writeInt(((BackgroundColorSpan) prop).getBackgroundColor());
                    writeWhere(p, sp, o);
                }

                if (prop instanceof TypefaceSpan) {
                    p.writeInt(TYPEFACE_SPAN);
                    p.writeString(((TypefaceSpan) prop).getFamily());
                    writeWhere(p, sp, o);
                }

                if (prop instanceof SuperscriptSpan) {
                    p.writeInt(SUPERSCRIPT_SPAN);
                    writeWhere(p, sp, o);
                }

                if (prop instanceof SubscriptSpan) {
                    p.writeInt(SUBSCRIPT_SPAN);
                    writeWhere(p, sp, o);
                }

                if (prop instanceof AbsoluteSizeSpan) {
                    p.writeInt(ABSOLUTE_SIZE_SPAN);
                    p.writeInt(((AbsoluteSizeSpan) prop).getSize());
                    writeWhere(p, sp, o);
                }

                if (prop instanceof TextAppearanceSpan) {
                    TextAppearanceSpan tas = (TextAppearanceSpan) prop;
                    p.writeInt(TEXT_APPEARANCE_SPAN);

                    String tf = tas.getFamily();
                    if (tf != null) {
                        p.writeInt(1);
                        p.writeString(tf);
                    } else {
                        p.writeInt(0);
                    }

                    p.writeInt(tas.getTextSize());
                    p.writeInt(tas.getTextStyle());

                    ColorStateList csl = tas.getTextColor();
                    if (csl == null) {
                        p.writeInt(0);
                    } else {
                        p.writeInt(1);
                        csl.writeToParcel(p, parcelableFlags);
                    }

                    csl = tas.getLinkTextColor();
                    if (csl == null) {
                        p.writeInt(0);
                    } else {
                        p.writeInt(1);
                        csl.writeToParcel(p, parcelableFlags);
                    }

                    writeWhere(p, sp, o);
                }

                if (prop instanceof Annotation) {
                    p.writeInt(ANNOTATION);
                    p.writeString(((Annotation) prop).getKey());
                    p.writeString(((Annotation) prop).getValue());
                    writeWhere(p, sp, o);
                }
            }

            p.writeInt(0);
        } else {
            p.writeInt(1);
            if (cs != null) {
                p.writeString(cs.toString());
            } else {
                p.writeString(null);
            }
        }
    }

    private static void writeWhere(Parcel p, Spanned sp, Object o) {
        p.writeInt(sp.getSpanStart(o));
        p.writeInt(sp.getSpanEnd(o));
        p.writeInt(sp.getSpanFlags(o));
    }

    public static final Parcelable.Creator<CharSequence> CHAR_SEQUENCE_CREATOR
            = new Parcelable.Creator<CharSequence>()
    {
        /**
         * Read and return a new CharSequence, possibly with styles,
         * from the parcel.
         */
        public  CharSequence createFromParcel(Parcel p) {
            int kind = p.readInt();

            if (kind == 1)
                return p.readString();

            SpannableString sp = new SpannableString(p.readString());

            while (true) {
                kind = p.readInt();

                if (kind == 0)
                    break;

                switch (kind) {
                case ALIGNMENT_SPAN:
                    readSpan(p, sp, new AlignmentSpan.Standard(
                            Layout.Alignment.valueOf(p.readString())));
                    break;

                case FOREGROUND_COLOR_SPAN:
                    readSpan(p, sp, new ForegroundColorSpan(p.readInt()));
                    break;

                case RELATIVE_SIZE_SPAN:
                    readSpan(p, sp, new RelativeSizeSpan(p.readFloat()));
                    break;

                case SCALE_X_SPAN:
                    readSpan(p, sp, new ScaleXSpan(p.readFloat()));
                    break;

                case STRIKETHROUGH_SPAN:
                    readSpan(p, sp, new StrikethroughSpan());
                    break;

                case UNDERLINE_SPAN:
                    readSpan(p, sp, new UnderlineSpan());
                    break;

                case STYLE_SPAN:
                    readSpan(p, sp, new StyleSpan(p.readInt()));
                    break;

                case BULLET_SPAN:
                    readSpan(p, sp, new BulletSpan());
                    break;

                case QUOTE_SPAN:
                    readSpan(p, sp, new QuoteSpan(p.readInt()));
                    break;

                case LEADING_MARGIN_SPAN:
                    readSpan(p, sp, new LeadingMarginSpan.Standard(p.readInt(),
                                                                   p.readInt()));
                break;

                case URL_SPAN:
                    readSpan(p, sp, new URLSpan(p.readString()));
                    break;

                case BACKGROUND_COLOR_SPAN:
                    readSpan(p, sp, new BackgroundColorSpan(p.readInt()));
                    break;

                case TYPEFACE_SPAN:
                    readSpan(p, sp, new TypefaceSpan(p.readString()));
                    break;

                case SUPERSCRIPT_SPAN:
                    readSpan(p, sp, new SuperscriptSpan());
                    break;

                case SUBSCRIPT_SPAN:
                    readSpan(p, sp, new SubscriptSpan());
                    break;

                case ABSOLUTE_SIZE_SPAN:
                    readSpan(p, sp, new AbsoluteSizeSpan(p.readInt()));
                    break;

                case TEXT_APPEARANCE_SPAN:
                    readSpan(p, sp, new TextAppearanceSpan(
                        p.readInt() != 0
                            ? p.readString()
                            : null,
                        p.readInt(),
                        p.readInt(),
                        p.readInt() != 0
                            ? ColorStateList.CREATOR.createFromParcel(p)
                            : null,
                        p.readInt() != 0
                            ? ColorStateList.CREATOR.createFromParcel(p)
                            : null));
                    break;

                case ANNOTATION:
                    readSpan(p, sp,
                             new Annotation(p.readString(), p.readString()));
                    break;

                default:
                    throw new RuntimeException("bogus span encoding " + kind);
                }
            }

            return sp;
        }

        public CharSequence[] newArray(int size)
        {
            return new CharSequence[size];
        }
    };

    /**
     * Return a new CharSequence in which each of the source strings is
     * replaced by the corresponding element of the destinations.
     */
    public static CharSequence replace(CharSequence template,
                                       String[] sources,
                                       CharSequence[] destinations) {
        SpannableStringBuilder tb = new SpannableStringBuilder(template);

        for (int i = 0; i < sources.length; i++) {
            int where = indexOf(tb, sources[i]);

            if (where >= 0)
                tb.setSpan(sources[i], where, where + sources[i].length(),
                           Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        }

        for (int i = 0; i < sources.length; i++) {
            int start = tb.getSpanStart(sources[i]);
            int end = tb.getSpanEnd(sources[i]);

            if (start >= 0) {
                tb.replace(start, end, destinations[i]);
            }
        }

        return tb;
    }

    /**
     * Replace instances of "^1", "^2", etc. in the
     * <code>template</code> CharSequence with the corresponding
     * <code>values</code>.  "^^" is used to produce a single caret in
     * the output.  Only up to 9 replacement values are supported,
     * "^10" will be produce the first replacement value followed by a
     * '0'.
     *
     * @param template the input text containing "^1"-style
     * placeholder values.  This object is not modified; a copy is
     * returned.
     *
     * @param values CharSequences substituted into the template.  The
     * first is substituted for "^1", the second for "^2", and so on.
     *
     * @return the new CharSequence produced by doing the replacement
     *
     * @throws IllegalArgumentException if the template requests a
     * value that was not provided, or if more than 9 values are
     * provided.
     */
    public static CharSequence expandTemplate(CharSequence template,
                                              CharSequence... values) {
        if (values.length > 9) {
            throw new IllegalArgumentException("max of 9 values are supported");
        }

        SpannableStringBuilder ssb = new SpannableStringBuilder(template);

        try {
            int i = 0;
            while (i < ssb.length()) {
                if (ssb.charAt(i) == '^') {
                    char next = ssb.charAt(i+1);
                    if (next == '^') {
                        ssb.delete(i+1, i+2);
                        ++i;
                        continue;
                    } else if (Character.isDigit(next)) {
                        int which = Character.getNumericValue(next) - 1;
                        if (which < 0) {
                            throw new IllegalArgumentException(
                                "template requests value ^" + (which+1));
                        }
                        if (which >= values.length) {
                            throw new IllegalArgumentException(
                                "template requests value ^" + (which+1) +
                                "; only " + values.length + " provided");
                        }
                        ssb.replace(i, i+2, values[which]);
                        i += values[which].length();
                        continue;
                    }
                }
                ++i;
            }
        } catch (IndexOutOfBoundsException ignore) {
            // happens when ^ is the last character in the string.
        }
        return ssb;
    }

    public static int getOffsetBefore(CharSequence text, int offset) {
        if (offset == 0)
            return 0;
        if (offset == 1)
            return 0;

        char c = text.charAt(offset - 1);

        if (c >= '\uDC00' && c <= '\uDFFF') {
            char c1 = text.charAt(offset - 2);

            if (c1 >= '\uD800' && c1 <= '\uDBFF')
                offset -= 2;
            else
                offset -= 1;
        } else {
            offset -= 1;
        }

        if (text instanceof Spanned) {
            ReplacementSpan[] spans = ((Spanned) text).getSpans(offset, offset,
                                                       ReplacementSpan.class);

            for (int i = 0; i < spans.length; i++) {
                int start = ((Spanned) text).getSpanStart(spans[i]);
                int end = ((Spanned) text).getSpanEnd(spans[i]);

                if (start < offset && end > offset)
                    offset = start;
            }
        }

        return offset;
    }

    public static int getOffsetAfter(CharSequence text, int offset) {
        int len = text.length();

        if (offset == len)
            return len;
        if (offset == len - 1)
            return len;

        char c = text.charAt(offset);

        if (c >= '\uD800' && c <= '\uDBFF') {
            char c1 = text.charAt(offset + 1);

            if (c1 >= '\uDC00' && c1 <= '\uDFFF')
                offset += 2;
            else
                offset += 1;
        } else {
            offset += 1;
        }

        if (text instanceof Spanned) {
            ReplacementSpan[] spans = ((Spanned) text).getSpans(offset, offset,
                                                       ReplacementSpan.class);

            for (int i = 0; i < spans.length; i++) {
                int start = ((Spanned) text).getSpanStart(spans[i]);
                int end = ((Spanned) text).getSpanEnd(spans[i]);

                if (start < offset && end > offset)
                    offset = end;
            }
        }

        return offset;
    }

    private static void readSpan(Parcel p, Spannable sp, Object o) {
        sp.setSpan(o, p.readInt(), p.readInt(), p.readInt());
    }

    public static void copySpansFrom(Spanned source, int start, int end,
                                     Class kind,
                                     Spannable dest, int destoff) {
        if (kind == null) {
            kind = Object.class;
        }

        Object[] spans = source.getSpans(start, end, kind);

        for (int i = 0; i < spans.length; i++) {
            int st = source.getSpanStart(spans[i]);
            int en = source.getSpanEnd(spans[i]);
            int fl = source.getSpanFlags(spans[i]);

            if (st < start)
                st = start;
            if (en > end)
                en = end;

            dest.setSpan(spans[i], st - start + destoff, en - start + destoff,
                         fl);
        }
    }

    public enum TruncateAt {
        START,
        MIDDLE,
        END,
    }

    public interface EllipsizeCallback {
        /**
         * This method is called to report that the specified region of
         * text was ellipsized away by a call to {@link #ellipsize}.
         */
        public void ellipsized(int start, int end);
    }

    private static String sEllipsis = null;

    /**
     * Returns the original text if it fits in the specified width
     * given the properties of the specified Paint,
     * or, if it does not fit, a truncated
     * copy with ellipsis character added at the specified edge or center.
     */
    public static CharSequence ellipsize(CharSequence text,
                                         TextPaint p,
                                         float avail, TruncateAt where) {
        return ellipsize(text, p, avail, where, false, null);
    }

    /**
     * Returns the original text if it fits in the specified width
     * given the properties of the specified Paint,
     * or, if it does not fit, a copy with ellipsis character added 
     * at the specified edge or center.
     * If <code>preserveLength</code> is specified, the returned copy
     * will be padded with zero-width spaces to preserve the original
     * length and offsets instead of truncating.
     * If <code>callback</code> is non-null, it will be called to
     * report the start and end of the ellipsized range.
     */
    public static CharSequence ellipsize(CharSequence text,
                                         TextPaint p,
                                         float avail, TruncateAt where,
                                         boolean preserveLength,
                                         EllipsizeCallback callback) {
        if (sEllipsis == null) {
            Resources r = Resources.getSystem();
            sEllipsis = r.getString(R.string.ellipsis);
        }

        int len = text.length();

        // Use Paint.breakText() for the non-Spanned case to avoid having
        // to allocate memory and accumulate the character widths ourselves.

        if (!(text instanceof Spanned)) {
            float wid = p.measureText(text, 0, len);

            if (wid <= avail) {
                if (callback != null) {
                    callback.ellipsized(0, 0);
                }

                return text;
            }

            float ellipsiswid = p.measureText(sEllipsis);

            if (ellipsiswid > avail) {
                if (callback != null) {
                    callback.ellipsized(0, len);
                }

                if (preserveLength) {
                    char[] buf = obtain(len);
                    for (int i = 0; i < len; i++) {
                        buf[i] = '\uFEFF';
                    }
                    String ret = new String(buf, 0, len);
                    recycle(buf);
                    return ret;
                } else {
                    return "";
                }
            }

            if (where == TruncateAt.START) {
                int fit = p.breakText(text, 0, len, false,
                                      avail - ellipsiswid, null);

                if (callback != null) {
                    callback.ellipsized(0, len - fit);
                }

                if (preserveLength) {
                    return blank(text, 0, len - fit);
                } else {
                    return sEllipsis + text.toString().substring(len - fit, len);
                }
            } else if (where == TruncateAt.END) {
                int fit = p.breakText(text, 0, len, true,
                                      avail - ellipsiswid, null);

                if (callback != null) {
                    callback.ellipsized(fit, len);
                }

                if (preserveLength) {
                    return blank(text, fit, len);
                } else {
                    return text.toString().substring(0, fit) + sEllipsis;
                } 
            } else /* where == TruncateAt.MIDDLE */ {
                int right = p.breakText(text, 0, len, false,
                                        (avail - ellipsiswid) / 2, null);
                float used = p.measureText(text, len - right, len);
                int left = p.breakText(text, 0, len - right, true,
                                       avail - ellipsiswid - used, null);

                if (callback != null) {
                    callback.ellipsized(left, len - right);
                }

                if (preserveLength) {
                    return blank(text, left, len - right);
                } else {
                    String s = text.toString();
                    return s.substring(0, left) + sEllipsis +
                           s.substring(len - right, len);
                }
            }
        }

        // But do the Spanned cases by hand, because it's such a pain
        // to iterate the span transitions backwards and getTextWidths()
        // will give us the information we need.

        // getTextWidths() always writes into the start of the array,
        // so measure each span into the first half and then copy the
        // results into the second half to use later.

        float[] wid = new float[len * 2];
        TextPaint temppaint = new TextPaint();
        Spanned sp = (Spanned) text;

        int next;
        for (int i = 0; i < len; i = next) {
            next = sp.nextSpanTransition(i, len, MetricAffectingSpan.class);

            Styled.getTextWidths(p, temppaint, sp, i, next, wid, null);
            System.arraycopy(wid, 0, wid, len + i, next - i);
        }

        float sum = 0;
        for (int i = 0; i < len; i++) {
            sum += wid[len + i];
        }

        if (sum <= avail) {
            if (callback != null) {
                callback.ellipsized(0, 0);
            }

            return text;
        }

        float ellipsiswid = p.measureText(sEllipsis);

        if (ellipsiswid > avail) {
            if (callback != null) {
                callback.ellipsized(0, len);
            }

            if (preserveLength) {
                char[] buf = obtain(len);
                for (int i = 0; i < len; i++) {
                    buf[i] = '\uFEFF';
                }
                SpannableString ss = new SpannableString(new String(buf, 0, len));
                recycle(buf);
                copySpansFrom(sp, 0, len, Object.class, ss, 0);
                return ss;
            } else {
                return "";
            }
        }

        if (where == TruncateAt.START) {
            sum = 0;
            int i;

            for (i = len; i >= 0; i--) {
                float w = wid[len + i - 1];

                if (w + sum + ellipsiswid > avail) {
                    break;
                }

                sum += w;
            }

            if (callback != null) {
                callback.ellipsized(0, i);
            }

            if (preserveLength) {
                SpannableString ss = new SpannableString(blank(text, 0, i));
                copySpansFrom(sp, 0, len, Object.class, ss, 0);
                return ss;
            } else {
                SpannableStringBuilder out = new SpannableStringBuilder(sEllipsis);
                out.insert(1, text, i, len);

                return out;
            }
        } else if (where == TruncateAt.END) {
            sum = 0;
            int i;

            for (i = 0; i < len; i++) {
                float w = wid[len + i];

                if (w + sum + ellipsiswid > avail) {
                    break;
                }

                sum += w;
            }

            if (callback != null) {
                callback.ellipsized(i, len);
            }

            if (preserveLength) {
                SpannableString ss = new SpannableString(blank(text, i, len));
                copySpansFrom(sp, 0, len, Object.class, ss, 0);
                return ss;
            } else {
                SpannableStringBuilder out = new SpannableStringBuilder(sEllipsis);
                out.insert(0, text, 0, i);

                return out;
            }
        } else /* where = TruncateAt.MIDDLE */ {
            float lsum = 0, rsum = 0;
            int left = 0, right = len;

            float ravail = (avail - ellipsiswid) / 2;
            for (right = len; right >= 0; right--) {
                float w = wid[len + right - 1];

                if (w + rsum > ravail) {
                    break;
                }

                rsum += w;
            }

            float lavail = avail - ellipsiswid - rsum;
            for (left = 0; left < right; left++) {
                float w = wid[len + left];

                if (w + lsum > lavail) {
                    break;
                }

                lsum += w;
            }

            if (callback != null) {
                callback.ellipsized(left, right);
            }

            if (preserveLength) {
                SpannableString ss = new SpannableString(blank(text, left, right));
                copySpansFrom(sp, 0, len, Object.class, ss, 0);
                return ss;
            } else {
                SpannableStringBuilder out = new SpannableStringBuilder(sEllipsis);
                out.insert(0, text, 0, left);
                out.insert(out.length(), text, right, len);

                return out;
            }
        }
    }

    private static String blank(CharSequence source, int start, int end) {
        int len = source.length();
        char[] buf = obtain(len);

        if (start != 0) {
            getChars(source, 0, start, buf, 0);
        }
        if (end != len) {
            getChars(source, end, len, buf, end);
        }

        if (start != end) {
            buf[start] = '\u2026';

            for (int i = start + 1; i < end; i++) {
                buf[i] = '\uFEFF';
            }
        }
    
        String ret = new String(buf, 0, len);
        recycle(buf);

        return ret;
    }

    /**
     * Converts a CharSequence of the comma-separated form "Andy, Bob,
     * Charles, David" that is too wide to fit into the specified width
     * into one like "Andy, Bob, 2 more".
     *
     * @param text the text to truncate
     * @param p the Paint with which to measure the text
     * @param avail the horizontal width available for the text
     * @param oneMore the string for "1 more" in the current locale
     * @param more the string for "%d more" in the current locale
     */
    public static CharSequence commaEllipsize(CharSequence text,
                                              TextPaint p, float avail,
                                              String oneMore,
                                              String more) {
        int len = text.length();
        char[] buf = new char[len];
        TextUtils.getChars(text, 0, len, buf, 0);

        int commaCount = 0;
        for (int i = 0; i < len; i++) {
            if (buf[i] == ',') {
                commaCount++;
            }
        }

        float[] wid;

        if (text instanceof Spanned) {
            Spanned sp = (Spanned) text;
            TextPaint temppaint = new TextPaint();
            wid = new float[len * 2];

            int next;
            for (int i = 0; i < len; i = next) {
                next = sp.nextSpanTransition(i, len, MetricAffectingSpan.class);

                Styled.getTextWidths(p, temppaint, sp, i, next, wid, null);
                System.arraycopy(wid, 0, wid, len + i, next - i);
            }

            System.arraycopy(wid, len, wid, 0, len);
        } else {
            wid = new float[len];
            p.getTextWidths(text, 0, len, wid);
        }

        int ok = 0;
        int okRemaining = commaCount + 1;
        String okFormat = "";

        int w = 0;
        int count = 0;

        for (int i = 0; i < len; i++) {
            w += wid[i];

            if (buf[i] == ',') {
                count++;

                int remaining = commaCount - count + 1;
                float moreWid;
                String format;

                if (remaining == 1) {
                    format = " " + oneMore;
                } else {
                    format = " " + String.format(more, remaining);
                }

                moreWid = p.measureText(format);

                if (w + moreWid <= avail) {
                    ok = i + 1;
                    okRemaining = remaining;
                    okFormat = format;
                }
            }
        }

        if (w <= avail) {
            return text;
        } else {
            SpannableStringBuilder out = new SpannableStringBuilder(okFormat);
            out.insert(0, text, 0, ok);
            return out;
        }
    }

    /* package */ static char[] obtain(int len) {
        char[] buf;

        synchronized (sLock) {
            buf = sTemp;
            sTemp = null;
        }

        if (buf == null || buf.length < len)
            buf = new char[ArrayUtils.idealCharArraySize(len)];

        return buf;
    }

    /* package */ static void recycle(char[] temp) {
        if (temp.length > 1000)
            return;

        synchronized (sLock) {
            sTemp = temp;
        }
    }

    /**
     * Html-encode the string.
     * @param s the string to be encoded
     * @return the encoded string
     */
    public static String htmlEncode(String s) {
        StringBuilder sb = new StringBuilder();
        char c;
        for (int i = 0; i < s.length(); i++) {
            c = s.charAt(i);
            switch (c) {
            case '<':
                sb.append("&lt;"); //$NON-NLS-1$
                break;
            case '>':
                sb.append("&gt;"); //$NON-NLS-1$
                break;
            case '&':
                sb.append("&amp;"); //$NON-NLS-1$
                break;
            case '\\':
                sb.append("&apos;"); //$NON-NLS-1$
                break;
            case '"':
                sb.append("&quot;"); //$NON-NLS-1$
                break;
            default:
                sb.append(c);
            }
        }
        return sb.toString();
    }

    /**
     * Returns a CharSequence concatenating the specified CharSequences,
     * retaining their spans if any.
     */
    public static CharSequence concat(CharSequence... text) {
        if (text.length == 0) {
            return "";
        }

        if (text.length == 1) {
            return text[0];
        }

        boolean spanned = false;
        for (int i = 0; i < text.length; i++) {
            if (text[i] instanceof Spanned) {
                spanned = true;
                break;
            }
        }

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < text.length; i++) {
            sb.append(text[i]);
        }

        if (!spanned) {
            return sb.toString();
        }

        SpannableString ss = new SpannableString(sb);
        int off = 0;
        for (int i = 0; i < text.length; i++) {
            int len = text[i].length();

            if (text[i] instanceof Spanned) {
                copySpansFrom((Spanned) text[i], 0, len, Object.class, ss, off);
            }

            off += len;
        }

        return new SpannedString(ss);
    }

    /**
     * Returns whether the given CharSequence contains any printable characters.
     */
    public static boolean isGraphic(CharSequence str) {
        final int len = str.length();
        for (int i=0; i<len; i++) {
            int gc = Character.getType(str.charAt(i));
            if (gc != Character.CONTROL
                    && gc != Character.FORMAT
                    && gc != Character.SURROGATE
                    && gc != Character.UNASSIGNED
                    && gc != Character.LINE_SEPARATOR
                    && gc != Character.PARAGRAPH_SEPARATOR
                    && gc != Character.SPACE_SEPARATOR) {
                return true;
            }
        }
        return false;
    }

    /**
     * Returns whether this character is a printable character.
     */
    public static boolean isGraphic(char c) {
        int gc = Character.getType(c);
        return     gc != Character.CONTROL
                && gc != Character.FORMAT
                && gc != Character.SURROGATE
                && gc != Character.UNASSIGNED
                && gc != Character.LINE_SEPARATOR
                && gc != Character.PARAGRAPH_SEPARATOR
                && gc != Character.SPACE_SEPARATOR;
    }

    /**
     * Returns whether the given CharSequence contains only digits.
     */
    public static boolean isDigitsOnly(CharSequence str) {
        final int len = str.length();
        for (int i = 0; i < len; i++) {
            if (!Character.isDigit(str.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    private static Object sLock = new Object();
    private static char[] sTemp = null;
}
