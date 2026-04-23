package org.ecommerce

import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles

@SpringBootTest
@ActiveProfiles("test")
@DisplayName("CommerceApplication: 스프링 애플리케이션 컨텍스트")
class CommerceApplicationTest {

    @Test
    @DisplayName("test 프로파일로 애플리케이션 컨텍스트가 정상 로드된다")
    fun contextLoads_testProfile_applicationContextLoaded() {
    }
}
