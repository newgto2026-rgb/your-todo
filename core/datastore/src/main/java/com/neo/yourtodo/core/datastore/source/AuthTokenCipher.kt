package com.neo.yourtodo.core.datastore.source

interface AuthTokenCipher {
    fun encrypt(plainText: String): String

    fun decrypt(cipherText: String): String?
}
