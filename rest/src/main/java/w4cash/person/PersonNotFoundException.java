package w4cash.person;

class PersonNotFoundException extends RuntimeException {

	PersonNotFoundException(Long id) {
		super("Could not find person " + id);
	}
}
