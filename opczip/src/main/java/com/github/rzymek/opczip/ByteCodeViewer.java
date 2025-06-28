package com.github.rzymek.opczip;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

/**
 * 파일의 바이트코드(이진 데이터)를 확인하는 유틸리티 클래스
 */
public class ByteCodeViewer {

    public static void main(String[] args) throws IOException {
        if (args.length < 1) {
            System.out.println("사용법: ByteCodeViewer <파일경로> [최대바이트수]");
            return;
        }

        String filePath = args[0];
        int maxBytes = (args.length > 1) ? Integer.parseInt(args[1]) : 256; // 기본 256 바이트 출력
        
        // 파일 바이트 읽기
        byte[] fileBytes = Files.readAllBytes(Paths.get(filePath));
        
        // 파일 크기 출력
        System.out.println("파일 경로: " + filePath);
        System.out.println("파일 크기: " + fileBytes.length + " 바이트");
        
        // 바이트 출력 제한
        int bytesToShow = Math.min(fileBytes.length, maxBytes);
        System.out.println("처음 " + bytesToShow + " 바이트 내용:");
        
        // 헥사와 아스키 형식으로 출력 (16바이트씩)
        for (int i = 0; i < bytesToShow; i += 16) {
            // 주소 출력
            System.out.printf("%08X  ", i);
            
            // 헥사 값 출력
            for (int j = 0; j < 16; j++) {
                if (i + j < bytesToShow) {
                    System.out.printf("%02X ", fileBytes[i + j] & 0xFF);
                } else {
                    System.out.print("   ");
                }
                
                // 8바이트마다 간격 추가
                if (j == 7) {
                    System.out.print(" ");
                }
            }
            
            // 구분자
            System.out.print(" |");
            
            // 아스키 문자 출력
            for (int j = 0; j < 16; j++) {
                if (i + j < bytesToShow) {
                    byte b = fileBytes[i + j];
                    // 출력 가능한 ASCII 문자만 표시
                    if (b >= 32 && b < 127) {
                        System.out.print((char) b);
                    } else {
                        System.out.print('.');
                    }
                } else {
                    System.out.print(" ");
                }
            }
            
            System.out.println("|");
        }
        
        // ZIP 파일인 경우 시그니처 확인
        if (fileBytes.length >= 4 && fileBytes[0] == 0x50 && fileBytes[1] == 0x4B) {
            System.out.println("\nZIP 파일 시그니처 확인됨 (PK...)");
            if (fileBytes[2] == 0x03 && fileBytes[3] == 0x04) {
                System.out.println("로컬 파일 헤더 시그니처 (0x04034B50) 발견");
            } else if (fileBytes[2] == 0x01 && fileBytes[3] == 0x02) {
                System.out.println("중앙 디렉터리 헤더 시그니처 (0x02014B50) 발견");
            } else if (fileBytes[2] == 0x05 && fileBytes[3] == 0x06) {
                System.out.println("End of Central Directory 시그니처 (0x06054B50) 발견");
            }
        }
    }
}
