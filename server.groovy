import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileFilter;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.StringTokenizer;
import java.util.TimeZone;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.annotation.Nullable;
import javax.ws.rs.GET;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.StreamingOutput;

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
import org.apache.sshd.ClientSession;
import org.apache.sshd.SshClient;
import org.apache.sshd.client.SftpClient;
import org.glassfish.jersey.jdkhttp.JdkHttpServerFactory;
import org.glassfish.jersey.server.ResourceConfig;
import org.json.JSONException;
import org.json.JSONObject;

import com.google.api.client.util.IOUtils;
import com.google.common.base.Predicate;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
//import com.jcraft.jsch.ChannelExec;
//import com.jcraft.jsch.ChannelSftp;
//import com.jcraft.jsch.JSch;
//import com.jcraft.jsch.Session;

public class Coagulate {
	@javax.ws.rs.Path("cmsfs")
	public static class MyResource { // Must be public
//		private static final String SFTPHOST = "netgear.rohidekar.com";
//		private static final String clientIdRSAPath = "/Users/sarnobat/.ssh/id_rsa";
//		private static final int SFTPPORT = 22;
//		private static final String SFTPUSER = "sarnobat";
		
//	    private static final JSch jsch = new JSch();
//	    private static Session session;
//	    private static synchronized Session getSession() throws Exception {
//	        try {
//	        	System.out.println("getSession() - 1" + getSessionStatus(session));
//	        	
//	        	ChannelExec testChannel = (ChannelExec) session.openChannel("shell");
//	        	System.out.println("getSession() - 2" + getSessionStatus(session));
//	            testChannel.setCommand("true");
//	        	System.out.println("getSession() - 3" + getSessionStatus(session));
//	            testChannel.connect();
//	        	System.out.println("getSession() - 4" + getSessionStatus(session));
//	            testChannel.disconnect();
//	        	System.out.println("getSession() - 5" + getSessionStatus(session));
//	        } catch (Throwable t) {
//	        	if (session == null) {
//		            session = jsch.getSession(SFTPUSER, SFTPHOST, SFTPPORT);
//	        	}
//	        	if (!Paths.get(clientIdRSAPath).toFile().exists()) {
//	        		throw new RuntimeException("No such file: " + clientIdRSAPath);
//	        	}
//	        	System.out.println("getSession() - 6" + getSessionStatus(session));
//	        	jsch.addIdentity(clientIdRSAPath);
//	        	System.out.println("getSession() - 7" + getSessionStatus(session));
//	            java.util.Properties config = new java.util.Properties();
//	        	System.out.println("getSession() - 8" + getSessionStatus(session));
//				config.put("StrictHostKeyChecking", "no");
//	        	System.out.println("getSession() - 9" + getSessionStatus(session));
//	            session.setConfig(config);
//	        	System.out.println("getSession() - 10" + getSessionStatus(session));
//	            if (!session.isConnected()) {
//	            	session.connect();
//		        	System.out.println("getSession() - 11" + getSessionStatus(session));
//	            }
//	        }
//	        return session;
//	    }
//	    
//		private static String getSessionStatus(Session session) {
//			return "\tsession connected = "+session.isConnected() + "::server alive count max = " + session.getServerAliveCountMax() + "::server alive interval = " + session.getServerAliveInterval();
//		}

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
		}

		private Path getDestinationFilePathAvoidingExisting(Path sourceFile)
				throws IllegalAccessError {
			String filename = sourceFile.getFileName().toString();
			Path parent = sourceFile.getParent().getParent().toAbsolutePath();
			String parentPath = parent.toAbsolutePath().toString();
			String destinationFilePath = parentPath + "/" + filename;
			return determineDestinationPathAvoidingExisting(destinationFilePath);
		}

