package com.nexus.launcher.system

import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraExtensionCharacteristics
import android.hardware.camera2.CameraManager
import android.os.Build

/**
 * READ-ONLY probe of what the camera stack exposes to third-party apps. No camera permission, no
 * camera is opened. It answers the key question for a futuristic NEXUS camera front end: can a normal
 * app reach the vendor's processing (Night / HDR / Bokeh via Camera2 extensions) and every lens, or
 * would it be stuck with plain frames? The Nothing Camera app itself is never touched.
 */
object CameraProbe {

    data class Lens(
        val id: String,
        val facing: String,
        val focalLengthsMm: List<Float>,
        val logicalMulti: Boolean,
        val physicalIds: List<String>,
        val extensions: List<String>,
    )

    /** Camera2 extension constants (CameraExtensionCharacteristics.EXTENSION_*). */
    fun extensionName(e: Int): String = when (e) {
        0 -> "AUTOMATIC"
        1 -> "FACE_RETOUCH"
        2 -> "BOKEH"
        3 -> "HDR"
        4 -> "NIGHT"
        else -> "EXT_$e"
    }

    fun facingName(f: Int?): String = when (f) {
        CameraCharacteristics.LENS_FACING_BACK -> "BACK"
        CameraCharacteristics.LENS_FACING_FRONT -> "FRONT"
        CameraCharacteristics.LENS_FACING_EXTERNAL -> "EXTERNAL"
        else -> "UNKNOWN"
    }

    fun probe(c: Context): List<Lens> {
        val cm = c.getSystemService(CameraManager::class.java) ?: return emptyList()
        val ids = runCatching { cm.cameraIdList.toList() }.getOrDefault(emptyList())
        return ids.map { id ->
            val ch = runCatching { cm.getCameraCharacteristics(id) }.getOrNull()
            val caps = ch?.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES)?.toList().orEmpty()
            val logical = CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_LOGICAL_MULTI_CAMERA in caps
            val ext = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                runCatching {
                    cm.getCameraExtensionCharacteristics(id).supportedExtensions.map { extensionName(it) }
                }.getOrDefault(emptyList())
            } else emptyList()
            Lens(
                id = id,
                facing = facingName(ch?.get(CameraCharacteristics.LENS_FACING)),
                focalLengthsMm = ch?.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)?.toList().orEmpty(),
                logicalMulti = logical,
                physicalIds = if (logical) ch?.physicalCameraIds?.toList().orEmpty() else emptyList(),
                extensions = ext,
            )
        }
    }

    /** One-paragraph verdict for the UI. */
    fun verdict(lenses: List<Lens>): String {
        val back = lenses.filter { it.facing == "BACK" }
        val ext = lenses.flatMap { it.extensions }.toSet()
        val lensNote = when {
            back.isEmpty() -> "No back camera is exposed to apps."
            back.size == 1 && !back[0].logicalMulti -> "Apps see one back camera only — extra lenses are reserved for the system camera."
            else -> "Apps can reach ${back.size} back camera id(s)" + (if (back.any { it.logicalMulti }) " incl. a logical multi-camera (zoom across lenses)." else ".")
        }
        val extNote = if (ext.isEmpty()) "No Camera2 vendor extensions: a third-party camera would get plain frames, without the vendor's Night/HDR/Bokeh processing."
        else "Vendor extensions available to apps: ${ext.sorted().joinToString()} — a NEXUS camera could request this processing."
        return "$lensNote $extNote"
    }
}
