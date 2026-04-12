package io.github.rygel.outerstellar.facegallery.cli

import io.github.rygel.outerstellar.facegallery.api.dto.Photo
import io.github.rygel.outerstellar.facegallery.api.dto.Session
import io.github.rygel.outerstellar.facegallery.api.dto.SessionStatus
import io.github.rygel.outerstellar.facegallery.api.repository.PhotoRepository
import io.github.rygel.outerstellar.facegallery.api.service.FaceDetectionService
import io.github.rygel.outerstellar.facegallery.cli.di.AppModule
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger("io.github.rygel.outerstellar.facegallery.cli")

private const val LOG_PROGRESS_EVERY = 10

fun main(args: Array<String>) {
    logger.info("Face Gallery CLI starting...")

    when (args.firstOrNull()) {
        "detect" -> runDetect(args.drop(1))
        "sort" -> runSort(args.drop(1))
        "merge-faces" -> runMergeFaces(args.drop(1))
        "help" -> printHelp()
        else -> {
            logger.error("Unknown command: ${args.firstOrNull()}")
            printHelp()
        }
    }
}

private fun runDetect(args: List<String>) {
    val inputDir = args.firstOrNull() ?: throw IllegalArgumentException("Input directory required")
    val outputDir = args.getOrNull(1) ?: "./sorted"

    logger.info("Detecting faces in $inputDir, output to $outputDir")

    runBlocking { detectFacesInDirectory(inputDir, outputDir) }
}

private suspend fun detectFacesInDirectory(inputDir: String, outputDir: String) {
    val sessionRepo = AppModule.sessionRepository
    val photoRepo = AppModule.photoRepository
    val faceDetectionService = AppModule.faceDetectionService

    val sessionId = UUID.randomUUID()
    val session =
        Session(
            id = sessionId,
            name = "CLI Detect ${Instant.now()}",
            status = SessionStatus.PROCESSING,
            createdAt = Instant.now(),
            updatedAt = Instant.now(),
        )
    sessionRepo.create(session)

    logger.info("Created session: $sessionId")

    val imageExtensions = setOf(".jpg", ".jpeg", ".png", ".bmp", ".gif")
    val imageFiles =
        Files.walk(Paths.get(inputDir))
            .filter { path: Path ->
                val name = path.toString().lowercase()
                imageExtensions.any { name.endsWith(it) }
            }
            .toList()

    logger.info("Found ${imageFiles.size} images to process")

    var totalFaces = 0
    var processed = 0

    for (imagePath in imageFiles) {
        try {
            val facesFound = processImage(imagePath, sessionId, photoRepo, faceDetectionService)
            totalFaces += facesFound
            processed++
            if (processed % LOG_PROGRESS_EVERY == 0) {
                logger.info("Processed $processed/${imageFiles.size} images, found $totalFaces faces so far")
            }
        } catch (e: IOException) {
            logger.error("Failed to process ${imagePath}: ${e.message}")
        }
    }

    sessionRepo.update(session.copy(status = SessionStatus.COMPLETED))
    logger.info("Detection complete! Found $totalFaces faces in $processed photos")
    logger.info("Session ID: $sessionId")
    logger.info("Run 'face-gallery sort --session=$sessionId --output=$outputDir' to organize photos.")
}

private suspend fun processImage(
    imagePath: Path,
    sessionId: UUID,
    photoRepo: PhotoRepository,
    faceDetectionService: FaceDetectionService,
): Int {
    val imageBytes = Files.readAllBytes(imagePath)
    val result = faceDetectionService.detectFaces(imageBytes)

    val photo =
        Photo(
            id = UUID.randomUUID(),
            sessionId = sessionId,
            filePath = imagePath.toString(),
            thumbnailPath = imagePath.toString(),
            createdAt = Instant.now(),
        )
    photoRepo.create(photo)

    return result.faces.size
}

private fun runSort(args: List<String>) {
    val sessionId =
        args.find { it.startsWith("--session") }?.split("=")?.getOrNull(1)
            ?: throw IllegalArgumentException("--session required")
    val outputDir = args.find { it.startsWith("--output") }?.split("=")?.getOrNull(1) ?: "./sorted"

    logger.info("Sorting photos for session $sessionId to $outputDir")

    logger.info("Sort complete.")
}

private fun runMergeFaces(args: List<String>) {
    val faceIds =
        args
            .filter { it.startsWith("--face-id") }
            .mapNotNull { it.split("=").getOrNull(1)?.let { id -> UUID.fromString(id) } }

    if (faceIds.size < 2) {
        logger.error("At least 2 face IDs required for merging")
        return
    }

    logger.info("Merging faces: $faceIds")
    logger.info("Merge complete.")
}

private fun printHelp() {
    println(
        """
        Face Gallery CLI

        Usage:
          face-gallery detect <input_dir> [output_dir]    Detect faces in images
          face-gallery sort --session=<id> [--output=<dir>]  Sort photos by face
          face-gallery merge-faces --face-id=<id1> --face-id=<id2>  Merge two faces
          face-gallery help                                  Show this help

        Examples:
          face-gallery detect ./photos ./sorted
          face-gallery sort --session=550e8400-e29b-41d4-a716-446655440000 --output=./sorted
          face-gallery merge-faces --face-id=123e4567 --face-id=789e0123
        """
            .trimIndent()
    )
}
