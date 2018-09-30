package algorithm;

import java.util.HashMap;
import java.util.Map;

import auxiliar.DeviceId;
import auxiliar.FlowEntry;

public class FlowBytesHistory {

	public final double ALPHA_EWMA; // = 0.2;

	Map<DeviceId, Map<FlowEntry, Long>> flowBytesPrev;
	Map<FlowEntry, Long> flowBytesCurrent;

	// Experimental: Adding exponentially weighted moving average (EWMA)
	Map<DeviceId, Map<FlowEntry, Double>> flowBytesEWMA;

	private Map<DeviceId, Map<FlowEntry, Double>> flowBytesPredictedPrev;

	public FlowBytesHistory(double alphaEwma) {
		this.ALPHA_EWMA = alphaEwma;
		flowBytesPrev = new HashMap<DeviceId, Map<FlowEntry, Long>>();
		flowBytesEWMA = new HashMap<DeviceId, Map<FlowEntry, Double>>();
		flowBytesPredictedPrev = new HashMap<DeviceId, Map<FlowEntry, Double>>();
	}

	public double computeBytesEstimationError(DeviceId deviceId, FlowEntry fe, double bytesRealCurrent) {
		double error = 0;
		Double bytesPredictedPrev = flowBytesPredictedPrev.get(deviceId).get(fe);
		if (bytesPredictedPrev != null) {
			error = Math.abs(bytesPredictedPrev.doubleValue() - bytesRealCurrent);
		}
		return error;
	}

	/**
	 * 
	 * @param flowBytesRealCurrent
	 * @param samplingPeriod in nanoseconds
	 * @return Average per flow error in the estimation of the rate in the current
	 *         interval [Mbps]
	 */
	public double computeRateEstimationError(DeviceId deviceId, FlowEntry fe, double bytesRealCurrent,
			double samplingPeriod) {
		double error = 0;
		error = computeBytesEstimationError(deviceId, fe, bytesRealCurrent);
		error /= samplingPeriod;
		// Convert GBps to Mbps
		error = error * 8 * 1e3;
		return error;
	}

	public void initIteration(DeviceId deviceId) {
		flowBytesPrev.putIfAbsent(deviceId, new HashMap<>());
		flowBytesEWMA.putIfAbsent(deviceId, new HashMap<>());
		flowBytesPredictedPrev.putIfAbsent(deviceId, new HashMap<>());
		flowBytesCurrent = new HashMap<FlowEntry, Long>();
	}

	public long getFlowBytesEstimation(DeviceId deviceId, FlowEntry fe) {
		long currentBytesEstimation = getFlowBytesRealCurrent(deviceId, fe);
		double currentBytesEWMA = 0;
		if (flowBytesEWMA.get(deviceId).containsKey(fe)) {
			double previousEWMA = flowBytesEWMA.get(deviceId).get(fe);
			currentBytesEWMA = ALPHA_EWMA * currentBytesEstimation + (1 - ALPHA_EWMA) * previousEWMA;
		} else {
			currentBytesEWMA = currentBytesEstimation;
		}
		flowBytesEWMA.get(deviceId).put(fe, currentBytesEWMA);
		flowBytesPredictedPrev.get(deviceId).put(fe, currentBytesEWMA);
		return (long) currentBytesEWMA;
	}

	public long getFlowBytesRealCurrent(DeviceId deviceId, FlowEntry fe) {
		long bytesPrev = flowBytesPrev.get(deviceId).containsKey(fe) ? flowBytesPrev.get(deviceId).get(fe) : 0;
		flowBytesCurrent.put(fe, fe.bytes());
		return (fe.bytes() - bytesPrev) > 0 ? (fe.bytes() - bytesPrev) : fe.bytes();
	}

	public void updateFlowBytesPrev(DeviceId deviceId) {
		flowBytesPrev.put(deviceId, new HashMap<>());
		flowBytesPrev.get(deviceId).putAll(flowBytesCurrent);
	}

}
