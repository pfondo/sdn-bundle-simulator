package algorithm;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import java.util.TreeSet;

import auxiliar.DeviceId;
import auxiliar.FlowEntry;
import auxiliar.Logger;
import auxiliar.PortNumber;
import tfm.NetworkSimulator;

public abstract class BaseAlgorithm {

	public final static boolean DEBUG = false;

	protected final Logger log = new Logger();

	protected LowLatencyBaseAlgorithm lowLatencyAlgorithm;

	protected double portBandwidth = 1.25E9; // 1.25E9 = 1.25 GB/s = 10 Gb/s
	protected double portBytesInterface;

	protected Set<FlowEntry> previousFlowEntries;

	protected double delay;
	protected double flowRuleTimeout;

	protected double alphaEwma;

	protected NetworkSimulator networkSimulator;

	private Map<DeviceId, Map<DeviceId, List<PortNumber>>> topology = new HashMap<DeviceId, Map<DeviceId, List<PortNumber>>>();

	/**
	 * Reallocates the current set of flows in the ports of the bundle.
	 * 
	 * @param flowMap
	 *            Map which contains the current set of flows with their respective
	 *            expected number of bytes transmitted in the following interval.
	 * @param linkPorts
	 *            The set of ports of the bundle.
	 * @return The new allocation of the flows to be used during the next interval.
	 */
	protected abstract Map<FlowEntry, PortNumber> computeAllocation(Map<FlowEntry, Long> flowMap,
			Set<PortNumber> linkPorts);

	/**
	 * Computes the number of bytes available on each port of the aggregation to be
	 * transmitted on the polling interval
	 * 
	 * @param numFlows
	 *            Number of flows assigned to the port. If 0, 100 % of the bandwidth
	 *            of the port is returned.
	 * @return The number of bytes available on the port of the aggregation
	 */
	protected double getPortBytesAvailable(long numFlows) {
		double portBytesAvailable = portBytesInterface;
		return portBytesAvailable;
	}

	public Map<DeviceId, Map<DeviceId, List<PortNumber>>> getTopology() {
		return topology;
	}

	private void setTopology(int numPorts) {
		// This method sets the topology that we are working on!
		DeviceId device1 = new DeviceId(1);
		DeviceId device2 = new DeviceId(2);
		Map<DeviceId, List<PortNumber>> device1to2Map = new HashMap<DeviceId, List<PortNumber>>();
		List<PortNumber> device1to2PortList = new ArrayList<PortNumber>();

		for (int i = 0; i < numPorts; i++) {
			device1to2PortList.add(new PortNumber(i + 1));
		}

		device1to2Map.put(device2, device1to2PortList);

		topology.put(device1, device1to2Map);
	}

	public Map<DeviceId, List<PortNumber>> getEggressLinks(DeviceId deviceId) {
		return getTopology().get(deviceId);
	}

	public BaseAlgorithm() {
		// portsBytes = new HashMap<DeviceId, Map<PortNumber, Long>>();
	}

	// Must be called after instantiation
	public void init(NetworkSimulator networkSimulator) {
		this.networkSimulator = networkSimulator;
		this.lowLatencyAlgorithm = networkSimulator.getLowLatencyAlgorithm();
		this.delay = networkSimulator.getDelay();
		this.flowRuleTimeout = networkSimulator.getDelay();
		this.alphaEwma = networkSimulator.getAlphaEwma();
		setTopology(networkSimulator.getNumPorts());
		portBytesInterface = portBandwidth * delay;
	}

	public static BaseAlgorithm newInstance(Class<? extends BaseAlgorithm> algorithmType) {
		BaseAlgorithm instance = null;
		try {
			instance = algorithmType.newInstance();
		} catch (InstantiationException e) {
			e.printStackTrace();
		} catch (IllegalAccessException e) {
			e.printStackTrace();
		}
		return instance;
	}

