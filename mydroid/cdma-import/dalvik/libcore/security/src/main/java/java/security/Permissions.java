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

/**
* @author Alexey V. Varlamov
* @version $Revision$
*/

package java.security;

import java.io.IOException;
import java.io.InvalidObjectException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.ObjectStreamField;
import java.io.Serializable;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.Map;
import java.util.NoSuchElementException;

import org.apache.harmony.security.internal.nls.Messages;

/**
 * A heterogeneous collection of permissions.
 * 
 */
public final class Permissions extends PermissionCollection implements
    Serializable {

    /**
     * @com.intel.drl.spec_ref
     */
    private static final long serialVersionUID = 4858622370623524688L;

    private static final ObjectStreamField[] serialPersistentFields = {
        new ObjectStreamField("perms", Hashtable.class), //$NON-NLS-1$
        new ObjectStreamField("allPermission", PermissionCollection.class), }; //$NON-NLS-1$

    // Hash to store PermissionCollection's
    private transient Map klasses = new HashMap();

    /**
     * @com.intel.drl.spec_ref
     */
    private boolean allEnabled;  // = false;

    /**
     * Adds the argument to the collection.
     * 
     * 
     * @param permission
     *            java.security.Permission the permission to add to the
     *            collection
     */
    public void add(Permission permission) {
        if (isReadOnly()) {
            throw new SecurityException(Messages.getString("security.15")); //$NON-NLS-1$
        }

        if (permission == null) {
            throw new NullPointerException(Messages.getString("security.20")); //$NON-NLS-1$
        }

        Class klass = permission.getClass();
        PermissionCollection klassMates = (PermissionCollection)klasses
            .get(klass);

        if (klassMates == null) {
            synchronized (klasses) {
                klassMates = (PermissionCollection)klasses.get(klass);
                if (klassMates == null) {

                    klassMates = permission.newPermissionCollection();
                    if (klassMates == null) {
                        klassMates = new PermissionsHash();
                    }
                    klasses.put(klass, klassMates);
                }
            }
        }
        klassMates.add(permission);

        if (klass == AllPermission.class) {
            allEnabled = true;
        }
    }

    /**
     * Returns an enumeration of the permissions in the receiver.
     * 
     * 
     * @return Enumeration the permissions in the receiver.
     */
    public Enumeration<Permission> elements() {
        return new MetaEnumeration(klasses.values().iterator());
    }

    /**
     * An auxiliary implementation for enumerating individual permissions from a
     * collection of PermissionCollections.
     * 
     */
    final static class MetaEnumeration implements Enumeration {

        private Iterator pcIter;

        private Enumeration current;

        /**
         * Initiates this enumeration.
         * 
         * @param outer an iterator over external collection of
         *        PermissionCollections
         */
        public MetaEnumeration(Iterator outer) {
            pcIter = outer;
            current = getNextEnumeration();
        }

        private Enumeration getNextEnumeration() {
            while (pcIter.hasNext()) {
                Enumeration en = ((PermissionCollection)pcIter.next())
                    .elements();
                if (en.hasMoreElements()) {
                    return en;
                }
            }
            return null;
        }

        /**
         * Indicates if there are more elements to enumerate.
         */
        public boolean hasMoreElements() {
            return current != null /* && current.hasMoreElements() */;
        }

        /**
         * Returns next element.
         */
        public Object nextElement() {
            if (current != null) {
                //assert current.hasMoreElements();
                Object next = current.nextElement();
                if (!current.hasMoreElements()) {
                    current = getNextEnumeration();
                }

                return next;
            }
            throw new NoSuchElementException(Messages.getString("security.17")); //$NON-NLS-1$
        }
    }

    /**
     * Indicates whether the argument permission is implied by the permissions
     * contained in the receiver.
     * 
     * 
     * @return boolean <code>true</code> if the argument permission is implied
     *         by the permissions in the receiver, and <code>false</code> if
     *         it is not.
     * @param permission
     *            java.security.Permission the permission to check
     */
    public boolean implies(Permission permission) {
        if (permission == null) {
            // RI compatible
            throw new NullPointerException(Messages.getString("security.21")); //$NON-NLS-1$
        }
        if (allEnabled) {
            return true;
        }
        Class klass = permission.getClass();
        PermissionCollection klassMates = null;

        UnresolvedPermissionCollection billets = (UnresolvedPermissionCollection)klasses
            .get(UnresolvedPermission.class);
        if (billets != null && billets.hasUnresolved(permission)) {
            // try to fill up klassMates with freshly resolved permissions
            synchronized (klasses) {
                klassMates = (PermissionCollection)klasses.get(klass);
                try {
                    klassMates = billets.resolveCollection(permission,
                                                           klassMates);
                } catch (Exception ignore) {
                    //TODO log warning
                    ignore.printStackTrace();
                }

                if (klassMates != null) {
                    //maybe klassMates were just created
                    // so put them into common map
                    klasses.put(klass, klassMates);
                    // very uncommon case, but not improbable one
                    if (klass == AllPermission.class) {
                        allEnabled = true;
                    }
                }
            }
        } else {
            klassMates = (PermissionCollection)klasses.get(klass);
        }

        if (klassMates != null) {
            return klassMates.implies(permission);
        }
        return false;
    }

    /**
     * Reads the object from stream and checks for consistency.
     */
    private void readObject(java.io.ObjectInputStream in) throws IOException,
        ClassNotFoundException {
        ObjectInputStream.GetField fields = in.readFields();
        Map perms = (Map)fields.get("perms", null); //$NON-NLS-1$
        klasses = new HashMap();
        synchronized (klasses) {
            for (Iterator iter = perms.keySet().iterator(); iter.hasNext();) {
                Class key = (Class)iter.next();
                PermissionCollection pc = (PermissionCollection)perms.get(key);
                if (key != pc.elements().nextElement().getClass()) {
                    throw new InvalidObjectException(Messages.getString("security.22")); //$NON-NLS-1$
                }
                klasses.put(key, pc);
            }
        }
        allEnabled = fields.get("allPermission", null) != null; //$NON-NLS-1$
        if (allEnabled && !klasses.containsKey(AllPermission.class)) {
            throw new InvalidObjectException(Messages.getString("security.23")); //$NON-NLS-1$
        }
    }

    /**
     * Outputs fields via default mechanism.
     */
    private void writeObject(java.io.ObjectOutputStream out) throws IOException {
        ObjectOutputStream.PutField fields = out.putFields();
        fields.put("perms", new Hashtable(klasses)); //$NON-NLS-1$
        fields.put("allPermission", allEnabled ? klasses //$NON-NLS-1$
            .get(AllPermission.class) : null);
        out.writeFields();
    }
}
