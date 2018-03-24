package auxiliar;

public class Packet {
	// Time needed to transmit this packet in seconds
	private final double transmissionTime;
	private final double queueArrivalTimestamp;
	private final long bytes;
	private final boolean isLowLatency;

	public Packet(final long bytes, final double bandwidthInterface, final double queueArrivalTimestamp,
			final boolean isLowLatency) {
		this.bytes = bytes;
		// Experimental: Nanosecond precission
		transmissionTime = ((double) Math.round((bytes / bandwidthInterface) * 1e9) / 1e9);
		this.queueArrivalTimestamp = queueArrivalTimestamp;
		this.isLowLatency = isLowLatency;
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

	public boolean isLowLatency() {
		return isLowLatency;
	}
}
