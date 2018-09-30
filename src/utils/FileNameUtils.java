package utils;

import java.text.DecimalFormat;

import algorithm.BaseAlgorithm;
import algorithm.LowLatencyBaseAlgorithm;

public class FileNameUtils {
	public final static String FOLDER_SEPARATOR = "/";
	public final static String BASE_PATH = "results/";
	public final static String PACKETS_PATH = "packets/";
	public final static String LOW_LATENCY_INDEX_FILE_SUFIX = "_low_latency_index.txt";

	public static String generateOutputFileName(Class<? extends BaseAlgorithm> algorithm, String inputFile,
			long samplingPeriod, long flowRuleTimeout, int startBitDstIp, int endBitDstIp, long queue, double speed,
			int numPorts, Class<? extends LowLatencyBaseAlgorithm> lowLatencyAlgorithm, double alphaEwma) {
		String toReturn = "";
		DecimalFormat df = DecimalFormatUtils.getDecimalFormat4();
		toReturn += "sim_alg=" + algorithm.getSimpleName() + "_llAlg=" + lowLatencyAlgorithm.getSimpleName()
				+ "_inputFile=" + inputFile + "_speed=" + df.format(speed) + "_ports=" + numPorts + "_alpha="
				+ df.format(alphaEwma) + "_period=" + df.format(samplingPeriod / 1e9) + "_bits=" + startBitDstIp + "-"
				+ endBitDstIp + "_queue=" + df.format(queue / 1e6) + "ms";
		return toReturn;
	}
}
