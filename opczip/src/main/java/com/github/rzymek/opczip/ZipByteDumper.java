package com.github.rzymek.opczip;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.List;

/**
 * ZIP 파일의 바이트코드를 텍스트 파일로 덤프하는 유틸리티 클래스
 */
public class ZipByteDumper {

    public static void main(String[] args) {
        try {
            // 덤프할 파일 목록
            List<String> zipFiles = Arrays.asList(
                "sheet1-compressed.zip",
                "sheet2-compressed.zip",
                "sheet3-compressed.zip",
                "ThreeSheets-complete.xlsx"
            );
            
            System.out.println("ZIP 파일 바이트코드 덤프를 시작합니다.");
            
            // 각 파일 덤프
            for (String zipFile : zipFiles) {
                File file = new File(zipFile);
                if (!file.exists()) {
                    System.out.println(zipFile + " 파일이 존재하지 않습니다.");
                    continue;
                }
                
                // 파일 덤프 실행
                dumpZipFileToTxt(zipFile);
            }
            
            System.out.println("모든 파일의 바이트코드 덤프가 완료되었습니다.");
            
        } catch (IOException e) {
            System.err.println("파일 덤프 중 오류 발생: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * ZIP 파일의 바이트코드를 텍스트 파일로 덤프합니다.
     * 
     * @param zipFile ZIP 파일 경로
     * @throws IOException 파일 읽기/쓰기 오류
     */
    private static void dumpZipFileToTxt(String zipFile) throws IOException {
        File file = new File(zipFile);
        byte[] bytes = Files.readAllBytes(file.toPath());
        
        // 출력 파일명 생성
        String outputFileName = zipFile.replace(".zip", "-bytecode.txt");
        if (zipFile.equals("ThreeSheets-complete.xlsx")) {
            outputFileName = "ThreeSheets-complete-bytecode.txt";
        }
        // 파일 내용 출력
        try (PrintWriter writer = new PrintWriter(new FileOutputStream(outputFileName))) {
            // 파일 정보 헤더 작성
            writer.println("===== ZIP 파일 바이트코드 덤프 =====");
            writer.println("파일명: " + zipFile);
            writer.println("크기: " + formatFileSize(bytes.length));
            writer.println("바이트 수: " + bytes.length);
            writer.println("========================================\n");
            
            // 전체 바이트코드 덤프 - 헥사 형식으로
            writer.println("전체 바이트코드 (헥사):");
            writeHexDump(writer, bytes);
            
            // ZIP 구조 분석
            writer.println("\n\n===== ZIP 파일 구조 분석 =====");
            
            // 로컬 파일 헤더 위치 찾기
            byte[] localHeaderSignature = {0x50, 0x4B, 0x03, 0x04}; // "PK\003\004"
            int[] headerPositions = findAllSignatures(bytes, localHeaderSignature);
            
            writer.println("\n로컬 파일 헤더 위치 (" + headerPositions.length + "개 발견):");
            for (int i = 0; i < headerPositions.length; i++) {
                int pos = headerPositions[i];
                writer.println(String.format("  헤더 #%d: 위치 0x%08X (%d)", i+1, pos, pos));
                
                // 추가 헤더 정보 출력
                if (pos + 30 <= bytes.length) {
                    // 파일명 길이
                    int filenameLength = ((bytes[pos + 26] & 0xFF) | ((bytes[pos + 27] & 0xFF) << 8));
                    // 추가 필드 길이
                    int extraFieldLength = ((bytes[pos + 28] & 0xFF) | ((bytes[pos + 29] & 0xFF) << 8));
                    // 압축된 크기
                    int compressedSize = ((bytes[pos + 18] & 0xFF) | 
                                         ((bytes[pos + 19] & 0xFF) << 8) | 
                                         ((bytes[pos + 20] & 0xFF) << 16) | 
                                         ((bytes[pos + 21] & 0xFF) << 24));
                    
                    // 파일명 추출
                    String filename = "";
                    if (pos + 30 + filenameLength <= bytes.length) {
                        byte[] filenameBytes = Arrays.copyOfRange(bytes, pos + 30, pos + 30 + filenameLength);
                        filename = new String(filenameBytes, java.nio.charset.StandardCharsets.UTF_8);
                    }
                    
                    writer.println(String.format("    파일명: %s", filename));
                    writer.println(String.format("    압축된 크기: %d 바이트", compressedSize));
                    
                    // 압축된 데이터 위치 계산
                    int dataStart = pos + 30 + filenameLength + extraFieldLength;
                    if (dataStart + compressedSize <= bytes.length) {
                        writer.println(String.format("    데이터 시작 위치: 0x%08X (%d)", dataStart, dataStart));
                        writer.println(String.format("    데이터 종료 위치: 0x%08X (%d)", dataStart + compressedSize - 1, dataStart + compressedSize - 1));
                        
                        // 압축된 데이터의 첫 32바이트 덤프 (또는 더 작은 경우 전체)
                        int dumpSize = Math.min(32, compressedSize);
                        writer.println("\n    압축 데이터 첫 " + dumpSize + " 바이트:");
                        byte[] dataSample = Arrays.copyOfRange(bytes, dataStart, dataStart + dumpSize);
                        writeHexDump(writer, dataSample, 8);
                    }
                }
                writer.println();
            }
            
            // 중앙 디렉터리 위치 찾기
            byte[] centralDirSignature = {0x50, 0x4B, 0x01, 0x02}; // "PK\001\002"
            int centralDirPos = findSignature(bytes, centralDirSignature);
            
            writer.println("\n중앙 디렉터리 위치: " + (centralDirPos >= 0 ? 
                                                 String.format("0x%08X (%d)", centralDirPos, centralDirPos) : 
                                                 "없음 (미완성 ZIP 파일)"));
            
            // 중앙 디렉터리 끝 레코드 위치 찾기
            byte[] endOfCentralDirSignature = {0x50, 0x4B, 0x05, 0x06}; // "PK\005\006"
            int endOfCentralDirPos = findSignature(bytes, endOfCentralDirSignature);
            
            writer.println("\n중앙 디렉터리 끝 레코드 위치: " + (endOfCentralDirPos >= 0 ? 
                                                      String.format("0x%08X (%d)", endOfCentralDirPos, endOfCentralDirPos) : 
                                                      "없음 (미완성 ZIP 파일)"));
        }
        
        System.out.println(zipFile + " 파일의 바이트코드를 " + outputFileName + "에 덤프했습니다.");
    }
    
    /**
     * 바이트 배열의 헥사 덤프를 출력합니다.
     */
    private static void writeHexDump(PrintWriter writer, byte[] data) {
        writeHexDump(writer, data, 16); // 기본값으로 한 줄에 16바이트 출력
    }
    
    /**
     * 바이트 배열의 헥사 덤프를 출력합니다.
     * 
     * @param writer 출력할 PrintWriter
     * @param data 바이트 배열
     * @param bytesPerLine 한 줄에 출력할 바이트 수
     */
    private static void writeHexDump(PrintWriter writer, byte[] data, int bytesPerLine) {
        for (int i = 0; i < data.length; i += bytesPerLine) {
            // 주소 출력
            writer.printf("%08X  ", i);
            
            // 헥사 값 출력
            for (int j = 0; j < bytesPerLine; j++) {
                if (i + j < data.length) {
                    writer.printf("%02X ", data[i + j] & 0xFF);
                } else {
                    writer.print("   ");
                }
                
                // 8바이트마다 추가 공백
                if (j == bytesPerLine / 2 - 1) {
                    writer.print(" ");
                }
            }
            
            // 구분자
            writer.print(" | ");

            // ASCII 출력
            for (int j = 0; j < bytesPerLine; j++) {
                if (i + j < data.length) {
                    byte b = data[i + j];
                    // 출력 가능한 ASCII 문자만 표시
                    if (b >= 32 && b < 127) {
                        writer.print((char) b);
                    } else {
                        writer.print(".");
                    }
                }
            }
            writer.println();
        }
    }
    
    /**
     * 주어진 바이트 배열에서 시그니처 패턴의 모든 위치를 찾습니다.
     */
    private static int[] findAllSignatures(byte[] data, byte[] signature) {
        List<Integer> positions = new java.util.ArrayList<>();
        
        mainLoop:
        for (int i = 0; i <= data.length - signature.length; i++) {
            for (int j = 0; j < signature.length; j++) {
                if (data[i + j] != signature[j]) {
                    continue mainLoop;
                }
            }
            positions.add(i); // 시그니처 발견
        }
        
        // List<Integer>를 int[]로 변환
        int[] result = new int[positions.size()];
        for (int i = 0; i < positions.size(); i++) {
            result[i] = positions.get(i);
        }
        
        return result;
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
}
