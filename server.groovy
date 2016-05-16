import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Predicates.not;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.io.StringReader;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLDecoder;
import java.nio.file.AccessDeniedException;
import java.nio.file.DirectoryStream;
import java.nio.file.DirectoryStream.Filter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;
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
import javax.json.JsonValue;
import javax.json.stream.JsonParsingException;
import javax.net.ssl.SSLContext;
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
import org.apache.commons.io.FilenameUtils;
import org.apache.http.ExceptionLogger;
import org.apache.http.HttpEntity;
import org.apache.http.HttpException;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.MethodNotSupportedException;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.InputStreamEntity;
import org.apache.http.impl.nio.bootstrap.HttpServer;
import org.apache.http.impl.nio.bootstrap.ServerBootstrap;
import org.apache.http.impl.nio.reactor.IOReactorConfig;
import org.apache.http.nio.entity.NStringEntity;
import org.apache.http.nio.protocol.BasicAsyncRequestConsumer;
import org.apache.http.nio.protocol.BasicAsyncResponseProducer;
import org.apache.http.nio.protocol.HttpAsyncExchange;
import org.apache.http.nio.protocol.HttpAsyncRequestConsumer;
import org.apache.http.nio.protocol.HttpAsyncRequestHandler;
import org.apache.http.protocol.HttpContext;
import org.apache.http.ssl.SSLContexts;
import org.apache.sshd.ClientSession;
import org.glassfish.jersey.jdkhttp.JdkHttpServerFactory;
import org.glassfish.jersey.server.ResourceConfig;
import org.json.JSONException;
import org.json.JSONObject;

