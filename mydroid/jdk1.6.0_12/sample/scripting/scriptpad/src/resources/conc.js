/*
* @(#)conc.js	1.1 06/08/06
*
* Copyright (c) 2006 Sun Microsystems, Inc. All Rights Reserved.
*
* Redistribution and use in source and binary forms, with or without
* modification, are permitted provided that the following conditions are met:
*
* -Redistribution of source code must retain the above copyright notice, this
*  list of conditions and the following disclaimer.
*
* -Redistribution in binary form must reproduce the above copyright notice,
*  this list of conditions and the following disclaimer in the documentation
*  and/or other materials provided with the distribution.
*
* Neither the name of Sun Microsystems, Inc. or the names of contributors may
* be used to endorse or promote products derived from this software without
* specific prior written permission.
*
* This software is provided "AS IS," without a warranty of any kind. ALL
* EXPRESS OR IMPLIED CONDITIONS, REPRESENTATIONS AND WARRANTIES, INCLUDING
* ANY IMPLIED WARRANTY OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE
* OR NON-INFRINGEMENT, ARE HEREBY EXCLUDED. SUN MICROSYSTEMS, INC. ("SUN")
* AND ITS LICENSORS SHALL NOT BE LIABLE FOR ANY DAMAGES SUFFERED BY LICENSEE
* AS A RESULT OF USING, MODIFYING OR DISTRIBUTING THIS SOFTWARE OR ITS
* DERIVATIVES. IN NO EVENT WILL SUN OR ITS LICENSORS BE LIABLE FOR ANY LOST
* REVENUE, PROFIT OR DATA, OR FOR DIRECT, INDIRECT, SPECIAL, CONSEQUENTIAL,
* INCIDENTAL OR PUNITIVE DAMAGES, HOWEVER CAUSED AND REGARDLESS OF THE THEORY
* OF LIABILITY, ARISING OUT OF THE USE OF OR INABILITY TO USE THIS SOFTWARE,
* EVEN IF SUN HAS BEEN ADVISED OF THE POSSIBILITY OF SUCH DAMAGES.
*
* You acknowledge that this software is not designed, licensed or intended
* for use in the design, construction, operation or maintenance of any
* nuclear facility.
*/

/*
 * Concurrency utilities for JavaScript. These are based on
 * java.lang and java.util.concurrent API. The following functions 
 * provide a simpler API for scripts. Instead of directly using java.lang
 * and java.util.concurrent classes, scripts can use functions and
 * objects exported from here. 
 */

/**
 * Wrapper for java.lang.Object.wait
 *
 * can be called only within a sync method
 */
function wait(object) {
    var objClazz = java.lang.Class.forName('java.lang.Object');
    var waitMethod = objClazz.getMethod('wait', null);
    waitMethod.invoke(object, null);
}
wait.docString = "convenient wrapper for java.lang.Object.wait method";


/**
 * Wrapper for java.lang.Object.notify
 *
 * can be called only within a sync method
 */
function notify(object) {
    var objClazz = java.lang.Class.forName('java.lang.Object');
    var notifyMethod = objClazz.getMethod('notify', null);
    notifyMethod.invoke(object, null);
}
notify.docString = "convenient wrapper for java.lang.Object.notify method";


/**
 * Wrapper for java.lang.Object.notifyAll
 *
 * can be called only within a sync method
 */
function notifyAll(object)  {
    var objClazz = java.lang.Class.forName('java.lang.Object');
    var notifyAllMethod = objClazz.getMethod('notifyAll', null);
    notifyAllMethod.invoke(object, null);
}
notifyAll.docString = "convenient wrapper for java.lang.Object.notifyAll method";


/**
 * Creates a java.lang.Runnable from a given script
 * function.
 */
Function.prototype.runnable = function() {
    var args = arguments;
    var func = this;
    return new java.lang.Runnable() {
        run: function() {
            func.apply(null, args);
        }
    }
}

/**
 * Executes the function on a new Java Thread.
 */
