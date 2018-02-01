package tfm;

import java.text.DecimalFormat;

import utils.DecimalFormatUtils;

public class DebugMain {
	public static void main(String[] args) {
		DecimalFormat df = DecimalFormatUtils.getDecimalFormat4();
		for (double pho = 0; pho <= 1.001; pho += 0.01) {
			System.out.println(df.format(pho) + " "
					+ df.format(100 * EnergyConsumptionUtils.computeEnergyConsumption(pho)));
		}
	}
}
