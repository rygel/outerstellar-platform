package io.github.rygel.outerstellar.facegallery.services

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import io.github.rygel.outerstellar.facegallery.api.config.MlServiceConfig
import io.github.rygel.outerstellar.facegallery.api.dto.BoundingBox
import io.github.rygel.outerstellar.facegallery.api.dto.CompareResponse
import io.github.rygel.outerstellar.facegallery.api.dto.DetectResponse
import io.github.rygel.outerstellar.facegallery.api.dto.FaceDetectionResult
import io.github.rygel.outerstellar.facegallery.api.service.FaceDetectionService
import java.io.ByteArrayInputStream
import java.net.URI
import org.http4k.client.OkHttp
import org.http4k.core.Method
import org.http4k.core.Request

class Http4kFaceDetectionService(private val config: MlServiceConfig) : FaceDetectionService {
    private val client = OkHttp()
    private val objectMapper = ObjectMapper()

    override suspend fun detectFaces(imageBytes: ByteArray): DetectResponse {
        val request =
            Request(Method.POST, URI.create("${config.baseUrl}/detect").toString())
                .header("Content-Type", "image/jpeg")
                .body(ByteArrayInputStream(imageBytes), imageBytes.size.toLong())

        val httpResponse = client(request)
        val body = httpResponse.bodyString()
        return parseDetectResponse(body)
    }

    override suspend fun compareFaces(enc1: List<Double>, enc2: List<Double>): CompareResponse {
        val request =
            Request(Method.POST, URI.create("${config.baseUrl}/compare").toString())
                .header("Content-Type", "application/json")
                .body("""{"enc1":$enc1,"enc2":$enc2}""")

        val httpResponse = client(request)
        val body = httpResponse.bodyString()
        return parseCompareResponse(body)
    }

    private fun parseDetectResponse(body: String): DetectResponse {
        val json: JsonNode = objectMapper.readTree(body)
        val faces =
            json["faces"].map { faceNode ->
                val bboxArray = faceNode["bbox"]
                val bbox =
                    BoundingBox(
                        x = bboxArray[0].asInt(),
                        y = bboxArray[1].asInt(),
                        width = bboxArray[2].asInt(),
                        height = bboxArray[3].asInt(),
                    )
                FaceDetectionResult(bbox = bbox, encoding = faceNode["encoding"].map { it.asDouble() })
            }
        return DetectResponse(faces = faces)
    }

    private fun parseCompareResponse(body: String): CompareResponse {
        val json: JsonNode = objectMapper.readTree(body)
        return CompareResponse(distance = json["distance"].asDouble(), match = json["match"].asBoolean())
    }
}
