import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

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

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;

public class Server {
	@javax.ws.rs.Path("cmsfs")
	public static class MyResource { // Must be public

		private static final int LIMIT = 60;

		private static final String[] _locations = Lists.newArrayList(
				//"/Volumes/Unsorted/Videos/other", 
				"/Volumes/Unsorted/Videos/wwf", 
				"/Volumes/Unsorted/Videos",
				"/Users/sarnobat/Windows/misc/ind",
				"/Users/sarnobat/Windows/misc"
		// "/Volumes/Unsorted/Videos/other", "/Volumes/Unsorted/Videos",
		// "/Users/sarnobat/Windows/misc",
		// "/Users/sarnobat/Windows/misc/ind",
		// "/Volumes/Unsorted/Videos/soccer",
		// "/Users/sarnobat/Windows/favorites",
		// "/Users/sarnobat/Desktop/new/videos/Atletico"
		// "/Users/sarnobat/Desktop/new/",
		// "/Users/sarnobat/Windows/Web/Personal Development/Romance",
		// "/e/new",
				).toArray(new String[0]);
		private static final Set<PosixFilePermission> perms = PosixFilePermissions
				.fromString("rwxr-x---");
		private static final FileAttribute<Set<PosixFilePermission>> attr = PosixFilePermissions
				.asFileAttribute(perms);

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
				destinationFilePath = destinationFilePathWithoutExtension + "." + extension;
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

		@Deprecated
		private static boolean imagePathAlreadyContainsFolder(File imageFile,
				String folderName) {
			if (imageFile == null) {
				return false;
			}
			if (imageFile.getName().equals(folderName)) {
				return true;
			} else {
				return imagePathAlreadyContainsFolder(
						imageFile.getParentFile(), folderName);
			}
		}

		@Deprecated
		private static void verifySourceImageExists(File imageFile)
				throws IllegalAccessError {
			Preconditions.checkNotNull(imageFile);
			if (!imageFile.exists()) {
				try {
					throw new IllegalAccessError(
							"Developer Error. File doesn't exist:"
									+ imageFile.getCanonicalPath());
				} catch (IllegalAccessError e) {
					throw new IllegalAccessError(
							"Developer Error. File doesn't exist:");
				} catch (IOException e) {
					throw new IllegalAccessError(
							"Developer Error. File doesn't exist:");
				}
			}
		}

		@GET
		@javax.ws.rs.Path("list")
		@Produces("application/json")
		public Response list(@QueryParam("dirs") String locations)
				throws JSONException, IOException {
			String[] locs = locations.split("\\n");
			JSONObject response = new JSONObject();
			_1: {
				JSONObject locationsJSON = new JSONObject();
				response.put("locations", locationsJSON);
			}
			_2: {
				JSONObject items = new JSONObject();
				JSONObject locationsJson = new JSONObject();
				_3: {
					for (String location : locs) {
						if(location.startsWith("#")) {
							continue;
						}
						File loc = new File(location);
						if (!loc.exists()) {
							System.out.println(location + " doesn't exist.");
							continue;
						}
						if (!loc.isDirectory()) {
							System.out
									.println(location + " is not a directory");
							continue;
						}
						File dir = loc;
						_4: {
							JSONObject filesInLocation = new JSONObject();

							java.nio.file.Path dir2 = Paths.get(dir
									.getAbsolutePath());
							DirectoryStream.Filter<java.nio.file.Path> filter = new DirectoryStream.Filter<java.nio.file.Path>() {
								public boolean accept(java.nio.file.Path entry)
										throws IOException {
									return !Files.isDirectory(entry);

								}
							};
							DirectoryStream<java.nio.file.Path> stream = Files
									.newDirectoryStream(dir2, filter);
							int i = 0;
							for (java.nio.file.Path entry : stream) {
								String name = entry.getFileName().toString();
								String absolutePath = entry.toAbsolutePath()
										.toString();
								if (name.contains("DS_Store")) {
									continue;
								}
								if (name.endsWith(".html")
										|| name.endsWith(".htm")
										|| loc.getName().endsWith("_files")) {
									System.out.println("Not supported yet: "
											+ name);
									continue;
								}
								JSONObject fileDetails = new JSONObject();
								fileDetails.put("location",
										loc.getAbsolutePath());

								filesInLocation.put(absolutePath, fileDetails);
								++i;
								if (i > LIMIT) {
									break;
								}
								System.out.println(absolutePath);

							}
							items.put(location, filesInLocation);
						}
						JSONObject locationDetails = new JSONObject();
						locationsJson.put(location, locationDetails);
						Collection<String> dirsWithBoundKey = addKeyBindings(
								location, locationDetails);
						addDirs(dir, locationDetails, dirsWithBoundKey);
					}
				}
				response.put("items", items);
				response.put("locations", locationsJson);
			}
			Response build = Response.ok()
					.header("Access-Control-Allow-Origin", "*")
					.entity(response.toString(4)).type("application/json")
					.build();
			return build;
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
				new URI("http://localhost:8011/"), new ResourceConfig(
						MyResource.class));
	}
}