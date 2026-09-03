package com.allergyout.s3;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.net.URI;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.multipart.MultipartFile;

import com.allergyout.global.exception.CustomException;
import com.allergyout.global.exception.ErrorCode;

import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Utilities;
import software.amazon.awssdk.services.s3.model.GetUrlRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

@ExtendWith(MockitoExtension.class)
class S3ServiceTest {

    @Mock
    private S3Client s3Client;

    @Mock
    private S3Utilities s3Utilities;

    private S3Service s3Service;

    private static final String DIR = "recipes";
    private static final Long ID = 42L;

    @BeforeEach
    void setUp() {
        s3Service = new S3Service(s3Client);
        ReflectionTestUtils.setField(s3Service, "bucket", "test-bucket");
    }

    private MockMultipartFile file(String filename) {
        return new MockMultipartFile("img", filename, "image/jpeg", new byte[] { 1, 2, 3 });
    }

    @Nested
    @DisplayName("정상 업로드")
    class Success {

        @BeforeEach
        void stub() throws Exception {
            when(s3Client.utilities()).thenReturn(s3Utilities);
            when(s3Utilities.getUrl(any(GetUrlRequest.class)))
                    .thenReturn(URI.create("https://test-bucket.s3.ap-northeast-2.amazonaws.com/x").toURL());
        }

        @Test
        @DisplayName("jpg / jpeg / png 는 통과하고 putObject 가 호출된다")
        void allowedExtensions() {
            for (String ext : new String[] { "jpg", "jpeg", "png" }) {
                s3Service.upload(file("photo." + ext), DIR, ID);
            }
            verify(s3Client, times(3)).putObject(any(PutObjectRequest.class),
                    any(software.amazon.awssdk.core.sync.RequestBody.class));
        }

        @Test
        @DisplayName("대문자 확장자(.JPG)도 통과하고, 저장 키의 확장자는 소문자(.jpg)로 생성된다")
        void uppercaseExtensionNormalizedInKey() {
            ArgumentCaptor<PutObjectRequest> cap = ArgumentCaptor.forClass(PutObjectRequest.class);

            s3Service.upload(file("PHOTO.JPG"), DIR, ID);

            verify(s3Client).putObject(cap.capture(), any(software.amazon.awssdk.core.sync.RequestBody.class));
            String key = cap.getValue().key();
            assertThat(key).startsWith("recipes/42/");
            assertThat(key).endsWith(".jpg");
            assertThat(key).doesNotContain(".JPG");
        }
    }

    @Nested
    @DisplayName("검증 실패 - 에러 코드")
    class ValidationFailure {

        @Test
        @DisplayName("파일 없음(빈 파일) → EMPTY_FILE")
        void emptyFile() {
            MultipartFile empty = new MockMultipartFile("img", "photo.jpg", "image/jpeg", new byte[0]);

            assertThatThrownBy(() -> s3Service.upload(empty, DIR, ID))
                    .isInstanceOf(CustomException.class)
                    .satisfies(e -> assertThat(((CustomException) e).getErrorCode()).isEqualTo(ErrorCode.EMPTY_FILE));
        }

        @Test
        @DisplayName("null 파일 → EMPTY_FILE")
        void nullFile() {
            assertThatThrownBy(() -> s3Service.upload(null, DIR, ID))
                    .isInstanceOf(CustomException.class)
                    .satisfies(e -> assertThat(((CustomException) e).getErrorCode()).isEqualTo(ErrorCode.EMPTY_FILE));
        }

        @Test
        @DisplayName("5MB 초과 → FILE_SIZE_EXCEEDED")
        void oversize() {
            byte[] big = new byte[5 * 1024 * 1024 + 1];
            MultipartFile file = new MockMultipartFile("img", "photo.jpg", "image/jpeg", big);

            assertThatThrownBy(() -> s3Service.upload(file, DIR, ID))
                    .isInstanceOf(CustomException.class)
                    .satisfies(e -> assertThat(((CustomException) e).getErrorCode()).isEqualTo(ErrorCode.FILE_SIZE_EXCEEDED));
        }

        @Test
        @DisplayName("미지원 확장자(.gif) → INVALID_FILE_EXTENSION")
        void unsupportedExtension() {
            assertThatThrownBy(() -> s3Service.upload(file("photo.gif"), DIR, ID))
                    .isInstanceOf(CustomException.class)
                    .satisfies(e -> assertThat(((CustomException) e).getErrorCode()).isEqualTo(ErrorCode.INVALID_FILE_EXTENSION));
        }

        @Test
        @DisplayName("확장자 없음 → INVALID_INPUT_VALUE")
        void noExtension() {
            assertThatThrownBy(() -> s3Service.upload(file("photo"), DIR, ID))
                    .isInstanceOf(CustomException.class)
                    .satisfies(e -> assertThat(((CustomException) e).getErrorCode()).isEqualTo(ErrorCode.INVALID_INPUT_VALUE));
        }
    }
}
