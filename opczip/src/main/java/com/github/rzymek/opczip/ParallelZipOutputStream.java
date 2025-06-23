package com.github.rzymek.opczip;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.zip.CRC32;
import java.util.zip.CheckedOutputStream;

public class ParallelZipOutputStream extends OutputStream {

    private final Path tempFilePath;
    private final OutputStream chainedStream;
    private final CRC32 crc = new CRC32();
    private final String entryName;

    private long originalSize = 0L;
    private boolean closed = false;

    /**
     * @param entryName 최종 Zip 파일에 사용될 엔트리 이름
     * @throws IOException 임시 파일 생성에 실패할 경우
     */
    public ParallelZipOutputStream(String entryName) throws IOException {
        this.entryName = Objects.requireNonNull(entryName, "entryName must not be null");
        // 1. 시스템의 기본 임시 디렉토리에 고유한 이름의 임시 파일을 생성합니다.
        this.tempFilePath = Files.createTempFile("workbook-part-", ".tmp");

        // 2. 스트림 체인 설정: File -> CRC 계산 -> 압축 순으로 데이터가 흐릅니다.
        // FileOutputStream: 실제 파일에 바이트를 씁니다.
        // CheckedOutputStream: 지나가는 데이터의 CRC32 값을 계산합니다.
        // DeflaterOutputStream: 데이터를 DEFLATE 알고리즘으로 압축합니다.
        this.chainedStream = new OpcOutputStream(
                new CheckedOutputStream(Files.newOutputStream(tempFilePath.toFile().toPath()), crc)
        );
    }

    @Override
    public void write(int b) throws IOException {
        chainedStream.write(b);
        originalSize++;
    }

    @Override
    public void write(byte[] b, int off, int len) throws IOException {
        chainedStream.write(b, off, len);
        originalSize += len;
    }

    @Override
    public void close() throws IOException {
        if (closed) {
            return;
        }
        // 체인의 가장 바깥쪽 스트림을 닫으면 내부 스트림들이 순차적으로 닫힙니다.
        chainedStream.close();
        closed = true;
    }

    /**
     * 스트림이 닫힌 후에만 호출해야 합니다.
     * 압축 작업의 최종 결과물인 FileBasedZipEntry 객체를 반환합니다.
     * @return 압축 결과 정보를 담은 DTO
     * @throws IllegalStateException 스트림이 아직 닫히지 않았을 때 호출하면 발생
     */
    public ParallelZipEntry getResult() {
        if (!closed) {
            throw new IllegalStateException("Stream must be closed before getting the result.");
        }
        return new ParallelZipEntry(entryName, tempFilePath, crc.getValue(), originalSize);
    }
}