	public Set<PortNumber> getLinkPorts(DeviceId src, DeviceId dstDevice) {
		if (dstDevice != null) {
			Set<PortNumber> aggregation = new TreeSet<PortNumber>(new Comparator<PortNumber>() {

				@Override
				public int compare(PortNumber o1, PortNumber o2) {
					return (int) Math.signum(o1.toLong() - o2.toLong());
				}

			});
			for (DeviceId deviceId : getEggressLinks(src).keySet()) {
				if (dstDevice.equals(deviceId)) {
					for (PortNumber pn : getEggressLinks(src).get(deviceId)) {
						aggregation.add(pn);
					}
				}
			}
			if (aggregation.size() > 1) {
				return aggregation;
			}
		}
		return null;
	}

	protected Set<DeviceId> getNeighbors(DeviceId deviceId) {
		return getTopology().get(deviceId).keySet();
	}

	protected void printFinalStatistics() {
		for (DeviceId deviceId : getTopology().keySet()) {
			networkSimulator.getPrintStream()
					.println("### Printing final statistics for device " + deviceId.getId() + ":");
			// flowBytesPrev.putIfAbsent(deviceId, new HashMap<>());
			// flowBytesCurrent = new HashMap<>();
			// portsBytes.putIfAbsent(deviceId, new HashMap<>());
			for (DeviceId neighbor : getNeighbors(deviceId)) {
				Set<PortNumber> linkPorts = getLinkPorts(deviceId, neighbor);
				if (linkPorts != null) {
					networkSimulator.printFinalPortStatistics(deviceId, linkPorts, portBandwidth);
				}
			}
		}
		// networkSimulator.printFinalQueueStatistics();
	}

	// In legacy: Task extends Thread.
	public void startTask() {
		FlowBytesHistory flowBytesHistory = new FlowBytesHistory(alphaEwma);
		while (!networkSimulator.isFinished()) {
			for (DeviceId deviceId : getTopology().keySet()) {
				flowBytesHistory.initIteration(deviceId);
				// portsBytes.putIfAbsent(deviceId, new HashMap<>());
				for (DeviceId neighbor : getNeighbors(deviceId)) {
					Set<PortNumber> linkPorts = getLinkPorts(deviceId, neighbor);
					if (linkPorts != null) {
						// i.e. there is an aggregate link between this two
						// switches
						Map<FlowEntry, Long> flowMap = new HashMap<FlowEntry, Long>();

						double accErrorRate = 0;
						long numFlows = 0;

						Collection<FlowEntry> flowEntries = networkSimulator.getFlowEntries(deviceId,
								previousFlowEntries, portBandwidth);

						if (flowEntries == null) {
							// Then execution has finished and we don't want to consider last interval since
							// it could be incomplete
							break;
						}

						for (FlowEntry fe : flowEntries) {
							PortNumber pn = fe.getOutputPort();
							if (!linkPorts.contains(pn)) {
								// Exclude this flow if it is not allocated to any port of the bundle
								continue;
							}

							// log.error("Device: " + device.id() + " port:
							// " + pn + " fe.id(): " + fe.id() + " bytes:" +
							// fe.bytes());

							double bytesRealCurrent = flowBytesHistory.getFlowBytesRealCurrent(deviceId, fe);
							numFlows += 1;
							accErrorRate += flowBytesHistory.computeRateEstimationError(deviceId, fe, bytesRealCurrent,
									delay);
							flowMap.put(fe, flowBytesHistory.getFlowBytesEstimation(deviceId, fe));
						}

						accErrorRate /= numFlows;

						// Remove low-latency flows from the map passed to the reallocation method
						Map<FlowEntry, Long> filteredFlowMap = new HashMap<FlowEntry, Long>(flowMap);
						Map<FlowEntry, Long> lowLatencyFlowMap = new HashMap<FlowEntry, Long>(flowMap);

						for (FlowEntry fe : flowMap.keySet()) {
							if (!fe.isLowLatency()) {
								lowLatencyFlowMap.remove(fe);
							}
							if (fe.isLowLatency() && !lowLatencyAlgorithm.mustReallocateWithAlgorithm()) {
								filteredFlowMap.remove(fe);
							}
						}

						long startTime = System.nanoTime();
						Map<FlowEntry, PortNumber> flowAllocation = computeAllocation(filteredFlowMap, linkPorts);
						long stopTime = System.nanoTime();
						long algorithmExecutionTime = stopTime - startTime;

						long numFlowMods = 0;

						// Update flows based on allocation
						Map<PortNumber, Long> numFlowsPerPort = new HashMap<PortNumber, Long>();
						for (PortNumber port : linkPorts) {
							numFlowsPerPort.put(port, (long) 0);
						}
						for (FlowEntry fe : filteredFlowMap.keySet()) {
							PortNumber oldOutputPort = fe.getOutputPort();
							numFlowsPerPort.put(oldOutputPort, numFlowsPerPort.get(oldOutputPort) + 1);

							if (flowAllocation.containsKey(fe)) {
								if (!fe.getOutputPort().equals(flowAllocation.get(fe))) {
									// The FlowEntry has been scheduled to a new port
									numFlowMods += 1;
									fe.setOutputPort(flowAllocation.get(fe));
								}
							}
						}

						// Compute allocation of low-latency flows (if applicable)
						Map<FlowEntry, PortNumber> lowLatencyFlowAllocation = lowLatencyAlgorithm
								.computeAllocationLowLatency(this, filteredFlowMap, lowLatencyFlowMap, linkPorts);
						// Update low-latency flows
						if (lowLatencyFlowAllocation != null) {
							for (FlowEntry fe : lowLatencyFlowMap.keySet()) {
								PortNumber oldOutputPort = fe.getOutputPort();
								numFlowsPerPort.put(oldOutputPort, numFlowsPerPort.get(oldOutputPort) + 1);

								if (lowLatencyFlowAllocation.containsKey(fe)) {
									networkSimulator.getPrintStream().println("Low-latency flow " + fe.getId()
											+ ": " + lowLatencyFlowAllocation.get(fe));
									if (!fe.getOutputPort().equals(lowLatencyFlowAllocation.get(fe))) {
										// The FlowEntry has been scheduled to a new port
										numFlowMods += 1;
										fe.setOutputPort(lowLatencyFlowAllocation.get(fe));
									}
								}
							}
						}

						// Print statistics of the previous interval (before modifying the flows!)
						networkSimulator.printPortStatistics(deviceId, linkPorts, numFlowsPerPort, numFlowMods,
								algorithmExecutionTime, accErrorRate, portBandwidth);

						previousFlowEntries = flowAllocation.keySet();
					}
				}

				flowBytesHistory.updateFlowBytesPrev(deviceId);
			}
		}
		/* This section is accessed when the execution has finished */
		printFinalStatistics();
	}

