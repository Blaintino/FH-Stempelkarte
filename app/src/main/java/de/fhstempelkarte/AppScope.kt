package de.fhstempelkarte

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/** Für Arbeit, die eine Activity überdauern soll (Hintergrundbild setzen nach finish()). */
object AppScope : CoroutineScope by CoroutineScope(SupervisorJob() + Dispatchers.IO)
