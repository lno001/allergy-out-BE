package com.allergyout.recipe.model.service;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import org.springframework.web.multipart.MultipartFile;

import com.allergyout.s3.S3Service;

import lombok.extern.slf4j.Slf4j;

/**
 * 한 번의 등록/수정 흐름에서 올린 S3 파일을 추적하고, 실패하면 일괄 되돌린다.
 * S3는 DB 트랜잭션 밖이라 수동 보상이 필요하다. 스프링 빈 아님 — 흐름마다 new 로 생성.
 */
@Slf4j
class CompensatingUpload {

    private final S3Service s3Service;
    private final List<String> uploadedKeys = new ArrayList<>();

    CompensatingUpload(S3Service s3Service) {
        this.s3Service = s3Service;
    }

    /** 업로드하고 키를 추적 목록에 담은 뒤 접근 URL을 반환. */
    String upload(MultipartFile file, String dirName, Long id) {
        String url = s3Service.upload(file, dirName, id);
        uploadedKeys.add(extractS3Key(url));
        return url;
    }

    /** 지금까지 올린 파일을 best-effort로 삭제. 삭제 실패는 로그만 남기고 삼킨다(원래 예외를 덮지 않기 위해). */
    void rollbackQuietly() {
        for (String key : uploadedKeys) {
            try {
                s3Service.delete(key);
            } catch (RuntimeException e) {
                log.warn("S3 보상 삭제 실패 (수동 정리 필요) key={}", key, e);
            }
        }
    }

    /** S3 접근 URL에서 버킷 키(경로)만 추출. https://bucket.s3.ap-northeast-2.amazonaws.com/recipes/1/x.jpg → recipes/1/x.jpg */
    private static String extractS3Key(String url) {
        String path = URI.create(url).getPath();
        return path.startsWith("/") ? path.substring(1) : path;
    }
}