Function.prototype.thread = function() {
    var t = new java.lang.Thread(this.runnable.apply(this, arguments));
    t.start();
    return t;
}

/**
 * Executes the function on a new Java daemon Thread.
 */
Function.prototype.daemon = function() {
    var t = new java.lang.Thread(this.runnable.apply(this, arguments));
    t.setDaemon(true);
    t.start();
    return t;
}

/**
 * Creates a java.util.concurrent.Callable from a given script
 * function.
 */
Function.prototype.callable = function() {
    var args = arguments;
    var func = this;
    return new java.util.concurrent.Callable() {
          call: function() { return func.apply(null, args); }
    }
}

/**
 * Registers the script function so that it will be called exit.
 */
Function.prototype.atexit = function () {
    var args = arguments;
    java.lang.Runtime.getRuntime().addShutdownHook(
         new java.lang.Thread(this.runnable.apply(this, args)));
}

/**
 * Executes the function asynchronously.  
 *
 * @return a java.util.concurrent.FutureTask
 */
Function.prototype.future = (function() {
    // default executor for future
    var juc = java.util.concurrent;
    var theExecutor = juc.Executors.newSingleThreadExecutor();
    // clean-up the default executor at exit
    (function() { theExecutor.shutdown(); }).atexit();
    return function() {
        return theExecutor.submit(this.callable.apply(this, arguments));
    }
})();

// shortcut for j.u.c lock classes
var Lock = java.util.concurrent.locks.ReentrantLock;
var RWLock = java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Executes a function after acquiring given lock. On return,
 * (normal or exceptional), lock is released.
 *
 * @param lock lock that is locked and unlocked
 */
Function.prototype.sync = function (lock) {
    if (arguments.length == 0) {
        throw "lock is missing";
    }
    var res = new Array(arguments.length - 1);
    for (var i = 0; i < res.length; i++) {
        res[i] = arguments[i + 1];
    }
    lock.lock();
    try {
        this.apply(null, res);
    } finally {
        lock.unlock();
    }
}

/**
 * Causes current thread to sleep for specified
 * number of milliseconds
 *
 * @param interval in milliseconds
 */
function sleep(interval) {
    java.lang.Thread.sleep(interval);
}
sleep.docString = "wrapper for java.lang.Thread.sleep method";

/**
 * Schedules a task to be executed once in
 * every N milliseconds specified. 
 *
 * @param callback function or expression to evaluate
 * @param interval in milliseconds to sleep
 * @return timeout ID (which is nothing but Thread instance)
 */
function setTimeout(callback, interval) {
    if (! (callback instanceof Function)) {
        callback = new Function(callback);
    }

    // start a new thread that sleeps given time
    // and calls callback in an infinite loop
    return (function() {
         while (true) {
             sleep(interval);
             callback();
         }
    }).daemon();
}
setTimeout.docString = "calls given callback once after specified interval"

/** 
 * Cancels a timeout set earlier.
 * @param tid timeout ID returned from setTimeout
 */
function clearTimeout(tid) {
    // we just interrupt the timer thread
    tid.interrupt();
}

/**
 * Simple access to thread local storage. 
 *
 * Script sample:
 *
 *  __thread.x = 44;
 *  function f() { 
 *      __thread.x = 'hello'; 
 *      print(__thread.x); 
 *  }
 *  f.thread();       // prints 'hello'
 * print(__thread.x); // prints 44 in main thread
 */
var __thread = (function () {
    var map = new Object();
    return new JSAdapter() {
        __has__: function(name) {
            return map[name] != undefined;
        },
        __get__: function(name) {
            if (map[name] != undefined) {
                return map[name].get();
            } else {
                return undefined;
            }
        },
        __put__: sync(function(name, value) {
            if (map[name] == undefined) {
                var tmp = new java.lang.ThreadLocal();
                tmp.set(value);
                map[name] = tmp;
            } else {
                map[name].set(value);
            }
        }),
        __delete__: function(name) {
            if (map[name] != undefined) {
                map[name].set(null);
            }            
        }
    }
})();

