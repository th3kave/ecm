/*
Copyright (c) 2020, Kayvan Mojarrad
All rights reserved.

This source code is licensed under the BSD-style license found in the
LICENSE file in the root directory of this source tree. 
*/

package com.bitsandgates.ecm.domain;

public class OperationValidationException extends RuntimeException {

    public OperationValidationException(String message) {
        super(message);
    }
}
