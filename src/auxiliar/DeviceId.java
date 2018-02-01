package auxiliar;

public class DeviceId {
	private int deviceId;

	public DeviceId() {
		this.deviceId = 0;
	}

	public DeviceId(int id) {
		this.deviceId = id;
	}

	public int getId() {
		return deviceId;
	}

	public void setId(int id) {
		this.deviceId = id;
	}

	@Override
	public boolean equals(Object obj) {
		if (obj instanceof DeviceId) {
			if (((DeviceId) obj).deviceId == this.deviceId) {
				return true;
			}
		}
		return false;
	}

	@Override
	public int hashCode() {
		return deviceId;
	}

	@Override
	public String toString() {
		return "" + deviceId;
	}

}
