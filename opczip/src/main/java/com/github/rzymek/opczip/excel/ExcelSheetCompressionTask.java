package com.github.rzymek.opczip.excel;

import com.github.rzymek.opczip.CompressionException;
import com.github.rzymek.opczip.SingleFileCompressionResult;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.concurrent.Callable;
import java.util.zip.CRC32;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;

/**
 * Excel 시트를 압축하는 작업을 담당하는 클래스입니다.
 * 각 시트를 독립적으로 XML로 변환하고 압축합니다.
 */
public class ExcelSheetCompressionTask implements Callable<SingleFileCompressionResult> {
    
    private static final int LOCAL_FILE_HEADER_SIGNATURE = 0x04034b50;
    
    private final ExcelSheet sheet;
    private final String zipPath;
    private final long baseOffset;
    private final int sheetIndex;
    
    /**
     * 새로운 Excel 시트 압축 작업을 생성합니다.
     * 
     * @param sheet 압축할 Excel 시트
     * @param zipPath ZIP 파일 내 경로
     * @param baseOffset 기본 오프셋
     * @param sheetIndex 시트 인덱스
     */
    public ExcelSheetCompressionTask(ExcelSheet sheet, String zipPath, long baseOffset, int sheetIndex) {
        this.sheet = sheet;
        this.zipPath = zipPath;
        this.baseOffset = baseOffset;
        this.sheetIndex = sheetIndex;
    }
    
    @Override
    public SingleFileCompressionResult call() throws Exception {
        long startTime = System.nanoTime();
        try {
            // 1. Excel 시트를 XML로 변환
            byte[] xmlData = ExcelXmlGenerator.generateWorksheetXml(sheet);
            
            // 2. XML 데이터 압축
            byte[] compressedData = compressData(xmlData);
            
            // 3. CRC32 계산
            CRC32 crc = new CRC32();
            crc.update(xmlData);
            long crcValue = crc.getValue();
            
            // 4. 로컬 파일 헤더 생성
            byte[] localHeader = createLocalFileHeader(zipPath, xmlData.length, compressedData.length, crcValue);
            
            // 5. DOS 시간 생성
            long dosTime = toDosTime(LocalDateTime.now());
            
            // 6. 압축 시간 계산
            Duration compressionTime = Duration.ofNanos(System.nanoTime() - startTime);
            
            return new SingleFileCompressionResult(
                zipPath,
                localHeader,
                compressedData,
                crcValue,
                xmlData.length,
                compressedData.length,
                dosTime,
                baseOffset,
                compressionTime
            );
            
        } catch (IOException e) {
            throw new CompressionException(
                "Failed to compress Excel sheet: " + sheet.getSheetName(), 
                e, 
                zipPath, 
                CompressionException.CompressionStage.COMPRESSION
            );
        } catch (Exception e) {
            throw new CompressionException(
                "Unexpected error while compressing Excel sheet: " + sheet.getSheetName(), 
                e, 
                zipPath, 
                CompressionException.CompressionStage.COMPRESSION
            );
        }
    }
    
    /**
     * 데이터를 DEFLATE 알고리즘으로 압축합니다.
     * 
     * @param data 압축할 데이터
     * @return 압축된 데이터
     * @throws IOException 압축 중 오류 발생시
     */
    private byte[] compressData(byte[] data) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        Deflater deflater = new Deflater(Deflater.DEFAULT_COMPRESSION, true); // true = nowrap
        
        try (DeflaterOutputStream dos = new DeflaterOutputStream(baos, deflater)) {
            dos.write(data);
        } finally {
            deflater.end();
        }
        
        return baos.toByteArray();
    }
    
    /**
     * 로컬 파일 헤더를 생성합니다.
     * 
     * @param fileName 파일 이름
     * @param uncompressedSize 압축 전 크기
     * @param compressedSize 압축 후 크기
     * @param crcValue CRC32 값
     * @return 로컬 파일 헤더 바이트 배열
     */
    private byte[] createLocalFileHeader(String fileName, int uncompressedSize, int compressedSize, long crcValue) {
        byte[] fileNameBytes = fileName.getBytes(StandardCharsets.UTF_8);
        long dosTime = toDosTime(LocalDateTime.now());
        
        ByteBuffer buffer = ByteBuffer.allocate(30 + fileNameBytes.length);
        buffer.order(ByteOrder.LITTLE_ENDIAN);
        
        buffer.putInt(LOCAL_FILE_HEADER_SIGNATURE);  // 시그니처
        buffer.putShort((short) 20);                 // Version needed to extract
        buffer.putShort((short) 0);                  // General purpose bit flag
        buffer.putShort((short) 8);                  // Compression method (DEFLATE)
        buffer.putInt((int) dosTime);                // Last mod file time & date
        buffer.putInt((int) crcValue);               // CRC-32
        buffer.putInt(compressedSize);               // Compressed size
        buffer.putInt(uncompressedSize);             // Uncompressed size
        buffer.putShort((short) fileNameBytes.length); // File name length
        buffer.putShort((short) 0);                  // Extra field length
        buffer.put(fileNameBytes);                   // File name
        
        return buffer.array();
    }
    
    /**
     * Java LocalDateTime을 MS-DOS 시간/날짜 형식으로 변환합니다.
     * 
     * @param time 변환할 시간
     * @return DOS 시간 형식
     */
    private long toDosTime(LocalDateTime time) {
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
     * 시트 정보를 반환합니다.
     * 
     * @return 시트
     */
    public ExcelSheet getSheet() {
        return sheet;
    }
    
    /**
     * ZIP 경로를 반환합니다.
     * 
     * @return ZIP 경로
     */
    public String getZipPath() {
        return zipPath;
    }
    
    /**
     * 시트 인덱스를 반환합니다.
     * 
     * @return 시트 인덱스
     */
    public int getSheetIndex() {
        return sheetIndex;
    }
}