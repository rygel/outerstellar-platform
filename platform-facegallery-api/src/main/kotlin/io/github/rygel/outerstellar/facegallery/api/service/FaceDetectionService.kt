package io.github.rygel.outerstellar.facegallery.api.service

data class MlServiceConfig(val baseUrl: String = "http://localhost:65432")

data class DetectResponse(val faces: List<FaceDetectionResult>)

data class FaceDetectionResult(val bbox: BoundingBox, val encoding: List<Double>)

data class CompareRequest(val enc1: List<Double>, val enc2: List<Double>)

data class CompareResponse(val distance: Double, val match: Boolean)

data class BoundingBox(val x: Int, val y: Int, val width: Int, val height: Int)

interface FaceDetectionService {
    suspend fun detectFaces(imageBytes: ByteArray): DetectResponse

    suspend fun compareFaces(enc1: List<Double>, enc2: List<Double>): CompareResponse
}

interface ProcessingService {
    fun getSettings(): ProcessingSettings

    fun updateSettings(newSettings: ProcessingSettings)

    fun start(sessionId: java.util.UUID)

    fun cancel(sessionId: java.util.UUID)
}

data class ProcessingSettings(val cropPadding: Int = 45, val tolerance: Double = 0.6)
