import static com.google.common.base.Preconditions.checkNotNull;
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
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.Queue;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.TimeZone;

import javax.annotation.Nullable;
import javax.json.Json;
import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;
import javax.json.JsonReader;
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
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Function;
import com.google.common.base.Predicate;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSet.Builder;
import com.google.common.collect.Sets;
import com.pastdev.jsch.DefaultSessionFactory;

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

		final List<String> whitelisted = ImmutableList
				.of("/media/sarnobat/Large/Videos/",
						"/e/Sridhar/Photos/2005-12-25 Chatting Screenshots",
						"/e/Sridhar/Photos/Skype Screenshots",
						"/e/Sridhar/Photos/screenshots",
"/e/Sridhar/Scans/screenshots",
						"/Videos/",
						"/media/sarnobat/Unsorted/images/",
						"/media/sarnobat/Unsorted/Videos/",
						"/media/sarnobat/d/Videos",
						"/e/Sridhar/Atletico Madrid/",
						"/e/Sridhar UK/Atletico Madrid/",
						"/e/Sridhar UK/Photos/Cats/",
                        "/e/Sridhar/Web/",
						"/media/sarnobat/e/Sridhar/Photos/camera phone photos/iPhone/",
						"/e/new/",
						"/media/sarnobat/e/Drive J/",
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

		private static final int LEVELS_TO_RECURSE = 2;

		@GET
		@javax.ws.rs.Path("list")
		@Produces("application/json")
		public Response list(@QueryParam("dirs") String iDirectoryPathsString, @QueryParam("limit") String iLimit)
				throws JSONException, IOException {
			System.out.println("list() - begin: " + iDirectoryPathsString);
			try {
				// To create JSONObject, do new JSONObject(aJsonObject.toString). But the other way round I haven't figured out
				JsonObject response = getDirectoryHierarchies(iDirectoryPathsString, Integer.parseInt(iLimit));
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

		private static JsonObject getDirectoryHierarchies(String iDirectoryPathsString, Integer iLimit) throws IOException {
			System.out.println("Coagulate.MyResource.getDirectoryHierarchies() - begin");
			JsonObject response = Json
					.createObjectBuilder()
					.add("itemsRecursive",
							RecursiveLimitByTotal.createFilesJsonRecursive(
									iDirectoryPathsString.split("\\n"), 
									iLimit, LEVELS_TO_RECURSE))
					.build();
			return response;
		}
	}

	private static class RecursiveLimitByTotal {
		
		/**
		 * Keep adding one more file from each directory (and its subdirectories) 
		 * until we reach the limit. The intention is to not spend too much time
		 * walking the file system and it taking such a long time for a response
		 * to the user. At the same time, we want to spread the returned files all over the hierarchy, not be hogged by one big directory.
		 */
		static JsonObject createFilesJsonRecursive(String[] iDirectoryPathStrings, int iLimit, int maxDepth)
				throws IOException {
			System.out.println("Coagulate.RecursiveLimitByTotal.createFilesJsonRecursive() - begin");
			JsonObject fold = fold(createDirecctoryHierarchies(iDirectoryPathStrings, iLimit, 1, maxDepth), iLimit);
			System.out.println("Coagulate.RecursiveLimitByTotal.createFilesJsonRecursive() - end");
			return fold;
		}

		private static final boolean debug = false;
		private static Set<JsonObject> createDirecctoryHierarchies(String[] iDirectoryPathStrings,
				int iLimit, int filesPerLevel, int maxDepth) {
			Set<JsonObject> directoryHierarchies = new HashSet<JsonObject>();
			// TODO: Mutable state
			Set<String> filesAlreadyObtained = new HashSet<String>();
			int total = 0;
			int swoopNumber = 0;
			while(total < iLimit && swoopNumber < iLimit){
				++swoopNumber;
				System.out.println("Coagulate.RecursiveLimitByTotal.createDirecctoryHierarchies() - Swoop number " + swoopNumber);
				List<String> dirPaths = ImmutableList.copyOf(iDirectoryPathStrings);
				Set<JsonObject> oneSwoopThroughDirs = swoopThroughDirs(dirPaths.get(0), dirPaths.subList(1, dirPaths.size()),iLimit, filesPerLevel, filesAlreadyObtained, maxDepth);
				
				Set<String> files = getFiles(oneSwoopThroughDirs);
				if (debug) {
					printFiles(files);
				}
				if (files.size() == 0) {
					// We didn't hit the limit, but the number of files in the specified dirs doesn't exceed the limit, i.e. there are no more files left that can be gotten.
					break;
				}
				filesAlreadyObtained.addAll(files);
				directoryHierarchies.addAll(oneSwoopThroughDirs);
				total = filesAlreadyObtained.size();//countAllFiles(directoryHierarchies);
				
				if (debug) {
					int countAllFiles = countAllFiles(directoryHierarchies);
					if (filesAlreadyObtained.size() != countAllFiles) {
						countAllFiles(directoryHierarchies);
						throw new RuntimeException(countAllFiles + " vs " + filesAlreadyObtained.size());
					}
				}
			}
			if (debug) {
				int countAllFiles = countAllFiles(directoryHierarchies);
				if (filesAlreadyObtained.size() != countAllFiles) {
					throw new RuntimeException(countAllFiles + " vs " + filesAlreadyObtained.size());
				} else {
				}
			} else {
			}
			return directoryHierarchies;
		}

		private static int countAllFiles(Set<JsonObject> directoryHierarchies) {
			int total = 0;
			for (JsonObject aHierarchy : directoryHierarchies) {
				total += countFilesInHierarchy2(aHierarchy);
				System.out.println("Coagulate.RecursiveLimitByTotal.countAllFiles() - total = " + total);
			}
			return total;
		}

		private static int countFilesInHierarchy2(JsonObject aHierarchy) {
			if (aHierarchy.keySet().size() != 1) {
				throw new RuntimeException("developerError");
			}
			JsonObject aDirectory = aHierarchy.getJsonObject((String)aHierarchy.keySet().toArray()[0]);
			return countFilesInHierarchy(aDirectory);
		}

		@SuppressWarnings("unused")
		private static int countFilesInShard(JsonObject aShard) {
			return countFilesInHierarchy(getOnlyValue(aShard));
		}
		@VisibleForTesting static int countFilesInHierarchy(JsonObject aHierarchy) {
			validateIsDirectoryNode(aHierarchy);
			int count = FluentIterable.from(aHierarchy.keySet()).filter(not(DIRS)).toSet().size();
			if (aHierarchy.containsKey("dirs")) {
				JsonObject dirs = aHierarchy.getJsonObject("dirs");
				for (String keyInDirs : dirs.keySet()) {
					JsonObject dirJsonInDirs = dirs.getJsonObject(keyInDirs);
//					System.out.println("Coagulate.RecursiveLimitByTotal.countFilesInHierarchy() - getting count for dir: " + dirJsonInDirs);
					count += countFilesInHierarchy(dirJsonInDirs);
				}
			}
			return count;
		}

		private static final Predicate<String> DIRS = new Predicate<String>() {
			@Override
			public boolean apply(String input) {
				return "dirs".equalsIgnoreCase(input);
			}
			
		};

		private static void printFiles(Set<String> alreadyObtained) {
			for (Iterator<String> iterator = alreadyObtained.iterator(); iterator.hasNext();) {
				String string = iterator.next();
				System.out.println("Coagulate.RecursiveLimitByTotal.printFiles() - " + string);
			}
		}

		private static Set<String> getFiles(Set<JsonObject> shards) {
			ImmutableSet.Builder<String> files = ImmutableSet.builder();
			for (JsonObject shard : shards) {
				files.addAll(getFilesInShard2(shard));
			}
			return files.build();
		}

		private static Set<String> getFilesInShard2(JsonObject shard2) {
			if (shard2.keySet().size() != 1) {
				throw new RuntimeException("Developer error");
			}
			String name = (String)shard2.keySet().toArray()[0];
			JsonObject shard = shard2.getJsonObject(name);
			return getFilesInShard(shard);
		}

		// TODO: move to predicates
		private static final Predicate<String> LEAF_KEY = new Predicate<String>() {
			
			@Override
			public boolean apply(String input) {
				return !"dirs".equals("input");
			}
			
		};
		private static Set<String> getFilesInShard(JsonObject shard) {
			Set<String> keysInShard = new HashSet<String>();
			keysInShard.addAll(shard.keySet());
			if (shard.isEmpty()) {
				return ImmutableSet.of();
			}
			else if (shard.containsKey("dirs")) {
				JsonObject jsonObject = shard.getJsonObject("dirs");
				for(String dirKey : FluentIterable.from(jsonObject.keySet()).filter(LEAF_KEY)) {
					JsonObject dirJson = jsonObject.getJsonObject(dirKey);
					DirObj dirObj = new DirObj(dirJson);
					keysInShard.addAll(dirObj.getFiles().keySet());
				}
			} else {
				System.out.println("Coagulate.RecursiveLimitByTotal.getFilesInShard() - not a directory node: " + new JSONObject(shard.toString()).toString(2));
				throw new RuntimeException("You must call this method on a directory node");
			}
			return keysInShard;
		}

		private static Set<JsonObject> swoopThroughDirs(String dirPath,
				List<String> dirPathsRemaining, int iLimit, int filesPerLevel,
				Set<String> filesAlreadyAdded, int maxDepth) {
			Builder<JsonObject> shardsForDir = ImmutableSet.builder();
			
			
			//
			// Recursive case
			//
			if (dirPathsRemaining.size() == 0) {
			} else {
				List<String> tail;
				if (dirPathsRemaining.size() == 1) {
					tail = ImmutableList.of(dirPathsRemaining.get(0));
				} else {
					tail = dirPathsRemaining.subList(1, dirPathsRemaining.size());
				}
				for (JsonObject shard : swoopThroughDirs(dirPathsRemaining.get(0), tail,
						iLimit, filesPerLevel, filesAlreadyAdded, maxDepth)) {
					JsonObjectBuilder ret = Json.createObjectBuilder();
					ret.add(dirPath, shard);
					JsonObject shard2 = ret.build();
					if (shard2.containsKey("dirs")) {
						throw new RuntimeException(shard2.toString());
					}
					shardsForDir.add(shard2);
				}
			}
			
			//
			// Base case (just 1 dir to swoop through)
			//
			// just get one file from every subdir
			int fileCountBefore = filesAlreadyAdded.size();
			if (debug) {
				System.out.println("Coagulate.RecursiveLimitByTotal.swoopThroughDirs() - filesAlreadyAdded before = " + fileCountBefore );
			}
			JsonObject dirHierarchyJson = dipIntoDir(Paths.get(dirPath), filesPerLevel,
					filesAlreadyAdded, maxDepth, iLimit, 1);
			if (debug) {
				int fileCountAfter = filesAlreadyAdded.size();
				System.out
						.println("Coagulate.RecursiveLimitByTotal.swoopThroughDirs() - filesAlreadyAdded after = "
								+ fileCountAfter);
				int filesAdded = fileCountAfter - fileCountBefore;

				System.out
						.println("Coagulate.RecursiveLimitByTotal.swoopThroughDirs() - files in swoop: "
								+ countFilesInHierarchy(dirHierarchyJson));
				int countFilesInHierarchy = countFilesInHierarchy(dirHierarchyJson);
				if (filesAdded != countFilesInHierarchy) {
					throw new RuntimeException(countFilesInHierarchy + ", " + filesAdded);
				}
			}
			JsonObjectBuilder dipJson = Json.createObjectBuilder();
			dipJson.add(dirPath, dirHierarchyJson);
			shardsForDir.add(dipJson.build());
			
			
			ImmutableSet<JsonObject> build = shardsForDir.build();
			if (debug) {
				int countAllFiles = countAllFiles(build);
				if (countAllFiles != filesAlreadyAdded.size()) {
					System.out.println("Coagulate.RecursiveLimitByTotal.swoopThroughDirs() - "
							+ countAllFiles + ", " + filesAlreadyAdded.size());
					System.out.println("Coagulate.RecursiveLimitByTotal.swoopThroughDirs()");
				}
			}
			return build;
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

		private static JsonObject dipIntoDir(Path iDirectoryPath, int filesPerLevel, Set<String> filesToIgnore, int maxDepth, int iLimit, int dipNumber) {
			if (debug) {
				System.out.println("Coagulate.RecursiveLimitByTotal.dipIntoDir() - dip number " + dipNumber);
				System.out.println("Coagulate.RecursiveLimitByTotal.dipIntoDir() - dipping into " + iDirectoryPath.toString());
			}
			JsonObjectBuilder dirHierarchyJson = Json.createObjectBuilder();
			Set<String> filesToIgnoreAtLevel = new HashSet<String>();
			// Sanity check
			if (!iDirectoryPath.toFile().isDirectory()) {
				throw new RuntimeException("cannot dip into a regular file");
			}
			
			// Immediate files
			ImmutableSet<Entry<String, JsonObject>> entrySet = getFilesInsideDir(iDirectoryPath, filesPerLevel,
					filesToIgnore, iLimit, filesToIgnoreAtLevel).entrySet();
			for (Entry<String, JsonObject> e : entrySet) {
				dirHierarchyJson.add(e.getKey(), e.getValue());
			}
			if (entrySet.size() > 0) {
				System.out.println("Coagulate.RecursiveLimitByTotal.dipIntoDir() - added "
						+ entrySet.size() + " files that are directly inside "
						+ iDirectoryPath.toString());
			} else {
//				System.out.print(".");
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
					JsonObject contentsRecursive = dipIntoDir(p, filesPerLevel, filesToIgnore, --maxDepth, iLimit, ++dipNumber);
					if (debug) {
						System.out.println("Coagulate.RecursiveLimitByTotal.dipIntoDir() - files from subdir " + countFilesInHierarchy(contentsRecursive));
					}
//					System.out.println("Coagulate.RecursiveLimitByTotal.dipIntoDir() - added files from subdir " + p.toString() + " to return object.");
					dirsJson.add(p.toAbsolutePath().toString(),contentsRecursive);
//					if (filesToIgnore.size() > iLimit) {
//						break;
//					}
					
				}
				dirHierarchyJson.add("dirs", dirsJson.build());
			} catch (IOException e) {
				e.printStackTrace();
			}
			JsonObject build = dirHierarchyJson.build();
			
			
			//System.out.println("Coagulate.RecursiveLimitByTotal.dipIntoDir() - before trimming, number of files = " + countFilesInHierarchy(build));
			return build;
//			JsonObject trimTreeToWithinLimitBreadthFirst = trimTreeToWithinLimitBreadthFirst(build, iLimit, new DirObjMutable(new JSONObject(build.toString())), iDirectoryPath.toAbsolutePath().toString());
//			System.out.println("Coagulate.RecursiveLimitByTotal.dipIntoDir() - after trimming, number of files = " + countFilesInHierarchy(trimTreeToWithinLimitBreadthFirst));
//			return trimTreeToWithinLimitBreadthFirst;
		}


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
//					System.out.println("Coagulate.RecursiveLimitByTotal.getFilesInsideDir() - " + absolutePath);
					
					filesInDir.put(absolutePath,
							Mappings.PATH_TO_JSON_ITEM.apply(p));
					++addedCount;
					filesToIgnore.add(p.toAbsolutePath().toString());
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

		private static Set<Path> getSubPaths(Path iDirectoryPath, Filter<Path> isfile2)
				throws IOException {
//			System.out.println("RecursiveLimitByTotal.getSubPaths() - " + iDirectoryPath);
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

		// precondition : the directory structure of all members of the input are the same
		private static JsonObject fold(Set<JsonObject> directoryHierarchies, int iLimit) {
			JsonObject untrimmed;
			if (directoryHierarchies.size() == 0) {
				untrimmed = Json.createObjectBuilder().build();
			} else if (directoryHierarchies.size() == 1) {
				untrimmed = directoryHierarchies.iterator().next();
			} else {
				List<JsonObject> l = ImmutableList.copyOf(directoryHierarchies);
				JsonObject head = l.get(0);
				if (head.containsKey("dirs")) {
					throw new RuntimeException(head.toString());
				}
				List<JsonObject> tail = l.subList(1, l.size());
				JsonObject mergeRecursive2 = mergeRecursive2(head, tail);
				untrimmed = mergeRecursive2;
			}
			JsonObject object = (JsonObject) untrimmed.values().toArray()[0];
			System.out.println("Coagulate.RecursiveLimitByTotal.fold() - size before trimming: " + countFilesInHierarchy(object));
			
			Trim3.Node vRoot = buildTreeFromJson(untrimmed);
			JsonObject trimTreeToWithinLimitBreadthFirst = Trim3.bfs2(vRoot, iLimit).getJsonObject("dirs");
			
			System.out.println("Coagulate.RecursiveLimitByTotal.fold() - size after trimming: " + countFilesInHierarchy(trimTreeToWithinLimitBreadthFirst));
			return trimTreeToWithinLimitBreadthFirst;
		}
		

		private static Trim3.Node buildTreeFromJson(
				JsonObject shard) {
			Trim3.Node shardNode = new Trim3.Node("{}",null,null);
			for (String key : shard.keySet()) {
				JsonObject jsonObject = shard.getJsonObject(key);
				buildTreeFromJson(jsonObject, shardNode, key);
			}
			return shardNode;
		}

		private static Trim3.Node buildTreeFromJson(JsonObject data, Trim3.Node parent, String path) {
			Trim3.Node n = new Trim3.Node(data.toString(),parent,path);
			parent.addChild(n);
			JsonObject jsonObject = data.getJsonObject("dirs");
			for(String subdirPath : jsonObject.keySet()) {
				JsonObject childData = jsonObject.getJsonObject(subdirPath);
				buildTreeFromJson(childData, n, subdirPath);
			}
			return n;
		}

		private static class Trim3 {
			private static Node bfs(Node vRoot, int iLimit) {
				Node rRootOut = null;
				Map<Node, Node> oldToNewMap = new HashMap<Node, Node>();
				Queue<Node> q = new LinkedList<Node>();
				q.add(vRoot);
				int filesAdded = 0;
				while (!q.isEmpty() && filesAdded < iLimit) {
					System.out.println("Coagulate.RecursiveLimitByTotal.Trim3.bfs() - nodes processed = " + filesAdded);
					Node uCurrentNode = q.remove();
					Node nodeOut = processNode(uCurrentNode,
							findCopyOf(uCurrentNode.getParent(), oldToNewMap));
					if (uCurrentNode.getParent() == null) {
						rRootOut = nodeOut; 
					}
					filesAdded += nodeOut.countFilesInNode();
					oldToNewMap.put(uCurrentNode, nodeOut);
					
					for (Node nChildNode : uCurrentNode.getChildren()) {
						q.add(nChildNode);
					}
					//System.out.println("Coagulate.RecursiveLimitByTotal.Trim3.bfs() - files in json: " + RecursiveLimitByTotal.countFilesInHierarchy(jsonFromString(serialize(rRootOut))));
				}
				System.out.println("Coagulate.RecursiveLimitByTotal.Trim3.bfs() - limit = " + iLimit);
				return checkNotNull(rRootOut);
			}

			static JsonObject bfs2(Coagulate.RecursiveLimitByTotal.Trim3.Node vRoot,
					int iLimit) {
				Node trimTreeToWithinLimitBreadthFirst = Trim3.bfs(vRoot, iLimit);
				return jsonFromString(serialize(trimTreeToWithinLimitBreadthFirst));
			}
			
			private static JsonObject jsonFromString(String string) {
				JsonReader jsonReader = Json.createReader(new StringReader(string));
				JsonObject object = jsonReader.readObject();
				jsonReader.close();
				return object;
			}

			private static String serialize(Node trimTreeToWithinLimitBreadthFirst) {
				
				String rootData = trimTreeToWithinLimitBreadthFirst.getData();
				JSONObject oRoot = new JSONObject(rootData);
				
				for (Node childNode : trimTreeToWithinLimitBreadthFirst.getChildren()) {
//					System.out.println("Coagulate.RecursiveLimitByTotal.Trim3.serialize() - " + oRoot.get("dirs"));
//					System.out.println("Coagulate.RecursiveLimitByTotal.Trim3.serialize() - " + oRoot.get("dirs").getClass());
					JSONObject dirs = oRoot.getJSONObject("dirs");
					dirs.put(childNode.getPath(), new JSONObject(serialize(childNode)));
				}
				
				return oRoot.toString();
			}


			private static Node findCopyOf(Object parent, Map<Node, Node> oldToNewMap) {
				return oldToNewMap.get(parent);
			}

			private static Node processNode(Node nodeIn, Node parentNodeOut) {
				Node nodeOut = new Node(nodeIn.getData(), parentNodeOut, nodeIn.getPath());
				if (parentNodeOut != null) {
					parentNodeOut.addChild(nodeOut);
				}
				return nodeOut;
			}

			private static class Node {
				private final String data;
				@Nullable private final Node parent;
				private final String path; 
				private Set<Node> children = new HashSet<Node>();

				Node(String iData, Node parent, String path) {
					this.parent = parent;
					this.data = removeChildren(new JSONObject(iData));
					if (!new JSONObject(data).has("dirs")) {
						throw new RuntimeException("lost empty dirs pair");
					}
					this.path = path;
				}
				
				public int countFilesInNode() {
					JSONObject j = new JSONObject(data);
					j.remove("dirs");
					return j.keySet().size();
				}

				public String getPath() {
					return path;
				}

				private static String removeChildren(JSONObject jsonObject) {
					jsonObject.remove("dirs");
					jsonObject.put("dirs", new JSONObject());
					if (!jsonObject.has("dirs")) {
						throw new RuntimeException("lost empty dirs pair");
					}
					String string = jsonObject.toString();
					if (!new JSONObject(string).has("dirs")) {
						throw new RuntimeException("lost empty dirs pair");
					}
					return string;
				}

				public Object getParent() {
					return parent;
				}

				public void addChild(Node child) {
					children.add(child);
					if (!child.getParent().equals(this)) {
						throw new RuntimeException("Inconsistent children and parent");
					}
				}

				Set<Node> getChildren() {
					return ImmutableSet.copyOf(children);
				}
				
				String getData() {
					if (!new JSONObject(data).has("dirs")) {
						throw new RuntimeException("lost empty dirs pair");
					}
					return data;
				}
			}
		}
		
		private static JsonObject mergeRecursive2(JsonObject accumulated, List<JsonObject> dirs) {
			System.out.println("Coagulate.RecursiveLimitByTotal.mergeRecursive2() - accumulated size : " + accumulated.toString().length());
			JsonObjectBuilder ret = Json.createObjectBuilder();
			ret.add((String) accumulated.keySet().toArray()[0],
					mergeRecursive(getOnlyValue(accumulated),
							FluentIterable.from(dirs).transform(GET_ONLY_VALUE).toList()));
			return ret.build();
		}

		private static final Function<JsonObject, JsonObject> GET_ONLY_VALUE = new Function<JsonObject,JsonObject>() {
			@Override
			public JsonObject apply(JsonObject input) {
				return getOnlyValue(input);
			}
		};

		private static JsonObject mergeRecursive(JsonObject accumulated, List<JsonObject> dirs) {
			if (dirs.size() == 0) {
				System.out.println("Coagulate.RecursiveLimitByTotal.mergeRecursive() - " + accumulated.toString().length());
				return accumulated;
			}
			JsonObject mergeDirectoryHierarchies = mergeDirectoryHierarchies(accumulated, dirs.get(0));
			System.out.println("Coagulate.RecursiveLimitByTotal.mergeRecursive() - " + mergeDirectoryHierarchies.toString().length());
			return mergeRecursive(mergeDirectoryHierarchies,
					dirs.subList(1, dirs.size()));
		}

		private static JsonObject mergeSetsOfDirectoryHierarchies(JsonObject dirs1, JsonObject dirs2) {
			JsonObjectBuilder ret = Json.createObjectBuilder();
			for (String key1 : dirs1.keySet()) {
				ret.add(key1,
						mergeDirectoryHierarchies(dirs1.getJsonObject(key1),
								dirs2.getJsonObject(key1)));
			}
			return ret.build();
		}

		private static JsonObject getOnlyValue(JsonObject shard1) {
			return shard1.getJsonObject((String)shard1.keySet().toArray()[0]);
		}

		@Deprecated
		private static class DirObj {
			private final JsonObject dirJson;
			DirObj(JsonObject dirJson) {
				this.dirJson = validateIsDirectoryNode(dirJson);
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
				JsonObject dirs = dirJson.getJsonObject("dirs");
				for (String path :FluentIterable.from(dirs.keySet()).toSet()) {
					JsonObject fileJson = dirs.getJsonObject(path);
					ret.put(path, new DirObj(fileJson));
				}
				return ret.build();
			}

			public JsonObject json() {
				return dirJson;
			}
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
		
		private static DirObj mergeDirectoryHierarchiesInternal(DirObj dir1, DirObj dir2) {
			Map<String, FileObj> files = mergeLeafNodes(dir1.getFiles(), dir2.getFiles());
			Map<String, DirObj> dirs = mergeOverlappingDirNodes(dir1.getDirs(), dir2.getDirs());
			
			JsonObjectBuilder ret = Json.createObjectBuilder();
			for (Entry<String, FileObj> entry : files.entrySet()) {
				ret.add(entry.getKey(), entry.getValue().json());
			}
			JsonObjectBuilder dirs2 = Json.createObjectBuilder();
			for (Entry<String, DirObj> entry : dirs.entrySet()) {
				dirs2.add(entry.getKey(), entry.getValue().json());
			}
			ret.add("dirs", dirs2);
			return new DirObj(ret.build());
		}

		private static Map<String, DirObj> mergeOverlappingDirNodes(Map<String, DirObj> dirs1,
				Map<String, DirObj> dirs2) {
			ImmutableMap.Builder<String, DirObj> ret = ImmutableMap.builder();
			for (String dirPath : Sets.union(dirs1.keySet(), dirs2.keySet())) {
				if (dirs1.containsKey(dirPath) && dirs2.containsKey(dirPath)) {
					DirObj dir = dirs1.get(dirPath);
					DirObj dir2 = dirs2.get(dirPath);
					DirObj dirsMerged = mergeDirectoryHierarchiesInternal(dir, dir2);
					ret.put(dirPath, dirsMerged);
				} else if (dirs1.containsKey(dirPath) && !dirs2.containsKey(dirPath)) {
					DirObj dir1 = dirs1.get(dirPath);
					ret.put(dirPath, dir1);
				} else if (!dirs1.containsKey(dirPath) && dirs2.containsKey(dirPath)) {
					DirObj dir2 = dirs2.get(dirPath);
					ret.put(dirPath, dir2);
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

		@VisibleForTesting static JsonObject mergeDirectoryHierarchies(JsonObject dir1, JsonObject dir2) {
			boolean searlesInInput = false;
			if (dir1.toString().contains("searles") || dir2.toString().contains("searles")) {
				searlesInInput = true;
			}
			JsonObject json = mergeDirectoryHierarchiesInternal(new DirObj(dir1), new DirObj(dir2)).json();
			if (searlesInInput) {
				if (!json.toString().contains("searl")) {
					System.out
							.println("Coagulate.RecursiveLimitByTotal.mergeDirectoryHierarchies() - arg 1 : "
									+ dir1.toString());
					System.out
							.println("Coagulate.RecursiveLimitByTotal.mergeDirectoryHierarchies() - size "
									+ +dir1.toString().length());
					System.out
							.println("Coagulate.RecursiveLimitByTotal.mergeDirectoryHierarchies() - arg 2 : "
									+ dir2.toString());
					System.out
							.println("Coagulate.RecursiveLimitByTotal.mergeDirectoryHierarchies() - size "
									+ dir2.toString().length());
					System.out
					.println("Coagulate.RecursiveLimitByTotal.mergeDirectoryHierarchies() - output " + json.toString());
					System.out
							.println("Coagulate.RecursiveLimitByTotal.mergeDirectoryHierarchies() - size "
									+ json.toString().length());
					throw new RuntimeException("merge has lost data");
				}
			}
			return json;
		}
		
		@Deprecated // This has a bug, rewrite it using a pojo wrapper
		@VisibleForTesting static JsonObject mergeDirectoryHierarchiesOld(JsonObject dir1, JsonObject dir2) {
			if (dir2 == null) {
//				System.out.println("Coagulate.RecursiveLimitByTotal.mergeDirectoryHierarchies() - base case");
				return dir1;
			}
			boolean searlesInInput = false;
			if (dir1.toString().contains("searles") || dir2.toString().contains("searles")) {
				searlesInInput = true;
			}
			System.out.println("Coagulate.RecursiveLimitByTotal.mergeDirectoryHierarchies() - " + dir2.toString());
			validateIsDirectoryNode(dir1);
			validateIsDirectoryNode(dir2);
//			System.out.println("Coagulate.RecursiveLimitByTotal.mergeDirectoryHierarchies() - dir1:\t" + dir1);
//			System.out.println("Coagulate.RecursiveLimitByTotal.mergeDirectoryHierarchies() - dir2:\t" + dir2);
			JsonObjectBuilder ret2 = Json.createObjectBuilder();
			
			// Merge the leaf nodes contents
			for (String key : FluentIterable.from(dir1.keySet()).filter(not(DIRS)).toSet()) {
				ret2.add(key, dir1.get(key));
			}
			for (String key : FluentIterable.from(dir2.keySet()).filter(not(DIRS)).toSet()) {
				ret2.add(key, dir2.get(key));
			}
			
			JsonObject mergeDirs = mergeDirs(dir1, dir2);
			ret2.add("dirs", mergeDirs);

			JsonObject build = ret2.build();
			System.out.println("Coagulate.RecursiveLimitByTotal.mergeDirectoryHierarchies() - out\t" + build);
			if (searlesInInput) {
				if (!build.toString().contains("searl")) {
					System.out
							.println("Coagulate.RecursiveLimitByTotal.mergeDirectoryHierarchies() - arg 1 : "
									+ dir1.toString());
					System.out
							.println("Coagulate.RecursiveLimitByTotal.mergeDirectoryHierarchies() - size "
									+ +dir1.toString().length());
					System.out
							.println("Coagulate.RecursiveLimitByTotal.mergeDirectoryHierarchies() - arg 2 : "
									+ dir2.toString());
					System.out
							.println("Coagulate.RecursiveLimitByTotal.mergeDirectoryHierarchies() - size "
									+ dir2.toString().length());
					System.out
					.println("Coagulate.RecursiveLimitByTotal.mergeDirectoryHierarchies() - output " + build.toString());
					System.out
							.println("Coagulate.RecursiveLimitByTotal.mergeDirectoryHierarchies() - size "
									+ build.toString().length());
					throw new RuntimeException("merge has lost data");
				}
			}
			return build;
		}

		private static JsonObject validateIsDirectoryNode(JsonObject dir) {
			
			if (!dir.isEmpty()) {
//				if (!dir.containsKey("dirs")) {
//					throw new RuntimeException("Not a directory node: " + prettyPrint(dir));
//				}
				if (dir.containsKey("location")) {
					throw new RuntimeException("Not a directory node: " + prettyPrint(dir));
				}
			}
			return dir;
			
		}

		private static String prettyPrint(JsonObject dir) {
			return new JSONObject(dir.toString()).toString(2);
		}

		private static JsonObject mergeDirs(JsonObject shard1, JsonObject shard2) {
			if (shard1.containsKey("dirs") && shard2.containsKey("dirs")) {
				// TODO: Wrong. We're not in a single directory hierarchy. We need a method called "mergeSetsOfDirectoryHierarchies()" 
				return mergeSetsOfDirectoryHierarchies(shard1.getJsonObject("dirs"),
						shard2.getJsonObject("dirs"));
			} else if (shard1.containsKey("dirs")) {
				return shard1.getJsonObject("dirs");
			} else if (shard2.containsKey("dirs")) {
				return shard2.getJsonObject("dirs");
			} else {
				return Json.createObjectBuilder().build();
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
			String prefix = "http://netgear.rohidekar.com:4451/cmsfs/static2/";
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
