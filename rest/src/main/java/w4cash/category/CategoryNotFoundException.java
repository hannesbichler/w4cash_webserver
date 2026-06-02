package w4cash.category;

class CategoryNotFoundException extends RuntimeException {

	CategoryNotFoundException(Long id) {
		super("Could not find category " + id);
	}
}
