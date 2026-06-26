package w4cash.person;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.NOT_FOUND)
class PersonNotFoundException extends RuntimeException {

	PersonNotFoundException(Long id) {
		super("Could not find person " + id);
	}
}
