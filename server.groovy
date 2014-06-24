import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;

import javax.ws.rs.GET;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Response;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.filefilter.FileFilterUtils;
import org.glassfish.jersey.jdkhttp.JdkHttpServerFactory;
import org.glassfish.jersey.server.ResourceConfig;
import org.json.JSONException;
import org.json.JSONObject;

public class Coagulate {
	@javax.ws.rs.Path("cmsfs")
	public static class MyResource { // Must be public

		private static final int LIMIT = 60;

		//
		// mutators
		//

		@GET
		@javax.ws.rs.Path("moveToParent")
		@Produces("application/json")
		public Response moveToParent(@QueryParam("filePath") String filePath)
				throws JSONException {

			if (filePath.endsWith("htm") || filePath.endsWith(".html")) {
				throw new RuntimeException("Need to move the _files folder too");
			}

			Path sourceFile = Paths.get(filePath);
			String filename = sourceFile.getFileName().toString();
			Path parent = sourceFile.getParent().getParent().toAbsolutePath();
			String parentPath = parent.toAbsolutePath().toString();
			String destinationFilePath = parentPath + "/" + filename;
			Path destinationFile = determineDestinationPathAvoidingExisting(destinationFilePath);
			doMove(sourceFile, destinationFile);
			System.out.println("File now resides at "
					+ destinationFile.toAbsolutePath().toString());
			JSONObject response = new JSONObject();
			Response build = Response.ok()
					.header("Access-Control-Allow-Origin", "*")
					.entity(response.toString(4)).type("application/json")
					.build();
			return build;
		}

		@GET
		@javax.ws.rs.Path("move")
		@Produces("application/json")
		public Response move(
				@QueryParam("filePath") String filePath,
				@QueryParam("destinationDirSimpleName") String destinationDirSimpleName)
				throws JSONException, IOException {

			if (filePath.endsWith("htm") || filePath.endsWith(".html")) {
				throw new RuntimeException("Need to move the _files folder too");
			}

			try {
				moveFileToSubfolder(filePath, destinationDirSimpleName);
			} catch (Exception e) {
				e.printStackTrace();
				throw new RuntimeException(e);
			}
			JSONObject response = new JSONObject();
			Response build = Response.ok()
					.header("Access-Control-Allow-Origin", "*")
					.entity(response.toString(4)).type("application/json")
					.build();
			return build;
		}

		private static void moveFileToSubfolder(String filePath,
				String folderName) throws IllegalAccessError, IOException {
			java.nio.file.Path path = Paths.get(filePath);
			// File imageFile = path.toFile();
			if (!Files.exists(path)) {
				throw new RuntimeException("File doesn't exist");
			}

			// Already in right location
			if (imagePathAlreadyContainsFolder(path, folderName)) {
				System.out.println("Path already contains " + folderName);
			}

			// if the subfolder exists, do nothing
			String parentDirPath = path.getParent().toAbsolutePath().toString();
			String destinationFolderPath = parentDirPath + "/" + folderName;
			if (folderName.equals(path.getParent().getFileName().toString())) {
				System.out.println("Not moving to identical subfolder");
				return;
			}
			java.nio.file.Path subfolder = getOrCreateDestinationFolder(destinationFolderPath);
			java.nio.file.Path destinationFile = allocateFile(path, subfolder);
			doMove(path, destinationFile);

		}

		private static void doMove(java.nio.file.Path path,
				java.nio.file.Path destinationFile) throws IllegalAccessError {
			try {
				Files.move(path, destinationFile);// By default, it won't
													// overwrite existing
				System.out.println("Success: file now at "
						+ destinationFile.toAbsolutePath());
			} catch (IOException e) {
				e.printStackTrace();
				throw new IllegalAccessError("Moving did not work");
			}
		}

		private static Path allocateFile(Path imageFile, Path subfolder)
				throws IllegalAccessError {
			// if destination file exists, rename the file to be moved(while
			// loop)
			String destinationFilePath = subfolder.normalize().toAbsolutePath().toString() + "/" + imageFile.getFileName().toString();

			Path destinationFile = determineDestinationPathAvoidingExisting(destinationFilePath);
			return destinationFile;
		}

		private static Path determineDestinationPathAvoidingExisting(
				String destinationFilePath) throws IllegalAccessError {
			String destinationFilePathWithoutExtension = destinationFilePath
					.substring(0, destinationFilePath.lastIndexOf('.'));
			String extension = FilenameUtils.getExtension(destinationFilePath);
			Path destinationFile = Paths.get(destinationFilePath);
			while (Files.exists(destinationFile)) {
				destinationFilePathWithoutExtension += "1";
				destinationFilePath = destinationFilePathWithoutExtension + "."
						+ extension;
				destinationFile = Paths.get(destinationFilePath);
			}
			if (Files.exists(destinationFile)) {
				throw new IllegalAccessError(
						"an existing file will get overwritten");
			}
			return destinationFile;
		}

