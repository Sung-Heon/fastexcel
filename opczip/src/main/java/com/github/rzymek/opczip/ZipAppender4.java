package com.github.rzymek.opczip;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;

import java.util.zip.CRC32;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;

/**
 * ZIP 파일에 새 항목을 추가하는 클래스로, 원자적 연산을 통해 원본 파일을 안전하게 보존합니다.
 * 이 클래스는 ZIP 파일을 3개의 논리적 부분(EOCD, Central Directory, 나머지)으로 분리한 후
 * 새 항목을 추가하고 적절하게 재구성합니다.
 */
public class ZipAppender4 {
    // ZIP 시그니처
    private static final int LOCAL_FILE_HEADER_SIGNATURE = 0x04034b50;
    private static final int CENTRAL_DIRECTORY_SIGNATURE = 0x02014b50;
    private static final int EOCD_SIGNATURE = 0x06054b50;
    
    /**
     * ZIP 파일에 새 파일을 추가합니다.
     * 
     * @param zipPath 기존 ZIP 파일 경로
     * @param fileToAdd 추가할 파일 경로
     * @param fileNameInZip ZIP 내에서의 파일 경로
     * @throws IOException I/O 오류 발생시
     */
    public static void appendFile(Path zipPath, Path fileToAdd, String fileNameInZip) throws IOException {
        System.out.println("\n======= ZIP 파일 추가 작업을 시작합니다 =======");
        System.out.println("대상 ZIP: " + zipPath);
        System.out.println("추가할 파일: " + fileToAdd);
        System.out.println("ZIP 내 경로: " + fileNameInZip);

        // 0. 추가할 파일 데이터 준비
        byte[] fileData = Files.readAllBytes(fileToAdd);
        byte[] fileNameBytes = fileNameInZip.getBytes(StandardCharsets.UTF_8);
        
        // 압축 및 CRC 계산
        byte[] compressedData = compress(fileData);
        CRC32 crc = new CRC32();
        crc.update(fileData);
        long crcValue = crc.getValue();

        // 1. 기존 ZIP 파일을 분석하여 3개 부분으로 분리
        System.out.println("\n[1단계] 기존 ZIP 파일 분석 및 분리...");
        ZipFileComponents components = splitZipFile(zipPath);
        
        // 파싱 결과 출력
        System.out.println("  - 데이터 영역 크기: " + components.dataSection.length + " 바이트");
        System.out.println("  - Central Directory 크기: " + components.centralDirectory.length + " 바이트");
        System.out.println("  - EOCD 크기: " + components.eocd.length + " 바이트");
        System.out.println("  - 총 엔트리 수: " + components.totalEntries);
        
        // 임시 파일 생성
        Path tempPath = Files.createTempFile(
            zipPath.getParent() != null ? zipPath.getParent() : Paths.get(System.getProperty("java.io.tmpdir")),
            zipPath.getFileName().toString(),
            ".tmp");
        System.out.println("임시 파일 생성: " + tempPath);
        
        // 2. 새 로컬 헤더 및 파일 데이터 생성
        System.out.println("\n[2단계] 새 로컬 헤더 및 파일 데이터 준비...");
        LocalFileEntry newEntry = createLocalFileEntry(fileNameBytes, fileData, compressedData, crcValue);
        System.out.println("  - 로컬 헤더 크기: " + newEntry.localHeader.length + " 바이트");
        System.out.println("  - 압축된 데이터 크기: " + compressedData.length + " 바이트");
        System.out.println("  - 압축률: " + String.format("%.1f%%", (1 - (double)compressedData.length / fileData.length) * 100));
        
        try (FileChannel channel = FileChannel.open(tempPath, StandardOpenOption.CREATE, StandardOpenOption.WRITE)) {
            // 3. 기존 데이터 영역을 임시 파일에 복사
            System.out.println("\n[3단계] 기존 데이터 영역 복사...");
            channel.write(ByteBuffer.wrap(components.dataSection));
            
            // 4. 새 로컬 헤더 및 데이터 추가
            System.out.println("\n[4단계] 새 로컬 헤더 및 데이터 추가...");
            long newEntryOffset = channel.position();
            System.out.println("  - 새 엔트리 시작 오프셋: " + newEntryOffset);
            
            // 로컬 헤더 쓰기
            channel.write(ByteBuffer.wrap(newEntry.localHeader));
            // 압축 데이터 쓰기
            channel.write(ByteBuffer.wrap(compressedData));
            
            // 5. 기존 Central Directory 복사
            System.out.println("\n[5단계] 기존 Central Directory 복사...");
            long newCdOffset = channel.position();
            System.out.println("  - 새 Central Directory 시작 오프셋: " + newCdOffset);
            channel.write(ByteBuffer.wrap(components.centralDirectory));
            
            // 6. 새 엔트리의 Central Directory 항목 추가
            System.out.println("\n[6단계] 새 엔트리의 Central Directory 항목 추가...");
            byte[] newCdEntry = createCentralDirectoryEntry(
                fileNameBytes, compressedData.length, fileData.length, 
                crcValue, newEntry.dosTime, newEntryOffset);
            channel.write(ByteBuffer.wrap(newCdEntry));
            
            // 7. 새로운 EOCD 생성 및 추가
            System.out.println("\n[7단계] 새 EOCD 생성 및 추가...");
            long newCdSize = components.centralDirectory.length + newCdEntry.length;
            byte[] newEocd = createNewEOCD(components.totalEntries + 1, newCdOffset, newCdSize);
            channel.write(ByteBuffer.wrap(newEocd));
        }
        
        // 8. 임시 파일을 원본 파일로 대체 (원자적 작업)
        System.out.println("\n[8단계] 임시 파일을 원본으로 대체 (원자적 작업)...");
        Files.move(tempPath, zipPath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        
        System.out.println("\n작업 완료! 새 파일이 ZIP에 추가되었습니다.");
        
        // 9. 결과 검증
        System.out.println("\n[9단계] 결과 ZIP 파일 분석...");
        ZipParser.parseAndPrintInfo(zipPath);
    }
    
    /**
     * ZIP 파일의 3개 구성요소
     */
    private static class ZipFileComponents {
        byte[] dataSection;       // Local headers와 파일 데이터
        byte[] centralDirectory;  // Central Directory
        byte[] eocd;             // End of Central Directory
        int totalEntries;        // 총 엔트리 수
    }
    
    /**
     * 로컬 파일 항목 데이터
     */
    private static class LocalFileEntry {
        byte[] localHeader;
        long dosTime;
    }
    
    /**
     * EOCD(End of Central Directory) 레코드 정보를 담는 데이터 클래스.
     */
    private static class EOCDRecord {
        final int totalEntries;
        final long centralDirectorySize;
        final long centralDirectoryOffset;
        final String comment;

        EOCDRecord(int totalEntries, long centralDirectorySize, long centralDirectoryOffset, String comment) {
            this.totalEntries = totalEntries;
            this.centralDirectorySize = centralDirectorySize;
            this.centralDirectoryOffset = centralDirectoryOffset;
            this.comment = comment;
        }
    }

    /**
     * ZIP 파일을 세 부분으로 분리합니다: 데이터 영역, Central Directory, EOCD
     */
    private static ZipFileComponents splitZipFile(Path zipPath) throws IOException {
        ZipFileComponents components = new ZipFileComponents();

        try (RandomAccessFile raf = new RandomAccessFile(zipPath.toFile(), "r")) {
            long fileSize = raf.length();

            // 1. EOCD 파싱하여 Central Directory 위치 및 크기 확인
            EOCDRecord eocd = findAndParseEOCD(raf);

            if (eocd == null) {
                // EOCD를 찾지 못한 경우 비어있는 ZIP으로 처리
                components.dataSection = new byte[0];
                components.centralDirectory = new byte[0];
                components.eocd = createNewEOCD(0, 0, 0);
                components.totalEntries = 0;
                return components;
            }

            System.out.println("  - EOCD 파싱 결과:");
            System.out.println("    - Central Directory 오프셋: " + eocd.centralDirectoryOffset);
            System.out.println("    - Central Directory 크기: " + eocd.centralDirectorySize);
            System.out.println("    - 총 엔트리 수: " + eocd.totalEntries);
            if (!eocd.comment.isEmpty()) {
                System.out.println("    - ZIP 주석: " + eocd.comment);
            }

            // 2. 데이터 영역 (Local headers와 파일 데이터) 읽기
            components.dataSection = new byte[(int)eocd.centralDirectoryOffset];
            raf.seek(0);
            raf.readFully(components.dataSection);

            // 3. Central Directory 읽기
            components.centralDirectory = new byte[(int)eocd.centralDirectorySize];
            raf.seek(eocd.centralDirectoryOffset);
            raf.readFully(components.centralDirectory);

            // 4. EOCD 읽기
            long eocdOffset = eocd.centralDirectoryOffset + eocd.centralDirectorySize;
            components.eocd = new byte[(int)(fileSize - eocdOffset)];
            raf.seek(eocdOffset);
            raf.readFully(components.eocd);

            components.totalEntries = eocd.totalEntries;
        }

        return components;
    }

    /**
     * 파일 끝에서부터 EOCD 시그니처를 찾아 레코드를 파싱합니다.
     */
    private static EOCDRecord findAndParseEOCD(RandomAccessFile raf) throws IOException {
        long fileSize = raf.length();
        // EOCD는 파일 끝에 위치하며, 주석 길이에 따라 위치가 가변적입니다.
        long scanStartPos = fileSize - 22; // 최소 EOCD 크기
        if (scanStartPos < 0) return null;

        long maxScanSize = Math.min(fileSize, 65535 + 22);
        long searchBoundary = fileSize - maxScanSize;
        if (searchBoundary < 0) searchBoundary = 0;

        for (long pos = scanStartPos; pos >= searchBoundary; pos--) {
            raf.seek(pos);
            if (raf.readInt() == Integer.reverseBytes(EOCD_SIGNATURE)) {
                raf.seek(pos);
                ByteBuffer buffer = ByteBuffer.allocate(22);
                buffer.order(ByteOrder.LITTLE_ENDIAN);
                raf.getChannel().read(buffer);
                buffer.flip();

                buffer.getInt(); // 시그니처 건너뛰기
                buffer.getShort(); // 이 디스크의 번호
                buffer.getShort(); // CD가 시작되는 디스크 번호
                buffer.getShort(); // 이 디스크의 엔트리 수

                int totalEntries = Short.toUnsignedInt(buffer.getShort());
                long cdSize = Integer.toUnsignedLong(buffer.getInt());
                long cdOffset = Integer.toUnsignedLong(buffer.getInt());
                int commentLength = Short.toUnsignedInt(buffer.getShort());

                String comment = "";
                if (commentLength > 0) {
                    raf.seek(pos + 22);
                    byte[] commentBytes = new byte[commentLength];
                    raf.readFully(commentBytes);
                    comment = new String(commentBytes, StandardCharsets.UTF_8);
                }

                return new EOCDRecord(totalEntries, cdSize, cdOffset, comment);
            }
        }
        return null; // EOCD를 찾지 못함
    }
    
    /**
     * 파일의 로컬 헤더를 생성합니다.
     */
    private static LocalFileEntry createLocalFileEntry(
            byte[] fileNameBytes, byte[] fileData, byte[] compressedData, long crcValue) {
        LocalFileEntry entry = new LocalFileEntry();
        
        // MS-DOS 시간/날짜 형식 생성
        entry.dosTime = toDosTime(LocalDateTime.now());
        
        // 로컬 파일 헤더 생성
        ByteBuffer buffer = ByteBuffer.allocate(30 + fileNameBytes.length);
        buffer.order(ByteOrder.LITTLE_ENDIAN);
        
        buffer.putInt(LOCAL_FILE_HEADER_SIGNATURE);
        buffer.putShort((short) 20); // Version needed to extract
        buffer.putShort((short) 0);  // General purpose bit flag
        buffer.putShort((short) 8);  // Compression method (DEFLATE)
        buffer.putInt((int) entry.dosTime); // Last mod file time & date
        buffer.putInt((int) crcValue);
        buffer.putInt(compressedData.length);
        buffer.putInt(fileData.length);
        buffer.putShort((short) fileNameBytes.length);
        buffer.putShort((short) 0); // Extra field length
        buffer.put(fileNameBytes);
        
        entry.localHeader = buffer.array();
        return entry;
    }
    
    /**
     * Central Directory 항목을 생성합니다.
     */
    private static byte[] createCentralDirectoryEntry(
            byte[] fileNameBytes, int compressedSize, int uncompressedSize, 
            long crc32, long dosTime, long localHeaderOffset) {
        
        ByteBuffer buffer = ByteBuffer.allocate(46 + fileNameBytes.length);
        buffer.order(ByteOrder.LITTLE_ENDIAN);
        
        buffer.putInt(CENTRAL_DIRECTORY_SIGNATURE);
        buffer.putShort((short) 20); // Version made by
        buffer.putShort((short) 20); // Version needed to extract
        buffer.putShort((short) 0);  // General purpose bit flag
        buffer.putShort((short) 8);  // Compression method
        buffer.putInt((int) dosTime); // Last mod file time & date
        buffer.putInt((int) crc32);
        buffer.putInt(compressedSize);
        buffer.putInt(uncompressedSize);
        buffer.putShort((short) fileNameBytes.length);
        buffer.putShort((short) 0); // Extra field length
        buffer.putShort((short) 0); // File comment length
        buffer.putShort((short) 0); // Disk number start
        buffer.putShort((short) 0); // Internal file attributes
        buffer.putInt(0);           // External file attributes
        buffer.putInt((int) localHeaderOffset); // Relative offset of local header
        buffer.put(fileNameBytes);
        
        return buffer.array();
    }
    
    /**
     * 새로운 EOCD 레코드를 생성합니다.
     */
    private static byte[] createNewEOCD(int totalEntries, long cdOffset, long cdSize) {
        ByteBuffer buffer = ByteBuffer.allocate(22); // 기본 EOCD 크기 (주석 없음)
        buffer.order(ByteOrder.LITTLE_ENDIAN);
        
        buffer.putInt(EOCD_SIGNATURE);
        buffer.putShort((short) 0); // Number of this disk
        buffer.putShort((short) 0); // Disk where central directory starts
        buffer.putShort((short) totalEntries); // Number of entries on this disk
        buffer.putShort((short) totalEntries); // Total number of entries
        buffer.putInt((int) cdSize); // Size of central directory
        buffer.putInt((int) cdOffset); // Offset of start of central directory
        buffer.putShort((short) 0); // ZIP file comment length
        
        return buffer.array();
    }
    
    /**
     * 데이터를 Deflate 알고리즘으로 압축합니다.
     */
    private static byte[] compress(byte[] data) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        Deflater deflater = new Deflater(Deflater.DEFAULT_COMPRESSION, true); // true = nowrap
        
        try (DeflaterOutputStream dos = new DeflaterOutputStream(baos, deflater)) {
            dos.write(data);
        }
        
        deflater.end();
        return baos.toByteArray();
    }
    
