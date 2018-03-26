package utils;

import java.text.DecimalFormat;

import algorithm.ReallocateFlowsTaskSimulator;

public class FileNameUtils {
	public final static String FOLDER_SEPARATOR = "/";
	public final static String BASE_PATH = "results/";
	public final static String PACKETS_PATH = "packets/";
	public final static String LOW_LATENCY_INDEX_FILE_SUFIX = "_low_latency_index.txt";

	public static String generateOutputFileName(Class<? extends ReallocateFlowsTaskSimulator> algorithm,
			String inputFile, double samplingPeriod, double flowRuleTimeout, int startBitDstIp, int endBitDstIp,
			double queue, double speed, int numPorts) {
		String toReturn = "";
		DecimalFormat df = DecimalFormatUtils.getDecimalFormat4();
		toReturn += "sim_alg=" + algorithm.getSimpleName() + "_inputFile=" + inputFile + "_speed=" + df.format(speed)
				+ "_ports=" + numPorts + "_period=" + df.format(samplingPeriod) + "_bits=" + startBitDstIp + "-"
				+ endBitDstIp + "_queue=" + df.format(queue * 1e3) + "ms";
		return toReturn;
	}
}
