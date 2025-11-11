package w4cash.product;

class ProductNotFoundException extends RuntimeException {

	ProductNotFoundException(String code) {
		super("Could not find product " + code);
	}
}
