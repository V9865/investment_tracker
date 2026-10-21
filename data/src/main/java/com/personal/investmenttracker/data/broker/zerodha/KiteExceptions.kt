package com.personal.investmenttracker.data.broker.zerodha

import java.io.IOException

sealed class KiteApiException(
    message: String,
    cause: Throwable? = null,
) : IOException(message, cause)

class KiteSessionExpiredException(
    message: String = "Zerodha session expired. Daily re-login required.",
) : KiteApiException(message)

class KiteRateLimitException(
    message: String = "Zerodha rate limit (429) exceeded after retry backoff.",
) : KiteApiException(message)

class KiteNetworkTimeoutException(
    message: String = "Zerodha API request timed out.",
    cause: Throwable? = null,
) : KiteApiException(message, cause)

class KiteForbiddenEndpointException(
    message: String,
) : SecurityException(message)

class KiteGenericApiException(
    val httpCode: Int,
    val errorType: String?,
    override val message: String,
) : KiteApiException(message)
