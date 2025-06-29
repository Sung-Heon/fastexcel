package com.github.rzymek.opczip;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.List;

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
            String referenceFile = "simple-compressed-unfinished.zip";
            
            // 파일 존재 여부 확인
            File refFile = new File(referenceFile);
            if (!refFile.exists()) {
                System.out.println(referenceFile + " 파일이 존재하지 않습니다.");
                System.out.println("SingleFileExample을 먼저 실행해야 합니다.");
                
                // SingleFileExample이 생성한 다른 이름의 파일이 있는지 확인
                File dir = new File(".");
                File[] files = dir.listFiles((d, name) -> name.endsWith(".zip"));
                if (files != null && files.length > 0) {
                    System.out.println("\n발견된 ZIP 파일들:");
                    for (File f : files) {
                        System.out.println(" - " + f.getName());
                    }
                    
                    if (files.length > 0) {
                        // 첫 번째 발견된 ZIP 파일을 참조 파일로 사용
                        referenceFile = files[0].getName();
                        System.out.println("\n대신 " + referenceFile + "을(를) 참조 파일로 사용합니다.");
                    }
                } else {
                    System.out.println("ZIP 파일을 찾을 수 없습니다. 먼저 ExcelCreator와 SingleFileExample을 실행해야 합니다.");
                    return;
                }
            }
            
            System.out.println("ZIP 파일의 압축된 내용물 비교 분석을 시작합니다.\n");
            
            // 각 시트 파일과 참조 파일의 압축된 내용물 비교
            for (String sheetFile : sheetFiles) {
                File sf = new File(sheetFile);
                if (!sf.exists()) {
                    System.out.println(sheetFile + " 파일이 존재하지 않습니다.");
                    continue;
                }
                
                compareCompressedContent(sheetFile, referenceFile);
            }
            
        } catch (IOException e) {
            System.err.println("파일 분석 중 오류 발생: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * 두 ZIP 파일의 압축된 내용물을 비교합니다.
     */
    private static void compareCompressedContent(String file1, String file2) throws IOException {
        File f1 = new File(file1);
        File f2 = new File(file2);
        
        byte[] bytes1 = Files.readAllBytes(f1.toPath());
        byte[] bytes2 = Files.readAllBytes(f2.toPath());
        
        // 파일 정보 출력
        System.out.println("\n==========================================");
        System.out.println("파일 1: " + file1 + " (크기: " + formatFileSize(bytes1.length) + ")");
        System.out.println("파일 2: " + file2 + " (크기: " + formatFileSize(bytes2.length) + ")");
        
        // ZIP 로컬 파일 헤더 위치 찾기
        byte[] localHeaderSignature = {0x50, 0x4B, 0x03, 0x04}; // "PK\003\004"
        
        int[] localHeaderPositions1 = findAllSignatures(bytes1, localHeaderSignature);
        if (localHeaderPositions1.length == 0) {
            System.out.println("파일 1에서 ZIP 로컬 파일 헤더를 찾을 수 없습니다.");
            return;
        }
        
        // 압축된 모든 내용물 추출 및 비교
        for (int i = 0; i < localHeaderPositions1.length; i++) {
            int headerPos = localHeaderPositions1[i];
            CompressedEntry entry = extractCompressedData(bytes1, headerPos);
            
            if (entry != null && entry.compressedData != null && entry.compressedData.length > 0) {
                System.out.println("\n엔트리 '" + entry.filename + "' 압축 데이터 분석:");
                System.out.println("  - 압축된 크기: " + formatFileSize(entry.compressedData.length));
                
                // 압축된 내용물이 파일2에 포함되어 있는지 검사
                int matchingBytes = findMaxMatchingByteSequence(entry.compressedData, bytes2);
                double matchPercentage = (double) matchingBytes / entry.compressedData.length * 100.0;
                
                System.out.printf("  - 내용물 일치도: %.2f%% (%d/%d 바이트)\n", 
                                 matchPercentage, matchingBytes, entry.compressedData.length);
                
                // 압축된 내용물의 해시코드 비교 (내용이 정확히 같은지 확인)
                int hash1 = Arrays.hashCode(entry.compressedData);
                
                // 파일2에서 해당 항목의 위치 찾기 시도
                int matchPosition = findBestMatchingPosition(entry.compressedData, bytes2);
                if (matchPosition >= 0) {
                    System.out.println("  - 일치하는 데이터 시작 위치: " + matchPosition);
                    
                    // 해당 위치의 압축 데이터 헥사 덤프 출력 (처음 16바이트만)
                    System.out.println("\n  압축된 데이터의 헥사 덤프 (처음 16바이트):");
                    printHexDump(entry.compressedData, Math.min(16, entry.compressedData.length));
                    
                    // 일치 부분을 더 정확히 분석
                    if (matchPosition + entry.compressedData.length <= bytes2.length) {
                        byte[] matchedData = Arrays.copyOfRange(bytes2, matchPosition, 
                                                              matchPosition + entry.compressedData.length);
                        int hash2 = Arrays.hashCode(matchedData);
                        
                        boolean exactMatch = hash1 == hash2;
                        System.out.println("  - 정확한 일치 여부: " + (exactMatch ? "예" : "아니오"));
                        
                        // 일치하지 않는 부분 찾기
                        if (!exactMatch) {
                            findDifferentBytes(entry.compressedData, matchedData);
                        }
                    }
                } else {
                    System.out.println("  - 일치하는 데이터 시작 위치를 찾을 수 없습니다.");
                }
            }
        }
        
        System.out.println("\n==========================================");
    }
    
    /**
     * 두 바이트 배열에서 일치하지 않는 처음 몇 바이트를 찾아 출력합니다.
     */
    private static void findDifferentBytes(byte[] data1, byte[] data2) {
        int diffCount = 0;
        int maxDiffsToShow = 3;
        
        System.out.println("\n  일치하지 않는 바이트 위치:");
        
        for (int i = 0; i < Math.min(data1.length, data2.length); i++) {
            if (data1[i] != data2[i]) {
                diffCount++;
                System.out.printf("    위치 %d: 0x%02X vs 0x%02X\n", 
                                 i, data1[i] & 0xFF, data2[i] & 0xFF);
                
                if (diffCount >= maxDiffsToShow) {
                    int remainingDiffs = countRemainingDiffs(data1, data2, i + 1);
                    System.out.println("    ... 외 " + remainingDiffs + " 개의 차이점이 더 있습니다.");
                    break;
                }
            }
        }
        
        if (diffCount == 0) {
            System.out.println("    차이점이 없습니다. 길이만 다를 수 있습니다.");
        }
    }
    
    /**
     * 주어진 인덱스 이후에 일치하지 않는 바이트 수를 계산합니다.
     */
    private static int countRemainingDiffs(byte[] data1, byte[] data2, int startIndex) {
        int diffCount = 0;
        for (int i = startIndex; i < Math.min(data1.length, data2.length); i++) {
            if (data1[i] != data2[i]) {
                diffCount++;
            }
        }
        return diffCount;
    }
    
    /**
     * ZIP 로컬 파일 헤더에서 압축된 데이터를 추출합니다.
     */
    private static CompressedEntry extractCompressedData(byte[] data, int headerPos) {
        if (headerPos < 0 || headerPos + 30 >= data.length) {
            return null;
        }
        
        // 파일명 길이 (오프셋 26-27, 2바이트)
        int filenameLength = ((data[headerPos + 26] & 0xFF) | 
                             ((data[headerPos + 27] & 0xFF) << 8));
        
        // 추가 필드 길이 (오프셋 28-29, 2바이트)
        int extraFieldLength = ((data[headerPos + 28] & 0xFF) | 
                               ((data[headerPos + 29] & 0xFF) << 8));
        
        // 압축된 크기 (오프셋 18-21, 4바이트)
        int compressedSize = ((data[headerPos + 18] & 0xFF) | 
                             ((data[headerPos + 19] & 0xFF) << 8) | 
                             ((data[headerPos + 20] & 0xFF) << 16) | 
                             ((data[headerPos + 21] & 0xFF) << 24));
        
        // 압축되지 않은 크기 (오프셋 22-25, 4바이트)
        int uncompressedSize = ((data[headerPos + 22] & 0xFF) | 
                               ((data[headerPos + 23] & 0xFF) << 8) | 
                               ((data[headerPos + 24] & 0xFF) << 16) | 
                               ((data[headerPos + 25] & 0xFF) << 24));
        
        // 파일명 추출
        int filenameStart = headerPos + 30;
        if (filenameStart + filenameLength > data.length) {
            return null;
        }
        
        byte[] filenameBytes = Arrays.copyOfRange(data, filenameStart, filenameStart + filenameLength);
        String filename = new String(filenameBytes, java.nio.charset.StandardCharsets.UTF_8);
        
        // 압축된 데이터 시작 위치
        int dataStart = filenameStart + filenameLength + extraFieldLength;
        if (dataStart + compressedSize > data.length) {
            return null;
        }
        
        // 압축된 데이터 추출
        byte[] compressedData = Arrays.copyOfRange(data, dataStart, dataStart + compressedSize);
        
        CompressedEntry entry = new CompressedEntry();
        entry.filename = filename;
        entry.compressedData = compressedData;
        entry.compressedSize = compressedSize;
        entry.uncompressedSize = uncompressedSize;
        
        return entry;
    }
    
    /**
     * 주어진 바이트 시퀀스와 가장 많이 일치하는 연속된 바이트 수를 찾습니다.
     */
    private static int findMaxMatchingByteSequence(byte[] source, byte[] target) {
        int maxMatchLength = 0;
        
        for (int i = 0; i <= target.length - source.length; i++) {
            int currentMatchLength = 0;
            for (int j = 0; j < source.length && i + j < target.length; j++) {
                if (source[j] == target[i + j]) {
                    currentMatchLength++;
                } else {
                    break; // 연속된 일치만 계산
                }
            }
            maxMatchLength = Math.max(maxMatchLength, currentMatchLength);
        }
        
        return maxMatchLength;
    }
    
    /**
     * 소스 바이트 배열이 타겟 배열 내에서 가장 잘 일치하는 위치를 찾습니다.
     */
    private static int findBestMatchingPosition(byte[] source, byte[] target) {
        int maxMatchLength = 0;
        int bestPosition = -1;
        
        // 슬라이딩 윈도우 방식으로 가장 많이 일치하는 위치 찾기
        for (int i = 0; i <= target.length - Math.min(50, source.length); i++) {
            // 최소한 50 바이트 이상 연속으로 일치해야 함
            int matchLength = countConsecutiveMatches(source, target, i);
            if (matchLength > maxMatchLength) {
                maxMatchLength = matchLength;
                bestPosition = i;
            }
        }
        
        // 일정 비율 이상 일치하는 경우에만 위치 반환
        double matchPercent = (double) maxMatchLength / source.length;
        if (matchPercent >= 0.5) { // 50% 이상 일치하면 유의미한 것으로 판단
            return bestPosition;
        }
        
        return -1;
    }
    
    /**
     * 소스 배열이 타겟 배열과 주어진 위치에서 시작하여 연속해서 일치하는 바이트 수를 계산합니다.
     */
    private static int countConsecutiveMatches(byte[] source, byte[] target, int targetOffset) {
        int matchCount = 0;
        for (int i = 0; i < source.length && targetOffset + i < target.length; i++) {
            if (source[i] == target[targetOffset + i]) {
                matchCount++;
            } else {
                break; // 연속된 일치만 계산
            }
        }
        return matchCount;
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
    
    /**
     * 압축된 데이터 엔트리를 저장하는 내부 클래스
     */
    private static class CompressedEntry {
        String filename;
        byte[] compressedData;
        int compressedSize;
        int uncompressedSize;
    }
}
