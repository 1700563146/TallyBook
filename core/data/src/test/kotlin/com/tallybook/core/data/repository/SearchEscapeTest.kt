package com.tallybook.core.data.repository

import org.junit.Assert.assertEquals
import org.junit.Test

class SearchEscapeTest {

    @Test
    fun `LIKE wildcards and escape characters are searched literally`() {
        assertEquals("100\\%\\_done\\\\", escapeLikePattern("100%_done\\"))
    }

    @Test
    fun `plain Chinese text is not changed`() {
        assertEquals("午饭 拉面", escapeLikePattern("午饭 拉面"))
    }
}
