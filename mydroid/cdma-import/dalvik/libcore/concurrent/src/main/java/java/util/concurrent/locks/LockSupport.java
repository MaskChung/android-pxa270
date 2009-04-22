/*
 * Written by Doug Lea with assistance from members of JCP JSR-166
 * Expert Group and released to the public domain, as explained at
 * http://creativecommons.org/licenses/publicdomain
 */

package java.util.concurrent.locks;
import java.util.concurrent.*;
import sun.misc.Unsafe;


/**
 * Basic thread blocking primitives for creating locks and other
 * synchronization classes.
 *
 * <p>This class associates with each thread that uses it, a permit
 * (in the sense of the {@link java.util.concurrent.Semaphore
 * Semaphore} class). A call to <tt>park</tt> will return immediately
 * if the permit is available, consuming it in the process; otherwise
 * it <em>may</em> block.  A call to <tt>unpark</tt> makes the permit
 * available, if it was not already available. (Unlike with Semaphores
 * though, permits do not accumulate. There is at most one.)
 *
 * <p>Methods <tt>park</tt> and <tt>unpark</tt> provide efficient
 * means of blocking and unblocking threads that do not encounter the
 * problems that cause the deprecated methods <tt>Thread.suspend</tt>
 * and <tt>Thread.resume</tt> to be unusable for such purposes: Races
 * between one thread invoking <tt>park</tt> and another thread trying
 * to <tt>unpark</tt> it will preserve liveness, due to the
 * permit. Additionally, <tt>park</tt> will return if the caller's
 * thread was interrupted, and timeout versions are supported. The
 * <tt>park</tt> method may also return at any other time, for "no
 * reason", so in general must be invoked within a loop that rechecks
 * conditions upon return. In this sense <tt>park</tt> serves as an
 * optimization of a "busy wait" that does not waste as much time
 * spinning, but must be paired with an <tt>unpark</tt> to be
 * effective.
 *
 * <p>These methods are designed to be used as tools for creating
 * higher-level synchronization utilities, and are not in themselves
 * useful for most concurrency control applications.
 *
 * <p><b>Sample Usage.</b> Here is a sketch of a First-in-first-out
 * non-reentrant lock class.
 * <pre>
 *   private AtomicBoolean locked = new AtomicBoolean(false);
 *   private Queue&lt;Thread&gt; waiters = new ConcurrentLinkedQueue&lt;Thread&gt;();
 *
 *   public void lock() { 
 *     boolean wasInterrupted = false;
 *     Thread current = Thread.currentThread();
 *     waiters.add(current);
 *
 *     // Block while not first in queue or cannot acquire lock
 *     while (waiters.peek() != current || 
 *            !locked.compareAndSet(false, true)) { 
 *        LockSupport.park();
 *        if (Thread.interrupted()) // ignore interrupts while waiting
 *          wasInterrupted = true;
 *     }
 *
 *     waiters.remove();
 *     if (wasInterrupted)          // reassert interrupt status on exit
 *        current.interrupt();
 *   }
 *
 *   public void unlock() {
 *     locked.set(false);
 *     LockSupport.unpark(waiters.peek());
 *   } 
 * }
 * </pre>
 */
@SuppressWarnings("all")
public class LockSupport {
    private LockSupport() {} // Cannot be instantiated.

    // BEGIN android-changed
    private static final Unsafe unsafe = UnsafeAccess.THE_ONE;
    // END android-changed

    /**
     * Make available the permit for the given thread, if it
     * was not already available.  If the thread was blocked on
     * <tt>park</tt> then it will unblock.  Otherwise, its next call
     * to <tt>park</tt> is guaranteed not to block. This operation
     * is not guaranteed to have any effect at all if the given
     * thread has not been started.
     * @param thread the thread to unpark, or <tt>null</tt>, in which case
     * this operation has no effect. 
     */
    public static void unpark(Thread thread) {
        if (thread != null)
            unsafe.unpark(thread);
    }

    /**
     * Disables the current thread for thread scheduling purposes unless the
     * permit is available.
     * <p>If the permit is available then it is consumed and the call returns
     * immediately; otherwise 
     * the current thread becomes disabled for thread scheduling 
     * purposes and lies dormant until one of three things happens:
     * <ul>
     * <li>Some other thread invokes <tt>unpark</tt> with the current thread 
     * as the target; or
     * <li>Some other thread {@link Thread#interrupt interrupts} the current
     * thread; or
     * <li>The call spuriously (that is, for no reason) returns.
     * </ul>
     * <p>This method does <em>not</em> report which of these caused the 
     * method to return. Callers should re-check the conditions which caused 
     * the thread to park in the first place. Callers may also determine, 
     * for example, the interrupt status of the thread upon return.
     */
    public static void park() {
        unsafe.park(false, 0L);
    }

    /**
     * Disables the current thread for thread scheduling purposes, for up to
     * the specified waiting time, unless the permit is available.
     * <p>If the permit is available then it is consumed and the call returns
     * immediately; otherwise 
     * the current thread becomes disabled for thread scheduling 
     * purposes and lies dormant until one of four things happens:
     * <ul>
     * <li>Some other thread invokes <tt>unpark</tt> with the current thread 
     * as the target; or
     * <li>Some other thread {@link Thread#interrupt interrupts} the current
     * thread; or
     * <li>The specified waiting time elapses; or
     * <li>The call spuriously (that is, for no reason) returns.
     * </ul>
     * <p>This method does <em>not</em> report which of these caused the 
     * method to return. Callers should re-check the conditions which caused 
     * the thread to park in the first place. Callers may also determine, 
     * for example, the interrupt status of the thread, or the elapsed time
     * upon return.
     *
     * @param nanos the maximum number of nanoseconds to wait
     */
    public static void parkNanos(long nanos) {
        if (nanos > 0)
            unsafe.park(false, nanos);   
    }

    /**
     * Disables the current thread for thread scheduling purposes, until
     * the specified deadline, unless the permit is available.
     * <p>If the permit is available then it is consumed and the call returns
     * immediately; otherwise 
     * the current thread becomes disabled for thread scheduling 
     * purposes and lies dormant until one of four things happens:
     * <ul>
     * <li>Some other thread invokes <tt>unpark</tt> with the current thread 
     * as the target; or
     * <li>Some other thread {@link Thread#interrupt interrupts} the current
     * thread; or
     * <li>The specified deadline passes; or
     * <li>The call spuriously (that is, for no reason) returns.
     * </ul>
     * <p>This method does <em>not</em> report which of these caused the 
     * method to return. Callers should re-check the conditions which caused 
     * the thread to park in the first place. Callers may also determine, 
     * for example, the interrupt status of the thread, or the current time
     * upon return.
     *
     * @param deadline the absolute time, in milliseconds from the Epoch, to
     * wait until
     */
    public static void parkUntil(long deadline) {
        unsafe.park(true, deadline);   
    }

}


