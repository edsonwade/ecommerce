package code.with.vanilson.productservice.except;

import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@EqualsAndHashCode(callSuper = true)
@Data
@ResponseStatus(HttpStatus.NOT_FOUND)
public class BusinessException extends RuntimeException {
    public BusinessException(String message) {
        super(message);
    }
}
