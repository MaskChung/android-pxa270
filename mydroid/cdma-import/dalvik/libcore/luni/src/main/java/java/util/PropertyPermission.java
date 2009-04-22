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
import java.io.ObjectStreamField;
import java.security.BasicPermission;
import java.security.Permission;
import java.security.PermissionCollection;

import org.apache.harmony.luni.util.Util;

/**
 * PropertyPermission objects represent permission to access system properties.
 */
public final class PropertyPermission extends BasicPermission {
    private static final long serialVersionUID = 885438825399942851L;

    transient private boolean read, write;

    /**
     * Constructs a new instance of this class.
     * 
     * @param name
     *            java.lang.String the (possibly wildcarded) name of the
     *            property.
     * @param actions
     *            java.lang.String the actions which are applicable to it.
     */
    public PropertyPermission(String name, String actions) {
        super(name);
        decodeActions(actions);
    }

    private void decodeActions(String actions) {
        StringTokenizer tokenizer = new StringTokenizer(Util.toASCIILowerCase(actions),
                " \t\n\r,"); //$NON-NLS-1$
        while (tokenizer.hasMoreTokens()) {
            String token = tokenizer.nextToken();
            if (token.equals("read")) { //$NON-NLS-1$
                read = true;
            } else if (token.equals("write")) { //$NON-NLS-1$
                write = true;
            } else {
                throw new IllegalArgumentException();
            }
        }
        if (!read && !write) {
            throw new IllegalArgumentException();
        }
    }

    /**
     * Compares the argument to the receiver, and returns true if they represent
     * the <em>same</em> object using a class specific comparison. In this
     * case, the receiver must be for the same property as the argument, and
     * must have the same actions.
     * 
     * @param o
     *            the object to compare with this object
     * @return <code>true</code> if the object is the same as this object
     *         <code>false</code> if it is different from this object
     * @see #hashCode
     */
    @Override
    public boolean equals(Object o) {
        if (super.equals(o)) {
            PropertyPermission pp = (PropertyPermission) o;
            return read == pp.read && write == pp.write;
        }
        return false;
    }

    /**
     * Returns the actions associated with the receiver. The result will be
     * either "read", "write", or "read,write".
     * 
     * @return String the actions associated with the receiver.
     */
    @Override
    public String getActions() {
        return read ? (write ? "read,write" : "read") : "write"; //$NON-NLS-1$ //$NON-NLS-2$//$NON-NLS-3$
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
        return super.hashCode();
    }

    /**
     * Indicates whether the argument permission is implied by the receiver.
     * 
     * @return boolean <code>true</code> if the argument permission is implied
     *         by the receiver, and <code>false</code> if it is not.
     * @param permission
     *            java.security.Permission the permission to check
     */
    @Override
    public boolean implies(Permission permission) {
        if (super.implies(permission)) {
            PropertyPermission pp = (PropertyPermission) permission;
            return (read || !pp.read) && (write || !pp.write);
        }
        return false;
    }

    /**
     * Returns a new PermissionCollection for holding permissions of this class.
     * Answer null if any permission collection can be used.
     * 
     * @return a new PermissionCollection or null
     * 
     * see java.security.BasicPermissionCollection
     */
    @Override
    public PermissionCollection newPermissionCollection() {
        return new PropertyPermissionCollection();
    }

    private static final ObjectStreamField[] serialPersistentFields = { new ObjectStreamField(
            "actions", String.class) }; //$NON-NLS-1$

    private void writeObject(ObjectOutputStream stream) throws IOException {
        ObjectOutputStream.PutField fields = stream.putFields();
        fields.put("actions", getActions()); //$NON-NLS-1$
        stream.writeFields();
    }

    private void readObject(ObjectInputStream stream) throws IOException,
            ClassNotFoundException {
        ObjectInputStream.GetField fields = stream.readFields();
        String actions = (String) fields.get("actions", ""); //$NON-NLS-1$ //$NON-NLS-2$
        decodeActions(actions);
    }
}
