package com.vectras.vm.core;

import org.junit.Assert;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;

public class ProcessLaunchLifecycleTest {

    @Test
    public void registryIdsDoNotCollideUnderBurstLaunches() throws Exception {
        Method method = ProcessLaunch.class.getDeclaredMethod(
                "buildRegistryId", String.class, String.class, String.class, String.class);
        method.setAccessible(true);
        Set<String> ids = new HashSet<>();
        for (int i = 0; i < 256; i++) {
            String id = (String) method.invoke(null, "setup", "probe", "test", "vm");
            Assert.assertTrue("duplicate registry ID: " + id, ids.add(id));
        }
    }

    @Test
    public void interruptedWaitTerminatesChildAndRestoresInterruptFlag() {
        InterruptedWaitProcess process = new InterruptedWaitProcess();
        try {
            ProcessRuntimeOps.TimeoutExecutionResult result =
                    ProcessRuntimeOps.waitForWithTimeout(process, 250L,
                            TimeUnit.MILLISECONDS, "interrupted-test");
            Assert.assertEquals(ProcessRuntimeOps.TimeoutExecutionResult.Status.ERROR, result.status);
            Assert.assertTrue(result.message.contains("_interrupted_terminated"));
            Assert.assertTrue(process.destroyCalled);
            Assert.assertFalse(process.isAlive());
            Assert.assertTrue(Thread.currentThread().isInterrupted());
        } finally {
            // Do not contaminate subsequent JUnit tests on a reused worker thread.
            Thread.interrupted();
        }
    }

    private static final class InterruptedWaitProcess extends Process {
        boolean alive = true;
        boolean firstWait = true;
        boolean destroyCalled;

        @Override
        public OutputStream getOutputStream() { return new ByteArrayOutputStream(); }

        @Override
        public InputStream getInputStream() { return new ByteArrayInputStream(new byte[0]); }

        @Override
        public InputStream getErrorStream() { return new ByteArrayInputStream(new byte[0]); }

        @Override
        public int waitFor() { return 143; }

        @Override
        public boolean waitFor(long timeout, TimeUnit unit) throws InterruptedException {
            if (firstWait) {
                firstWait = false;
                throw new InterruptedException("simulated");
            }
            return !alive;
        }

        @Override
        public int exitValue() {
            if (alive) throw new IllegalThreadStateException("still alive");
            return 143;
        }

        @Override
        public void destroy() {
            destroyCalled = true;
            alive = false;
        }

        @Override
        public Process destroyForcibly() {
            alive = false;
            return this;
        }

        @Override
        public boolean isAlive() {
            return alive;
        }
    }
}
