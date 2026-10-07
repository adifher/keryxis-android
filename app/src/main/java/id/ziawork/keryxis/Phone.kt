package id.ziawork.keryxis

object Phone {
    fun normalize(input: String): String {
        val digits = input.filter { it in '0'..'9' }
        val result = when {
            digits.startsWith("0") -> "62" + digits.drop(1)
            digits.startsWith("8") -> "62$digits"
            else -> digits
        }
        require(Regex("62[0-9]{8,13}").matches(result)) { "Nomor WhatsApp tidak valid" }
        return result
    }
}
