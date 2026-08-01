package com.pointquest.android

import com.pointquest.android.generated.api.DefaultApi
import com.pointquest.android.generated.model.AuthRefresh201Response
import com.pointquest.android.generated.model.PracticeSummaryDto
import com.pointquest.android.generated.model.TokenResponseDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class GeneratedContractSmokeTest {
    @Test
    fun generatedStudentContractIsOnClasspath() {
        assertEquals("DefaultApi", DefaultApi::class.java.simpleName)
        assertNotNull(TokenResponseDto::class.java)
        assertNotNull(AuthRefresh201Response::class.java)
        assertNotNull(PracticeSummaryDto::class.java)
    }
}
