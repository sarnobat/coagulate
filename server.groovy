import static com.google.common.base.Predicates.not;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.StringReader;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.DirectoryStream;
import java.nio.file.DirectoryStream.Filter;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.spi.FileSystemProvider;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.TimeZone;

import javax.annotation.Nullable;
import javax.json.Json;
import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;
import javax.json.JsonReader;
import javax.json.stream.JsonParsingException;
import javax.ws.rs.GET;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.StreamingOutput;

import net.coobird.thumbnailator.Thumbnailator;

import org.apache.commons.imaging.ImageReadException;
import org.apache.commons.imaging.Imaging;
import org.apache.commons.imaging.common.IImageMetadata;
import org.apache.commons.imaging.formats.jpeg.JpegImageMetadata;
import org.apache.commons.imaging.formats.tiff.TiffField;
import org.apache.commons.imaging.formats.tiff.constants.GpsTagConstants;
import org.apache.commons.imaging.formats.tiff.constants.TiffTagConstants;
import org.apache.commons.imaging.formats.tiff.taginfos.TagInfo;
import org.apache.commons.io.FilenameUtils;
import org.apache.sshd.ClientSession;
import org.apache.sshd.SshClient;
import org.apache.sshd.client.SftpClient;
import org.glassfish.jersey.jdkhttp.JdkHttpServerFactory;
import org.glassfish.jersey.server.ResourceConfig;
import org.json.JSONException;
import org.json.JSONObject;

import com.google.api.client.util.IOUtils;
import com.google.common.base.Function;
import com.google.common.base.Predicate;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import com.jcraft.jsch.JSchException;
import com.pastdev.jsch.DefaultSessionFactory;
import com.pastdev.jsch.command.CommandRunner.ChannelExecWrapper;
import com.pastdev.jsch.nio.file.UnixSshFileSystem;
import com.pastdev.jsch.nio.file.UnixSshFileSystemProvider;
import com.pastdev.jsch.nio.file.UnixSshPath;

/**
 * SSHD uses slf4j. So add the api + binding jars, and point to a properties file
 */