    /**
     * Java LocalDateTime을 MS-DOS 시간/날짜 형식으로 변환합니다.
     */
    private static long toDosTime(LocalDateTime time) {
        int year = time.getYear();
        if (year < 1980) year = 1980;
        
        return ((long) (year - 1980) << 25) |
                ((long) time.getMonthValue() << 21) |
                ((long) time.getDayOfMonth() << 16) |
                ((long) time.getHour() << 11) |
                ((long) time.getMinute() << 5) |
                ((long) time.getSecond() >> 1);
    }
    
    /**
     * 데모용 메인 메소드
     */
    public static void main(String[] args) {
        try {
            // 테스트용 ZIP 파일 준비
            Path zipPath = Paths.get("new_archive.zip");
            Path fileToAdd1 = Paths.get("file_to_add.txt");
            Path fileToAdd2 = Paths.get("file_to_add2.txt");
            
            // 테스트용 빈 ZIP 파일 생성 (존재하지 않는 경우)
            if (!Files.exists(zipPath)) {
                ByteBuffer emptyZip = ByteBuffer.allocate(22);
                emptyZip.order(ByteOrder.LITTLE_ENDIAN);
                emptyZip.putInt(EOCD_SIGNATURE);
                emptyZip.putShort((short) 0); // 디스크 번호
                emptyZip.putShort((short) 0); // CD 시작 디스크
                emptyZip.putShort((short) 0); // 이 디스크의 엔트리 수
                emptyZip.putShort((short) 0); // 총 엔트리 수
                emptyZip.putInt(0);          // CD 크기
                emptyZip.putInt(0);          // CD 오프셋
                emptyZip.putShort((short) 0); // 주석 길이
                emptyZip.flip();
                
                try (FileChannel channel = FileChannel.open(zipPath, 
                     StandardOpenOption.CREATE, StandardOpenOption.WRITE)) {
                    channel.write(emptyZip);
                }
                
                System.out.println("빈 ZIP 파일 생성: " + zipPath);
            }
            
            // 테스트용 파일 생성
            Files.write(fileToAdd1, "이것은 첫 번째 테스트 파일입니다.".getBytes(StandardCharsets.UTF_8));
            Files.write(fileToAdd2, "이것은 두 번째 테스트 파일입니다. 좀 더 긴 내용을 포함하고 있습니다.".getBytes(StandardCharsets.UTF_8));
            
            // 첫 번째 파일 추가
            System.out.println("\n===== 첫 번째 파일 추가 =====");
            appendFile(zipPath, fileToAdd1, "folder1/file12.txt");
            
            // 두 번째 파일 추가
            System.out.println("\n===== 두 번째 파일 추가 =====");
            appendFile(zipPath, fileToAdd2, "folder2/subfolder/file23.txt");
            
        } catch (IOException e) {
            System.err.println("오류 발생: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
