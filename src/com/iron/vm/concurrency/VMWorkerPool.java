package com.iron.vm.concurrency;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.LockSupport;

public final class VMWorkerPool {

    private final int workerCount;
    private final VMWorkerThread[] workers;
    private final BlockingQueue<VMTask> taskQueue;
    private final AtomicBoolean isShutdown;
    private final AtomicInteger activeWorkers;
    private final AtomicInteger safepointRequested;
    private final Object safepointMonitor;

    public interface VMTask {
        void execute(int workerId);
    }

    public VMWorkerPool(int workerCount) {
        if (workerCount <= 0) {
            throw new IllegalArgumentException("Worker count must be greater than zero.");
        }
        this.workerCount = workerCount;
        this.workers = new VMWorkerThread[workerCount];
        this.taskQueue = new LinkedBlockingQueue<>();
        this.isShutdown = new AtomicBoolean(false);
        this.activeWorkers = new AtomicInteger(0);
        this.safepointRequested = new AtomicInteger(0);
        this.safepointMonitor = new Object();

        ThreadFactory factory = r -> {
            Thread t = new Thread(r);
            t.setDaemon(true);
            t.setUncaughtExceptionHandler((thread, ex) -> {
                System.err.println("[IRON-VM PANIC] Uncaught exception in worker thread " + thread.getName() + ": " + ex.getMessage());
                ex.printStackTrace(System.err);
            });
            return t;
        };

        for (int i = 0; i < workerCount; i++) {
            workers[i] = new VMWorkerThread(i, this, factory);
            workers[i].start();
        }
    }

    public void submit(VMTask task) {
        if (isShutdown.get()) {
            throw new IllegalStateException("Cannot submit tasks to a shut down VMWorkerPool.");
        }
        if (task == null) {
            throw new IllegalArgumentException("Task cannot be null.");
        }
        taskQueue.offer(task);
    }

    public void requestSafepoint() {
        if (isShutdown.get()) {
            return;
        }
        safepointRequested.set(1);
        
        // Interrupt all workers waiting or sleeping so they check the safepoint flag
        for (int i = 0; i < workerCount; i++) {
            VMWorkerThread worker = workers[i];
            if (worker != null) {
                LockSupport.unpark(worker);
            }
        }

        // Wait until all active or executing threads reach the safepoint
        synchronized (safepointMonitor) {
            while (true) {
                int active = activeWorkers.get();
                int reached = 0;
                for (int i = 0; i < workerCount; i++) {
                    if (workers[i].isAtSafepoint()) {
                        reached++;
                    }
                }
                // If every worker is either idle (not active) or parked at a safepoint, we achieve STW.
                if (reached == workerCount || active == 0) {
                    break;
                }
                try {
                    safepointMonitor.wait(1);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
    }

    public void releaseSafepoint() {
        safepointRequested.set(0);
        synchronized (safepointMonitor) {
            safepointMonitor.notifyAll();
        }
        for (int i = 0; i < workerCount; i++) {
            VMWorkerThread worker = workers[i];
            if (worker != null) {
                worker.signalReleaseSafepoint();
            }
        }
    }

    public boolean isSafepointRequested() {
        return safepointRequested.get() != 0;
    }

    public void cooperativeSafepointCheck() {
        if (safepointRequested.get() != 0) {
            VMWorkerThread current = (VMWorkerThread) Thread.currentThread();
            current.setAtSafepoint(true);
            synchronized (safepointMonitor) {
                safepointMonitor.notifyAll();
            }
            try {
                while (safepointRequested.get() != 0 && !isShutdown.get()) {
                    LockSupport.park(this);
                }
            } finally {
                current.setAtSafepoint(false);
            }
        }
    }

    public void shutdown() {
        if (isShutdown.compareAndSet(false, true)) {
            for (int i = 0; i < workerCount; i++) {
                if (workers[i] != null) {
                    workers[i].interrupt();
                    LockSupport.unpark(workers[i]);
                }
            }
            for (int i = 0; i < workerCount; i++) {
                try {
                    if (workers[i] != null) {
                        workers[i].join(1000);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        }
    }

    public int getWorkerCount() {
        return workerCount;
    }

    public int getActiveWorkerCount() {
        return activeWorkers.get();
    }

    private static final class VMWorkerThread extends Thread {
        private final int workerId;
        private final VMWorkerPool pool;
        private volatile boolean atSafepoint;

        private VMWorkerThread(int workerId, VMWorkerPool pool, ThreadFactory factory) {
            super();
            this.workerId = workerId;
            this.pool = pool;
            this.atSafepoint = false;
            setName("IronVM-Worker-" + workerId);
        }

        @Override
        public void run() {
            while (!pool.isShutdown.get()) {
                try {
                    pool.cooperativeSafepointCheck();
                    
                    VMTask task = pool.taskQueue.poll();
                    if (task == null) {
                        pool.activeWorkers.decrementAndGet();
                        task = pool.taskQueue.take();
                        pool.activeWorkers.incrementAndGet();
                    }

                    pool.activeWorkers.incrementAndGet();
                    try {
                        pool.cooperativeSafepointCheck();
                        task.execute(workerId);
                    } catch (Throwable t) {
                        System.err.println("[IRON-VM ERROR] Worker " + workerId + " caught exception during task execution: " + t.getMessage());
                        t.printStackTrace(System.err);
                    } finally {
                        pool.activeWorkers.decrementAndGet();
                    }

                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    if (pool.isShutdown.get()) {
                        break;
                    }
                }
            }
        }

        public boolean isAtSafepoint() {
            return atSafepoint;
        }

        public void setAtSafepoint(boolean atSafepoint) {
            this.atSafepoint = atSafepoint;
        }

        public void signalReleaseSafepoint() {
            LockSupport.unpark(this);
        }
    }
}