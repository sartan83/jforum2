package net.jforum.web;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/** Legacy "message" page for denied access (e.g. restricted forum). */
@ResponseStatus(HttpStatus.FORBIDDEN)
public class ForbiddenException extends RuntimeException {

    public ForbiddenException(String messageKey) {
        super(messageKey);
    }
}
