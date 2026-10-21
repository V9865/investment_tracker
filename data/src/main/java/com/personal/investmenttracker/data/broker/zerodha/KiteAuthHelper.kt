package com.personal.investmenttracker.data.broker.zerodha

import java.net.URI
import java.security.MessageDigest
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

object KiteAuthHelper {
    private const val LOGIN_BASE_URL = "https://kite.zerodha.com/connect/login"
    private const val KITE_VERSION = "3"
    private val IST_ZONE: ZoneId = ZoneId.of("Asia/Kolkata")
    private val SESSION_EXPIRY_TIME: LocalTime = LocalTime.of(6, 0) // 06:00 AM IST

    fun computeChecksum(
        apiKey: String,
        requestToken: String,
        apiSecret: String,
    ): String {
        val input = apiKey + requestToken + apiSecret
        val digest = MessageDigest.getInstance("SHA-256")
        val hashBytes = digest.digest(input.toByteArray(Charsets.UTF_8))
        return hashBytes.joinToString("") { "%02x".format(it) }
    }

    fun buildLoginUrl(
        apiKey: String,
        redirectParams: String? = null,
    ): String {
        val baseUrl = "$LOGIN_BASE_URL?v=$KITE_VERSION&api_key=$apiKey"
        return if (redirectParams.isNullOrBlank()) {
            baseUrl
        } else {
            "$baseUrl&redirect_params=$redirectParams"
        }
    }

    fun extractRequestToken(rawInput: String): String? {
        val trimmed = rawInput.trim()
        val token =
            when {
                trimmed.isEmpty() -> null
                isUrlOrQuery(trimmed) -> extractTokenFromUrl(trimmed)
                trimmed.contains("request_token=") -> extractTokenFromParam(trimmed)
                trimmed.matches("^[a-zA-Z0-9_\\-]{8,64}$".toRegex()) -> trimmed
                else -> null
            }
        return token
    }

    private fun isUrlOrQuery(input: String): Boolean = input.contains("://") || input.contains("?")

    private fun extractTokenFromUrl(url: String): String? {
        if (!url.contains("request_token=")) return null
        return try {
            val uri = URI(url)
            val query = uri.query ?: uri.schemeSpecificPart.substringAfter("?", "")
            query
                .split("&")
                .map { it.split("=") }
                .firstOrNull { it.size == 2 && it[0] == "request_token" }
                ?.get(1)
        } catch (_: java.net.URISyntaxException) {
            extractTokenFromParam(url)
        } catch (_: IllegalArgumentException) {
            extractTokenFromParam(url)
        }
    }

    private fun extractTokenFromParam(input: String): String? {
        val regex = "request_token=([a-zA-Z0-9_\\-]+)".toRegex()
        return regex.find(input)?.groupValues?.get(1)
    }

    fun isSessionExpired(
        sessionCreatedAtMs: Long,
        currentTimeMs: Long = System.currentTimeMillis(),
    ): Boolean {
        if (sessionCreatedAtMs <= 0 || currentTimeMs < sessionCreatedAtMs) {
            return sessionCreatedAtMs <= 0
        }

        val createdZdt = ZonedDateTime.ofInstant(Instant.ofEpochMilli(sessionCreatedAtMs), IST_ZONE)
        val currentZdt = ZonedDateTime.ofInstant(Instant.ofEpochMilli(currentTimeMs), IST_ZONE)

        val expiryZdt =
            if (createdZdt.toLocalTime().isBefore(SESSION_EXPIRY_TIME)) {
                createdZdt.with(SESSION_EXPIRY_TIME)
            } else {
                createdZdt.plusDays(1).with(SESSION_EXPIRY_TIME)
            }

        return !currentZdt.isBefore(expiryZdt)
    }
}
