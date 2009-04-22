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

package java.nio.charset;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.spi.CharsetProvider;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.Collections;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Locale;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;

import com.ibm.icu4jni.charset.CharsetProviderICU;

/**
 * A charset defines a mapping between a Unicode character sequence and a byte
 * sequence. It facilitate the encoding from a Unicode character sequence into a
 * byte sequence, and the decoding from a byte sequence into a Unicode character
 * sequence.
 * <p>
 * A charset has a canonical name, which are usually in uppercase. Typically it
 * also has one or more aliases. The name string can only consist of the
 * following characters: '0' - '9', 'A' - 'Z', 'a' - 'z', '.', ':'. '-' and '_'.
 * The first character of the name must be a digit or a letter.
 * </p>
 * <p>
 * The following charsets should be supported by any java platforms: US-ASCII,
 * ISO-8859-1, UTF-8, UTF-16BE, UTF-16LE, UTF-16.
 * </p>
 * <p>
 * Additional charsets can be made available by configuring one or more charset
 * providers through provider configuration files. Such files are always named
 * as "java.nio.charset.spi.CharsetProvider" and located in the
 * "META-INF/services" sub folder of one or more classpaths. The files should be
 * encoded in "UTF-8". Each line of their content specifies the class name of a
 * charset provider which extends <code>java.nio.spi.CharsetProvider</code>.
 * A line should ends with '\r', '\n' or '\r\n'. Leading and trailing
 * whitespaces are trimmed. Blank lines, and lines (after trimmed) starting with
 * "#" which are regarded as comments, are both ignored. Duplicates of already
 * appeared names are also ignored. Both the configuration files and the
 * provider classes will be loaded using the thread context class loader.
 * </p>
 * <p>
 * This class is thread-safe.
 * </p>
 * 
 * @see java.nio.charset.spi.CharsetProvider
 * 
 */
public abstract class Charset implements Comparable<Charset> {

    /*
     * --------------------------------------------------------------------
     * Constants
     * --------------------------------------------------------------------
     */

    /*
     * the name of configuration files where charset provider class names can be
     * specified.
     */
    private static final String PROVIDER_CONFIGURATION_FILE_NAME = "META-INF/services/java.nio.charset.spi.CharsetProvider"; //$NON-NLS-1$

    /*
     * the encoding of configuration files
     */
    private static final String PROVIDER_CONFIGURATION_FILE_ENCODING = "UTF-8"; //$NON-NLS-1$

    /*
     * the comment string used in configuration files
     */
    private static final String PROVIDER_CONFIGURATION_FILE_COMMENT = "#"; //$NON-NLS-1$

    private static ClassLoader systemClassLoader;

    /*
     * --------------------------------------------------------------------
     * Class variables
     * --------------------------------------------------------------------
     */

    // built in provider instance, assuming thread-safe
    private static CharsetProviderICU _builtInProvider = null;

    // cached built in charsets
    private static TreeMap<String, Charset> _builtInCharsets = null;

    /*
     * --------------------------------------------------------------------
     * Instance variables
     * --------------------------------------------------------------------
     */

    private final String canonicalName;

    // the aliases set
    private final HashSet<String> aliasesSet;

    // cached Charset table
    private static HashMap<String, Charset> cachedCharsetTable = new HashMap<String, Charset>();

    // cached CharsetDecoder table
    private static HashMap<String, CharsetDecoder> cachedCharsetDecoderTable = new HashMap<String, CharsetDecoder>();

    // cached CharsetEncoder table
    private static HashMap<String, CharsetEncoder> cachedCharsetEncoderTable = new HashMap<String, CharsetEncoder>();

    /*
     * -------------------------------------------------------------------
     * Global initialization
     * -------------------------------------------------------------------
     */
    static {
        /*
         * create built-in charset provider even if no privilege to access
         * charset provider.
         */
        _builtInProvider = AccessController
                .doPrivileged(new PrivilegedAction<CharsetProviderICU>() {
                    public CharsetProviderICU run() {
                        return new CharsetProviderICU();
                    }
                });
    }

