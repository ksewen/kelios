package com.github.ksewen.kelios.platform;

/** Stable application failure; HTTP adapters translate codes without parsing message text. */
public final class BusinessFailure extends IllegalArgumentException {
    private final String code;
    public BusinessFailure(String code, String detail) { super(detail); this.code = code; }
    public String code() { return code; }
    public static BusinessFailure missing() { return new BusinessFailure("NOT_FOUND", "Resource not found"); }
    public static BusinessFailure invalid(String detail) { return new BusinessFailure("INVALID_INPUT", detail); }
    public static BusinessFailure conflict(String code, String detail) { return new BusinessFailure(code, detail); }
}
