package com.webpieces.http2parser.api.dto;

import com.webpieces.http2parser.api.Http2SettingsMap;

public class Http2Settings extends Http2Frame {
    public enum Parameter {
        SETTINGS_HEADER_TABLE_SIZE(0x1),
        SETTINGS_ENABLE_PUSH(0x2),
        SETTINGS_MAX_CONCURRENT_STREAMS(0x3),
        SETTINGS_INITIAL_WINDOW_SIZE(0x4),
        SETTINGS_MAX_FRAME_SIZE(0x5),
        SETTINGS_MAX_HEADER_LIST_SIZE(0x6);

        private short id;

        Parameter(int id) {
            this.id = (short) id;
        }

        public short getId() {
            return id;
        }

        public static Parameter fromId(short id) {
            switch (id) {
                case 0x1:
                    return SETTINGS_HEADER_TABLE_SIZE;
                case 0x2:
                    return SETTINGS_ENABLE_PUSH;
                case 0x3:
                    return SETTINGS_MAX_CONCURRENT_STREAMS;
                case 0x4:
                    return SETTINGS_INITIAL_WINDOW_SIZE;
                case 0x5:
                    return SETTINGS_MAX_FRAME_SIZE;
                case 0x6:
                    return SETTINGS_MAX_HEADER_LIST_SIZE;
                default:
                    return SETTINGS_HEADER_TABLE_SIZE; // TODO: throw here
            }
        }
    }
    @Override
    public Http2FrameType getFrameType() {
        return Http2FrameType.SETTINGS;
    }

    /* flags */
    private boolean ack = false; /* 0x1 */

    public boolean isAck() {
        return ack;
    }

    public void setAck(boolean ack) {
        this.ack = ack;

        // If ack, the settings must be empty
        if(ack)
            this.settings.clear();
    }



    /* payload */

    // id 16bits
    // value 32bits
    private Http2SettingsMap settings = new Http2SettingsMap();

    public void setSetting(Http2Settings.Parameter param, Long value) {
        settings.put(param, value);
    }

    public void setSettings(Http2SettingsMap settings) {
        this.settings = settings;
    }

    public Http2SettingsMap getSettings() {
        if (!ack) {
            return settings;
        } else {
            return new Http2SettingsMap();
        }
    }

    @Override
    public String toString() {
        return "Http2Settings{" +
                "ack=" + ack +
                ", settings=" + settings +
                "} " + super.toString();
    }
}