    /*
     * -------------------------------------------------------------------
     * Constructors
     * -------------------------------------------------------------------
     */

    /**
     * Constructs a <code>Charset</code> object. Duplicated aliases are
     * ignored.
     * 
     * @param canonicalName
     *            the canonical name of the charset
     * @param aliases
     *            an array containing all aliases of the charset
     * @throws IllegalCharsetNameException
     *             on an illegal value being supplied for either
     *             <code>canonicalName</code> or for any element of
     *             <code>aliases</code>.
     * 
     */
    protected Charset(String canonicalName, String[] aliases)
            throws IllegalCharsetNameException {
        // throw IllegalArgumentException if name is null
        if (null == canonicalName) {
            throw new NullPointerException();
        }
        // check whether the given canonical name is legal
        checkCharsetName(canonicalName);
        this.canonicalName = canonicalName;
        // check each alias and put into a set
        this.aliasesSet = new HashSet<String>();
        if (null != aliases) {
            for (int i = 0; i < aliases.length; i++) {
                checkCharsetName(aliases[i]);
                this.aliasesSet.add(aliases[i]);
            }
        }
    }

    /*
     * -------------------------------------------------------------------
     * Methods
     * -------------------------------------------------------------------
     */

    /*
     * Checks whether a character is a special character that can be used in
     * charset names, other than letters and digits.
     */
    private static boolean isSpecial(char c) {
        return ('-' == c || '.' == c || ':' == c || '_' == c);
    }

    /*
     * Checks whether a character is a letter (ascii) which are defined in Java
     * Spec.
     */
    private static boolean isLetter(char c) {
        return ('a' <= c && c <= 'z') || ('A' <= c && c <= 'Z');
    }

    /*
     * Checks whether a character is a digit (ascii) which are defined in Java
     * Spec.
     */
    private static boolean isDigit(char c) {
        return ('0' <= c && c <= '9');
    }

    /*
     * Checks whether a given string is a legal charset name. The argument name
     * should not be null.
     */
    private static void checkCharsetName(String name) {
        // An empty string is illegal charset name
        if (name.length() == 0) {
            throw new IllegalCharsetNameException(name);
        }
        // The first character must be a letter or a digit
        // This is related to HARMONY-68 (won't fix)
        // char first = name.charAt(0);
        // if (!isLetter(first) && !isDigit(first)) {
        // throw new IllegalCharsetNameException(name);
        // }
        // Check the remaining characters
        int length = name.length();
        for (int i = 0; i < length; i++) {
            char c = name.charAt(i);
            if (!isLetter(c) && !isDigit(c) && !isSpecial(c)) {
                throw new IllegalCharsetNameException(name);
            }
        }
    }

    /*
     * Use privileged code to get the context class loader.
     */
    private static ClassLoader getContextClassLoader() {
        final Thread t = Thread.currentThread();
        return AccessController
                .doPrivileged(new PrivilegedAction<ClassLoader>() {
                    public ClassLoader run() {
                        return t.getContextClassLoader();
                    }
                });
    }

    /*
     * Use privileged code to get the system class loader.
     */
    private static void getSystemClassLoader() {
        if (null == systemClassLoader) {
            systemClassLoader = AccessController
                    .doPrivileged(new PrivilegedAction<ClassLoader>() {
                        public ClassLoader run() {
                            return ClassLoader.getSystemClassLoader();
                        }
                    });
        }
    }

    /*
     * Add the charsets supported by the given provider to the map.
     */
    private static void addCharsets(CharsetProvider cp,
            TreeMap<String, Charset> charsets) {
        Iterator<Charset> it = cp.charsets();
        while (it.hasNext()) {
            Charset cs = it.next();
            // Only new charsets will be added
            if (!charsets.containsKey(cs.name())) {
                charsets.put(cs.name(), cs);
            }
        }
    }

    /*
     * Trim comment string, and then trim white spaces.
     */
    private static String trimClassName(String name) {
        String trimedName = name;
        int index = name.indexOf(PROVIDER_CONFIGURATION_FILE_COMMENT);
        // Trim comments
        if (index != -1) {
            trimedName = name.substring(0, index);
        }
        return trimedName.trim();
    }

