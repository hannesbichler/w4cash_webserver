package w4cash.places;

class PlaceNotFoundException extends RuntimeException {

	PlaceNotFoundException(Long id) {
		super("Could not find place " + id);
	}
}
