package auxiliar;

public class FlowEntry {
	private PortNumber outputPort;
	private long bytes;
	private String id;
	private double lastUse;
	private double since;

	public FlowEntry() {
		outputPort = null;
		bytes = 0;
	}

	public FlowEntry(String id, PortNumber outputPort, long bytes, double lastUse, double since) {
		this.outputPort = outputPort;
		this.bytes = bytes;
		this.id = id;
		this.lastUse = lastUse;
		this.since = since;
	}

	public PortNumber getOutputPort() {
		return outputPort;
	}

	public void setOutputPort(PortNumber outputPort) {
		this.outputPort = outputPort;
	}

	public long getBytes() {
		return bytes;
	}

	public void setBytes(long bytes) {
		this.bytes = bytes;
	}

	// This method is an alias of getBytes()
	public long bytes() {
		return getBytes();
	}

	public String getId() {
		return id;
	}

	public void setId(String id) {
		this.id = id;
	}

	public double getSince() {
		return since;
	}

	public void setSince(double life) {
		this.since = life;
	}

	public double life(double current) {
		return current - since;
	}

	@Override
	public boolean equals(Object obj) {
		if (obj instanceof FlowEntry) {
			return ((FlowEntry) obj).id.equals(this.id);
		}
		if (obj instanceof String) {
			return ((String) obj).equals(this.id);
		}
		return false;
	}

	@Override
	public int hashCode() {
		return id.hashCode();
	}

	public double getLastUse() {
		return lastUse;
	}

	public void setLastUse(double lastUse) {
		this.lastUse = lastUse;
	}

	@Override
	public String toString() {
		return id;
	}

}
