package com.github.rzymek.opczip;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.CRC32;
import java.util.zip.Deflater;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * ZIP 파일에 직접 새로운 항목(entry)을 추가하는 기능을 제공하는 클래스.
 * 기존 ZIP 파일의 EOCD(End of Central Directory)를 수정하여 새로운 항목을 추가합니다.
 */
public class ZipAppender implements Closeable {
    // ZIP 구조 관련 상수
    private static final int EOCD_SIGNATURE = 0x06054B50;
    private static final int CENTRAL_DIRECTORY_SIGNATURE = 0x02014B50;
    private static final int LOCAL_FILE_HEADER_SIGNATURE = 0x04034B50;
    private static final int DATA_DESCRIPTOR_SIGNATURE = 0x08074B50;

    private final Path zipPath;
    private final RandomAccessFile raf;
    private final FileChannel channel;
    private final List<EntryInfo> addedEntries = new ArrayList<>();

    // 기존 EOCD 정보
    private long eocdOffset;
    private int diskNumber;
    private int startDiskNumber;
    private int numEntriesOnDisk;
    private int totalEntries;
    private long cdSize;
    private long cdOffset;
    private int commentLength;
    private byte[] comment;

    /**
     * 기존 ZIP 파일을 열고 EOCD를 파싱합니다.
     *
     * @param zipPath 기존 ZIP 파일의 경로
     * @throws IOException I/O 오류 발생시
     */
    public ZipAppender(Path zipPath) throws IOException {
        this.zipPath = zipPath;

        // 파일이 존재하지 않으면 빈 ZIP 파일 생성
        if (!Files.exists(zipPath)) {
            try (FileOutputStream fos = new FileOutputStream(zipPath.toFile());
                 ZipOutputStream zos = new ZipOutputStream(fos)) {
                zos.finish();
            }
        }

        this.raf = new RandomAccessFile(zipPath.toFile(), "rw");
        this.channel = raf.getChannel();

        // EOCD 파싱
        parseEocd();
    }

    /**
     * ZIP 파일의 EOCD를 찾아 파싱합니다.
     *
     * @throws IOException I/O 오류 발생시
     */
    private void parseEocd() throws IOException {
        long fileLength = raf.length();
        if (fileLength < 22) {
            // 최소 EOCD 크기보다 작으면 빈 ZIP으로 초기화
            initializeEmptyZip();
            return;
        }

        // EOCD 검색을 위한 버퍼 크기 계산 (주석 최대 길이 + EOCD 크기)
        int bufferSize = (int) Math.min(fileLength, 65535 + 22);
        byte[] buffer = new byte[bufferSize];

        // 파일 끝에서부터 읽기
        raf.seek(fileLength - bufferSize);
        raf.readFully(buffer);

        // EOCD 시그니처 검색
        int eocdOffsetInBuffer = -1;
        for (int i = bufferSize - 4; i >= 0; i--) {
            if (buffer[i] == 0x50 && buffer[i + 1] == 0x4B &&
                buffer[i + 2] == 0x05 && buffer[i + 3] == 0x06) {
                int commentLen = (buffer[i + 20] & 0xFF) | ((buffer[i + 21] & 0xFF) << 8);
                // 주석 길이가 파일 끝까지의 거리와 일치하는지 확인
                if (fileLength - (fileLength - bufferSize + i + 22 + commentLen) == 0) {
                    eocdOffsetInBuffer = i;
                    break;
                }
            }
        }

        if (eocdOffsetInBuffer == -1) {
            // EOCD를 찾을 수 없으면 빈 ZIP으로 초기화
            initializeEmptyZip();
            return;
        }

        // 실제 파일 내의 EOCD 오프셋 계산
        eocdOffset = fileLength - bufferSize + eocdOffsetInBuffer;
        ByteBuffer eocdBuffer = ByteBuffer.wrap(buffer, eocdOffsetInBuffer, buffer.length - eocdOffsetInBuffer);
        eocdBuffer.order(ByteOrder.LITTLE_ENDIAN);

        // EOCD 필드 파싱
        eocdBuffer.position(eocdOffsetInBuffer + 4); // 시그니처 건너뛰기

        diskNumber = eocdBuffer.getShort() & 0xFFFF;
        startDiskNumber = eocdBuffer.getShort() & 0xFFFF;
        numEntriesOnDisk = eocdBuffer.getShort() & 0xFFFF;
        totalEntries = eocdBuffer.getShort() & 0xFFFF;
        cdSize = eocdBuffer.getInt() & 0xFFFFFFFFL;
        cdOffset = eocdBuffer.getInt() & 0xFFFFFFFFL;
        commentLength = eocdBuffer.getShort() & 0xFFFF;

        // 주석이 있으면 읽기
        if (commentLength > 0) {
            comment = new byte[commentLength];
            eocdBuffer.get(comment);
        } else {
            comment = new byte[0];
        }
    }

