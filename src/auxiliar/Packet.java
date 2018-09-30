package auxiliar;

public class Packet {
	// Time needed to transmit this packet in nanoseconds
	private final long transmissionTime;
	private final long queueArrivalTimestamp;
	private final long bytes;
	private final boolean isLowLatency;

	private long initTransmissionTimestamp;

	/**
	 * 
	 * @param bytes
	 * @param bandwidthInterface In bytes per second
	 * @param queueArrivalTimestamp
	 * @param isLowLatency
	 */
	public Packet(final long bytes, final double bandwidthInterface, final long queueArrivalTimestamp,
			final boolean isLowLatency) {
		this.bytes = bytes;
		// Experimental: Nanosecond precision
		transmissionTime = (long) Math.round((bytes / bandwidthInterface) * 1e9);
		this.queueArrivalTimestamp = queueArrivalTimestamp;
		this.isLowLatency = isLowLatency;
	}

	/**
	 * [DEPRECATED]
	 * 
	 * @param timeAvailable
	 * @return
	 */
	public boolean isExpired(long timeAvailable) {
		return transmissionTime <= timeAvailable;
	}

	/**
	 * [UNUSED]
	 * 
	 * @param currentTimestamp
	 * @return
	 */
	public boolean canInitTransmission(long currentTimestamp) {
		return currentTimestamp >= initTransmissionTimestamp;
	}

	public long getTransmissionTime() {
		return transmissionTime;
	}

	public long getQueueArrivalTimestamp() {
		return queueArrivalTimestamp;
	}

	public long getBytes() {
		return bytes;
	}

	public boolean isLowLatency() {
		return isLowLatency;
	}

	public long getInitTransmissionTimestamp() {
		return initTransmissionTimestamp;
	}

	public void setInitTransmissionTimestamp(long initTransmissionTimestamp) {
		this.initTransmissionTimestamp = initTransmissionTimestamp;
	}
}
