package w4cash.person;

class PersonNotFoundException extends RuntimeException {

	PersonNotFoundException(String code) {
		super("Could not find person " + code);
	}
}