//@Grab(group='com.pastdev', module='jsch-nio', version='0.1.5')
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
			if (sourceFilePathString.endsWith("htm") || sourceFilePathString.endsWith(".html")) {
				throw new RuntimeException("Need to move the _files folder too");
			}
			Operations.doMoveToParent(sourceFilePathString);
			return Response.ok()
					.header("Access-Control-Allow-Origin", "*")
					.entity(new JSONObject().toString(4)).type("application/json")
					.build();
		}

		private final List<String> whitelisted = ImmutableList.of(
				"/e/Videos/",
				"/d/Videos/",
				"/e/Sridhar/Photos/2005-12-25 Chatting Screenshots",
				"/e/Sridhar/Photos/Skype Screenshots",
				"/e/Sridhar/Photos/screenshots",
				"/e/Sridhar/Photos/camera phone photos/iPhone/",
				"/e/Sridhar/Scans/screenshots",
				"/e/Sridhar/Web/",
				"/e/Sridhar/Atletico Madrid/",
				"/e/Sridhar UK/Atletico Madrid/",
				"/e/Sridhar UK/Photos/Cats/",
				"/e/new/",
				"/Videos/",
				"/media/sarnobat/Unsorted/images/",
				"/media/sarnobat/e/Drive J/",
				"/media/sarnobat/d/Videos",
				"/Unsorted/Videos/", 
				"/media/sarnobat/Large/Videos/",
				"/media/sarnobat/Large/Videos_Home/AVCHD/AVCHD/BDMV/STREAM",
				"/media/sarnobat/Record/Videos_Home/Home Video/small videos (non HD camcorder)/",
				"/media/sarnobat/Record/Videos_Home/Home Video/home movies (high-definition)/",
				"/media/sarnobat/3TB/jungledisk_sync_final/sync3/jungledisk_sync_final/misc");

		@GET
		@javax.ws.rs.Path("static2/{absolutePath : .+}")
		@Produces("application/json")
		// getFileViaSsh servefileoverssh
		public Response getFileSsh(@PathParam("absolutePath") String absolutePathWithSlashMissing, @Context HttpHeaders header, @QueryParam("width") final Integer iWidth){
			final String absolutePath = "/" +absolutePathWithSlashMissing;
			if (FluentIterable.from(ImmutableList.copyOf(whitelisted)).anyMatch(Predicates.IS_UNDER(absolutePath))){
				try {
//					final SftpClient sftp = getSftpClient();
					final SshClient client = getSshClient();
					final ClientSession session = getSession(client);
					final SftpClient sftp = session.createSftpClient();
					final InputStream is = sftp.read(absolutePath);
					StreamingOutput stream = new StreamingOutput() {
					    @Override
						public void write(OutputStream os) throws IOException,
								WebApplicationException {
					//		System.out.println("getFileSsh() - file to get: " + absolutePath + ", status = " + getStatus(sftp));
							// TODO: for most files, a straight copy is wanted. For images, check the file dimensions
							if (iWidth != null) {
								try {
									Thumbnailator.createThumbnail(is, os, iWidth, iWidth);
								} catch (Exception e) {
									System.out.println(e);
									e.printStackTrace();
								}
							} else {
								IOUtils.copy(is, os);
							}
							is.close();
							os.close();
							sftp.close();
							session.close(true);
							client.stop();
							System.out.print(".");
					//		System.out.println("getFileSsh() - Success. (note: if you try to leave anything open, make sure you don't end up with hundreds of sshd processes)");
						}

					  };
					  
					return Response.ok().entity(stream).type(FileServerGroovy.getMimeType(absolutePath)).build();
				} catch (Exception e) {
				//	e.printStackTrace();
					System.err.println("getFileSsh() - FAILED: " + absolutePath + ". " + e.toString());
				}
			} else {
				System.out.println("Not whitelisted: " + absolutePath);
			}
			return Response.serverError()
					.header("Access-Control-Allow-Origin", "*")
					.entity("{ 'foo' : 'bar' }").type("application/json")
					.build();
		}

		/**
		 * Use asynchronous IO for SSH. Maybe this will allow more parallel connections and higher throughput 
		 */
		@GET
		@javax.ws.rs.Path("static3/{absolutePath : .+}")
		@Produces("application/json")
		public Response getFileSshNio(@PathParam("absolutePath") String absolutePathWithSlashMissing, @Context HttpHeaders header, @QueryParam("width") final Integer iWidth){
			final String absolutePath = "/" +absolutePathWithSlashMissing;
			if (FluentIterable.from(ImmutableList.copyOf(whitelisted)).anyMatch(Predicates.IS_UNDER(absolutePath))){
				try {
//					System.out.println("getFileSshNio() - 1");
					final FileSystem client = getAsyncClient();

//					System.out.println("getFileSshNio() - 2");
					Path path = client.getPath(absolutePath);

//					System.out.println("getFileSshNio() - 3");
					FileSystemProvider provider = path.getFileSystem().provider();

//					System.out.println("getFileSshNio() - 4");
					final InputStream is = provider.newInputStream(path);

//					System.out.println("getFileSshNio() - 5");
					StreamingOutput stream = new StreamingOutput() {
					    @Override
						public void write(OutputStream os) throws IOException,
								WebApplicationException {
//							System.out.println("getFileSshNio() - 6");
							// TODO: for most files, a straight copy is wanted. For images, check the file dimensions
							if (iWidth != null) {
								try {
									net.coobird.thumbnailator.Thumbnailator.createThumbnail(is, os, iWidth, iWidth);
								} catch (Exception e) {
									System.out.println(e);
									e.printStackTrace();
									System.out.println("Failed thumbnailing for " + absolutePath);
								}
							} else {

//								System.out.println("getFileSshNio() - 7");
								IOUtils.copy(is, os);
							}
//							System.out.println("getFileSshNio() - 8");
							client.close();
							is.close();
//							os.close();
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
					.entity("{ 'foo' : 'bar' }").type("application/json")
					.build();
		}

		@GET
		@javax.ws.rs.Path("static4/{absolutePath : .+}")
		@Produces("application/json")
		public Response getFileSshNio2(@PathParam("absolutePath") String absolutePathWithSlashMissing, @Context HttpHeaders header, @QueryParam("width") final Integer iWidth){
			final String absolutePath = "/" + absolutePathWithSlashMissing;
			try {
				final UnixSshFileSystem unixSshFileSystem = new UnixSshFileSystem(
						new UnixSshFileSystemProvider(), new URI(
								"ssh.unix://sarnobat@192.168.1.2:22/home/sarnobat"),
						getEnvironment());
				final ChannelExecWrapper channel = getExecWrapper(unixSshFileSystem, absolutePath);
				final InputStream inputStream = channel.getInputStream();

				StreamingOutput stream = new StreamingOutput() {
					@Override
					public void write(OutputStream os) throws IOException, WebApplicationException {
						if (debug) {
						System.out
								.println("Coagulate.MyResource.getFileSshNio2(...).new StreamingOutput() {...}.write()");
						}
						if (iWidth != null) {
							try {
								net.coobird.thumbnailator.Thumbnailator.createThumbnail(
										inputStream, os, iWidth, iWidth);
							} catch (Exception e) {
								e.printStackTrace();
							}
						} else {
							IOUtils.copy(inputStream, os);
						}
						inputStream.close();
						os.close();
						channel.close();
						unixSshFileSystem.close();
					}
				};


				return Response.ok().entity(stream)
						.type(FileServerGroovy.getMimeType(absolutePath)).build();
			} catch (Exception e) {
				e.printStackTrace();
			}
			return Response.serverError().header("Access-Control-Allow-Origin", "*")
					.entity("{ 'foo' : 'bar' }").type("application/json").build();
		}

		private static Map<String, Object> getEnvironment() {
			Map<String, Object> environment = new HashMap<String, Object>();
			DefaultSessionFactory defaultSessionFactory;
			try {
				defaultSessionFactory = new DefaultSessionFactory("sarnobat", "192.168.1.2", 22);
			} catch (Exception e) {
				e.printStackTrace();
				throw new RuntimeException(e);
			}
			environment.put("defaultSessionFactory", defaultSessionFactory);
			return environment;
		}

		private static ChannelExecWrapper getExecWrapper(UnixSshFileSystem unixSshFileSystem, String string)
				throws IOException {
			UnixSshPath unixPath = unixSshFileSystem.getPath(string);
			ChannelExecWrapper channel;
			try {
				channel = unixPath.getFileSystem().getCommandRunner()
						.open("/bin/cat" + " '" + unixPath.toString()  + "'");
			} catch (JSchException e) {
				unixSshFileSystem.close();
				throw new RuntimeException(e);
			}
			return channel;
		}
		
		private FileSystem getAsyncClient() {
			DefaultSessionFactory defaultSessionFactory;
			try {
//				System.out.println("getAsyncClient() - a");
				defaultSessionFactory = new DefaultSessionFactory("sarnobat", "192.168.1.2", 22);
			} catch (Exception e) {
				e.printStackTrace();
				throw new RuntimeException(e);
			}
//			System.out.println("getAsyncClient() - b");
			try {
				defaultSessionFactory.setKnownHosts(System.getProperty("user.home")  + "/.ssh/known_hosts");
//				System.out.println("getAsyncClient() - c");
				defaultSessionFactory.setIdentityFromPrivateKey(System.getProperty("user.home")  + "/.ssh/id_rsa");
//				defaultSessionFactory.setKnownHosts("/home/sarnobat/.ssh/known_hosts");
//				defaultSessionFactory.setIdentityFromPrivateKey("/home/sarnobat/.ssh/id_rsa");
//			    defaultSessionFactory.setConfig( "StrictHostKeyChecking", "no" );
			} catch (Exception e) {
				e.printStackTrace();
				System.exit(-1);
			}
//			System.out.println("getAsyncClient() - d");
			Map<String, Object> environment = new HashMap<String, Object>();
			environment.put("defaultSessionFactory", defaultSessionFactory);
			URI uri;
//			System.out.println("getAsyncClient() - e");
			try {
				uri = new URI("ssh.unix://sarnobat@192.168.1.2:22/home/sarnobat");
				//uri = new URI("ssh.unix://sarnobat@192.168.1.2:22/home/sarnobat");
			} catch (URISyntaxException e) {
				throw new RuntimeException(e);
			}
//			System.out.println("getAsyncClient() - f");
			FileSystem sshfs;
			try {
				sshfs = FileSystems.newFileSystem(uri, environment, getClass().getClassLoader());
			} catch (IOException e) {
				throw new RuntimeException(e);
			}
//			System.out.println("getAsyncClient() - g");
			return sshfs;
		}

		@SuppressWarnings("unused")
		private static synchronized SftpClient getSftpClient() throws InterruptedException,
				IOException {
			// if (sftp == null) {

			// ClientSession session ;
			SftpClient sftp = getSftpClient2();
			
			// } else {
			// sftp = session.createSftpClient();
			// }
			return sftp;
		}

		private static SftpClient getSftpClient2() throws InterruptedException, IOException {
			SshClient client = getSshClient();
			session = getSession(client);
			SftpClient sftp = session.createSftpClient();
			return sftp;
		}

		private static ClientSession getSession(SshClient client) throws InterruptedException, IOException {
			ClientSession session; 
//			if (session != null && session.isClosed()) {
//				System.out.println("getClient() - too late, was closed");
//			}
			session = client.connect("sarnobat", "netgear.rohidekar.com", 22).await().getSession();
			// TODO: Use key authentication instead
			session.addPasswordIdentity("aize2F");
			session.auth().await();
			return session;
			
		}

		private static SshClient getSshClient() {
			SshClient client;
			client = SshClient.setUpDefaultClient();
			// client.getProperties().put(ClientFactoryManager.HEARTBEAT_INTERVAL,
			// "50000");
			client.start();
			return client;
		}
		private static ClientSession session ;

		@GET
		@javax.ws.rs.Path("static/{absolutePath : .+}")
		@Produces("application/json")
		@Deprecated // Use SSH for serving, so we can put the app server on a separate host.
		public Response getFile(@PathParam("absolutePath") String absolutePath, @Context HttpHeaders header){
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
					mimeType = r.mimeType;
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
				Operations.copyFileToFolder(iFilePath, iDestinationDirPath);
			} catch (Exception e) {
				e.printStackTrace();
				throw new RuntimeException(e);
			}
			return Response.ok()
					.header("Access-Control-Allow-Origin", "*")
					.entity(new JSONObject().toString(4)).type("application/json")
					.build();
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
			if (iDestinationDirSimpleName.equals("_ 1")) {
				System.out.println("move() - dir name is wrong");
				throw new RuntimeException("dir name is wrong: " + iDestinationDirSimpleName);
			}
			try {
				Operations.moveFileToSubfolder(iFilePath, iDestinationDirSimpleName);
			} catch (Exception e) {
				e.printStackTrace();
				throw new RuntimeException(e);
			}
			return Response.ok()
					.header("Access-Control-Allow-Origin", "*")
					.entity(new JSONObject().toString(4)).type("application/json")
					.build();
		}

		@GET
		@javax.ws.rs.Path("list")
		@Produces("application/json")
		public Response list(@QueryParam("dirs") String iDirectoryPathsString, @QueryParam("limit") String iLimit)
				throws JSONException, IOException {
			System.out.println("list() - begin: " + iDirectoryPathsString);
			try {
				// To create JSONObject, do new JSONObject(aJsonObject.toString). But the other way round I haven't figured out
				JsonObject response = RecursiveLimitByTotal2.getDirectoryHierarchies(
								iDirectoryPathsString, Integer.parseInt(iLimit));
				System.out.println("list() - end");
				return Response.ok().header("Access-Control-Allow-Origin", "*")
						.entity(response.toString()).type("application/json")
						.build();
			} catch (Exception e) {
				e.printStackTrace();
				return Response.serverError()
						.header("Access-Control-Allow-Origin", "*")
						.entity("{ 'foo' : " + e.getMessage() + " }")
						.type("application/json").build();
			}
		}
	}

	private static class RecursiveLimitByTotal2 {

		static JsonObject getDirectoryHierarchies(String iDirectoryPathsString, int iLimit) {
			JsonObject response = Json
					.createObjectBuilder()
					.add("itemsRecursive",
							createFilesJsonRecursive(
									iDirectoryPathsString.split("\\n"), 
									iLimit))
					.build();
			return response;
		}

		private static JsonObject createFilesJsonRecursive(String[] iDirectoryPaths, int iLimit) {
			Set<DirPair> dirPairs = swoopRepeatedlyUntilLimitExceeded(new HashSet<DirPair>(),
					iDirectoryPaths, iLimit);
			JsonObjectBuilder json = Json.createObjectBuilder();
			for (DirPair p : dirPairs) {
				// TODO: ensure we aren't overwriting existing key data 
				json.add(p.getDirPath(), p.getDirObj().json());
			}
			return json.build();
		}
		
		private static Set<DirPair> swoopRepeatedlyUntilLimitExceeded(Set<DirPair> dirPairsAccumulated, String[] iDirectoryPaths, int iLimit) {
//			System.out
//					.println("Coagulate.RecursiveLimitByTotal2.swoopRepeatedlyUntilLimitExceeded() - begin");
			if (iLimit < 1) {
				return dirPairsAccumulated;
			}
			Set<DirPair> dirPairs = FluentIterable.from(ImmutableList.copyOf(iDirectoryPaths))
					.transform(new PathToDirPair(getFilesAlreadyObtained(dirPairsAccumulated)))
					.toSet();
			int filesObtained = countFiles(dirPairs);
			System.out
					.println("Coagulate.RecursiveLimitByTotal2.swoopRepeatedlyUntilLimitExceeded() - " + dirPairs);
			int newLimit = iLimit - filesObtained;
			if (filesObtained == 0) {
				return dirPairsAccumulated;
			}
			System.out
					.println("Coagulate.RecursiveLimitByTotal2.swoopRepeatedlyUntilLimitExceeded() - remaining = "
							+ newLimit);
			return swoopRepeatedlyUntilLimitExceeded(
					mergeDirectoryHierarchies(dirPairsAccumulated, dirPairs), iDirectoryPaths,
					newLimit);
//			} else {
//				System.out
//						.println("Coagulate.RecursiveLimitByTotal2.swoopRepeatedlyUntilLimitExceeded() - finished recursing - " + dirPairsAccumulated.size());
//				return dirPairsAccumulated;
//			}
		}
		
		private static Set<String> getFilesAlreadyObtained(Set<DirPair> dirPairsAccumulated) {
//			System.out.println("Coagulate.RecursiveLimitByTotal2.getFilesAlreadyObtained() - begin");
			ImmutableSet.Builder<String> builder = ImmutableSet.builder();
			for(DirPair p : dirPairsAccumulated) {
				System.out.println("Coagulate.RecursiveLimitByTotal2.getFilesAlreadyObtained() - " + p.getDirPath());
				builder.addAll(getFilesInDir(p.getDirObj()));
			}
//			System.out.println("Coagulate.RecursiveLimitByTotal2.getFilesAlreadyObtained() - end");
			return builder.build();
		}

		private static Set<String> getFilesInDir(DirObj iDirObj) {
			System.out.println("Coagulate.RecursiveLimitByTotal2.getFilesInDir() - begin");
			Set<String> keysInShard = new HashSet<String>();
			keysInShard.addAll(iDirObj.getFiles().keySet());
			keysInShard.addAll(iDirObj.getFiles().keySet());

			if (iDirObj.getDirs().size() > 0) {
				Map<String, DirObj> dirs = iDirObj.getDirs();
				for(String dirKey : dirs.keySet()) {
					DirObj dirObj = dirs.get(dirKey);
					keysInShard.addAll(dirObj.getFiles().keySet());
				}
			}
			return keysInShard;
		}

		private static int countFiles(Set<DirPair> directoryHierarchies) {
			int total = 0;
			for (DirPair aHierarchy : directoryHierarchies) {
				total += countFilesInHierarchy2(aHierarchy.json());
				System.out.println("Coagulate.RecursiveLimitByTotal.countAllFiles() - total = " + total);
			}
			return total;
		}

		private static int countFilesInHierarchy2(JsonObject aHierarchy) {
			if (aHierarchy.keySet().size() != 1) {
				System.err.println(new JSONObject(aHierarchy.toString()).toString(2));
				throw new RuntimeException("developerError");
			}
			JsonObject aDirectory = aHierarchy.getJsonObject((String)aHierarchy.keySet().toArray()[0]);
			return countFilesInHierarchy(aDirectory);
		}

		private static int countFilesInHierarchy(JsonObject aHierarchy) {
			validateIsDirectoryNode(aHierarchy);
			int count = FluentIterable.from(aHierarchy.keySet()).filter(not(DIRS)).toSet().size();
			if (aHierarchy.containsKey("dirs")) {
				JsonObject dirs = aHierarchy.getJsonObject("dirs");
				for (String keyInDirs : dirs.keySet()) {
					JsonObject dirJsonInDirs = dirs.getJsonObject(keyInDirs);
					count += countFilesInHierarchy(dirJsonInDirs);
				}
			}
			return count;
		}

		private static Set<DirPair> mergeDirectoryHierarchies(Set<DirPair> left, Set<DirPair> right) {
			System.out.println("Coagulate.RecursiveLimitByTotal2.mergeDirectoryHierarchies() - left "  + left.size());
			System.out.println("Coagulate.RecursiveLimitByTotal2.mergeDirectoryHierarchies() - right "  + right.size());
			ImmutableMap.Builder<String, DirPair> lb = ImmutableMap.builder();
			for (DirPair l : left) {
				lb.put(l.getDirPath(), l);
			}
			Map<String, DirPair> lm = lb.build();
			ImmutableMap.Builder<String, DirPair> rb = ImmutableMap.builder();
			for (DirPair r : right) {
				rb.put(r.getDirPath(), r);
			}
			Map<String, DirPair> rm = rb.build();
			ImmutableSet.Builder<DirPair> ret = ImmutableSet.builder();
			for (String dirPath : Sets.union(lm.keySet(), rm.keySet())) {
				DirPair l = lm.get(dirPath);
				DirPair r = rm.get(dirPath);
				if (lm.containsKey(dirPath) && rm.containsKey(dirPath)) {
					ret.add(new DirPair(dirPath, mergeDirectoryHierarchiesInternal(l.getDirObj(), r.getDirObj())));
				} else if (lm.containsKey(dirPath)) {
					ret.add(l);
				} else if (rm.containsKey(dirPath)) {
					ret.add(r);
				} else {
					throw new RuntimeException("Impossible");
				}
			}
			ImmutableSet<DirPair> build = ret.build();
			System.out.println("Coagulate.RecursiveLimitByTotal2.mergeDirectoryHierarchies() - out : " + build.size());
			return build;
		}

		private static DirObj mergeDirectoryHierarchiesInternal(DirObj dir1, DirObj dir2) {
			if (!dir1.getPath().equals(dir2.getPath())) {
				throw new RuntimeException("Must merge on a per-directory basis");
			}
			String commonDirPath = dir1.getPath();
			Map<String, FileObj> files = mergeLeafNodes(dir1.getFiles(), dir2.getFiles());
			Map<String, DirObj> dirs = mergeOverlappingDirNodes(dir1.getDirs(), dir2.getDirs(), commonDirPath);
			
			JsonObjectBuilder ret = Json.createObjectBuilder();
			for (Entry<String, FileObj> entry : files.entrySet()) {
				ret.add(entry.getKey(), entry.getValue().json());
			}
			JsonObjectBuilder dirs2 = Json.createObjectBuilder();
			for (Entry<String, DirObj> entry : dirs.entrySet()) {
				dirs2.add(entry.getKey(), entry.getValue().json());
			}
			ret.add("dirs", dirs2);
			return new DirObj(ret.build(), commonDirPath);
		}

		private static Map<String, DirObj> mergeOverlappingDirNodes(Map<String, DirObj> dirs1,
				Map<String, DirObj> dirs2, String commonDirPath) {
			ImmutableMap.Builder<String, DirObj> ret = ImmutableMap.builder();
			for (String dirPath : Sets.union(dirs1.keySet(), dirs2.keySet())) {
				if (dirs1.containsKey(dirPath) && dirs2.containsKey(dirPath)) {
					ret.put(dirPath,
							mergeDirectoryHierarchiesInternal(dirs1.get(dirPath),
									dirs2.get(dirPath)));
				} else if (dirs1.containsKey(dirPath) && !dirs2.containsKey(dirPath)) {
					ret.put(dirPath, dirs1.get(dirPath));
				} else if (!dirs1.containsKey(dirPath) && dirs2.containsKey(dirPath)) {
					ret.put(dirPath, dirs2.get(dirPath));
				} else {
					throw new RuntimeException("Impossible");
				}
			}
			return ret.build();
		}

		private static <T> Map<String, T> mergeLeafNodes(Map<String, T> leafNodes,
				Map<String, T> leafNodes2) {
			return ImmutableMap.<String, T> builder().putAll(leafNodes).putAll(leafNodes2).build();
		}

		private static JsonObject jsonFromString(String string) {
			if (string.contains("ebm:[locati")) {
				System.err.println("Coagulate.RecursiveLimitByTotal2.jsonFromString() - " + string);
				throw new RuntimeException("No square brackets allowed");
			}
			JsonReader jsonReader = Json.createReader(new StringReader(string));
			JsonObject object;
			try {
				object = jsonReader.readObject();
			} catch (JsonParsingException e) {
				System.err.println("Coagulate.RecursiveLimitByTotal2.jsonFromString()\n" + string);
				throw new RuntimeException(e);
			}
			jsonReader.close();
			return object;
		}

		private static class PathToDirPair implements Function<String, DirPair> {

			private final Set<String> _filesAlreadyObtained;
			PathToDirPair (Set<String> filesAlreadyObtained) {
				_filesAlreadyObtained = ImmutableSet.copyOf(filesAlreadyObtained);
			}

			@Override
			public DirPair apply(String input) {
				DirObj dirObj = new PathToDirObj(_filesAlreadyObtained).apply(input);
				return new DirPair(input, dirObj);
			}
		}


		private static class PathToDirObj implements Function<String, DirObj> {
			
			private final Set<String> _filesAlreadyObtained;
			PathToDirObj (Set<String> filesAlreadyObtained) {
				_filesAlreadyObtained = ImmutableSet.copyOf(filesAlreadyObtained);
			}
			
			@Override
			public DirObj apply(String dirPath) {
				JsonObject j;
				try {
					j = dipIntoDirRecursive(Paths.get(dirPath), 1, _filesAlreadyObtained, 0,
							100000, 0, false);
				} catch (CannotDipIntoDirException e) {
					throw new RuntimeException(e);
				}
				DirObj dirObj = new DirObj(j, dirPath);
				return dirObj;
			}
			
			private static JsonObject dipIntoDirRecursive(Path iDirectoryPath, int filesPerLevel,
					Set<String> filesToIgnore, int maxDepth, int iLimit, int dipNumber,
					boolean isTopLevel) throws CannotDipIntoDirException {
				JsonObjectBuilder dirHierarchyJson = Json.createObjectBuilder();
				Set<String> filesToIgnoreAtLevel = new HashSet<String>();
				// Sanity check
				if (!iDirectoryPath.toFile().isDirectory()) {
					return dirHierarchyJson.build();
				}
				
				// Immediate files
				int filesPerLevel2 = isTopLevel ? filesPerLevel + iLimit/2 // /5 
						: filesPerLevel; 
				ImmutableSet<Entry<String, JsonObject>> entrySet = getFilesInsideDir(iDirectoryPath, filesPerLevel2,
						filesToIgnore, iLimit, filesToIgnoreAtLevel).entrySet();
				for (Entry<String, JsonObject> e : entrySet) {
//					System.out
//							.println("Coagulate.RecursiveLimitByTotal2.PathToDirObj.dipIntoDirRecursive() - " + e.getKey() );
					dirHierarchyJson.add(e.getKey(), e.getValue());
				}
				// For ALL subdirectories, recurse
				try {
					JsonObjectBuilder dirsJson = Json.createObjectBuilder();
					for (Path p : getSubPaths(iDirectoryPath, isDirectory)) {
						// TODO: This causes a depth-first recursion which will cut off immediate
						// subdirs near the end of the alphabet if we were to impose a limit.
						// Ideally we want a breadth-first recursion. I think a queue is the way to 
						// achieve that. But then how to attach the output to the parent also requires
						// extra storage.
						// Actually, trimming the output may be better though you do do a lot of 
						// file system traversal (which isn't so bad since we use NIO).
						JsonObject contentsRecursive = dipIntoDirRecursive(p, filesPerLevel, filesToIgnore, --maxDepth, iLimit, ++dipNumber, false);
						dirsJson.add(p.toAbsolutePath().toString(),contentsRecursive);
					}
					dirHierarchyJson.add("dirs", dirsJson.build());
				} catch (IOException e) {
					e.printStackTrace();
				}
				JsonObject build = dirHierarchyJson.build();
//				System.out
//						.println("Coagulate.RecursiveLimitByTotal2.PathToDirObj.dipIntoDirRecursive() - " + build);
				return build;
			}
			

			private static Set<Path> getSubPaths(Path iDirectoryPath, Filter<Path> isfile2)
					throws IOException {
//				System.out.println("RecursiveLimitByTotal.getSubPaths() - " + iDirectoryPath);
				DirectoryStream<Path> filesInDir2 = Files.newDirectoryStream(iDirectoryPath, isfile2);
				Set<Path> filesInDir = FluentIterable.from(filesInDir2).filter(SHOULD_DIP_INTO).toSet();
				filesInDir2.close();
				return filesInDir;
			}

			private static final Predicate<Path> SHOULD_DIP_INTO = new Predicate<Path>() {
				@Override
				public boolean apply(Path input) {
					Set<String> forbidden = ImmutableSet.of("_thumbnails");
					return !forbidden.contains(input.getName(input.getNameCount() -1).toString());
				}
			};
			
			private static final boolean debug = false;
			
			private static ImmutableMap<String, JsonObject> getFilesInsideDir(Path iDirectoryPath,
					int filesPerLevel, Set<String> filesToIgnore, int iLimit,
					Set<String> filesToIgnoreAtLevel) {
				ImmutableMap.Builder<String, JsonObject> filesInDir = ImmutableMap.builder();
				// Get one leaf node
				try {
					int addedCount = 0;
					Predicates.Contains predicate = new Predicates.Contains(filesToIgnore);
					for (Path p : FluentIterable.from(getSubPaths(iDirectoryPath, isFile))
							.filter(not(predicate)).filter(Predicates.IS_DISPLAYABLE).toSet()) {
						String absolutePath = p.toAbsolutePath().toString();
//						System.out.println("Coagulate.RecursiveLimitByTotal.getFilesInsideDir() - " + absolutePath);
						
						filesInDir.put(absolutePath,
								Mappings.PATH_TO_JSON_ITEM.apply(p));
						++addedCount;
						filesToIgnoreAtLevel.add(p.toAbsolutePath().toString());
						if (debug) {
							System.out.println("Coagulate.RecursiveLimitByTotal.getFilesInsideDir() - files added: " + filesToIgnore.size());
						}
						if (filesToIgnore.size() > iLimit) {
							break;
						}
						if (addedCount >= filesPerLevel) {
							break;
						}
					}
				} catch (IOException e) {
					e.printStackTrace();
				}
				ImmutableMap<String, JsonObject> build1 = filesInDir.build();
				return build1;
			}
			private static class CannotDipIntoDirException extends Exception {
				private static final long serialVersionUID = 1L;
			}
			// TODO: Move to Predicates
			private static final DirectoryStream.Filter<Path> isFile = new DirectoryStream.Filter<Path>() {
				public boolean accept(Path entry) throws IOException {
					return !Files.isDirectory(entry);
				}
			};
			// TODO: Move to Predicates
			private static final DirectoryStream.Filter<Path> isDirectory = new DirectoryStream.Filter<Path>() {
				public boolean accept(Path entry) throws IOException {
					return Files.isDirectory(entry);
				}
			};
		};		
		
		
		private static class DirObj {

			private final String dirPath;
			private final JsonObject dirJson;

			@Override 
			public String toString() {
				return json().toString();
			}
			
			DirObj(JsonObject dirJson, String dirPath) {
				this.dirJson = validateIsDirectoryNode(dirJson);
				this.dirPath = dirPath;
			}
			
			Map<String, FileObj> getFiles() {
				ImmutableMap.Builder<String, FileObj> ret = ImmutableMap.builder();
				for (String path :FluentIterable.from(dirJson.keySet()).filter(not(DIRS)).toSet()) {
					JsonObject fileJson = dirJson.getJsonObject(path);
					ret.put(path, new FileObj(fileJson));
				}
				return ret.build();
			}

			public Map<String, DirObj> getDirs() {
				ImmutableMap.Builder<String, DirObj> ret = ImmutableMap.builder();
				if (dirJson.containsKey("dirs")) {
					JsonObject dirs = dirJson.getJsonObject("dirs");
					for (String path :FluentIterable.from(dirs.keySet()).toSet()) {
						JsonObject fileJson = dirs.getJsonObject(path);
						ret.put(path, new DirObj(fileJson, path));
					}
				} else {
					System.out.println("Coagulate.RecursiveLimitByTotal.DirObj.getDirs() - no subdirs" );
				}
				return ret.build();
			}

			public JsonObject json() {
				return dirJson;
			}

			public String getPath() {
				return dirPath;
			}
		}
		
		private static final Predicate<String> DIRS = new Predicate<String>() {
			@Override
			public boolean apply(String input) {
				return "dirs".equalsIgnoreCase(input);
			}
		};
		
		private static JsonObject validateIsDirectoryNode(JsonObject dir) {

			if (!dir.isEmpty()) {
				// if (!dir.containsKey("dirs")) {
				// throw new RuntimeException("Not a directory node: " +
				// prettyPrint(dir));
				// }
				if (dir.containsKey("location")) {
					throw new RuntimeException("Not a directory node: " + prettyPrint(dir));
				}
			}
			// Check parsing succeeds
			jsonFromString(dir.toString());
			return dir;
		}

		private static String prettyPrint(JsonObject dir) {
			return new JSONObject(dir.toString()).toString(2);
		}

		private static class FileObj {
			private final JsonObject fileJson;

			FileObj(JsonObject fileJson) {
				this.fileJson = fileJson;
			}

			public JsonObject json() {
				return fileJson;
			}
		}

		// TODO: remove this and just use the supertype.
		private static class DirPair extends HashMap<String, DirObj> {
			private static final long serialVersionUID = 1L;
			private final String dirPath;
			private DirObj dirObj;
			DirPair(String dirPath, DirObj dirObj) {
				this.dirPath = dirPath;
				dirObj.json();// check parsing succeeds
				this.dirObj = dirObj;
			}
			
			public JsonObject json() {
				return jsonFromString("{ \"" + dirPath + "\" : " + dirObj.json().toString() + "}");
			}

			public String getDirPath() {
				return dirPath;
			}

			DirObj getDirObj() {
				return dirObj;	
			}
			
			@Override 
			public String toString() {
				return json().toString();
			}
		}
	}


	private static class Mappings {
		
		private static final Function<Path, JsonObject> PATH_TO_JSON_ITEM = new Function<Path, JsonObject>() {
			@Override
			public JsonObject apply(Path iPath) {
//				System.out.print("f");
				return Json
						.createObjectBuilder()
						.add("location",
								iPath.getParent().toFile().getAbsolutePath()
										.toString())
						.add("fileSystem", iPath.toAbsolutePath().toString())
						.add("httpUrl",
								httpLinkFor(iPath.toAbsolutePath().toString()))
						.add("thumbnailUrl", httpLinkFor(thumbnailFor(iPath)))
						.build();
			}
		};

		private static String httpLinkFor(String iAbsolutePath) {
			String prefix = "http://netgear.rohidekar.com:4451/cmsfs/static4/";
			return prefix + iAbsolutePath;
		}

		private static String thumbnailFor(Path iPath) {
			return iPath.getParent().toFile().getAbsolutePath() + "/_thumbnails/" + iPath.getFileName().getFileName() + ".jpg";
		}
	}

	private static class Predicates {

		static class Contains implements Predicate<Path> {

			private final Collection<String> files ;

			public Contains(Collection<String> files) {
				this.files = files;
			}

			@Override
			public boolean apply(@Nullable Path input) {
				return files.contains(input.toAbsolutePath().toString());
			}

		}

		static Predicate<String> IS_UNDER(final String absolutePath) {
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
		
		private static final Predicate<Path> IS_DISPLAYABLE = new Predicate<Path>() {
			@Override
			public boolean apply(Path iPath) {
				if (iPath.toFile().isDirectory()) {
					return false;
				}
				String filename = iPath.getFileName().toString();
				if (filename.contains(".txt")) {
					return false;
				}
				if (filename.contains(".ini")) {
					return false;
				}
				if (filename.contains("DS_Store")) {
					return false;
				}
				if (filename.endsWith(".html") || filename.endsWith(".htm")) {
					return false;
				}
				return true;
			}
		};
		
	}
	


	@SuppressWarnings("unused")
	private static class Exif {
		static JSONObject getExifData(Path aFilePath)
				throws IOException {
			JSONObject exifJson = new JSONObject();
			exifJson.put("datetime",
					getTag(aFilePath, TiffTagConstants.TIFF_TAG_DATE_TIME));
			exifJson.put(
					"orientation",
					getTag(aFilePath, TiffTagConstants.TIFF_TAG_ORIENTATION));
			exifJson.put(
					"latitude_ref",
					getTag(aFilePath,
							GpsTagConstants.GPS_TAG_GPS_LATITUDE_REF));
			exifJson.put("latitude",
					getTag(aFilePath, GpsTagConstants.GPS_TAG_GPS_LATITUDE));
			exifJson.put(
					"longitude_ref",
					getTag(aFilePath,
							GpsTagConstants.GPS_TAG_GPS_LONGITUDE_REF));
			exifJson.put(
					"longitude",
					getTag(aFilePath, GpsTagConstants.GPS_TAG_GPS_LONGITUDE));
			return exifJson;
		}

		// TODO: I think this is slow.
		// See if you can predetermine cases where you will get an Exception
		// We may have to limit the depth (or breadth) which I'd rather not
		// do.
		private static String getTag(Path aFilePath, TagInfo tagInfo) {
			String ret = "";
			try {
				IImageMetadata metadata = Imaging.getMetadata(aFilePath
						.toFile());

				if (metadata instanceof JpegImageMetadata) {
					JpegImageMetadata jpegMetadata = (JpegImageMetadata) metadata;

					TiffField field = jpegMetadata
							.findEXIFValueWithExactMatch(tagInfo);
					if (field == null) {
					} else {
						Map<String, String> m = getPair(jpegMetadata,
								tagInfo);
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

		private static Map<String, String> getPair(
				JpegImageMetadata jpegMetadata, TagInfo tagInfo2) {
			String name = tagInfo2.name;
			String value = jpegMetadata.findEXIFValueWithExactMatch(
					tagInfo2).getValueDescription();
			return ImmutableMap.of(name, value);
		}
	}
	
	private static class Operations {

		private static boolean fileAlreadyInDesiredSubdir(
				String subfolderSimpleName, Path sourceFilePath) {
			return subfolderSimpleName.equals(sourceFilePath.getParent().getFileName().toString());
		}

		private static Path getUnconflictedDestinationFilePath(String folderName, Path path)
				throws IllegalAccessError, IOException {
			String parentDirPath = path.getParent().toAbsolutePath().toString();
			String destinationFolderPath = parentDirPath + "/" + folderName;
			Path subfolder = getOrCreateDestinationFolder(destinationFolderPath);
			return Operations.allocateFile(path, subfolder);
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
		
		static void moveFileToSubfolder(String filePath,
				String iSubfolderSimpleName) throws IllegalAccessError, IOException {
			System.out.println("moveFileToSubfolder() - begin");
			Path sourceFilePath = Paths.get(filePath);
			if (!Files.exists(sourceFilePath)) {
				throw new RuntimeException("No such source file: " + sourceFilePath.toAbsolutePath().toString());
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
				//System.out.println("Not moving to self");
				return;
			}
			Operations.doMove(sourceFilePath, getUnconflictedDestinationFilePath(iSubfolderSimpleName, sourceFilePath));

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
		
		static void copyFileToFolder(String filePath,
				String iDestinationDirPath) throws IllegalAccessError, IOException {
			Path sourceFilePath = Paths.get(filePath);
			if (!Files.exists(sourceFilePath)) {
				throw new RuntimeException("No such source file");
			}
			String string = sourceFilePath.getFileName().toString();
			Path destinationDir = Paths.get(iDestinationDirPath);
			doCopy(sourceFilePath, getUnconflictedDestinationFilePath(destinationDir, string));
		}

		private static Path getUnconflictedDestinationFilePath (Path destinationDir, String sourceFileSimpleName) {
			Path rDestinationFile = allocateFile(destinationDir, sourceFileSimpleName);
			return rDestinationFile;
		}
		
		private static Path allocateFile(Path folder, String fileSimpleName)
				throws IllegalAccessError {
			// if destination file exists, rename the file to be moved(while
			// loop)
			return Operations.determineDestinationPathAvoidingExisting(folder
					.normalize().toAbsolutePath().toString()
					+ "/" + fileSimpleName);
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
		
		static void doMoveToParent(String sourceFilePathString)
				throws IllegalAccessError {
			Path sourceFilePath = Paths.get(sourceFilePathString);
			Path destinationFile = getDestinationFilePathAvoidingExisting(sourceFilePath);
			doMove(sourceFilePath, destinationFile);
		}

		private static Path getDestinationFilePathAvoidingExisting(Path sourceFile)
				throws IllegalAccessError {
			String filename = sourceFile.getFileName().toString();
			Path parent = sourceFile.getParent().getParent().toAbsolutePath();
			String parentPath = parent.toAbsolutePath().toString();
			String destinationFilePath = parentPath + "/" + filename;
			return determineDestinationPathAvoidingExisting(destinationFilePath);
		}

		private static Path allocateFile(Path imageFile, Path subfolder)
				throws IllegalAccessError {
			// if destination file exists, rename the file to be moved(while
			// loop)
			return determineDestinationPathAvoidingExisting(new StringBuffer()
					.append(subfolder.normalize().toAbsolutePath().toString()).append("/")
					.append(imageFile.getFileName().toString()).toString());
		}

		private static Path determineDestinationPathAvoidingExisting(
				String destinationFilePath) throws IllegalAccessError {
			String destinationFilePathWithoutExtension = destinationFilePath
					.substring(0, destinationFilePath.lastIndexOf('.'));
			String extension = FilenameUtils
					.getExtension(destinationFilePath);
			Path rDestinationFile = Paths.get(destinationFilePath);
			while (Files.exists(rDestinationFile)) {
				destinationFilePathWithoutExtension += "1";
				destinationFilePath = destinationFilePathWithoutExtension + "." + extension;
				rDestinationFile = Paths.get(destinationFilePath);
			}
			if (Files.exists(rDestinationFile)) {
				throw new IllegalAccessError(
						"an existing file will get overwritten");
			}
			return rDestinationFile;
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
			 * 
			 * @deprecated - Use {@link MyResource#getFileSsh}
			 */
			@Deprecated 
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
					mime = theMimeTypes.get(fileFullPath
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

	private static final boolean debug = false;

	public static void main(String[] args) throws URISyntaxException, IOException {
		System.out.println("Note this doesn't work with JVM 1.8 build 45 due to some issue with TLS");
		try {
			JdkHttpServerFactory.createHttpServer(new URI(
					"http://localhost:4451/"), new ResourceConfig(
					MyResource.class));
		} catch (Exception e) {
			System.out.println("Port already listened on.");
		}
	}
}
