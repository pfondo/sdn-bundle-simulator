package algorithm;

import java.util.HashMap;
import java.util.Map;

import auxiliar.DeviceId;
import auxiliar.FlowEntry;

public class FlowBytesHistory {

	Map<DeviceId, Map<FlowEntry, Long>> flowBytesPrev;
	Map<FlowEntry, Long> flowBytesCurrent;

	public FlowBytesHistory() {
		flowBytesPrev = new HashMap<DeviceId, Map<FlowEntry, Long>>();
	}

	public void initIteration(DeviceId deviceId) {
		flowBytesPrev.putIfAbsent(deviceId, new HashMap<>());
		flowBytesCurrent = new HashMap<FlowEntry, Long>();
	}

	public long getFlowBytesEstimation(DeviceId deviceId, FlowEntry fe) {
		long bytesPrev = flowBytesPrev.get(deviceId).containsKey(fe) ? flowBytesPrev.get(deviceId).get(fe) : 0;
		flowBytesCurrent.put(fe, fe.bytes());
		return (fe.bytes() - bytesPrev) > 0 ? (fe.bytes() - bytesPrev) : fe.bytes();
	}

	public void updateFlowBytesPrev(DeviceId deviceId) {
		flowBytesPrev.put(deviceId, new HashMap<>());
		for (FlowEntry flowEntry : flowBytesCurrent.keySet()) {
			flowBytesPrev.get(deviceId).put(flowEntry, flowBytesCurrent.get(flowEntry));
		}
	}

}