    /*
     * Read a configuration file and add the charsets supported by the providers
     * specified by this configuration file to the map.
     */
    private static void loadConfiguredCharsets(URL configFile,
            ClassLoader contextClassLoader, TreeMap<String, Charset> charsets) {
        BufferedReader reader = null;
        try {
            InputStream is = configFile.openStream();
            // Read each line for charset provider class names
            // BEGIN android-modified
            reader = new BufferedReader(new InputStreamReader(is,
                            PROVIDER_CONFIGURATION_FILE_ENCODING), 8192);
            // END android-modified
            String providerClassName = reader.readLine();
            while (null != providerClassName) {
                providerClassName = trimClassName(providerClassName);
                // Skip comments and blank lines
                if (providerClassName.length() > 0) { // Non empty string
                    // Load the charset provider
                    Object cp = null;
                    try {
                        Class<?> c = Class.forName(providerClassName, true,
                                contextClassLoader);
                        cp = c.newInstance();
                    } catch (Exception ex) {
                        // try to use system classloader when context
                        // classloader failed to load config file.
                        try {
                            getSystemClassLoader();
                            Class<?> c = Class.forName(providerClassName, true,
                                    systemClassLoader);
                            cp = c.newInstance();
                        } catch (Exception e) {
                            throw new Error(e.getMessage(), e);
                        }
                    }
                    // Put the charsets supported by this provider into the map
                    addCharsets((CharsetProvider) cp, charsets);
                }
                // Read the next line of the config file
                providerClassName = reader.readLine();
            }
        } catch (IOException ex) {
            // Can't read this configuration file, ignore
        } finally {
            try {
                if (null != reader) {
                    reader.close();
                }
            } catch (IOException ex) {
                // Ignore closing exception
            }
        }
    }

    /**
     * Gets a map of all available charsets supported by the runtime.
     * <p>
     * The returned map contains mappings from canonical names to corresponding
     * instances of <code>Charset</code>. The canonical names can be
     * considered as case-insensitive.
     * </p>
     * 
     * @return an unmodifiable map of all available charsets supported by the
     *         runtime
     */
    @SuppressWarnings("unchecked")
    public static SortedMap<String, Charset> availableCharsets() {
        // Initialize the built-in charsets map cache if necessary
        if (null == _builtInCharsets) {
            synchronized (Charset.class) {
                if (null == _builtInCharsets) {
                    _builtInCharsets = new TreeMap<String, Charset>(
                            IgnoreCaseComparator.getInstance());
                    _builtInProvider.putCharsets(_builtInCharsets);
                }
            }
        }

        // Add built-in charsets
        TreeMap<String, Charset> charsets = (TreeMap<String, Charset>) _builtInCharsets
                .clone();

        // Collect all charsets provided by charset providers
        ClassLoader contextClassLoader = getContextClassLoader();
        Enumeration<URL> e = null;
        try {
            if (null != contextClassLoader) {
                e = contextClassLoader
                        .getResources(PROVIDER_CONFIGURATION_FILE_NAME);
            } else {
                getSystemClassLoader();
                e = systemClassLoader
                        .getResources(PROVIDER_CONFIGURATION_FILE_NAME);
            }
            // Examine each configuration file
            while (e.hasMoreElements()) {
                loadConfiguredCharsets(e.nextElement(), contextClassLoader,
                        charsets);
            }
        } catch (IOException ex) {
            // Unexpected ClassLoader exception, ignore
        }
        return Collections.unmodifiableSortedMap(charsets);
    }

