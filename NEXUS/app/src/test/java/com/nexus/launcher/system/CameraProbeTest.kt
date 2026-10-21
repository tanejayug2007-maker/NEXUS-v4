package com.nexus.launcher.system

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CameraProbeTest {
    private fun lens(id: String, facing: String, logical: Boolean = false, ext: List<String> = emptyList()) =
        CameraProbe.Lens(id, facing, listOf(5.0f), logical, if (logical) listOf("2", "3") else emptyList(), ext)

    @Test fun extensionNames() {
        assertEquals("NIGHT", CameraProbe.extensionName(4))
        assertEquals("HDR", CameraProbe.extensionName(3))
        assertEquals("BOKEH", CameraProbe.extensionName(2))
        assertEquals("EXT_9", CameraProbe.extensionName(9))
    }

    @Test fun verdictWithoutExtensionsSaysPlainFrames() {
        val v = CameraProbe.verdict(listOf(lens("0", "BACK"), lens("1", "FRONT")))
        assertTrue(v, v.contains("one back camera only"))
        assertTrue(v, v.contains("No Camera2 vendor extensions"))
    }

    @Test fun verdictWithLogicalCameraAndExtensions() {
        val v = CameraProbe.verdict(listOf(lens("0", "BACK", logical = true, ext = listOf("NIGHT", "HDR")), lens("1", "FRONT")))
        assertTrue(v, v.contains("logical multi-camera"))
        assertTrue(v, v.contains("HDR, NIGHT"))
    }

    @Test fun verdictWithNoBackCamera() {
        assertTrue(CameraProbe.verdict(emptyList()).startsWith("No back camera"))
    }
}
