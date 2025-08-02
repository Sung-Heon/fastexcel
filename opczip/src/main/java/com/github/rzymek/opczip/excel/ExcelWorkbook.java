package com.github.rzymek.opczip.excel;

import java.util.*;

/**
 * Excel 워크북을 표현하는 클래스입니다.
 * 여러 시트를 포함하며, 멀티스레드 압축을 위해 각 시트를 독립적으로 처리할 수 있습니다.
 */
public class ExcelWorkbook {
    private final List<ExcelSheet> sheets;
    private final Map<String, ExcelSheet> sheetsByName;
    
    /**
     * 새로운 Excel 워크북을 생성합니다.
     */
    public ExcelWorkbook() {
        this.sheets = new ArrayList<>();
        this.sheetsByName = new HashMap<>();
    }
    
    /**
     * 워크북에 시트를 추가합니다.
     * 
     * @param sheet 추가할 시트
     * @throws IllegalArgumentException 시트 이름이 중복되거나 null인 경우
     */
    public void addSheet(ExcelSheet sheet) {
        if (sheet == null) {
            throw new IllegalArgumentException("Sheet cannot be null");
        }
        
        String sheetName = sheet.getSheetName();
        if (sheetsByName.containsKey(sheetName)) {
            throw new IllegalArgumentException("Sheet with name '" + sheetName + "' already exists");
        }
        
        sheets.add(sheet);
        sheetsByName.put(sheetName, sheet);
    }
    
    /**
     * 새로운 시트를 생성하고 워크북에 추가합니다.
     * 
     * @param sheetName 시트 이름
     * @return 생성된 시트
     */
    public ExcelSheet createSheet(String sheetName) {
        ExcelSheet sheet = new ExcelSheet(sheetName);
        addSheet(sheet);
        return sheet;
    }
    
    /**
     * 이름으로 시트를 가져옵니다.
     * 
     * @param sheetName 시트 이름
     * @return 시트 (없으면 null)
     */
    public ExcelSheet getSheet(String sheetName) {
        return sheetsByName.get(sheetName);
    }
    
    /**
     * 인덱스로 시트를 가져옵니다.
     * 
     * @param index 시트 인덱스 (0부터 시작)
     * @return 시트
     * @throws IndexOutOfBoundsException 인덱스가 범위를 벗어난 경우
     */
    public ExcelSheet getSheet(int index) {
        return sheets.get(index);
    }
    
    /**
     * 모든 시트를 반환합니다.
     * 
     * @return 시트 목록 (읽기 전용)
     */
    public List<ExcelSheet> getSheets() {
        return Collections.unmodifiableList(sheets);
    }
    
    /**
     * 시트 개수를 반환합니다.
     * 
     * @return 시트 개수
     */
    public int getSheetCount() {
        return sheets.size();
    }
    
    /**
     * 워크북이 비어있는지 확인합니다.
     * 
     * @return 시트가 없으면 true
     */
    public boolean isEmpty() {
        return sheets.isEmpty();
    }
    
    /**
     * 시트 이름이 존재하는지 확인합니다.
     * 
     * @param sheetName 시트 이름
     * @return 존재하면 true
     */
    public boolean hasSheet(String sheetName) {
        return sheetsByName.containsKey(sheetName);
    }
    
    /**
     * 시트를 제거합니다.
     * 
     * @param sheetName 제거할 시트 이름
     * @return 제거된 시트 (없으면 null)
     */
    public ExcelSheet removeSheet(String sheetName) {
        ExcelSheet sheet = sheetsByName.remove(sheetName);
        if (sheet != null) {
            sheets.remove(sheet);
        }
        return sheet;
    }
    
    /**
     * 모든 시트를 제거합니다.
     */
    public void clear() {
        sheets.clear();
        sheetsByName.clear();
    }
    
    /**
     * 워크북의 총 셀 개수를 반환합니다.
     * 
     * @return 총 셀 개수
     */
    public int getTotalCellCount() {
        return sheets.stream().mapToInt(ExcelSheet::getCellCount).sum();
    }
    
    @Override
    public String toString() {
        return String.format("ExcelWorkbook{sheets=%d, totalCells=%d}", 
                           sheets.size(), getTotalCellCount());
    }
}