	/**
	 * Selects the output port to allocate a new flow between src and dst devices.
	 * Default implementation: Returns a random port.
	 * 
	 * @param src
	 *            Source device id
	 * @param dst
	 *            Destination device id
	 * @return The port where this flow must be allocated.
	 */
	public PortNumber selectOutputPort(DeviceId src, DeviceId dst) {

		PortNumber portNumber = null;
		if (dst != null) {
			Set<PortNumber> aggregation = new TreeSet<PortNumber>(new Comparator<PortNumber>() {

				@Override
				public int compare(PortNumber o1, PortNumber o2) {
					return (int) Math.signum(o1.toLong() - o2.toLong());
				}

			});
			for (PortNumber pn : getLinkPorts(src, dst)) {
				aggregation.add(pn);
			}
			int selected = (int) (aggregation.size() * Math.random());
			portNumber = (PortNumber) aggregation.toArray()[selected];
		}
		return portNumber;
	}

	/**
	 * Selects the output port to allocate a new low-latency flow between src and
	 * dst devices. Default implementation: Returns the port with the highest
	 * PortNumber.
	 * 
	 * @param src
	 *            Source device id
	 * @param dst
	 *            Destination device id
	 * @return The port where this flow must be allocated.
	 */
	public PortNumber selectOutputPortLowLatency(DeviceId src, DeviceId dstDevice) {
		PortNumber portNumber;
		portNumber = lowLatencyAlgorithm.selectOutputPortLowLatency(getLinkPorts(src, dstDevice));
		if (portNumber == null) {
			portNumber = this.selectOutputPort(src, dstDevice);
		}
		return portNumber;
	}

	public void schedule() {
		startTask();
	}

}
