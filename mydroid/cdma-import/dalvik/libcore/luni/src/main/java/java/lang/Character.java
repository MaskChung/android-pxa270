/*
 *  Licensed to the Apache Software Foundation (ASF) under one or more
 *  contributor license agreements.  See the NOTICE file distributed with
 *  this work for additional information regarding copyright ownership.
 *  The ASF licenses this file to You under the Apache License, Version 2.0
 *  (the "License"); you may not use this file except in compliance with
 *  the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package java.lang;

import java.io.Serializable;
// BEGIN android-removed
// import java.util.SortedMap;
// import java.util.TreeMap;
// import org.apache.harmony.luni.util.BinarySearch;
// END android-removed
import com.ibm.icu4jni.lang.UCharacter;

/**
 * <p>
 * Character is the wrapper for the primitive type <code>char</code>. This
 * class also provides a number of utility methods for working with
 * <code>char</code>s.
 * </p>
 * 
 * <p>
 * Character data is based upon the Unicode Standard, 4.0. The Unicode
 * specification, character tables and other information are available at <a
 * href="http://www.unicode.org/">http://www.unicode.org/</a>.
 * </p>
 * 
 * <p>
 * Unicode characters are referred to as <i>code points</i>. The range of valid
 * code points is U+0000 to U+10FFFF. The <i>Basic Multilingual Plane (BMP)</i>
 * is the code point range U+0000 to U+FFFF. Characters above the BMP are
 * referred to as <i>Supplementary Characters</i>. On the Java platform, UTF-16
 * encoding and <code>char</code> pairs are used to represent code points in
 * the supplementary range. A pair of <code>char</code> values that represent
 * a supplementary character are made up of a <i>high surrogate</i> with a
 * value range of 0xD800 to 0xDBFF and a <i>low surrogate</i> with a value
 * range of 0xDC00 to 0xDFFF.
 * </p>
 * 
 * <p>
 * On the Java platform a <code>char</code> value represents either a single
 * BMP code point or a UTF-16 unit that's part of a surrogate pair. The
 * <code>int</code> type is used to represent all Unicode code points.
 * </p>
 * 
 * @since 1.0
 */
public final class Character implements Serializable, Comparable<Character> {
    private static final long serialVersionUID = 3786198910865385080L;

    private final char value;

    /**
     * The minimum possible Character value.
     */
    public static final char MIN_VALUE = '\u0000';

    /**
     * The maximum possible Character value.
     */
    public static final char MAX_VALUE = '\uffff';

    /**
     * The minimum possible radix used for conversions between Characters and
     * integers.
     */
    public static final int MIN_RADIX = 2;

    /**
     * The maximum possible radix used for conversions between Characters and
     * integers.
     */
    public static final int MAX_RADIX = 36;

    /**
     * The <code>char</code> {@link Class} object.
     */
    @SuppressWarnings("unchecked")
    public static final Class<Character> TYPE = (Class<Character>) new char[0]
            .getClass().getComponentType();

    // Note: This can't be set to "char.class", since *that* is
    // defined to be "java.lang.Character.TYPE";

    /**
     * Unicode category constant Cn.
     */
    public static final byte UNASSIGNED = 0;

    /**
     * Unicode category constant Lu.
     */
    public static final byte UPPERCASE_LETTER = 1;

    /**
     * Unicode category constant Ll.
     */
    public static final byte LOWERCASE_LETTER = 2;

    /**
     * Unicode category constant Lt.
     */
    public static final byte TITLECASE_LETTER = 3;

    /**
     * Unicode category constant Lm.
     */
    public static final byte MODIFIER_LETTER = 4;

    /**
     * Unicode category constant Lo.
     */
    public static final byte OTHER_LETTER = 5;

    /**
     * Unicode category constant Mn.
     */
    public static final byte NON_SPACING_MARK = 6;

    /**
     * Unicode category constant Me.
     */
    public static final byte ENCLOSING_MARK = 7;

    /**
     * Unicode category constant Mc.
     */
    public static final byte COMBINING_SPACING_MARK = 8;

    /**
     * Unicode category constant Nd.
     */
    public static final byte DECIMAL_DIGIT_NUMBER = 9;

    /**
     * Unicode category constant Nl.
     */
    public static final byte LETTER_NUMBER = 10;

    /**
     * Unicode category constant No.
     */
    public static final byte OTHER_NUMBER = 11;

    /**
     * Unicode category constant Zs.
     */
    public static final byte SPACE_SEPARATOR = 12;

    /**
     * Unicode category constant Zl.
     */
    public static final byte LINE_SEPARATOR = 13;

    /**
     * Unicode category constant Zp.
     */
    public static final byte PARAGRAPH_SEPARATOR = 14;

    /**
     * Unicode category constant Cc.
     */
    public static final byte CONTROL = 15;

    /**
     * Unicode category constant Cf.
     */
    public static final byte FORMAT = 16;

    /**
     * Unicode category constant Co.
     */
    public static final byte PRIVATE_USE = 18;

    /**
     * Unicode category constant Cs.
     */
    public static final byte SURROGATE = 19;

    /**
     * Unicode category constant Pd.
     */
    public static final byte DASH_PUNCTUATION = 20;

    /**
     * Unicode category constant Ps.
     */
    public static final byte START_PUNCTUATION = 21;

    /**
     * Unicode category constant Pe.
     */
    public static final byte END_PUNCTUATION = 22;

    /**
     * Unicode category constant Pc.
     */
    public static final byte CONNECTOR_PUNCTUATION = 23;

    /**
     * Unicode category constant Po.
     */
    public static final byte OTHER_PUNCTUATION = 24;

    /**
     * Unicode category constant Sm.
     */
    public static final byte MATH_SYMBOL = 25;

    /**
     * Unicode category constant Sc.
     */
    public static final byte CURRENCY_SYMBOL = 26;

    /**
     * Unicode category constant Sk.
     */
    public static final byte MODIFIER_SYMBOL = 27;

    /**
     * Unicode category constant So.
     */
    public static final byte OTHER_SYMBOL = 28;

    /**
     * Unicode category constant Pi.
     * @since 1.4
     */
    public static final byte INITIAL_QUOTE_PUNCTUATION = 29;

    /**
     * Unicode category constant Pf.
     * @since 1.4
     */
    public static final byte FINAL_QUOTE_PUNCTUATION = 30;

    /**
     * Unicode bidirectional constant.
     * @since 1.4
     */
    public static final byte DIRECTIONALITY_UNDEFINED = -1;

    /**
     * Unicode bidirectional constant L.
     * @since 1.4
     */
    public static final byte DIRECTIONALITY_LEFT_TO_RIGHT = 0;

    /**
     * Unicode bidirectional constant R.
     * @since 1.4
     */
    public static final byte DIRECTIONALITY_RIGHT_TO_LEFT = 1;

    /**
     * Unicode bidirectional constant AL.
     * @since 1.4
     */
    public static final byte DIRECTIONALITY_RIGHT_TO_LEFT_ARABIC = 2;

    /**
     * Unicode bidirectional constant EN.
     * @since 1.4
     */
    public static final byte DIRECTIONALITY_EUROPEAN_NUMBER = 3;

    /**
     * Unicode bidirectional constant ES.
     * @since 1.4
     */
    public static final byte DIRECTIONALITY_EUROPEAN_NUMBER_SEPARATOR = 4;

    /**
     * Unicode bidirectional constant ET.
     * @since 1.4
     */
    public static final byte DIRECTIONALITY_EUROPEAN_NUMBER_TERMINATOR = 5;

    /**
     * Unicode bidirectional constant AN.
     * @since 1.4
     */
    public static final byte DIRECTIONALITY_ARABIC_NUMBER = 6;

    /**
     * Unicode bidirectional constant CS.
     * @since 1.4
     */
    public static final byte DIRECTIONALITY_COMMON_NUMBER_SEPARATOR = 7;

    /**
     * Unicode bidirectional constant NSM.
     * @since 1.4
     */
    public static final byte DIRECTIONALITY_NONSPACING_MARK = 8;

    /**
     * Unicode bidirectional constant BN.
     * @since 1.4
     */
    public static final byte DIRECTIONALITY_BOUNDARY_NEUTRAL = 9;

    /**
     * Unicode bidirectional constant B.
     * @since 1.4
     */
    public static final byte DIRECTIONALITY_PARAGRAPH_SEPARATOR = 10;

    /**
     * Unicode bidirectional constant S.
     * @since 1.4
     */
    public static final byte DIRECTIONALITY_SEGMENT_SEPARATOR = 11;

    /**
     * Unicode bidirectional constant WS.
     * @since 1.4
     */
    public static final byte DIRECTIONALITY_WHITESPACE = 12;

    /**
     * Unicode bidirectional constant ON.
     * @since 1.4
     */
    public static final byte DIRECTIONALITY_OTHER_NEUTRALS = 13;

    /**
     * Unicode bidirectional constant LRE.
     * @since 1.4
     */
    public static final byte DIRECTIONALITY_LEFT_TO_RIGHT_EMBEDDING = 14;

    /**
     * Unicode bidirectional constant LRO.
     * @since 1.4
     */
    public static final byte DIRECTIONALITY_LEFT_TO_RIGHT_OVERRIDE = 15;

    /**
     * Unicode bidirectional constant RLE.
     * @since 1.4
     */
    public static final byte DIRECTIONALITY_RIGHT_TO_LEFT_EMBEDDING = 16;

    /**
     * Unicode bidirectional constant RLO.
     * @since 1.4
     */
    public static final byte DIRECTIONALITY_RIGHT_TO_LEFT_OVERRIDE = 17;

    /**
     * Unicode bidirectional constant PDF.
     * @since 1.4
     */
    public static final byte DIRECTIONALITY_POP_DIRECTIONAL_FORMAT = 18;
    
    /**
     * <p>
     * Minimum value of a high surrogate or leading surrogate unit in UTF-16
     * encoding - <code>'\uD800'</code>.
     * </p>
     * 
     * @since 1.5
     */
    public static final char MIN_HIGH_SURROGATE = '\uD800';

    /**
     * <p>
     * Maximum value of a high surrogate or leading surrogate unit in UTF-16
     * encoding - <code>'\uDBFF'</code>.
     * </p>
     * 
     * @since 1.5
     */
    public static final char MAX_HIGH_SURROGATE = '\uDBFF';

    /**
     * <p>
     * Minimum value of a low surrogate or trailing surrogate unit in UTF-16
     * encoding - <code>'\uDC00'</code>.
     * </p>
     * 
     * @since 1.5
     */
    public static final char MIN_LOW_SURROGATE = '\uDC00';

    /**
     * Maximum value of a low surrogate or trailing surrogate unit in UTF-16
     * encoding - <code>'\uDFFF'</code>.
     * </p>
     * 
     * @since 1.5
     */
    public static final char MAX_LOW_SURROGATE = '\uDFFF';

    /**
     * <p>
     * Minimum value of a surrogate unit in UTF-16 encoding - <code>'\uD800'</code>.
     * </p>
     * 
     * @since 1.5
     */
    public static final char MIN_SURROGATE = '\uD800';

    /**
     * <p>
     * Maximum value of a surrogate unit in UTF-16 encoding - <code>'\uDFFF'</code>.
     * </p>
     * 
     * @since 1.5
     */
    public static final char MAX_SURROGATE = '\uDFFF';

    /**
     * <p>
     * Minimum value of a supplementary code point - <code>U+010000</code>.
     * </p>
     * 
     * @since 1.5
     */
    public static final int MIN_SUPPLEMENTARY_CODE_POINT = 0x10000;

    /**
     * <p>
     * Minimum code point value - <code>U+0000</code>.
     * </p>
     * 
     * @since 1.5
     */
    public static final int MIN_CODE_POINT = 0x000000;

    /**
     * <p>
     * Maximum code point value - <code>U+10FFFF</code>.
     * </p>
     * 
     * @since 1.5
     */
    public static final int MAX_CODE_POINT = 0x10FFFF;

    /**
     * <p>
     * Constant for the number of bits to represent a <code>char</code> in
     * two's compliment form.
     * </p>
     * 
     * @since 1.5
     */
    public static final int SIZE = 16;

    // BEGIN android-removed
    // removed strings containing information about chars that now are read from
    // icu data.
    // END android-removed
    
