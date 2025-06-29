package com.github.rzymek.opczip;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * ZIP 파일들의 바이트코드를 비교하여 압축된 내용물의 일치도를 분석합니다.
 */
public class ZipByteComparer {
    
    public static void main(String[] args) {
        try {
            // 개별 시트 파일들
            List<String> sheetFiles = Arrays.asList(
                "sheet1-compressed.zip",
                "sheet2-compressed.zip",
                "sheet3-compressed.zip"
            );
            
            // 비교 대상 파일
            String referenceFile = "ThreeSheets-complete.xlsx";
            
            // 파일 존재 여부 확인
            File refFile = new File(referenceFile);
            if (!refFile.exists()) {
                System.out.println(referenceFile + " 파일이 존재하지 않습니다.");
                System.out.println("ExcelCreator를 먼저 실행해야 합니다.");
                return;
            }
            
            System.out.println("ZIP 파일의 압축된 내용물 비교 분석을 시작합니다.\n");
            
            // ThreeSheets-complete.xlsx 파일에서 시트 XML 추출
            byte[][] excelSheetBytes = new byte[3][];
            try {
                excelSheetBytes = extractSheetsFromExcel(referenceFile);
                System.out.println("Excel 파일에서 " + countNonNull(excelSheetBytes) + "개의 시트를 추출했습니다.\n");
            } catch (Exception e) {
                System.out.println("Excel 파일에서 시트 추출 중 오류 발생: " + e.getMessage());
                e.printStackTrace();
            }
            
            // 각 시트 파일과 Excel 파일의 시트 부분 비교
            for (int i = 0; i < sheetFiles.size(); i++) {
                String sheetFile = sheetFiles.get(i);
                File sf = new File(sheetFile);
                if (!sf.exists()) {
                    System.out.println(sheetFile + " 파일이 존재하지 않습니다.");
                    continue;
                }
                
                System.out.println("\n==========================================");
                System.out.println("파일 1: " + sheetFile + " (크기: " + formatFileSize(sf.length()) + ")");
                System.out.println("파일 2: " + referenceFile + " (크기: " + formatFileSize(refFile.length()) + ")");
                
                // 개별 압축 파일에서 시트 데이터 추출
                byte[] sheetCompressedData = extractCompressedDataFromPartialZip(sheetFile);
                
                if (sheetCompressedData != null) {
                    System.out.println("\n시트 파일에서 압축된 데이터 추출: " + formatFileSize(sheetCompressedData.length));
                    
                    // Excel 파일의 해당 시트와 비교 (인덱스가 일치한다고 가정)
                    if (i < excelSheetBytes.length && excelSheetBytes[i] != null) {
                        byte[] excelSheetData = excelSheetBytes[i];
                        System.out.println("Excel 파일의 시트 " + (i+1) + " 데이터 크기: " + formatFileSize(excelSheetData.length));
                        
                        // 두 압축 데이터 비교
                        compareCompressedData(sheetCompressedData, excelSheetData);
                    } else {
                        System.out.println("Excel 파일에서 시트 " + (i+1) + "를 찾을 수 없습니다.");
                    }
                } else {
                    System.out.println("\n시트 파일에서 압축된 데이터를 추출할 수 없습니다.");
                }
                
                System.out.println("==========================================");
            }
            
        } catch (IOException e) {
            System.err.println("파일 분석 중 오류 발생: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * 비완성 ZIP 파일(헤더만 있고 중앙 디렉터리 없는)에서 압축된 데이터를 추출합니다.
     */
    private static byte[] extractCompressedDataFromPartialZip(String zipFile) throws IOException {
        File file = new File(zipFile);
        byte[] allBytes = Files.readAllBytes(file.toPath());
        
        // ZIP 로컬 파일 헤더 시그니처 찾기
        byte[] localHeaderSignature = {0x50, 0x4B, 0x03, 0x04}; // "PK\003\004"
        int headerPos = findSignature(allBytes, localHeaderSignature);
        
        if (headerPos < 0) {
            System.out.println("ZIP 로컬 파일 헤더를 찾을 수 없습니다.");
            return null;
        }
        
        // 파일명 길이 (오프셋 26-27, 2바이트)
        int filenameLength = ((allBytes[headerPos + 26] & 0xFF) | 
                             ((allBytes[headerPos + 27] & 0xFF) << 8));
        
        // 추가 필드 길이 (오프셋 28-29, 2바이트)
        int extraFieldLength = ((allBytes[headerPos + 28] & 0xFF) | 
                               ((allBytes[headerPos + 29] & 0xFF) << 8));
        
        // 압축된 크기 (오프셋 18-21, 4바이트)
        int compressedSize = ((allBytes[headerPos + 18] & 0xFF) | 
                             ((allBytes[headerPos + 19] & 0xFF) << 8) | 
                             ((allBytes[headerPos + 20] & 0xFF) << 16) | 
                             ((allBytes[headerPos + 21] & 0xFF) << 24));
        
        // 압축된 데이터 시작 위치
        int dataStart = headerPos + 30 + filenameLength + extraFieldLength;
        
        // 데이터 추출
        if (dataStart + compressedSize > allBytes.length) {
            System.out.println("압축 데이터가 파일 범위를 초과합니다.");
            return null;
        }
        
        // 파일명 추출 (디버깅용)
        String filename = "";
        if (headerPos + 30 + filenameLength <= allBytes.length) {
            byte[] filenameBytes = Arrays.copyOfRange(allBytes, headerPos + 30, headerPos + 30 + filenameLength);
            filename = new String(filenameBytes, java.nio.charset.StandardCharsets.UTF_8);
            System.out.println("파일명: " + filename);
        }
        
        System.out.println("압축 데이터 위치: " + dataStart + ", 크기: " + compressedSize);
        
        return Arrays.copyOfRange(allBytes, dataStart, dataStart + compressedSize);
    }
    
    /**
     * Excel 파일에서 각 시트 XML의 압축된 데이터를 추출합니다.
     * 
     * @return 3개의 시트에 해당하는 압축된 데이터 배열
     */
    private static byte[][] extractSheetsFromExcel(String excelFile) throws IOException {
        byte[][] sheetData = new byte[3][];
        
        try (ZipFile zipFile = new ZipFile(new File(excelFile))) {
            Enumeration<? extends ZipEntry> entries = zipFile.entries();
            
            System.out.println("Excel 파일 내 ZIP 항목들:");
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                String name = entry.getName();
                System.out.println(" - " + name + " (" + formatFileSize(entry.getCompressedSize()) + ")");
                
                // xl/worksheets/sheet1.xml, sheet2.xml, sheet3.xml 찾기
                if (name.matches("xl/worksheets/sheet[1-3]\\.xml")) {
                    int sheetNum = Integer.parseInt(name.substring(name.length() - 5, name.length() - 4)) - 1;
                    
                    try (InputStream is = zipFile.getInputStream(entry)) {
                        byte[] entryData = is.readAllBytes();
                        
                        // 압축되기 전의 XML 데이터를 다시 압축
                        byte[] recompressedData = recompressXmlData(entryData);
                        sheetData[sheetNum] = recompressedData;
                        
                        System.out.println("   * 추출 및 재압축됨: 원본 " + formatFileSize(entryData.length) + 
                                          " -> 압축 " + formatFileSize(recompressedData.length));
                    }
                }
            }
        }
        
        return sheetData;
    }
    
    /**
     * XML 데이터를 DEFLATE 알고리즘으로 재압축합니다.
     */
    private static byte[] recompressXmlData(byte[] xmlData) throws IOException {
        // 메모리 버퍼에 압축 결과를 저장
        java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
        
        // DEFLATE 압축 스트림 생성 (압축 레벨 4 = ExcelCreator에서 설정한 값)
        java.util.zip.DeflaterOutputStream deflater = 
            new java.util.zip.DeflaterOutputStream(baos, new java.util.zip.Deflater(4));
        
        // 데이터 압축
        deflater.write(xmlData);
        deflater.finish();
        deflater.close();
        
        // 압축된 바이트 배열 반환
        return baos.toByteArray();
    }
    
    /**
     * 두 압축 데이터를 비교합니다.
     */
    private static void compareCompressedData(byte[] data1, byte[] data2) {
        System.out.println("\n압축 데이터 비교 결과:");
        
        // 바이트 단위 비교
        int matchingByteCount = 0;
        int matchingSequenceStart = -1;
        int longestSequenceLength = 0;
        int currentSequenceLength = 0;
        
        int minLength = Math.min(data1.length, data2.length);
        
        for (int i = 0; i < minLength; i++) {
            if (data1[i] == data2[i]) {
                matchingByteCount++;
                currentSequenceLength++;
                
                if (currentSequenceLength > longestSequenceLength) {
                    longestSequenceLength = currentSequenceLength;
                }
            } else {
                if (currentSequenceLength > 10 && matchingSequenceStart < 0) {
                    matchingSequenceStart = i - currentSequenceLength;
                }
                currentSequenceLength = 0;
            }
        }
        
        // 일치율 계산
        double matchPercentage = (double) matchingByteCount / minLength * 100;
        System.out.printf("바이트 일치율: %.2f%% (%d/%d 바이트)\n", 
                         matchPercentage, matchingByteCount, minLength);
        
        if (matchingSequenceStart >= 0) {
            System.out.println("가장 긴 연속 일치 시작 위치: " + matchingSequenceStart + 
                             ", 길이: " + longestSequenceLength + " 바이트");
        }
        
        // 처음 다른 바이트 위치 찾기
        int firstDiffPos = -1;
        for (int i = 0; i < minLength; i++) {
            if (data1[i] != data2[i]) {
                firstDiffPos = i;
                break;
            }
        }
        
        if (firstDiffPos >= 0) {
            System.out.println("\n첫 번째 차이점:");
            System.out.printf("위치: %d (0x%04X)\n", firstDiffPos, firstDiffPos);
            System.out.printf("파일 1: 0x%02X\n", data1[firstDiffPos] & 0xFF);
            System.out.printf("파일 2: 0x%02X\n", data2[firstDiffPos] & 0xFF);
            
            // 차이점 주변의 헥스 덤프 출력 (차이점 전후 8바이트씩)
            int startDump = Math.max(0, firstDiffPos - 8);
            int endDump = Math.min(minLength, firstDiffPos + 8);
            
            System.out.println("\n차이점 주변 헥스 덤프 (위치 " + startDump + "-" + endDump + "):");
            System.out.println("파일 1:");
            printHexDump(Arrays.copyOfRange(data1, startDump, endDump), endDump - startDump);
            System.out.println("파일 2:");
            printHexDump(Arrays.copyOfRange(data2, startDump, endDump), endDump - startDump);
        } else if (data1.length != data2.length) {
            System.out.println("\n모든 공통 바이트가 일치하지만 길이가 다릅니다:");
            System.out.println("파일 1: " + data1.length + " 바이트");
            System.out.println("파일 2: " + data2.length + " 바이트");
            
            // 길이 차이가 있는 경우 추가 바이트 출력
            if (data1.length > data2.length) {
                System.out.println("\n파일 1의 추가 바이트 (인덱스 " + data2.length + "-" + (data1.length - 1) + "):");
                printHexDump(Arrays.copyOfRange(data1, data2.length, data1.length), data1.length - data2.length);
            } else {
                System.out.println("\n파일 2의 추가 바이트 (인덱스 " + data1.length + "-" + (data2.length - 1) + "):");
                printHexDump(Arrays.copyOfRange(data2, data1.length, data2.length), data2.length - data1.length);
            }
        } else {
            System.out.println("\n모든 바이트가 100% 일치합니다!");
        }
    }
    
    /**
     * null이 아닌 배열 요소 개수를 계산합니다.
     */
    private static int countNonNull(Object[] array) {
        int count = 0;
        for (Object obj : array) {
            if (obj != null) count++;
        }
        return count;
    }
    
    /**
     * 주어진 바이트 배열에서 시그니처 패턴의 첫 번째 위치를 찾습니다.
     */
    private static int findSignature(byte[] data, byte[] signature) {
        mainLoop:
        for (int i = 0; i <= data.length - signature.length; i++) {
            for (int j = 0; j < signature.length; j++) {
                if (data[i + j] != signature[j]) {
                    continue mainLoop;
                }
            }
            return i; // 시그니처 발견
        }
        return -1; // 발견되지 않음
    }
    
    /**
     * 파일 크기를 읽기 쉬운 형식으로 포맷합니다.
     */
    private static String formatFileSize(long size) {
        if (size < 1024) {
            return size + " 바이트";
        } else if (size < 1024 * 1024) {
            return String.format("%.2f KB", size / 1024.0);
        } else {
            return String.format("%.2f MB", size / (1024.0 * 1024));
        }
    }
    
    /**
     * 바이트 배열의 헥사 덤프를 출력합니다.
     */
    private static void printHexDump(byte[] data, int maxBytes) {
        int bytesToShow = Math.min(data.length, maxBytes);
        for (int i = 0; i < bytesToShow; i += 16) {
            // 주소 출력
            System.out.printf("    %08X  ", i);
            
            // 헥사 값 출력
            for (int j = 0; j < 16; j++) {
                if (i + j < bytesToShow) {
                    System.out.printf("%02X ", data[i + j] & 0xFF);
                } else {
                    System.out.print("   ");
                }
                
                // 8바이트마다 추가 공백
                if (j == 7) {
                    System.out.print(" ");
                }
            }
            
            // 구분자
            System.out.print(" | ");
            
            // ASCII 출력
            for (int j = 0; j < 16; j++) {
                if (i + j < bytesToShow) {
                    byte b = data[i + j];
                    // 출력 가능한 ASCII 문자만 표시
                    if (b >= 32 && b < 127) {
                        System.out.print((char) b);
                    } else {
                        System.out.print(".");
                    }
                }
            }
            System.out.println();
        }
    }
}
