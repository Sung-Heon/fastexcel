package com.github.rzymek.opczip;

import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;

/**
 * OpcOutputStream을 사용하여 단일 파일을 압축하는 예제
 * finish() 메서드를 호출하지 않고 엔트리만 닫은 상태로 파일 작성
 */
public class SingleFileExample {
    public static void main(String[] args) throws IOException {
        // 출력 파일 이름 설정
        String outputFileName = "simple-compressed-unfinished.zip";
        
        // 압축할 내용 생성
        String content = "이것은 OpcOutputStream을 사용하여 직접 압축된 파일입니다.\n" +
                "OpcOutputStream은 ZIP64 형식으로 압축을 지원하는 저수준 API입니다.\n" +
                "이 클래스는 Excel 파일과 호환되는 ZIP 스트림을 생성합니다.";
        
        // 일반 FileOutputStream 생성
        FileOutputStream fos = new FileOutputStream(outputFileName);
        
        // OpcOutputStream 생성 (try-with-resources를 사용하지 않음)
        OpcOutputStream ops = new OpcOutputStream(fos);
        
        try {
            // 압축 레벨 설정 (선택 사항)
            ops.setLevel(9); // 최대 압축
            
            // ZIP 엔트리 추가
            ZipEntry entry = new ZipEntry("sample.txt");
            ops.putNextEntry(entry);
            
            // 데이터 쓰기
            byte[] data = content.getBytes(StandardCharsets.UTF_8);
            ops.write(data, 0, data.length);
            
            // 엔트리만 닫기 (finish 호출하지 않음)
            ops.closeEntry();
            
            // 스트림을 명시적으로 닫지 않고 flush만 수행
            // finish()를 호출하지 않음으로써 ZIP 파일은 완전하지 않은 상태로 남게 됨
            fos.flush();
            
            System.out.println("엔트리가 추가된 비완성 상태의 ZIP 파일이 생성되었습니다: " + outputFileName);
            System.out.println("참고: 이 파일은 ZIP 파일의 중앙 디렉터리와 끝 레코드가 없어 표준 ZIP 리더로는 읽을 수 없습니다.");
        } finally {
            // 리소스 누수 방지를 위해 스트림은 닫아줍니다.
            // 참고: ops.close()는 내부적으로 finish()를 호출하므로 여기서는 호출하지 않습니다.
            fos.close();
        }
    }
}