    private static final char[] typeTags = "\u0002\u0002\u0002\u0002\u0002\u0002\u0002\u0002\u0002\u0000\u0000\u0000\u0000\u0000\u0002\u0002\u0002\u0002\u0002\u0002\u0002\u0002\u0002\u0002\u0002\u0002\u0002\u0002\u0000\u0000\u0000\u0000\u0000\u0000\u0000\u0000\u0003\u0000\u0000\u0000\u0000\u0000\u0000\u0000\u0000\u0000\u0000\u0000\u0002\u0002\u0002\u0002\u0002\u0002\u0002\u0002\u0002\u0002\u0000\u0000\u0000\u0000\u0000\u0000\u0000\u0003\u0003\u0003\u0003\u0003\u0003\u0003\u0003\u0003\u0003\u0003\u0003\u0003\u0003\u0003\u0003\u0003\u0003\u0003\u0003\u0003\u0003\u0003\u0003\u0003\u0003\u0000\u0000\u0000\u0000\u0003\u0000\u0003\u0003\u0003\u0003\u0003\u0003\u0003\u0003\u0003\u0003\u0003\u0003\u0003\u0003\u0003\u0003\u0003\u0003\u0003\u0003\u0003\u0003\u0003\u0003\u0003\u0003\u0000\u0000\u0000\u0000\u0002"
            .getValue();
    
    private static final byte[] DIRECTIONALITY = new byte[] {
            DIRECTIONALITY_LEFT_TO_RIGHT, DIRECTIONALITY_RIGHT_TO_LEFT,
            DIRECTIONALITY_EUROPEAN_NUMBER,
            DIRECTIONALITY_EUROPEAN_NUMBER_SEPARATOR,
            DIRECTIONALITY_EUROPEAN_NUMBER_TERMINATOR,
            DIRECTIONALITY_ARABIC_NUMBER,
            DIRECTIONALITY_COMMON_NUMBER_SEPARATOR,
            DIRECTIONALITY_PARAGRAPH_SEPARATOR,
            DIRECTIONALITY_SEGMENT_SEPARATOR, DIRECTIONALITY_WHITESPACE,
            DIRECTIONALITY_OTHER_NEUTRALS,
            DIRECTIONALITY_LEFT_TO_RIGHT_EMBEDDING,
            DIRECTIONALITY_LEFT_TO_RIGHT_OVERRIDE,
            DIRECTIONALITY_RIGHT_TO_LEFT_ARABIC,
            DIRECTIONALITY_RIGHT_TO_LEFT_EMBEDDING,
            DIRECTIONALITY_RIGHT_TO_LEFT_OVERRIDE,
            DIRECTIONALITY_POP_DIRECTIONAL_FORMAT,
            DIRECTIONALITY_NONSPACING_MARK, DIRECTIONALITY_BOUNDARY_NEUTRAL };

    private static final int ISJAVASTART = 1;

    private static final int ISJAVAPART = 2;

    // BEGIN android-removed
    // removed strings containing information about chars that now are read from
    // icu data.
    // END android-removed
    
    /*
     * Subset represents a subset of characters.
     */
    public static class Subset {
        String name;

        protected Subset(String string) {
            if (string == null) {
                throw new NullPointerException();
            }
            name = string;
        }

        /**
         * Compares the specified object to this Subset and returns true if they
         * are equal. The object must be the same instance of Subset.
         * 
         * @param object
         *            the object to compare
         * @return true if the specified object is equal to this Subset, false
         *         otherwise
         * 
         * @see #hashCode
         */
        @Override
        public final boolean equals(Object object) {
            return super.equals(object);
        }

        /**
         * Returns an integer hash code for the receiver. Objects which are
         * equal answer the same value for this method.
         * 
         * @return the receiver's hash
         * 
         * @see #equals
         */
        @Override
        public final int hashCode() {
            return super.hashCode();
        }

        /**
         * Returns the string representation of this Subset.
         * 
         * @return the string representation of this Subset
         */
        @Override
        public final String toString() {
            return name;
        }
    }

    // BEGIN android-changed
    
