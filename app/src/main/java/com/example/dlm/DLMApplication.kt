// com.example.dlm/DLMApplication.kt
package com.example.dlm

import android.app.Application
import com.amazonaws.regions.Regions
import com.example.dlm.s3.S3Manager

class DLMApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        // **IMPORTANTE**: Reemplaza estos valores con tus credenciales reales
        val accessKey = "AKIAS65LXJC4QPUT5ZKS"
        val secretKey = "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"
        val region = Regions.US_EAST_2

        S3Manager.initialize(accessKey, secretKey, region)
    }
}