    /**
     * 빈 ZIP 파일로 초기화합니다.
     */
    private void initializeEmptyZip() throws IOException {
        raf.setLength(0);

        // 빈 ZIP 파일의 EOCD 초기화
        ByteBuffer eocdBuffer = ByteBuffer.allocate(22);
        eocdBuffer.order(ByteOrder.LITTLE_ENDIAN);
        eocdBuffer.putInt(EOCD_SIGNATURE);
        eocdBuffer.putShort((short) 0); // diskNumber
        eocdBuffer.putShort((short) 0); // startDiskNumber
        eocdBuffer.putShort((short) 0); // numEntriesOnDisk
        eocdBuffer.putShort((short) 0); // totalEntries
        eocdBuffer.putInt(0); // cdSize
        eocdBuffer.putInt(0); // cdOffset
        eocdBuffer.putShort((short) 0); // commentLength
        eocdBuffer.flip();

        // 파일에 쓰기
        channel.position(0);
        channel.write(eocdBuffer);

        // 필드 초기화
        eocdOffset = 0;
        diskNumber = 0;
        startDiskNumber = 0;
        numEntriesOnDisk = 0;
        totalEntries = 0;
        cdSize = 0;
        cdOffset = 0;
        commentLength = 0;
        comment = new byte[0];
    }

    /**
     * ZIP 파일에 새 항목을 추가합니다.
     *
     * @param entryName 추가할 항목의 이름
     * @param data 항목의 데이터
     * @throws IOException I/O 오류 발생시
     */
    public void addEntry(String entryName, byte[] data) throws IOException {
        addEntry(entryName, data, Deflater.DEFAULT_COMPRESSION);
    }