		private static java.nio.file.Path getOrCreateDestinationFolder(
				String destinationFolderPath) throws IllegalAccessError,
				IOException {
			// File subfolder = new File(destinationFolderPath);
			java.nio.file.Path subfolder = Paths.get(destinationFolderPath);
			// if the subfolder does not exist, create it
			if (!Files.exists(subfolder)) {
				Files.createDirectory(subfolder);
			}
			if (!Files.isDirectory(subfolder)) {
				throw new IllegalAccessError(
						"Developer Error: not a directory - "
								+ subfolder.toAbsolutePath());
			}
			return subfolder;
		}

		private static boolean imagePathAlreadyContainsFolder(
				java.nio.file.Path imageFile, String folderName) {
			if (imageFile == null) {
				return false;
			}
			if (imageFile.toString().equals(folderName)) {
				return true;
			} else {
				return imagePathAlreadyContainsFolder(imageFile.getParent(),
						folderName);
			}
		}

		@GET
		@javax.ws.rs.Path("list")
		@Produces("application/json")
		public Response list(@QueryParam("dirs") String iDirectoryPathsString)
				throws JSONException, IOException {
			String[] allDirectoryPathStrings = iDirectoryPathsString
					.split("\\n");
			JSONObject response = new JSONObject();
			response.put("items", getItems(allDirectoryPathStrings));
			response.put("locations", getLocationsJson(allDirectoryPathStrings));

			Response build = Response.ok()
					.header("Access-Control-Allow-Origin", "*")
					.entity(response.toString(4)).type("application/json")
					.build();
			return build;
		}

		private JSONObject getItems(String[] allDirectoryPathStrings)
				throws IOException {
			JSONObject itemsJson = new JSONObject();
			for (String aDirectoryPathString : allDirectoryPathStrings) {
				if (!shouldGetContents(aDirectoryPathString)) {
					continue;
				}
				itemsJson.put(aDirectoryPathString, getContentsAsJson(new File(
						aDirectoryPathString)));
			}
			return itemsJson;
		}

		private JSONObject getLocationsJson(String[] allDirectoryPathStrings)
				throws IOException {
			JSONObject locationsJson = new JSONObject();
			for (String aDirectoryPathString : allDirectoryPathStrings) {
				if (!shouldGetContents(aDirectoryPathString)) {
					continue;
				}

				JSONObject locationDetailsJson = new JSONObject();
				_4: {
					locationsJson
							.put(aDirectoryPathString, locationDetailsJson);
					_5: {
						Collection<String> dirsWithBoundKey = addKeyBindings(
								aDirectoryPathString, locationDetailsJson);
						File aDirectory2 = new File(aDirectoryPathString);
						addDirs(aDirectory2, locationDetailsJson,
								dirsWithBoundKey);
					}
				}
			}

			return locationsJson;
		}

		private boolean shouldGetContents(String aDirectoryPathString) {
			File aDirectory = new File(aDirectoryPathString);
			if (aDirectoryPathString.startsWith("#")) {
				return false;
			}
			if (!aDirectory.exists()) {
				System.out.println(aDirectoryPathString + " doesn't exist.");
				return false;
			}
			if (!aDirectory.isDirectory()) {
				System.out
						.println(aDirectoryPathString + " is not a directory");
				return false;
			}
			return true;
		}

		private JSONObject getContentsAsJson(File aDirectory)
				throws IOException {
			JSONObject filesInLocationJson = new JSONObject();
			int fileCount = 0;
			for (Path aFilePath : getDirectoryStream(aDirectory)) {
				String filename = aFilePath.getFileName().toString();
				String fileAbsolutePath = aFilePath.toAbsolutePath().toString();
				if (filename.contains("DS_Store")) {
					continue;
				}
				if (filename.endsWith(".html") || filename.endsWith(".htm")
						|| aDirectory.getName().endsWith("_files")) {
					System.out.println("Not supported yet: " + filename);
					continue;
				}
				JSONObject fileEntryJson = createFileEntryJson(
						aDirectory.getAbsolutePath(),
						httpLinkFor(fileAbsolutePath));

				filesInLocationJson.put(fileAbsolutePath, fileEntryJson);
				++fileCount;
				if (fileCount > LIMIT) {
					break;
				}
				System.out.println(fileAbsolutePath);

			}
			return filesInLocationJson;
		}

		private DirectoryStream<Path> getDirectoryStream(File aDirectory)
				throws IOException {
			return getDirectoryStream(Paths.get(aDirectory.getAbsolutePath()));
		}