    /**
     * Blocks of characters, as defined by the Unicode 4.0.1 specification. 
     * @since 1.2
     */
    public static final class UnicodeBlock extends Subset {
        /**
         * The &quot;Surrogates Area&quot; Unicode Block. 
         * @deprecated As of Java 5, this block has been replaced by {@link #HIGH_SURROGATES}, {@link #HIGH_PRIVATE_USE_SURROGATES} and {@link #LOW_SURROGATES}.
         */
        @Deprecated
        public static final UnicodeBlock SURROGATES_AREA = new UnicodeBlock("SURROGATES_AREA");
        /**
         * The &quot;Basic Latin&quot; Unicode Block. 
         * @since 1.2
         */
        public static final UnicodeBlock BASIC_LATIN = new UnicodeBlock("BASIC_LATIN");
        /**
         * The &quot;Latin-1 Supplement&quot; Unicode Block. 
         * @since 1.2
         */
        public static final UnicodeBlock LATIN_1_SUPPLEMENT = new UnicodeBlock("LATIN_1_SUPPLEMENT");
        /**
         * The &quot;Latin Extended-A&quot; Unicode Block. 
         * @since 1.2
         */
        public static final UnicodeBlock LATIN_EXTENDED_A = new UnicodeBlock("LATIN_EXTENDED_A");
        /**
         * The &quot;Latin Extended-B&quot; Unicode Block. 
         * @since 1.2
         */
        public static final UnicodeBlock LATIN_EXTENDED_B = new UnicodeBlock("LATIN_EXTENDED_B");
        /**
         * The &quot;IPA Extensions&quot; Unicode Block. 
         * @since 1.2
         */
        public static final UnicodeBlock IPA_EXTENSIONS = new UnicodeBlock("IPA_EXTENSIONS");
        /**
         * The &quot;Spacing Modifier Letters&quot; Unicode Block. 
         * @since 1.2
         */
        public static final UnicodeBlock SPACING_MODIFIER_LETTERS = new UnicodeBlock("SPACING_MODIFIER_LETTERS");
        /**
         * The &quot;Combining Diacritical Marks&quot; Unicode Block. 
         * @since 1.2
         */
        public static final UnicodeBlock COMBINING_DIACRITICAL_MARKS = new UnicodeBlock("COMBINING_DIACRITICAL_MARKS");
        /**
         * The &quot;Greek and Coptic&quot; Unicode Block. Previously referred to as &quot;Greek&quot;.
         * @since 1.2
         */
        public static final UnicodeBlock GREEK = new UnicodeBlock("GREEK");
        /**
         * The &quot;Cyrillic&quot; Unicode Block. 
         * @since 1.2
         */
        public static final UnicodeBlock CYRILLIC = new UnicodeBlock("CYRILLIC");
        /**
         * The &quot;Cyrillic Supplement&quot; Unicode Block. Previously referred to as &quot;Cyrillic Supplementary&quot;.
         * @since 1.5
         */
        public static final UnicodeBlock CYRILLIC_SUPPLEMENTARY = new UnicodeBlock("CYRILLIC_SUPPLEMENTARY");
        /**
         * The &quot;Armenian&quot; Unicode Block. 
         * @since 1.2
         */
        public static final UnicodeBlock ARMENIAN = new UnicodeBlock("ARMENIAN");
        /**
         * The &quot;Hebrew&quot; Unicode Block. 
         * @since 1.2
         */
        public static final UnicodeBlock HEBREW = new UnicodeBlock("HEBREW");
        /**
         * The &quot;Arabic&quot; Unicode Block. 
         * @since 1.2
         */
        public static final UnicodeBlock ARABIC = new UnicodeBlock("ARABIC");
        /**
         * The &quot;Syriac&quot; Unicode Block. 
         * @since 1.4
         */
        public static final UnicodeBlock SYRIAC = new UnicodeBlock("SYRIAC");
        /**
         * The &quot;Thaana&quot; Unicode Block. 
         * @since 1.4
         */
        public static final UnicodeBlock THAANA = new UnicodeBlock("THAANA");
        /**
         * The &quot;Devanagari&quot; Unicode Block. 
         * @since 1.2
         */
        public static final UnicodeBlock DEVANAGARI = new UnicodeBlock("DEVANAGARI");
        /**
         * The &quot;Bengali&quot; Unicode Block. 
         * @since 1.2
         */
        public static final UnicodeBlock BENGALI = new UnicodeBlock("BENGALI");
        /**
         * The &quot;Gurmukhi&quot; Unicode Block. 
         * @since 1.2
         */
        public static final UnicodeBlock GURMUKHI = new UnicodeBlock("GURMUKHI");
        /**
         * The &quot;Gujarati&quot; Unicode Block. 
         * @since 1.2
         */
        public static final UnicodeBlock GUJARATI = new UnicodeBlock("GUJARATI");
        /**
         * The &quot;Oriya&quot; Unicode Block. 
         * @since 1.2
         */
        public static final UnicodeBlock ORIYA = new UnicodeBlock("ORIYA");
        /**
         * The &quot;Tamil&quot; Unicode Block. 
         * @since 1.2
         */
        public static final UnicodeBlock TAMIL = new UnicodeBlock("TAMIL");
        /**
         * The &quot;Telugu&quot; Unicode Block. 
         * @since 1.2
         */
        public static final UnicodeBlock TELUGU = new UnicodeBlock("TELUGU");
        /**
         * The &quot;Kannada&quot; Unicode Block. 
         * @since 1.2
         */
        public static final UnicodeBlock KANNADA = new UnicodeBlock("KANNADA");
        /**
         * The &quot;Malayalam&quot; Unicode Block. 
         * @since 1.2
         */
        public static final UnicodeBlock MALAYALAM = new UnicodeBlock("MALAYALAM");
        /**
         * The &quot;Sinhala&quot; Unicode Block. 
         * @since 1.4
         */
        public static final UnicodeBlock SINHALA = new UnicodeBlock("SINHALA");
        /**
         * The &quot;Thai&quot; Unicode Block. 
         * @since 1.2
         */
        public static final UnicodeBlock THAI = new UnicodeBlock("THAI");
        /**
         * The &quot;Lao&quot; Unicode Block. 
         * @since 1.2
         */
        public static final UnicodeBlock LAO = new UnicodeBlock("LAO");
        /**
         * The &quot;Tibetan&quot; Unicode Block. 
         * @since 1.2
         */
        public static final UnicodeBlock TIBETAN = new UnicodeBlock("TIBETAN");
        /**
         * The &quot;Myanmar&quot; Unicode Block. 
         * @since 1.4
         */
        public static final UnicodeBlock MYANMAR = new UnicodeBlock("MYANMAR");
        /**
         * The &quot;Georgian&quot; Unicode Block. 
         * @since 1.2
         */
        public static final UnicodeBlock GEORGIAN = new UnicodeBlock("GEORGIAN");
        /**
         * The &quot;Hangul Jamo&quot; Unicode Block. 
         * @since 1.2
         */
        public static final UnicodeBlock HANGUL_JAMO = new UnicodeBlock("HANGUL_JAMO");
        /**
         * The &quot;Ethiopic&quot; Unicode Block. 
         * @since 1.4
         */
        public static final UnicodeBlock ETHIOPIC = new UnicodeBlock("ETHIOPIC");
        /**
         * The &quot;Cherokee&quot; Unicode Block. 
         * @since 1.4
         */
        public static final UnicodeBlock CHEROKEE = new UnicodeBlock("CHEROKEE");
        /**
         * The &quot;Unified Canadian Aboriginal Syllabics&quot; Unicode Block. 
         * @since 1.4
         */
        public static final UnicodeBlock UNIFIED_CANADIAN_ABORIGINAL_SYLLABICS = new UnicodeBlock("UNIFIED_CANADIAN_ABORIGINAL_SYLLABICS");
        /**
         * The &quot;Ogham&quot; Unicode Block. 
         * @since 1.4
         */
        public static final UnicodeBlock OGHAM = new UnicodeBlock("OGHAM");
        /**
         * The &quot;Runic&quot; Unicode Block. 
         * @since 1.4
         */
        public static final UnicodeBlock RUNIC = new UnicodeBlock("RUNIC");
        /**
         * The &quot;Tagalog&quot; Unicode Block. 
         * @since 1.5
         */
        public static final UnicodeBlock TAGALOG = new UnicodeBlock("TAGALOG");
        /**
         * The &quot;Hanunoo&quot; Unicode Block. 
         * @since 1.5
         */
        public static final UnicodeBlock HANUNOO = new UnicodeBlock("HANUNOO");
        /**
         * The &quot;Buhid&quot; Unicode Block. 
         * @since 1.5
         */
        public static final UnicodeBlock BUHID = new UnicodeBlock("BUHID");
        /**
         * The &quot;Tagbanwa&quot; Unicode Block. 
         * @since 1.5
         */
        public static final UnicodeBlock TAGBANWA = new UnicodeBlock("TAGBANWA");
        /**
         * The &quot;Khmer&quot; Unicode Block. 
         * @since 1.4
         */
        public static final UnicodeBlock KHMER = new UnicodeBlock("KHMER");
        /**
         * The &quot;Mongolian&quot; Unicode Block. 
         * @since 1.4
         */
        public static final UnicodeBlock MONGOLIAN = new UnicodeBlock("MONGOLIAN");
        /**
         * The &quot;Limbu&quot; Unicode Block. 
         * @since 1.5
         */
        public static final UnicodeBlock LIMBU = new UnicodeBlock("LIMBU");
        /**
         * The &quot;Tai Le&quot; Unicode Block. 
         * @since 1.5
         */
        public static final UnicodeBlock TAI_LE = new UnicodeBlock("TAI_LE");
        /**
         * The &quot;Khmer Symbols&quot; Unicode Block. 
         * @since 1.5
         */
        public static final UnicodeBlock KHMER_SYMBOLS = new UnicodeBlock("KHMER_SYMBOLS");
        /**
         * The &quot;Phonetic Extensions&quot; Unicode Block. 
         * @since 1.5
         */
        public static final UnicodeBlock PHONETIC_EXTENSIONS = new UnicodeBlock("PHONETIC_EXTENSIONS");
        /**
         * The &quot;Latin Extended Additional&quot; Unicode Block. 
         * @since 1.2
         */
        public static final UnicodeBlock LATIN_EXTENDED_ADDITIONAL = new UnicodeBlock("LATIN_EXTENDED_ADDITIONAL");
        /**
         * The &quot;Greek Extended&quot; Unicode Block. 
         * @since 1.2
         */
        public static final UnicodeBlock GREEK_EXTENDED = new UnicodeBlock("GREEK_EXTENDED");
        /**
         * The &quot;General Punctuation&quot; Unicode Block. 
         * @since 1.2
         */
        public static final UnicodeBlock GENERAL_PUNCTUATION = new UnicodeBlock("GENERAL_PUNCTUATION");
        /**
         * The &quot;Superscripts and Subscripts&quot; Unicode Block. 
         * @since 1.2
         */
        public static final UnicodeBlock SUPERSCRIPTS_AND_SUBSCRIPTS = new UnicodeBlock("SUPERSCRIPTS_AND_SUBSCRIPTS");
        /**
         * The &quot;Currency Symbols&quot; Unicode Block. 
         * @since 1.2
         */
        public static final UnicodeBlock CURRENCY_SYMBOLS = new UnicodeBlock("CURRENCY_SYMBOLS");
        /**
         * The &quot;Combining Diacritical Marks for Symbols&quot; Unicode Block. Previously referred to as &quot;Combining Marks for Symbols&quot;.
         * @since 1.2
         */
        public static final UnicodeBlock COMBINING_MARKS_FOR_SYMBOLS = new UnicodeBlock("COMBINING_MARKS_FOR_SYMBOLS");
        /**
         * The &quot;Letterlike Symbols&quot; Unicode Block. 
         * @since 1.2
         */
        public static final UnicodeBlock LETTERLIKE_SYMBOLS = new UnicodeBlock("LETTERLIKE_SYMBOLS");
        /**
         * The &quot;Number Forms&quot; Unicode Block. 
         * @since 1.2
         */
        public static final UnicodeBlock NUMBER_FORMS = new UnicodeBlock("NUMBER_FORMS");
        /**
         * The &quot;Arrows&quot; Unicode Block. 
         * @since 1.2
         */
        public static final UnicodeBlock ARROWS = new UnicodeBlock("ARROWS");
        /**
         * The &quot;Mathematical Operators&quot; Unicode Block. 
         * @since 1.2
         */
        public static final UnicodeBlock MATHEMATICAL_OPERATORS = new UnicodeBlock("MATHEMATICAL_OPERATORS");
        /**
         * The &quot;Miscellaneous Technical&quot; Unicode Block. 
         * @since 1.2
         */
        public static final UnicodeBlock MISCELLANEOUS_TECHNICAL = new UnicodeBlock("MISCELLANEOUS_TECHNICAL");
        /**
         * The &quot;Control Pictures&quot; Unicode Block. 
         * @since 1.2
         */
        public static final UnicodeBlock CONTROL_PICTURES = new UnicodeBlock("CONTROL_PICTURES");
        /**
         * The &quot;Optical Character Recognition&quot; Unicode Block. 
         * @since 1.2
         */
        public static final UnicodeBlock OPTICAL_CHARACTER_RECOGNITION = new UnicodeBlock("OPTICAL_CHARACTER_RECOGNITION");
        /**
         * The &quot;Enclosed Alphanumerics&quot; Unicode Block. 
         * @since 1.2
         */
        public static final UnicodeBlock ENCLOSED_ALPHANUMERICS = new UnicodeBlock("ENCLOSED_ALPHANUMERICS");
        /**
         * The &quot;Box Drawing&quot; Unicode Block. 
         * @since 1.2
         */
        public static final UnicodeBlock BOX_DRAWING = new UnicodeBlock("BOX_DRAWING");
        /**
         * The &quot;Block Elements&quot; Unicode Block. 
         * @since 1.2
         */
        public static final UnicodeBlock BLOCK_ELEMENTS = new UnicodeBlock("BLOCK_ELEMENTS");
        /**
         * The &quot;Geometric Shapes&quot; Unicode Block. 
         * @since 1.2
         */
        public static final UnicodeBlock GEOMETRIC_SHAPES = new UnicodeBlock("GEOMETRIC_SHAPES");
        /**
         * The &quot;Miscellaneous Symbols&quot; Unicode Block. 
         * @since 1.2
         */
        public static final UnicodeBlock MISCELLANEOUS_SYMBOLS = new UnicodeBlock("MISCELLANEOUS_SYMBOLS");
        /**
         * The &quot;Dingbats&quot; Unicode Block. 
         * @since 1.2
         */
        public static final UnicodeBlock DINGBATS = new UnicodeBlock("DINGBATS");
        /**
         * The &quot;Miscellaneous Mathematical Symbols-A&quot; Unicode Block. 
         * @since 1.5
         */
        public static final UnicodeBlock MISCELLANEOUS_MATHEMATICAL_SYMBOLS_A = new UnicodeBlock("MISCELLANEOUS_MATHEMATICAL_SYMBOLS_A");
        /**
         * The &quot;Supplemental Arrows-A&quot; Unicode Block. 
         * @since 1.5
         */
        public static final UnicodeBlock SUPPLEMENTAL_ARROWS_A = new UnicodeBlock("SUPPLEMENTAL_ARROWS_A");
        /**
         * The &quot;Braille Patterns&quot; Unicode Block. 
         * @since 1.4
         */
        public static final UnicodeBlock BRAILLE_PATTERNS = new UnicodeBlock("BRAILLE_PATTERNS");
        /**
         * The &quot;Supplemental Arrows-B&quot; Unicode Block. 
         * @since 1.5
         */
        public static final UnicodeBlock SUPPLEMENTAL_ARROWS_B = new UnicodeBlock("SUPPLEMENTAL_ARROWS_B");
        /**
         * The &quot;Miscellaneous Mathematical Symbols-B&quot; Unicode Block. 
         * @since 1.5
         */
        public static final UnicodeBlock MISCELLANEOUS_MATHEMATICAL_SYMBOLS_B = new UnicodeBlock("MISCELLANEOUS_MATHEMATICAL_SYMBOLS_B");
        /**
         * The &quot;Supplemental Mathematical Operators&quot; Unicode Block. 
         * @since 1.5
         */
        public static final UnicodeBlock SUPPLEMENTAL_MATHEMATICAL_OPERATORS = new UnicodeBlock("SUPPLEMENTAL_MATHEMATICAL_OPERATORS");
        /**
         * The &quot;Miscellaneous Symbols and Arrows&quot; Unicode Block. 
         * @since 1.2
         */
        public static final UnicodeBlock MISCELLANEOUS_SYMBOLS_AND_ARROWS = new UnicodeBlock("MISCELLANEOUS_SYMBOLS_AND_ARROWS");
        /**
         * The &quot;CJK Radicals Supplement&quot; Unicode Block. 
         * @since 1.4
         */
        public static final UnicodeBlock CJK_RADICALS_SUPPLEMENT = new UnicodeBlock("CJK_RADICALS_SUPPLEMENT");
        /**
         * The &quot;Kangxi Radicals&quot; Unicode Block. 
         * @since 1.4
         */
        public static final UnicodeBlock KANGXI_RADICALS = new UnicodeBlock("KANGXI_RADICALS");
        /**
         * The &quot;Ideographic Description Characters&quot; Unicode Block. 
         * @since 1.4
         */
        public static final UnicodeBlock IDEOGRAPHIC_DESCRIPTION_CHARACTERS = new UnicodeBlock("IDEOGRAPHIC_DESCRIPTION_CHARACTERS");
        /**
         * The &quot;CJK Symbols and Punctuation&quot; Unicode Block. 
         * @since 1.2
         */
        public static final UnicodeBlock CJK_SYMBOLS_AND_PUNCTUATION = new UnicodeBlock("CJK_SYMBOLS_AND_PUNCTUATION");
        /**
         * The &quot;Hiragana&quot; Unicode Block. 
         * @since 1.2
         */
        public static final UnicodeBlock HIRAGANA = new UnicodeBlock("HIRAGANA");
        /**
         * The &quot;Katakana&quot; Unicode Block. 
         * @since 1.2
         */
        public static final UnicodeBlock KATAKANA = new UnicodeBlock("KATAKANA");
        /**
         * The &quot;Bopomofo&quot; Unicode Block. 
         * @since 1.2
         */
        public static final UnicodeBlock BOPOMOFO = new UnicodeBlock("BOPOMOFO");
        /**
         * The &quot;Hangul Compatibility Jamo&quot; Unicode Block. 
         * @since 1.2
         */
        public static final UnicodeBlock HANGUL_COMPATIBILITY_JAMO = new UnicodeBlock("HANGUL_COMPATIBILITY_JAMO");
        /**
         * The &quot;Kanbun&quot; Unicode Block. 
         * @since 1.2
         */
        public static final UnicodeBlock KANBUN = new UnicodeBlock("KANBUN");
        /**
         * The &quot;Bopomofo Extended&quot; Unicode Block. 
         * @since 1.4
         */
        public static final UnicodeBlock BOPOMOFO_EXTENDED = new UnicodeBlock("BOPOMOFO_EXTENDED");
        /**
         * The &quot;Katakana Phonetic Extensions&quot; Unicode Block. 
         * @since 1.5
         */
        public static final UnicodeBlock KATAKANA_PHONETIC_EXTENSIONS = new UnicodeBlock("KATAKANA_PHONETIC_EXTENSIONS");
        /**
         * The &quot;Enclosed CJK Letters and Months&quot; Unicode Block. 
         * @since 1.2
         */
        public static final UnicodeBlock ENCLOSED_CJK_LETTERS_AND_MONTHS = new UnicodeBlock("ENCLOSED_CJK_LETTERS_AND_MONTHS");
        /**
         * The &quot;CJK Compatibility&quot; Unicode Block. 
         * @since 1.2
         */
        public static final UnicodeBlock CJK_COMPATIBILITY = new UnicodeBlock("CJK_COMPATIBILITY");
        /**
         * The &quot;CJK Unified Ideographs Extension A&quot; Unicode Block. 
         * @since 1.4
         */
        public static final UnicodeBlock CJK_UNIFIED_IDEOGRAPHS_EXTENSION_A = new UnicodeBlock("CJK_UNIFIED_IDEOGRAPHS_EXTENSION_A");
        /**
         * The &quot;Yijing Hexagram Symbols&quot; Unicode Block. 
         * @since 1.5
         */
        public static final UnicodeBlock YIJING_HEXAGRAM_SYMBOLS = new UnicodeBlock("YIJING_HEXAGRAM_SYMBOLS");
        /**
         * The &quot;CJK Unified Ideographs&quot; Unicode Block. 
         * @since 1.2
         */
        public static final UnicodeBlock CJK_UNIFIED_IDEOGRAPHS = new UnicodeBlock("CJK_UNIFIED_IDEOGRAPHS");
        /**
         * The &quot;Yi Syllables&quot; Unicode Block. 
         * @since 1.4
         */
        public static final UnicodeBlock YI_SYLLABLES = new UnicodeBlock("YI_SYLLABLES");
        /**
         * The &quot;Yi Radicals&quot; Unicode Block. 
         * @since 1.4
         */
        public static final UnicodeBlock YI_RADICALS = new UnicodeBlock("YI_RADICALS");
        /**
         * The &quot;Hangul Syllables&quot; Unicode Block. 
         * @since 1.2
         */
        public static final UnicodeBlock HANGUL_SYLLABLES = new UnicodeBlock("HANGUL_SYLLABLES");
        /**
         * The &quot;High Surrogates&quot; Unicode Block. 
         * This block represents code point values in the high surrogate range 0xD800 to 0xDB7F @since 1.5
         */
        public static final UnicodeBlock HIGH_SURROGATES = new UnicodeBlock("HIGH_SURROGATES");
        /**
         * The &quot;High Private Use Surrogates&quot; Unicode Block. 
         * This block represents code point values in the high surrogate range 0xDB80 to 0xDBFF @since 1.5
         */
        public static final UnicodeBlock HIGH_PRIVATE_USE_SURROGATES = new UnicodeBlock("HIGH_PRIVATE_USE_SURROGATES");
        /**
         * The &quot;Low Surrogates&quot; Unicode Block. 
         * This block represents code point values in the low surrogate range 0xDC00 to 0xDFFF @since 1.5
         */
        public static final UnicodeBlock LOW_SURROGATES = new UnicodeBlock("LOW_SURROGATES");
        /**
         * The &quot;Private Use Area&quot; Unicode Block. 
         * @since 1.2
         */
        public static final UnicodeBlock PRIVATE_USE_AREA = new UnicodeBlock("PRIVATE_USE_AREA");
        /**
         * The &quot;CJK Compatibility Ideographs&quot; Unicode Block. 
         * @since 1.2
         */
        public static final UnicodeBlock CJK_COMPATIBILITY_IDEOGRAPHS = new UnicodeBlock("CJK_COMPATIBILITY_IDEOGRAPHS");
        /**
         * The &quot;Alphabetic Presentation Forms&quot; Unicode Block. 
         * @since 1.2
         */
        public static final UnicodeBlock ALPHABETIC_PRESENTATION_FORMS = new UnicodeBlock("ALPHABETIC_PRESENTATION_FORMS");
        /**
         * The &quot;Arabic Presentation Forms-A&quot; Unicode Block. 
         * @since 1.2
         */
        public static final UnicodeBlock ARABIC_PRESENTATION_FORMS_A = new UnicodeBlock("ARABIC_PRESENTATION_FORMS_A");
        /**
         * The &quot;Variation Selectors&quot; Unicode Block. 
         * @since 1.5
         */
        public static final UnicodeBlock VARIATION_SELECTORS = new UnicodeBlock("VARIATION_SELECTORS");
        /**
         * The &quot;Combining Half Marks&quot; Unicode Block. 
         * @since 1.2
         */
        public static final UnicodeBlock COMBINING_HALF_MARKS = new UnicodeBlock("COMBINING_HALF_MARKS");
        /**
         * The &quot;CJK Compatibility Forms&quot; Unicode Block. 
         * @since 1.2
         */
        public static final UnicodeBlock CJK_COMPATIBILITY_FORMS = new UnicodeBlock("CJK_COMPATIBILITY_FORMS");
        /**
         * The &quot;Small Form Variants&quot; Unicode Block. 
         * @since 1.2
         */
        public static final UnicodeBlock SMALL_FORM_VARIANTS = new UnicodeBlock("SMALL_FORM_VARIANTS");
        /**
         * The &quot;Arabic Presentation Forms-B&quot; Unicode Block. 
         * @since 1.2
         */
        public static final UnicodeBlock ARABIC_PRESENTATION_FORMS_B = new UnicodeBlock("ARABIC_PRESENTATION_FORMS_B");
        /**
         * The &quot;Halfwidth and Fullwidth Forms&quot; Unicode Block. 
         * @since 1.2
         */
        public static final UnicodeBlock HALFWIDTH_AND_FULLWIDTH_FORMS = new UnicodeBlock("HALFWIDTH_AND_FULLWIDTH_FORMS");
        /**
         * The &quot;Specials&quot; Unicode Block. 
         * @since 1.2
         */
        public static final UnicodeBlock SPECIALS = new UnicodeBlock("SPECIALS");
        /**
         * The &quot;Linear B Syllabary&quot; Unicode Block. 
         * @since 1.2
         */
        public static final UnicodeBlock LINEAR_B_SYLLABARY = new UnicodeBlock("LINEAR_B_SYLLABARY");
        /**
         * The &quot;Linear B Ideograms&quot; Unicode Block. 
         * @since 1.5
         */
        public static final UnicodeBlock LINEAR_B_IDEOGRAMS = new UnicodeBlock("LINEAR_B_IDEOGRAMS");
        /**
         * The &quot;Aegean Numbers&quot; Unicode Block. 
         * @since 1.5
         */
        public static final UnicodeBlock AEGEAN_NUMBERS = new UnicodeBlock("AEGEAN_NUMBERS");
        /**
         * The &quot;Old Italic&quot; Unicode Block. 
         * @since 1.5
         */
        public static final UnicodeBlock OLD_ITALIC = new UnicodeBlock("OLD_ITALIC");
        /**
         * The &quot;Gothic&quot; Unicode Block. 
         * @since 1.5
         */
        public static final UnicodeBlock GOTHIC = new UnicodeBlock("GOTHIC");
        /**
         * The &quot;Ugaritic&quot; Unicode Block. 
         * @since 1.5
         */
        public static final UnicodeBlock UGARITIC = new UnicodeBlock("UGARITIC");
        /**
         * The &quot;Deseret&quot; Unicode Block. 
         * @since 1.5
         */
        public static final UnicodeBlock DESERET = new UnicodeBlock("DESERET");
        /**
         * The &quot;Shavian&quot; Unicode Block. 
         * @since 1.5
         */
        public static final UnicodeBlock SHAVIAN = new UnicodeBlock("SHAVIAN");
        /**
         * The &quot;Osmanya&quot; Unicode Block. 
         * @since 1.5
         */
        public static final UnicodeBlock OSMANYA = new UnicodeBlock("OSMANYA");
        /**
         * The &quot;Cypriot Syllabary&quot; Unicode Block. 
         * @since 1.5
         */
        public static final UnicodeBlock CYPRIOT_SYLLABARY = new UnicodeBlock("CYPRIOT_SYLLABARY");
        /**
         * The &quot;Byzantine Musical Symbols&quot; Unicode Block. 
         * @since 1.5
         */
        public static final UnicodeBlock BYZANTINE_MUSICAL_SYMBOLS = new UnicodeBlock("BYZANTINE_MUSICAL_SYMBOLS");
        /**
         * The &quot;Musical Symbols&quot; Unicode Block. 
         * @since 1.5
         */
        public static final UnicodeBlock MUSICAL_SYMBOLS = new UnicodeBlock("MUSICAL_SYMBOLS");
        /**
         * The &quot;Tai Xuan Jing Symbols&quot; Unicode Block. 
         * @since 1.5
         */
        public static final UnicodeBlock TAI_XUAN_JING_SYMBOLS = new UnicodeBlock("TAI_XUAN_JING_SYMBOLS");
        /**
         * The &quot;Mathematical Alphanumeric Symbols&quot; Unicode Block. 
         * @since 1.5
         */
        public static final UnicodeBlock MATHEMATICAL_ALPHANUMERIC_SYMBOLS = new UnicodeBlock("MATHEMATICAL_ALPHANUMERIC_SYMBOLS");
        /**
         * The &quot;CJK Unified Ideographs Extension B&quot; Unicode Block. 
         * @since 1.5
         */
        public static final UnicodeBlock CJK_UNIFIED_IDEOGRAPHS_EXTENSION_B = new UnicodeBlock("CJK_UNIFIED_IDEOGRAPHS_EXTENSION_B");
        /**
         * The &quot;CJK Compatibility Ideographs Supplement&quot; Unicode Block. 
         * @since 1.5
         */
        public static final UnicodeBlock CJK_COMPATIBILITY_IDEOGRAPHS_SUPPLEMENT = new UnicodeBlock("CJK_COMPATIBILITY_IDEOGRAPHS_SUPPLEMENT");
        /**
         * The &quot;Tags&quot; Unicode Block. 
         * @since 1.5
         */
        public static final UnicodeBlock TAGS = new UnicodeBlock("TAGS");
        /**
         * The &quot;Variation Selectors Supplement&quot; Unicode Block. 
         * @since 1.5
         */
        public static final UnicodeBlock VARIATION_SELECTORS_SUPPLEMENT = new UnicodeBlock("VARIATION_SELECTORS_SUPPLEMENT");
        /**
         * The &quot;Supplementary Private Use Area-A&quot; Unicode Block. 
         * @since 1.5
         */
        public static final UnicodeBlock SUPPLEMENTARY_PRIVATE_USE_AREA_A = new UnicodeBlock("SUPPLEMENTARY_PRIVATE_USE_AREA_A");
        /**
         * The &quot;Supplementary Private Use Area-B&quot; Unicode Block. 
         * @since 1.5
         */
        public static final UnicodeBlock SUPPLEMENTARY_PRIVATE_USE_AREA_B = new UnicodeBlock("SUPPLEMENTARY_PRIVATE_USE_AREA_B");

