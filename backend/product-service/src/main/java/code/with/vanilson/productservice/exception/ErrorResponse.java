package code.with.vanilson.productservice.exception;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ErrorResponse {
    private int status;
    private String error;
    private String path;

    public ErrorResponse(int status, String error, String path) {
        this.status = status;
        this.error = error;
        this.path = path;
    }
}
