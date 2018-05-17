package auxiliar;

public class Packet {
	// Time needed to transmit this packet in seconds
	private final double transmissionTime;
	private final double queueArrivalTimestamp;
	private final long bytes;
	private final boolean isLowLatency;

	private double initTransmissionTimestamp;

	public Packet(final long bytes, final double bandwidthInterface, final double queueArrivalTimestamp,
			final boolean isLowLatency) {
		this.bytes = bytes;
		// Experimental: Nanosecond precision
		transmissionTime = ((double) Math.round((bytes / bandwidthInterface) * 1e9) / 1e9);
		this.queueArrivalTimestamp = queueArrivalTimestamp;
		this.isLowLatency = isLowLatency;
	}

	/**
	 * [DEPRECATED]
	 * 
	 * @param timeAvailable
	 * @return
	 */
	public boolean isExpired(double timeAvailable) {
		return transmissionTime <= timeAvailable;
	}
	
	/**
	 * [UNUSED]
	 * @param currentTimestamp
	 * @return
	 */
	public boolean canInitTransmission(double currentTimestamp) {
		return currentTimestamp >= initTransmissionTimestamp;
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

	public double getInitTransmissionTimestamp() {
		return initTransmissionTimestamp;
	}

	public void setInitTransmissionTimestamp(double initTransmissionTimestamp) {
		this.initTransmissionTimestamp = initTransmissionTimestamp;
	}
}