        /*
         * All of the UnicodeBlocks with valid ranges in ascending order.
         */
        private static UnicodeBlock[] BLOCKS;

        // END android-changed
        
        /**
         * <p>
         * Retrieves the constant that corresponds to the block name given. The
         * block names are defined by the Unicode 4.0.1 specification in the
         * <code>Blocks-4.0.1.txt</code> file.
         * </p>
         * <p>
         * Block names may be one of the following:
         * </p>
         * <ul>
         * <li>Canonical block name, as defined by the Unicode specification;
         * case-insensitive.</li>
         * <li>Canonical block name without any spaces, as defined by the
         * Unicode specification; case-insensitive.</li>
         * <li><code>UnicodeBlock</code> constant identifier. This is
         * determined by uppercasing the canonical name and replacing all spaces
         * and hyphens with underscores.</li>
         * </ul>
         * 
         * @param blockName The name of the block to retrieve.
         * @return A UnicodeBlock constant.
         * @throws NullPointerException if <code>blockName</code> is
         *         <code>null</code>.
         * @throws IllegalArgumentException if <code>blockName</code> is not a
         *         valid block name.
         * @since 1.5
         */
        public static UnicodeBlock forName(String blockName) {
            if (blockName == null) {
                throw new NullPointerException();
            }
            // BEGIN android-changed
            if (BLOCKS == null) {
                BLOCKS = UCharacter.getBlockTable();
            }
            int block = UCharacter.forName(blockName);
            if (block == -1) {
                if(blockName.equals("SURROGATES_AREA")) {
                    return SURROGATES_AREA;
                } else if(blockName.equalsIgnoreCase("greek")) {
                    return GREEK;
                } else if(blockName.equals("COMBINING_MARKS_FOR_SYMBOLS") || 
                        blockName.equals("Combining Marks for Symbols") ||
                        blockName.equals("CombiningMarksforSymbols")) {
                    return COMBINING_MARKS_FOR_SYMBOLS;
                }
                throw new IllegalArgumentException();
            }
            return BLOCKS[block];
            // END android-changed
        }
        
        /**
         * <p>
         * Retrieves the constant that contains the given <code>char</code> or
         * <code>null</code> if there is none.
         * </p>
         * 
         * @param c The character to retrieve a UnicodeBlock for.
         * @return A UnicodeBlock constant or <code>null</code>.
         */
        public static UnicodeBlock of(char c) {
            return of((int) c);
        }
        
        /**
         * <p>
         * Retrieves the constant that contains the given Unicode code point or
         * <code>null</code> if there is none.
         * </p>
         * 
         * @param codePoint The Unicode code point to retrieve a UnicodeBlock
         *        for.
         * @return A UnicodeBlock constant or <code>null</code>.
         * @throws IllegalArgumentException if <code>codePoint</code> is not a
         *         valid Unicode code point.
         * @since 1.5
         */
        public static UnicodeBlock of(int codePoint) {
            if (!isValidCodePoint(codePoint)) {
                throw new IllegalArgumentException();
            }
            // BEGIN android-changed
            if (BLOCKS == null) {
                BLOCKS = UCharacter.getBlockTable();
            }
            int block = UCharacter.of(codePoint);
            if(block == -1 || block >= BLOCKS.length) {
                return null;
            }
            return BLOCKS[block];
            // END android-changed
        }
        
        // BEGIN android-changed
        private UnicodeBlock(String blockName) {
            super(blockName);
        }
        // END android-changed
    }

    /**
     * Constructs a new instance of the receiver which represents the char
     * valued argument.
     * 
     * @param value
     *            the char to store in the new instance.
     */
    public Character(char value) {
        this.value = value;
    }

    /**
     * Returns the char value which the receiver represents.
     * 
     * @return char the value of the receiver
     */
    public char charValue() {
        return value;
    }

    /**
     * Compares the receiver to the specified Character to determine the
     * relative ordering.
     * 
     * @param c
     *            the Character
     * @return an int < 0 if this Character is less than the specified
     *         Character, 0 if they are equal, and > 0 if this Character is
     *         greater
     * @throws NullPointerException
     *             if <code>c</code> is <code>null</code>.
     * @since 1.2
     */
    public int compareTo(Character c) {
        return value - c.value;
    }
    
    /**
     * <p>
     * Returns a <code>Character</code> instance for the <code>char</code>
     * value passed. This method is preferred over the constructor, as this
     * method may maintain a cache of instances.
     * </p>
     * 
     * @param c The char value.
     * @return A <code>Character</code> instance.
     * @since 1.5
     */
    public static Character valueOf(char c) {
        if (c >= CACHE_LEN ) {
            return new Character(c);
        }
        return valueOfCache.CACHE[c];
    }

