package com.qrcheckin.request;

import jakarta.persistence.Basic;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;

/**
 * 증빙 파일(진단서·예비군 확인서 사진 등). 신청 목록을 가볍게 읽도록 따로 둔다.
 * 파일은 DB 에 저장한다 → Docker 볼륨 하나로 함께 백업되고, 서버 폴더를 따로 관리할 필요가 없다.
 */
@Entity
@Table(name = "request_evidence")
public class RequestEvidence {

    /** attendance_request.id 와 같다 */
    @Id
    @Column(name = "request_id")
    private Long requestId;

    @Column(name = "file_name", nullable = false, length = 200)
    private String fileName;

    @Column(name = "content_type", nullable = false, length = 100)
    private String contentType;

    @Lob
    @Basic(fetch = FetchType.LAZY)
    @Column(nullable = false, length = 6 * 1024 * 1024)
    private byte[] data;

    protected RequestEvidence() {
    }

    public RequestEvidence(Long requestId, String fileName, String contentType, byte[] data) {
        this.requestId = requestId;
        this.fileName = fileName;
        this.contentType = contentType;
        this.data = data;
    }

    public Long getRequestId() { return requestId; }
    public String getFileName() { return fileName; }
    public String getContentType() { return contentType; }
    public byte[] getData() { return data; }
}
