package com.allergyout;

import javax.sql.DataSource;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

// 애플리케이션 컨텍스트가 정상적으로 뜨는지만 확인하는 스모크 테스트.
// 외부 의존(Oracle·S3)은 CI/로컬에 자격증명이 없으므로:
//  - application.yml 의 ${} 플레이스홀더는 더미 값으로 채운다 (형식만 맞으면 됨.
//    AES_KEY/HMAC_KEY 는 AesUtil/HmacUtil 이 32-byte base64 키를 요구 → 32 zero-bytes)
//  - DataSource 는 목으로 대체해 실제 Oracle(운영 IP) 연결 시도를 막는다.
// ※ RecipeApiServerTest 와 같은 패턴. 세 번째 @SpringBootTest 가 생기면 이 설정을
//   src/test/resources/application.yml 로 빼서 중복을 없애는 게 낫다.
@SpringBootTest
@TestPropertySource(properties = {
		"DB_USERNAME=test", "DB_PASSWORD=test",
		"S3ACESSKEY=test", "S3SECRETKEY=test",
		"AES_KEY=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
		"HMAC_KEY=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA="
})
class AllergyOutApplicationTests {

	@MockitoBean DataSource dataSource;   // 실제 Oracle 연결 회피 (자격증명 없음)

	@Test
	void contextLoads() {
	}

}
