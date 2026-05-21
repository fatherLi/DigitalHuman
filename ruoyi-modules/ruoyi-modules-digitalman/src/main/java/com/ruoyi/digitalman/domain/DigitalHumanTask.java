package com.ruoyi.digitalman.domain;

import java.io.Serializable;

public class DigitalHumanTask implements Serializable {
    private static final long serialVersionUID = 1L;

    private Long userId;
    private String text;
    private String mouthShape;
    private String style;
    private String msgId;

    public DigitalHumanTask() {}

    public DigitalHumanTask(Long userId, String text, String mouthShape, String style) {
        this.userId = userId;
        this.text = text;
        this.mouthShape = mouthShape;
        this.style = style;
    }

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getText() { return text; }
    public void setText(String text) { this.text = text; }
    public String getMouthShape() { return mouthShape; }
    public void setMouthShape(String mouthShape) { this.mouthShape = mouthShape; }
    public String getStyle() { return style; }
    public void setStyle(String style) { this.style = style; }
    public void setMsgId(String msgId) { this.msgId = msgId; }
    public String getMsgId() { return msgId; }
}