    private static final int CACHE_LEN = 512;

    static class valueOfCache {
        /*
        * Provides a cache for the 'valueOf' method. A size of 512 should cache the
        * first couple pages of Unicode, which includes the ASCII/Latin-1
        * characters, which other parts of this class are optimized for.
        */
        private static final Character[] CACHE = new Character[CACHE_LEN ];

        static {
            for(int i=0; i<CACHE.length; i++){
                CACHE[i] =  new Character((char)i);
            }
        }
    }
    /**
     * <p>
     * A test for determining if the <code>codePoint</code> is a valid Unicode
     * code point.
     * </p>
     * 
     * @param codePoint The code point to test.
     * @return A boolean value.
     * @since 1.5
     */
    public static boolean isValidCodePoint(int codePoint) {
        return (MIN_CODE_POINT <= codePoint && MAX_CODE_POINT >= codePoint);
    }

    /**
     * <p>
     * A test for determining if the <code>codePoint</code> is within the
     * supplementary code point range.
     * </p>
     * 
     * @param codePoint The code point to test.
     * @return A boolean value.
     * @since 1.5
     */
    public static boolean isSupplementaryCodePoint(int codePoint) {
        return (MIN_SUPPLEMENTARY_CODE_POINT <= codePoint && MAX_CODE_POINT >= codePoint);
    }

    /**
     * <p>
     * A test for determining if the <code>char</code> is a high
     * surrogate/leading surrogate unit that's used for representing
     * supplementary characters in UTF-16 encoding.
     * </p>
     * 
     * @param ch The <code>char</code> unit to test.
     * @return A boolean value.
     * @since 1.5
     * @see #isLowSurrogate(char)
     */
    public static boolean isHighSurrogate(char ch) {
        return (MIN_HIGH_SURROGATE <= ch && MAX_HIGH_SURROGATE >= ch);
    }

    /**
     * <p>
     * A test for determining if the <code>char</code> is a high
     * surrogate/leading surrogate unit that's used for representing
     * supplementary characters in UTF-16 encoding.
     * </p>
     * 
     * @param ch The <code>char</code> unit to test.
     * @return A boolean value.
     * @since 1.5
     * @see #isHighSurrogate(char)
     */
    public static boolean isLowSurrogate(char ch) {
        return (MIN_LOW_SURROGATE <= ch && MAX_LOW_SURROGATE >= ch);
    }

    /**
     * <p>
     * A test for determining if the <code>char</code> pair is a valid
     * surrogate pair.
     * </p>
     * 
     * @param high The high surrogate unit to test.
     * @param low The low surrogate unit to test.
     * @return A boolean value.
     * @since 1.5
     * @see #isHighSurrogate(char)
     * @see #isLowSurrogate(char)
     */
    public static boolean isSurrogatePair(char high, char low) {
        return (isHighSurrogate(high) && isLowSurrogate(low));
    }

    /**
     * <p>
     * Calculates the number of <code>char</code> values required to represent
     * the Unicode code point. This method only tests if the
     * <code>codePoint</code> is greater than or equal to <code>0x10000</code>,
     * in which case <code>2</code> is returned, otherwise <code>1</code>.
     * To test if the code point is valid, use the
     * {@link #isValidCodePoint(int)} method.
     * </p>
     * 
     * @param codePoint The code point to test.
     * @return An <code>int</code> value of 2 or 1.
     * @since 1.5
     * @see #isValidCodePoint(int)
     * @see #isSupplementaryCodePoint(int)
     */
    public static int charCount(int codePoint) {
        return (codePoint >= 0x10000 ? 2 : 1);
    }

    /**
     * <p>
     * Converts a surrogate pair into a Unicode code point. This method assume
     * that the pair are valid surrogates. If the pair are NOT valid surrogates,
     * then the result is indeterminate. The
     * {@link #isSurrogatePair(char, char)} method should be used prior to this
     * method to validate the pair.
     * </p>
     * 
     * @param high The high surrogate unit.
     * @param low The low surrogate unit.
     * @return The decoded code point.
     * @since 1.5
     * @see #isSurrogatePair(char, char)
     */
    public static int toCodePoint(char high, char low) {
        // See RFC 2781, Section 2.2
        // http://www.faqs.org/rfcs/rfc2781.html
        int h = (high & 0x3FF) << 10;
        int l = low & 0x3FF;
        return (h | l) + 0x10000;
    }

    /**
     * <p>
     * Returns the code point at the index in the <code>CharSequence</code>.
     * If <code>char</code> unit at the index is a high-surrogate unit, the
     * next index is less than the length of the sequence and the
     * <code>char</code> unit at the next index is a low surrogate unit, then
     * the code point represented by the pair is returned; otherwise the
     * <code>char</code> unit at the index is returned.
     * </p>
     * 
     * @param seq The sequence of <code>char</code> units.
     * @param index The index into the <code>seq</code> to retrieve and
     *        convert.
     * @return The Unicode code point.
     * @throws NullPointerException if <code>seq</code> is <code>null</code>.
     * @throws IndexOutOfBoundsException if the <code>index</code> is negative
     *         or greater than or equal to <code>seq.length()</code>.
     * @since 1.5
     */
    public static int codePointAt(CharSequence seq, int index) {
        if (seq == null) {
            throw new NullPointerException();
        }
        int len = seq.length();
        if (index < 0 || index >= len) {
            throw new IndexOutOfBoundsException();
        }

        char high = seq.charAt(index++);
        if (index >= len) {
            return high;
        }
        char low = seq.charAt(index);
        if (isSurrogatePair(high, low)) {
            return toCodePoint(high, low);
        }
        return high;
    }

    /**
     * <p>
     * Returns the code point at the index in the <code>char[]</code>. If
     * <code>char</code> unit at the index is a high-surrogate unit, the next
     * index is less than the length of the sequence and the <code>char</code>
     * unit at the next index is a low surrogate unit, then the code point
     * represented by the pair is returned; otherwise the <code>char</code>
     * unit at the index is returned.
     * </p>
     * 
     * @param seq The sequence of <code>char</code> units.
     * @param index The index into the <code>seq</code> to retrieve and
     *        convert.
     * @return The Unicode code point.
     * @throws NullPointerException if <code>seq</code> is <code>null</code>.
     * @throws IndexOutOfBoundsException if the <code>index</code> is negative
     *         or greater than or equal to <code>seq.length()</code>.
     * @since 1.5
     */
    public static int codePointAt(char[] seq, int index) {
        if (seq == null) {
            throw new NullPointerException();
        }
        int len = seq.length;
        if (index < 0 || index >= len) {
            throw new IndexOutOfBoundsException();
        }

        char high = seq[index++];
        if (index >= len) {
            return high;
        }
        char low = seq[index];
        if (isSurrogatePair(high, low)) {
            return toCodePoint(high, low);
        }
        return high;
    }

    /**
     * <p>
     * Returns the code point at the index in the <code>char[]</code> that's
     * within the limit. If <code>char</code> unit at the index is a
     * high-surrogate unit, the next index is less than the <code>limit</code>
     * and the <code>char</code> unit at the next index is a low surrogate
     * unit, then the code point represented by the pair is returned; otherwise
     * the <code>char</code> unit at the index is returned.
     * </p>
     * 
     * @param seq The sequence of <code>char</code> units.
     * @param index The index into the <code>seq</code> to retrieve and
     *        convert.
     * @param limit The exclusive index into the <code>seq</code> that marks
     *        the end of the units that can be used.
     * @return The Unicode code point.
     * @throws NullPointerException if <code>seq</code> is <code>null</code>.
     * @throws IndexOutOfBoundsException if the <code>index</code> is
     *         negative, greater than or equal to <code>limit</code>,
     *         <code>limit</code> is negative or <code>limit</code> is
     *         greater than the length of <code>seq</code>.
     * @since 1.5
     */
    public static int codePointAt(char[] seq, int index, int limit) {
        if (index < 0 || index >= limit || limit < 0 || limit > seq.length) {
            throw new IndexOutOfBoundsException();
        }       

        char high = seq[index++];
        if (index >= limit) {
            return high;
        }
        char low = seq[index];
        if (isSurrogatePair(high, low)) {
            return toCodePoint(high, low);
        }
        return high;
    }

    /**
     * <p>
     * Returns the Unicode code point that proceeds the <code>index</code> in
     * the <code>CharSequence</code>. If the <code>char</code> unit at
     * <code>index - 1</code> is within the low surrogate range, the value
     * <code>index - 2</code> isn't negative and the <code>char</code> unit
     * at <code>index - 2</code> is within the high surrogate range, then the
     * supplementary code point made up of the surrogate pair is returned;
     * otherwise, the <code>char</code> value at <code>index - 1</code> is
     * returned.
     * </p>
     * 
     * @param seq The <code>CharSequence</code> to search.
     * @param index The index into the <code>seq</code>.
     * @return A Unicode code point.
     * @throws NullPointerException if <code>seq</code> is <code>null</code>.
     * @throws IndexOutOfBoundsException if <code>index</code> is less than 1
     *         or greater than <code>seq.length()</code>.
     * @since 1.5
     */
    public static int codePointBefore(CharSequence seq, int index) {
        if (seq == null) {
            throw new NullPointerException();
        }
        int len = seq.length();
        if (index < 1 || index > len) {
            throw new IndexOutOfBoundsException();
        }

        char low = seq.charAt(--index);
        if (--index < 0) {
            return low;
        }
        char high = seq.charAt(index);
        if (isSurrogatePair(high, low)) {
            return toCodePoint(high, low);
        }
        return low;
    }

    /**
     * <p>
     * Returns the Unicode code point that proceeds the <code>index</code> in
     * the <code>char[]</code>. If the <code>char</code> unit at
     * <code>index - 1</code> is within the low surrogate range, the value
     * <code>index - 2</code> isn't negative and the <code>char</code> unit
     * at <code>index - 2</code> is within the high surrogate range, then the
     * supplementary code point made up of the surrogate pair is returned;
     * otherwise, the <code>char</code> value at <code>index - 1</code> is
     * returned.
     * </p>
     * 
     * @param seq The <code>char[]</code> to search.
     * @param index The index into the <code>seq</code>.
     * @return A Unicode code point.
     * @throws NullPointerException if <code>seq</code> is <code>null</code>.
     * @throws IndexOutOfBoundsException if <code>index</code> is less than 1
     *         or greater than <code>seq.length</code>.
     * @since 1.5
     */
    public static int codePointBefore(char[] seq, int index) {
        if (seq == null) {
            throw new NullPointerException();
        }
        int len = seq.length;
        if (index < 1 || index > len) {
            throw new IndexOutOfBoundsException();
        }

        char low = seq[--index];
        if (--index < 0) {
            return low;
        }
        char high = seq[index];
        if (isSurrogatePair(high, low)) {
            return toCodePoint(high, low);
        }
        return low;
    }

    /**
     * <p>
     * Returns the Unicode code point that proceeds the <code>index</code> in
     * the <code>char[]</code> and isn't less than <code>start</code>. If
     * the <code>char</code> unit at <code>index - 1</code> is within the
     * low surrogate range, the value <code>index - 2</code> isn't less than
     * <code>start</code> and the <code>char</code> unit at
     * <code>index - 2</code> is within the high surrogate range, then the
     * supplementary code point made up of the surrogate pair is returned;
     * otherwise, the <code>char</code> value at <code>index - 1</code> is
     * returned.
     * </p>
     * 
     * @param seq The <code>char[]</code> to search.
     * @param index The index into the <code>seq</code>.
     * @return A Unicode code point.
     * @throws NullPointerException if <code>seq</code> is <code>null</code>.
     * @throws IndexOutOfBoundsException if <code>index</code> is less than or
     *         equal to <code>start</code>, <code>index</code> is greater
     *         than <code>seq.length</code>, <code>start</code> is not
     *         negative and <code>start</code> is greater than
     *         <code>seq.length</code>.
     * @since 1.5
     */
    public static int codePointBefore(char[] seq, int index, int start) {
        if (seq == null) {
            throw new NullPointerException();
        }
        int len = seq.length;
        if (index <= start || index > len || start < 0 || start >= len) {
            throw new IndexOutOfBoundsException();
        }

        char low = seq[--index];
        if (--index < start) {
            return low;
        }
        char high = seq[index];
        if (isSurrogatePair(high, low)) {
            return toCodePoint(high, low);
        }
        return low;
    }

