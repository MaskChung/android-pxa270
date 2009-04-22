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

package tests.api.java.util;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class LocaleTest extends junit.framework.TestCase {

    Locale testLocale;

    Locale l;
    
    Locale defaultLocale;

    /**
     * @tests java.util.Locale#Locale(java.lang.String, java.lang.String)
     */
    public void test_ConstructorLjava_lang_StringLjava_lang_String() {
        // Test for method java.util.Locale(java.lang.String, java.lang.String)
        Locale x = new Locale("xx", "CV");
        assertTrue("Failed to create Locale", x.getCountry().equals("CV")
                && x.getVariant().equals(""));
    }

    /**
     * @tests java.util.Locale#Locale(java.lang.String, java.lang.String,
     *        java.lang.String)
     */
    public void test_ConstructorLjava_lang_StringLjava_lang_StringLjava_lang_String() {
        // Test for method java.util.Locale(java.lang.String, java.lang.String,
        // java.lang.String)
        Locale x = new Locale("xx", "CV", "ZZ");
        assertTrue("Failed to create Locale", x.getLanguage().equals("xx")
                && (x.getCountry().equals("CV") && x.getVariant().equals("ZZ")));
                try {
                   new Locale(null, "CV", "ZZ");
                   fail("expected NullPointerException with 1st parameter == null");
                } catch(NullPointerException e) {
                }

                try {
                   new Locale("xx", null, "ZZ");
                   fail("expected NullPointerException with 2nd parameter == null");
                } catch(NullPointerException e) {
                }

                try {
                   new Locale("xx", "CV", null);
                   fail("expected NullPointerException with 3rd parameter == null");
                } catch(NullPointerException e) {
                }
    }

    /**
     * @tests java.util.Locale#clone()
     */
    public void test_clone() {
        // Test for method java.lang.Object java.util.Locale.clone()
        assertTrue("Clone failed", l.clone().equals(l));
    }

    /**
     * @tests java.util.Locale#equals(java.lang.Object)
     */
    public void test_equalsLjava_lang_Object() {
        // Test for method boolean java.util.Locale.equals(java.lang.Object)
        Locale l2 = new Locale("en", "CA", "WIN32");
        assertTrue("Same object returned false", testLocale.equals(testLocale));
        assertTrue("Same values returned false", testLocale.equals(l2));
        assertTrue("Different locales returned true", !testLocale.equals(l));

    }

    /**
     * @tests java.util.Locale#getAvailableLocales()
     */
    public void test_getAvailableLocales() {
// BEGIN android-changed
        // Test for method java.util.Locale []
        // java.util.Locale.getAvailableLocales()
        // Assumes there will generally be about 10+ available locales...
        // even in minimal configurations for android
        try {
            Locale[] locales = Locale.getAvailableLocales();
            assertTrue("Wrong number of locales: " + locales.length, locales.length > 10);
            // regression test for HARMONY-1514
            // HashSet can filter duplicate locales
            Set<Locale> localesSet = new HashSet<Locale>(Arrays.asList(locales));
            assertEquals(localesSet.size(), locales.length);            
        } catch (Exception e) {
            fail("Exception during test : " + e.getMessage());
        }
// END android-changed
    }

    /**
     * @tests java.util.Locale#getCountry()
     */
    public void test_getCountry() {
        // Test for method java.lang.String java.util.Locale.getCountry()
        assertTrue("Returned incorrect country: " + testLocale.getCountry(),
                testLocale.getCountry().equals("CA"));
    }

    /**
     * @tests java.util.Locale#getDefault()
     */
    public void test_getDefault() {
        // Test for method java.util.Locale java.util.Locale.getDefault()
        assertTrue("returns copy", Locale.getDefault() == Locale.getDefault());
        Locale org = Locale.getDefault();
        Locale.setDefault(l);
        Locale x = Locale.getDefault();
        Locale.setDefault(org);
        assertEquals("Failed to get locale", "fr_CA_WIN32", x.toString());
    }

    /**
     * @tests java.util.Locale#getDisplayCountry()
     */
    public void test_getDisplayCountry() {
        // Test for method java.lang.String java.util.Locale.getDisplayCountry()
        assertTrue("Returned incorrect country: "
                + testLocale.getDisplayCountry(), testLocale
                .getDisplayCountry().equals("Canada"));
        
        // Regression for Harmony-1146
        Locale l_countryCD = new Locale("", "CD"); //$NON-NLS-1$ //$NON-NLS-2$
// BEGIN android-changed
// ICU has different display name for countries
//                assertEquals("The Democratic Republic Of Congo", //$NON-NLS-1$
//                        l_countryCD.getDisplayCountry());
        assertEquals("Congo - Kinshasa", //$NON-NLS-1$
              l_countryCD.getDisplayCountry());
// END android-changed
    }

    /**
     * @tests java.util.Locale#getDisplayCountry(java.util.Locale)
     */
    public void test_getDisplayCountryLjava_util_Locale() {
        // Test for method java.lang.String
        // java.util.Locale.getDisplayCountry(java.util.Locale)
        assertEquals("Returned incorrect country", "Italie", Locale.ITALY
                .getDisplayCountry(l));
    }

    /**
     * @tests java.util.Locale#getDisplayLanguage()
     */
    public void test_getDisplayLanguage() {
        // Test for method java.lang.String
        // java.util.Locale.getDisplayLanguage()
        assertTrue("Returned incorrect language: "
                + testLocale.getDisplayLanguage(), testLocale
                .getDisplayLanguage().equals("English"));
        
        // Regression for Harmony-1146
        Locale l_languageAE = new Locale("ae", ""); //$NON-NLS-1$ //$NON-NLS-2$
        assertEquals("Avestan", l_languageAE.getDisplayLanguage()); //$NON-NLS-1$
    }

    /**
     * @tests java.util.Locale#getDisplayLanguage(java.util.Locale)
     */
    public void test_getDisplayLanguageLjava_util_Locale() {
        // Test for method java.lang.String
        // java.util.Locale.getDisplayLanguage(java.util.Locale)
        assertTrue("Returned incorrect language: "
                + testLocale.getDisplayLanguage(l), testLocale
                .getDisplayLanguage(l).equals("anglais"));
    }

    /**
     * @tests java.util.Locale#getDisplayName()
     */
    public void test_getDisplayName() {
        // Test for method java.lang.String java.util.Locale.getDisplayName()
        assertTrue("Returned incorrect name: " + testLocale.getDisplayName(),
                testLocale.getDisplayName().equals("English (Canada,WIN32)"));
    }

    /**
     * @tests java.util.Locale#getDisplayName(java.util.Locale)
     */
    public void test_getDisplayNameLjava_util_Locale() {
        // Test for method java.lang.String
        // java.util.Locale.getDisplayName(java.util.Locale)
        assertTrue("Returned incorrect name: " + testLocale.getDisplayName(l),
                testLocale.getDisplayName(l).equals("anglais (Canada,WIN32)"));
    }

    /**
     * @tests java.util.Locale#getDisplayVariant()
     */
    public void test_getDisplayVariant() {
        // Test for method java.lang.String java.util.Locale.getDisplayVariant()
        assertTrue("Returned incorrect variant: "
                + testLocale.getDisplayVariant(), testLocale
                .getDisplayVariant().equals("WIN32"));
    }

    /**
     * @tests java.util.Locale#getDisplayVariant(java.util.Locale)
     */
    public void test_getDisplayVariantLjava_util_Locale() {
        // Test for method java.lang.String
        // java.util.Locale.getDisplayVariant(java.util.Locale)
        assertTrue("Returned incorrect variant: "
                + testLocale.getDisplayVariant(l), testLocale
                .getDisplayVariant(l).equals("WIN32"));
    }

    /**
     * @tests java.util.Locale#getISO3Country()
     */
    public void test_getISO3Country() {
        // Test for method java.lang.String java.util.Locale.getISO3Country()
        assertTrue("Returned incorrect ISO3 country: "
                + testLocale.getISO3Country(), testLocale.getISO3Country()
                .equals("CAN"));
        
        Locale l = new Locale("", "CD");
        assertEquals("COD", l.getISO3Country());
    }

    /**
     * @tests java.util.Locale#getISO3Language()
     */
    public void test_getISO3Language() {
        // Test for method java.lang.String java.util.Locale.getISO3Language()
        assertTrue("Returned incorrect ISO3 language: "
                + testLocale.getISO3Language(), testLocale.getISO3Language()
                .equals("eng"));
        
        Locale l = new Locale("ae");
        assertEquals("ave", l.getISO3Language());
        
        // Regression for Harmony-1146
        Locale l_CountryCS = new Locale("", "CS"); //$NON-NLS-1$ //$NON-NLS-2$
        assertEquals("SCG", l_CountryCS.getISO3Country()); //$NON-NLS-1$
        
        // Regression for Harmony-1129
        l = new Locale("ak", ""); //$NON-NLS-1$ //$NON-NLS-2$
        assertEquals("aka", l.getISO3Language()); //$NON-NLS-1$
    }

    /**
     * @tests java.util.Locale#getISOCountries()
     */
    public void test_getISOCountries() {
        // Test for method java.lang.String []
        // java.util.Locale.getISOCountries()
        // Assumes all countries are 2 digits, and that there will always be
        // 230 countries on the list...
        String[] isoCountries = Locale.getISOCountries();
        int length = isoCountries.length;
        int familiarCount = 0;
        for (int i = 0; i < length; i++) {
            if (isoCountries[i].length() != 2) {
                fail("Wrong format for ISOCountries.");
            }
            if (isoCountries[i].equals("CA") || isoCountries[i].equals("BB")
                    || isoCountries[i].equals("US")
                    || isoCountries[i].equals("KR"))
                familiarCount++;
        }
        assertTrue("ISOCountries missing.", familiarCount == 4 && length > 230);
    }

    /**
     * @tests java.util.Locale#getISOLanguages()
     */
    public void test_getISOLanguages() {
        // Test for method java.lang.String []
        // java.util.Locale.getISOLanguages()
        // Assumes always at least 131 ISOlanguages...
        String[] isoLang = Locale.getISOLanguages();
        int length = isoLang.length;
        assertTrue("Random element in wrong format.", (isoLang[length / 2]
                .length() == 2)
                && isoLang[length / 2].toLowerCase()
                        .equals(isoLang[length / 2]));
        assertTrue("Wrong number of ISOLanguages.", length > 130);
    }

    /**
     * @tests java.util.Locale#getLanguage()
     */
    public void test_getLanguage() {
        // Test for method java.lang.String java.util.Locale.getLanguage()
        assertTrue("Returned incorrect language: " + testLocale.getLanguage(),
                testLocale.getLanguage().equals("en"));
    }

    /**
     * @tests java.util.Locale#getVariant()
     */
    public void test_getVariant() {
        // Test for method java.lang.String java.util.Locale.getVariant()
        assertTrue("Returned incorrect variant: " + testLocale.getVariant(),
                testLocale.getVariant().equals("WIN32"));
    }

    /**
     * @tests java.util.Locale#setDefault(java.util.Locale)
     */
    public void test_setDefaultLjava_util_Locale() {
        // Test for method void java.util.Locale.setDefault(java.util.Locale)

        Locale org = Locale.getDefault();
        Locale.setDefault(l);
        Locale x = Locale.getDefault();
        Locale.setDefault(org);
        assertEquals("Failed to set locale", "fr_CA_WIN32", x.toString());

        Locale.setDefault(new Locale("tr", ""));
        String res1 = "\u0069".toUpperCase();
        String res2 = "\u0049".toLowerCase();
        Locale.setDefault(org);
        assertEquals("Wrong toUppercase conversion", "\u0130", res1);
        assertEquals("Wrong toLowercase conversion", "\u0131", res2);
    }

    /**
     * @tests java.util.Locale#toString()
     */
    public void test_toString() {
        // Test for method java.lang.String java.util.Locale.toString()
        assertEquals("Returned incorrect string representation", "en_CA_WIN32", testLocale
                .toString());

        Locale l = new Locale("en", "");
        assertEquals("Wrong representation 1", "en", l.toString());
        l = new Locale("", "CA");
        assertEquals("Wrong representation 2", "_CA", l.toString());
        l = new Locale("", "CA", "var");
        assertEquals("Wrong representation 2.5", "_CA_var", l.toString());
        l = new Locale("en", "", "WIN");
        assertEquals("Wrong representation 4", "en__WIN", l.toString());
        l = new Locale("en", "CA");
        assertEquals("Wrong representation 6", "en_CA", l.toString());
        l = new Locale("en", "CA", "VAR");
        assertEquals("Wrong representation 7", "en_CA_VAR", l.toString());
        
        l = new Locale("", "", "var");
        assertEquals("Wrong representation 8", "", l.toString());

    }
    
// BEGIN android-removed
// These locales are not part of the android reference impl
//    // Regression Test for HARMONY-2953
//    public void test_getISO() {
//        Locale locale = new Locale("an");
//        assertEquals("arg", locale.getISO3Language());
//
//        locale = new Locale("PS");
//        assertEquals("pus", locale.getISO3Language());
//
//        List<String> languages = Arrays.asList(Locale.getISOLanguages());
//        assertTrue(languages.contains("ak"));
//
//        List<String> countries = Arrays.asList(Locale.getISOCountries());
//        assertTrue(countries.contains("CS"));
//    }
// END android-removed
    
    /**
     * Sets up the fixture, for example, open a network connection. This method
     * is called before a test is executed.
     */
    protected void setUp() {
        defaultLocale = Locale.getDefault();
        Locale.setDefault(Locale.US);
        testLocale = new Locale("en", "CA", "WIN32");
        l = new Locale("fr", "CA", "WIN32");
    }

    /**
     * Tears down the fixture, for example, close a network connection. This
     * method is called after a test is executed.
     */
    protected void tearDown() {
        Locale.setDefault(defaultLocale);
    }
}


