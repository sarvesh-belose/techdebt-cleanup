package com.enterprise.techdebt.workspace;

public class GitException extends RuntimeException {
    private final int exitCode;
    private final String stderr;

    public GitException(String message, int exitCode, String stderr) {
        super(message + " (exit=" + exitCode + "): " + stderr);
        this.exitCode = exitCode;
        this.stderr = stderr;
    }

    public GitException(String message, Throwable cause) {
        super(message, cause);
        this.exitCode = -1;
        this.stderr = null;
    }

    public int getExitCode() {
        return exitCode;
    }

    public String getStderr() {
        return stderr;
    }
}
