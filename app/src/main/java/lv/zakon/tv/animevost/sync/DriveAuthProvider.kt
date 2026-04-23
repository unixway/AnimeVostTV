package lv.zakon.tv.animevost.sync

import android.accounts.AccountManager
import android.content.Context
import com.google.android.gms.auth.GoogleAuthException
import com.google.android.gms.auth.GoogleAuthUtil
import com.google.android.gms.auth.UserRecoverableAuthException
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException

/**
 * DriveAuthProvider obtains OAuth access tokens for Google Drive API using
 * the system AccountManager and GoogleAuthUtil. No tokens are cached or stored.
 */
class DriveAuthProvider(private val context: Context) {
    companion object {
        const val DRIVE_APPDATA_SCOPE = "oauth2:https://www.googleapis.com/auth/drive.appdata"
    }

    /**
     * Obtains a fresh OAuth access token for the given scope.
     *
     * This method performs blocking I/O (GoogleAuthUtil.getToken) and must be called
     * from a coroutine. The blocking call is dispatched to Dispatchers.IO.
     *
     * @param scope OAuth scope string (e.g. DRIVE_APPDATA_SCOPE)
     * @return Ok(token) on success, Err(AuthError) on failure
     *
     * Errors:
     * - AuthError.NoAccount: no Google accounts found on device
     * - AuthError.TokenException: token retrieval failed (UserRecoverableAuthException,
     *   GoogleAuthException, IOException)
     */
    suspend fun getAccessToken(scope: String): Result<String, AuthError> = withContext(Dispatchers.IO) {
        try {
            val accountManager = AccountManager.get(context)
            val accounts = accountManager.getAccountsByType("com.google")

            if (accounts.isEmpty()) {
                return@withContext Err(AuthError.NoAccount)
            }

            // ADR-002: multiple accounts -> take first
            val account = accounts[0]
            val token = GoogleAuthUtil.getToken(context, account, scope)

            Ok(token)
        } catch (e: UserRecoverableAuthException) {
            Err(AuthError.TokenException(e.message ?: ""))
        } catch (e: GoogleAuthException) {
            Err(AuthError.TokenException(e.message ?: ""))
        } catch (e: IOException) {
            Err(AuthError.TokenException(e.message ?: ""))
        }
    }
}
