package auxiliar;

public class PortNumber {
	long portNumber;

	public PortNumber() {
		this.portNumber = -1;
	}

	public PortNumber(long portNumber) {
		this.portNumber = portNumber;
	}

	public long toLong() {
		return portNumber;
	}

	@Override
	public boolean equals(Object obj) {
		if (obj instanceof PortNumber) {
			if (((PortNumber) obj).portNumber == this.portNumber) {
				return true;
			}
		}
		return false;
	}

	@Override
	public int hashCode() {
		return ((int) portNumber % Integer.MAX_VALUE);
	}

	@Override
	public String toString() {
		return "" + portNumber;
	}
}
