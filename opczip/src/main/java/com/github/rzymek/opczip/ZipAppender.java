package com.github.rzymek.opczip;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.CRC32;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;

/**
 * ZipAppender는 기존 ZIP 파일에 새로운 항목을 추가하는 클래스입니다.
 * 이 클래스는 기존 ZIP 파일의 구조를 분석하고, 새로운 항목을 추가한 후
 * Central Directory와 End of Central Directory(EOCD)를 업데이트합니다.
 */
public class ZipAppender implements Closeable {
    // ZIP 시그니처
    private static final int LOCAL_FILE_HEADER_SIGNATURE = 0x04034b50;
    private static final int CENTRAL_DIRECTORY_SIGNATURE = 0x02014b50;
    private static final int EOCD_SIGNATURE = 0x06054b50;
    
    // 클래스 필드
    private final Path zipPath;
    private final List<EntryInfo> addedEntries = new ArrayList<>();
    
    // EOCD 정보
    private int totalEntries;
    private long cdSize;
    private long cdOffset;
    private int commentLength;
    private byte[] comment;
    
    // 임시 파일 경로
    private Path tempPath;
    
    // I/O 필드
    private RandomAccessFile originalRaf;
    private FileChannel originalChannel;
    private FileOutputStream outputStream;
    private FileChannel outputChannel;
    private long currentPosition;
    
    /**
     * 항목 정보를 저장하는 내부 클래스
     */
    private static class EntryInfo {
        String name;
        int method;
        int size;
        int compressedSize;
        long crc;
        long localHeaderOffset;
    }

    /**
     * 기존 ZIP 파일을 열고 분석합니다.
     *
     * @param zipPath ZIP 파일 경로
     * @throws IOException I/O 오류 발생시
     */
    public ZipAppender(Path zipPath) throws IOException {
        this.zipPath = zipPath;
        
        // 파일이 존재하지 않으면 빈 ZIP 파일 생성
        if (!Files.exists(zipPath)) {
            try (FileOutputStream fos = new FileOutputStream(zipPath.toFile());
                 java.util.zip.ZipOutputStream zos = new java.util.zip.ZipOutputStream(fos)) {
                zos.finish();
            }
            System.out.println("[DEBUG] 새 ZIP 파일 생성: " + zipPath);
        }
        
        // 원본 파일 열기 (읽기 전용)
        this.originalRaf = new RandomAccessFile(zipPath.toFile(), "r");
        this.originalChannel = originalRaf.getChannel();
        
        // 원본 파일에서 EOCD 및 CD 정보 파싱
        parseEocd();
        
        // 임시 파일 생성 및 열기 (쓰기 모드)
        Path parent = zipPath.getParent();
        if (parent != null) {
            this.tempPath = Files.createTempFile(parent, 
                                               zipPath.getFileName().toString(), ".tmp");
        } else {
            // 부모 경로가 없는 경우 시스템 임시 디렉토리 사용
            this.tempPath = Files.createTempFile(
                                zipPath.getFileName().toString(), ".tmp");
        }
        
        // 원본 파일 내용을 임시 파일로 복사
        Files.copy(zipPath, tempPath, StandardCopyOption.REPLACE_EXISTING);
        
        // 임시 파일을 쓰기 모드로 열기
        this.outputStream = new FileOutputStream(tempPath.toFile(), true);
        this.outputChannel = outputStream.getChannel();
        
        // 현재 쓰기 위치는 파일 끝 (다음 로컬 파일 헤더가 작성될 위치)
        this.currentPosition = outputChannel.size();
        
        System.out.println("[DEBUG] ZipAppender 초기화 - 원본 크기: " + originalChannel.size() + 
                          ", 항목 수: " + totalEntries + 
                          ", CD 오프셋: " + cdOffset + 
                          ", CD 크기: " + cdSize);
    }
    
    /**
     * 압축 메서드로 텍스트 항목을 추가합니다.
     *
     * @param name 항목 이름
     * @param content 텍스트 내용
     * @throws IOException I/O 오류 발생시
     */
    public void addTextEntry(String name, String content) throws IOException {
        addEntry(name, content.getBytes(), 6); // 기본 압축 레벨 6
    }
    
