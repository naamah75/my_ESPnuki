package com.example.doorapp

object Fnv1a {
  fun responseFor(challenge: String, secret: String): String {
    val payload = "${challenge.uppercase()}:$secret"
    var hash = 0x811C9DC5.toInt()
    payload.encodeToByteArray().forEach { byte ->
      hash = hash xor (byte.toInt() and 0xFF)
      hash *= 0x01000193
    }
    return "%08X".format(hash)
  }
}
