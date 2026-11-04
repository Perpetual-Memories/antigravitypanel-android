package com.nzd.antigravitypanel.data.yyb

import android.util.Base64
import com.nzd.antigravitypanel.yyb.mobile.Mobile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive

private class GoYybLoginEngine : YybLoginEngine {
    private val client = Mobile.newClient()
    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun createQr(): YybQrSession = withContext(Dispatchers.IO) {
        val root = json.parseToJsonElement(client.createQR())
        val obj = root as kotlinx.serialization.json.JsonObject
        YybQrSession(
            id = obj.getValue("session_id").jsonPrimitive.content,
            imageBytes = Base64.decode(
                obj.getValue("image_base64").jsonPrimitive.content,
                Base64.DEFAULT,
            ),
        )
    }

    override suspend fun poll(sessionId: String): YybPollResult = withContext(Dispatchers.IO) {
        val root = json.parseToJsonElement(client.poll(sessionId)) as kotlinx.serialization.json.JsonObject
        YybPollResult(
            status = root.getValue("status").jsonPrimitive.content,
            errCode = root["errcode"]?.jsonPrimitive?.intOrNull,
        )
    }

    override suspend fun confirmAndGetCode(sessionId: String, appId: String): String =
        withContext(Dispatchers.IO) { client.confirmAndGetCode(sessionId, appId) }

    override fun cancel(sessionId: String) {
        client.cancel(sessionId)
    }

    override fun close() {
        client.close()
    }
}

actual fun createYybLoginEngine(): YybLoginEngine = GoYybLoginEngine()
