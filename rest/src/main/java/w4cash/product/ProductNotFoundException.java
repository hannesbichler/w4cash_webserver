package w4cash.product;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.NOT_FOUND)
class ProductNotFoundException extends RuntimeException {

	ProductNotFoundException(Long id) {
		super("Could not find product " + id);
	}
}
