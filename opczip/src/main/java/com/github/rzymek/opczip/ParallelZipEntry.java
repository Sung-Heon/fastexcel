package com.github.rzymek.opczip;

import java.nio.file.Path;
import java.util.Objects;

public class ParallelZipEntry {

    private final String entryName;       // ZIP 파일 내의 전체 경로 (예: /xl/worksheets/sheet1.xml)
    private final Path tempFilePath;      // 압축된 데이터가 저장된 디스크 상의 임시 파일 경로
    private final long crc;               // 압축된 데이터의 CRC-32 체크섬 값
    private final long originalSize;      // 원본 데이터(압축 전)의 크기 (바이트 단위)

    /**
     * FileBasedZipEntry의 새 인스턴스를 생성합니다.
     *
     * @param entryName    ZIP 엔트리 이름 (null 허용 안 함)
     * @param tempFilePath 임시 파일 경로 (null 허용 안 함)
     * @param crc          CRC-32 값
     * @param originalSize 원본 크기
     */
    public ParallelZipEntry(String entryName, Path tempFilePath, long crc, long originalSize) {
        // null 체크를 통해 객체의 안정성을 보장합니다. (Fail-fast)
        this.entryName = Objects.requireNonNull(entryName, "entryName must not be null");
        this.tempFilePath = Objects.requireNonNull(tempFilePath, "tempFilePath must not be null");
        this.crc = crc;
        this.originalSize = originalSize;
    }

    // 각 필드에 대한 Getter 메소드들
    public String getEntryName() {
        return entryName;
    }

    public Path getTempFilePath() {
        return tempFilePath;
    }

    public long getCrc() {
        return crc;
    }

    public long getOriginalSize() {
        return originalSize;
    }

    /**
     * 디버깅 및 로깅 시 객체의 상태를 쉽게 확인할 수 있도록 유용한 문자열을 반환합니다.
     */
    @Override
    public String toString() {
        return "ParallelZipEntry{" +
                "entryName='" + entryName + '\'' +
                ", tempFilePath=" + tempFilePath +
                ", crc=" + crc +
                ", originalSize=" + originalSize +
                '}';
    }
}
