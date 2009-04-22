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
 * This exception is thrown when an attempt is made to add more than one
 * listener to an event source which only supports a single listener. It is also
 * thrown when the same listener is added more than once.
 * 
 * @see java.lang.Exception
 */
public class TooManyListenersException extends Exception {

    private static final long serialVersionUID = 5074640544770687831L;

    /**
     * Constructs a new instance of this class with its walkback filled in.
     */
    public TooManyListenersException() {
        super();
    }

    /**
     * Constructs a new instance of this class with its walkback and message
     * filled in.
     * 
     * @param detailMessage
     *            String The detail message for the exception.
     */
    public TooManyListenersException(String detailMessage) {
        super(detailMessage);
    }

}