import com.google.api.client.util.IOUtils;
import com.google.common.base.Function;
import com.google.common.base.Preconditions;
import com.google.common.base.Predicate;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMap.Builder;
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
	
		public MyResource() {
			System.out.println("Coagulate.MyResource.MyResource()");
		}

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
		@javax.ws.rs.Path("static4/{absolutePath : .+}")
		@Produces("application/json")
		@Deprecated // I don't remember why this was insufficient. But use {@link NioFileServer}
		public Response getFileSshNio2(@PathParam("absolutePath") String absolutePathWithSlashMissing, @Context HttpHeaders header, @QueryParam("width") final Integer iWidth){
			final String absolutePath = "/" + absolutePathWithSlashMissing;
			System.out.println("Coagulate.MyResource.getFileSshNio2() - " + absolutePath);
			if (!FluentIterable.from(ImmutableList.copyOf(whitelisted)).anyMatch(
					Predicates.IS_UNDER(absolutePath))) {
				return Response.serverError().header("Access-Control-Allow-Origin", "*")
						.entity("{ 'foo' : 'bar' }").type("application/json").build();
			}
			try {
				final UnixSshFileSystem unixSshFileSystem = new UnixSshFileSystem(
						new UnixSshFileSystemProvider(), new URI(
								"ssh.unix://sarnobat@192.168.1.2:22/home/sarnobat"),
						Nio4.getEnvironment());
				final ChannelExecWrapper channel = Nio4.getExecWrapper(unixSshFileSystem, absolutePath);
				final InputStream inputStream = channel.getInputStream();

				StreamingOutput stream = new StreamingOutput() {
					@Override
					public void write(OutputStream os) throws IOException, WebApplicationException {
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

		private static class Nio4 {
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

			static ChannelExecWrapper getExecWrapper(UnixSshFileSystem unixSshFileSystem, String string)
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
		}
		
		
		@SuppressWarnings("unused")
		private static class Unused {
			private static ClientSession session ;
		}

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
		@javax.ws.rs.Path("moveDir")
		@Produces("application/json")
		public Response moveDir(
				@QueryParam("dirPath") String iFilePath,
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
		public Response list(@QueryParam("dirs") String iDirectoryPathsString, @QueryParam("limit") String iLimit, @QueryParam("depth") Integer iDepth)
				throws JSONException, IOException {
			System.out.println("list() - begin: " + iDirectoryPathsString + ", depth = " + iDepth);
			try {
				// To create JSONObject, do new JSONObject(aJsonObject.toString). But the other way round I haven't figured out
				JsonObject response = RecursiveLimitByTotal2.getDirectoryHierarchies(
								iDirectoryPathsString, Integer.parseInt(iLimit), iDepth);
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

		static JsonObject getDirectoryHierarchies(String iDirectoryPathsString, int iLimit, Integer iDepth) {
			JsonObject response = Json
					.createObjectBuilder()
					.add("itemsRecursive",
							createFilesJsonRecursive(
									iDirectoryPathsString.split("\\n"), 
									iLimit, iDepth))
					.build();
			return response;
		}

		private static JsonObject createFilesJsonRecursive(String[] iDirectoryPaths, int iLimit, Integer iDepth) {
			Set<DirPair> dirPairs1 = swoopRepeatedlyUntilLimitExceeded(new HashSet<DirPair>(),
					iDirectoryPaths, iLimit, iDepth);
			// For sort mode
			Map<String, Map<String, FileObj>> moreFilesAtTopLevel = getFilesInDirImmediate(iDirectoryPaths, iLimit);
			Set<DirPair> dirPairs = addExtraFiles(dirPairs1, moreFilesAtTopLevel, iDepth);
			JsonObjectBuilder json = Json.createObjectBuilder();
			for (DirPair p : dirPairs) {
				// TODO: ensure we aren't overwriting existing key data 
				json.add(p.getDirPath(), p.getDirObj().json());
			}
			return json.build();
		}
		
		private static Map<String, Map<String, FileObj>> getFilesInDirImmediate(
				String[] iDirectoryPaths, int iLimit) {
			ImmutableMap.Builder<String, Map<String, FileObj>> ret = ImmutableMap.builder();
			for (String dirPath : iDirectoryPaths) {
				ret.put(dirPath, getMoreFiles(dirPath, iLimit));
			}
			return ret.build();
		}

		private static Map<String, FileObj> getMoreFiles(String dirPath, int iLimit) {
			return getFilesInsideDir(Paths.get(dirPath), iLimit, ImmutableSet.<String> of(), iLimit,
					ImmutableSet.<String> of());
		}

		private static Map<String, FileObj> getFilesInsideDir(Path iDirectoryPath,
				int filesPerLevel, Set<String> filesToIgnore, int iLimit,
				Set<String> filesToIgnoreAtLevel) {
			ImmutableMap.Builder<String, FileObj> filesInDir = ImmutableMap.builder();
			// Get one leaf node
			try {
				int addedCount = 0;
				Predicates.Contains predicate = new Predicates.Contains(filesToIgnore);
				for (Path p : FluentIterable.from(getSubPaths(iDirectoryPath, Predicates.IS_FILE))
						.filter(not(predicate)).filter(Predicates.IS_DISPLAYABLE).toSet()) {
					String absolutePath = p.toAbsolutePath().toString();
					filesInDir.put(absolutePath,
							new FileObj(Mappings.PATH_TO_JSON_ITEM.apply(p)));
					++addedCount;
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
			ImmutableMap<String, FileObj> build1 = filesInDir.build();
			return build1;
		}


		private static Set<Path> getSubPaths(Path iDirectoryPath, Filter<Path> isfile2)
				throws IOException {
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
		
		private static Set<DirPair> addExtraFiles(Set<DirPair> dirPairs,
				Map<String, Map<String, FileObj>> moreFilesAtTopLevel, Integer iDepth) {
			ImmutableSet.Builder<DirPair> ret = ImmutableSet.builder();
			for (DirPair dirPair : dirPairs) {
				Map<String, FileObj> newFiles = moreFilesAtTopLevel.get(dirPair.getDirPath());
				
				JsonObject build = augmentDirJson(dirPair, newFiles);
				DirObj dirObjWithMoreFiles = new DirObj(build, dirPair.getDirPath());
				DirPair dirPairWithMoreFiles = new DirPair(dirPair.getDirPath(), dirObjWithMoreFiles);
				ret.add(dirPairWithMoreFiles);
			}
			return ret.build();
		}

		private static JsonObject augmentDirJson(DirPair dirPair, Map<String, FileObj> newFiles) {
			// Clone the existing json
			JsonObjectBuilder dirJsonWithMoreFiles = Json.createObjectBuilder();
			JsonObject dirJson = dirPair.getDirObj().json();
			for(String dirJsonKey: dirJson.keySet()) {
				dirJsonWithMoreFiles.add(dirJsonKey, dirJson.getJsonObject(dirJsonKey));
			}
			// Now add the new files
			for (String file : newFiles.keySet()) {
				if (dirJson.keySet().contains(file)) {
					continue;
				}
				dirJsonWithMoreFiles.add(file, newFiles.get(file).json()); 
			}
			JsonObject build = dirJsonWithMoreFiles.build();
			return build;
		}

		private static Set<DirPair> swoopRepeatedlyUntilLimitExceeded(Set<DirPair> dirPairsAccumulated, String[] iDirectoryPaths, int iLimit, Integer iDepth) {
			if (iLimit < 1) {
				return dirPairsAccumulated;
			}
			// For each dir path, we ultimately call {@link PathToDirObj#dipIntoDirRecursive}
			Set<DirPair> dirPairs = FluentIterable.from(ImmutableList.copyOf(iDirectoryPaths))
					.transform(new PathToDirPair(getFilesAlreadyObtained(dirPairsAccumulated, iDepth), iDepth.intValue()))
					.toSet();
			int filesObtained = countFiles(dirPairs);
			System.out
					.println("Coagulate.RecursiveLimitByTotal2.swoopRepeatedlyUntilLimitExceeded() - filesObtained = " + filesObtained);
			int newLimit = iLimit - filesObtained;
			if (filesObtained == 0) {
				return dirPairsAccumulated;
			}
			return swoopRepeatedlyUntilLimitExceeded(
					mergeDirectoryHierarchies(dirPairsAccumulated, dirPairs), iDirectoryPaths,
					newLimit, iDepth);
		}
		
		private static Set<String> getFilesAlreadyObtained(Set<DirPair> dirPairsAccumulated, Integer iDepth) {
			ImmutableSet.Builder<String> builder = ImmutableSet.builder();
			for(DirPair p : dirPairsAccumulated) {
				builder.addAll(getFilesInDir(p.getDirObj(), iDepth));
			}
			return builder.build();
		}

		private static Set<String> getFilesInDir(DirObj iDirObj, Integer iDepth) {
			Set<String> keysInShard = new HashSet<String>();
			// TODO : why is this done twice?
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
			Map<String, DirPair> lm = mapFromSet(left);
			Map<String, DirPair> rm = mapFromSet(right);
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
			return build;
		}

		private static Map<String, DirPair> mapFromSet(Set<DirPair> left2) {
			ImmutableMap.Builder<String, DirPair> lb = ImmutableMap.builder();
			for (DirPair l : left2) {
				lb.put(l.getDirPath(), l);
			}
			Map<String, DirPair> lm = lb.build();
			return lm;
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
			Builder<String, T> putAll = ImmutableMap.<String, T> builder().putAll(leafNodes);
			for (String key : leafNodes2.keySet()) {
				if (leafNodes.keySet().contains(key)) {
					
				} else {
					putAll.put(key, leafNodes2.get(key));
				}
			}
			return putAll.build();
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
			private final int depth;
			PathToDirPair (Set<String> filesAlreadyObtained, int iDepth) {
				_filesAlreadyObtained = ImmutableSet.copyOf(filesAlreadyObtained);
				depth = iDepth;
			}

			@Override
			public DirPair apply(String input) {
				DirObj dirObj = new PathToDirObj(_filesAlreadyObtained, depth).apply(input);
				return new DirPair(input, dirObj);
			}
		}

		private static class PathToDirObj implements Function<String, DirObj> {
			
			private final Set<String> _filesAlreadyObtained;
			private final int depth;
			PathToDirObj (Set<String> filesAlreadyObtained, int iDepth) {
				_filesAlreadyObtained = ImmutableSet.copyOf(filesAlreadyObtained);
				depth = iDepth;
			}
			
			@Override
			public DirObj apply(String dirPath) {
				JsonObject j;
				try {
					j = dipIntoDirRecursive(Paths.get(dirPath), 1, _filesAlreadyObtained, 0,
							100000, 0, false, depth);
				} catch (CannotDipIntoDirException e) {
					throw new RuntimeException(e);
				}
				DirObj dirObj = new DirObj(j, dirPath);
				return dirObj;
			}
			
			private static JsonObject dipIntoDirRecursive(Path iDirectoryPath, int filesPerLevel,
					Set<String> filesToIgnore, int maxDepth, int iLimit, int dipNumber,
					boolean isTopLevel, int depth) throws CannotDipIntoDirException {
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
					dirHierarchyJson.add(e.getKey(), e.getValue());
				}
				// For ALL subdirectories, recurse

				if (depth >= 0) {
				try {
					JsonObjectBuilder dirsJson = Json.createObjectBuilder();
					for (Path p : getSubPaths(iDirectoryPath, Predicates.IS_DIRECTORY)) {
						JsonObject contentsRecursive = dipIntoDirRecursive(p, filesPerLevel,
								filesToIgnore, --maxDepth, iLimit, ++dipNumber, false, depth - 1);
						if (depth > 0) {
							dirsJson.add(p.toAbsolutePath().toString(), contentsRecursive);
						} else {
							dirsJson.add(p.toAbsolutePath().toString(), Json.createObjectBuilder().build());
						}
					}
					dirHierarchyJson.add("dirs", dirsJson.build());
				} catch (IOException e) {
	e.printStackTrace();
					}
				}
				JsonObject build = dirHierarchyJson.build();
				return build;
			}

			private static Set<Path> getSubPaths(Path iDirectoryPath, Filter<Path> isfile2)
					throws IOException {
				DirectoryStream<Path> filesInDir2 = null;
				Set<Path> filesInDir ;
				try {
					filesInDir2 = Files.newDirectoryStream(iDirectoryPath, isfile2);
					filesInDir = FluentIterable.from(filesInDir2).filter(SHOULD_DIP_INTO).toSet();
				} catch (AccessDeniedException e) {
					filesInDir = ImmutableSet.of();
				} finally {
					if (filesInDir2 != null) {
						filesInDir2.close();
					}
				}
				return filesInDir;
			}

			private static final Predicate<Path> SHOULD_DIP_INTO = new Predicate<Path>() {
				@Override
				public boolean apply(Path input) {
					Set<String> forbidden = ImmutableSet.of("_thumbnails");
					return !forbidden.contains(input.getName(input.getNameCount() -1).toString());
				}
			};
			
			private static ImmutableMap<String, JsonObject> getFilesInsideDir(Path iDirectoryPath,
					int filesPerLevel, Set<String> filesToIgnore, int iLimit,
					Set<String> filesToIgnoreAtLevel) {
//				System.out
//						.println("Coagulate.RecursiveLimitByTotal2.PathToDirObj.getFilesInsideDir() - " + iDirectoryPath);
				ImmutableMap.Builder<String, JsonObject> filesInDir = ImmutableMap.builder();
				// Get one leaf node
				try {
					int addedCount = 0;
					Predicates.Contains predicate = new Predicates.Contains(filesToIgnore);
					for (Path p : FluentIterable.from(getSubPaths(iDirectoryPath, Predicates.IS_FILE))
							.filter(not(predicate)).filter(Predicates.IS_DISPLAYABLE).toSet()) {
						String absolutePath = p.toAbsolutePath().toString();
						filesInDir.put(absolutePath,
								Mappings.PATH_TO_JSON_ITEM.apply(p));
						++addedCount;
						filesToIgnoreAtLevel.add(p.toAbsolutePath().toString());
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
//					System.out.println("Coagulate.RecursiveLimitByTotal.DirObj.getDirs() - no subdirs" );
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
				if (dir.containsKey("location")) {
					throw new RuntimeException("Not a directory node: " + prettyPrint(dir));
				}
			}
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

		// TODO: remove this and just use the supertype?
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
				long created;
				try {
					created = Files.readAttributes(iPath, BasicFileAttributes.class).creationTime().toMillis();
				} catch (IOException e) {
					System.err.println("PATH_TO_JSON_ITEM.apply() - " + e.getMessage());
					created = 0;
				}
				return Json
						.createObjectBuilder()
						.add("location",
								iPath.getParent().toFile().getAbsolutePath()
								.toString())
								.add("fileSystem", iPath.toAbsolutePath().toString())
								.add("httpUrl",
										httpLinkFor(iPath.toAbsolutePath().toString()))
										.add("thumbnailUrl", httpLinkFor(thumbnailFor(iPath)))
										.add("created", created)
										.build();
			}
		};

		private static String httpLinkFor(String iAbsolutePath) {
//			String prefix = "http://netgear.rohidekar.com:4451/cmsfs/static4/";
			int fsPort = port + 1;
			String prefix = "http://netgear.rohidekar.com:4" + fsPort;
			if (iAbsolutePath.contains("Coru")) {
//				try {
//					System.out.println("Coagulate.Mappings.httpLinkFor() " + URLEncoder.encode(iAbsolutePath, "UTF-8"));
//					System.out.println("Coagulate.Mappings.httpLinkFor() " + iAbsolutePath);
//					return prefix + URLEncoder.encode(iAbsolutePath, "UTF-8");
//				} catch (UnsupportedEncodingException e) {
//					e.printStackTrace();
//				}
			}
			return prefix + iAbsolutePath;
		}

		private static String thumbnailFor(Path iPath) {
			return iPath.getParent().toFile().getAbsolutePath() + "/_thumbnails/" + iPath.getFileName().getFileName() + ".jpg";
		}
	}

	private static class Predicates {

		static final DirectoryStream.Filter<Path> IS_FILE = new DirectoryStream.Filter<Path>() {
			public boolean accept(Path entry) throws IOException {
				return !Files.isDirectory(entry);
			}
		};
		
		static final DirectoryStream.Filter<Path> IS_DIRECTORY = new DirectoryStream.Filter<Path>() {
			public boolean accept(Path entry) throws IOException {
				return Files.isDirectory(entry);
			}
		};
		
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
			System.out.println("moveFileToSubfolder() - begin: " + filePath);
			Path sourceFilePath = Paths.get(filePath);
                        System.out.println("moveFileToSubfolder() - sourceFilePath = " + sourceFilePath);
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
// I'm pretty sure we can delete this
//			if (fileAlreadyInDesiredSubdir(iSubfolderSimpleName, sourceFilePath)) {
//				System.out.println("moveFileToSubfolder() - Not moving to self");
//				return;
//			}
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

		// Only works for files
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

	/** Based on Apache Commons NIO's NHttpFileServer sample */
	private static class NioFileServer {
		static void startServer(int port) throws NoSuchAlgorithmException,
				KeyManagementException, KeyStoreException, UnrecoverableKeyException,
				CertificateException, IOException, InterruptedException {
			SSLContext sslcontext = null;
			if (port == 8443) {
				// Initialize SSL context
				URL url = NioFileServer.class.getResource("/my.keystore");
				if (url == null) {
					System.out.println("Keystore not found");
					System.exit(1);
				}
				sslcontext = SSLContexts.custom()
						.loadKeyMaterial(url, "secret".toCharArray(), "secret".toCharArray())
						.build();
			}

			IOReactorConfig config = IOReactorConfig.custom().setSoTimeout(15000)
					.setTcpNoDelay(true).build();

			final HttpServer server = ServerBootstrap.bootstrap().setListenerPort(port)
					.setServerInfo("Test/1.1").setIOReactorConfig(config).setSslContext(sslcontext)
					.setExceptionLogger(ExceptionLogger.STD_ERR)
					.registerHandler("*", new HttpFileHandler()).create();

			server.start();
		}

		private static class HttpFileHandler implements HttpAsyncRequestHandler<HttpRequest> {

			@Override
			public HttpAsyncRequestConsumer<HttpRequest> processRequest(final HttpRequest request,
					final HttpContext context) {
				System.out.println("Coagulate.NioFileServer.HttpFileHandler.processRequest() - begin");
				// Buffer request content in memory for simplicity
				return new BasicAsyncRequestConsumer();
			}

			@Override
			public void handle(final HttpRequest request, final HttpAsyncExchange httpexchange,
					final HttpContext context) throws HttpException, IOException {
				System.out.println("Coagulate.NioFileServer.HttpFileHandler.handle() - begin");
				HttpResponse response = httpexchange.getResponse();
				handleInternal(request, response, context);
				httpexchange.submitResponse(new BasicAsyncResponseProducer(response));
			}

			private static void handleInternal(final HttpRequest request,
					final HttpResponse response, final HttpContext context) throws HttpException,
					IOException {

				String method = request.getRequestLine().getMethod().toUpperCase(Locale.ENGLISH);
				if (!method.equals("GET") && !method.equals("HEAD") && !method.equals("POST")) {
					throw new MethodNotSupportedException(method + " method not supported");
				}

				handle2(request, response, context);
			}

			private static void handle2(final HttpRequest request, final HttpResponse response,
					final HttpContext context) throws UnsupportedEncodingException {
				String target;
				try {
//					System.out.println("Coagulate.NioFileServer.HttpFileHandler.handleInternal() - about to decode");
					target = request.getRequestLine().getUri().replaceAll(".width.*", "").replace("%20", " ");
//					target = urlDecoder.decode(request.getRequestLine().getUri());
				} catch (Exception e) {
					
					response.setStatusCode(HttpStatus.SC_FORBIDDEN);
					NStringEntity entity = new NStringEntity(
							e.getStackTrace().toString(), ContentType.create(
									"text/html", "UTF-8"));
					response.setEntity(entity);
//					System.out.println("Coagulate.NioFileServer.HttpFileHandler.handleInternal() - failed to decode");
					return;
//					throw new RuntimeException(e);
				}//, "UTF-8");//.replaceAll(".width.*", "").replace("%20", " ");
				
				final File file = Paths.get(URLDecoder.decode(target, "UTF-8").replace("/_ 1", "/_+1")).toFile();// ,
//																 URLDecoder.decode(target,
//																 "UTF-8"));
				System.out.println("NHttpFileServer.HttpFileHandler.handleInternal() - serving "
						+ file.getAbsolutePath());
				if (!file.canRead()) {
					throw new RuntimeException("cannot read");
				}
				if (!file.exists()) {

					response.setStatusCode(HttpStatus.SC_NOT_FOUND);
					NStringEntity entity = new NStringEntity("<html><body><h1>File"
							+ file.getPath() + " not found</h1></body></html>", ContentType.create(
							"text/html", "UTF-8"));
					response.setEntity(entity);
					System.out.println("File " + file.getPath() + " not found");

				} else if (!file.canRead() || file.isDirectory()) {

					response.setStatusCode(HttpStatus.SC_FORBIDDEN);
					NStringEntity entity = new NStringEntity(
							"<html><body><h1>Access denied</h1></body></html>", ContentType.create(
									"text/html", "UTF-8"));
					response.setEntity(entity);
					System.out.println("Cannot read file " + file.getPath());
				} else {
					response.setStatusCode(HttpStatus.SC_OK);
					serveFileStreaming(response, file);
				}
			}

			private static void serveFileStreaming(final HttpResponse response, File file) {
				try {
					final InputStream fis = new FileInputStream(file);
//					PipedInputStream pis = createThumbnail(fis);
					HttpEntity body = new InputStreamEntity(fis, ContentType.create("image/jpeg"));
					response.setEntity(body);
				} catch (FileNotFoundException e) {
					e.printStackTrace();
				}
//				catch (IOException e) {
//					e.printStackTrace();
//				}
			}

			// This actually slows down throughput, but the memory footprint on the client side is lower.
			@SuppressWarnings("unused")
			private static PipedInputStream createThumbnail(final InputStream fis)
					throws IOException {
				System.out
						.println("Coagulate.NioFileServer.HttpFileHandler.serveFileStreaming() - about to copy");

				// Works
				final PipedOutputStream out = new PipedOutputStream();
				PipedInputStream pis = new PipedInputStream(out);
				try {
					new Thread() {
						@Override
						public void run() {
							try {

								net.coobird.thumbnailator.Thumbnailator.createThumbnail(fis,
										out, 250, 250);
								fis.close();
								out.close();
							} catch (IOException e) {
								e.printStackTrace();
							}
						}
					}.start();

				} finally {
				}
				return pis;
			}
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
	
			private static String renderFilename(String uri, String filenameAfter) {
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

	private static final int port = 4451;
	private static final int fsPort = 4452;

	public static void main(String[] args) throws URISyntaxException, IOException, KeyManagementException, UnrecoverableKeyException, NoSuchAlgorithmException, KeyStoreException, CertificateException, InterruptedException {
		System.out.println("Note this doesn't work with JVM 1.8 build 45 due to some issue with TLS");
		try {
			NioFileServer.startServer(4452);
		} catch (Exception e) {
			e.printStackTrace();
			System.exit(-1);
		}
		try {
//			System.out.println(Paths.get("/Unsorted/Videos/Atletico/1990s/_thumbnails/Juan Carlos ValeroÃÅn - FIFA Futbol Mundial.mp4.jpg").toAbsolutePath());
			JdkHttpServerFactory.createHttpServer(new URI(
					"http://localhost:" + port + "/"), new ResourceConfig(
					MyResource.class));
		} catch (Exception e) {
			//e.printStackTrace();
			System.out.println("Port already listened on.");
			System.exit(-1);
		}
	}
}
