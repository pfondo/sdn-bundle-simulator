package algorithm;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;

import auxiliar.PortNumber;
import auxiliar.Queue;
import tfm.EnergyConsumptionUtils;

public class PortStatistics {
	private long time;
	private double portBandwidth;

	private PortNumber portNumber;
	private long bytes;
	private double rate; // In Mbps
	private double occupation; // In parts per unit
	private long numFlowsInterval;
	private long numPackets;
	private long numPacketsInterval;
	private long bytesInterval;
	private double lossPacketPercent;
	private double energyConsumption; // In parts per unit
	private long idleTime; // In seconds
	private double realEnergyConsumption; // In parts per unit
	private List<Double> energyConsumptions;
	private Queue queue;

	// Experimental
	private double remainingOccupation;

	public PortStatistics(String executionFile, PortNumber portNumber, long time, double portBandwidth,
			long queueSize) {
		this.time = time;
		this.portBandwidth = portBandwidth;
		this.portNumber = portNumber;
		this.bytes = 0;
		this.rate = 0;
		this.occupation = 0;
		this.remainingOccupation = 0;
		this.numFlowsInterval = 0;
		this.numPacketsInterval = 0;
		this.lossPacketPercent = 0;
		this.realEnergyConsumption = 0;
		this.bytesInterval = 0;
		this.numPackets = 0;
		this.energyConsumptions = new ArrayList<Double>();
		if (queueSize != 0) {
			this.queue = new Queue(executionFile, "port" + portNumber.toLong(), queueSize);
		} else {
			this.queue = null;
		}
	}

	public long getNumFlowsInterval() {
		return numFlowsInterval;
	}

	public void setNumFlowsInterval(long numFlowsInterval) {
		this.numFlowsInterval = numFlowsInterval;
	}

	public void addFlow() {
		this.numFlowsInterval += 1;
	}

	public void removeFlow() {
		this.numFlowsInterval -= 1;
	}

	public double getOccupation(boolean isInterval) {
		setOccupation(isInterval);
		return occupation;
	}

	private void setOccupation(boolean isInterval) {
		double portBytesAvailable = portBandwidth * (time / 1e9);
		if (isInterval) {
			occupation = (bytesInterval / portBytesAvailable) + remainingOccupation;
			// Experimental: Uncomment this line to have a non zero remaining occupation
			// remainingOccupation = Math.max(0, occupation - 1);
		} else {
			occupation = (bytes / portBytesAvailable);
		}
	}

	public double getRate(boolean isInterval) {
		setRate(isInterval);
		return rate;
	}

	private void setRate(boolean isInterval) {
		if (isInterval) {
			rate = bytesInterval * 8 / (time / 1e3);
		} else {
			rate = bytes * 8 / (time / 1e3);
		}
	}

	public long getBytes() {
		return bytes;
	}

	public void setBytes(long bytes) {
		this.bytes = bytes;
	}

	public void addBytes(long bytes) {
		this.bytes += bytes;
		this.bytesInterval += bytes;
	}

	public PortNumber getPortNumber() {
		return portNumber;
	}

	public void setPortNumber(PortNumber portNumber) {
		this.portNumber = portNumber;
	}

	public long getNumPackets() {
		return numPackets;
	}

	public void setNumPackets(long numPackets) {
		this.numPackets = numPackets;
	}

	public void addPackets(long packets) {
		this.numPackets += packets;
		this.numPacketsInterval += packets;
	}

	public Queue getQueue() {
		return queue;
	}

	public void setQueue(Queue queue) {
		this.queue = queue;
	}

	public double getLossPacketPercent(boolean isInterval) {
		setLossPacketPercent(isInterval);
		return lossPacketPercent;
	}

	public void setLossPacketPercent(boolean isInterval) {
		if (numPackets > 0) {
			lossPacketPercent = queue.getNumExceeded() * 100.0 / numPackets;
		} else {
			lossPacketPercent = 0;
		}
	}

	public double getEnergyConsumption(boolean isInterval) {
		setEnergyConsumption(isInterval);
		return energyConsumption;
	}

	public void setEnergyConsumption(boolean isInterval) {
		if (isInterval) {
			energyConsumption = EnergyConsumptionUtils.computeEnergyConsumption(getOccupation(isInterval));
		} else {
			double accEnergyConsumption = 0;
			for (double intervalEnergyConsumption : energyConsumptions) {
				accEnergyConsumption += intervalEnergyConsumption;
			}
			energyConsumption = accEnergyConsumption / energyConsumptions.size();
		}
	}

	public void setTime(long time) {
		this.time = time;
	}

