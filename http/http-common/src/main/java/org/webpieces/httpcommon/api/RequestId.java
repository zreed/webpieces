package org.webpieces.httpcommon.api;


public class RequestId {
    private Integer value;

    public RequestId(int value) {
        this.value = value;
    }

    public RequestId(String s) throws NumberFormatException {
        this.value = new Integer(s);
    }

    public Integer getValue() {
        return value;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        RequestId requestId = (RequestId) o;

        return getValue() != null ? getValue().equals(requestId.getValue()) : requestId.getValue() == null;

    }

    @Override
    public int hashCode() {
        return getValue() != null ? getValue().hashCode() : 0;
    }
}
