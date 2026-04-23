package lv.zakon.tv.animevost.sync

import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.IOException

/**
 * DriveFileRepository performs all Google Drive REST API v3 operations
 * for device sync files stored in appDataFolder.
 *
 * Uses Ktor CIO HttpClient (injected) and DriveAuthProvider for OAuth tokens.
 * All HTTP errors are mapped to DriveError variants — no exceptions thrown.
 */
class DriveFileRepository(
    private val authProvider: DriveAuthProvider,
    private val httpClient: HttpClient
) {
    private val json = Json { ignoreUnknownKeys = true }

    @Serializable
    private data class FilesListResponse(
        val files: List<DriveFileJson>
    )

    @Serializable
    private data class DriveFileJson(
        val id: String,
        val name: String
    )

    @Serializable
    private data class FileMetadata(
        val name: String,
        val parents: List<String>
    )

    /**
     * Lists all device sync files in appDataFolder matching "animevost_sync_" prefix.
     *
     * @return Ok(list of DriveFile) on success, Err(DriveError) on failure
     *
     * Errors:
     * - DriveError.Unauthorized: HTTP 401
     * - DriveError.ApiError(code): HTTP 403 or other non-2xx
     * - DriveError.NetworkError: IOException or auth failure
     */
    suspend fun listDeviceFiles(): Result<List<DriveFile>, DriveError> {
        val tokenResult = getTokenOrError()
        if (tokenResult is Err) {
            return tokenResult
        }
        val token = (tokenResult as Ok).value

        return try {
            val response = httpClient.get("https://www.googleapis.com/drive/v3/files") {
                header("Authorization", "Bearer $token")
                parameter("spaces", "appDataFolder")
                parameter("q", "name contains 'animevost_sync_'")
                parameter("fields", "files(id,name)")
            }

            when (response.status) {
                HttpStatusCode.OK -> {
                    val body = response.bodyAsText()
                    val parsed = json.decodeFromString<FilesListResponse>(body)
                    val driveFiles = parsed.files.map { DriveFile(id = it.id, name = it.name) }
                    Ok(driveFiles)
                }
                HttpStatusCode.Unauthorized -> Err(DriveError.Unauthorized)
                else -> Err(DriveError.ApiError(response.status.value))
            }
        } catch (e: IOException) {
            Err(DriveError.NetworkError(e.message ?: ""))
        } catch (e: Exception) {
            // Ktor client exceptions, JSON parsing errors
            Err(DriveError.NetworkError(e.message ?: ""))
        }
    }

    /**
     * Downloads the content of a Drive file by fileId.
     *
     * @param fileId Google Drive file ID
     * @return Ok(file content as String) on success, Err(DriveError) on failure
     *
     * Errors:
     * - DriveError.Unauthorized: HTTP 401
     * - DriveError.NotFound: HTTP 404
     * - DriveError.ApiError(code): other non-2xx
     * - DriveError.NetworkError: IOException or auth failure
     */
    suspend fun downloadFile(fileId: String): Result<String, DriveError> {
        val tokenResult = getTokenOrError()
        if (tokenResult is Err) {
            return tokenResult
        }
        val token = (tokenResult as Ok).value

        return try {
            val response = httpClient.get("https://www.googleapis.com/drive/v3/files/$fileId") {
                header("Authorization", "Bearer $token")
                parameter("alt", "media")
            }

            when (response.status) {
                HttpStatusCode.OK -> {
                    val content = response.bodyAsText()
                    Ok(content)
                }
                HttpStatusCode.Unauthorized -> Err(DriveError.Unauthorized)
                HttpStatusCode.NotFound -> Err(DriveError.NotFound)
                else -> Err(DriveError.ApiError(response.status.value))
            }
        } catch (e: IOException) {
            Err(DriveError.NetworkError(e.message ?: ""))
        } catch (e: Exception) {
            Err(DriveError.NetworkError(e.message ?: ""))
        }
    }

    /**
     * Uploads a new file or updates an existing file in appDataFolder.
     *
     * If a file with the same fileName already exists (found via listDeviceFiles),
     * performs PATCH to update content. Otherwise, performs multipart POST to create
     * a new file with parents=["appDataFolder"].
     *
     * @param fileName Name of the file (e.g. "animevost_sync_device123.json")
     * @param jsonContent JSON string content to upload
     * @return Ok(Unit) on success, Err(DriveError) on failure
     *
     * Errors:
     * - DriveError.Unauthorized: HTTP 401
     * - DriveError.ApiError(code): other non-2xx
     * - DriveError.NetworkError: IOException or auth failure
     *
     * Edge case: if listDeviceFiles fails, treats as no existing file and performs POST.
     */
    suspend fun uploadOrUpdateFile(fileName: String, jsonContent: String): Result<Unit, DriveError> {
        val tokenResult = getTokenOrError()
        if (tokenResult is Err) {
            return tokenResult
        }
        val token = (tokenResult as Ok).value

        // Find existing file with same name
        val listResult = listDeviceFiles()
        val existingFile = if (listResult is Ok) {
            listResult.value.find { it.name == fileName }
        } else {
            // listDeviceFiles failed -> fallback to POST as new file
            null
        }

        return try {
            if (existingFile != null) {
                // Update existing file via PATCH
                val response = httpClient.patch("https://www.googleapis.com/upload/drive/v3/files/${existingFile.id}") {
                    header("Authorization", "Bearer $token")
                    parameter("uploadType", "media")
                    contentType(ContentType.Application.Json)
                    setBody(jsonContent)
                }

                when (response.status) {
                    HttpStatusCode.OK -> Ok(Unit)
                    HttpStatusCode.Unauthorized -> Err(DriveError.Unauthorized)
                    else -> Err(DriveError.ApiError(response.status.value))
                }
            } else {
                // Create new file via multipart POST
                val boundary = "==AnimeVostBoundary=="
                val metadata = FileMetadata(name = fileName, parents = listOf("appDataFolder"))
                val metadataJson = json.encodeToString(FileMetadata.serializer(), metadata)

                val multipartBody = buildString {
                    appendLine("--$boundary")
                    appendLine("Content-Type: application/json; charset=UTF-8")
                    appendLine()
                    appendLine(metadataJson)
                    appendLine("--$boundary")
                    appendLine("Content-Type: application/json")
                    appendLine()
                    appendLine(jsonContent)
                    appendLine("--$boundary--")
                }

                val response = httpClient.post("https://www.googleapis.com/upload/drive/v3/files") {
                    header("Authorization", "Bearer $token")
                    parameter("uploadType", "multipart")
                    contentType(ContentType.parse("multipart/related; boundary=\"$boundary\""))
                    setBody(multipartBody)
                }

                when (response.status) {
                    HttpStatusCode.OK, HttpStatusCode.Created -> Ok(Unit)
                    HttpStatusCode.Unauthorized -> Err(DriveError.Unauthorized)
                    else -> Err(DriveError.ApiError(response.status.value))
                }
            }
        } catch (e: IOException) {
            Err(DriveError.NetworkError(e.message ?: ""))
        } catch (e: Exception) {
            Err(DriveError.NetworkError(e.message ?: ""))
        }
    }

    /**
     * Helper: obtains access token or returns DriveError.
     *
     * Maps AuthError to DriveError.NetworkError as specified in detail design.
     */
    private suspend fun getTokenOrError(): Result<String, DriveError> {
        val tokenResult = authProvider.getAccessToken(DriveAuthProvider.DRIVE_APPDATA_SCOPE)
        return when (tokenResult) {
            is Ok -> Ok(tokenResult.value)
            is Err -> Err(DriveError.NetworkError("Auth failed: ${tokenResult.error}"))
        }
    }
}
