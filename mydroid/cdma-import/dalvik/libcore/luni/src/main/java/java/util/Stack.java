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


/**
 * <code>Stack</code> is a Last-In/First-Out(LIFO) data structure which
 * represents a stack of objects. It enables users to pop and push onto the
 * stack, including null objects. There is no limit to the size of the stack
 */
public class Stack<E> extends Vector<E> {
    
    private static final long serialVersionUID = 1224463164541339165L;

    /**
     * Constructs a stack with the default size of <code>Vector</code>.
     */
    public Stack() {
        super();
    }

    /**
     * Determines if the stack is empty or not.
     * 
     * @return true if the stack is empty, false otherwise
     */
    public boolean empty() {
        return elementCount == 0;
    }

    /**
     * Returns the element at the top of the stack without removing it.
     * 
     * @return the element at the top of the Stack
     * @exception EmptyStackException
     *                when empty() is true
     * @see #pop
     */
    public synchronized E peek() {
        try {
            return (E)elementData[elementCount - 1];
        } catch (IndexOutOfBoundsException e) {
            throw new EmptyStackException();
        }
    }

    /**
     * Returns the element at the top of the stack and removes it.
     * 
     * @return the element at the top of the stack.
     * @exception EmptyStackException
     *                when empty() is true
     * @see #peek
     * @see #push
     */
    public synchronized E pop() {
        try {
            int index = elementCount - 1;
            E obj = (E)elementData[index];
            removeElementAt(index);
            return obj;
        } catch (IndexOutOfBoundsException e) {
            throw new EmptyStackException();
        }
    }

    /**
     * Pushes the object from the parameter onto the top of the stack.
     * 
     * @param object
     *            The object to be added to the stack
     * 
     * @return the object argument
     * 
     * @see #peek
     * @see #pop
     */
    public synchronized E push(E object) {
        addElement(object);
        return object;
    }

    /**
     * Returns the index of the first occurrence of the object.
     * 
     * @return the index of the first occurrence of the object
     * @param o
     *            the object to be searched
     */
    public synchronized int search(Object o) {
        int index = lastIndexOf(o);
        if (index >= 0)
            return (elementCount - index);
        return -1;
    }
}
