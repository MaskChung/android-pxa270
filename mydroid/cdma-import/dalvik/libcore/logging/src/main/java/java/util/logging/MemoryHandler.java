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

package java.util.logging;

import java.security.AccessController;
import java.security.PrivilegedExceptionAction;

import org.apache.harmony.logging.internal.nls.Messages;


/**
 * A <code>Handler</code> put the description of log events into a cycled memory 
 * buffer.
 * <p> 
 * Mostly this <code>MemoryHandler</code> just puts the given <code>LogRecord</code>
 * into the internal buffer and doesn't perform any formatting or any other process.
 * When the buffer is full, the earliest buffered records will be discarded.  
 * </p>
 * <p>
 * Every <code>MemoryHandler</code> has a target handler, and push action can be 
 * triggered so that all buffered records will be output to the target handler 
 * and normally the latter will publish the records. After the push action, the 
 * buffer will be cleared.    
 * </p>
 * <p>
 * The push action can be triggered in three ways:
 * <ul>
 * <li>The push method is called explicitly</li>
 * <li>When a new <code>LogRecord</code> is put into the internal buffer, and it has a level which is not less than the specified push level.</li>
 * <li>A subclass extends this <code>MemoryHandler</code> and call push method implicitly according to some criteria.</li>
 * </ul>
 * </p>
 * <p>
 * <code>MemoryHandler</code> will read following <code>LogManager</code> 
 * properties for initialization, if given properties are not defined or has 
 * invalid values, default value will be used.
 * <ul>
 * <li>java.util.logging.MemoryHandler.level specifies the level for this 
 * <code>Handler</code>, defaults to <code>Level.ALL</code>.</li>
 * <li>java.util.logging.MemoryHandler.filter specifies the <code>Filter</code> 
 * class name, defaults to no <code>Filter</code>.</li>
 * <li>java.util.logging.MemoryHandler.size specifies the buffer size in number 
 * of <code>LogRecord</code>, defaults to 1000.</li>
 * <li>java.util.logging.MemoryHandler.push specifies the push level, defaults 
 * to level.SEVERE.</li>
 * <li>java.util.logging.MemoryHandler.target specifies the class of the target 
 * <code>Handler</code>, no default value, which means this property must be 
 * specified either by property setting or by constructor.</li> 
 * </ul>
 * </p>
 * 
 */
public class MemoryHandler extends Handler {

    //default maximum buffered number of LogRecord 
    private static final int DEFAULT_SIZE = 1000;
    //target handler
    private Handler target;
    
    //buffer size
    private int size = DEFAULT_SIZE;
    
    //push level
    private Level push = Level.SEVERE;

    //LogManager instance for convenience
    private final LogManager manager = LogManager.getLogManager();
    
    //buffer
    private LogRecord[] buffer;
    
    //current position in buffer
    private int cursor;
    
    /**
     * Default constructor, construct and init a <code>MemoryHandler</code> using 
     * <code>LogManager</code> properties or default values
     */
    public MemoryHandler() {
        super();
        String className = this.getClass().getName();
        //init target
        final String targetName = manager.getProperty(className+".target"); //$NON-NLS-1$
        try {
            Class<?> targetClass = AccessController.doPrivileged(new PrivilegedExceptionAction<Class<?>>(){
                public Class<?> run() throws Exception{
                    ClassLoader loader = Thread.currentThread().getContextClassLoader();
                    if(loader == null){
                        loader = ClassLoader.getSystemClassLoader();
                    }
                    return loader.loadClass(targetName);
                }
            });
            target = (Handler) targetClass.newInstance();
        } catch (Exception e) {
            // logging.10=Cannot load target handler:{0}
            throw new RuntimeException(Messages.getString("logging.10", //$NON-NLS-1$
                    targetName));
        }
        //init size
        String sizeString = manager.getProperty(className+".size"); //$NON-NLS-1$
        if (null != sizeString) {
            try {
                size = Integer.parseInt(sizeString);
                if(size <= 0){
                    size = DEFAULT_SIZE;
                }
            } catch (Exception e) {
                printInvalidPropMessage(className+".size", sizeString, e); //$NON-NLS-1$
            }
        }
        //init push level
        String pushName = manager.getProperty(className+".push"); //$NON-NLS-1$
        if (null != pushName) {
            try {
                push = Level.parse(pushName);
            } catch (Exception e) {
                printInvalidPropMessage(className+".push", pushName, e); //$NON-NLS-1$
            }
        }
        //init other properties which are common for all Handler
        initProperties("ALL", null, "java.util.logging.SimpleFormatter", null);  //$NON-NLS-1$//$NON-NLS-2$
        buffer = new LogRecord[size];
    }
    