    /*
     * Read a configuration file and try to find the desired charset among those
     * which are supported by the providers specified in this configuration
     * file.
     */
    private static Charset searchConfiguredCharsets(String charsetName,
            ClassLoader contextClassLoader, URL configFile) {
        BufferedReader reader = null;
        try {
            InputStream is = configFile.openStream();
            // Read each line for charset provider class names
            // BEGIN android-modified
            reader = new BufferedReader(new InputStreamReader(is,
                            PROVIDER_CONFIGURATION_FILE_ENCODING), 8192);
            // END android-modified
            String providerClassName = reader.readLine();
            while (null != providerClassName) {
                providerClassName = trimClassName(providerClassName);
                if (providerClassName.length() > 0) { // Non empty string
                    // Load the charset provider
                    Object cp = null;
                    try {
                        Class<?> c = Class.forName(providerClassName, true,
                                contextClassLoader);
                        cp = c.newInstance();
                    } catch (Exception ex) {
                        // try to use system classloader when context
                        // classloader failed to load config file.
                        try {
                            getSystemClassLoader();
                            Class<?> c = Class.forName(providerClassName, true,
                                    systemClassLoader);
                            cp = c.newInstance();
                        } catch (SecurityException e) {
                            throw e;  
                        } catch (Exception e) {
                            throw new Error(e.getMessage(), e);
                        }
                    }
                    // Try to get the desired charset from this provider
                    Charset cs = ((CharsetProvider) cp)
                            .charsetForName(charsetName);
                    if (null != cs) {
                        return cs;
                    }
                }
                // Read the next line of the config file
                providerClassName = reader.readLine();
            }
            return null;
        } catch (IOException ex) {
            // Can't read this configuration file
            return null;
        } finally {
            try {
                if (null != reader) {
                    reader.close();
                }
            } catch (IOException ex) {
                // Ignore closing exception
            }
        }
    }

    /*
     * Gets a <code> Charset </code> instance for the specified charset name. If
     * the charset is not supported, returns null instead of throwing an
     * exception.
     */
    private static Charset forNameInternal(String charsetName)
            throws IllegalCharsetNameException {
        if (null == charsetName) {
            throw new IllegalArgumentException();
        }
        checkCharsetName(charsetName);
        synchronized (Charset.class) {
            // Try to get Charset from cachedCharsetTable
            Charset cs = getCachedCharset(charsetName);
            if (null != cs) {
                return cs;
            }
            // Try built-in charsets
            cs = _builtInProvider.charsetForName(charsetName);
            if (null != cs) {
                cacheCharset(cs);
                return cs;
            }

            // Collect all charsets provided by charset providers
            ClassLoader contextClassLoader = getContextClassLoader();
            Enumeration<URL> e = null;
            try {
                if (null != contextClassLoader) {
                    e = contextClassLoader
                            .getResources(PROVIDER_CONFIGURATION_FILE_NAME);
                } else {
                    getSystemClassLoader();
                    e = systemClassLoader
                            .getResources(PROVIDER_CONFIGURATION_FILE_NAME);
                }
                // Examine each configuration file
                while (e.hasMoreElements()) {
                    cs = searchConfiguredCharsets(charsetName,
                            contextClassLoader, e.nextElement());
                    if (null != cs) {
                        cacheCharset(cs);
                        return cs;
                    }
                }
            } catch (IOException ex) {
                // Unexpected ClassLoader exception, ignore
            }
        }
        return null;
    }

    /*
     * save charset into cachedCharsetTable
     */
    private static void cacheCharset(Charset cs) {
        cachedCharsetTable.put(cs.name(), cs);
        Set<String> aliasesSet = cs.aliases();
        if (null != aliasesSet) {
            Iterator<String> iter = aliasesSet.iterator();
            while (iter.hasNext()) {
                String alias = iter.next();
                cachedCharsetTable.put(alias, cs);
            }
        }
    }

    /*
     * get cached charset reference by name
     */
    private static Charset getCachedCharset(String name) {
        return cachedCharsetTable.get(name);
    }

    /**
     * Gets a <code>Charset</code> instance for the specified charset name.
     * 
     * @param charsetName
     *            the name of the charset
     * @return a <code>Charset</code> instance for the specified charset name
     * @throws IllegalCharsetNameException
     *             If the specified charset name is illegal.
     * @throws UnsupportedCharsetException
     *             If the desired charset is not supported by this runtime.
     */
    public static Charset forName(String charsetName)
            throws IllegalCharsetNameException, UnsupportedCharsetException {
        Charset c = forNameInternal(charsetName);
        if (null == c) {
            throw new UnsupportedCharsetException(charsetName);
        }
        return c;
    }

