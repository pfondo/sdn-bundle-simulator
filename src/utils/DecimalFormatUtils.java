package utils;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;

public class DecimalFormatUtils {
	private static DecimalFormat df2;

	private static DecimalFormat df4;

	private static DecimalFormat df4pad0;

	private static DecimalFormat df9;

	static {
		DecimalFormatSymbols sym = DecimalFormatSymbols.getInstance();
		sym.setDecimalSeparator('.');
		df2 = new DecimalFormat("#.##");
		df2.setDecimalFormatSymbols(sym);
		df4 = new DecimalFormat("#.####");
		df4.setDecimalFormatSymbols(sym);
		df4pad0 = new DecimalFormat("0.0000");
		df4pad0.setDecimalFormatSymbols(sym);
		df9 = new DecimalFormat("#.#########");
		df9.setDecimalFormatSymbols(sym);
	}

	public static DecimalFormat getDecimalFormat2() {
		return df2;
	}

	public static DecimalFormat getDecimalFormat4() {
		return df4;
	}
	
	public static DecimalFormat getDecimalFormat4Pad0() {
		return df4pad0;
	}

	public static DecimalFormat getDecimalFormat9() {
		return df9;
	}
}
