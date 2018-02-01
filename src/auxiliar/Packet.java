package auxiliar;

public class Packet {
	// Time needed to transmit this packet in seconds
	private final double transmissionTime;
	private final double queueArrivalTimestamp;
	private final long bytes;

	public Packet(final long bytes, double bandwidthInterface, double queueArrivalTimestamp) {
		this.bytes = bytes;
		transmissionTime = bytes / bandwidthInterface;
		this.queueArrivalTimestamp = queueArrivalTimestamp;
	}

	public boolean isExpired(double timeAvailable) {
		return transmissionTime <= timeAvailable;
	}

	public double getTransmissionTime() {
		return transmissionTime;
	}

	public double getQueueArrivalTimestamp() {
		return queueArrivalTimestamp;
	}
	
	public long getBytes() {
		return bytes;
	}
}
