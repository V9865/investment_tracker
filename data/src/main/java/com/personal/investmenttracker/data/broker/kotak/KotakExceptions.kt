package com.personal.investmenttracker.data.broker.kotak

import java.io.IOException

sealed class KotakApiException(
    message: String,
    cause: Throwable? = null,
) : IOException(message, cause)

class KotakSessionExpiredException(
    message: String = "Kotak Neo token expired. Update token from trade.kotakneo.com/trade-api.",
) : KotakApiException(message)

class KotakForbiddenEndpointException(
    message: String,
) : SecurityException(message)

class KotakFileImportException(
    message: String,
    cause: Throwable? = null,
) : KotakApiException(message, cause)

class KotakGenericApiException(
    val httpCode: Int,
    override val message: String,
) : KotakApiException(message)