		private DirectoryStream<Path> getDirectoryStream(Path aDirectoryPath)
				throws IOException {
			DirectoryStream<Path> theDirectoryStream = Files
					.newDirectoryStream(aDirectoryPath,
							new DirectoryStream.Filter<Path>() {
								public boolean accept(Path entry)
										throws IOException {
									return !Files.isDirectory(entry);

								}
							});
			return theDirectoryStream;
		}

		private JSONObject createFileEntryJson(String iLocalFileSystemPath,
				String iHttpUrl) {
			JSONObject fileEntryJson = new JSONObject();
			fileEntryJson.put("location", iLocalFileSystemPath);
			fileEntryJson.put("httpUrl", iHttpUrl);
			return fileEntryJson;
		}

		private String httpLinkFor(String absolutePath) {
			// Unsorted
			String http = absolutePath.replaceFirst("/Volumes/Unsorted",
					"http://netgear.rohidekar.com:8020/");
			http = http.replaceFirst("/media/sarnobat/Unsorted",
					"http://netgear.rohidekar.com:8020/");

			// Large
			http = http.replaceFirst("/media/sarnobat/Large/",
					"http://netgear.rohidekar.com:8021/");
			http = http.replaceFirst("/Volumes/Large/",
					"http://netgear.rohidekar.com:8021/");

			http = http.replaceFirst("^/e/Sridhar/Photos",
					"http://netgear.rohidekar.com:8022/");

			return http;
		}

		private void addDirs(File dir, JSONObject locationDetails,
				Collection<String> dirsWithBoundKey) throws JSONException {
			JSONObject containedDirsJson = new JSONObject();

			for (File file : getDirectories(dir)) {
				if (file.getName().endsWith("_files")) {
					continue;
				}
				if (dirsWithBoundKey.contains(file.getName())) {
					// continue;
				}
				containedDirsJson.put(file.getName(), "");
			}
			locationDetails.put("dirs", containedDirsJson);
		}

		private Collection<String> addKeyBindings(String location,
				JSONObject locationDetails) throws IOException, JSONException {
			Collection<String> dirsWithBoundKey = new HashSet<String>();
			_6: {
				JSONObject fileBindingsJson = new JSONObject();
				File f = new File(location + "/" + "categories.txt");
				File f2 = new File(location + "/" + "photoSorter.txt");
				File categoriesFile = null;
				if (f.exists()) {
					categoriesFile = f;
				}
				if (f2.exists()) {
					categoriesFile = f2;
				}
				if (categoriesFile != null) {
					List<String> allCategoriesInFile = FileUtils
							.readLines(categoriesFile);
					for (String aBindingLine : allCategoriesInFile) {
						// Ignore comments
						if (aBindingLine.trim().startsWith("#")) {
							continue;
						}
						try {
							char keyCode = getKeyCode(aBindingLine);
							String folderName = getFolderName(aBindingLine);
							fileBindingsJson.put(String.valueOf(keyCode),
									folderName);
							dirsWithBoundKey.add(folderName);
						} catch (RuntimeException e) {
							e.printStackTrace();
							System.err.println("Exception: " + e.getMessage()
									+ ": " + aBindingLine);
						}
					}
					locationDetails.put("keys", fileBindingsJson);
				}
			}
			return dirsWithBoundKey;
		}

		private static String getFolderName(String uncommentedBindingLine)
				throws RuntimeException {
			String rightSide = parseBindingLine(uncommentedBindingLine)[1];
			if (rightSide.length() < 1) {
				throw new IllegalAccessError("Developer error");
			}
			return rightSide;
		}

		private static char getKeyCode(String uncommentedBindingLine)
				throws RuntimeException {

			String leftSide = parseBindingLine(uncommentedBindingLine)[0];
			if (leftSide.length() != 1) {
				throw new IllegalAccessError("Developer error");
			}
			char keyCode = leftSide.charAt(0);
			return keyCode;
		}

		private static String[] parseBindingLine(String aBindingLine)
				throws RuntimeException {
			if (aBindingLine.trim().startsWith("#")) {
				throw new IllegalAccessError("Developer error");
			}
			String[] pair = aBindingLine.split("=");
			if (pair.length != 2) {
				throw new RuntimeException(pair.toString());
			}
			return pair;
		}

		private File[] getDirectories(File loc) {
			return loc.listFiles((FileFilter) FileFilterUtils
					.directoryFileFilter());
		}

	}

	public static void main(String[] args) throws URISyntaxException {
		JdkHttpServerFactory.createHttpServer(
				new URI("http://localhost:4451/"), new ResourceConfig(
						MyResource.class));
	}
}