    /**
     * <p>
     * Converts the Unicode code point, <code>codePoint</code>, into a UTF-16
     * encoded sequence and copies the value(s) into the
     * <code>char[]</code> <code>dst</code>, starting at the index
     * <code>dstIndex</code>.
     * </p>
     * 
     * @param codePoint The Unicode code point to encode.
     * @param dst The <code>char[]</code> to copy the encoded value into.
     * @param dstIndex The index to start copying into <code>dst</code>.
     * @return The number of <code>char</code> value units copied into
     *         <code>dst</code>.
     * @throws IllegalArgumentException if <code>codePoint</code> is not a
     *         valid Unicode code point.
     * @throws NullPointerException if <code>dst</code> is <code>null</code>.
     * @throws IndexOutOfBoundsException if <code>dstIndex</code> is negative,
     *         greater than or equal to <code>dst.length</code> or equals
     *         <code>dst.length - 1</code> when <code>codePoint</code> is a
     *         {@link #isSupplementaryCodePoint(int) supplementary code point}.
     * @since 1.5
     */
    public static int toChars(int codePoint, char[] dst, int dstIndex) {
        if (!isValidCodePoint(codePoint)) {
            throw new IllegalArgumentException();
        }
        if (dst == null) {
            throw new NullPointerException();
        }
        if (dstIndex < 0 || dstIndex >= dst.length) {
            throw new IndexOutOfBoundsException();
        }

        if (isSupplementaryCodePoint(codePoint)) {
            if (dstIndex == dst.length - 1) {
                throw new IndexOutOfBoundsException();
            }
            // See RFC 2781, Section 2.1
            // http://www.faqs.org/rfcs/rfc2781.html
            int cpPrime = codePoint - 0x10000;
            int high = 0xD800 | ((cpPrime >> 10) & 0x3FF);
            int low = 0xDC00 | (cpPrime & 0x3FF);
            dst[dstIndex] = (char) high;
            dst[dstIndex + 1] = (char) low;
            return 2;
        }

        dst[dstIndex] = (char) codePoint;
        return 1;
    }

    /**
     * <p>
     * Converts the Unicode code point, <code>codePoint</code>, into a UTF-16
     * encoded sequence that is returned as a <code>char[]</code>.
     * </p>
     * 
     * @param codePoint The Unicode code point to encode.
     * @return The UTF-16 encoded <code>char</code> sequence; if code point is
     *         a {@link #isSupplementaryCodePoint(int) supplementary code point},
     *         then a 2 <code>char</code> array is returned, otherwise a 1
     *         <code>char</code> array is returned.
     * @throws IllegalArgumentException if <code>codePoint</code> is not a
     *         valid Unicode code point.
     * @since 1.5
     */
    public static char[] toChars(int codePoint) {
        if (!isValidCodePoint(codePoint)) {
            throw new IllegalArgumentException();
        }

        if (isSupplementaryCodePoint(codePoint)) {
            int cpPrime = codePoint - 0x10000;
            int high = 0xD800 | ((cpPrime >> 10) & 0x3FF);
            int low = 0xDC00 | (cpPrime & 0x3FF);
            return new char[] { (char) high, (char) low };
        }
        return new char[] { (char) codePoint };
    }

    /**
     * <p>
     * Counts the number of Unicode code points in the subsequence of the
     * <code>CharSequence</code>, as delineated by the
     * <code>beginIndex</code> and <code>endIndex</code>. Any surrogate
     * values with missing pair values will be counted as 1 code point.
     * </p>
     * 
     * @param seq The <code>CharSequence</code> to look through.
     * @param beginIndex The inclusive index to begin counting at.
     * @param endIndex The exclusive index to stop counting at.
     * @return The number of Unicode code points.
     * @throws NullPointerException if <code>seq</code> is <code>null</code>.
     * @throws IndexOutOfBoundsException if <code>beginIndex</code> is
     *         negative, greater than <code>seq.length()</code> or greater
     *         than <code>endIndex</code>.
     * @since 1.5
     */
    public static int codePointCount(CharSequence seq, int beginIndex,
            int endIndex) {
        if (seq == null) {
            throw new NullPointerException();
        }
        int len = seq.length();
        if (beginIndex < 0 || endIndex > len || beginIndex > endIndex) {
            throw new IndexOutOfBoundsException();
        }

        int result = 0;
        for (int i = beginIndex; i < endIndex; i++) {
            char c = seq.charAt(i);
            if (isHighSurrogate(c)) {
                if (++i < endIndex) {
                    c = seq.charAt(i);
                    if (!isLowSurrogate(c)) {
                        result++;
                    }
                }
            }
            result++;
        }
        return result;
    }

    /**
     * <p>
     * Counts the number of Unicode code points in the subsequence of the
     * <code>char[]</code>, as delineated by the <code>offset</code> and
     * <code>count</code>. Any surrogate values with missing pair values will
     * be counted as 1 code point.
     * </p>
     * 
     * @param seq The <code>char[]</code> to look through.
     * @param offset The inclusive index to begin counting at.
     * @param count The number of <code>char</code> values to look through in
     *        <code>seq</code>.
     * @return The number of Unicode code points.
     * @throws NullPointerException if <code>seq</code> is <code>null</code>.
     * @throws IndexOutOfBoundsException if <code>offset</code> or
     *         <code>count</code> is negative or if <code>endIndex</code> is
     *         greater than <code>seq.length</code>.
     * @since 1.5
     */
    public static int codePointCount(char[] seq, int offset, int count) {
        if (seq == null) {
            throw new NullPointerException();
        }
        int len = seq.length;
        int endIndex = offset + count;
        if (offset < 0 || count < 0 || endIndex > len) {
            throw new IndexOutOfBoundsException();
        }

        int result = 0;
        for (int i = offset; i < endIndex; i++) {
            char c = seq[i];
            if (isHighSurrogate(c)) {
                if (++i < endIndex) {
                    c = seq[i];
                    if (!isLowSurrogate(c)) {
                        result++;
                    }
                }
            }
            result++;
        }
        return result;
    }

    /**
     * <p>
     * Determines the index into the <code>CharSequence</code> that is offset
     * (measured in code points and specified by <code>codePointOffset</code>),
     * from the <code>index</code> argument.
     * </p>
     * 
     * @param seq The <code>CharSequence</code> to find the index within.
     * @param index The index to begin from, within the
     *        <code>CharSequence</code>.
     * @param codePointOffset The number of code points to look back or
     *        forwards; may be a negative or positive value.
     * @return The calculated index that is <code>codePointOffset</code> code
     *         points from <code>index</code>.
     * @throws NullPointerException if <code>seq</code> is <code>null</code>.
     * @throws IndexOutOfBoundsException if <code>index</code> is negative,
     *         greater than <code>seq.length()</code>, there aren't enough
     *         values in <code>seq</code> after <code>index</code> or before
     *         <code>index</code> if <code>codePointOffset</code> is
     *         negative.
     * @since 1.5
     */
    public static int offsetByCodePoints(CharSequence seq, int index,
            int codePointOffset) {
        if (seq == null) {
            throw new NullPointerException();
        }
        int len = seq.length();
        if (index < 0 || index > len) {
            throw new IndexOutOfBoundsException();
        }

        if (codePointOffset == 0) {
            return index;
        }

        if (codePointOffset > 0) {
            int codePoints = codePointOffset;
            int i = index;
            while (codePoints > 0) {
                codePoints--;
                if (i >= len) {
                    throw new IndexOutOfBoundsException();
                }
                if (isHighSurrogate(seq.charAt(i))) {
                    int next = i + 1;
                    if (next < len && isLowSurrogate(seq.charAt(next))) {
                        i++;
                    }
                }
                i++;
            }
            return i;
        }

        assert codePointOffset < 0;
        int codePoints = -codePointOffset;
        int i = index;
        while (codePoints > 0) {
            codePoints--;
            i--;
            if (i < 0) {
                throw new IndexOutOfBoundsException();
            }
            if (isLowSurrogate(seq.charAt(i))) {
                int prev = i - 1;
                if (prev >= 0 && isHighSurrogate(seq.charAt(prev))) {
                    i--;
                }
            }
        }
        return i;
    }

    /**
     * <p>
     * Determines the index into the <code>char[]</code> that is offset
     * (measured in code points and specified by <code>codePointOffset</code>),
     * from the <code>index</code> argument and is within the subsequence as
     * delineated by <code>start</code> and <code>count</code>.
     * </p>
     * 
     * @param seq The <code>char[]</code> to find the index within.
     * 
     * @param index The index to begin from, within the <code>char[]</code>.
     * @param codePointOffset The number of code points to look back or
     *        forwards; may be a negative or positive value.
     * @param start The inclusive index that marks the beginning of the
     *        subsequence.
     * @param count The number of <code>char</code> values to include within
     *        the subsequence.
     * @return The calculated index that is <code>codePointOffset</code> code
     *         points from <code>index</code>.
     * @throws NullPointerException if <code>seq</code> is <code>null</code>.
     * @throws IndexOutOfBoundsException if <code>start</code> or
     *         <code>count</code> is negative, <code>start + count</code>
     *         greater than <code>seq.length</code>, <code>index</code> is
     *         less than <code>start</code>, <code>index</code> is greater
     *         than <code>start + count</code> or there aren't enough values
     *         in <code>seq</code> after <code>index</code> or before
     *         <code>index</code> if <code>codePointOffset</code> is
     *         negative.
     * @since 1.5
     */
    public static int offsetByCodePoints(char[] seq, int start, int count,
            int index, int codePointOffset) {
        if (seq == null) {
            throw new NullPointerException();
        }
        int end = start + count;
        if (start < 0 || count < 0 || end > seq.length || index < start
                || index > end) {
            throw new IndexOutOfBoundsException();
        }

        if (codePointOffset == 0) {
            return index;
        }

        if (codePointOffset > 0) {
            int codePoints = codePointOffset;
            int i = index;
            while (codePoints > 0) {
                codePoints--;
                if (i >= end) {
                    throw new IndexOutOfBoundsException();
                }
                if (isHighSurrogate(seq[i])) {
                    int next = i + 1;
                    if (next < end && isLowSurrogate(seq[next])) {
                        i++;
                    }
                }
                i++;
            }
            return i;
        }

        assert codePointOffset < 0;
        int codePoints = -codePointOffset;
        int i = index;
        while (codePoints > 0) {
            codePoints--;
            i--;
            if (i < start) {
                throw new IndexOutOfBoundsException();
            }
            if (isLowSurrogate(seq[i])) {
                int prev = i - 1;
                if (prev >= start && isHighSurrogate(seq[prev])) {
                    i--;
                }
            }
        }
        return i;
    }

    /**
     * Convenient method to determine the value of character <code>c</code> in
     * the supplied radix. The value of <code>radix</code> must be between
     * MIN_RADIX and MAX_RADIX.
     * 
     * @param c
     *            the character
     * @param radix
     *            the radix
     * @return if <code>radix</code> lies between {@link #MIN_RADIX} and
     *         {@link #MAX_RADIX} then the value of the character in the radix,
     *         otherwise -1.
     */
    public static int digit(char c, int radix) {
        // BEGIN android-changed
        // if (radix >= MIN_RADIX && radix <= MAX_RADIX) {
        //     if (c < 128) {
        //         // Optimized for ASCII
        //         int result = -1;
        //         if ('0' <= c && c <= '9') {
        //             result = c - '0';
        //         } else if ('a' <= c && c <= 'z') {
        //             result = c - ('a' - 10);
        //         } else if ('A' <= c && c <= 'Z') {
        //             result = c - ('A' - 10);
        //         }
        //         return result < radix ? result : -1;
        //     }
        //     int result = BinarySearch.binarySearchRange(digitKeys, c);
        //     if (result >= 0 && c <= digitValues[result * 2]) {
        //         int value = (char) (c - digitValues[result * 2 + 1]);
        //         if (value >= radix) {
        //             return -1;
        //         }
        //         return value;
        //     }
        // }
        // return -1;
        return UCharacter.digit(c, radix);
        // ENd android-changed
    }
    
    /**
     * Convenient method to determine the value of character
     * <code>codePoint</code> in the supplied radix. The value of
     * <code>radix</code> must be between MIN_RADIX and MAX_RADIX.
     * 
     * @param codePoint
     *            the character, including supplementary characters
     * @param radix
     *            the radix
     * @return if <code>radix</code> lies between {@link #MIN_RADIX} and
     *         {@link #MAX_RADIX} then the value of the character in the radix,
     *         otherwise -1.
     */
    public static int digit(int codePoint, int radix) {
        return UCharacter.digit(codePoint, radix);
    }

    /**
     * Compares the argument to the receiver, and returns true if they represent
     * the <em>same</em> object using a class specific comparison.
     * <p>
     * In this case, the argument must also be a Character, and the receiver and
     * argument must represent the same char value.
     * 
     * @param object
     *            the object to compare with this object
     * @return <code>true</code> if the object is the same as this object
     *         <code>false</code> if it is different from this object
     * 
     * @see #hashCode
     */
    @Override
    public boolean equals(Object object) {
        return (object instanceof Character)
                && (value == ((Character) object).value);
    }