    /**
     * 지정한 압축 수준으로 항목을 추가합니다.
     *
     * @param name 항목 이름
     * @param data 항목 데이터
     * @param compressionLevel 압축 수준 (0-9, 0은 압축 없음)
     * @throws IOException I/O 오류 발생시
     */
    public void addEntry(String name, byte[] data, int compressionLevel) throws IOException {
        System.out.println("[DEBUG] 항목 추가 시작: " + name);
        System.out.println("[DEBUG] 원본 데이터 크기: " + data.length + " 바이트");
        
        // CRC 계산
        CRC32 crc = new CRC32();
        crc.update(data);
        long crcValue = crc.getValue();
        
        // 데이터 압축 (압축 수준이 0이면 압축하지 않음)
        byte[] compressedData;
        int method;
        
        if (compressionLevel == 0) {
            compressedData = data;
            method = 0; // STORED
        } else {
            compressedData = compressData(data, compressionLevel);
            
            // 압축 결과가 원본보다 크면 압축하지 않음
            if (compressedData.length >= data.length) {
                compressedData = data;
                method = 0; // STORED
            } else {
                method = 8; // DEFLATED
            }
        }
        
        // 파일명 바이트로 변환
        byte[] nameBytes = name.getBytes();
        int nameLength = nameBytes.length;
        System.out.println("[DEBUG] 파일명 바이트 길이: " + nameLength);
        
        // 로컬 파일 헤더 크기 계산
        int localHeaderSize = 30 + nameLength; // 로컬 헤더(30) + 파일명 길이
        System.out.println("[DEBUG] 로컬 파일 헤더 크기: " + localHeaderSize + " 바이트");
        
        // 로컬 파일 헤더 작성
        ByteBuffer headerBuffer = ByteBuffer.allocate(localHeaderSize);
        headerBuffer.order(ByteOrder.LITTLE_ENDIAN);
        
        headerBuffer.putInt(LOCAL_FILE_HEADER_SIGNATURE);
        headerBuffer.putShort((short) 20); // 버전 (2.0)
        headerBuffer.putShort((short) 0);  // 플래그
        headerBuffer.putShort((short) method); // 압축 방식 (0:저장, 8:deflate)
        headerBuffer.putShort((short) 0);  // 수정 시간
        headerBuffer.putShort((short) 0);  // 수정 날짜
        headerBuffer.putInt((int) crcValue); // CRC-32
        headerBuffer.putInt(compressedData.length); // 압축된 크기
        headerBuffer.putInt(data.length); // 압축 해제된 크기
        headerBuffer.putShort((short) nameLength); // 파일명 길이
        headerBuffer.putShort((short) 0);  // 추가 필드 길이
        headerBuffer.put(nameBytes); // 파일명
        headerBuffer.flip();
        
        // 항목 정보 생성
        EntryInfo entry = new EntryInfo();
        entry.name = name;
        entry.method = method;
        entry.size = data.length;
        entry.compressedSize = compressedData.length;
        entry.crc = crcValue;
        entry.localHeaderOffset = currentPosition;
        addedEntries.add(entry);
        
        // 로컬 파일 헤더 쓰기
        outputChannel.position(currentPosition);
        outputChannel.write(headerBuffer);
        
        // 압축된 데이터 쓰기
        outputChannel.write(ByteBuffer.wrap(compressedData));
        
        // 현재 위치 업데이트
        currentPosition += localHeaderSize + compressedData.length;
    }
    
    /**
     * 기본 압축 수준으로 항목을 추가합니다.
     *
     * @param name 항목 이름
     * @param data 항목 데이터
     * @throws IOException I/O 오류 발생시
     */
    public void addEntry(String name, byte[] data) throws IOException {
        addEntry(name, data, 6); // 기본 압축 레벨 6
    }
    
    /**
     * 압축 메서드를 사용하여 데이터를 압축합니다.
     *
     * @param data 원본 데이터
     * @param level 압축 수준 (1-9)
     * @return 압축된 데이터
     */
    private byte[] compressData(byte[] data, int level) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        Deflater deflater = new Deflater(level, true); // true = nowrap (ZLib 헤더 없이)
        
        try (DeflaterOutputStream dos = new DeflaterOutputStream(baos, deflater)) {
            dos.write(data);
        }
        
