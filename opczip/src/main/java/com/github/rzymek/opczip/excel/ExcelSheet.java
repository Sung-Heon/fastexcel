package com.github.rzymek.opczip.excel;

import java.util.*;

/**
 * Excel 시트의 데이터를 표현하는 클래스입니다.
 * 각 시트는 이름과 셀 데이터를 가지며, 멀티스레드 압축을 위해 독립적으로 처리될 수 있습니다.
 */
public class ExcelSheet {
    private final String sheetName;
    private final Map<String, Object> cells; // "A1" -> value 형태로 저장
    private final int maxRow;
    private final int maxColumn;
    
    /**
     * 새로운 Excel 시트를 생성합니다.
     * 
     * @param sheetName 시트 이름
     */
    public ExcelSheet(String sheetName) {
        if (sheetName == null || sheetName.trim().isEmpty()) {
            throw new IllegalArgumentException("Sheet name cannot be null or empty");
        }
        this.sheetName = sheetName.trim();
        this.cells = new HashMap<>();
        this.maxRow = 0;
        this.maxColumn = 0;
    }
    
    /**
     * 셀에 값을 설정합니다.
     * 
     * @param cellReference 셀 참조 (예: "A1", "B2")
     * @param value 셀 값
     */
    public void setCellValue(String cellReference, Object value) {
        if (cellReference == null || cellReference.trim().isEmpty()) {
            throw new IllegalArgumentException("Cell reference cannot be null or empty");
        }
        
        validateCellReference(cellReference);
        cells.put(cellReference.toUpperCase(), value);
    }
    
    /**
     * 행과 열 인덱스로 셀에 값을 설정합니다.
     * 
     * @param row 행 인덱스 (0부터 시작)
     * @param column 열 인덱스 (0부터 시작)
     * @param value 셀 값
     */
    public void setCellValue(int row, int column, Object value) {
        if (row < 0 || column < 0) {
            throw new IllegalArgumentException("Row and column indices must be non-negative");
        }
        
        String cellReference = columnIndexToLetter(column) + (row + 1);
        setCellValue(cellReference, value);
    }
    
    /**
     * 셀 값을 가져옵니다.
     * 
     * @param cellReference 셀 참조
     * @return 셀 값 (없으면 null)
     */
    public Object getCellValue(String cellReference) {
        if (cellReference == null) {
            return null;
        }
        return cells.get(cellReference.toUpperCase());
    }
    
    /**
     * 시트 이름을 반환합니다.
     * 
     * @return 시트 이름
     */
    public String getSheetName() {
        return sheetName;
    }
    
    /**
     * 모든 셀 데이터를 반환합니다.
     * 
     * @return 셀 데이터 맵 (읽기 전용)
     */
    public Map<String, Object> getCells() {
        return Collections.unmodifiableMap(cells);
    }
    
    /**
     * 시트가 비어있는지 확인합니다.
     * 
     * @return 비어있으면 true
     */
    public boolean isEmpty() {
        return cells.isEmpty();
    }
    
    /**
     * 셀 개수를 반환합니다.
     * 
     * @return 셀 개수
     */
    public int getCellCount() {
        return cells.size();
    }
    
    /**
     * 사용된 최대 행 번호를 반환합니다 (1부터 시작).
     * 
     * @return 최대 행 번호
     */
    public int getMaxRow() {
        if (cells.isEmpty()) {
            return 0;
        }
        
        int maxRow = 0;
        for (String cellRef : cells.keySet()) {
            int row = extractRowNumber(cellRef);
            maxRow = Math.max(maxRow, row);
        }
        return maxRow;
    }
    
    /**
     * 사용된 최대 열 번호를 반환합니다 (1부터 시작).
     * 
     * @return 최대 열 번호
     */
    public int getMaxColumn() {
        if (cells.isEmpty()) {
            return 0;
        }
        
        int maxColumn = 0;
        for (String cellRef : cells.keySet()) {
            int column = extractColumnNumber(cellRef);
            maxColumn = Math.max(maxColumn, column);
        }
        return maxColumn;
    }
    
    /**
     * 셀 참조의 유효성을 검증합니다.
     * 
     * @param cellReference 셀 참조
     */
    private void validateCellReference(String cellReference) {
        String ref = cellReference.toUpperCase().trim();
        
        if (!ref.matches("^[A-Z]+[1-9][0-9]*$")) {
            throw new IllegalArgumentException("Invalid cell reference format: " + cellReference);
        }
        
        // 최대 열 수 제한 (Excel의 XFD = 16384)
        int column = extractColumnNumber(ref);
        if (column > 16384) {
            throw new IllegalArgumentException("Column number exceeds Excel limit: " + cellReference);
        }
        
        // 최대 행 수 제한 (Excel의 1048576)
        int row = extractRowNumber(ref);
        if (row > 1048576) {
            throw new IllegalArgumentException("Row number exceeds Excel limit: " + cellReference);
        }
    }
    
    /**
     * 셀 참조에서 행 번호를 추출합니다.
     * 
     * @param cellReference 셀 참조 (예: "A1")
     * @return 행 번호 (1부터 시작)
     */
    private int extractRowNumber(String cellReference) {
        String ref = cellReference.toUpperCase();
        StringBuilder rowStr = new StringBuilder();
        
        for (int i = 0; i < ref.length(); i++) {
            char c = ref.charAt(i);
            if (Character.isDigit(c)) {
                rowStr.append(c);
            }
        }
        
        return Integer.parseInt(rowStr.toString());
    }
    
    /**
     * 셀 참조에서 열 번호를 추출합니다.
     * 
     * @param cellReference 셀 참조 (예: "A1")
     * @return 열 번호 (1부터 시작)
     */
    private int extractColumnNumber(String cellReference) {
        String ref = cellReference.toUpperCase();
        StringBuilder columnStr = new StringBuilder();
        
        for (int i = 0; i < ref.length(); i++) {
            char c = ref.charAt(i);
            if (Character.isLetter(c)) {
                columnStr.append(c);
            } else {
                break;
            }
        }
        
        return columnLetterToIndex(columnStr.toString()) + 1;
    }
    
    /**
     * 열 인덱스를 Excel 열 문자로 변환합니다.
     * 
     * @param columnIndex 열 인덱스 (0부터 시작)
     * @return 열 문자 (예: "A", "B", "AA")
     */
    private String columnIndexToLetter(int columnIndex) {
        StringBuilder result = new StringBuilder();
        
        while (columnIndex >= 0) {
            result.insert(0, (char) ('A' + (columnIndex % 26)));
            columnIndex = columnIndex / 26 - 1;
        }
        
        return result.toString();
    }
    
    /**
     * Excel 열 문자를 인덱스로 변환합니다.
     * 
     * @param columnLetter 열 문자 (예: "A", "B", "AA")
     * @return 열 인덱스 (0부터 시작)
     */
    private int columnLetterToIndex(String columnLetter) {
        int result = 0;
        
        for (int i = 0; i < columnLetter.length(); i++) {
            result = result * 26 + (columnLetter.charAt(i) - 'A' + 1);
        }
        
        return result - 1;
    }
    
    @Override
    public String toString() {
        return String.format("ExcelSheet{name='%s', cells=%d, maxRow=%d, maxColumn=%d}", 
                           sheetName, cells.size(), getMaxRow(), getMaxColumn());
    }
}