    /**
     * Returns the character which represents the value in the specified radix.
     * The radix must be between MIN_RADIX and MAX_RADIX inclusive.
     * 
     * @param digit
     *            the integer value
     * @param radix
     *            the radix
     * @return the character which represents the value in the radix
     */
    public static char forDigit(int digit, int radix) {
        if (MIN_RADIX <= radix && radix <= MAX_RADIX) {
            if (0 <= digit && digit < radix) {
                return (char) (digit < 10 ? digit + '0' : digit + 'a' - 10);
            }
        }
        return 0;
    }

    /**
     * Gets the numeric value of the Unicode character.
     * 
     * @param c
     *            the character
     * @return a numeric int value >= 0, -1 if there is no numeric value, -2 if
     *         the numeric value is not an int >= 0
     */
    public static int getNumericValue(char c) {
        // BEGIN android-changed
        // if (c < 128) {
        //     // Optimized for ASCII
        //     if (c >= '0' && c <= '9') {
        //         return c - '0';
        //     }
        //     if (c >= 'a' && c <= 'z') {
        //         return c - ('a' - 10);
        //     }
        //     if (c >= 'A' && c <= 'Z') {
        //         return c - ('A' - 10);
        //     }
        //     return -1;
        // }
        // int result = BinarySearch.binarySearchRange(numericKeys, c);
        // if (result >= 0 && c <= numericValues[result * 2]) {
        //     char difference = numericValues[result * 2 + 1];
        //     if (difference == 0) {
        //         return -2;
        //     }
        //     // Value is always positive, must be negative value
        //     if (difference > c) {
        //         return c - (short) difference;
        //     }
        //     return c - difference;
        // }
        // return -1;
        return UCharacter.getNumericValue(c);
        // END android-changed
    }
    
    /**
     * Gets the numeric value of the Unicode character.
     * 
     * @param codePoint
     *            the character, including supplementary characters
     * @return a numeric int value which is not negative, -1 if there is no numeric value, -2 if
     *         the numeric value is negative
     */
    public static int getNumericValue(int codePoint) {
        return UCharacter.getNumericValue(codePoint);
    }

    /**
     * Gets the general Unicode category of the specified character.
     * 
     * @param c
     *            the character
     * @return the Unicode category
     */
    public static int getType(char c) {
        // BEGIN android-changed
        // int result = BinarySearch.binarySearchRange(typeKeys, c);
        // int high = typeValues[result * 2];
        // if (c <= high) {
        //     int code = typeValues[result * 2 + 1];
        //     if (code < 0x100) {
        //         return code;
        //     }
        //     return (c & 1) == 1 ? code >> 8 : code & 0xff;
        // }
        // return UNASSIGNED;
        return getType((int)c);
        // END android-changed
    }
    
    /**
     * Gets the general Unicode category of the specified character.
     * 
     * @param codePoint
     *            the character, including supplementary characters
     * @return the Unicode category
     */
    public static int getType(int codePoint) {
        int type = UCharacter.getType(codePoint);

        // the type values returned by UCharacter are not compatible with what
        // the spec says. RI's Character type values skip the value 17.
        if (type <= Character.FORMAT) {
            return type;
        }
        return (type + 1);
    }

    /**
     * Gets the Unicode directionality of the specified character.
     * 
     * @param c
     *            the character
     * @return the Unicode directionality
     */
    public static byte getDirectionality(char c) {
        // BEGIN android-changed
        // int result = BinarySearch.binarySearchRange(bidiKeys, c);
        // int high = bidiValues[result * 2];
        // if (c <= high) {
        //     int code = bidiValues[result * 2 + 1];
        //     if (code < 0x100) {
        //         return (byte) (code - 1);
        //     }
        //     return (byte) (((c & 1) == 1 ? code >> 8 : code & 0xff) - 1);
        // }
        // return DIRECTIONALITY_UNDEFINED;
        return getDirectionality((int)c);
        // END android-changed
    }
    
    /**
     * Gets the Unicode directionality of the specified character.
     * 
     * @param codePoint
     *            the character, including supplementary characters
     * @return the Unicode directionality
     */
    public static byte getDirectionality(int codePoint) {
        if (getType(codePoint) == Character.UNASSIGNED) {
            return Character.DIRECTIONALITY_UNDEFINED;
        }
        
        byte UCDirectionality = UCharacter.getDirectionality(codePoint);       
        if (UCDirectionality == -1) {
            return -1;
        }
        return DIRECTIONALITY[UCDirectionality];
    }

    /**
     * Returns whether the specified character is mirrored
     * 
     * @param c
     *            the character
     * @return true if the character is mirrored, false otherwise
     */
    public static boolean isMirrored(char c) {
        // BEGIN android-changed
        // int value = c / 16;
        // if (value >= mirrored.length) {
        //     return false;
        // }
        // int bit = 1 << (c % 16);
        // return (mirrored[value] & bit) != 0;
        return isMirrored((int)c);
        // ENd android-changed
    }
    
    /**
     * Returns whether the specified character is mirrored
     * 
     * @param codePoint
     *            the character, including supplementary characters
     * @return true if the character is mirrored, false otherwise
     */
    public static boolean isMirrored(int codePoint) {
        return UCharacter.isMirrored(codePoint);
    }

    /**
     * Returns an integer hash code for the receiver. Any two objects which
     * answer <code>true</code> when passed to <code>equals</code> must
     * answer the same value for this method.
     * 
     * @return the receiver's hash
     * 
     * @see #equals
     */
    @Override
    public int hashCode() {
        return value;
    }

    /**
     * Returns whether the specified character is defined in the Unicode
     * specification.
     * 
     * @param c
     *            the character
     * @return true if the general Unicode category of the character is not
     *         UNASSIGNED, false otherwise
     */
    public static boolean isDefined(char c) {
        // BEGIN android-changed
        // return getType(c) != UNASSIGNED;
        return UCharacter.isDefined(c);
        // END android-changed
    }
    
    /**
     * Returns whether the specified character is defined in the Unicode
     * specification.
     * 
     * @param codePoint
     *            the character, including supplementary characters
     * @return true if the general Unicode category of the character is not
     *         UNASSIGNED, false otherwise
     */
    public static boolean isDefined(int codePoint) {
        return UCharacter.isDefined(codePoint);
    }

    /**
     * Returns whether the character is a digit.
     * 
     * @param c
     *            the character
     * @return true when the character is a digit, false otherwise
     */
    public static boolean isDigit(char c) {
        // BEGIN android-changed
        // // Optimized case for ASCII
        // if ('0' <= c && c <= '9') {
        //     return true;
        // }
        // if (c < 1632) {
        //     return false;
        // }
        // return getType(c) == DECIMAL_DIGIT_NUMBER;
        return UCharacter.isDigit(c);
        // END android-changed
    }
    
    /**
     * Returns whether the character is a digit.
     * 
     * @param codePoint
     *            the character, including supplementary characters
     * @return true when the character is a digit, false otherwise
     */
    public static boolean isDigit(int codePoint) {
        return UCharacter.isDigit(codePoint);
    }

    /**
     * Returns whether the specified character is ignorable in a Java or Unicode
     * identifier.
     * 
     * @param c
     *            the character
     * @return true when the character is ignorable, false otherwise
     */
    public static boolean isIdentifierIgnorable(char c) {
        // BEGIN android-changed
        // return (c >= 0 && c <= 8) || (c >= 0xe && c <= 0x1b)
        //         || (c >= 0x7f && c <= 0x9f) || getType(c) == FORMAT;
        return UCharacter.isIdentifierIgnorable(c);
        // END android-changed
    }
    
    /**
     * Returns whether the specified character is ignorable in a Java or Unicode
     * identifier.
     * 
     * @param codePoint
     *            the character, including supplementary characters
     * @return true when the character is ignorable, false otherwise
     */
    public static boolean isIdentifierIgnorable(int codePoint) {
        return UCharacter.isIdentifierIgnorable(codePoint);
    }

    /**
     * Returns whether the character is an ISO control character.
     * 
     * @param c
     *            the character
     * @return <code>true</code> if <code>c</code> is an ISO control
     *         character, otherwise <code>false</code>
     */
    public static boolean isISOControl(char c) {
        return isISOControl((int)c);
    }

    /**
     * Returns whether the character is an ISO control character.
     * 
     * @param c
     *            the character, including supplementary characters
     * @return <code>true</code> if <code>c</code> is an ISO control
     *         character, otherwise <code>false</code>
     */
    public static boolean isISOControl(int c) {
        return (c >= 0 && c <= 0x1f) || (c >= 0x7f && c <= 0x9f);
    }

    /**
     * Returns whether the character is a valid part of a Unicode identifier as
     * other than the first character.
     * 
     * @param c
     *            the character
     * @return true when the character is valid as part of a Java identifier,
     *         false otherwise
     */
    public static boolean isJavaIdentifierPart(char c) {
        // Optimized case for ASCII
        if (c < 128) {
            return (typeTags[c] & ISJAVAPART) != 0;
        }

        int type = getType(c);
        return (type >= UPPERCASE_LETTER && type <= OTHER_LETTER)
                || type == CURRENCY_SYMBOL || type == CONNECTOR_PUNCTUATION
                || (type >= DECIMAL_DIGIT_NUMBER && type <= LETTER_NUMBER)
                || type == NON_SPACING_MARK || type == COMBINING_SPACING_MARK
                || (c >= 0x80 && c <= 0x9f) || type == FORMAT;
    }

    /**
     * Returns whether the character is a valid part of a Unicode identifier as
     * other than the first character.
     * 
     * @param codePoint
     *            the character, including supplementary characters
     * @return true when the character is valid as part of a Java identifier,
     *         false otherwise
     */
    public static boolean isJavaIdentifierPart(int codePoint) {
        int type = getType(codePoint);
        return (type >= UPPERCASE_LETTER && type <= OTHER_LETTER)
                || type == CURRENCY_SYMBOL || type == CONNECTOR_PUNCTUATION
                || (type >= DECIMAL_DIGIT_NUMBER && type <= LETTER_NUMBER)
                || type == COMBINING_SPACING_MARK || type == NON_SPACING_MARK 
                || isIdentifierIgnorable(codePoint);
    }

    /**
     * Returns whether the character is a valid start of a Unicode identifier
     * 
     * @param c
     *            the character
     * @return true when the character is a valid start of a Java identifier,
     *         false otherwise
     */
    public static boolean isJavaIdentifierStart(char c) {
        // Optimized case for ASCII
        if (c < 128) {
            return (typeTags[c] & ISJAVASTART) != 0;
        }

        int type = getType(c);
        return (type >= UPPERCASE_LETTER && type <= OTHER_LETTER)
                || type == CURRENCY_SYMBOL || type == CONNECTOR_PUNCTUATION
                || type == LETTER_NUMBER;
    }
    
    /**
     * Returns whether the character is a valid start of a Unicode identifier
     * 
     * @param codePoint
     *            the character, including supplementary characters
     * @return true when the character is a valid start of a Java identifier,
     *         false otherwise
     */
    public static boolean isJavaIdentifierStart(int codePoint) {
        int type = getType(codePoint);
        return isLetter(codePoint) || type == CURRENCY_SYMBOL
                || type == CONNECTOR_PUNCTUATION || type == LETTER_NUMBER;
    }

    /**
     * Returns whether the character is a Java letter.
     * 
     * @deprecated Use {@link #isJavaIdentifierStart(char)}
     */
    @Deprecated
    public static boolean isJavaLetter(char c) {
        return isJavaIdentifierStart(c);
    }

    /**
     * Returns whether the character is a Java letter or digit character.
     * 
     * @deprecated Use {@link #isJavaIdentifierPart(char)}
     */
    @Deprecated
    public static boolean isJavaLetterOrDigit(char c) {
        return isJavaIdentifierPart(c);
    }

    /**
     * Returns whether the character is a letter.
     * 
     * @param c
     *            the character
     * @return true when the character is a letter, false otherwise
     */
    public static boolean isLetter(char c) {
        // BEGIN android-changed
        // if (('A' <= c && c <= 'Z') || ('a' <= c && c <= 'z')) {
        //     return true;
        // }
        // if (c < 128) {
        //     return false;
        // }
        // int type = getType(c);
        // return type >= UPPERCASE_LETTER && type <= OTHER_LETTER;
        return UCharacter.isLetter(c);
        // END android-changed
    }
    
    /**
     * Returns whether the character is a letter.
     * 
     * @param codePoint
     *            the character, including supplementary characters
     * @return true when the character is a letter, false otherwise
     */
    public static boolean isLetter(int codePoint) {
        return UCharacter.isLetter(codePoint);
    }

    /**
     * Returns whether the character is a letter or a digit.
     * 
     * @param c
     *            the character
     * @return true when the character is a letter or a digit, false otherwise
     */
    public static boolean isLetterOrDigit(char c) {
        // BEGIN android-changed
        // int type = getType(c);
        // return (type >= UPPERCASE_LETTER && type <= OTHER_LETTER)
        //         || type == DECIMAL_DIGIT_NUMBER;
        return UCharacter.isLetterOrDigit(c);
        // END andorid-changed
    }
    
