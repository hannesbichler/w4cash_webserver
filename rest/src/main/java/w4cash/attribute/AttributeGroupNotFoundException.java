package w4cash.attribute;

class AttributeGroupNotFoundException extends RuntimeException {

	AttributeGroupNotFoundException(Long id) {
		super("Could not find attribute set " + id);
	}
}