    /**
     * Determines whether the specified charset is supported by this runtime.
     * 
     * @param charsetName
     *            the name of the charset
     * @return true if the specified charset is supported, otherwise false
     * @throws IllegalCharsetNameException
     *             If the specified charset name is illegal.
     */
    public static boolean isSupported(String charsetName)
            throws IllegalCharsetNameException {
        Charset cs = forNameInternal(charsetName);
        return (null != cs);
    }

    /**
     * Determines whether this charset is a super set of the given charset.
     * 
     * @param charset
     *            a given charset
     * @return true if this charset is a super set of the given charset,
     *         otherwise false
     */
    public abstract boolean contains(Charset charset);

    /**
     * Gets a new instance of encoder for this charset.
     * 
     * @return a new instance of encoder for this charset
     */
    public abstract CharsetEncoder newEncoder();

    /**
     * Gets a new instance of decoder for this charset.
     * 
     * @return a new instance of decoder for this charset
     */
    public abstract CharsetDecoder newDecoder();

    /**
     * Gets the canonical name of this charset.
     * 
     * @return this charset's name in canonical form.
     */
    public final String name() {
        return this.canonicalName;
    }

    /**
     * Gets the set of this charset's aliases.
     * 
     * @return an unmodifiable set of this charset's aliases
     */
    public final Set<String> aliases() {
        return Collections.unmodifiableSet(this.aliasesSet);
    }

    /**
     * Gets the name of this charset for the default locale.
     * 
     * @return the name of this charset for the default locale
     */
    public String displayName() {
        return this.canonicalName;
    }

    /**
     * Gets the name of this charset for the specified locale.
     * 
     * @param l
     *            a certain locale
     * @return the name of this charset for the specified locale
     */
    public String displayName(Locale l) {
        return this.canonicalName;
    }

    /**
     * Returns whether this charset is known to be registered in the IANA
     * Charset Registry.
     * 
     * @return true if the charset is known to be registered, otherwise returns
     *         false.
     */
    public final boolean isRegistered() {
        return !canonicalName.startsWith("x-") //$NON-NLS-1$
                && !canonicalName.startsWith("X-"); //$NON-NLS-1$
    }

    /**
     * Returns true if this charset supports encoding, otherwise false.
     * 
     * @return true
     */
    public boolean canEncode() {
        return true;
    }

    /**
     * Encodes the content of the give character buffer and outputs to a byte
     * buffer that is to be returned.
     * <p>
     * The default action in case of encoding errors is
     * <code>CodingErrorAction.REPLACE</code>.
     * </p>
     * 
     * @param buffer
     *            the character buffer containing the content to be encoded
     * @return the result of the encoding
     */
    synchronized public final ByteBuffer encode(CharBuffer buffer) {
        CharsetEncoder e = getCachedCharsetEncoder(canonicalName);
        try {
            synchronized (e) {
                return e.encode(buffer);
            }
        } catch (CharacterCodingException ex) {
            throw new Error(ex.getMessage(), ex);
        }
    }

    /*
     * get cached CharsetEncoder by canonical name
     */
    private CharsetEncoder getCachedCharsetEncoder(String name) {
        synchronized (cachedCharsetEncoderTable) {
            CharsetEncoder e = cachedCharsetEncoderTable
                    .get(name);
            if (null == e) {
                e = this.newEncoder();
                e.onMalformedInput(CodingErrorAction.REPLACE);
                e.onUnmappableCharacter(CodingErrorAction.REPLACE);
                cachedCharsetEncoderTable.put(name, e);
            }
            return e;
        }
    }

    /**
     * Encodes a string and outputs to a byte buffer that is to be returned.
     * <p>
     * The default action in case of encoding errors is
     * <code>CodingErrorAction.REPLACE</code>.
     * </p>
     * 
     * @param s
     *            the string to be encoded
     * @return the result of the encoding
     */
    public final ByteBuffer encode(String s) {
        return encode(CharBuffer.wrap(s));
    }

