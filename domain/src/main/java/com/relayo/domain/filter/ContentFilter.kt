package com.relayo.domain.filter

interface ContentFilter {
    fun isAllowed(text:String):Boolean
    fun findViolation(text:String):String?
    fun sanitize(text:String):String
}
