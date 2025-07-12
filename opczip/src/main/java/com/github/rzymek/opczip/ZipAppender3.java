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

/**
 * 기존 ZIP 파일에 파일을 추가하는 기능을 구현한 클래스입니다.
 * java.util.zip의 고수준 API를 사용하지 않고, ZIP 파일 형식을 직접 조작합니다.
 * 참고: 이 코드는 ZIP64 확장을 지원하지 않습니다. (파일 크기 ~4GB, 엔트리 수 ~65535개 제한)
 */
public class ZipAppender3 {

    // ZIP 파일 구조에 사용되는 시그니처 (Little Endian 기준)
    private static final int LOCAL_FILE_HEADER_SIGNATURE = 0x04034b50;
    private static final int CENTRAL_DIRECTORY_FILE_HEADER_SIGNATURE = 0x02014b50;
    private static final int END_OF_CENTRAL_DIRECTORY_SIGNATURE = 0x06054b50;

    /**
     * EOCD(End of Central Directory) 레코드 정보를 담는 클래스.
     */
    private static class EOCDRecord {
        long centralDirectoryOffset;
        int totalEntries;
        long centralDirectorySize;
    }

    public static void main(String[] args) {
        try {
            // --- 데모용 테스트 파일 준비 ---
            Path originalZipPath = Paths.get("example.zip");
            Path fileToAddPath = Paths.get("newFile.txt");
            String fileNameInZip = "added/newFile.txt";

            // 1. 테스트를 위해 기존 ZIP 파일 삭제 후 재생성
            Files.deleteIfExists(originalZipPath);
            Files.deleteIfExists(fileToAddPath);

            createDummyZip(originalZipPath);
            System.out.println("테스트용 원본 ZIP 파일 생성: " + originalZipPath.toAbsolutePath());

            // 2. 추가할 파일 생성
            Files.write(fileToAddPath, "이것은 새로 추가될 파일의 내용입니다.".getBytes(StandardCharsets.UTF_8));
            System.out.println("추가할 테스트 파일 생성: " + fileToAddPath.toAbsolutePath());

            // --- 핵심 로직 실행 ---
            System.out.println("\nZIP 파일에 새 파일 추가 작업을 시작합니다...");
            addFileToZip(originalZipPath, fileToAddPath, fileNameInZip);

            System.out.println("\n작업 완료! " + originalZipPath.getFileName() + " 파일이 성공적으로 업데이트되었습니다.");
            System.out.println("표준 ZIP 유틸리티로 파일이 정상적으로 열리는지 확인해보세요.");

        } catch (IOException e) {
            System.err.println("오류가 발생했습니다: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * 기존 ZIP 파일에 새로운 파일을 추가합니다.
     *
     * @param originalZipPath 원본 ZIP 파일 경로
     * @param newFilePath 추가할 파일 경로
     * @param fileNameInZip ZIP 파일 내에 저장될 파일 이름 (경로 포함 가능)
     * @throws IOException I/O 오류 발생 시
     */
    public static void addFileToZip(Path originalZipPath, Path newFilePath, String fileNameInZip) throws IOException {
        // 부모 경로가 없는 경우 (상대 경로인 경우) 임시 디렉토리를 사용합니다.
        Path tempDir = originalZipPath.getParent();
        if (tempDir == null) {
            tempDir = Paths.get(System.getProperty("java.io.tmpdir"));
        }
        Path tempZipPath = Files.createTempFile(tempDir, "zip_temp", ".tmp");

        try (RandomAccessFile raf = new RandomAccessFile(originalZipPath.toFile(), "r");
             FileChannel sourceChannel = raf.getChannel();
             FileChannel destChannel = FileChannel.open(tempZipPath, StandardOpenOption.WRITE, StandardOpenOption.CREATE)) {

            // 1. EOCD 레코드를 찾아 파싱합니다.
            EOCDRecord eocd = findAndParseEOCD(raf);
            if (eocd == null) {
                throw new IOException("유효한 ZIP 파일이 아니거나 EOCD 레코드를 찾을 수 없습니다.");
            }

            // 2. [수정] 기존 ZIP 파일의 데이터 부분 (Central Directory 이전까지)을 임시 파일에 안정적으로 복사합니다.
            copyChannel(sourceChannel, destChannel, 0, eocd.centralDirectoryOffset);
            long newEntryOffset = destChannel.position();

            // 3. 추가할 파일을 압축하고, Local File Header와 함께 임시 파일에 씁니다.
            byte[] fileBytes = Files.readAllBytes(newFilePath);
            byte[] fileNameBytes = fileNameInZip.getBytes(StandardCharsets.UTF_8);

            CRC32 crc = new CRC32();
            crc.update(fileBytes);
            long crcValue = crc.getValue();

            byte[] compressedData = compress(fileBytes);
            long dosTime = toDosTime(LocalDateTime.now());

            ByteBuffer localHeader = createLocalFileHeader(fileNameBytes, compressedData.length, fileBytes.length, crcValue, dosTime);
            destChannel.write(localHeader);
            destChannel.write(ByteBuffer.wrap(compressedData));

            // 4. 새로운 Central Directory의 시작 위치를 기록합니다.
            long newCentralDirectoryOffset = destChannel.position();

            // 5. [수정] 기존 Central Directory를 임시 파일에 안정적으로 복사합니다.
            copyChannel(sourceChannel, destChannel, eocd.centralDirectoryOffset, eocd.centralDirectorySize);

            // 6. 새로 추가된 파일에 대한 Central Directory File Header를 임시 파일에 추가합니다.
            ByteBuffer cdHeader = createCentralDirectoryHeader(fileNameBytes, compressedData.length, fileBytes.length, crcValue, dosTime, newEntryOffset);
            destChannel.write(cdHeader);

            // 7. 업데이트된 정보로 새로운 EOCD 레코드를 작성합니다.
            ByteBuffer newEocd = createNewEOCD(
                    eocd.totalEntries + 1,
                    newCentralDirectoryOffset,
                    eocd.centralDirectorySize + cdHeader.capacity()
            );
            destChannel.write(newEocd);

        } catch (IOException e) {
            Files.deleteIfExists(tempZipPath);
            throw e;
        }

        // 8. 모든 작업이 성공하면, 원본 파일을 임시 파일로 원자적으로 교체합니다.
        Files.move(tempZipPath, originalZipPath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    }

    /**
     * [신규] FileChannel의 내용을 안정적으로 복사하는 헬퍼 메소드.
     */
    private static void copyChannel(FileChannel source, FileChannel dest, long startOffset, long length) throws IOException {
        source.position(startOffset);
        long bytesRemaining = length;
        ByteBuffer buffer = ByteBuffer.allocate(8192); // 8KB buffer

        while (bytesRemaining > 0) {
            buffer.clear();
            // 버퍼 크기와 남은 바이트 수 중 작은 값만큼 리밋 설정
            int bytesToRead = (int) Math.min(bytesRemaining, buffer.capacity());
            buffer.limit(bytesToRead);

            int bytesRead = source.read(buffer);
            if (bytesRead == -1) {
                break; // 소스 파일의 끝에 도달
            }

            buffer.flip();
            dest.write(buffer);
            bytesRemaining -= bytesRead;
        }
    }

    /**
     * 파일 끝에서부터 EOCD 시그니처를 찾아 레코드를 파싱합니다.
     */
    private static EOCDRecord findAndParseEOCD(RandomAccessFile raf) throws IOException {
        long fileSize = raf.length();
        long scanStartPos = fileSize - 22;
        if (scanStartPos < 0) return null;

        long maxScanSize = Math.min(fileSize, 65535 + 22);
        long searchBoundary = fileSize - maxScanSize;
        if (searchBoundary < 0) searchBoundary = 0;

        for (long pos = scanStartPos; pos >= searchBoundary; pos--) {
            raf.seek(pos);
            // RandomAccessFile.readInt()는 Big Endian으로 읽으므로,
            // Little Endian인 ZIP 시그니처와 비교하려면 값을 뒤집어주어야 합니다.
            if (raf.readInt() == Integer.reverseBytes(END_OF_CENTRAL_DIRECTORY_SIGNATURE)) {
                raf.seek(pos);
                ByteBuffer buffer = ByteBuffer.allocate(22);
                buffer.order(ByteOrder.LITTLE_ENDIAN);
                raf.getChannel().read(buffer);
                buffer.flip();

                buffer.getInt(); // 시그니처
                buffer.getShort(); // 디스크 번호
                buffer.getShort(); // CD 시작 디스크
                buffer.getShort(); // 이 디스크의 엔트리 수

                EOCDRecord record = new EOCDRecord();
                record.totalEntries = Short.toUnsignedInt(buffer.getShort());
                record.centralDirectorySize = Integer.toUnsignedLong(buffer.getInt());
                record.centralDirectoryOffset = Integer.toUnsignedLong(buffer.getInt());

                return record;
            }
        }
        return null;
    }

    private static ByteBuffer createLocalFileHeader(byte[] fileNameBytes, int compressedSize, int uncompressedSize, long crc32, long dosTime) {
        ByteBuffer buffer = ByteBuffer.allocate(30 + fileNameBytes.length);
        buffer.order(ByteOrder.LITTLE_ENDIAN);
        buffer.putInt(LOCAL_FILE_HEADER_SIGNATURE);
        buffer.putShort((short) 20);
        buffer.putShort((short) 0);
        buffer.putShort((short) 8);
        buffer.putInt((int) dosTime);
        buffer.putInt((int) crc32);
        buffer.putInt(compressedSize);
        buffer.putInt(uncompressedSize);
        buffer.putShort((short) fileNameBytes.length);
        buffer.putShort((short) 0);
        buffer.put(fileNameBytes);
        buffer.flip();
        return buffer;
    }

    private static ByteBuffer createCentralDirectoryHeader(byte[] fileNameBytes, int compressedSize, int uncompressedSize, long crc32, long dosTime, long localHeaderOffset) {
        ByteBuffer buffer = ByteBuffer.allocate(46 + fileNameBytes.length);
        buffer.order(ByteOrder.LITTLE_ENDIAN);
        buffer.putInt(CENTRAL_DIRECTORY_FILE_HEADER_SIGNATURE);
        buffer.putShort((short) 20);
        buffer.putShort((short) 20);
        buffer.putShort((short) 0);
        buffer.putShort((short) 8);
        buffer.putInt((int) dosTime);
        buffer.putInt((int) crc32);
        buffer.putInt(compressedSize);
        buffer.putInt(uncompressedSize);
        buffer.putShort((short) fileNameBytes.length);
        buffer.putShort((short) 0);
        buffer.putShort((short) 0);
        buffer.putShort((short) 0);
        buffer.putShort((short) 0);
        buffer.putInt(0);
        buffer.putInt((int) localHeaderOffset);
        buffer.put(fileNameBytes);
        buffer.flip();
        return buffer;
    }

    private static ByteBuffer createNewEOCD(int totalEntries, long cdOffset, long cdSize) {
        ByteBuffer buffer = ByteBuffer.allocate(22);
        buffer.order(ByteOrder.LITTLE_ENDIAN);
        buffer.putInt(END_OF_CENTRAL_DIRECTORY_SIGNATURE);
        buffer.putShort((short) 0);
        buffer.putShort((short) 0);
        buffer.putShort((short) totalEntries);
        buffer.putShort((short) totalEntries);
        buffer.putInt((int) cdSize);
        buffer.putInt((int) cdOffset);
        buffer.putShort((short) 0);
        buffer.flip();
        return buffer;
    }

    private static byte[] compress(byte[] data) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        Deflater deflater = new Deflater(Deflater.DEFAULT_COMPRESSION, true);
        try (java.util.zip.DeflaterOutputStream dos = new java.util.zip.DeflaterOutputStream(baos, deflater)) {
            dos.write(data);
        }
        deflater.end();
        return baos.toByteArray();
    }

    /**
     * [수정] Java LocalDateTime을 MS-DOS 시간/날짜 형식으로 변환합니다.
     */
    private static long toDosTime(LocalDateTime time) {
        int year = time.getYear();
        if (year < 1980) year = 1980;

        return ((long)(year - 1980) << 25) |
                ((long)time.getMonthValue() << 21) |
                ((long)time.getDayOfMonth() << 16) |
                ((long)time.getHour() << 11) |
                ((long)time.getMinute() << 5) |
                ((long)time.getSecond() >> 1);
    }

    private static void createDummyZip(Path path) throws IOException {
        if (Files.exists(path)) return;
        ByteBuffer emptyZip = createNewEOCD(0, 0, 0);
        try (FileChannel channel = FileChannel.open(path, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
            channel.write(emptyZip);
        }
    }
}
//센트럴 찾아내기. 제대로 나오는지 확인하기. 그걸로 만들어보기.
