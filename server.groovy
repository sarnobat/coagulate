
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileFilter;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.Date;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.StringTokenizer;
import java.util.TimeZone;
import java.util.Vector;

import javax.annotation.Nullable;
import javax.ws.rs.GET;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.Response;

import org.apache.commons.imaging.ImageReadException;
import org.apache.commons.imaging.Imaging;
import org.apache.commons.imaging.common.IImageMetadata;
import org.apache.commons.imaging.formats.jpeg.JpegImageMetadata;
import org.apache.commons.imaging.formats.tiff.TiffField;
import org.apache.commons.imaging.formats.tiff.constants.GpsTagConstants;
import org.apache.commons.imaging.formats.tiff.constants.TiffTagConstants;
import org.apache.commons.imaging.formats.tiff.taginfos.TagInfo;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.filefilter.FileFilterUtils;
import org.glassfish.jersey.jdkhttp.JdkHttpServerFactory;
import org.glassfish.jersey.server.ResourceConfig;
import org.json.JSONException;
import org.json.JSONObject;

import com.google.common.base.Predicate;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

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
		@javax.ws.rs.Path("static/{absolutePath : .+}")
		@Produces("application/json")
		public Response getFile(@PathParam("absolutePath") String absolutePath, @Context HttpHeaders header){
//			System.out.println("getFile() - begin\t" + absolutePath);
			Object entity = "{ 'foo' : 'bar' }";
			String mimeType = "application/json";
			final String absolutePath2 = "/" +absolutePath;
			final List<String> whitelisted = ImmutableList
					.of("/media/sarnobat/Large/Videos/",
							"/media/sarnobat/Unsorted/images/",
							"/media/sarnobat/Unsorted/Videos/",
							"/media/sarnobat/e/Sridhar/Photos/camera phone photos/iPhone/",
							"/e/new/",
							"/media/sarnobat/e/Drive J/");
			Predicate<String> IS_UNDER = new Predicate<String>() {
				@Override
				public boolean apply(@Nullable String permittedDirectory) {
					if (absolutePath2.startsWith(permittedDirectory)) {
						return true;
					}
					if (absolutePath2.startsWith(permittedDirectory.replace("/media/sarnobat",""))) {
						return true;
					}
					if (absolutePath2.replace("/media/sarnobat","").startsWith(permittedDirectory)) {
						return true;
					}
					return false;
				}};
			if (FluentIterable.from(ImmutableList.copyOf(whitelisted)).anyMatch(IS_UNDER)){
				try {
	
					Coagulate.FileServerGroovy.Response r = FileServerGroovy
							.serveFile(absolutePath, new Properties(),
									Paths.get("/").toFile(), true);
					//System.out.println(r.mimeType);
					mimeType = r.mimeType;
					//System.out.println(header);
					entity = r.data;
				} catch (Exception e) {
					e.printStackTrace();
				}
			} else {
				System.out.println("Not whitelisted: " + absolutePath2);
			}
			return Response.ok()
					.header("Access-Control-Allow-Origin", "*")
					.entity(entity).type(mimeType)
					.build();
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
				System.out.println("Success: file now at " + destinationFile.toAbsolutePath());
			} catch (IOException e) {
				e.printStackTrace();
				throw new IllegalAccessError("Moving did not work");
			}
		}

		private static void doCopy(Path sourceFilePath, Path destinationFilePath) {
			try {
				Files.copy(sourceFilePath, destinationFilePath);// By default, it won't
													// overwrite existing
				System.out.println("Success: copied file now at " + destinationFilePath.toAbsolutePath());
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
			JSONObject response;
			try {
				response = createListJson(iDirectoryPathsString
						.split("\\n"));
			} catch (Exception e) {
				e.printStackTrace();
				return Response.serverError()
						.header("Access-Control-Allow-Origin", "*")
						.entity("{ 'foo' : " + e.getMessage() + " }").type("application/json")
						.build();
			}
			System.out.println("list() - end");
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
			System.out.println("createListJson() - end");
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

		private JSONObject createFilesJsonRecursive(String[] iDirectoryPathStrings)
				throws IOException {
			System.out.println("createFilesJsonRecursive() - begin");
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
			System.out.println("createFilesJsonRecursive() - end");
			return rItemsJson;
		}

		private JSONObject createFilesJson(String[] iDirectoryPathStrings)
				throws IOException {
			System.out.println("createFilesJson() - begin");
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
			System.out.println("createFilesJson() - end");
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
			return getContentsAsJsonRecursive(new File(iDirectoryPathString),2);
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
			System.out.println("3 " + iDirectoryPathString);
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
		
		// I forgot what this was for
		@Deprecated
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
			System.out.println("getContentsAsJson() - begin");
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
			System.out.println("getContentsAsJson() - end");
			return rFilesInLocationJson;
		}
		

		private JSONObject getContentsAsJsonRecursive(File iDirectory, int iLevelToRecurse)
				throws IOException {
//			System.out.println("getContentsAsJsonRecursive() - begin");
			int levelToRecurse = iLevelToRecurse - 1;
			JSONObject rFilesInLocationJson = new JSONObject();
			JSONObject dirsJson = new JSONObject();
			System.out.println();
			System.out.println("getContentsAsJsonRecursive() - " + iDirectory.toString());
			for (Path aFilePath : getDirectoryStreamRecursive(iDirectory)) {
				System.out.print(".");
//					System.out
//							.println("getContentsAsJsonRecursive() - dir loop - "
//									+ aFilePath);
				if (!Files.isDirectory(aFilePath)) {
					continue;
				}
//					System.out
//							.println("getContentsAsJsonRecursive() - dir loop - recursing into "
//									+ aFilePath);
				if (levelToRecurse > 0 || aFilePath.getFileName().toString().startsWith("_")) {
					dirsJson.put(
							aFilePath.toAbsolutePath().toString(),
							getContentsAsJsonRecursive(aFilePath.toFile(),
									levelToRecurse));
				}
			}
//			System.out.println("getContentsAsJsonRecursive() - aFilePath - finished recursing");
//			System.out.println("getContentsAsJsonRecursive() - " + iDirectory.toString());
			rFilesInLocationJson.put("dirs", dirsJson);
			for (Path aFilePath : getSubdirectoryStream(iDirectory)) {
				System.out.print(",");
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
				if (filename.matches("(?i).*jpg")) {
					JSONObject exifJson = getExifData(aFilePath);
					fileEntryJson.put("exif", exifJson);
				}
				rFilesInLocationJson.put(fileAbsolutePath, fileEntryJson);
			}
//			System.out.println("getContentsAsJsonRecursive() - finished file loop");
			return rFilesInLocationJson;
		}

		private JSONObject getExifData(Path aFilePath) throws IOException {
			JSONObject exifJson = new JSONObject();
			exifJson.put("datetime",
					getTag(aFilePath, TiffTagConstants.TIFF_TAG_DATE_TIME));
			exifJson.put("orientation",
					getTag(aFilePath, TiffTagConstants.TIFF_TAG_ORIENTATION));
			exifJson.put("latitude_ref",
					getTag(aFilePath, GpsTagConstants.GPS_TAG_GPS_LATITUDE_REF));
			exifJson.put("latitude",
					getTag(aFilePath, GpsTagConstants.GPS_TAG_GPS_LATITUDE));
			exifJson.put(
					"longitude_ref",
					getTag(aFilePath, GpsTagConstants.GPS_TAG_GPS_LONGITUDE_REF));
			exifJson.put("longitude",
					getTag(aFilePath, GpsTagConstants.GPS_TAG_GPS_LONGITUDE));
			return exifJson;
		}

		// TODO: I think this is slow.
		// See if you can predetermine cases where you will get an Exception
		// We may have to limit the depth (or breadth) which I'd rather not do.
		private String getTag(Path aFilePath,
				TagInfo tagInfo) {
			String ret = "";
			try {
				IImageMetadata metadata = Imaging.getMetadata(aFilePath.toFile());

				if (metadata instanceof JpegImageMetadata) {
					JpegImageMetadata jpegMetadata = (JpegImageMetadata) metadata;
					
					TiffField field = jpegMetadata.findEXIFValueWithExactMatch(tagInfo);
					if (field == null) {
					} else {
						Map<String, String> m = getPair(jpegMetadata, tagInfo);
						String firstkey = m.keySet().toArray(new String[0])[0];
						ret = m.get(firstkey);
					}
				}
			} catch (ImageReadException e) {
				System.out.print("!");
				//e.printStackTrace();
			} catch (IOException e) {
				//e.printStackTrace();
				System.out.println(e);
			}
			return ret;
		}

		private Map<String, String> getPair(JpegImageMetadata jpegMetadata,
				TagInfo tagInfo2) {
			String name = tagInfo2.name;
			String value = jpegMetadata.findEXIFValueWithExactMatch(tagInfo2).getValueDescription();
			Map<String, String> m = ImmutableMap.of(name,value);
			return m;
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
			String prefix = "http://192.168.1.2:4451/cmsfs/static/";
			return prefix + iAbsolutePath;
		}

		private String httpLinkForOld(String iAbsolutePath) {
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

	private static class FileServerGroovy {
		// ==================================================
		// API parts
		// ==================================================

		/**
		 * Override this to customize the server.<p>
		 * <p/>
		 * (By default, this delegates to serveFile() and allows directory listing.)
		 *
		 * @param uri	Percent-decoded URI without parameters, for example "/index.cgi"
		 * @param method "GET", "POST" etc.
		 * @param parms  Parsed, percent decoded parameters from URI and, in case of POST, data.
		 * @param header Header entries, percent decoded
		 * @return HTTP response, see class Response for details
		 */
		public Response serve (String uri, String method, Properties header, Properties parms, Properties files, InputStream body) {
			//myOut.println(method + " '" + uri + "' ");

			Enumeration<?> e = header.propertyNames();
			while (e.hasMoreElements()) {
				String value = (String) e.nextElement();
				//myOut.println("  HDR: '" + value + "' = '" +header.getProperty(value) + "'");
			}
			e = parms.propertyNames();
			while (e.hasMoreElements()) {
				String value = (String) e.nextElement();
				//myOut.println("  PRM: '" + value + "' = '" +parms.getProperty(value) + "'");
			}
			e = files.propertyNames();
			while (e.hasMoreElements()) {
				String value = (String) e.nextElement();
				//myOut.println("  UPLOADED: '" + value + "' = '" +files.getProperty(value) + "'");
			}

			return serveFile(uri, header, myRootDir, true);
		}

		/**
		 * HTTP response.
		 * Return one of these from serve().
		 */
		public static class Response {
			/**
			 * Default constructor: response = HTTP_OK, data = mime = 'null'
			 */
			public Response () {
				this.status = HTTP_OK;
			}

			/**
			 * Basic constructor.
			 */
			public Response (String status, String mimeType, InputStream data) {
				this(status, mimeType);
				this.data = data;
			}

			public Response (String status, String mimeType) {
				this.status = status;
				this.mimeType = mimeType;
			}

			/**
			 * Convenience method that makes an InputStream out of
			 * given text.
			 */
			public Response (String status, String mimeType, String txt) {
				this(status, mimeType);
				try {
					this.data = new ByteArrayInputStream(txt.getBytes("UTF-8"));
				} catch (java.io.UnsupportedEncodingException uee) {
					uee.printStackTrace();
				}
			}

			public Response(String status, String mimeType, Object entity) {
				this(status, mimeType);
				if (data instanceof InputStream) {
					this.data = (InputStream) entity;
				} else if (entity instanceof String) {
					try {
						this.data = new ByteArrayInputStream(
								((String) entity).getBytes("UTF-8"));
					} catch (java.io.UnsupportedEncodingException uee) {
						uee.printStackTrace();
					}
				} else {
					throw new RuntimeException("entity not valid type");
				}
			}
			/**
			 * Adds given line to the header.
			 */
			public void addHeader (String name, String value) {
				header.put(name, value);
			}

			/**
			 * HTTP status code after processing, e.g. "200 OK", HTTP_OK
			 */
			public String status;

			/**
			 * MIME type of content, e.g. "text/html"
			 */
			public String mimeType;

			/**
			 * Data of the response, may be null.
			 */
			public InputStream data;

			/**
			 * Headers for the HTTP response. Use addHeader()
			 * to add lines.
			 */
			public Properties header = new Properties();
		}

		/**
		 * Some HTTP response status codes
		 */
		public static final String HTTP_OK = "200 OK";
		public static final String HTTP_PARTIALCONTENT = "206 Partial Content";
		public static final String HTTP_RANGE_NOT_SATISFIABLE = "416 Requested Range Not Satisfiable";
		public static final String HTTP_REDIRECT = "301 Moved Permanently";
		public static final String HTTP_NOTMODIFIED = "304 Not Modified";
		public static final String HTTP_FORBIDDEN = "403 Forbidden";
		public static final String HTTP_NOTFOUND = "404 Not Found";
		public static final String HTTP_BADREQUEST = "400 Bad Request";
		public static final String HTTP_INTERNALERROR = "500 Internal Server Error";
		public static final String HTTP_NOTIMPLEMENTED = "501 Not Implemented";

		/**
		 * Common mime types for dynamic content
		 */
		public static final String MIME_PLAINTEXT = "text/plain";
		public static final String MIME_HTML = "text/html";
		public static final String MIME_DEFAULT_BINARY = "application/octet-stream";
		public static final String MIME_XML = "text/xml";

		// ==================================================
		// Socket & server code
		// ==================================================

		public FileServerGroovy (int port) throws IOException {
			this(port, new File(".").getAbsoluteFile());
		}

		public FileServerGroovy (SocketAddress inetAddress) throws IOException {
			this(new File(".").getAbsoluteFile(), inetAddress);
		}

		public FileServerGroovy (int port, File wwwroot) throws IOException {
			this(wwwroot, new InetSocketAddress(port));
		}

		/**
		 * Starts a HTTP server to given port.<p>
		 * Throws an IOException if the socket is already in use
		 */
		public FileServerGroovy (/*int port, */File wwwroot, SocketAddress inetAddress) throws IOException {
			//        myTcpPort = port;
			this.myRootDir = wwwroot;
			//        myServerSocket = new ServerSocket(myTcpPort, inetAddress);
			myServerSocket = new ServerSocket();
			myServerSocket.bind(inetAddress);
			myThread = new Thread(new Runnable() {
				public void run () {
					try {
						while (true) {
							new HTTPSession(myServerSocket.accept());
						}
					} catch (IOException ioe) {
					}
				}
			});
			myThread.setDaemon(true);
			myThread.start();
		}


		/**
		 * Stops the server.
		 */
		public void stop () {
			try {
				myServerSocket.close();
				myThread.join();
			} catch (IOException ioe) {
			} catch (InterruptedException e) {
			}
		}
		

		/**
		 * Handles one session, i.e. parses the HTTP request
		 * and returns the response.
		 */
		private class HTTPSession implements Runnable {
			public HTTPSession (Socket s) {
				mySocket = s;
				Thread t = new Thread(this);
				t.setDaemon(true);
				t.start();
			}

			public void run () {
				try {
					InputStream is = mySocket.getInputStream();
					if (is == null) return;

					// Read the first 8192 bytes.
					// The full header should fit in here.
					// Apache's default header limit is 8KB.
					int bufsize = 8192;
					byte[] buf = new byte[bufsize];
					int rlen = is.read(buf, 0, bufsize);
					if (rlen <= 0) return;

					// Create a BufferedReader for parsing the header.
					ByteArrayInputStream hbis = new ByteArrayInputStream(buf, 0, rlen);
					BufferedReader hin = new BufferedReader(new InputStreamReader(hbis));
					Properties pre = new Properties();
					Properties parms = new Properties();
					Properties header = new Properties();
					Properties files = new Properties();

					// Decode the header into parms and header java properties
					decodeHeader(hin, pre, parms, header);
					String method = pre.getProperty("method");
					String uri = pre.getProperty("uri");

					long size = 0x7FFFFFFFFFFFFFFFl;
					String contentLength = header.getProperty("content-length");
					if (contentLength != null) {
						try {
							size = Integer.parseInt(contentLength);
						} catch (NumberFormatException ex) {
						}
					}

					// We are looking for the byte separating header from body.
					// It must be the last byte of the first two sequential new lines.
					int splitbyte = 0;
					boolean sbfound = false;
					while (splitbyte < rlen) {
						if (buf[splitbyte] == '\r' && buf[++splitbyte] == '\n' && buf[++splitbyte] == '\r' && buf[++splitbyte] == '\n') {
							sbfound = true;
							break;
						}
						splitbyte++;
					}
					splitbyte++;

					// Write the part of body already read to ByteArrayOutputStream f
					ByteArrayOutputStream f = new ByteArrayOutputStream();
					if (splitbyte < rlen) f.write(buf, splitbyte, rlen - splitbyte);

					// While Firefox sends on the first read all the data fitting
					// our buffer, Chrome and Opera sends only the headers even if
					// there is data for the body. So we do some magic here to find
					// out whether we have already consumed part of body, if we
					// have reached the end of the data to be sent or we should
					// expect the first byte of the body at the next read.
					if (splitbyte < rlen) {
						size -= rlen - splitbyte + 1;
					} else if (!sbfound || size == 0x7FFFFFFFFFFFFFFFl) {
						size = 0;
					}

					// Now read all the body and write it to f
					buf = new byte[1024 * 16];
					while (rlen >= 0 && size > 0) {
						rlen = is.read(buf, 0, 1024 * 16);
						size -= rlen;
						if (rlen > 0) {
							f.write(buf, 0, rlen);
						}
					}

					// Get the raw body as a byte []
					byte[] fbuf = f.toByteArray();

					// Create a BufferedReader for easily reading it as string.
					ByteArrayInputStream bin = new ByteArrayInputStream(fbuf);
					BufferedReader br = new BufferedReader(new InputStreamReader(bin));

					// If the method is POST, there may be parameters
					// in data section, too, read it:
					if (method.equalsIgnoreCase("POST")) {
						String contentType = "";
						String contentTypeHeader = header.getProperty("content-type");
						StringTokenizer st = new StringTokenizer(contentTypeHeader, "; ");
						if (st.hasMoreTokens()) {
							contentType = st.nextToken();
						}

						if (contentType.equalsIgnoreCase("multipart/form-data")) {
							// Handle multipart/form-data
							if (!st.hasMoreTokens()) {
								sendError(HTTP_BADREQUEST, "BAD REQUEST: Content type is multipart/form-data but boundary missing. Usage: GET /example/file.html");
							}
							String boundaryExp = st.nextToken();
							st = new StringTokenizer(boundaryExp, "=");
							if (st.countTokens() != 2) {
								sendError(HTTP_BADREQUEST, "BAD REQUEST: Content type is multipart/form-data but boundary syntax error. Usage: GET /example/file.html");
							}
							st.nextToken();
							String boundary = st.nextToken();

							decodeMultipartData(boundary, fbuf, br, parms, files);
						} else {
							// Handle application/x-www-form-urlencoded
							/*
													String postLine = "";
													char pbuf[] = new char[512];
													int read = in.read(pbuf);
													while (read >= 0 && !postLine.endsWith("\r\n")) {
														postLine += String.valueOf(pbuf, 0, read);
														read = in.read(pbuf);
													}
													postLine = postLine.trim();
													*/
							//body = postLine;
							//decodeParms(postLine, parms);
						}
					}

					if (method.equalsIgnoreCase("PUT")) {
						files.put("content", saveTmpFile(fbuf, 0, f.size()));
					}

					// Ok, now do the serve()
					Response r = serve(uri, method, header, parms, files, bin);
					if (r == null) {
						sendError(HTTP_INTERNALERROR, "SERVER INTERNAL ERROR: Serve() returned a null response.");
					} else {
						sendResponse(r.status, r.mimeType, r.header, r.data);
					}

					br.close();
					is.close();
				} catch (IOException ioe) {
					try {
						sendError(HTTP_INTERNALERROR, "SERVER INTERNAL ERROR: IOException: " + ioe.getMessage());
					} catch (Throwable t) {
					}
				} catch (InterruptedException ie) {
					// Thrown by sendError, ignore and exit the thread.
				}
			}

			/**
			 * Decodes the sent headers and loads the data into
			 * java Properties' key - value pairs
			 */
			private void decodeHeader (BufferedReader input, Properties pre, Properties parms, Properties header)
					throws InterruptedException {
				try {
					// Read the request line
					String inLine = input.readLine();
					if (inLine == null) return;
					StringTokenizer st = new StringTokenizer(inLine);
					if (!st.hasMoreTokens()) {
						sendError(HTTP_BADREQUEST, "BAD REQUEST: Syntax error. Usage: GET /example/file.html");
					}

					String method = st.nextToken();
					pre.put("method", method);

					if (!st.hasMoreTokens()) {
						sendError(HTTP_BADREQUEST, "BAD REQUEST: Missing URI. Usage: GET /example/file.html");
					}

					String uri = st.nextToken();

					// Decode parameters from the URI
					int qmi = uri.indexOf('?');
					if (qmi >= 0) {
						decodeParms(uri.substring(qmi + 1), parms);
						uri = decodePercent(uri.substring(0, qmi));
					} else {
						uri = decodePercent(uri);
					}

					// If there's another token, it's protocol version,
					// followed by HTTP headers. Ignore version but parse headers.
					// NOTE: this now forces header names lowercase since they are
					// case insensitive and vary by client.
					if (st.hasMoreTokens()) {
						String line = input.readLine();
						while (line != null && line.trim().length() > 0) {
							int p = line.indexOf(':');
							if (p >= 0) {
								header.put(line.substring(0, p).trim().toLowerCase(), line.substring(p + 1).trim());
							}
							line = input.readLine();
						}
					}

					pre.put("uri", uri);
				} catch (IOException ioe) {
					sendError(HTTP_INTERNALERROR, "SERVER INTERNAL ERROR: IOException: " + ioe.getMessage());
				}
			}

			/**
			 * Decodes the Multipart Body data and put it
			 * into java Properties' key - value pairs.
			 */
			private void decodeMultipartData (String boundary, byte[] fbuf, BufferedReader input, Properties parms, Properties files)
					throws InterruptedException {
				try {
					int[] bpositions = getBoundaryPositions(fbuf, boundary.getBytes());
					int boundarycount = 1;
					String mpline = input.readLine();
					while (mpline != null) {
						if (mpline.indexOf(boundary) == -1) {
							sendError(HTTP_BADREQUEST, "BAD REQUEST: Content type is multipart/form-data but next chunk does not start with boundary. Usage: GET /example/file.html");
						}
						boundarycount++;
						Properties item = new Properties();
						mpline = input.readLine();
						while (mpline != null && mpline.trim().length() > 0) {
							int p = mpline.indexOf(':');
							if (p != -1) {
								item.put(mpline.substring(0, p).trim().toLowerCase(), mpline.substring(p + 1).trim());
							}
							mpline = input.readLine();
						}
						if (mpline != null) {
							String contentDisposition = item.getProperty("content-disposition");
							if (contentDisposition == null) {
								sendError(HTTP_BADREQUEST, "BAD REQUEST: Content type is multipart/form-data but no content-disposition info found. Usage: GET /example/file.html");
							}
							StringTokenizer st = new StringTokenizer(contentDisposition, "; ");
							Properties disposition = new Properties();
							while (st.hasMoreTokens()) {
								String token = st.nextToken();
								int p = token.indexOf('=');
								if (p != -1) {
									disposition.put(token.substring(0, p).trim().toLowerCase(), token.substring(p + 1).trim());
								}
							}
							String pname = disposition.getProperty("name");
							pname = pname.substring(1, pname.length() - 1);

							String value = "";
							if (item.getProperty("content-type") == null) {
								while (mpline != null && mpline.indexOf(boundary) == -1) {
									mpline = input.readLine();
									if (mpline != null) {
										int d = mpline.indexOf(boundary);
										if (d == -1) {
											value += mpline;
										} else {
											value += mpline.substring(0, d - 2);
										}
									}
								}
							} else {
								if (boundarycount > bpositions.length) {
									sendError(HTTP_INTERNALERROR, "Error processing request");
								}
								int offset = stripMultipartHeaders(fbuf, bpositions[boundarycount - 2]);
								String path = saveTmpFile(fbuf, offset, bpositions[boundarycount - 1] - offset - 4);
								files.put(pname, path);
								value = disposition.getProperty("filename");
								value = value.substring(1, value.length() - 1);
								boolean enteredLoopAtLeastOnce = false;
								while (enteredLoopAtLeastOnce || (mpline != null && mpline.indexOf(boundary) == -1))
								{
									enteredLoopAtLeastOnce = true;
									mpline = input.readLine();
								}
//								do {
//									mpline = input.readLine();
//								} while (mpline != null && mpline.indexOf(boundary) == -1);
							}
							parms.put(pname, value);
						}
					}
				} catch (IOException ioe) {
					sendError(HTTP_INTERNALERROR, "SERVER INTERNAL ERROR: IOException: " + ioe.getMessage());
				}
			}

			/**
			 * Find the byte positions where multipart boundaries start.
			 */
			public int[] getBoundaryPositions (byte[] b, byte[] boundary) {
				int matchcount = 0;
				int matchbyte = -1;
				Vector matchbytes = new Vector();
				for (int i = 0; i < b.length; i++) {
					if (b[i] == boundary[matchcount]) {
						if (matchcount == 0) {
							matchbyte = i;
						}
						matchcount++;
						if (matchcount == boundary.length) {
							matchbytes.addElement(new Integer(matchbyte));
							matchcount = 0;
							matchbyte = -1;
						}
					} else {
						i -= matchcount;
						matchcount = 0;
						matchbyte = -1;
					}
				}
				int[] ret = new int[matchbytes.size()];
				for (int i = 0; i < ret.length; i++) {
					ret[i] = ((Integer) matchbytes.elementAt(i)).intValue();
				}
				return ret;
			}

			/**
			 * Retrieves the content of a sent file and saves it
			 * to a temporary file.
			 * The full path to the saved file is returned.
			 */
			private String saveTmpFile (byte[] b, int offset, int len) {
				String path = "";
				if (len > 0) {
					String tmpdir = System.getProperty("java.io.tmpdir");
					try {
						File temp = File.createTempFile("NanoHTTPD", "", new File(tmpdir));
						OutputStream fstream = new FileOutputStream(temp);
						fstream.write(b, offset, len);
						fstream.close();
						path = temp.getAbsolutePath();
					} catch (Exception e) { // Catch exception if any
						System.err.println("Error: " + e.getMessage());
					}
				}
				return path;
			}


			/**
			 * It returns the offset separating multipart file headers
			 * from the file's data.
			 */
			private int stripMultipartHeaders (byte[] b, int offset) {
				int i = 0;
				for (i = offset; i < b.length; i++) {
					if (b[i] == '\r' && b[++i] == '\n' && b[++i] == '\r' && b[++i] == '\n') {
						break;
					}
				}
				return i + 1;
			}

			/**
			 * Decodes the percent encoding scheme. <br/>
			 * For example: "an+example%20string" -> "an example string"
			 */
			private String decodePercent (String str) throws InterruptedException {
				try {
					StringBuffer sb = new StringBuffer();
					for (int i = 0; i < str.length(); i++) {
						char c = str.charAt(i);
						switch (c) {
							case '+':
								sb.append(' ');
								break;
							case '%':
								sb.append((char) Integer.parseInt(str.substring(i + 1, i + 3), 16));
								i += 2;
								break;
							default:
								sb.append(c);
								break;
						}
					}
					return sb.toString();
				} catch (Exception e) {
					sendError(HTTP_BADREQUEST, "BAD REQUEST: Bad percent-encoding.");
					return null;
				}
			}

			/**
			 * Decodes parameters in percent-encoded URI-format
			 * ( e.g. "name=Jack%20Daniels&pass=Single%20Malt" ) and
			 * adds them to given Properties. NOTE: this doesn't support multiple
			 * identical keys due to the simplicity of Properties -- if you need multiples,
			 * you might want to replace the Properties with a Hashtable of Vectors or such.
			 */
			private void decodeParms (String parms, Properties p)
					throws InterruptedException {
				if (parms == null) {
					return;
				}

				StringTokenizer st = new StringTokenizer(parms, "&");
				while (st.hasMoreTokens()) {
					String e = st.nextToken();
					int sep = e.indexOf('=');
					if (sep >= 0) {
						p.put(decodePercent(e.substring(0, sep)).trim(),
								decodePercent(e.substring(sep + 1)));
					}
				}
			}

			/**
			 * Returns an error message as a HTTP response and
			 * throws InterruptedException to stop further request processing.
			 */
			private void sendError (String status, String msg) throws InterruptedException {
				sendResponse(status, MIME_PLAINTEXT, null, new ByteArrayInputStream(msg.getBytes()));
				throw new InterruptedException();
			}

			/**
			 * Sends given response to the socket.
			 */
			private void sendResponse (String status, String mime, Properties header, InputStream data) {
				try {
					if (status == null) {
						throw new Error("sendResponse(): Status can't be null.");
					}

					OutputStream out = mySocket.getOutputStream();
					PrintWriter pw = new PrintWriter(out);
					pw.print("HTTP/1.0 " + status + " \r\n");

					if (mime != null) {
						pw.print("Content-Type: " + mime + "\r\n");
					}

					if (header == null || header.getProperty("Date") == null) {
						pw.print("Date: " + gmtFrmt.format(new Date()) + "\r\n");
					}

					if (header != null) {
						Enumeration e = header.keys();
						while (e.hasMoreElements()) {
							String key = (String) e.nextElement();
							String value = header.getProperty(key);
							pw.print(key + ": " + value + "\r\n");
						}
					}

					pw.print("\r\n");
					pw.flush();

					if (data != null) {
						int pending = data.available();	// This is to support partial sends, see serveFile()
						byte[] buff = new byte[theBufferSize];
						while (pending > 0) {
							int read = data.read(buff, 0, ((pending > theBufferSize) ? theBufferSize : pending));
							if (read <= 0) break;
							out.write(buff, 0, read);
							pending -= read;
						}
					}
					out.flush();
					out.close();
					if (data != null) {
						data.close();
					}
				} catch (IOException ioe) {
					// Couldn't write? No can do.
					try {
						mySocket.close();
					} catch (Throwable t) {
					}
				}
			}

			private Socket mySocket;
		}

		/**
		 * URL-encodes everything between "/"-characters.
		 * Encodes spaces as '%20' instead of '+'.
		 */
		static String encodeUri (String uri) {
			String newUri = "";
			StringTokenizer st = new StringTokenizer(uri, "/ ", true);
			while (st.hasMoreTokens()) {
				String tok = st.nextToken();
				if (tok.equals("/")) {
					newUri += "/";
				} else if (tok.equals(" ")) {
					newUri += "%20";
				} else {
					newUri += URLEncoder.encode(tok);
					// For Java 1.4 you'll want to use this instead:
					// try { newUri += URLEncoder.encode( tok, "UTF-8" ); } catch ( java.io.UnsupportedEncodingException uee ) {}
				}
			}
			return newUri;
		}

		private int myTcpPort;
		private final ServerSocket myServerSocket;
		private Thread myThread;
		private File myRootDir;

		// ==================================================
		// File server code
		// ==================================================
		

		/**
		 * (Rewritten without mutable state) 
		 *
		 * Serves file from homeDir and its' subdirectories (only).
		 * Uses only URI, ignores all headers and HTTP parameters.
		 */
		public static Response serveFile(String url, Properties header, File homeDir,
				boolean allowDirectoryListing) {

			if (!isDirectory(homeDir)) {
				return new Response(HTTP_INTERNALERROR, MIME_PLAINTEXT,
						"INTERNAL ERRROR: serveFile(): given homeDir is not a directory.");
			}
			String urlWithoutQueryString = removeQueryString(url);
			if (containsUpwardTraversal(urlWithoutQueryString)) {
				return new Response(HTTP_FORBIDDEN, MIME_PLAINTEXT,
						"FORBIDDEN: Won't serve ../ for security reasons.");
			}
			File requestedFileOrDir = new File(homeDir, urlWithoutQueryString);
			if (!requestedFileOrDir.exists()) {
				return new Response(HTTP_NOTFOUND, MIME_PLAINTEXT,
						"Error 404, file not found.");
			}
			
			if (requestedFileOrDir.isDirectory()) {
				return serveDirectory(header, homeDir, allowDirectoryListing,
						urlWithoutQueryString, requestedFileOrDir);
			} else {// is a regular file
				return serveRegularFile(requestedFileOrDir, header);
			}
		}

		private static Response serveDirectory(Properties header, File homeDir,
				boolean allowDirectoryListing, String urlWithoutQueryString,
				File requestedFileOrDir) {
			File requestedDir = requestedFileOrDir;
			String urlWithDirectoryPathStandardized = maybeFixTrailingSlash(urlWithoutQueryString);
			if (!urlWithoutQueryString.endsWith("/")) {
				return doRedirect(urlWithDirectoryPathStandardized);
			}
			if (containsFile(requestedDir, "index.html")) {
				return serveRegularFile(new File(homeDir,
						urlWithDirectoryPathStandardized + "/index.html"),
						header);
			} else if (containsFile(requestedDir, "index.html")) {
				return serveRegularFile(new File(homeDir,
						urlWithDirectoryPathStandardized + "/index.htm"),
						header);
			} else {
				return serveDirectory(header, homeDir,
						allowDirectoryListing, requestedDir,
						urlWithDirectoryPathStandardized);
			}
		}

		private static Response serveDirectory(Properties header, File homeDir,
				boolean allowDirectoryListing, File requestedDir,
				String urlWithDirectoryPathStandardized) {
			File fileAfterRewrite = maybeRewriteToDefaultFile(homeDir,
					requestedDir, urlWithDirectoryPathStandardized);
			if (fileAfterRewrite.isDirectory()) {
				return serveDirectory(fileAfterRewrite,
						allowDirectoryListing,
						urlWithDirectoryPathStandardized);
			} else {
				return serveRegularFile(fileAfterRewrite, header);
			}
		}

		private static Response serveRegularFile(File file, Properties header) {
			try {
				String mimeType = getMimeType(file);
				String eTag = getEtag(file);
				String range = getRange(header);
				long start = getStartOfRange(range);
				if (rangeBeginsAfterStart(range, start)) {
					return serveFileChunk(file, mimeType, eTag, range, start);
				} else {
					if (eTag.equals(header.getProperty("if-none-match"))) {
						return serveContentNotChanged(mimeType);
					} else {
						return serveEntireFile(file, mimeType, eTag, file.length());
					}
				}
			} catch (IOException e) {
				return new Response(HTTP_FORBIDDEN, MIME_PLAINTEXT,
						"FORBIDDEN: Reading file failed.");
			}
		}

		private static String getEtag(File file) {
			String etag = Integer.toHexString((file.getAbsolutePath()
					+ file.lastModified() + "" + file.length()).hashCode());
			return etag;
		}

		private static boolean rangeBeginsAfterStart(String range,
				long startFrom) {
			boolean requestingRangeWithoutBeginning = range != null && startFrom >= 0;
			return requestingRangeWithoutBeginning;
		}

		private static Response serveFileChunk(File f, String mime,
				String etag, String range, long startFrom)
				throws FileNotFoundException, IOException {
			long endAt = getEndAt(range);

			final boolean invalidRangeRequested = startFrom >= f.length();
			final boolean rangeContainsEndOfData = endAt < 0;
			String mime2 = getMimeType(mime,
					invalidRangeRequested);

			String status = getStatus(invalidRangeRequested);

			long endRangeAt = getEndRangeAt(endAt, f.length(),
					rangeContainsEndOfData,
					invalidRangeRequested);

			final long newLen = getNewLength(startFrom,
					invalidRangeRequested, endRangeAt);

			Object entity = getEntity(f, startFrom,
					invalidRangeRequested, newLen);

			String contentRange = getContentRange(startFrom,
					f.length(), invalidRangeRequested, endRangeAt);

			boolean hasContentLength = hasContentLength(invalidRangeRequested);

			long contentLength = getContentLength(
					invalidRangeRequested, newLen);

			Response res1 = new Response(status, mime2, entity);
			if (hasContentLength) {
				res1.addHeader("Content-Length", ""
						+ contentLength);
			}
			res1.addHeader("ETag", etag);
			res1.addHeader("Content-Range", contentRange);
			res1.addHeader("Accept-Ranges", "bytes"); // Announce that the file server accepts partial content requests
			return res1;
		}

		private static long getEndAt(String range) {
			long endAt = -1;
			if (range != null) {
				if (range.startsWith("bytes=")) {
					int minus = range.indexOf('-');
					try {
						if (minus > 0) {
							endAt = Long.parseLong(range.substring(minus + 1));
						}
					} catch (NumberFormatException nfe) {
					}
				}
			}
			return endAt;
		}

		private static long getStartOfRange(String range) {
			long startFrom = 0;
			if (range != null) {
				if (range.startsWith("bytes=")) {
					int minus = range.indexOf('-');
					try {
						if (minus > 0) {
							startFrom = Long.parseLong(range.substring(0, minus));
						}
					} catch (NumberFormatException nfe) {
					}
				}
			}
			return startFrom;
		}

		private static String getRange(Properties header) {
			String range = header.getProperty("range");
			if (range != null) {
				if (range.startsWith("bytes=")) {
					range = range.substring("bytes=".length());
				}
			}
			return range;
		}

		@Nullable
		private static String getMimeType(File regularFile) throws IOException {
			if (regularFile.isDirectory()) {
				throw new RuntimeException("Developer error");
			}
			String mime = null;
			// Get MIME type from file name extension, if possible
			int dot = regularFile.getCanonicalPath().lastIndexOf('.');
			if (dot >= 0) {
				mime = (String) theMimeTypes.get(regularFile.getCanonicalPath()
						.substring(dot + 1).toLowerCase());
			}
			if (mime == null) {
				mime = MIME_DEFAULT_BINARY;
			}
			return mime;
		}

		private static boolean containsUpwardTraversal(
				String uri) {
			return uri.startsWith("..") || uri.endsWith("..") || uri.indexOf("../") >= 0;
		}

		private static String removeQueryString(String url) {
			String ret;
			String uri = url.trim().replace(File.separatorChar, "/".charAt(0));
			if (uri.indexOf('?') >= 0) {
				ret = uri.substring(0, uri.indexOf('?'));
			} else {
				ret = uri;
			}
			return ret;
		}

		private static boolean isDirectory(File iFile) {
			return iFile.isDirectory();
		}

		private static boolean containsFile(File dir, String filename) {
			if (!dir.isDirectory()) {
				throw new RuntimeException("developer error");
			}
			return new File(dir, filename).exists();
		}

		private static Response serveDirectory(File dir, boolean allowDirectoryListing, String uri) {
			if (!dir.isDirectory()) {
				throw new RuntimeException("developer error");
			} 
			if (allowDirectoryListing && dir.canRead()) {
				String[] files = dir.list();
				String msg = listDirectoryAsHtml(uri, dir, files);
				return new Response(HTTP_OK, MIME_HTML, msg);
			} else {
				return new Response(HTTP_FORBIDDEN, MIME_PLAINTEXT,
						"FORBIDDEN: No directory listing.");
			}
		}

		private static Response doRedirect(
				String urlWithDirectoryPathStandardized) {
			Response res = new Response(HTTP_REDIRECT, MIME_HTML,
					"<html><body>Redirected: <a href=\"" + urlWithDirectoryPathStandardized + "\">" +
							urlWithDirectoryPathStandardized + "</a></body></html>");
			res.addHeader("Location", urlWithDirectoryPathStandardized);
			return res;
		}

		private static String maybeFixTrailingSlash(String urlWithoutQueryString) {
			String urlWithDirectoryPathStandardized;
			if (!urlWithoutQueryString.endsWith("/")) {
				urlWithDirectoryPathStandardized = addTrainingSlash(urlWithoutQueryString);
			} else {
				urlWithDirectoryPathStandardized = urlWithoutQueryString;
			}
			return urlWithDirectoryPathStandardized;
		}

		private static String addTrainingSlash(String urlWithoutQueryString) {
			return urlWithoutQueryString + '/';
		}

		private static File maybeRewriteToDefaultFile(File homeDir,
				File requestedDir, String urlWithDirectoryPathStandardized) {
			File fileAfterRewrite = requestedDir;
			if (new File(requestedDir, "index.html").exists()) {
				fileAfterRewrite = new File(homeDir,
						urlWithDirectoryPathStandardized + "/index.html");
			} else if (new File(requestedDir, "index.htm").exists()) {
				fileAfterRewrite = new File(homeDir,
						urlWithDirectoryPathStandardized + "/index.htm");
			}
			return fileAfterRewrite;
		}

		/** Just return the HTTP head */
		private static Response serveContentNotChanged(String mime) {
			Response res;
			res = new Response(HTTP_NOTMODIFIED, mime, "");
			res.addHeader("Accept-Ranges", "bytes"); // Announce that the file server accepts partial content requests
			// Do not add etag
			return res;
		}

		private static Response serveEntireFile(File f, String mime,
				String etag, final long fileLen) throws FileNotFoundException {
			Response res;
			res = new Response(HTTP_OK, mime,
					new FileInputStream(f));
			res.addHeader("Content-Length", "" + fileLen);
			res.addHeader("ETag", etag);
			res.addHeader("Accept-Ranges", "bytes"); // Announce that the file server accepts partial content requests
			return res;
		}

		private static long getContentLength(boolean invalidRangeRequested,
				final long newLen) {
			long contentLength = -1;
			if (invalidRangeRequested) {
			} else {
				contentLength = newLen;
			}
			return contentLength;
		}

		private static boolean hasContentLength(boolean invalidRangeRequested) {
			boolean hasContentLength = false;
			if (invalidRangeRequested) {
				hasContentLength = false;
			} else {
				hasContentLength = true;
			}
			return hasContentLength;
		}

		private static String getContentRange(final long startFrom,
				final long fileLen, boolean invalidRangeRequested,
				long endRangeAt) {
			String contentRange;
			if (invalidRangeRequested) {
				contentRange = "bytes 0-0/" + fileLen;
			} else {
				contentRange = "bytes " + startFrom + "-" + endRangeAt + "/" + fileLen;
			}
			return contentRange;
		}

		private static Object getEntity(File f, final long startFrom,
				boolean invalidRangeRequested, final long newLen)
				throws FileNotFoundException, IOException {
			Object entity;
			if (invalidRangeRequested) {
				entity = "";
			} else {
				entity = prepareFileInputStream(f, startFrom,
						newLen);
			}
			return entity;
		}

		private static long getEndRangeAt(long endAt, final long fileLen,
				boolean rangeContainsEndOfData, boolean invalidRangeRequested) {
			long endRangeAt;
			if (invalidRangeRequested) {
				endRangeAt = -1;
			} else {
				endRangeAt = getEndOfRange(endAt, fileLen,
						rangeContainsEndOfData);
			}
			return endRangeAt;
		}

		private static long getNewLength(final long startFrom,
				boolean invalidRangeRequested, long endRangeAt) {
			final long newLen;
			if (invalidRangeRequested) {
				newLen = -1;
			} else {
				newLen = getNewLength(startFrom, endRangeAt);
			}
			return newLen;
		}

		private static String getStatus(boolean invalidRangeRequested) {
			String status;
			if (invalidRangeRequested) {
				status = HTTP_RANGE_NOT_SATISFIABLE;
			} else {
				status = HTTP_PARTIALCONTENT;
			}
			return status;
		}

		private static String getMimeType(String mime,
				boolean invalidRangeRequested) {
			String mime2;
			if (invalidRangeRequested) {
				mime2 = MIME_PLAINTEXT;
			} else {
				mime2 = mime;
			}
			return mime2;
		}

		private static long getEndOfRange(long endAt, final long fileLen,
				boolean rangeContainsEndOfData) {
			long endRangeAt = endAt;
			if (rangeContainsEndOfData) {
				endRangeAt = fileLen - 1;
			}
			return endRangeAt;
		}

		private static long getNewLength(final long startFrom, long endRangeAt) {
			if (endRangeAt < 0) {
				throw new RuntimeException("DeveloperError");
			}
			long newLen = endRangeAt - startFrom + 1;
			if (newLen < 0) {
				newLen = 0;
			}
			return newLen;
		}

		private static FileInputStream prepareFileInputStream(File f,
				long startFrom, final long dataLen)
				throws FileNotFoundException, IOException {
			FileInputStream fis = new FileInputStream(f) {
				public int available () throws IOException {
					return (int) dataLen;
				}
			};
			fis.skip(startFrom);
			return fis;
		}

		/**
		 * Serves file from homeDir and its' subdirectories (only).
		 * Uses only URI, ignores all headers and HTTP parameters.
		 */
		@Deprecated // Mutable state. 
		public static Response serveFileOld (String uri, Properties header, File homeDir,
				boolean allowDirectoryListing) {
			Response res = null;

			// Make sure we won't die of an exception later
			if (!homeDir.isDirectory()) {
				res = new Response(HTTP_INTERNALERROR, MIME_PLAINTEXT,
						"INTERNAL ERRROR: serveFile(): given homeDir is not a directory.");
			}

			if (res == null) {
				// Remove URL arguments
				uri = uri.trim().replace(File.separatorChar, "/".charAt(0));
				if (uri.indexOf('?') >= 0) {
					uri = uri.substring(0, uri.indexOf('?'));
				}

				// Prohibit getting out of current directory
				if (uri.startsWith("..") || uri.endsWith("..") || uri.indexOf("../") >= 0) {
					res = new Response(HTTP_FORBIDDEN, MIME_PLAINTEXT,
							"FORBIDDEN: Won't serve ../ for security reasons.");
				}
			}

			File f = new File(homeDir, uri);
			if (res == null && !f.exists()) {
				res = new Response(HTTP_NOTFOUND, MIME_PLAINTEXT,
						"Error 404, file not found.");
			}

			// List the directory, if necessary
			if (res == null && f.isDirectory()) {
				// Browsers get confused without '/' after the
				// directory, send a redirect.
				if (!uri.endsWith("/")) {
					uri += "/";
					res = new Response(HTTP_REDIRECT, MIME_HTML,
							"<html><body>Redirected: <a href=\"" + uri + "\">" +
									uri + "</a></body></html>");
					res.addHeader("Location", uri);
				}

				if (res == null) {
					// First try index.html and index.htm
					if (new File(f, "index.html").exists()) {
						f = new File(homeDir, uri + "/index.html");
					} else if (new File(f, "index.htm").exists()) {
						f = new File(homeDir, uri + "/index.htm");
					}
					// No index file, list the directory if it is readable
					else if (allowDirectoryListing && f.canRead()) {
						String[] files = f.list();
						String msg = listDirectoryAsHtml(uri, f, files);
						res = new Response(HTTP_OK, MIME_HTML, msg);
					} else {
						res = new Response(HTTP_FORBIDDEN, MIME_PLAINTEXT,
								"FORBIDDEN: No directory listing.");
					}
				}
			}

			try {
				if (res == null) {
					// Get MIME type from file name extension, if possible
					String mime = null;
					int dot = f.getCanonicalPath().lastIndexOf('.');
					if (dot >= 0) {
						mime = (String) theMimeTypes.get(f.getCanonicalPath().substring(dot + 1).toLowerCase());
					}
					if (mime == null) {
						mime = MIME_DEFAULT_BINARY;
					}

					String etag = getEtag(f);

					// Support (simple) skipping:
					long startFrom = 0;
					long endAt = -1;
					String range = header.getProperty("range");
					if (range != null) {
						if (range.startsWith("bytes=")) {
							range = range.substring("bytes=".length());
							int minus = range.indexOf('-');
							try {
								if (minus > 0) {
									startFrom = Long.parseLong(range.substring(0, minus));
									endAt = Long.parseLong(range.substring(minus + 1));
								}
							} catch (NumberFormatException nfe) {
							}
						}
					}

					// Change return code and add Content-Range header when skipping is requested
					long fileLen = f.length();
					if (range != null && startFrom >= 0) {
						if (startFrom >= fileLen) {
							res = new Response(HTTP_RANGE_NOT_SATISFIABLE, MIME_PLAINTEXT, "");
							res.addHeader("Content-Range", "bytes 0-0/" + fileLen);
							res.addHeader("ETag", etag);
						} else {
							if (endAt < 0) {
								endAt = fileLen - 1;
							}
							long newLen = endAt - startFrom + 1;
							if (newLen < 0) newLen = 0;

							final long dataLen = newLen;
							FileInputStream fis = new FileInputStream(f) {
								public int available () throws IOException {
									return (int) dataLen;
								}
							};
							fis.skip(startFrom);

							res = new Response(HTTP_PARTIALCONTENT, mime, fis);
							res.addHeader("Content-Length", "" + dataLen);
							res.addHeader("Content-Range", "bytes " + startFrom + "-" + endAt + "/" + fileLen);
							res.addHeader("ETag", etag);
						}
					} else {
						if (etag.equals(header.getProperty("if-none-match"))) {
							res = new Response(HTTP_NOTMODIFIED, mime, "");
						} else {
							res = new Response(HTTP_OK, mime, new FileInputStream(f));
							res.addHeader("Content-Length", "" + fileLen);
							res.addHeader("ETag", etag);
						}
					}
				}
			} catch (IOException ioe) {
				res = new Response(HTTP_FORBIDDEN, MIME_PLAINTEXT, "FORBIDDEN: Reading file failed.");
			}

			res.addHeader("Accept-Ranges", "bytes"); // Announce that the file server accepts partial content requestes
			return res;
		}

		private static String listDirectoryAsHtml(String uri, File f, String[] files) {
			String msg = "<html><body><h1>Directory " + uri + "</h1><br/>";

			if (uri.length() > 1) {
				String u = uri.substring(0, uri.length() - 1);
				int slash = u.lastIndexOf('/');
				if (slash >= 0 && slash < u.length()) {
					msg += "<b><a href=\"" + uri.substring(0, slash + 1) + "\">..</a></b><br/>";
				}
			}

			if (files != null) {
				for (int i = 0; i < files.length; ++i) {
					String filenameBefore = files[i];
					File curFile = new File(f, filenameBefore);
					boolean dir = curFile.isDirectory();
					if (dir) {
						msg += "<b>";
						files[i] += "/";
					}

					String filenameAfter = files[i];
					msg += renderFilename(uri, filenameAfter);

					// Show file size
					msg = showfileSize2(msg, curFile);
					msg += "<br/>";
					if (dir) {
						msg += "</b>";
					}
				}
			}
			msg += "</body></html>";
			return msg;
		}

		private static String showfileSize2(String msg, File curFile) {
			if (curFile.isFile()) {
				msg = showFileSize(msg, curFile);
			}
			return msg;
		}

		/** Override this */
//		String renderFilename(String uri, String filenameAfter) {
//			return "<a href=\"" + encodeUri(uri + filenameAfter) + "\">" +
//					filenameAfter  + "</a>";
//		}


		//@Override
		static String renderFilename(String uri, String filenameAfter) {
			String path = encodeUri(uri + filenameAfter);
			String insideLink;
			if (filenameAfter.endsWith("jpg") || filenameAfter.endsWith("jpg") || filenameAfter.endsWith("gif")
					|| filenameAfter.endsWith("png")) {
				insideLink = "<img src=\"" + path + "\" width=100>" + filenameAfter;
			} else {
				insideLink = filenameAfter;
			}
			return "<a href=\"" + path + "\">" + insideLink + "</a>";
		}
		
		private static String showFileSize(String msg, File curFile) {
			long len = curFile.length();
			msg += " &nbsp;<font size=2>(";
			if (len < 1024) {
				msg += len + " bytes";
			} else if (len < 1024 * 1024) {
				long m = len % 1024;
				long l = m / 10;
				long n = l % 100;
				msg += len / 1024 + "." + n + " KB";
			} else {
				int i = 1024 * 1024;
				long l = len % i;
				long m = l / 10;
				msg += len / i + "." + m % 100 + " MB";
			}

			msg += ")</font>";
			return msg;
		}

		/**
		 * Hashtable mapping (String)FILENAME_EXTENSION -> (String)MIME_TYPE
		 */
		private static Hashtable theMimeTypes = new Hashtable();

		static {
			StringTokenizer st = new StringTokenizer(
					"css		text/css " +
							"htm		text/html " +
							"html		text/html " +
							"xml		text/xml " +
							"txt		text/plain " +
							"asc		text/plain " +
							"gif		image/gif " +
							"jpg		image/jpeg " +
							"jpeg		image/jpeg " +
							"png		image/png " +
							"mp3		audio/mpeg " +
							"m3u		audio/mpeg-url " +
							"mp4		video/mp4 " +
							"ogv		video/ogg " +
							"flv		video/x-flv " +
							"mov		video/quicktime " +
							"swf		application/x-shockwave-flash " +
							"js			application/javascript " +
							"pdf		application/pdf " +
							"doc		application/msword " +
							"ogg		application/x-ogg " +
							"zip		application/octet-stream " +
							"exe		application/octet-stream " +
							"class		application/octet-stream ");
			while (st.hasMoreTokens()) {
				theMimeTypes.put(st.nextToken(), st.nextToken());
			}
		}

		private static int theBufferSize = 16 * 1024;

		// Change this if you want to log to somewhere else than stdout
		protected static PrintStream myOut = System.out;

		/**
		 * GMT date formatter
		 */
		private static java.text.SimpleDateFormat gmtFrmt;

		static {
			gmtFrmt = new java.text.SimpleDateFormat("E, d MMM yyyy HH:mm:ss 'GMT'", Locale.US);
			gmtFrmt.setTimeZone(TimeZone.getTimeZone("GMT"));
		}

		/**
		 * The distribution licence
		 */
		private static final String LICENCE =
				"Copyright (C) 2001,2005-2011 by Jarno Elonen <elonen@iki.fi>\n" +
						"and Copyright (C) 2010 by Konstantinos Togias <info@ktogias.gr>\n" +
						"\n" +
						"Redistribution and use in source and binary forms, with or without\n" +
						"modification, are permitted provided that the following conditions\n" +
						"are met:\n" +
						"\n" +
						"Redistributions of source code must retain the above copyright notice,\n" +
						"this list of conditions and the following disclaimer. Redistributions in\n" +
						"binary form must reproduce the above copyright notice, this list of\n" +
						"conditions and the following disclaimer in the documentation and/or other\n" +
						"materials provided with the distribution. The name of the author may not\n" +
						"be used to endorse or promote products derived from this software without\n" +
						"specific prior written permission. \n" +
						" \n" +
						"THIS SOFTWARE IS PROVIDED BY THE AUTHOR ``AS IS'' AND ANY EXPRESS OR\n" +
						"IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES\n" +
						"OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED.\n" +
						"IN NO EVENT SHALL THE AUTHOR BE LIABLE FOR ANY DIRECT, INDIRECT,\n" +
						"INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT\n" +
						"NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,\n" +
						"DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY\n" +
						"THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT\n" +
						"(INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE\n" +
						"OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.";
	}
	
//	public static void main(String[] args) {
//		try {
//			new FileServerGroovy(8082,
//					Paths.get("/media/sarnobat/Unsorted/images").toFile());
//			System.out.println("Started");
//		} catch (IOException ioe) {
//			System.err.println("Couldn't start server:\n" + ioe);
//			System.exit(-1);
//		}
//		try {
//			System.in.read();
//		} catch (Throwable t) {
//			System.out.println("Exiting");
//		}
//	}
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
