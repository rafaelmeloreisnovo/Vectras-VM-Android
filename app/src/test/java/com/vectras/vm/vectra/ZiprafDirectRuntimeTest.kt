package com.vectras.vm.vectra

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ZiprafDirectRuntimeTest {
    @Test
    fun mappedStore_usesThreeStages_andEightLanes() {
        val file = File.createTempFile("zipraf-direct", ".bin")
        try {
            file.writeBytes(ByteArray(512) { it.toByte() })
            val plan = ZiprafRuntimePlan(64, 16, 128, 8)
            ZiprafDirectRuntime(file, ZiprafStoredExtent(32, 256), plan).use { runtime ->
                val l1 = runtime.window(31, ZiprafMemoryStage.L1_HOT, 17)
                val l2 = runtime.window(240, ZiprafMemoryStage.L2_SHARED, 17)
                assertEquals(16, l1.length)
                assertEquals(1, l1.coreLane)
                assertEquals(63, l1.bytes.get(0).toInt() and 0xff)
                assertEquals(16, l2.length)
                assertEquals(1, l2.coreLane)
            }
        } finally { file.delete() }
    }

    @Test(expected = IllegalArgumentException::class)
    fun nonStoreMethod_isRejected() {
        val file = File.createTempFile("zipraf-deflate", ".bin")
        try {
            file.writeBytes(ByteArray(64))
            ZiprafDirectRuntime(file, ZiprafStoredExtent(0, 64, 8)).close()
        } finally { file.delete() }
    }

    @Test
    fun fixedBits_neverMove() {
        val mask = 0xF00000000000000FuL.toLong()
        val fixed = 0xA000000000000005uL.toLong()
        val result = ZiprafDirectRuntime.preserveFixedBits(-1L, mask, fixed)
        assertTrue((result and mask) == (fixed and mask))
    }
}