    /**
     * Returns whether the character is a letter or a digit.
     * 
     * @param codePoint
     *            the character, including supplementary characters
     * @return true when the character is a letter or a digit, false otherwise
     */
    public static boolean isLetterOrDigit(int codePoint) {
        return UCharacter.isLetterOrDigit(codePoint);
    }

    /**
     * Returns whether the character is a lower case letter.
     * 
     * @param c
     *            the character
     * @return true when the character is a lower case letter, false otherwise
     */
    public static boolean isLowerCase(char c) {
        // BEGIN android-changed
        // // Optimized case for ASCII
        // if ('a' <= c && c <= 'z') {
        //     return true;
        // }
        // if (c < 128) {
        //     return false;
        // }
        // 
        // return getType(c) == LOWERCASE_LETTER;
        return UCharacter.isLowerCase(c);
        // END android-changed
    }
    
    /**
     * Returns whether the character is a lower case letter.
     * 
     * @param codePoint
     *            the character, including supplementary characters
     * @return true when the character is a lower case letter, false otherwise
     */
    public static boolean isLowerCase(int codePoint) {
        return UCharacter.isLowerCase(codePoint);
    }

    /**
     * Returns whether the character is a Java space.
     * 
     * @deprecated Use {@link #isWhitespace(char)}
     */
    @Deprecated
    public static boolean isSpace(char c) {
        return c == '\n' || c == '\t' || c == '\f' || c == '\r' || c == ' ';
    }

    /**
     * Returns whether the character is a Unicode space character. A member of
     * one of the Unicode categories Space Separator, Line Separator, or
     * Paragraph Separator.
     * 
     * @param c
     *            the character
     * @return true when the character is a Unicode space character, false
     *         otherwise
     */
    public static boolean isSpaceChar(char c) {
        // BEGIN android-changed
        // if (c == 0x20 || c == 0xa0 || c == 0x1680) {
        //     return true;
        // }
        // if (c < 0x2000) {
        //     return false;
        // }
        // return c <= 0x200b || c == 0x2028 || c == 0x2029 || c == 0x202f
        //         || c == 0x3000;
        return UCharacter.isSpaceChar(c);
        // END android-changed
    }
    
    /**
     * Returns whether the character is a Unicode space character. A member of
     * one of the Unicode categories Space Separator, Line Separator, or
     * Paragraph Separator.
     * 
     * @param codePoint
     *            the character, including supplementary characters
     * @return true when the character is a Unicode space character, false
     *         otherwise
     */
    public static boolean isSpaceChar(int codePoint) {
        return UCharacter.isSpaceChar(codePoint);
    }

    /**
     * Returns whether the character is a titlecase character.
     * 
     * @param c
     *            the character
     * @return true when the character is a titlecase character, false
     *         otherwise
     */
    public static boolean isTitleCase(char c) {
        // BEGIN android-changed
        // if (c == '\u01c5' || c == '\u01c8' || c == '\u01cb' || c == '\u01f2') {
        //     return true;
        // }
        // if (c >= '\u1f88' && c <= '\u1ffc') {
        //     // 0x1f88 - 0x1f8f, 0x1f98 - 0x1f9f, 0x1fa8 - 0x1faf
        //     if (c > '\u1faf') {
        //         return c == '\u1fbc' || c == '\u1fcc' || c == '\u1ffc';
        //     }
        //     int last = c & 0xf;
        //     return last >= 8 && last <= 0xf;
        // }
        // return false;
        return UCharacter.isTitleCase(c);
        // END android-changed
    }
    
    /**
     * Returns whether the character is a titlecase character.
     * 
     * @param codePoint
     *            the character, including supplementary characters
     * @return true when the character is a titlecase character, false
     *         otherwise
     */
    public static boolean isTitleCase(int codePoint) {
        return UCharacter.isTitleCase(codePoint);
    }

    /**
     * Returns whether the character is valid as part of a Unicode identifier as
     * other than the first character.
     * 
     * @param c
     *            the character
     * @return true when the character is valid as part of a Unicode identifier,
     *         false otherwise
     */
    public static boolean isUnicodeIdentifierPart(char c) {
        // BEGIN android-changed
        // int type = getType(c);
        // return (type >= UPPERCASE_LETTER && type <= OTHER_LETTER)
        //         || type == CONNECTOR_PUNCTUATION
        //         || (type >= DECIMAL_DIGIT_NUMBER && type <= LETTER_NUMBER)
        //         || type == NON_SPACING_MARK || type == COMBINING_SPACING_MARK
        //         || isIdentifierIgnorable(c);
        return UCharacter.isUnicodeIdentifierPart(c);
        // END android-changed
    }
    
    /**
     * Returns whether the character is valid as part of a Unicode identifier as
     * other than the first character.
     * 
     * @param codePoint
     *            the character, including supplementary characters
     * @return true when the character is valid as part of a Unicode identifier,
     *         false otherwise
     */
    public static boolean isUnicodeIdentifierPart(int codePoint) {
        return UCharacter.isUnicodeIdentifierPart(codePoint);
    }

    /**
     * Returns whether the character is a valid initial character for a Unicode
     * identifier.
     * 
     * @param c
     *            the character
     * @return true when the character is a valid start of a Unicode identifier,
     *         false otherwise
     */
    public static boolean isUnicodeIdentifierStart(char c) {
        // BEGIN android-changed
        // int type = getType(c);
        // return (type >= UPPERCASE_LETTER && type <= OTHER_LETTER)
        //         || type == LETTER_NUMBER;
        return UCharacter.isUnicodeIdentifierStart(c);
        // END android-changed
    }
    
    /**
     * Returns whether the character is a valid initial character for a Unicode
     * identifier.
     * 
     * @param codePoint
     *            the character, including supplementary characters
     * @return true when the character is a valid start of a Unicode identifier,
     *         false otherwise
     */
    public static boolean isUnicodeIdentifierStart(int codePoint) {
        return UCharacter.isUnicodeIdentifierStart(codePoint);
    }

    /**
     * Returns whether the character is an upper case letter.
     * 
     * @param c
     *            the character
     * @return true when the character is a upper case letter, false otherwise
     */
    public static boolean isUpperCase(char c) {
        // BEGIN android-changed
        // // Optimized case for ASCII
        // if ('A' <= c && c <= 'Z') {
        //     return true;
        // }
        // if (c < 128) {
        //     return false;
        // }
        // 
        // return getType(c) == UPPERCASE_LETTER;
        return UCharacter.isUpperCase(c);
        // END android-changed
    }
    
    /**
     * Returns whether the character is an upper case letter.
     * 
     * @param codePoint
     *            the character, including supplementary characters
     * @return true when the character is a upper case letter, false otherwise
     */
    public static boolean isUpperCase(int codePoint) {
        return UCharacter.isUpperCase(codePoint);
    }

    /**
     * Returns whether the character is a whitespace character in Java.
     * 
     * @param c
     *            the character
     * @return true if the supplied <code>c</code> is a whitespace character
     *         in Java, otherwise false.
     */
    public static boolean isWhitespace(char c) {
        // BEGIN android-changed
        // // Optimized case for ASCII
        // if ((c >= 0x1c && c <= 0x20) || (c >= 0x9 && c <= 0xd)) {
        //     return true;
        // }
        // if (c == 0x1680) {
        //     return true;
        // }
        // if (c < 0x2000 || c == 0x2007) {
        //     return false;
        // }
        // return c <= 0x200b || c == 0x2028 || c == 0x2029 || c == 0x3000;
        return UCharacter.isWhitespace(c);
        // END android-changed
    }
    
    /**
     * Returns whether the character is a whitespace character in Java.
     * 
     * @param codePoint
     *            the character, including supplementary characters
     * @return true if the supplied <code>c</code> is a whitespace character
     *         in Java, otherwise false.
     */
    public static boolean isWhitespace(int codePoint) {
        //FIXME depends on ICU when the codePoint is '\u2007'
        return UCharacter.isWhitespace(codePoint);
    }

    /**
     * Reverse the order of the first and second bytes in character
     * @param c
     *            the character
     * @return    the character with reordered bytes.
     */
    public static char reverseBytes(char c) {
        return (char)((c<<8) | (c>>8));
    }

    /**
     * Returns the lower case equivalent for the character when the character is
     * an upper case letter, otherwise returns the character.
     * 
     * @param c
     *            the character
     * @return if c is not a lower case character then its lower case
     *         counterpart, otherwise just c
     */
    public static char toLowerCase(char c) {
        // BEGIN android-changed
        // // Optimized case for ASCII
        // if ('A' <= c && c <= 'Z') {
        //     return (char) (c + ('a' - 'A'));
        // }
        // if (c < 128) {
        //     return c;
        // }
        // 
        // int result = BinarySearch.binarySearchRange(lowercaseKeys, c);
        // if (result >= 0) {
        //     boolean by2 = false;
        //     char start = lowercaseKeys.charAt(result);
        //     char end = lowercaseValues[result * 2];
        //     if ((start & 0x8000) != (end & 0x8000)) {
        //         end ^= 0x8000;
        //         by2 = true;
        //     }
        //     if (c <= end) {
        //         if (by2 && (c & 1) != (start & 1)) {
        //             return c;
        //         }
        //         char mapping = lowercaseValues[result * 2 + 1];
        //         return (char) (c + mapping);
        //     }
        // }
        // return c;
        return (char)UCharacter.toLowerCase(c);
        // END android-changed
    }
    
    /**
     * Returns the lower case equivalent for the character when the character is
     * an upper case letter, otherwise returns the character.
     * 
     * @param codePoint
     *            the character, including supplementary characters
     * @return if codePoint is not a lower case character then its lower case
     *         counterpart, otherwise just codePoint
     */
    public static int toLowerCase(int codePoint) {
        return UCharacter.toLowerCase(codePoint);
    }

    /**
     * Returns a string containing a concise, human-readable description of the
     * receiver.
     * 
     * @return a printable representation for the receiver.
     */
    @Override
    public String toString() {
        return String.valueOf(value);
    }

    /**
     * Converts the specified character to its string representation.
     * 
     * @param value
     *            the character
     * @return the character converted to a string
     */
    public static String toString(char value) {
        return String.valueOf(value);
    }

    /**
     * Returns the title case equivalent for the character, otherwise returns the
     * character.
     * 
     * @param c
     *            the character
     * @return the title case equivalent of the character
     */
    public static char toTitleCase(char c) {
        // BEGIN android-changed
        // if (isTitleCase(c)) {
        //     return c;
        // }
        // int result = BinarySearch.binarySearch(titlecaseKeys, c);
        // if (result >= 0) {
        //     return titlecaseValues[result];
        // }
        // return toUpperCase(c);
        return (char)UCharacter.toTitleCase(c);
        // ENd android-changed
    }
    
    /**
     * Returns the title case equivalent for the character, otherwise returns the
     * character.
     * 
     * @param codePoint
     *            the character
     * @return the title case equivalent of the character
     */
    public static int toTitleCase(int codePoint) {
        return UCharacter.toTitleCase(codePoint);
    }

    /**
     * Returns the upper case equivalent for the character when the character is
     * a lower case letter, otherwise returns the character.
     * 
     * @param c
     *            the character
     * @return if c is not an upper case character then its upper case
     *         counterpart, otherwise just c
     */
    public static char toUpperCase(char c) {
        // BEGIN android-changed
        // // Optimized case for ASCII
        // if ('a' <= c && c <= 'z') {
        //     return (char) (c - ('a' - 'A'));
        // }
        // if (c < 128) {
        //     return c;
        // }
        // 
        // int result = BinarySearch.binarySearchRange(uppercaseKeys, c);
        // if (result >= 0) {
        //     boolean by2 = false;
        //     char start = uppercaseKeys.charAt(result);
        //     char end = uppercaseValues[result * 2];
        //     if ((start & 0x8000) != (end & 0x8000)) {
        //         end ^= 0x8000;
        //         by2 = true;
        //     }
        //     if (c <= end) {
        //         if (by2 && (c & 1) != (start & 1)) {
        //             return c;
        //         }
        //         char mapping = uppercaseValues[result * 2 + 1];
        //         return (char) (c + mapping);
        //     }
        // }
        // return c;
        return (char)UCharacter.toUpperCase(c);
        // END android-changed
    }
    
    /**
     * Returns the upper case equivalent for the character when the character is
     * a lower case letter, otherwise returns the character.
     * 
     * @param codePoint
     *            the character, including supplementary characters
     * @return if codePoint is not an upper case character then its upper case
     *         counterpart, otherwise just codePoint
     */
    public static int toUpperCase(int codePoint) {
        return UCharacter.toUpperCase(codePoint);
    }

}
