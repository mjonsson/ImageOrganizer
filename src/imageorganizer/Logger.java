package imageorganizer;

public abstract class Logger {
	public static void error(String message) {
		if (Settings.logLevel <= 3) {
			System.err.println("[E] " + message);
		}
	}

	public static void info(String message) {
		if (Settings.logLevel <= 2) {
			System.out.println("[I] " + message);
		}
	}
	
	public static void debug(String message) {
		if (Settings.logLevel <= 1) {
			System.out.println("[D] " + message);
		}
	}

	public static void error(String format, Object... param) {
		if (Settings.logLevel <= 3) {
			System.err.format("[E] " + format + "\n", param);
		}
	}

	public static void info(String format, Object... param) {
		if (Settings.logLevel <= 2) {
			System.out.format("[I] " + format + "\n", param);
		}
	}
	
	public static void debug(String format, Object... param) {
		if (Settings.logLevel <= 1) {
			System.out.format("[D] " + format + "\n", param);
		}
	}
}
