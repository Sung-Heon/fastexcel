package com.github.rzymek.opczip;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;

public class EocdParser {

    // EOCD 레코드의 고정 시그니처 (PK\x05\x06)
    private static final int EOCD_SIGNATURE = 0x06054B50;

    public static void main(String[] args) {
        // 여기에 분석하고 싶은 ZIP 파일 경로를 입력하세요.
        String zipFileName = "example.zip";
        File zipFile = new File(zipFileName);

        if (!zipFile.exists()) {
            System.err.println("오류: '" + zipFileName + "' 파일을 찾을 수 없습니다.");
            return;
        }

        try {
            System.out.println("✅ '" + zipFileName + "' 파일의 EOCD 레코드를 분석합니다.\n");
            findAndParseEocd(zipFile);
        } catch (IOException e) {
            System.err.println("파일 처리 중 오류가 발생했습니다: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * ZIP 파일에서 EOCD(End of Central Directory) 레코드를 찾아 파싱하고 출력합니다.
     *
     * @param file 분석할 ZIP 파일
     * @throws IOException 파일 읽기/탐색 중 오류 발생 시
     */
    public static void findAndParseEocd(File file) throws IOException {
        try (RandomAccessFile raf = new RandomAccessFile(file, "r")) {
            long fileLength = raf.length();
            System.out.println("파일 크기: " + fileLength + " 바이트");
            // EOCD 레코드는 최소 22바이트 크기
            if (fileLength < 22) {
                System.err.println("오류: 유효한 ZIP 파일이 아닙니다 (파일 크기가 너무 작음).");
                return;
            }

            // 파일 끝에서부터 EOCD 시그니처를 검색할 버퍼 준비
            // 주석 최대 길이(65535) + EOCD 고정 크기(22)
            int bufferSize = (int) Math.min(fileLength, 65535 + 22);
            byte[] buffer = new byte[bufferSize];

            // 파일 끝에서 버퍼 크기만큼 앞으로 이동하여 읽기
            raf.seek(fileLength - bufferSize);
            raf.readFully(buffer);
// 다긁어왔어
            // 버퍼의 뒤에서부터 EOCD 시그니처를 검색
            int eocdOffsetInBufer = -1;
            for (int i = bufferSize - 4; i >= 0; i--) {
                if (buffer[i] == 0x50 && buffer[i + 1] == 0x4B &&
                        buffer[i + 2] == 0x05 && buffer[i + 3] == 0x06) {
                    int commentLength = (buffer[i + 20] & 0xFF) | ((buffer[i + 21] & 0xFF) << 8);

                    // 주석 길이가 파일 끝까지의 거리와 일치하는지 확인
                    if (fileLength - (fileLength - bufferSize + i + 22 + commentLength) == 0) {
                        eocdOffsetInBufer = i;
                        break;
                    }
                }
            }

            if (eocdOffsetInBufer == -1) {
                System.err.println("오류: 파일에서 EOCD 시그니처 (0x06054B50)를 찾을 수 없습니다.");
                return;
            }
            // 찾은 EOCD 데이터를 파싱하기 위해 ByteBuffer로 래핑
            ByteBuffer eocdBuffer = ByteBuffer.wrap(buffer, eocdOffsetInBufer, buffer.length - eocdOffsetInBufer);
            eocdBuffer.order(ByteOrder.LITTLE_ENDIAN);

            // 실제 파일 내에서의 EOCD 레코드 시작 오프셋 계산
            long eocdStartOffsetInFile = fileLength - bufferSize + eocdOffsetInBufer;

            System.out.println("=====  ditemukan End of Central Directory (EOCD) Record =====");
            System.out.printf("📄 위치 (Offset): 0x%X (%d)\n", eocdStartOffsetInFile, eocdStartOffsetInFile);
            System.out.println("----------------------------------------------------------");

            // EOCD 필드 파싱 및 출력 (시그니처는 이미 확인했으므로 4바이트 건너뜀)
            eocdBuffer.position(eocdOffsetInBufer + 4); // 시그니처 건너뛰기

            int diskNumber = eocdBuffer.getShort() & 0xFFFF;
            int startDiskNumber = eocdBuffer.getShort() & 0xFFFF;
            int numEntriesOnDisk = eocdBuffer.getShort() & 0xFFFF;
            int totalEntries = eocdBuffer.getShort() & 0xFFFF;
            long cdSize = eocdBuffer.getInt() & 0xFFFFFFFFL;
            long cdOffset = eocdBuffer.getInt() & 0xFFFFFFFFL;
            int commentLength = eocdBuffer.getShort() & 0xFFFF;

            System.out.printf("  - 디스크 번호 (Number of this disk): %d\n", diskNumber);
            System.out.printf("  - 중앙 디렉터리 시작 디스크 (Disk where CD starts): %d\n", startDiskNumber);
            System.out.printf("  - 현재 디스크의 중앙 디렉터리 엔트리 수: %d\n", numEntriesOnDisk);
            System.out.printf("  - 전체 중앙 디렉터리 엔트리 수: %d\n", totalEntries);
            System.out.printf("  - 중앙 디렉터리 크기 (Size of CD): %d bytes\n", cdSize);
            System.out.printf("  - 중앙 디렉터리 시작 오프셋 (Offset of CD): 0x%X (%d)\n", cdOffset, cdOffset);
            System.out.printf("  - ZIP 파일 주석 길이 (Comment length): %d bytes\n", commentLength);

            if (commentLength > 0) {
                if (eocdBuffer.remaining() >= commentLength) {
                    byte[] commentBytes = new byte[commentLength];
                    eocdBuffer.get(commentBytes);
                    System.out.printf("  - 주석 (Comment): %s\n", new String(commentBytes, StandardCharsets.UTF_8));
                }
            }
            System.out.println("==========================================================");
        }
    }
}