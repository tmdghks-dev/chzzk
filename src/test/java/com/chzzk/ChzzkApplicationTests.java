package com.chzzk;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

// 테스트는 실제 DB 파일(./data/chzzk) 대신 메모리 DB를 쓴다
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:chzzk-test")
class ChzzkApplicationTests {

    @Test
    void contextLoads() {
    }

}
