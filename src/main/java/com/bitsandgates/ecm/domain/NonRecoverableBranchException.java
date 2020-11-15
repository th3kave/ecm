package com.bitsandgates.ecm.domain;


public class NonRecoverableBranchException extends RuntimeException {

    public NonRecoverableBranchException(String message, Throwable cause) {
        super(message, cause);
    }

    public NonRecoverableBranchException(String message) {
        super(message);
    }

    public NonRecoverableBranchException(Throwable cause) {
        super(cause);
    }
}