		@GET
		@javax.ws.rs.Path("static2/{absolutePath : .+}")
		@Produces("application/json")
		public Response getFileSsh(@PathParam("absolutePath") String absolutePathWithSlashMissing, @Context HttpHeaders header){
			System.out.println("getFileSsh() - begin\t" + absolutePathWithSlashMissing);
			Object entity = "{ 'foo' : 'bar' }";
			String mimeType = "application/json";
			final String absolutePath = "/" +absolutePathWithSlashMissing;
			final List<String> whitelisted = ImmutableList
					.of("/media/sarnobat/Large/Videos/",
							"/media/sarnobat/Unsorted/images/",
							"/media/sarnobat/Unsorted/Videos/",
							"/media/sarnobat/e/Sridhar/Photos/camera phone photos/iPhone/",
							"/e/new/",
							"/media/sarnobat/e/Drive J/",
							"/media/sarnobat/3TB/jungledisk_sync_final/sync3/jungledisk_sync_final/misc");
			if (FluentIterable.from(ImmutableList.copyOf(whitelisted)).anyMatch(IS_UNDER(absolutePath))){
				try {
					final SftpClient sftp = getClient();
					
//					final ChannelSftp sftp = getChannelSftp();
//					sftp.cd(Paths.get(absolutePath).getParent().toAbsolutePath().toString());
//					String fileSimpleName = Paths.get(absolutePath)
//							.getFileName().toString();
					System.out.println("getFileSsh() - 1" + getStatus(sftp));
					final InputStream is = sftp.read(absolutePath);
					System.out.println("getFileSsh() - 2"+ getStatus(sftp));
					StreamingOutput stream = new StreamingOutput() {
					    @Override
					    public void write(OutputStream os) throws IOException,
					    WebApplicationException {
					    	System.out.println("Start");
					    	System.out.println("getFileSsh() - 3"+ getStatus(sftp));
					      IOUtils.copy(is, os);
					      System.out.println("getFileSsh() - 4"+ getStatus(sftp));
					      is.close();
					      System.out.println("getFileSsh() - 5"+ getStatus(sftp));
					      os.close();
					      System.out.println("getFileSsh() - 6"+ getStatus(sftp));
//					      sftp.disconnect();
					      System.out.println("getFileSsh() - 7"+ getStatus(sftp));
//					      sftp.exit();
//					      client.close();
//					      session.close(false);
//					      sftp.close();
					      System.out.println("getFileSsh() - 8"+ getStatus(sftp));
					      System.out.println("getFileSsh() - served\t" + absolutePath);
					      System.out.println("Done");
					    }

					  };
					  
					return Response.ok().entity(stream).type(FileServerGroovy.getMimeType(absolutePath)).build();
				} catch (Exception e) {
					e.printStackTrace();
				}
			} else {
				System.out.println("Not whitelisted: " + absolutePath);
			}
			return Response.serverError()
					.header("Access-Control-Allow-Origin", "*")
					.entity(entity).type(mimeType)
					.build();
		}

		private static synchronized SftpClient getClient() throws InterruptedException,
				IOException {
//			if (sftp == null) {
				client = SshClient.setUpDefaultClient();
				client.start();
				session = client
						.connect("sarnobat", "netgear.rohidekar.com", 22)
						.await().getSession();
				// TODO: Use key authentication instead
				session.addPasswordIdentity("aize2F");
				session.auth().await();
				sftp = session.createSftpClient();
//			} else {
//				sftp = session.createSftpClient();
//			}
			return sftp;
		}
		private static SshClient client;
		private static ClientSession session ;
		private static SftpClient sftp ;

		private static String getStatus(SftpClient sftp) {
//			return sftp.isConnected() + "::" + sftp.isClosed();
			return "";
		}
		
