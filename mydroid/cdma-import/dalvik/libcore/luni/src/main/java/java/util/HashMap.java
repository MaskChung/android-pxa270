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

package java.util;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;

/**
 * HashMap is an implementation of Map. All optional operations are supported,
 * adding and removing. Keys and values can be any objects.
 */
public class HashMap<K, V> extends AbstractMap<K, V> implements Map<K, V>,
        Cloneable, Serializable {
    private static final long serialVersionUID = 362498820763181265L;

    transient int elementCount;

    transient Entry<K, V>[] elementData;

    final float loadFactor;

    int threshold;

    transient int modCount = 0;

    private static final int DEFAULT_SIZE = 16;

    static class Entry<K, V> extends MapEntry<K, V> {
        final int origKeyHash;

        Entry<K, V> next;

        Entry(K theKey, int hash) {
            super(theKey, null);
            this.origKeyHash = hash;
        }

        Entry(K theKey, V theValue) {
            super(theKey, theValue);
            origKeyHash = (theKey == null ? 0 : theKey.hashCode());
        }

        @Override
        @SuppressWarnings("unchecked")
        public Object clone() {
            Entry<K, V> entry = (Entry<K, V>) super.clone();
            if (next != null) {
                entry.next = (Entry<K, V>) next.clone();
            }
            return entry;
        }
    }

    static class HashMapIterator<E, KT, VT> implements Iterator<E> {
        private int position = 0;

        int expectedModCount;

        final MapEntry.Type<E, KT, VT> type;

        boolean canRemove = false;

        Entry<KT, VT> entry;

        Entry<KT, VT> lastEntry;

        final HashMap<KT, VT> associatedMap;

        HashMapIterator(MapEntry.Type<E, KT, VT> value, HashMap<KT, VT> hm) {
            associatedMap = hm;
            type = value;
            expectedModCount = hm.modCount;
        }

        public boolean hasNext() {
            if (entry != null) {
                return true;
            }

            // BEGIN android-changed
            Entry<KT, VT>[] elementData = associatedMap.elementData;
            int length = elementData.length;
            int newPosition = position;
            boolean result = false;

            while (newPosition < length) {
                if (elementData[newPosition] == null) {
                    newPosition++;
                } else {
                    result = true;
                    break;
                }
            }

            position = newPosition;
            return result;
            // END android-changed
        }

        void checkConcurrentMod() throws ConcurrentModificationException {
            if (expectedModCount != associatedMap.modCount) {
                throw new ConcurrentModificationException();
            }
        }

        public E next() {
            // BEGIN android-changed
            // inline checkConcurrentMod()
            if (expectedModCount != associatedMap.modCount) {
                throw new ConcurrentModificationException();
            }
            if (!hasNext()) {
                throw new NoSuchElementException();
            }

            MapEntry<KT, VT> result;
            Entry<KT, VT> _entry  = entry;
            if (_entry == null) {
                result = lastEntry = associatedMap.elementData[position++];
                entry = lastEntry.next;
            } else {
                if (lastEntry.next != _entry) {
                    lastEntry = lastEntry.next;
                }
                result = _entry;
                entry = _entry.next;
            }
            canRemove = true;
            return type.get(result);
            // END android-changed
        }

        public void remove() {
            checkConcurrentMod();
            if (!canRemove) {
                throw new IllegalStateException();
            }

            canRemove = false;
            associatedMap.modCount++;
            if (lastEntry.next == entry) {
                while (associatedMap.elementData[--position] == null) {
                    // Do nothing
                }
                associatedMap.elementData[position] = associatedMap.elementData[position].next;
                entry = null;
            } else {
                lastEntry.next = entry;
            }
            associatedMap.elementCount--;
            expectedModCount++;
        }
    }

    static class HashMapEntrySet<KT, VT> extends AbstractSet<Map.Entry<KT, VT>> {
        private final HashMap<KT, VT> associatedMap;

        public HashMapEntrySet(HashMap<KT, VT> hm) {
            associatedMap = hm;
        }

        HashMap<KT, VT> hashMap() {
            return associatedMap;
        }

        @Override
        public int size() {
            return associatedMap.elementCount;
        }

        @Override
        public void clear() {
            associatedMap.clear();
        }

        @Override
        public boolean remove(Object object) {
            if (contains(object)) {
                associatedMap.remove(((Map.Entry<?, ?>) object).getKey());
                return true;
            }
            return false;
        }

        @Override
        public boolean contains(Object object) {
            if (object instanceof Map.Entry) {
                Object key = ((Map.Entry<?, ?>) object).getKey();
                Entry entry;
                if (key == null) {
                    entry = associatedMap.findNullKeyEntry();
                } else {
                    int hash = key.hashCode();
                    int index = (hash & 0x7FFFFFFF) % associatedMap.elementData.length;
                    entry = associatedMap.findNonNullKeyEntry(key, index, hash);
                }
                return object.equals(entry);
            }
            return false;
        }

        @Override
        public Iterator<Map.Entry<KT, VT>> iterator() {
            return new HashMapIterator<Map.Entry<KT, VT>, KT, VT>(
                    new MapEntry.Type<Map.Entry<KT, VT>, KT, VT>() {
                        public Map.Entry<KT, VT> get(MapEntry<KT, VT> entry) {
                            return entry;
                        }
                    }, associatedMap);
        }
    }

    /**
     * Create a new element array
     *
     * @param s
     * @return Reference to the element array
     */
    @SuppressWarnings("unchecked")
    Entry<K, V>[] newElementArray(int s) {
        return new Entry[s];
    }

    /**
     * Constructs a new empty instance of HashMap.
     *
     */
    public HashMap() {
        this(DEFAULT_SIZE);
    }

    /**
     * Constructs a new instance of HashMap with the specified capacity.
     *
     * @param capacity
     *            the initial capacity of this HashMap
     *
     * @exception IllegalArgumentException
     *                when the capacity is less than zero
     */
    public HashMap(int capacity) {
        if (capacity >= 0) {
            elementCount = 0;
            elementData = newElementArray(capacity == 0 ? 1 : capacity);
            loadFactor = 0.75f; // Default load factor of 0.75
            computeMaxSize();
        } else {
            throw new IllegalArgumentException();
        }
    }

    /**
     * Constructs a new instance of HashMap with the specified capacity and load
     * factor.
     *
     *
     * @param capacity
     *            the initial capacity
     * @param loadFactor
     *            the initial load factor
     *
     * @exception IllegalArgumentException
     *                when the capacity is less than zero or the load factor is
     *                less or equal to zero
     */
    public HashMap(int capacity, float loadFactor) {
        if (capacity >= 0 && loadFactor > 0) {
            elementCount = 0;
            elementData = newElementArray(capacity == 0 ? 1 : capacity);
            this.loadFactor = loadFactor;
            computeMaxSize();
        } else {
            throw new IllegalArgumentException();
        }
    }

    /**
     * Constructs a new instance of HashMap containing the mappings from the
     * specified Map.
     *
     * @param map
     *            the mappings to add
     */
    public HashMap(Map<? extends K, ? extends V> map) {
        this(map.size() < 6 ? 11 : map.size() * 2);
        putAllImpl(map);
    }

    /**
     * Removes all mappings from this HashMap, leaving it empty.
     *
     * @see #isEmpty
     * @see #size
     */
    @Override
    public void clear() {
        if (elementCount > 0) {
            elementCount = 0;
            Arrays.fill(elementData, null);
            modCount++;
        }
    }

    /**
     * Returns a new HashMap with the same mappings and size as this HashMap.
     *
     * @return a shallow copy of this HashMap
     *
     * @see java.lang.Cloneable
     */
    @Override
    @SuppressWarnings("unchecked")
    public Object clone() {
        try {
            HashMap<K, V> map = (HashMap<K, V>) super.clone();
            map.elementCount = 0;
            map.elementData = newElementArray(elementData.length);
            Entry<K, V> entry;
            for (int i = 0; i < elementData.length; i++) {
                if ((entry = elementData[i]) != null){
                    map.putImpl(entry.getKey(), entry.getValue());
                    while (entry.next != null){
                        entry = entry.next;
                        map.putImpl(entry.getKey(), entry.getValue());
                    }
                }
            }
            return map;
        } catch (CloneNotSupportedException e) {
            return null;
        }
    }

    private void computeMaxSize() {
        threshold = (int) (elementData.length * loadFactor);
    }

    /**
     * Searches this HashMap for the specified key.
     *
     * @param key
     *            the object to search for
     * @return true if <code>key</code> is a key of this HashMap, false
     *         otherwise
     */
    @Override
    public boolean containsKey(Object key) {
        Entry<K, V> m;
        if (key == null) {
            m = findNullKeyEntry();
        } else {
            int hash = key.hashCode();
            int index = (hash & 0x7FFFFFFF) % elementData.length;
            m = findNonNullKeyEntry(key, index, hash);
        }
        return m != null;
    }

    /**
     * Searches this HashMap for the specified value.
     *
     * @param value
     *            the object to search for
     * @return true if <code>value</code> is a value of this HashMap, false
     *         otherwise
     */
    @Override
    public boolean containsValue(Object value) {
        if (value != null) {
            for (int i = elementData.length; --i >= 0;) {
                Entry<K, V> entry = elementData[i];
                while (entry != null) {
                    if (value.equals(entry.value)) {
                        return true;
                    }
                    entry = entry.next;
                }
            }
        } else {
            for (int i = elementData.length; --i >= 0;) {
                Entry<K, V> entry = elementData[i];
                while (entry != null) {
                    if (entry.value == null) {
                        return true;
                    }
                    entry = entry.next;
                }
            }
        }
        return false;
    }

    /**
     * Returns a Set of the mappings contained in this HashMap. Each element in
     * the set is a Map.Entry. The set is backed by this HashMap so changes to
     * one are reflected by the other. The set does not support adding.
     *
     * @return a Set of the mappings
     */
    @Override
    public Set<Map.Entry<K, V>> entrySet() {
        return new HashMapEntrySet<K, V>(this);
    }

    /**
     * Returns the value of the mapping with the specified key. If no entry is
     * found null is returned.
     *
     * @param key the key
     * @return the value of the mapping with the specified key, or null if the
     *         entry doesn't exist.
     */
    @Override
    public V get(Object key) {
        Entry<K, V> m;
        if (key == null) {
            m = findNullKeyEntry();
        } else {
            int hash = key.hashCode();
            int index = (hash & 0x7FFFFFFF) % elementData.length;
            m = findNonNullKeyEntry(key, index, hash);
        }
        if (m != null) {
            return m.value;
        }
        return null;
    }

    final Entry<K,V> findNonNullKeyEntry(Object key, int index, int keyHash) {
        Entry<K,V> m = elementData[index];
        // BEGIN android-changed
//        while (m != null && (m.origKeyHash != keyHash || !key.equals(m.key))) {
//            m = m.next;
//        }
        while (m != null) {
            if (m.origKeyHash == keyHash) {
                if (key instanceof String) {
                    // The VM can optimize String.equals but not Object.equals
                    if (((String) key).equals(m.key)) {
                        return m;
                    }
                } else {
                    if (key.equals(m.key)) {
                        return m;
                    }
                }
            }
            m = m.next;
        }
        return null;
        // END android-changed
    }

    final Entry<K,V> findNullKeyEntry() {
        Entry<K,V> m = elementData[0];
        while (m != null && m.key != null)
            m = m.next;
        return m;
    }

    /**
     * Returns if this HashMap has no elements, a size of zero.
     *
     * @return true if this HashMap has no elements, false otherwise
     *
     * @see #size
     */
    @Override
    public boolean isEmpty() {
        return elementCount == 0;
    }

    /**
     * Returns a Set of the keys contained in this HashMap. The set is backed by
     * this HashMap so changes to one are reflected by the other. The set does
     * not support adding.
     *
     * @return a Set of the keys
     */
    @Override
    public Set<K> keySet() {
        if (keySet == null) {
            keySet = new AbstractSet<K>() {
                @Override
                public boolean contains(Object object) {
                    return containsKey(object);
                }

                @Override
                public int size() {
                    return HashMap.this.size();
                }

                @Override
                public void clear() {
                    HashMap.this.clear();
                }

                @Override
                public boolean remove(Object key) {
                    Entry<K, V> entry = HashMap.this.removeEntry(key);
                    return entry != null;
                }

                @Override
                public Iterator<K> iterator() {
                    return new HashMapIterator<K, K, V>(
                            new MapEntry.Type<K, K, V>() {
                                public K get(MapEntry<K, V> entry) {
                                    return entry.key;
                                }
                            }, HashMap.this);
                }
            };
        }
        return keySet;
    }

    /**
     * Maps the specified key to the specified value.
     *
     * @param key
     *            the key
     * @param value
     *            the value
     * @return the value of any previous mapping with the specified key or null
     *         if there was no mapping
     */
    @Override
    public V put(K key, V value) {
        return putImpl(key, value);
    }

    V putImpl(K key, V value) {
        Entry<K,V> entry;
        if(key == null) {
            entry = findNullKeyEntry();
            if (entry == null) {
                modCount++;
                if (++elementCount > threshold) {
                    rehash();
                }
                entry = createHashedEntry(key, 0, 0);
            }
        } else {
            int hash = key.hashCode();
            int index = (hash & 0x7FFFFFFF) % elementData.length;
            entry = findNonNullKeyEntry(key, index, hash);
            if (entry == null) {
                modCount++;
                if (++elementCount > threshold) {
                    rehash();
                    index = (hash & 0x7FFFFFFF) % elementData.length;
                }
                entry = createHashedEntry(key, index, hash);
            }
        }

        V result = entry.value;
        entry.value = value;
        return result;
    }

    Entry<K, V> createEntry(K key, int index, V value) {
        Entry<K, V> entry = new Entry<K, V>(key, value);
        entry.next = elementData[index];
        elementData[index] = entry;
        return entry;
    }

    Entry<K,V> createHashedEntry(K key, int index, int hash) {
        Entry<K,V> entry = new Entry<K,V>(key,hash);
        entry.next = elementData[index];
        elementData[index] = entry;
        return entry;
    }

    /**
     * Copies all the mappings in the given map to this map. These mappings will
     * replace all mappings that this map had for any of the keys currently in
     * the given map.
     *
     * @param map
     *            the Map to copy mappings from
     * @throws NullPointerException
     *             if the given map is null
     */
    @Override
    public void putAll(Map<? extends K, ? extends V> map) {
        if (!map.isEmpty()) {
            putAllImpl(map);
        }
    }

    private void putAllImpl(Map<? extends K, ? extends V> map) {
        int capacity = elementCount + map.size();
        if (capacity > threshold) {
            rehash(capacity);
        }
        for (Map.Entry<? extends K, ? extends V> entry : map.entrySet()) {
            putImpl(entry.getKey(), entry.getValue());
        }
    }

    void rehash(int capacity) {
        int length = (capacity == 0 ? 1 : capacity << 1);

        Entry<K, V>[] newData = newElementArray(length);
        for (int i = 0; i < elementData.length; i++) {
            Entry<K, V> entry = elementData[i];
            while (entry != null) {
                int index = (entry.origKeyHash & 0x7FFFFFFF) % length;
                Entry<K, V> next = entry.next;
                entry.next = newData[index];
                newData[index] = entry;
                entry = next;
            }
        }
        elementData = newData;
        computeMaxSize();
    }

    void rehash() {
        rehash(elementData.length);
    }

    /**
     * Removes a mapping with the specified key from this HashMap.
     *
     * @param key
     *            the key of the mapping to remove
     * @return the value of the removed mapping or null if key is not a key in
     *         this HashMap
     */
    @Override
    public V remove(Object key) {
        Entry<K, V> entry = removeEntry(key);
        if (entry != null) {
            return entry.value;
        }
        return null;
    }

    Entry<K, V> removeEntry(Object key) {
        int index = 0;
        Entry<K, V> entry;
        Entry<K, V> last = null;
        if (key != null) {
            int hash = key.hashCode();
            index = (hash & 0x7FFFFFFF) % elementData.length;
            entry = elementData[index];
            while (entry != null && !(entry.origKeyHash == hash && key.equals(entry.key))) {
                last = entry;
                entry = entry.next;
            }
        } else {
            entry = elementData[0];
            while (entry != null && entry.key != null) {
                last = entry;
                entry = entry.next;
            }
        }
        if (entry == null) {
            return null;
        }
        if (last == null) {
            elementData[index] = entry.next;
        } else {
            last.next = entry.next;
        }
        modCount++;
        elementCount--;
        return entry;
    }

    /**
     * Returns the number of mappings in this HashMap.
     *
     * @return the number of mappings in this HashMap
     */
    @Override
    public int size() {
        return elementCount;
    }

    /**
     * Returns a Collection of the values contained in this HashMap. The
     * collection is backed by this HashMap so changes to one are reflected by
     * the other. The collection does not support adding.
     *
     * @return a Collection of the values
     */
    @Override
    public Collection<V> values() {
        if (valuesCollection == null) {
            valuesCollection = new AbstractCollection<V>() {
                @Override
                public boolean contains(Object object) {
                    return containsValue(object);
                }

                @Override
                public int size() {
                    return HashMap.this.size();
                }

                @Override
                public void clear() {
                    HashMap.this.clear();
                }

                @Override
                public Iterator<V> iterator() {
                    return new HashMapIterator<V, K, V>(
                            new MapEntry.Type<V, K, V>() {
                                public V get(MapEntry<K, V> entry) {
                                    return entry.value;
                                }
                            }, HashMap.this);
                }
            };
        }
        return valuesCollection;
    }

    private void writeObject(ObjectOutputStream stream) throws IOException {
        stream.defaultWriteObject();
        stream.writeInt(elementData.length);
        stream.writeInt(elementCount);
        Iterator<?> iterator = entrySet().iterator();
        while (iterator.hasNext()) {
            Entry<?, ?> entry = (Entry<?, ?>) iterator.next();
            stream.writeObject(entry.key);
            stream.writeObject(entry.value);
            entry = entry.next;
        }
    }

    @SuppressWarnings("unchecked")
    private void readObject(ObjectInputStream stream) throws IOException,
            ClassNotFoundException {
        stream.defaultReadObject();
        int length = stream.readInt();
        elementData = newElementArray(length);
        elementCount = stream.readInt();
        for (int i = elementCount; --i >= 0;) {
            K key = (K) stream.readObject();
            int index = (null == key) ? 0 : (key.hashCode() & 0x7FFFFFFF)
                    % length;
            createEntry(key, index, (V) stream.readObject());
        }
    }

}
