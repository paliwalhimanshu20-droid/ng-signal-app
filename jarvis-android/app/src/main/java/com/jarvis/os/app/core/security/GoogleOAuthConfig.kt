package com.jarvis.os.app.core.security

/**
 * Sprint 13 "Production Google Workspace Authentication".
 *
 * SETUP REQUIRED (one-time, owner does this in Google Cloud Console --
 * no code can do this step; there's no API to create your own OAuth
 * client from inside your own app):
 *   1. console.cloud.google.com -> create/select a project.
 *   2. Enable the Gmail API, Google Calendar API, and Google Drive API.
 *   3. APIs & Services -> OAuth consent screen -> External, add your
 *      own Google account as a Test User (keeps it in "Testing" mode,
 *      which is fine -- no Google verification review needed for your
 *      own account to use it).
 *   4. APIs & Services -> Credentials -> Create Credentials -> OAuth
 *      client ID -> Application type "iOS". (Yes, iOS, on an Android
 *      app -- this is Google's own documented workaround for a public
 *      client with a custom URI-scheme redirect and no client secret;
 *      see developers.google.com/identity/protocols/oauth2/native-app.
 *      Android-type clients route through the Google Sign-In SDK
 *      instead, which does NOT expose a refresh token to your app --
 *      this sprint's brief explicitly requires storing one, hence
 *      AppAuth + the iOS-type client instead.) Set the Bundle ID to
 *      [REDIRECT_URI]'s scheme, i.e. "com.jarvis.os.app".
 *   5. Paste the resulting Client ID below, replacing the placeholder.
 *      It is not a secret (installed-app client IDs are public by
 *      design -- there is no client secret in this flow) so committing
 *      it to source control the same way [REDIRECT_URI] is committed
 *      is fine and matches Part 7's "no secrets in source code" rule
 *      (the actual secret -- the refresh token -- never goes here; see
 *      GoogleWorkspaceTokenStore).
 *
 * SCOPES ARE FIXED. This is "JARVIS must never silently expand OAuth
 * scopes" (Sprint 13's own Security section), enforced structurally:
 * [SCOPES] is a `val`, read by exactly one call site
 * (GoogleAuthManager.buildAuthorizationIntent), and every
 * re-authorization request below asks for this exact same set again --
 * never a superset. Requesting an additional scope (e.g. write access,
 * or a new Google product) requires a code change here, which is
 * itself the "explicit owner approval" the brief calls for -- there is
 * deliberately no runtime code path that can add a scope Ankush didn't
 * put in this file himself.
 */
object GoogleOAuthConfig {
    /** TODO(owner): replace with the real "iOS"-type OAuth Client ID from Google Cloud Console -- see this file's class docstring. */
    const val CLIENT_ID: String = "REPLACE_WITH_YOUR_GOOGLE_OAUTH_CLIENT_ID.apps.googleusercontent.com"

    /** Custom-scheme redirect AppAuth's RedirectUriReceiverActivity listens for -- must match app/build.gradle.kts's appAuthRedirectScheme placeholder exactly. */
    const val REDIRECT_URI: String = "com.jarvis.os.app:/oauth2redirect"

    const val AUTH_ENDPOINT: String = "https://accounts.google.com/o/oauth2/v2/auth"
    const val TOKEN_ENDPOINT: String = "https://oauth2.googleapis.com/token"
    const val REVOKE_ENDPOINT: String = "https://oauth2.googleapis.com/revoke"

    /** Fixed, minimal, read-only. See this object's docstring for why this list may only ever change via a code edit, never at runtime. */
    val SCOPES: List<String> = listOf(
        "https://www.googleapis.com/auth/gmail.readonly",
        "https://www.googleapis.com/auth/calendar.readonly",
        "https://www.googleapis.com/auth/drive.readonly",
        "email",
    )
}
