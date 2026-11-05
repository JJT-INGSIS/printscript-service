package com.jjt.ingsis.printscript.processor

import java.util.concurrent.Semaphore
import java.util.concurrent.atomic.AtomicBoolean

class ProcessorCapacity(
    maximum: Int,
) {
    private val active = Semaphore(maximum)
    private val background = Semaphore(1)

    @Synchronized
    fun acquire(backgroundWork: Boolean = false): AutoCloseable? {
        if (backgroundWork && (active.availablePermits() <= 1 || !background.tryAcquire())) return null
        if (!active.tryAcquire()) {
            if (backgroundWork) background.release()
            return null
        }
        val released = AtomicBoolean()
        return AutoCloseable {
            if (released.compareAndSet(false, true)) {
                active.release()
                if (backgroundWork) background.release()
            }
        }
    }
}
