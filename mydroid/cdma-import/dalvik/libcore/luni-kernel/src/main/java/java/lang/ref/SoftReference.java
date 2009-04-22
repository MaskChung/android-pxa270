/* 
 * Licensed to the Apache Software Foundation (ASF) under one or more
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

package java.lang.ref;

/**
 * Implements a soft reference, which is the least-weak of the three types of
 * references. Once the garbage collector has decided that an object {@code obj}
 * is <a href="package.html#definitions>softly-reachable</a>, the following
 * may happen, either immediately or at a later point:
 * 
 * <ul>
 *   <li>
 *     A set {@code ref} of references is determined. {@code ref} contains the
 *     following elements:
 *     <ul>
 *       <li>
 *         All soft references pointing to {@code obj}.
 *       </li>
 *       <li>
 *         All soft references pointing to objects from which {@code obj} is
 *         strongly reachable.
 *       </li>
 *     </ul>
 *   </li>
 *   <li>
 *     All references in {@code ref} are atomically cleared.
 *   </li>
 *   <li>
 *     At the same time or some time in the future, all references in {@code
 *     ref} will be enqueued with their corresponding reference queues, if any.
 *   </li>  
 * </ul>
 * 
 * The system may decide not to clear and enqueue soft references until a later
 * time, yet all {@code SoftReference}s pointing to softly reachable objects are
 * guaranteed to be cleared before the VM will throw an {@link
 * java.lang.OutOfMemoryError}.
 *  
 * Soft references are useful for caches that should automatically have
 * their entries removed once they are not referenced any more (from outside),
 * and there is a need for memory. The difference between a {@code
 * SoftReference} and a {@code WeakReference} is the point of time at which the
 * decision is made to clear and enqueue the reference:
 * 
 * <ul>
 *   <li>
 *     A {@code SoftReference} should be cleared and enqueued <em>as late as
 *     possible</em>, that is, in case the VM is in danger of running out of
 *     memory.
 *   </li>
 *   <li>
 *     A {@code WeakReference} may be cleared and enqueued as soon as is
 *     known to be weakly-referenced.  
 *   </li>
 * </ul>
 * 
 * @since Android 1.0
 */
public class SoftReference<T> extends Reference<T> {

    /**
     * Constructs a new soft reference to the given referent. The newly created
     * reference is not registered with any reference queue.
     * 
     * @param r the referent to track
     * 
     * @since Android 1.0
     */
    public SoftReference(T r) {
        super();
        
        referent = r;
    }
    
    /**
     * Constructs a new soft reference to the given referent. The newly created
     * reference is registered with the given reference queue.
     * 
     * @param r the referent to track
     * @param q the queue to register to the reference object with. A null value
     *          results in a weak reference that is not associated with any
     *          queue.
     * 
     * @since Android 1.0
     */
    public SoftReference(T r, ReferenceQueue<? super T> q) {
        super();
        
        referent = r;
        queue = q;
    }

// BEGIN android-removed
//    /**
//     * Return the referent of the reference object.
//     * 
//     * @return the referent to which reference refers, or {@code null} if the
//     *         referent has been cleared.
//     * 
//     * @since Android 1.0
//     */
//    @Override
//    public T get() {
//        return super.get();
//    }
// END android-removed
    
}