    /**
     * ZIP 파일에 새 항목을 압축 레벨을 지정하여 추가합니다.
     *
     * @param entryName 추가할 항목의 이름
     * @param data 항목의 데이터
     * @param compressionLevel 압축 레벨 (0-9, 0은 압축 없음)
     * @throws IOException I/O 오류 발생시
     */
    public void addEntry(String entryName, byte[] data, int compressionLevel) throws IOException {
        // 이미 추가된 항목이나 기존 항목과 이름이 충돌하는지 확인
        // 실제 구현에서는 기존 항목 확인 로직 추가 필요

        // 추가할 위치 계산 (기존 CD 시작 위치)
        long fileSize = raf.length();
        long newDataOffset = cdOffset;

        // 1. 파일 포인터를 추가할 위치로 이동
        channel.position(newDataOffset);

        // 데이터 압축
        byte[] compressedData;
        int method = ZipEntry.DEFLATED;
        CRC32 crc = new CRC32();
        crc.update(data);
        long crcValue = crc.getValue();

        if (compressionLevel == 0) {
            // 압축 없음
            compressedData = data;
            method = ZipEntry.STORED;
        } else {
            // 데이터 압축
            Deflater deflater = new Deflater(compressionLevel);
            deflater.setInput(data);
            deflater.finish();

            ByteArrayOutputStream baos = new ByteArrayOutputStream(data.length);
            byte[] buffer = new byte[1024];
            while (!deflater.finished()) {
                int count = deflater.deflate(buffer);
                baos.write(buffer, 0, count);
            }
            deflater.end();

            compressedData = baos.toByteArray();

            // 압축 효과가 없으면 압축하지 않음
            if (compressedData.length >= data.length) {
                compressedData = data;
                method = ZipEntry.STORED;
            }
        }

        // 2. Local File Header 작성
        ByteBuffer localHeaderBuffer = ByteBuffer.allocate(30 + entryName.getBytes().length);
        localHeaderBuffer.order(ByteOrder.LITTLE_ENDIAN);

        localHeaderBuffer.putInt(LOCAL_FILE_HEADER_SIGNATURE); // 로컬 파일 헤더 시그니처
        localHeaderBuffer.putShort((short) 20); // 버전 (2.0)
        localHeaderBuffer.putShort((short) 0); // 플래그
        localHeaderBuffer.putShort((short) method); // 압축 방식
        localHeaderBuffer.putShort((short) 0); // 수정 시간
        localHeaderBuffer.putShort((short) 0); // 수정 날짜
        localHeaderBuffer.putInt((int) crcValue); // CRC-32
        localHeaderBuffer.putInt(compressedData.length); // 압축된 크기
        localHeaderBuffer.putInt(data.length); // 압축 해제된 크기
        localHeaderBuffer.putShort((short) entryName.getBytes().length); // 파일명 길이
        localHeaderBuffer.putShort((short) 0); // 추가 필드 길이
        localHeaderBuffer.put(entryName.getBytes()); // 파일명
        localHeaderBuffer.flip();

        // 로컬 파일 헤더 쓰기
        channel.write(localHeaderBuffer);

        // 3. 압축된 데이터 쓰기
        channel.write(ByteBuffer.wrap(compressedData));

        // 4. 추가된 항목 정보 저장
        EntryInfo entryInfo = new EntryInfo();
        entryInfo.name = entryName;
        entryInfo.method = method;
        entryInfo.crc = crcValue;
        entryInfo.compressedSize = compressedData.length;
        entryInfo.size = data.length;
        entryInfo.localHeaderOffset = newDataOffset;

        addedEntries.add(entryInfo);

        // 5. 다음 파일 위치 계산
        long nextFilePos = channel.position();

        // 6. 기존 Central Directory 읽기
        byte[] centralDirectory = null;
        if (cdSize > 0) {
            centralDirectory = new byte[(int) cdSize];
            channel.position(cdOffset);
            ByteBuffer cdBuffer = ByteBuffer.wrap(centralDirectory);
            channel.read(cdBuffer);
        }

        // 7. 새로운 Central Directory 항목들 작성
        ByteBuffer newCdBuffer = ByteBuffer.allocate(46 * addedEntries.size());
        newCdBuffer.order(ByteOrder.LITTLE_ENDIAN);

        for (EntryInfo entry : addedEntries) {
            newCdBuffer.putInt(CENTRAL_DIRECTORY_SIGNATURE); // 중앙 디렉터리 시그니처
            newCdBuffer.putShort((short) 20); // 생성 버전 (2.0)
            newCdBuffer.putShort((short) 20); // 추출 버전 (2.0)
            newCdBuffer.putShort((short) 0); // 플래그
            newCdBuffer.putShort((short) entry.method); // 압축 방식
            newCdBuffer.putShort((short) 0); // 수정 시간
            newCdBuffer.putShort((short) 0); // 수정 날짜
            newCdBuffer.putInt((int) entry.crc); // CRC-32
            newCdBuffer.putInt(entry.compressedSize); // 압축된 크기
            newCdBuffer.putInt(entry.size); // 압축 해제된 크기
            newCdBuffer.putShort((short) entry.name.getBytes().length); // 파일명 길이
            newCdBuffer.putShort((short) 0); // 추가 필드 길이
            newCdBuffer.putShort((short) 0); // 파일 주석 길이
            newCdBuffer.putShort((short) 0); // 디스크 번호
            newCdBuffer.putShort((short) 0); // 내부 파일 속성
            newCdBuffer.putInt(0); // 외부 파일 속성
            newCdBuffer.putInt((int) entry.localHeaderOffset); // 로컬 파일 헤더 오프셋
            newCdBuffer.put(entry.name.getBytes()); // 파일명
        }
        newCdBuffer.flip();

        // 8. 새 Central Directory 시작 위치에 쓰기
        channel.position(nextFilePos);
        channel.write(newCdBuffer);

        // 9. 기존 Central Directory 쓰기 (있는 경우)
        if (centralDirectory != null) {
            channel.write(ByteBuffer.wrap(centralDirectory));
        }

        // 10. 업데이트된 EOCD 작성
        long newCdOffset = nextFilePos;
        long newCdSize = newCdBuffer.capacity() + cdSize;
        int newTotalEntries = totalEntries + addedEntries.size();

        ByteBuffer eocdBuffer = ByteBuffer.allocate(22 + commentLength);
        eocdBuffer.order(ByteOrder.LITTLE_ENDIAN);
        eocdBuffer.putInt(EOCD_SIGNATURE);
        eocdBuffer.putShort((short) diskNumber);
        eocdBuffer.putShort((short) startDiskNumber);
        eocdBuffer.putShort((short) newTotalEntries);
        eocdBuffer.putShort((short) newTotalEntries);
        eocdBuffer.putInt((int) newCdSize);
        eocdBuffer.putInt((int) newCdOffset);
        eocdBuffer.putShort((short) commentLength);
        if (commentLength > 0) {
            eocdBuffer.put(comment);
        }
        eocdBuffer.flip();

        channel.write(eocdBuffer);

        // 11. 필드 업데이트
        cdOffset = newCdOffset;
        cdSize = newCdSize;
        totalEntries = newTotalEntries;
        numEntriesOnDisk = totalEntries;
        eocdOffset = channel.position() - eocdBuffer.capacity();

        // 변경된 내용을 디스크에 강제 기록
        channel.force(true);
    }

    /**
     * ZIP 파일에 텍스트 파일 항목을 추가합니다.
     *
     * @param entryName 추가할 항목의 이름
     * @param text 텍스트 내용
     * @throws IOException I/O 오류 발생시
     */
    public void addTextEntry(String entryName, String text) throws IOException {
        addEntry(entryName, text.getBytes());
    }

    @Override
    public void close() throws IOException {
        if (channel != null && channel.isOpen()) {
            channel.close();
        }
        if (raf != null) {
            raf.close();
        }
    }

    /**
     * ZIP 항목 정보를 저장하는 내부 클래스
     */
    private static class EntryInfo {
        String name;
        int method;
        long crc;
        int compressedSize;
        int size;
        long localHeaderOffset;
    }
}
