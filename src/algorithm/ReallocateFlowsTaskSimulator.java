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

public abstract class ReallocateFlowsTaskSimulator {

	public final static boolean DEBUG = false;

	protected final Logger log = new Logger();

	protected double portBandwidth = 1.25E9; // 1.25E9 = 1.25 GB/s = 10 Gb/s
	protected double portBytesInterface;

	protected Set<FlowEntry> previousFlowEntries;

	protected double delay;
	protected double flowRuleTimeout;

	protected NetworkSimulator networkSimulator;

	private Map<DeviceId, Map<DeviceId, List<PortNumber>>> topology = new HashMap<DeviceId, Map<DeviceId, List<PortNumber>>>();

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

	private void setTopology() {
		// This method sets the topology that we are working on!
		DeviceId device1 = new DeviceId(1);
		DeviceId device2 = new DeviceId(2);
		Map<DeviceId, List<PortNumber>> device1to2Map = new HashMap<DeviceId, List<PortNumber>>();
		List<PortNumber> device1to2PortList = new ArrayList<PortNumber>();
		device1to2PortList.add(new PortNumber(1));
		device1to2PortList.add(new PortNumber(2));
		device1to2PortList.add(new PortNumber(3));
		device1to2PortList.add(new PortNumber(4));
		device1to2PortList.add(new PortNumber(5));
		device1to2Map.put(device2, device1to2PortList);

		topology.put(device1, device1to2Map);
	}

	public Map<DeviceId, List<PortNumber>> getEggressLinks(DeviceId deviceId) {
		return getTopology().get(deviceId);
	}

	public ReallocateFlowsTaskSimulator() {
		setTopology();
		// portsBytes = new HashMap<DeviceId, Map<PortNumber, Long>>();
	}

	// Must be called after instantiation
	public void init(NetworkSimulator networkSimulator) {
		this.networkSimulator = networkSimulator;
		this.delay = networkSimulator.getDelay();
		this.flowRuleTimeout = networkSimulator.getDelay();
		portBytesInterface = portBandwidth * delay;
	}

	public static ReallocateFlowsTaskSimulator newInstance(
			Class<? extends ReallocateFlowsTaskSimulator> algorithmType) {
		ReallocateFlowsTaskSimulator instance = null;
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

	protected abstract Map<FlowEntry, PortNumber> computeAllocation(Map<FlowEntry, Long> flowMap,
			Set<PortNumber> linkPorts);

	// In legacy: Task extends Thread.
	class Task {
		public void start() {
			FlowBytesHistory flowBytesHistory = new FlowBytesHistory();
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

								flowMap.put(fe, flowBytesHistory.getFlowBytesEstimation(deviceId, fe));
							}

							// Remove low-latency flows from the map passed to the reallocation method
							Map<FlowEntry, Long> filteredFlowMap = new HashMap<FlowEntry, Long>(flowMap);
							for (FlowEntry fe : flowMap.keySet()) {
								if (fe.isLowLatency()) {
									filteredFlowMap.remove(fe);
								}
							}
							Map<FlowEntry, PortNumber> flowAllocation = computeAllocation(filteredFlowMap, linkPorts);

							long numFlowMods = 0;

							// Update flows based on allocation: Only need to be
							// done in simulator
							Map<PortNumber, Long> numFlowsPerPort = new HashMap<PortNumber, Long>();
							for (PortNumber port : linkPorts) {
								numFlowsPerPort.put(port, (long) 0);
							}
							for (FlowEntry fe : flowMap.keySet()) {
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

							// Print statistics of the previous interval (before modifying the flows!)
							networkSimulator.printPortStatistics(deviceId, linkPorts, numFlowsPerPort, numFlowMods,
									portBandwidth);

							previousFlowEntries = flowAllocation.keySet();
						}
					}

					flowBytesHistory.updateFlowBytesPrev(deviceId);
				}
			}
			/* This section is accessed when the execution has finished */
			printFinalStatistics();
		}

	}

	public void schedule() {
		new Task().start();
	}

}
