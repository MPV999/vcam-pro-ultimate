package com.vcampro.ultimate

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Kiểm thử cam kết OFFLINE của ứng dụng.
 *
 * Đây là bài kiểm thử quan trọng nhất của dự án: nếu ứng dụng khai báo quyền
 * INTERNET, hệ điều hành sẽ cho phép nó liên lạc ra ngoài — điều này phá vỡ
 * cam kết "local-first, zero VPS".
 */
@RunWith(AndroidJUnit4::class)
class OfflineGuaranteeTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun manifest_doesNotRequestInternetPermission() {
        val info = context.packageManager.getPackageInfo(
            context.packageName,
            PackageManager.GET_PERMISSIONS
        )
        val requested = info.requestedPermissions?.toList() ?: emptyList()
        assertFalse(
            "Ứng dụng KHÔNG được khai báo quyền INTERNET",
            requested.contains(Manifest.permission.INTERNET)
        )
        assertFalse(
            "Ứng dụng KHÔNG được khai báo quyền ACCESS_NETWORK_STATE",
            requested.contains(Manifest.permission.ACCESS_NETWORK_STATE)
        )
    }

    @Test
    fun manifest_requestsCameraPermission() {
        val info = context.packageManager.getPackageInfo(
            context.packageName,
            PackageManager.GET_PERMISSIONS
        )
        val requested = info.requestedPermissions?.toList() ?: emptyList()
        assertTrue(
            "Camera Studio cần quyền CAMERA",
            requested.contains(Manifest.permission.CAMERA)
        )
    }

    @Test
    fun packageName_isVcamPro() {
        assertTrue(context.packageName.startsWith("com.vcampro.ultimate"))
    }
}
