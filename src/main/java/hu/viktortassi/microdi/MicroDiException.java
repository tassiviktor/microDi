/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package hu.viktortassi.microdi;

public class MicroDiException extends RuntimeException {

    public MicroDiException(String message, Throwable cause) {
        super(message, cause);
    }

    public MicroDiException(String message) {
        super(message);
    }

}
