/* Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 * 
 *     http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */


package java.util.prefs;

import java.io.Serializable;
import java.util.EventObject;
import java.io.ObjectOutputStream;
import java.io.ObjectInputStream;
import java.io.NotSerializableException;
import java.io.IOException;

/**
 * This is the event class to indicate one child of the preferences node has 
 * been added or deleted.
 * <p>
 * Please note that this class cannot be serialized actually, so relevant 
 * serialization methods only throw <code>NotSerializableException</code>.</p>
 * 
 * @see java.util.prefs.Preferences
 * @see java.util.prefs.NodeChangeListener
 * 
 * @since 1.4
 */
public class NodeChangeEvent extends EventObject implements Serializable {
    
    private static final long serialVersionUID = 8068949086596572957L;
    
    private final Preferences parent;
    private final Preferences child;
    
    /**
     * Construct a new <code>NodeChangeEvent</code> instance.
     * 
     * @param p        the <code>Preferences</code> instance that this event happened, 
     *                 this object is considered as event's source.
     * @param c        the child <code>Preferences</code> instance that was added 
     *                 or deleted.
     */
    public NodeChangeEvent (Preferences p, Preferences c) {
        super(p);
        parent = p;
        child = c;
    }
    
    /**
     * Get the <code>Preferences</code> instance that this event happened.
     * 
     * @return        the <code>Preferences</code> instance that this event happened.
     */
    public Preferences getParent() {
        return parent;
    }
    
    /**
     * Get the child <code>Preferences</code> node that was added or removed.
     * 
     * @return        the child <code>Preferences</code> node that was added or removed.
     */
    public Preferences getChild() {
        return child;
    }
    
    /*
     * This method always throws a <code>NotSerializableException</code>, because 
     * this object cannot be serialized,  
     */
    private void writeObject (ObjectOutputStream out) throws IOException {
        throw new NotSerializableException();
    }
    
    /*
     * This method always throws a <code>NotSerializableException</code>, because 
     * this object cannot be serialized,  
     */
    private void readObject (ObjectInputStream in) throws IOException, ClassNotFoundException {
        throw new NotSerializableException();
    }
}



 