	public long getNumPacketsInterval() {
		return numPacketsInterval;
	}

	public void setNumPacketsInterval(long numPacketsInterval) {
		this.numPacketsInterval = numPacketsInterval;
	}

	public long getBytesInterval() {
		return bytesInterval;
	}

	public void setBytesInterval(long bytesInterval) {
		this.bytesInterval = bytesInterval;
	}

	public void finishInterval(boolean discardPreviousIntervals, long timestamp) {
		energyConsumptions.add(getEnergyConsumption(true));
		numPacketsInterval = 0;
		bytesInterval = 0;
		numFlowsInterval = 0;
		if (discardPreviousIntervals) {
			discardPreviousIntervals(timestamp);
		}
	}

	/*
	 * IMPORTANT: This method must be called at the end of finishInterval
	 */
	private void discardPreviousIntervals(long timestamp) {
		numPackets = 0;
		bytes = 0;
		idleTime = 0;
		energyConsumptions.clear();
		queue.clean(timestamp);
	}

	public double getRealEnergyConsumption(boolean isInterval) {
		setRealEnergyConsumption(isInterval);
		return realEnergyConsumption;
	}

	public void setRealEnergyConsumption(boolean isInterval) {
		if (isInterval) {
			long idleTimeFromQueue = getIdleTimeFromQueue();
			long idleTime = getIdleTime();
			// System.out.println("[" + portNumber + "] idleTimeFromQueue=" +
			// idleTimeFromQueue + " idleTime=" + idleTime
			// + " time=" + time);
			double idleFraction = (idleTimeFromQueue - idleTime) / (double) time;
			this.realEnergyConsumption = idleFraction * 0.1 + (1 - idleFraction) * 1;
			// Update idleTime variable for next intervals
			setIdleTime(idleTimeFromQueue);
		} else {
			long idleTimeFromQueue = getIdleTimeFromQueue();
			// System.out.println("[" + portNumber + "] idleTimeFromQueue=" +
			// idleTimeFromQueue + " time=" + time);
			double idleFraction = idleTimeFromQueue / (double) time;
			this.realEnergyConsumption = idleFraction * 0.1 + (1 - idleFraction) * 1;
		}
	}

	public long getIdleTimeFromQueue() {
		return queue.getIdleTime();
	}

	public long getIdleTime() {
		return idleTime;
	}

	public void setIdleTime(long idleTime) {
		this.idleTime = idleTime;
	}

	public String toStringInterval() {
		// System.out.println(
		// "Accumulated total delay: " + (queue.getAccumulatedDelay() +
		// queue.getAccumulatedDelayLowLatency()));
		boolean isInterval = true;
		DecimalFormat df = new DecimalFormat("#.##");
		String toReturn = "";
		toReturn += "Port " + getPortNumber().toLong() + ": " + getBytesInterval() + " bytes; ";
		toReturn += df.format(getRate(isInterval)) + " Mbps; ";
		toReturn += getNumFlowsInterval() + " flows; ";
		toReturn += getNumPacketsInterval() + " packets; ";
		toReturn += "pho: " + df.format(getOccupation(isInterval) * 100.0) + "%; ";
		toReturn += "E_cModel: " + df.format(getEnergyConsumption(isInterval) * 100.0) + "%; ";
		toReturn += "E_cReal: " + df.format(getRealEnergyConsumption(isInterval) * 100.0) + "%; ";
		toReturn += "lostPackets: " + queue.getNumExceeded() + "; ";
		toReturn += "lossPacketsPercent: " + df.format(getLossPacketPercent(isInterval)) + "%";
		return toReturn;
	}

	@Override
	public String toString() {
		boolean isInterval = false;
		DecimalFormat df = new DecimalFormat("#.##");
		String toReturn = "";
		toReturn += "Port " + getPortNumber().toLong() + ": " + getBytes() + " bytes; ";
		toReturn += df.format(getRate(isInterval)) + " Mbps; ";
		toReturn += getNumPackets() + " packets; ";
		toReturn += "pho: " + df.format(getOccupation(isInterval) * 100.0) + "%; ";
		toReturn += "E_cModel: " + df.format(getEnergyConsumption(isInterval) * 100.0) + "%; ";
		toReturn += "E_cReal: " + df.format(getRealEnergyConsumption(isInterval) * 100.0) + "%; ";
		toReturn += "lostPackets: " + queue.getNumExceeded() + "; ";
		toReturn += "lossPacketsPercent: " + df.format(getLossPacketPercent(isInterval)) + "%";
		return toReturn;
	}
}
