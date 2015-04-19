package imageorganizer;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

public abstract class Settings {
	public static Map<String, Path> sourceFolders = new HashMap<String, Path>();
	public static Path targetFolder = null;
	public static int logLevel = 2;
	public static String[] months = { "Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sept", "Oct", "Nov", "Dec" };
	public static int geocodingRetryCount = 50;
}