        return baos.toByteArray();
    }
    
    /**
     * EOCD를 파싱하여 Central Directory 정보를 읽습니다.
     */
    private void parseEocd() throws IOException {
        long fileSize = originalChannel.size();
        
        // 작은 파일은 빈 파일로 처리
        if (fileSize < 22) {
            totalEntries = 0;
            cdSize = 0;
            cdOffset = 0;
            commentLength = 0;
            comment = new byte[0];
            return;
        }
        
        // 파일 끝에서 EOCD를 찾기 위한 최대 버퍼 크기 계산
        // (EOCD는 최대 64K 주석을 가질 수 있음)
        int maxBufferSize = Math.min((int)fileSize, 65536 + 22);
        ByteBuffer searchBuffer = ByteBuffer.allocate(maxBufferSize);
        
        // 파일 끝에서부터 읽기
        originalChannel.position(fileSize - maxBufferSize);
        originalChannel.read(searchBuffer);
        searchBuffer.flip();
        
        // EOCD 시그니처 찾기
        int eocdPos = -1;
        for (int i = searchBuffer.limit() - 22; i >= 0; i--) {
            if (searchBuffer.get(i) == 0x50 && 
                searchBuffer.get(i + 1) == 0x4B && 
                searchBuffer.get(i + 2) == 0x05 && 
                searchBuffer.get(i + 3) == 0x06) {
                
                // 확인: 이 위치에서 EOCD가 파일 끝까지의 거리와 일치하는지
                int commentLen = (searchBuffer.get(i + 20) & 0xFF) | 
                                ((searchBuffer.get(i + 21) & 0xFF) << 8);
                if ((searchBuffer.limit() - i - 22) == commentLen) {
                    eocdPos = i;
                    break;
                }
            }
        }
        
        // EOCD를 찾지 못했으면 빈 ZIP으로 처리
        if (eocdPos == -1) {
            totalEntries = 0;
            cdSize = 0;
            cdOffset = 0;
            commentLength = 0;
            comment = new byte[0];
            return;
        }
        
        // EOCD 데이터 파싱
        ByteBuffer eocdBuffer = ((ByteBuffer)searchBuffer.position(eocdPos)).slice();
        eocdBuffer.order(ByteOrder.LITTLE_ENDIAN);
        
        // EOCD 필드 파싱
        int signature = eocdBuffer.getInt();  // 0x06054b50
        eocdBuffer.getShort();  // 디스크 번호
        eocdBuffer.getShort();  // CD 시작 디스크
        int entriesOnDisk = eocdBuffer.getShort() & 0xFFFF;
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
     * Central Directory를 작성하고 파일을 마무리합니다.
     */
    @Override
    public void close() throws IOException {
        try {
            // 새로운 Central Directory 시작 위치
            long newCdOffset = currentPosition;
            System.out.println("[DEBUG] 새 CD 시작 위치: " + newCdOffset);
            
            // 1. 기존 Central Directory 복사
            if (totalEntries > 0 && cdSize > 0) {
                // 원본 파일의 CD 위치로 이동
                originalChannel.position(cdOffset);
                
                // 버퍼를 통해 CD 복사
                ByteBuffer cdBuffer = ByteBuffer.allocate(8192);
                long remaining = cdSize;
                
                while (remaining > 0) {
                    cdBuffer.clear();
                    int toRead = (int) Math.min(cdBuffer.capacity(), remaining);
                    cdBuffer.limit(toRead);
                    
                    int bytesRead = originalChannel.read(cdBuffer);
                    if (bytesRead <= 0) break;
                    
                    cdBuffer.flip();
                    outputChannel.write(cdBuffer);
                    
                    remaining -= bytesRead;
                }
                
                System.out.println("[DEBUG] 기존 CD 복사 완료 - " + cdSize + " 바이트");
            }
            
            // 2. 새로 추가된 항목들의 Central Directory 항목 작성
            long newEntriesCdSize = 0;
            if (!addedEntries.isEmpty()) {
                for (EntryInfo entry : addedEntries) {
                    byte[] nameBytes = entry.name.getBytes();
                    int entrySize = 46 + nameBytes.length;
                    
                    ByteBuffer cdEntry = ByteBuffer.allocate(entrySize);
                    cdEntry.order(ByteOrder.LITTLE_ENDIAN);
                    
                    cdEntry.putInt(CENTRAL_DIRECTORY_SIGNATURE);
                    cdEntry.putShort((short) 20); // 버전 생성자
                    cdEntry.putShort((short) 20); // 버전 추출자
                    cdEntry.putShort((short) 0);  // 플래그
                    cdEntry.putShort((short) entry.method); // 압축 방식
                    cdEntry.putShort((short) 0);  // 마지막 수정 시간
                    cdEntry.putShort((short) 0);  // 마지막 수정 날짜
                    cdEntry.putInt((int) entry.crc); // CRC-32
                    cdEntry.putInt(entry.compressedSize); // 압축된 크기
                    cdEntry.putInt(entry.size); // 압축 해제된 크기
                    cdEntry.putShort((short) nameBytes.length); // 파일명 길이
                    cdEntry.putShort((short) 0);  // 추가 필드 길이
                    cdEntry.putShort((short) 0);  // 파일 주석 길이
                    cdEntry.putShort((short) 0);  // 디스크 번호 시작
                    cdEntry.putShort((short) 0);  // 내부 파일 속성
                    cdEntry.putInt(0); // 외부 파일 속성
                    cdEntry.putInt((int) entry.localHeaderOffset); // 로컬 헤더 상대 오프셋
                    cdEntry.put(nameBytes); // 파일명
                    
                    cdEntry.flip();
                    outputChannel.write(cdEntry);
                    
                    newEntriesCdSize += entrySize;
                    System.out.println("[DEBUG] 새 CD 항목 작성: " + entry.name + " - " + entrySize + " 바이트");
                }
                
                System.out.println("[DEBUG] 새 CD 항목 작성 완료 - " + newEntriesCdSize + " 바이트");
            }
            
            // 3. 새 EOCD 작성
            long newTotalCdSize = cdSize + newEntriesCdSize;
            int newTotalEntries = totalEntries + addedEntries.size();
            
            // EOCD 위치
            long eocdOffset = currentPosition + newTotalCdSize;
            
            // EOCD 헤더 작성
            ByteBuffer eocd = ByteBuffer.allocate(22 + commentLength);
            eocd.order(ByteOrder.LITTLE_ENDIAN);
            
            eocd.putInt(EOCD_SIGNATURE); // 시그니처
            eocd.putShort((short) 0); // 디스크 번호
            eocd.putShort((short) 0); // 시작 디스크
            eocd.putShort((short) newTotalEntries); // 디스크의 항목 수
            eocd.putShort((short) newTotalEntries); // 전체 항목 수
            eocd.putInt((int) newTotalCdSize); // CD 크기
            eocd.putInt((int) newCdOffset); // CD 시작 위치
            eocd.putShort((short) commentLength); // 주석 길이
            
            // 기존 주석 복사
            if (commentLength > 0) {
                eocd.put(comment);
            }
            
            eocd.flip();
            outputChannel.write(eocd);
            
            System.out.println("[DEBUG] EOCD 작성 완료 - 위치: " + eocdOffset);
            System.out.println("[DEBUG] EOCD 정보 - 새 CD 오프셋: " + newCdOffset);
            System.out.println("[DEBUG] EOCD 정보 - 기존 CD 크기: " + cdSize + " 바이트");
            System.out.println("[DEBUG] EOCD 정보 - 새 항목 CD 크기: " + newEntriesCdSize + " 바이트");
            System.out.println("[DEBUG] EOCD 정보 - 총 CD 크기: " + newTotalCdSize + " 바이트");
            System.out.println("[DEBUG] EOCD 정보 - 기존 항목 수: " + totalEntries);
            System.out.println("[DEBUG] EOCD 정보 - 새 항목 수: " + addedEntries.size());
            System.out.println("[DEBUG] EOCD 정보 - 총 항목 수: " + newTotalEntries);
            
            // 모든 스트림 닫기
            outputStream.close();
            originalRaf.close();
            
            // 임시 파일을 원본 파일로 대체
            Files.move(tempPath, zipPath, StandardCopyOption.REPLACE_EXISTING);
            
            System.out.println("[DEBUG] ZIP 파일 업데이트 완료");
        } catch (Exception e) {
            // 예외 발생 시 리소스 정리
            try {
                if (outputStream != null) outputStream.close();
                if (originalRaf != null) originalRaf.close();
                // 임시 파일 삭제
                Files.deleteIfExists(tempPath);
            } catch (IOException ex) {
                e.addSuppressed(ex);
            }
            throw e;
        }
    }
}