    /**
     * Decodes the content of the give byte buffer and outputs to a character
     * buffer that is to be returned.
     * <p>
     * The default action in case of decoding errors is
     * <code>CodingErrorAction.REPLACE</code>.
     * </p>
     * 
     * @param buffer
     *            the byte buffer containing the content to be decoded
     * @return a character buffer containing the output of the decoding
     */
    public final CharBuffer decode(ByteBuffer buffer) {
        CharsetDecoder d = getCachedCharsetDecoder(canonicalName);
        try {
            synchronized (d) {
                return d.decode(buffer);
            }
        } catch (CharacterCodingException ex) {
            throw new Error(ex.getMessage(), ex);
        }
    }

    /*
     * get cached CharsetDecoder by canonical name
     */
    private CharsetDecoder getCachedCharsetDecoder(String name) {
        synchronized (cachedCharsetDecoderTable) {
            CharsetDecoder d = cachedCharsetDecoderTable
                    .get(name);
            if (null == d) {
                d = this.newDecoder();
                d.onMalformedInput(CodingErrorAction.REPLACE);
                d.onUnmappableCharacter(CodingErrorAction.REPLACE);
                cachedCharsetDecoderTable.put(name, d);
            }
            return d;
        }
    }

    /*
     * -------------------------------------------------------------------
     * Methods implementing parent interface Comparable
     * -------------------------------------------------------------------
     */

    /**
     * Compares this charset with the given charset.
     * 
     * @param charset
     *            the given object to be compared with
     * @return a negative integer if less than the given object, a positive
     *         integer if larger than it, or 0 if equal to it
     */
    public final int compareTo(Charset charset) {
        return this.canonicalName.compareToIgnoreCase(charset.canonicalName);
    }

    /*
     * -------------------------------------------------------------------
     * Methods overriding parent class Object
     * -------------------------------------------------------------------
     */

    /**
     * Determines whether this charset equals to the given object. They are
     * considered to be equal if they have the same canonical name.
     * 
     * @param obj
     *            the given object to be compared with
     * @return true if they have the same canonical name, otherwise false
     */
    @Override
    public final boolean equals(Object obj) {
        if (obj instanceof Charset) {
            Charset that = (Charset) obj;
            return this.canonicalName.equals(that.canonicalName);
        }
        return false;
    }

    /**
     * Gets the hash code of this charset.
     * 
     * @return the hash code of this charset
     */
    @Override
    public final int hashCode() {
        return this.canonicalName.hashCode();
    }

    /**
     * Gets a string representation of this charset. Usually this contains the
     * canonical name of the charset.
     * 
     * @return a string representation of this charset
     */
    @Override
    public final String toString() {
        return "Charset[" + this.canonicalName + "]"; //$NON-NLS-1$//$NON-NLS-2$
    }

    /**
     * Gets the system default charset from jvm.
     * 
     * @return the default charset
     */
    public static Charset defaultCharset() {
        Charset defaultCharset = null;
        String encoding = AccessController
                .doPrivileged(new PrivilegedAction<String>() {
                    public String run() {
                        return System.getProperty("file.encoding"); //$NON-NLS-1$
                    }
                });
        try {
            defaultCharset = Charset.forName(encoding);
        } catch (UnsupportedCharsetException e) {
            defaultCharset = Charset.forName("UTF-8"); //$NON-NLS-1$
        }
        return defaultCharset;
    }

    /**
     * A comparator that ignores case.
     */
    static class IgnoreCaseComparator implements Comparator<String> {

        // the singleton
        private static Comparator<String> c = new IgnoreCaseComparator();

        /*
         * Default constructor.
         */
        private IgnoreCaseComparator() {
            // no action
        }

        /*
         * Gets a single instance.
         */
        public static Comparator<String> getInstance() {
            return c;
        }

        /*
         * Compares two strings ignoring case.
         */
        public int compare(String s1, String s2) {
            return s1.compareToIgnoreCase(s2);
        }
    }
}
