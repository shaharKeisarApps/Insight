package com.keisardev.insight.core.common.parcelize

actual interface Parcelable

@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.BINARY)
actual annotation class Parcelize()