		private static Predicate<String> IS_UNDER(final String absolutePath) {
			Predicate<String> IS_UNDER = new Predicate<String>() {
				@Override
				public boolean apply(@Nullable String permittedDirectory) {
					if (absolutePath.startsWith(permittedDirectory)) {
						return true;
					}
					if (absolutePath.startsWith(permittedDirectory.replace("/media/sarnobat",""))) {
						return true;
					}
					if (absolutePath.replace("/media/sarnobat","").startsWith(permittedDirectory)) {
						return true;
					}
					return false;
				}};
			return IS_UNDER;
		}
		
//		private static ChannelSftp getChannelSftp() {
//			System.out.println("getChannelSftp() - begin. Getting session.");
//			try {
//				Session session = getSession();
//				System.out.println("getChannelSftp() - got session, about to open channel");
//				ChannelSftp openChannel = (ChannelSftp) session.openChannel("sftp");
//				System.out.println("getChannelSftp() - checking if connected");
//				if (!openChannel.isConnected()) {
//					System.out.println("getChannelSftp() - not connected, connecting");
//					openChannel.connect();
//					System.out.println("getChannelSftp() - connected");
//				}
//				System.out.println("getChannelSftp() - end");
//				return openChannel;
//			} catch (Exception e) {
//				e.printStackTrace();
//				throw new RuntimeException(e);
//			}
//		}

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
							"/media/sarnobat/e/Drive J/",
							"/media/sarnobat/3TB/jungledisk_sync_final/sync3/jungledisk_sync_final/misc");
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
			System.out.println("move() - begin");
			if (iFilePath.endsWith("htm") || iFilePath.endsWith(".html")) {
				throw new RuntimeException("Need to move the _files folder too");
			}
			System.out.println("move() - 2");
			if (iDestinationDirSimpleName.equals("_ 1")) {
				System.out.println("move() - dir name is wrong");
				throw new RuntimeException("dir name is wrong: " + iDestinationDirSimpleName);
			}
