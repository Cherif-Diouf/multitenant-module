package net.atos.digital.multitenant;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.lang.Nullable;
import org.springframework.web.client.HttpStatusCodeException;

import java.nio.charset.Charset;

public class BadRequestException extends HttpStatusCodeException {

    private static final long serialVersionUID = 1L;

    public BadRequestException(HttpStatus statusCode) {
        super(statusCode);
    }

    public BadRequestException(HttpStatus statusCode, String statusText) {
        super(statusCode, statusText);
    }

    public BadRequestException(HttpStatus statusCode, String statusText,
                               @Nullable byte[] responseBody, @Nullable Charset responseCharset) {
        super(statusCode, statusText, responseBody, responseCharset);
    }

    public BadRequestException(HttpStatus statusCode, String statusText,
                               @Nullable HttpHeaders responseHeaders, @Nullable byte[] responseBody,
                               @Nullable Charset responseCharset) {
        super(statusCode, statusText, responseHeaders, responseBody, responseCharset);
    }

}
