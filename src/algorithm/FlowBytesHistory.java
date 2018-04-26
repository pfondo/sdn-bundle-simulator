package algorithm;

import java.util.HashMap;
import java.util.Map;

import auxiliar.DeviceId;
import auxiliar.FlowEntry;

public class FlowBytesHistory {

	public static final double ALPHA_EWMA = 0.2;

	Map<DeviceId, Map<FlowEntry, Long>> flowBytesPrev;
	Map<FlowEntry, Long> flowBytesCurrent;

	// Experimental: Adding exponentially weighted moving average (EWMA)
	Map<DeviceId, Map<FlowEntry, Double>> flowBytesEWMA;

	public FlowBytesHistory() {
		flowBytesPrev = new HashMap<DeviceId, Map<FlowEntry, Long>>();
		flowBytesEWMA = new HashMap<DeviceId, Map<FlowEntry, Double>>();
	}

	public void initIteration(DeviceId deviceId) {
		flowBytesPrev.putIfAbsent(deviceId, new HashMap<>());
		flowBytesEWMA.putIfAbsent(deviceId, new HashMap<>());
		flowBytesCurrent = new HashMap<FlowEntry, Long>();
	}

	public long getFlowBytesEstimation(DeviceId deviceId, FlowEntry fe) {
		long currentBytesEstimation = getFlowBytesCurrentEstimation(deviceId, fe);
		double currentBytesEWMA = 0;
		if (flowBytesEWMA.get(deviceId).containsKey(fe)) {
			double previousEWMA = flowBytesEWMA.get(deviceId).get(fe);
			currentBytesEWMA = ALPHA_EWMA * currentBytesEstimation + (1 - ALPHA_EWMA) * previousEWMA;
		} else {
			currentBytesEWMA = currentBytesEstimation;
		}
		flowBytesEWMA.get(deviceId).put(fe, currentBytesEWMA);
		return (long) currentBytesEWMA;
	}

	public long getFlowBytesCurrentEstimation(DeviceId deviceId, FlowEntry fe) {
		long bytesPrev = flowBytesPrev.get(deviceId).containsKey(fe) ? flowBytesPrev.get(deviceId).get(fe) : 0;
		flowBytesCurrent.put(fe, fe.bytes());
		return (fe.bytes() - bytesPrev) > 0 ? (fe.bytes() - bytesPrev) : fe.bytes();
	}

	public void updateFlowBytesPrev(DeviceId deviceId) {
		flowBytesPrev.put(deviceId, new HashMap<>());
		// TODO: Recently changed. Test that it works!
		flowBytesPrev.get(deviceId).putAll(flowBytesCurrent);
	}

}
