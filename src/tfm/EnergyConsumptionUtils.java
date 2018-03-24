package tfm;

public class EnergyConsumptionUtils {
	/**
	 * Reciprocal of the average frame transmission time
	 */
	private static double MU = (10e9) / (8 * 750);
//	public final static double T_S = 2.28e-6;
	public final static double T_S = 2.88e-6;
	public final static double T_W = 4.48e-6;

	/**
	 * Computes the energy consumption (in parts per unit) of a port given its
	 * occupation (offered load)
	 * 
	 * @param pho
	 *            Port occupation (offered load) in parts per unit
	 * @return Energy consumption in parts per unit
	 */
	public static double computeEnergyConsumption(double pho) {
		/* First we have to limit pho to be in the [0, 1] interval */
		pho = Math.min(Math.max(0, pho), 1);
		return 1 - 0.9 * (1 - pho) * f(pho) / (f(pho) + MU * pho * (T_S + T_W));
	}

	private static double f(double pho) {
		return Math.exp(-MU * pho * T_S);
	}

}
