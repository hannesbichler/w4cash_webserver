package w4cash.floors;

class FloorNotFoundException extends RuntimeException {

	FloorNotFoundException(Long id) {
		super("Could not find floor " + id);
	}
}