//			System.out.println("move() - 3");
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
			System.out.println("moveFileToSubfolder() - begin");
			Path sourceFilePath = Paths.get(filePath);
			if (!Files.exists(sourceFilePath)) {
				throw new RuntimeException("No such source file");
			}
			Path targetDir = Paths.get(sourceFilePath.getParent().toString()
					+ "/" + iSubfolderSimpleName);
			if (!Files.exists(targetDir)) {
				System.out.println("moveFileToSubfolder() - creating dir " + targetDir.toString());
				Files.createDirectory(targetDir);
			} else if (!Files.isDirectory(targetDir)) {
				throw new RuntimeException("Target is an existing file");
			}
			if (fileAlreadyInDesiredSubdir(iSubfolderSimpleName, sourceFilePath)) {
//				System.out.println("Not moving to self");
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
			String parentDirPath = path.getParent().toAbsolutePath().toString();
			String destinationFolderPath = parentDirPath + "/" + folderName;
			Path subfolder = getOrCreateDestinationFolder(destinationFolderPath);
			return allocateFile(path, subfolder);
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
//			rResponse.put("locations",
//					createLocationsJson(iDirectoryPathStrings));
			rResponse.put("subdirectories",
					createSubdirectoriesJson(iDirectoryPathStrings));
			System.out.println("createListJson() - end");
			return rResponse;
		}

		private JSONObject createSubdirectoriesJson(
				String[] iDirectoryPathStrings) {
			JSONObject rItemsJson = new JSONObject();
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
			return rItemsJson;
		}

		private JSONObject createSubdirDetailsJson2(String iDirectoryPathString) throws IOException {
			return getSubdirsAsJson2(new File(iDirectoryPathString));
		}

		private JSONObject createFilesJsonRecursive(String[] iDirectoryPathStrings)
				throws IOException {
			System.out.println("createFilesJsonRecursive() - begin");
			JSONObject rItemsJson = new JSONObject();
			for (String aDirectoryPathString : iDirectoryPathStrings) {
				if (!shouldGetContents(aDirectoryPathString)) {
					continue;
				}
				rItemsJson.put(aDirectoryPathString,
						createItemDetailsJsonRecursive(aDirectoryPathString));
			}
			System.out.println("createFilesJsonRecursive() - end");
			return rItemsJson;
		}

		private JSONObject createFilesJson(String[] iDirectoryPathStrings)
				throws IOException {
			System.out.println("createFilesJson() - begin");
			JSONObject rItemsJson = new JSONObject();
			for (String aDirectoryPathString : iDirectoryPathStrings) {
				if (!shouldGetContents(aDirectoryPathString)) {
					continue;
				}
				rItemsJson.put(aDirectoryPathString,
						createItemDetailsJson(aDirectoryPathString));
			}
			System.out.println("createFilesJson() - end");
			return rItemsJson;
		}

		private JSONObject createLocationsJson(String[] iDirectoryPathStrings)
				throws IOException {
			JSONObject rLocationsJson = new JSONObject();
			for (String aDirectoryPathString : iDirectoryPathStrings) {
				if (!shouldGetContents(aDirectoryPathString)) {
					continue;
				}
				rLocationsJson.put(aDirectoryPathString,
						createLocationDetailsJson(aDirectoryPathString));
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
		
		@SuppressWarnings("unused")
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
				return false;
			}
			File aDirectory = new File(iDirectoryPathString);
			if (!aDirectory.exists()) {
				return false;
			}
			if (!aDirectory.isDirectory()) {
				return false;
			}
			return true;
		}

		@SuppressWarnings("unused")
		private JSONObject getSubdirsAsJson2(File iDirectory)
				throws IOException {
			JSONObject rFilesInLocationJson = new JSONObject();
			DirectoryStream<Path> subdirectoryStream2 = getSubdirectoryStream2(iDirectory);
			for (Path aFilePath : subdirectoryStream2) {
				String filename = aFilePath.getFileName().toString();
				String fileAbsolutePath = aFilePath.toAbsolutePath().toString();
				if (filename.contains("DS_Store")) {
					continue;
				}
				if (filename.endsWith(".html") || filename.endsWith(".htm")
						|| iDirectory.getName().endsWith("_files")) {
					continue;
				}
				String thumbnailFileAbsolutePath = iDirectory.getAbsolutePath() + "/_thumbnails/" + filename + ".jpg";
				JSONObject fileEntryJson ;
				_2: {
					JSONObject rFileEntryJson = new JSONObject();
					rFileEntryJson
							.put("location", iDirectory.getAbsolutePath());
					rFileEntryJson.put("fileSystem", fileAbsolutePath);
					rFileEntryJson
							.put("httpUrl", httpLinkFor(fileAbsolutePath));
					rFileEntryJson.put("thumbnailUrl",
							httpLinkFor(thumbnailFileAbsolutePath));
					fileEntryJson = rFileEntryJson;
				}
				rFilesInLocationJson.put(fileAbsolutePath, fileEntryJson);
			}
			subdirectoryStream2.close();
			return rFilesInLocationJson;
		}
		
		
		@SuppressWarnings("unused")
		private JSONObject getContentsAsJson(File iDirectory)
				throws IOException {
			System.out.println("getContentsAsJson() - begin");
			JSONObject rFilesInLocationJson = new JSONObject();
			DirectoryStream<Path> directoryStream = getDirectoryStream(iDirectory);
			for (Path aFilePath : directoryStream) {
				String filename = aFilePath.getFileName().toString();
				String fileAbsolutePath = aFilePath.toAbsolutePath().toString();
				if (filename.contains("DS_Store")) {
					continue;
				}
				if (filename.endsWith(".html") || filename.endsWith(".htm")
						|| iDirectory.getName().endsWith("_files")) {
					continue;
				}
				String thumbnailFileAbsolutePath = iDirectory.getAbsolutePath() + "/_thumbnails/" + filename + ".jpg"; 
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
			}
			directoryStream.close();
			System.out.println("getContentsAsJson() - end");
			return rFilesInLocationJson;
		}
		

		private static final int SUBDIRS_LIMIT = 20;
		private static final int FILES_PER_DIR_LIMIT = 20;
		@SuppressWarnings("unused")
		private JSONObject getContentsAsJsonRecursive(File iDirectory, int iLevelToRecurse)
				throws IOException {
			int levelToRecurse = iLevelToRecurse - 1;
			JSONObject rFilesInLocationJson = new JSONObject();
			JSONObject dirsJson = new JSONObject();
			System.out.println();
			System.out.println("getContentsAsJsonRecursive() - " + iDirectory.toString());
			int  i = 0;
			DirectoryStream<Path> directoryStreamRecursive = getDirectoryStreamRecursive(iDirectory);
			for (Path aFilePath : directoryStreamRecursive) {
				if (!Files.isDirectory(aFilePath)) {
					continue;
				}
				System.out.print("d");
				if (levelToRecurse > 0 || aFilePath.getFileName().toString().startsWith("_")) {
//					if (i > SUBDIRS_LIMIT) {
//						break;
//					}
					dirsJson.put(
							aFilePath.toAbsolutePath().toString(),
							getContentsAsJsonRecursive(aFilePath.toFile(),
									levelToRecurse));
					++i;
				}
			}
			directoryStreamRecursive.close();
			System.out.println();
			rFilesInLocationJson.put("dirs", dirsJson);
			int j = 0;
			DirectoryStream<Path> subdirectoryStream = getSubdirectoryStream(iDirectory);
			for (Path aFilePath : subdirectoryStream) {
				String filename = aFilePath.getFileName().toString();
				String fileAbsolutePath = aFilePath.toAbsolutePath().toString();
				if (Files.isDirectory(aFilePath)) {
					continue;
				} 
				if (filename.contains("DS_Store")) {
					continue;
				}
				if (filename.endsWith(".html") || filename.endsWith(".htm")
						|| iDirectory.getName().endsWith("_files")) {
					continue;
				}
				if (!iDirectory.getName().startsWith("_+")) {
					if (j > FILES_PER_DIR_LIMIT) {
						break;
					}
				}
				System.out.print("f");
				String thumbnailFileAbsolutePath = iDirectory.getAbsolutePath() + "/_thumbnails/" + filename + ".jpg";
				JSONObject fileEntryJson;
				_2: {
					JSONObject rFileEntryJson = new JSONObject();
					rFileEntryJson
							.put("location", iDirectory.getAbsolutePath());
					rFileEntryJson.put("fileSystem", fileAbsolutePath);
					rFileEntryJson
							.put("httpUrl", httpLinkFor(fileAbsolutePath));
					rFileEntryJson.put("thumbnailUrl",
							httpLinkFor(thumbnailFileAbsolutePath));
					fileEntryJson = rFileEntryJson;
					++j;
				}
				if (filename.matches("(?i).*jpg")) {
					JSONObject exifJson = getExifData(aFilePath);
					fileEntryJson.put("exif", exifJson);
				}
				rFilesInLocationJson.put(fileAbsolutePath, fileEntryJson);
			}
			subdirectoryStream.close();
			
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
			} catch (IOException e) {
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
			String prefix = "http://192.168.1.2:4451/cmsfs/static2/";
			return prefix + iAbsolutePath;
		}

		
		@SuppressWarnings("unused")
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
		 * HTTP response.
		 * Return one of these from serve().
		 */
		public static class Response {

			public Response (String status, String mimeType, InputStream data) {
				this(status, mimeType);
				this.data = data;
			}

			public Response (String status, String mimeType) {
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
		@SuppressWarnings("unused")
		public static final String HTTP_BADREQUEST = "400 Bad Request";
		public static final String HTTP_INTERNALERROR = "500 Internal Server Error";
		@SuppressWarnings("unused")
		public static final String HTTP_NOTIMPLEMENTED = "501 Not Implemented";

		/**
		 * Common mime types for dynamic content
		 */
		public static final String MIME_PLAINTEXT = "text/plain";
		public static final String MIME_HTML = "text/html";
		public static final String MIME_DEFAULT_BINARY = "application/octet-stream";

		// ==================================================
		// File server code
		// ==================================================
		
		/**
		 * (Rewritten without mutable state) 
		 *
		 * Serves file from homeDir and its' subdirectories (only).
		 * Uses only URI, ignores all headers and HTTP parameters.
		 */
		@Deprecated // This only works for local files. We should serve files through SSH.
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

		@Deprecated // Use {@link #serveRegularFileViaSsh}
		private static Response serveRegularFile(File file, Properties header) {
			try {
				String mimeType = getMimeTypeFromFile(file);
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

		private static Response serveFileChunk(File file, String mime,
				String etag, String range, long startFrom)
				throws FileNotFoundException, IOException {
			boolean invalidRangeRequested = startFrom >= file.length();
			long endRangeAt = getEndRangeAt(getEndAt(range), file.length(),
					invalidRangeRequested);
			long newLen = getNewLength(startFrom, invalidRangeRequested,
					endRangeAt);
			Response response = new Response(getStatus(invalidRangeRequested),
					getMimeTypeForRange(mime, invalidRangeRequested), getEntity(file,
							startFrom, invalidRangeRequested, newLen));
			if (hasContentLength(invalidRangeRequested)) {
				response.addHeader("Content-Length",
						"" + getContentLength(invalidRangeRequested, newLen));
			}
			response.addHeader("ETag", etag);
			response.addHeader("Content-Range", getContentRange(startFrom,
					file.length(), invalidRangeRequested, endRangeAt));
			response.addHeader("Accept-Ranges", "bytes"); // Announce that the file server accepts partial content requests
			return response;
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
		private static String getMimeTypeFromFile(File regularFile) throws IOException {
			if (regularFile.isDirectory()) {
				throw new RuntimeException("Developer error");
			}
			String fileFullPath = regularFile.getCanonicalPath();
			return getMimeType(fileFullPath);
		}

		public static String getMimeType(String fileFullPath) {
			String mime = null;
			// Get MIME type from file name extension, if possible
			int dot = fileFullPath.lastIndexOf('.');
			if (dot >= 0) {
				mime = (String) theMimeTypes.get(fileFullPath
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
				// TODO: we need to get the list of files from a stream over SSH
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
			res.addHeader("Location", "/");
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

		@Deprecated // Use {@link #serveEntireFileViaSsh}
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
				 boolean invalidRangeRequested) {
			boolean rangeContainsEndOfData = endAt < 0;
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

		private static String getMimeTypeForRange(String mime,
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



		private static String listDirectoryAsHtml(String uri, File directory, String[] files) {
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
					File curFile = new File(directory, filenameBefore);
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
			String path = filenameAfter;//encodeUri(uri); + Paths.get(filenameAfter).getFileName().toString());
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
		private static Hashtable<String, String> theMimeTypes = new Hashtable<String, String>();

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

		private static java.text.SimpleDateFormat gmtFrmt;

		static {
			gmtFrmt = new java.text.SimpleDateFormat("E, d MMM yyyy HH:mm:ss 'GMT'", Locale.US);
			gmtFrmt.setTimeZone(TimeZone.getTimeZone("GMT"));
		}
	}
	

	public static void main(String[] args) throws URISyntaxException {
	// Turn off log4j which sshd spews out
		Handler[] handlers = Logger.getLogger("").getHandlers();
		for (int index = 0; index < handlers.length; index++) {
			handlers[index].setLevel(Level.SEVERE);
		}
		try {
			JdkHttpServerFactory.createHttpServer(new URI(
					"http://localhost:4451/"), new ResourceConfig(
					MyResource.class));
		} catch (Exception e) {
			System.out.println("Port already listened on.");
		}
	}
}
