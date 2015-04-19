package imageorganizer;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.FileVisitResult;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.CopyOption;
import java.nio.file.FileVisitor;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.BasicParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.OptionBuilder;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.sanselan.common.IImageMetadata;
import org.apache.sanselan.formats.jpeg.JpegImageMetadata;
import org.apache.sanselan.formats.tiff.TiffField;
import org.apache.sanselan.formats.tiff.TiffImageMetadata;
import org.apache.sanselan.formats.tiff.TiffImageMetadata.GPSInfo;
import org.apache.sanselan.formats.tiff.constants.ExifTagConstants;
import org.apache.sanselan.ImageReadException;
import org.apache.sanselan.Sanselan;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;

public class ImageOrganizer {

	//	private static List<Node> nodeList = new ArrayList<Node>();
	private static String currentTitle;
	private static Path currentSource;
	private static Map<String, Node> checksums = new HashMap<String, Node>();

	private static FileVisitor<Path> findImageFiles = new SimpleFileVisitor<Path>() {
		@Override
		public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
			String fileString = file.toString().toLowerCase();
			if (attrs.isRegularFile() && 
					(fileString.endsWith("jpg") || fileString.endsWith("mp4") || fileString.endsWith("mov"))) {
				String checksum;

				if (!file.toFile().canRead()) {
					Logger.error("No access to source file \"%s\". Skipping...", file.toString());
					return FileVisitResult.CONTINUE;
				}
				try {
					checksum = createSHA1(file);
				}
				catch (IOException e) {
					Logger.error("No access to source file \"%s\". Skipping...", file.toString());
					return FileVisitResult.CONTINUE;
				}
				if (checksums.containsKey(checksum)) {
					Path otherFile = checksums.get(checksum).sourcePath;
					if (file.getFileName().toString().length() < otherFile.getFileName().toString().length()) {
						checksums.remove(checksum);
						Logger.error("Skipping duplicate file \"%s\" in source. Keeping other source file \"%s\".", otherFile.toString(), file.toString());
					}
					else {
						Logger.error("Skipping duplicate file \"%s\" in source. Keeping other source file \"%s\".", file.toString(), otherFile.toString());
						return FileVisitResult.CONTINUE;
					}
				}
				Node node = new Node(currentSource, file);
				Logger.debug("Adding file \"%s\".", file.toString());
				Logger.debug("Checksum key is \"%s\"", checksum);
				Logger.debug("Storing property \"%s\", value \"%s\"", "source_title", currentTitle);
				node.properties.put("source_title", currentTitle);
				checksums.put(checksum, node);
			}
			return FileVisitResult.CONTINUE;
		}
	};

	private static FileVisitor<Path> removeDuplicates = new SimpleFileVisitor<Path>() {
		@Override
		public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
			String fileString = file.toString().toLowerCase();
			if (attrs.isRegularFile() && 
					(fileString.endsWith("jpg") || fileString.endsWith("mp4") || fileString.endsWith("mov"))) {
				String checksum;

				if (!file.toFile().canRead()) {
					Logger.error("No access to target file \"%s\". Skipping...", file.toString());
					return FileVisitResult.CONTINUE;
				}
				try {
					checksum = createSHA1(file);
				}
				catch (IOException e) {
					Logger.error("No access to target file \"%s\". Skipping...", file.toString());
					return FileVisitResult.CONTINUE;
				}
				if (checksums.containsKey(checksum)) {
					Path sourcePath = checksums.get(checksum).sourcePath; 
					Logger.error("Skipping duplicate file \"%s\" in source. Keeping target file \"%s\".", sourcePath.toString(), file.toString());
					checksums.remove(checksum);
				}
			}
			return FileVisitResult.CONTINUE;
		}
	};

	private static String createSHA1(Path file) throws FileNotFoundException, IOException {
		String checksum = "";

		try {
			InputStream fis =  new FileInputStream(file.toString());
			byte[] buffer = new byte[1024];
			MessageDigest complete;

			complete = MessageDigest.getInstance("SHA1");
			int numRead;
			do {
				numRead = fis.read(buffer);
				if (numRead > 0) {
					complete.update(buffer, 0, numRead);
				}
			} while (numRead != -1);
			fis.close();

			for (byte b : complete.digest()) {
				checksum += Integer.toString((b & 0xff) + 0x100, 16).substring(1);
			}
		} catch (NoSuchAlgorithmException e) { }
		return checksum;
	}

	private static void getImageProperties() throws ImageReadException, IOException {
		for (Node node : checksums.values()) {
			IImageMetadata meta;

			try {
				meta = Sanselan.getMetadata(node.sourcePath.toFile());
			}
			catch (ImageReadException e) {
				Logger.debug("Found no exif data for source file \"%s\".", node.sourcePath.toFile());
				continue;
			}

			if (meta != null && meta instanceof JpegImageMetadata) {
				JpegImageMetadata data = (JpegImageMetadata) meta;

				TiffImageMetadata exifData = data.getExif();

				if (exifData != null) {
					TiffField exifDatetime = exifData.findField(ExifTagConstants.EXIF_TAG_DATE_TIME_ORIGINAL);
					if (exifDatetime != null) {
						Logger.debug("Found exif datetime data for source file \"%s\".", node.sourcePath.toFile());
						String datetime = exifDatetime.getStringValue().trim();
						String year = datetime.split(" ")[0].split(":")[0].trim();
						String month = datetime.split(" ")[0].split(":")[1].trim();
						String day = datetime.split(" ")[0].split(":")[2].trim();
						String time = datetime.split(" ")[1].replace(':', '_').trim();
						Logger.debug("Storing property \"%s\", value \"%s\"", "year", year);
						Logger.debug("Storing property \"%s\", value \"%s\"", "month", month);
						Logger.debug("Storing property \"%s\", value \"%s\"", "day", day);
						Logger.debug("Storing property \"%s\", value \"%s\"", "time", time);
						node.properties.put("year", year);
						node.properties.put("month", month);
						node.properties.put("day", day);
						node.properties.put("time", time);
					}
					else {
						Logger.debug("Found no exif datetime data for source file \"%s\".", node.sourcePath.toFile());
					}


					GPSInfo gpsInfo = exifData.getGPS();
					if (gpsInfo != null) {
						String lat = Double.toString(gpsInfo.getLatitudeAsDegreesNorth());
						String lng = Double.toString(gpsInfo.getLongitudeAsDegreesEast());
						Logger.debug("Found exif GPS data for source file \"%s\".", node.sourcePath.toFile());
						Logger.debug("Storing property \"%s\", value \"%s\"", "latitude", lat);
						Logger.debug("Storing property \"%s\", value \"%s\"", "longitude", lng);
						node.properties.put("latitude", lat);
						node.properties.put("longitude", lng);
					}
				}
			}
		}
	}

	private static void getGPSLocations() throws MalformedURLException, IOException, org.json.simple.parser.ParseException, InterruptedException {
		for (Node node : checksums.values()) {
			String lat = node.properties.get("latitude");
			String lng = node.properties.get("longitude");

			if (lat == null || lng == null)
				continue;

			URL url = new URL("http://maps.googleapis.com/maps/api/geocode/json?latlng=" + lat + "," + lng + "&sensor=true");

			boolean geoCodeFound = false;
			for (int r = 0; r < Settings.geocodingRetryCount; r++) {
				HttpURLConnection urlConnection = (HttpURLConnection) url.openConnection();

				try {
					InputStream in = url.openStream();
					BufferedReader reader = new BufferedReader(new InputStreamReader(in, "UTF-8"));
					String result, line = reader.readLine();
					result = line;
					while ((line = reader.readLine()) != null) {
						result += line;
					}

					JSONParser parser = new JSONParser();
					JSONObject rsp = (JSONObject) parser.parse(result);

					Logger.debug("Adding geocode data for \"%s\"", node.sourcePath.toString());
					Logger.debug("Geocode URL is \"%s\"", url.toString());
					if (rsp.containsKey("results")) {
						JSONArray results = (JSONArray) rsp.get("results");
						for (int i = 0; i < results.size(); i++) {
							JSONArray addressComponents = (JSONArray)((JSONObject) results.get(i)).get("address_components");
							for (int j = 0; j < addressComponents.size(); j++) { 
								JSONObject addrObj = (JSONObject) addressComponents.get(j);
								String addrType = (String)((JSONArray) addrObj.get("types")).get(0);
								String addrValue = addrObj.get("long_name").toString();
								if (!node.properties.containsKey(addrType)) {
									Logger.debug("Storing property \"%s\", value \"%s\"", addrType, addrValue);
									node.properties.put(addrType, addrValue);
								}
								geoCodeFound = true;
							}
						}
					}
					if (geoCodeFound) {
						break;
					}
					else {
						Logger.debug("Error in retrieving Geocode data. Sleeping 3 sec, then retrying...");
						Thread.sleep(3000);
					}
				}
				catch (Exception e) {
					e.printStackTrace();
				}
				finally {
					urlConnection.disconnect();
				}
			}
		}
	}

	private static String createLocalityString(List<String> localities) {
		String output = "";
		for (String locality : localities) {
			if (locality != null) {
				if (output.equals("")) {
					output = " " + locality;
				}
				else {
					output += ", " + locality;
				}
			}
		}
		return output;
	}

	private static Path findUniqueTargetName(Node node, String targetPath) {
		Path uniqueName = null;
		LinkOption[] loption = new LinkOption[] { LinkOption.NOFOLLOW_LINKS };

		int counter = 0;
		do {
			String counterString = "";
			if (counter > 0) {
				counterString = String.format(" %03d", counter);
			}
			String newTargetPath = targetPath + counterString + "." + node.fileExtension;
			uniqueName = Paths.get(newTargetPath);
			counter++;
		} while (Files.exists(uniqueName, loption));

		return uniqueName;
	}

	private static void sortImages() throws IOException {
		CopyOption[] coption = new CopyOption[] { StandardCopyOption.COPY_ATTRIBUTES };

		for (Map.Entry<String, Node> entry : checksums.entrySet()) {
			Node node = entry.getValue();

			String year = node.properties.get("year");
			String month = node.properties.get("month");
			String day = node.properties.get("day");
			String time = node.properties.get("time");

			List<String> folderAddress = new ArrayList<String>();
			folderAddress.add(node.properties.get("postal_town"));
			if (node.properties.get("locality") != null)
				if (!node.properties.get("locality").equals(node.properties.get("postal_town"))) folderAddress.add(node.properties.get("locality"));
			folderAddress.add(node.properties.get("country"));

			List<String> fileAddress = new ArrayList<String>();
//			fileAddress.add(node.properties.get("street_number"));
			fileAddress.add(node.properties.get("route"));
			fileAddress.add(node.properties.get("neighborhood"));
			if (node.properties.get("neighborhood") == null) fileAddress.add(node.properties.get("sublocality_level_1"));

			String path = null;
			if (year != null) {
				String folderAddressString = createLocalityString(folderAddress);
				String fileAddressString = createLocalityString(fileAddress);
				if (fileAddressString.equals("")) fileAddressString = " " + node.fileName;

				path = Settings.targetFolder + File.separator + year + File.separator + month + " - " +
						Settings.months[Integer.parseInt(month) - 1] + File.separator + day + " - " +
						folderAddressString + File.separator + time + " - " + fileAddressString;
			}
			else {
				String title = node.properties.get("source_title");

				path = Settings.targetFolder + File.separator + title + File.separator + node.relativePath + node.fileName;
			}


			Path targetPath = findUniqueTargetName(node, path);
			Path parentPath = targetPath.getParent();
			if (parentPath != null) {
				File destDir = parentPath.toFile();
				destDir.mkdirs();
			}

			Logger.debug("Copying file \"%s\" --> \"%s\".", node.sourcePath.toString(), targetPath.toString());
			Files.copy(node.sourcePath, targetPath, coption);
		}
	}

	@SuppressWarnings({ "static-access", "rawtypes" })
	public static boolean readCLI(String[] args) {
		Options options = new Options();

		Option logLevel = OptionBuilder.withArgName("log level")
				.hasArg()
				.withDescription("Set the verbosity of application console logging. Can be ERROR (default), INFO or DEBUG)")
				.create("loglevel");

		Option targetFolder = OptionBuilder.withArgName("name of source;path to source")
				.isRequired()
				.hasArg()
				.withDescription("target folder where image files are copied")
				.create("targetfolder");
		Option sourceFolder = OptionBuilder.withArgName("sourcefolder")
				.isRequired()
				.hasArgs(2)
				.withValueSeparator(';')
				.withDescription("enter one or many source folders where images are gathered")
				.create("sourcefolder");

		options.addOption(logLevel);
		options.addOption(targetFolder);
		options.addOption(sourceFolder);

		// create the parser
		CommandLineParser parser = new BasicParser();
		HelpFormatter formatter = new HelpFormatter();
		try {
			Properties parsedSourceFolder = null;
			String parsedTargetFolder = null;
			String parsedLogLevel = null;

			// parse the command line arguments
			CommandLine line = parser.parse(options, args);

			if (line.hasOption("loglevel")) {
				parsedLogLevel = line.getOptionValue("loglevel").trim().toLowerCase();
			}
			if (line.hasOption("sourcefolder")) {
				parsedSourceFolder = line.getOptionProperties("sourcefolder");
			}
			if (line.hasOption("targetfolder")) {
				parsedTargetFolder = line.getOptionValue("targetfolder").trim();
			}

			if (parsedLogLevel.equals("error")) Settings.logLevel = 3;
			else if (parsedLogLevel.equals("info")) Settings.logLevel = 2;
			else if (parsedLogLevel.equals("debug")) Settings.logLevel = 1;
			for (Entry entry : parsedSourceFolder.entrySet()) {
				Settings.sourceFolders.put(((String)entry.getKey()).trim(), (Path) Paths.get(((String)entry.getValue()).trim()));
			}
			Settings.targetFolder = Paths.get(parsedTargetFolder.trim());
		}
		catch(ParseException e) {
			System.err.println(e.getMessage());
			formatter.printHelp("imageorganizer", options);
			return false;
		}
		return true;
	}

	public static void main(String[] args) {
		//Settings.sourceFolders.put("iCloud Photos", "C:\\Users\\Mattias\\Pictures\\iCloud Photos");
		//Settings.sourceFolders.put("MyCloud Photos", "C:\\Users\\Mattias\\MyCloud\\Mattias iPhone Camera backup");
		//Settings.sourceFolders.put("One Drive Camera Roll", "D:\\Pictures_Temp");

		try {
			if (!readCLI(args))
				return;

			Logger.debug("Command line parameters:");
			for (Entry<String, Path> entry : Settings.sourceFolders.entrySet()) {
				Logger.debug("Source folder \"%s\" with path \"%s\" added.", entry.getKey(), entry.getValue().toString());
			}
			Logger.debug("Target folder \"%s\" added.", Settings.targetFolder.toString());
			Logger.debug("Log level is \"%d\"", Settings.logLevel);

			Logger.info("Adding all source files...");
			for (Entry<String, Path> entry : Settings.sourceFolders.entrySet()) {
				currentTitle = entry.getKey();
				currentSource = entry.getValue();
				Files.walkFileTree(currentSource, findImageFiles);
			}
			if (checksums.size() > 0) {
				Logger.info("Checking for duplicate files in target folder...");
				Files.walkFileTree(Settings.targetFolder, removeDuplicates);
			}
			if (checksums.size() > 0) {
				Logger.info("Retrieving image EXIF data...");
				getImageProperties();
				Logger.info("Retrieving Geocode data...");
				getGPSLocations();
				Logger.info("Copying images...");
				sortImages();
			}
			Logger.info("Operation complete!");
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

}
