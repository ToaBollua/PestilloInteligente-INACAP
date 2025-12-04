package com.borcele.pestillointeligente

import java.util.Date

data class LogEntry(
    val accion: String? = null,
    val usuario: String? = null,
    val fecha: Date? = null
) {
    // A no-argument constructor is required for Firestore's toObjects() method.
    constructor() : this(null, null, null)
}