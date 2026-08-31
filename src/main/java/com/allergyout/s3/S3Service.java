package com.allergyout.s3;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.allergyout.global.exception.CustomException;
import com.allergyout.global.exception.ErrorCode;

import lombok.RequiredArgsConstructor;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetUrlRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

@Service
@RequiredArgsConstructor
public class S3Service {

    private static final long MAX_FILE_SIZE = 5 * 1024 * 1024; // 5MB
    // 명세서([조리법] 등록): 이미지 형식은 PNG, JPG만 지원. (공용 변경 — 팀 승인됨)
    private static final List<String> ALLOWED_EXTENSIONS = List.of("jpg", "jpeg", "png");
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyMMdd");

    private final S3Client s3Client;

    @Value("${cloud.aws.s3.bucket}")
    private String bucket;

    /**
     * S3에 파일을 업로드하고, 접근 가능한 전체 URL을 리턴한다.
     * 키 형식: {dirName}/{id}/{uuid}_{yyMMdd}.{ext}  (예: recipes/42/3f9a1c2b_260826.jpg)
     */
    public String upload(MultipartFile file, String dirName, Long id) {
        validate(file);

        String key = generateKey(dirName, id, file.getOriginalFilename());

        try {
            s3Client.putObject(
                    PutObjectRequest.builder()
                            .bucket(bucket)
                            .key(key)
                            .contentType(file.getContentType())
                            .build(),
                    RequestBody.fromInputStream(file.getInputStream(), file.getSize())
            );
        } catch (Exception e) {
            throw new CustomException(ErrorCode.INTERNAL_SERVER_ERROR);
        }

        // baseUrl을 따로 설정값으로 안 두고, SDK가 버킷+리전 정보로 URL을 직접 만들어줌
        return s3Client.utilities()
                .getUrl(GetUrlRequest.builder().bucket(bucket).key(key).build())
                .toString();

        // --- 참고: baseUrl을 application.yml에 별도로 두는 방식(아래) ---
        // DB에는 key(recipes/xxx.jpg)만 저장하고, 응답 내려줄 때 baseUrl + key로 조립하는 방식.
        // 버킷/CDN 도메인이 바뀌어도 DB 데이터를 안 건드려도 된다는 장점이 있어서,
        // 나중에 필요해지면 이 방식으로 바꿔도 됨.
        //
        // @Value("${cloud.aws.s3.base-url}")
        // private String baseUrl;
        //
        // return baseUrl + "/" + key;
    }

    public void delete(String key) {
        s3Client.deleteObject(DeleteObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .build());
    }

    private void validate(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);
        }
        String extension = extractExtension(file.getOriginalFilename());
        if (!ALLOWED_EXTENSIONS.contains(extension.toLowerCase())) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);
        }
    }

    private String extractExtension(String originalFilename) {
        if (originalFilename == null || !originalFilename.contains(".")) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);
        }
        return originalFilename.substring(originalFilename.lastIndexOf('.') + 1);
    }

    // 원본 파일명은 버리고, S3에 저장할 새 키(경로+이름)를 새로 만듦: {dirName}/{id}/{uuid}_{yyMMdd}.{ext}
    private String generateKey(String dirName, Long id, String originalFilename) {
        String extension = extractExtension(originalFilename);
        String newFileName = UUID.randomUUID() + "_" + LocalDate.now().format(DATE_FORMAT) + "." + extension;
        return dirName + "/" + id + "/" + newFileName;
    }
}