    /**
     * Construct and init a <code>MemoryHandler</code> using given target, size 
     * and push level, other properties using <code>LogManager</code> properties
     * or default values
     * 
     * @param target
     *                 the given <code>Handler</code> to output
     * @param size    the maximum number of buffered <code>LogRecord</code>
     * @param pushLevel
     *                 the push level
     * @throws IllegalArgumentException
     *                 if size<=0
     */
    public MemoryHandler(Handler target, int size, Level pushLevel) {
        if (size <= 0) {
            // logging.11=Size must be positive.
            throw new IllegalArgumentException(Messages.getString("logging.11")); //$NON-NLS-1$
        }
        target.getLevel();
        pushLevel.intValue();
        this.target = target;
        this.size = size;
        this.push = pushLevel;
        initProperties("ALL", null, "java.util.logging.SimpleFormatter", null);  //$NON-NLS-1$//$NON-NLS-2$
        buffer = new LogRecord[size];
    }
    
    /**
     * Close this handler and target handler, free all associated resources
     * 
     * @throws SecurityException
     *                 if security manager exists and it determines that caller 
     *                 does not have the required permissions to control this handler
     */
    @Override
    public void close() {
        manager.checkAccess();
        target.close();
        setLevel(Level.OFF);
    }

    /**
     * Call target handler to flush any buffered output.
     * 
     * Note that this doesn't cause this <code>MemoryHandler</code> to push.
     * 
     */
    @Override
    public void flush() {
        target.flush();
    }

    /**
     * Put a given <code>LogRecord</code> into internal buffer.
     * 
     * If given record is not loggable, just return. Otherwise it is stored in 
     * the buffer. Furthermore if the record's level is not less than the push
     * level, the push action is triggered to output all the buffered records 
     * to the target handler, and the target handler will publish them.
     * 
     * @param record the log record.
     */
    @Override
    public synchronized void publish(LogRecord record) {
        if (!isLoggable(record)) {
            return;
        }
        if (cursor >= size) {
            cursor = 0;
        }
        buffer[cursor++] = record;
        if (record.getLevel().intValue() >= push.intValue()) {
            push();
        }
        return;
    }

    /**
     * Return the push level.
     * 
     * @return the push level
     */
    public Level getPushLevel() {
        return push;
    }

    /**
     * <p>Check if given <code>LogRecord</code> would be put into this 
     * <code>MemoryHandler</code>'s internal buffer.
     * </p>
     * <p>
     * The given <code>LogRecord</code> is loggable if and only if it has 
     * appropriate level and it pass any associated filter's check. 
     * </p>
     * <p>
     * Note that the push level is not used for this check. 
     * </p>
     * @param record
     *                 the given <code>LogRecord</code>
     * @return         if the given <code>LogRecord</code> should be logged
     */
    @Override
    public boolean isLoggable(LogRecord record) {
        return super.isLoggable(record);
    }

    /**
     * Triggers a push action to output all buffered records to the target handler,
     * and the target handler will publish them. Then the buffer is cleared.
     */
    public void push() {
        for (int i = cursor; i < size; i++) {
            if(null != buffer[i]) {
                target.publish(buffer[i]);
            }
            buffer[i] = null;
        }
        for (int i = 0; i < cursor; i++) {
            if(null != buffer[i]) {
                target.publish(buffer[i]);
            }
            buffer[i] = null;
        }
        cursor = 0;
    }

    /**
     * Set the push level. The push level is used to check the push action 
     * triggering. When a new <code>LogRecord</code> is put into the internal
     * buffer and its level is not less than the push level, the push action 
     * will be triggered. Note that set new push level won't trigger push action.
     * 
     * @param newLevel
     *                 the new level to set
     * @throws SecurityException
     *                 if security manager exists and it determines that caller 
     *                 does not have the required permissions to control this handler
     */
    public void setPushLevel(Level newLevel) {
        manager.checkAccess();
        newLevel.intValue();
        this.push = newLevel;
    }
}
