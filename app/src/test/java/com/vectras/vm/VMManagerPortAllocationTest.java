package com.vectras.vm;

import org.junit.Assert;
import org.junit.Test;

import java.net.ServerSocket;
import java.util.HashSet;
import java.util.Set;

public class VMManagerPortAllocationTest {

    @Test
    public void startRandomPort_returnsAvailableEphemeralPortWithoutRandomGuessFallback() throws Exception {
        int port = VMManager.startRandomPort();

        Assert.assertTrue("allocated port should be positive", port > 0);
        try (ServerSocket probe = new ServerSocket(port)) {
            Assert.assertEquals(port, probe.getLocalPort());
        }
    }

    @Test
    public void allocateAvailablePort_skipsReservedEphemeralCandidate() throws Exception {
        int reserved;
        try (ServerSocket socket = new ServerSocket(0)) {
            reserved = socket.getLocalPort();
        }
        Set<Integer> reservedPorts = new HashSet<>();
        reservedPorts.add(reserved);

        int allocated = VMManager.allocateAvailablePort(reservedPorts);

        Assert.assertNotEquals(reserved, allocated);
        try (ServerSocket probe = new ServerSocket(allocated)) {
            Assert.assertEquals(allocated, probe.getLocalPort());
        }
    }
}
