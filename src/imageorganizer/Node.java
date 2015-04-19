package imageorganizer;

import java.io.File;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

public class Node {
	public Path sourcePath;
	public String relativePath;
	public String fileName;
	public String fileExtension;
	public Map<String, String> properties = new HashMap<String, String>();

	public Node(Path sourcePath) {
		this.sourcePath = sourcePath;
		this.fileName = sourcePath.getFileName().toString();
		this.fileExtension = fileName.substring(fileName.lastIndexOf('.') + 1, fileName.length());
		this.fileName = fileName.substring(0, fileName.lastIndexOf('.') );
	}

	public Node(Path currentSource, Path sourcePath) {
		this.sourcePath = sourcePath;
		this.fileName = sourcePath.getFileName().toString();
		this.fileExtension = fileName.substring(fileName.lastIndexOf('.') + 1, fileName.length());
		this.fileName = fileName.substring(0, fileName.lastIndexOf('.'));
		Path relative = currentSource.relativize(sourcePath).getParent();
		if (relative == null) {
			this.relativePath = File.separator;
		}
		else {
			this.relativePath = relative.toString() + File.separator;
		}
		if (relativePath == null) relativePath = "";
	}
}
