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
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class Coagulate {
	@javax.ws.rs.Path("cmsfs")
	public static class MyResource { // Must be public

		//
		// mutators
		//

		@GET
		@javax.ws.rs.Path("moveToParent")
		@Produces("application/json")
		public Response moveToParent(@QueryParam("filePath") String sourceFilePathString)
				throws JSONException {
//			System.out.println("moveToParent() - begin - " + sourceFilePathString);
			if (sourceFilePathString.endsWith("htm") || sourceFilePathString.endsWith(".html")) {
				throw new RuntimeException("Need to move the _files folder too");
			}

			doMoveToParent(sourceFilePathString);
			
			return Response.ok()
					.header("Access-Control-Allow-Origin", "*")
					.entity(new JSONObject().toString(4)).type("application/json")
					.build();
		}

		private void doMoveToParent(String sourceFilePathString)
				throws IllegalAccessError {
			Path sourceFilePath = Paths.get(sourceFilePathString);
			Path destinationFile = getDestinationFilePathAvoidingExisting(sourceFilePath);
			doMove(sourceFilePath, destinationFile);
//			System.out.println("File now resides at "
//					+ destinationFile.toAbsolutePath().toString());
		}

		private Path getDestinationFilePathAvoidingExisting(Path sourceFile)
				throws IllegalAccessError {
			Path destinationFile;
			_1: {
				String filename = sourceFile.getFileName().toString();
				Path parent = sourceFile.getParent().getParent().toAbsolutePath();
				String parentPath = parent.toAbsolutePath().toString();
				String destinationFilePath = parentPath + "/" + filename;
				destinationFile = determineDestinationPathAvoidingExisting(destinationFilePath);
			}
			return destinationFile;
		}

		@GET
		@javax.ws.rs.Path("copyToFolder")
		@Produces("application/json")
		public Response copy(
				@QueryParam("filePath") String iFilePath,
				@QueryParam("destinationDirPath") String iDestinationDirPath)
				throws JSONException, IOException {

			if (iFilePath.endsWith("htm") || iFilePath.endsWith(".html")) {
				throw new RuntimeException("Need to move the _files folder too");
			}

			try {
				copyFileToFolder(iFilePath, iDestinationDirPath);
			} catch (Exception e) {
				e.printStackTrace();
				throw new RuntimeException(e);
			}
			return Response.ok()
					.header("Access-Control-Allow-Origin", "*")
					.entity(new JSONObject().toString(4)).type("application/json")
					.build();
		}

		private static void copyFileToFolder(String filePath,
				String iDestinationDirPath) throws IllegalAccessError, IOException {
			Path sourceFilePath = Paths.get(filePath);
//			System.out.println(filePath);
			if (!Files.exists(sourceFilePath)) {
				throw new RuntimeException("No such source file");
			}
			String string = sourceFilePath.getFileName().toString();
//			System.out.println(iDestinationDirPath);
			Path destinationDir = Paths.get(iDestinationDirPath);
			doCopy(sourceFilePath, getUnconflictedDestinationFilePath(destinationDir, string));
		}

		private static Path getUnconflictedDestinationFilePath (Path destinationDir, String sourceFileSimpleName) {
			Path rDestinationFile = allocateFile(destinationDir, sourceFileSimpleName);
			return rDestinationFile;
		}

		@GET
		@javax.ws.rs.Path("move")
		@Produces("application/json")
		public Response move(
				@QueryParam("filePath") String iFilePath,
				@QueryParam("destinationDirSimpleName") String iDestinationDirSimpleName)
				throws JSONException, IOException {

			if (iFilePath.endsWith("htm") || iFilePath.endsWith(".html")) {
				throw new RuntimeException("Need to move the _files folder too");
			}

			try {
				moveFileToSubfolder(iFilePath, iDestinationDirSimpleName);
			} catch (Exception e) {
				e.printStackTrace();
				throw new RuntimeException(e);
			}
			return Response.ok()
					.header("Access-Control-Allow-Origin", "*")
					.entity(new JSONObject().toString(4)).type("application/json")
					.build();
		}

		private static void moveFileToSubfolder(String filePath,
				String iSubfolderSimpleName) throws IllegalAccessError, IOException {
			Path sourceFilePath = Paths.get(filePath);
			if (!Files.exists(sourceFilePath)) {
				throw new RuntimeException("No such source file");
			}
			Path targetDir = Paths.get(sourceFilePath.getParent().toString()
					+ "/" + iSubfolderSimpleName);
			if (!Files.exists(targetDir)) {
				Files.createDirectory(targetDir);
			} else if (!Files.isDirectory(targetDir)) {
				throw new RuntimeException("Target is an existing file");
			}
			if (fileAlreadyInDesiredSubdir(iSubfolderSimpleName, sourceFilePath)) {
//				System.out.println("Not moving to identical subfolder");
				return;
			}
			doMove(sourceFilePath, getUnconflictedDestinationFilePath(iSubfolderSimpleName, sourceFilePath));

		}

		private static boolean fileAlreadyInDesiredSubdir(
				String subfolderSimpleName, Path sourceFilePath) {
			return subfolderSimpleName.equals(sourceFilePath.getParent().getFileName().toString());
		}

		private static Path getUnconflictedDestinationFilePath(String folderName, Path path)
				throws IllegalAccessError, IOException {
			Path rDestinationFile;
			_1: {
				String parentDirPath = path.getParent().toAbsolutePath()
						.toString();
				String destinationFolderPath = parentDirPath + "/" + folderName;
				Path subfolder = getOrCreateDestinationFolder(destinationFolderPath);
				rDestinationFile = allocateFile(path, subfolder);
			}
			return rDestinationFile;
		}

		private static void doMove(Path path, Path destinationFile)
				throws IllegalAccessError {
			try {
				Files.move(path, destinationFile);// By default, it won't
													// overwrite existing
//				System.out.println("Success: file now at "
//						+ destinationFile.toAbsolutePath());
			} catch (IOException e) {
				e.printStackTrace();
				throw new IllegalAccessError("Moving did not work");
			}
		}

		private static void doCopy(Path sourceFilePath, Path destinationFilePath) {
			try {
				Files.copy(sourceFilePath, destinationFilePath);// By default, it won't
													// overwrite existing
//				System.out.println("Success: copied file now at "
//						+ destinationFilePath.toAbsolutePath());
			} catch (IOException e) {
				e.printStackTrace();
				throw new IllegalAccessError("Copying did not work");
			}
		}

		private static Path allocateFile(Path folder, String fileSimpleName)
				throws IllegalAccessError {
			// if destination file exists, rename the file to be moved(while
			// loop)
			String destinationFilePath = folder.normalize().toAbsolutePath()
					.toString() + "/" + fileSimpleName;

			Path rDestinationFile = determineDestinationPathAvoidingExisting(destinationFilePath);
			return rDestinationFile;
		}

		private static Path allocateFile(Path imageFile, Path subfolder)
				throws IllegalAccessError {
			// if destination file exists, rename the file to be moved(while
			// loop)
			String destinationFilePath = subfolder.normalize().toAbsolutePath()
					.toString() + "/" + imageFile.getFileName().toString();

			Path rDestinationFile = determineDestinationPathAvoidingExisting(destinationFilePath);
			return rDestinationFile;
		}

		private static Path determineDestinationPathAvoidingExisting(
				String destinationFilePath) throws IllegalAccessError {
			String destinationFilePathWithoutExtension = destinationFilePath
					.substring(0, destinationFilePath.lastIndexOf('.'));
			String extension = FilenameUtils.getExtension(destinationFilePath);
			Path rDestinationFile = Paths.get(destinationFilePath);
			while (Files.exists(rDestinationFile)) {
				destinationFilePathWithoutExtension += "1";
				destinationFilePath = destinationFilePathWithoutExtension + "."	+ extension;
				rDestinationFile = Paths.get(destinationFilePath);
			}
			if (Files.exists(rDestinationFile)) {
				throw new IllegalAccessError(
						"an existing file will get overwritten");
			}
			return rDestinationFile;
		}

		private static java.nio.file.Path getOrCreateDestinationFolder(
				String destinationFolderPath) throws IllegalAccessError,
				IOException {
			java.nio.file.Path rSubfolder = Paths.get(destinationFolderPath);
			// if the subfolder does not exist, create it
			if (!Files.exists(rSubfolder)) {
				Files.createDirectory(rSubfolder);
			}
			if (!Files.isDirectory(rSubfolder)) {
				throw new IllegalAccessError(
						"Developer Error: not a directory - "
								+ rSubfolder.toAbsolutePath());
			}
			return rSubfolder;
		}

		@GET
		@javax.ws.rs.Path("list")
		@Produces("application/json")
		public Response list(@QueryParam("dirs") String iDirectoryPathsString)
				throws JSONException, IOException {
			System.out.println("list() - begin");
			JSONObject response = createListJson(iDirectoryPathsString
					.split("\\n"));

			return Response.ok()
					.header("Access-Control-Allow-Origin", "*")
					.entity(response.toString(4)).type("application/json")
					.build();
		}

		private JSONObject createListJson(String[] iDirectoryPathStrings)
				throws IOException {
//			System.out.println("createListJson() - begin");
			JSONObject rResponse = new JSONObject();
			rResponse.put("items", createFilesJson(iDirectoryPathStrings));
			rResponse.put("itemsRecursive", createFilesJsonRecursive(iDirectoryPathStrings));
			rResponse.put("locations",
					createLocationsJson(iDirectoryPathStrings));
			rResponse.put("subdirectories",
					createSubdirectoriesJson(iDirectoryPathStrings));
			
			return rResponse;
		}

		private JSONObject createSubdirectoriesJson(
				String[] iDirectoryPathStrings) {
			JSONObject rItemsJson = new JSONObject();
			_3: {
				for (String aDirectoryPathString : iDirectoryPathStrings) {
					if (!shouldGetContents(aDirectoryPathString)) {
						continue;
					}
					try {
						rItemsJson.put(aDirectoryPathString,
								createSubdirDetailsJson2(aDirectoryPathString));
					} catch (JSONException e) {
						e.printStackTrace();
					} catch (IOException e) {
						e.printStackTrace();
					}
				}
			}
			return rItemsJson;
		}

		private JSONObject createSubdirDetailsJson2(String iDirectoryPathString) throws IOException {
			return getSubdirsAsJson2(new File(iDirectoryPathString));
		}

		private JSONObject createSubdirDetailsJson(String iDirectoryPathString) throws IOException {
			return getSubdirsAsJson(new File(iDirectoryPathString));
		}

		private JSONObject createFilesJsonRecursive(String[] iDirectoryPathStrings)
				throws IOException {
//			System.out.println("createFilesJsonRecursive() - begin");
			JSONObject rItemsJson = new JSONObject();
			_3: {
				for (String aDirectoryPathString : iDirectoryPathStrings) {
					if (!shouldGetContents(aDirectoryPathString)) {
						continue;
					}
					rItemsJson.put(aDirectoryPathString,
							createItemDetailsJsonRecursive(aDirectoryPathString));
				}
			}
			return rItemsJson;
		}

		private JSONObject createFilesJson(String[] iDirectoryPathStrings)
				throws IOException {
			JSONObject rItemsJson = new JSONObject();
			_3: {
				for (String aDirectoryPathString : iDirectoryPathStrings) {
					if (!shouldGetContents(aDirectoryPathString)) {
						continue;
					}
					rItemsJson.put(aDirectoryPathString,
							createItemDetailsJson(aDirectoryPathString));
				}
			}
			return rItemsJson;
		}

		private JSONObject createLocationsJson(String[] iDirectoryPathStrings)
				throws IOException {
			JSONObject rLocationsJson = new JSONObject();
			_3: {
				for (String aDirectoryPathString : iDirectoryPathStrings) {
					if (!shouldGetContents(aDirectoryPathString)) {
						continue;
					}
					rLocationsJson.put(
							aDirectoryPathString,
							createLocationDetailsJson(aDirectoryPathString));
				}
			}
			return rLocationsJson;
		}

		private JSONObject createItemDetailsJson(String iDirectoryPathString)
				throws IOException {
			return getContentsAsJson(new File(iDirectoryPathString));
		}
		
		private JSONObject createItemDetailsJsonRecursive(String iDirectoryPathString)
				throws IOException {
			return getContentsAsJsonRecursive(new File(iDirectoryPathString),1);
		}
		
		private JSONObject createLocationDetailsJson(String iDirectoryPathString) throws IOException {
			JSONObject rLocationDetailsJson = new JSONObject();
			_1: {
				File aDirectory = new File(iDirectoryPathString);
				_2: {
					Collection<String> dirsWithBoundKey = addKeyBindings(
							iDirectoryPathString,
							rLocationDetailsJson);
					addDirs(aDirectory, rLocationDetailsJson,
							dirsWithBoundKey);
				}
			}
			return rLocationDetailsJson;
		}

		private boolean shouldGetContents(String iDirectoryPathString) {
//			System.out.println("3 " + iDirectoryPathString);
			if (iDirectoryPathString.startsWith("#")) {
//				System.out.println("4 " + iDirectoryPathString);
				return false;
			}
//			System.out.println("5 " + iDirectoryPathString);

			File aDirectory = new File(iDirectoryPathString);
			if (!aDirectory.exists()) {
//				System.out.println(iDirectoryPathString + " doesn't exist.");
				return false;
			}
			if (!aDirectory.isDirectory()) {
//				System.out
//						.println(iDirectoryPathString + " is not a directory");
				return false;
			}
			return true;
		}

		private JSONObject getSubdirsAsJson2(File iDirectory)
				throws IOException {
			JSONObject rFilesInLocationJson = new JSONObject();
			for (Path aFilePath : getSubdirectoryStream2(iDirectory)) {
				String filename = aFilePath.getFileName().toString();
				String fileAbsolutePath = aFilePath.toAbsolutePath().toString();
				if (filename.contains("DS_Store")) {
					continue;
				}
				if (filename.endsWith(".html") || filename.endsWith(".htm")
						|| iDirectory.getName().endsWith("_files")) {
//					System.out.println("Not supported yet: " + filename);
					continue;
				}
				String thumbnailFileAbsolutePath;
				_1: {
					thumbnailFileAbsolutePath = iDirectory.getAbsolutePath() + "/_thumbnails/" + filename + ".jpg"; 
				}
				JSONObject fileEntryJson ;
				_2: {
					JSONObject rFileEntryJson = new JSONObject();
					rFileEntryJson
							.put("location", iDirectory.getAbsolutePath());
					rFileEntryJson
							.put("fileSystem", fileAbsolutePath);
					rFileEntryJson.put("httpUrl", httpLinkFor(fileAbsolutePath));
					rFileEntryJson.put("thumbnailUrl", httpLinkFor(thumbnailFileAbsolutePath));
				fileEntryJson = rFileEntryJson;
				}
				rFilesInLocationJson.put(fileAbsolutePath, fileEntryJson);
//				System.out.println(fileAbsolutePath);
			}
			return rFilesInLocationJson;
		}
		private JSONObject getSubdirsAsJson(File iDirectory)
				throws IOException {
			JSONObject rFilesInLocationJson = new JSONObject();
			for (Path aFilePath : getSubdirectoryStream2(iDirectory)) {
				String filename = aFilePath.getFileName().toString();
				String fileAbsolutePath = aFilePath.toAbsolutePath().toString();
				if (filename.contains("DS_Store")) {
					continue;
				}
				if (filename.endsWith(".html") || filename.endsWith(".htm")
						|| iDirectory.getName().endsWith("_files")) {
//					System.out.println("Not supported yet: " + filename);
					continue;
				}
				String thumbnailFileAbsolutePath;
				_1: {
					thumbnailFileAbsolutePath = iDirectory.getAbsolutePath() + "/_thumbnails/" + filename + ".jpg"; 
				}
				JSONObject fileEntryJson ;
				_2: {
					JSONObject rFileEntryJson = new JSONObject();
					rFileEntryJson
							.put("location", iDirectory.getAbsolutePath());
					rFileEntryJson
							.put("fileSystem", fileAbsolutePath);
					rFileEntryJson.put("httpUrl", httpLinkFor(fileAbsolutePath));
					rFileEntryJson.put("thumbnailUrl", httpLinkFor(thumbnailFileAbsolutePath));
				fileEntryJson = rFileEntryJson;
				}
				rFilesInLocationJson.put(fileAbsolutePath, fileEntryJson);
//				System.out.println(fileAbsolutePath);
			}
			return rFilesInLocationJson;
		}
		
		private JSONObject getContentsAsJson(File iDirectory)
				throws IOException {
			JSONObject rFilesInLocationJson = new JSONObject();
			for (Path aFilePath : getDirectoryStream(iDirectory)) {
				String filename = aFilePath.getFileName().toString();
				String fileAbsolutePath = aFilePath.toAbsolutePath().toString();
				if (filename.contains("DS_Store")) {
					continue;
				}
				if (filename.endsWith(".html") || filename.endsWith(".htm")
						|| iDirectory.getName().endsWith("_files")) {
//					System.out.println("Not supported yet: " + filename);
					continue;
				}
				String thumbnailFileAbsolutePath;
				_1: {
					thumbnailFileAbsolutePath = iDirectory.getAbsolutePath() + "/_thumbnails/" + filename + ".jpg"; 
				}
				JSONObject fileEntryJson ;
				_2: {
					JSONObject rFileEntryJson = new JSONObject();
					rFileEntryJson
							.put("location", iDirectory.getAbsolutePath());
					rFileEntryJson
							.put("fileSystem", fileAbsolutePath);
					rFileEntryJson.put("httpUrl", httpLinkFor(fileAbsolutePath));
					rFileEntryJson.put("thumbnailUrl", httpLinkFor(thumbnailFileAbsolutePath));
				fileEntryJson = rFileEntryJson;
				}
				rFilesInLocationJson.put(fileAbsolutePath, fileEntryJson);
//				System.out.println(fileAbsolutePath);
			}
			return rFilesInLocationJson;
		}
		

		private JSONObject getContentsAsJsonRecursive(File iDirectory, int levelToRecurse)
				throws IOException {
//			System.out.println("getContentsAsJsonRecursive() - begin");
			JSONObject rFilesInLocationJson = new JSONObject();
			JSONObject dirsJson = new JSONObject();
			if (levelToRecurse > 0) {
				for (Path aFilePath : getDirectoryStreamRecursive(iDirectory)) {
//					System.out
//							.println("getContentsAsJsonRecursive() - dir loop - "
//									+ aFilePath);
					if (!Files.isDirectory(aFilePath)) {
						continue;
					}
//					System.out
//							.println("getContentsAsJsonRecursive() - dir loop - recursing into "
//									+ aFilePath);
					dirsJson.put(aFilePath.toAbsolutePath().toString(),getContentsAsJsonRecursive(
							aFilePath.toFile(), --levelToRecurse));
				}
			}
//			System.out.println("getContentsAsJsonRecursive() - aFilePath - finished recursing");
			rFilesInLocationJson.put("dirs", dirsJson);
			for (Path aFilePath : getSubdirectoryStream(iDirectory)) {
				String filename = aFilePath.getFileName().toString();
				String fileAbsolutePath = aFilePath.toAbsolutePath().toString();
//				System.out.println("getContentsAsJsonRecursive() file loop: " + fileAbsolutePath);
				if (Files.isDirectory(aFilePath)) {
					continue;
				} 
				if (filename.contains("DS_Store")) {
					continue;
				}
				if (filename.endsWith(".html") || filename.endsWith(".htm")
						|| iDirectory.getName().endsWith("_files")) {
//					System.out.println("Not supported yet: " + filename);
					continue;
				}
//				System.out.println("getContentsAsJsonRecursive() file loop; not a dir: " + fileAbsolutePath);
//				System.out.println("getContentsAsJsonRecursive() file loop; parent dir: " + iDirectory.getAbsolutePath());
				String thumbnailFileAbsolutePath;
				_1: {
					thumbnailFileAbsolutePath = iDirectory.getAbsolutePath() + "/_thumbnails/" + filename + ".jpg";
				}
//				System.out.println("getContentsAsJsonRecursive() file loop; got thumbnail path for " + fileAbsolutePath);
				JSONObject fileEntryJson;
				_2: {
					JSONObject rFileEntryJson = new JSONObject();
					rFileEntryJson
							.put("location", iDirectory.getAbsolutePath());
//					System.out.println("getContentsAsJsonRecursive() file loop; setting file path path for " + fileAbsolutePath);
					rFileEntryJson.put("fileSystem", fileAbsolutePath);
//					System.out.println("getContentsAsJsonRecursive() file loop; getting http url: " + fileAbsolutePath);
					rFileEntryJson
							.put("httpUrl", httpLinkFor(fileAbsolutePath));
					rFileEntryJson.put("thumbnailUrl",
							httpLinkFor(thumbnailFileAbsolutePath));
					fileEntryJson = rFileEntryJson;
				}
				rFilesInLocationJson.put(fileAbsolutePath, fileEntryJson);
				
			}
//			System.out.println("getContentsAsJsonRecursive() - finished file loop");
			return rFilesInLocationJson;
		}
		
		private DirectoryStream<Path> getSubdirectoryStream2(File aDirectory)
				throws IOException {
			String absolutePath = aDirectory.getAbsolutePath();
			Path aDirectoryPath = Paths.get(absolutePath);
			return getDirectoryStream2(aDirectoryPath);
		}

		
		private DirectoryStream<Path> getSubdirectoryStream(File aDirectory)
				throws IOException {
			String absolutePath = aDirectory.getAbsolutePath();
			Path aDirectoryPath = Paths.get(absolutePath);
			return getDirectoryStream(aDirectoryPath);
		}

		
		private DirectoryStream<Path> getDirectoryStream(File aDirectory)
				throws IOException {
			String absolutePath = aDirectory.getAbsolutePath();
			Path aDirectoryPath = Paths.get(absolutePath);
			return getDirectoryStream(aDirectoryPath);
		}
		
		private DirectoryStream<Path> getDirectoryStreamRecursive(File aDirectory)
				throws IOException {
			String absolutePath = aDirectory.getAbsolutePath();
			Path aDirectoryPath = Paths.get(absolutePath);
			return getSubdirectoryStreamRecursive(aDirectoryPath);
		}

		private DirectoryStream<Path> getSubdirectoryStreamRecursive(Path iDirectoryPath)
				throws IOException {
			DirectoryStream<Path> rDirectoryStream = Files
					.newDirectoryStream(iDirectoryPath,
							new DirectoryStream.Filter<Path>() {
								public boolean accept(Path entry)
										throws IOException {
									if (entry.endsWith("_thumbnails")) {
										return false;
									}
									return Files.isDirectory(entry);

								}
							});
			return rDirectoryStream;
		}
		
		private DirectoryStream<Path> getSubdirectoryStream(Path iDirectoryPath)
				throws IOException {
			DirectoryStream<Path> rDirectoryStream = Files
					.newDirectoryStream(iDirectoryPath,
							new DirectoryStream.Filter<Path>() {
								public boolean accept(Path entry)
										throws IOException {
									return Files.isDirectory(entry);

								}
							});
			return rDirectoryStream;
		}
		
		private DirectoryStream<Path> getDirectoryStream2(Path iDirectoryPath)
				throws IOException {
			DirectoryStream<Path> rDirectoryStream = Files
					.newDirectoryStream(iDirectoryPath,
							new DirectoryStream.Filter<Path>() {
								public boolean accept(Path entry)
										throws IOException {
									return Files.isDirectory(entry);
								}
							});
			return rDirectoryStream;
		}

		
		private DirectoryStream<Path> getDirectoryStream(Path iDirectoryPath)
				throws IOException {
			DirectoryStream<Path> rDirectoryStream = Files
					.newDirectoryStream(iDirectoryPath,
							new DirectoryStream.Filter<Path>() {
								public boolean accept(Path entry)
										throws IOException {
									return !Files.isDirectory(entry);
								}
							});
			return rDirectoryStream;
		}


		private String httpLinkFor(String iAbsolutePath) {
			//String domain = "http://netgear.rohidekar.com";
			String domain = "http://192.168.1.2";
			// Unsorted
			String rHttpUrl = iAbsolutePath.replaceFirst("/Volumes/Unsorted/",
					domain + ":8020/");
			rHttpUrl = rHttpUrl.replaceFirst("/media/sarnobat/Unsorted/",
					domain + ":8020/");

			// Record
                        rHttpUrl = rHttpUrl.replaceFirst("/media/sarnobat/Record/",
                                        domain + ":8024/");
                        rHttpUrl = rHttpUrl.replaceFirst("/Volumes/Record/",
                                        domain + ":8024/");
                        rHttpUrl = rHttpUrl.replaceFirst("/Record/",
                                        domain + ":8024/");

			// Large
			rHttpUrl = rHttpUrl.replaceFirst("/media/sarnobat/Large/",
					domain + ":8021/");
			rHttpUrl = rHttpUrl.replaceFirst("/Volumes/Large/",
					domain + ":8021/");

			rHttpUrl = rHttpUrl.replaceFirst(".*/e/Sridhar/Photos",
					domain + ":8022/");

			// Books
			rHttpUrl = rHttpUrl.replaceFirst(".*/e/Sridhar/Books", 
					domain + ":8023/");

                        rHttpUrl = rHttpUrl.replaceFirst(".*/e/new",
                                        domain + ":8025/");

   			rHttpUrl = rHttpUrl.replaceFirst(".*/e/Drive J",
                                        domain + ":8026/");

			// 3TB
			rHttpUrl = rHttpUrl.replaceFirst("/media/sarnobat/3TB/",
					domain + ":8027/");
			rHttpUrl = rHttpUrl.replaceFirst("/3TB/",
					domain + ":8027/");
			rHttpUrl = rHttpUrl.replaceFirst("/Volumes/3TB/",
					domain + ":8027/");

			return rHttpUrl;
		}

		// TODO: bad. Do not use output parameters. Return it instead.
		private void addDirs(File iDir, JSONObject oLocationDetails,
				Collection<String> iDirsWithBoundKey) throws JSONException {
			JSONObject containedDirsJson = new JSONObject();

			for (File file : getDirectories(iDir)) {
				if (file.getName().endsWith("_files")) {
					continue;
				}
				if (iDirsWithBoundKey.contains(file.getName())) {
					// continue;
				}
				containedDirsJson.put(file.getName(), "");
			}
			oLocationDetails.put("dirs", containedDirsJson);
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
		try {
			JdkHttpServerFactory.createHttpServer(new URI(
					"http://localhost:4451/"), new ResourceConfig(
					MyResource.class));
		} catch (Exception e) {
			System.out.println("Port already listened on.");
		}
	}
}
