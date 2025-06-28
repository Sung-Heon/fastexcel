package com.github.rzymek.opczip;

import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;

/**
 * OpcOutputStream을 사용하여 단일 파일을 압축하는 예제
 */
public class SingleFileExample {
    public static void main(String[] args) throws IOException {
        // 출력 파일 이름 설정
        String outputFileName = "simple-compressed.zip";
        
        // 압축할 내용 생성
        String content = "이것은 OpcOutputStream을 사용하여 직접 압축된 파일입니다.\n" +
                "OpcOutputStream은 ZIP64 형식으로 압축을 지원하는 저수준 API입니다.\n" +
                "이 클래스는 Excel 파일과 호환되는 ZIP 스트림을 생성합니다.";
        
        // OpcOutputStream을 사용하여 ZIP 파일 생성
        try (FileOutputStream fos = new FileOutputStream(outputFileName);
             OpcOutputStream ops = new OpcOutputStream(fos)) {
            
            // 압축 레벨 설정 (선택 사항)
            ops.setLevel(4); // 최대 압축
            
            // ZIP 엔트리 추가
            ZipEntry entry = new ZipEntry("sample.txt");
            ops.putNextEntry(entry);
            
            // 데이터 쓰기
            byte[] data = content.getBytes(StandardCharsets.UTF_8);
            ops.write(data, 0, data.length);
            
            // 엔트리 닫기
            ops.closeEntry();
            
            // ZIP 파일 마무리 (finish는 close 메서드에서 호출되지만, 명시적으로 호출할 수도 있음)
            ops.finish();
            
            System.out.println("파일이 성공적으로 압축되었습니다: " + outputFileName);
            System.out.println("압축된 파일 내용: sample.txt");
        }
    }
}
