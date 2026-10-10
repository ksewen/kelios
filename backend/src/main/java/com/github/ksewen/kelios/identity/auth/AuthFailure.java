package com.github.ksewen.kelios.identity.auth;

/** Safe public error text; never contains provider payloads or credentials. */
public class AuthFailure extends RuntimeException {
    private final int status;
    public AuthFailure(int status, String message) { super(message); this.status=status; }
    public int status() { return status; }
    public static AuthFailure denied() { return new AuthFailure(403,"Access denied"); }
    public static AuthFailure invalid() { return new AuthFailure(401,"Invalid credentials or token"); }
    public static AuthFailure provider() { return new AuthFailure(503,"Identity provider unavailable or invalid"); }
}
