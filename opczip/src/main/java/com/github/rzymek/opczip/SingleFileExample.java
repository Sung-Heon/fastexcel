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
        
        // Excel 시트 XML 생성
        String sheetXml = createSimpleSheetXml();
        
        // 일반 FileOutputStream 생성
        FileOutputStream fos = new FileOutputStream(outputFileName);
        
        // OpcOutputStream 생성 (try-with-resources를 사용하지 않음)
        OpcOutputStream ops = new OpcOutputStream(fos);
        
        try {
            // 압축 레벨 설정 (선택 사항)
            ops.setLevel(9); // 최대 압축
            
            // ZIP 엔트리 추가 - Excel 시트 XML 파일
            ZipEntry entry = new ZipEntry("sheet1.xml");
            ops.putNextEntry(entry);
            
            // Excel 시트 XML 데이터 쓰기
            byte[] data = sheetXml.getBytes(StandardCharsets.UTF_8);
            ops.write(data, 0, data.length);
            
            // 엔트리만 닫기 (finish 호출하지 않음)
            ops.closeEntry();
            
            // 스트림을 명시적으로 닫지 않고 flush만 수행
            // finish()를 호출하지 않음으로써 ZIP 파일은 완전하지 않은 상태로 남게 됨
            fos.flush();
            
            System.out.println("Excel 시트 XML이 포함된 비완성 상태의 ZIP 파일이 생성되었습니다: " + outputFileName);
            System.out.println("참고: 이 파일은 ZIP 파일의 중앙 디렉터리와 끝 레코드가 없어 표준 ZIP 리더로는 읽을 수 없습니다.");
        } finally {
            // 리소스 누수 방지를 위해 스트림은 닫아줍니다.
            // 참고: ops.close()는 내부적으로 finish()를 호출하므로 여기서는 호출하지 않습니다.
            fos.close();
        }
    }
    
    /**
     * 간단한 Excel 시트 XML을 생성합니다.
     * 테이블 데이터가 포함된 기본 워크시트 구조입니다.
     */
    private static String createSimpleSheetXml() {
        StringBuilder sb = new StringBuilder();
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n")
          .append("<worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\">\n")
          .append("  <sheetData>\n")
          // 헤더 행
          .append("    <row r=\"1\">\n")
          .append("      <c r=\"A1\" t=\"inlineStr\"><is><t>이름</t></is></c>\n")
          .append("      <c r=\"B1\" t=\"inlineStr\"><is><t>나이</t></is></c>\n")
          .append("      <c r=\"C1\" t=\"inlineStr\"><is><t>직업</t></is></c>\n")
          .append("    </row>\n")
          // 데이터 행 1
          .append("    <row r=\"2\">\n")
          .append("      <c r=\"A2\" t=\"inlineStr\"><is><t>김철수</t></is></c>\n")
          .append("      <c r=\"B2\"><v>28</v></c>\n")
          .append("      <c r=\"C2\" t=\"inlineStr\"><is><t>개발자</t></is></c>\n")
          .append("    </row>\n")
          // 데이터 행 2
          .append("    <row r=\"3\">\n")
          .append("      <c r=\"A3\" t=\"inlineStr\"><is><t>이영희</t></is></c>\n")
          .append("      <c r=\"B3\"><v>32</v></c>\n")
          .append("      <c r=\"C3\" t=\"inlineStr\"><is><t>디자이너</t></is></c>\n")
          .append("    </row>\n")
          // 데이터 행 3
          .append("    <row r=\"4\">\n")
          .append("      <c r=\"A4\" t=\"inlineStr\"><is><t>박민수</t></is></c>\n")
          .append("      <c r=\"B4\"><v>45</v></c>\n")
          .append("      <c r=\"C4\" t=\"inlineStr\"><is><t>매니저</t></is></c>\n")
          .append("    </row>\n")
          .append("  </sheetData>\n")
          .append("</worksheet>");
        
        return sb.toString();
    }
}
