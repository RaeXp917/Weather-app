package com.example.myweatherapp

sealed class ErrorState {
    class ApiKeyConfigError(val message: String = "API Key Configuration Error.") : ErrorState()
    class NetworkError(val message: String = "Network error. Please check your connection.") : ErrorState()
    class ApiError(val code: Int?, val apiMessage: String?) : ErrorState() {
        val displayMessage: String
            get() = "API Error (Code: ${code ?: "N/A"}): ${apiMessage ?: "An unknown API error occurred."}"
    }
    class ClientRequestError(val statusCode: Int?, val clientMessage: String?) : ErrorState() {
        val displayMessage: String
            get() = "Client Error (Status: ${statusCode ?: "N/A"}): ${clientMessage ?: "Could not process request."}"
    }
    class GenericError(val customMessage: String?) : ErrorState() {
        val displayMessage: String
            get() = customMessage ?: "An unexpected error occurred."
    }
    object NoError : ErrorState()
}