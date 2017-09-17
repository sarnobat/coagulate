import static com.google.common.base.Predicates.not;

import org.apache.commons.codec.binary.Base64;
import org.apache.commons.codec.binary.StringUtils;
import java.util.*;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.io.StringReader;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.channels.WritableByteChannel;
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
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.StringTokenizer;

import javax.annotation.Nullable;
import javax.json.Json;
import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;
import javax.json.JsonReader;
import javax.json.JsonValue;
import javax.json.stream.JsonParsingException;
import javax.ws.rs.GET;
import javax.ws.rs.HeaderParam;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.StreamingOutput;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.commons.io.FilenameUtils;
import org.glassfish.jersey.jdkhttp.JdkHttpServerFactory;
import org.glassfish.jersey.server.ResourceConfig;
import org.json.JSONException;
import org.json.JSONObject;

import com.google.common.base.Function;
import com.google.common.base.Preconditions;
import com.google.common.base.Predicate;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Multimap;
import com.google.common.collect.Sets;

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
		
		@GET
		@javax.ws.rs.Path("moveDirToParent")
		@Produces("application/json")
		public Response moveDirToParent(@QueryParam("filePath") String sourceFilePathString)
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
		@javax.ws.rs.Path("moveBase64")
		@Produces("application/json")
		public Response moveBase64(
				@QueryParam("filePath") String iFilePath1,
				@QueryParam("destinationDirSimpleName") String iDestinationDirSimpleName)
				throws JSONException, IOException {
			System.err.println("moveBase64() " + iFilePath1);

			String iFilePath = "";
			try {
			iFilePath = StringUtils.newStringUtf8(Base64.decodeBase64(iFilePath1));//Base64.getDecoder().decode(iFilePath1);
			} catch (Exception e) {
				e.printStackTrace();
				System.err.println("moveBase64() " + e.toString());
				throw e;
			}
			System.err.println("moveBase64() " + iFilePath);
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
			System.err.println("move() " + iFilePath);
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

		@Deprecated // Done in separate file
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
	
	@Deprecated
	// Note - two slashes will fail
	@javax.ws.rs.Path("{filePath : .+}")
	public static class StreamingFileServer { // Must be public
	    @GET
	    public Response streamFile(
	    		@PathParam("filePath") String filePath1,
	    		@HeaderParam("Range") String range) throws Exception {
	    	System.out.println("Coagulate.StreamingFileServer.streamFile() 1");
	        File audio;
	        String filePath = filePath1;
	        if (!filePath1.startsWith("/")) {
	        	filePath  = "/"+filePath1;
	        }
	        audio = Paths.get("/"+filePath).toFile();
			System.out.println("Coagulate.MediaResource.streamVideo() " + filePath);
	        return PartialContentServer.buildStream(audio, range, getMimeType(audio));
	    }	
	    
		private static String getMimeType(File file) {
			String mimeType;
			Path path = Paths.get(file.getAbsolutePath());
			String extension = FilenameUtils.getExtension(path.getFileName().toString())
					.toLowerCase();
                        System.out.println("Coagulate.FileServerNio.HttpFileHandler.serveFileStreaming() extension = " + extension);
			mimeType = theMimeTypes.get(extension);
			System.out.println("Coagulate.FileServerNio.HttpFileHandler.serveFileStreaming() mimetype = " + mimeType);
			return mimeType;
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
                                                        "mwk            text/plain " +
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
	}

	@Deprecated
	private static class PartialContentServer {
		static Response buildStream(final File asset, final String range, String contentType) throws Exception {
//			System.out.println("Coagulate.PartialContentServer.buildStream() 0");
	        if (range == null) {
//	        	System.out.println("Coagulate.PartialContentServer.buildStream() 1");
	            StreamingOutput streamer = new StreamingOutput() {
	                @Override
	                public void write(OutputStream output) throws IOException, WebApplicationException {
//System.out
//		.println("Coagulate.PartialContentServer.buildStream(...).new StreamingOutput() {...}.write() 1");
	                    @SuppressWarnings("resource")
						FileChannel inputChannel = new FileInputStream(asset).getChannel();
	                    WritableByteChannel outputChannel = Channels.newChannel(output);
	                    try {
//	                    	System.out
//									.println("Coagulate.PartialContentServer.buildStream(...).new StreamingOutput() {...}.write() 2");
	                        inputChannel.transferTo(0, inputChannel.size(), outputChannel);
	                    } finally {
	                        // closing the channels
	                        inputChannel.close();
	                        outputChannel.close();
	                    }
	                }
	            };
//	            System.out.println("Coagulate.PartialContentServer.buildStream() 11");
	            return Response.ok(streamer).status(200).header(HttpHeaders.CONTENT_LENGTH, asset.length()).header(HttpHeaders.CONTENT_TYPE, contentType).build();
	        }

//	        System.out.println("Coagulate.PartialContentServer.buildStream() 2");
	        String[] ranges = range.split("=")[1].split("-");
	        int from = Integer.parseInt(ranges[0]);
	        /**
	         * Chunk media if the range upper bound is unspecified. Chrome sends "bytes=0-"
	         */
	        int chunk_size = 1024 * 1024; // 1MB chunks
	        int to = chunk_size + from;
	        if (to >= asset.length()) {
	            to = (int) (asset.length() - 1);
	        }
	        if (ranges.length == 2) {
	            to = Integer.parseInt(ranges[1]);
	        }

			String responseRange = String.format("bytes %d-%d/%d", from, to, asset.length());
			RandomAccessFile raf = new RandomAccessFile(asset, "r");
			raf.seek(from);

			int len = to - from + 1;
			MediaStreamer streamer = new MediaStreamer(len, raf);
	        Response.ResponseBuilder res = Response.ok(streamer).status(206)
	                .header("Accept-Ranges", "bytes")
	                .header("Content-Range", responseRange)
	                .header(HttpHeaders.CONTENT_LENGTH, streamer.getLenth())
	                .header(HttpHeaders.CONTENT_TYPE, contentType)
	                .header(HttpHeaders.LAST_MODIFIED, new Date(asset.lastModified()));
	        return res.build();
	    }
		
		private static class MediaStreamer implements StreamingOutput {

		    private int length;
		    private RandomAccessFile raf;
		    final byte[] buf = new byte[4096];

		    public MediaStreamer(int length, RandomAccessFile raf) {
		        this.length = length;
		        this.raf = raf;
		    }

		    @Override
		    public void write(OutputStream outputStream) throws IOException, WebApplicationException {
		        try {
		            while( length != 0) {
		                int read = raf.read(buf, 0, buf.length > length ? length : buf.length);
		                outputStream.write(buf, 0, read);
		                length -= read;
		            }
		        } catch(java.io.IOException e) {
		        	System.out.println("Broken pipe (we don't need to log this)");
		        } finally {
		            raf.close();
		        }
		    }

		    public int getLenth() {
		        return length;
		    }
		}

	}

	@Deprecated
	private static class RecursiveLimitByTotal2 {

		static JsonObject getDirectoryHierarchies(String iDirectoryPathsString, int iLimit, Integer iDepth) {
			JsonObject response = Json
					.createObjectBuilder()
					.add("itemsRecursive",
							createFilesJsonRecursiveNew(
									iDirectoryPathsString.split("\\n"), 
									iLimit, iDepth))
					.build();
			return response;
		}
		
		private static JsonValue createFilesJsonRecursiveNew(String[] iDirectoryPaths, int iLimit,
				Integer iDepth) {
			
			List<DirPair> allDirsAccumulated = new LinkedList<DirPair>();
			Set<String> dirPathsFullyRead = new HashSet<String>();
			// TODO: My first functional attempt at this failed. See if any of
			// it can be translated to functional again.
			while (totalFiles(allDirsAccumulated) < iLimit) {
				boolean noMoreFilesToRead = false;
				for (String aDirectoryPath1 : iDirectoryPaths) {
System.out.println("createFilesJsonRecursiveNew() - " + aDirectoryPath1);
					String aDirectoryPath = aDirectoryPath1.trim();
					if (dirPathsFullyRead.contains(aDirectoryPath)) {
						continue;
					}
					Set<FileObj> filesAlreadyAdded = getFiles(allDirsAccumulated);
System.out.println("createFilesJsonRecursiveNew() - 3");
					DirPair newFiles = new PathToDirPair(getFilePaths(filesAlreadyAdded), iDepth, iLimit)
							.apply(aDirectoryPath);
System.out.println("createFilesJsonRecursiveNew() - 4");
					allDirsAccumulated.add(newFiles);
					if (getFiles(newFiles.getDirObj()).size() == 0) {
						dirPathsFullyRead.add(aDirectoryPath);
						if (dirPathsFullyRead.size() == iDirectoryPaths.length) {
							noMoreFilesToRead = true;
							break;
						}
					}
System.out.println("createFilesJsonRecursiveNew() - 5");
					int totalFiles = totalFiles(allDirsAccumulated);
					if (totalFiles > iLimit) {
						break;
					}
				}
				if (noMoreFilesToRead) {
					break;
				}
			}
		System.out.println("createFilesJsonRecursiveNew() - 10");	
			Multimap<String, DirObj> unmerged = toMultiMap(allDirsAccumulated);
			Map<String, DirObj> merged = mergeHierarhcies(unmerged);
			
			JsonObjectBuilder jsonObject = Json.createObjectBuilder();
			for (String dirPath : merged.keySet()) {
System.out.println("createFilesJsonRecursiveNew() - 11 " + dirPath);
				DirObj dirObj = merged.get(dirPath);
				JSONObject json = new JSONObject(dirObj.json().toString());
				JsonObject json2 = new SubDirObj(RecursiveLimitByTotal2.jsonFromString(RecursiveLimitByTotal2.createSubdirObjs(dirPath).toString())).json();
				json.put("subDirObjs", new JSONObject(json2.toString()));
				// correct
				String string = json.toString();
				// incorrect
				JsonObject jsonFromString = jsonFromString(string);
				// incorrect
				jsonObject.add(dirPath, jsonFromString);
			}
			JsonObject build = jsonObject.build();
			return build;
		}

		private static Multimap<String, DirObj> toMultiMap(Collection<DirPair> allDirsAccumulated) {
			Multimap<String, DirObj> m = ArrayListMultimap.create();
			for (DirPair dirPair : allDirsAccumulated) {
				m.put(dirPair.getDirPath(), dirPair.getDirObj());
			}
			return m;
		}

		private static Map<String, DirObj> mergeHierarhcies(Multimap<String, DirObj> unmerged) {
			Map<String, DirObj> m = new HashMap<String, DirObj>();
			for (String dirPath : unmerged.keySet()) {
				m.put(dirPath, mergeDirObjs(unmerged.get(dirPath)));
			}
			return ImmutableMap.copyOf(m);
		}

		private static DirObj mergeDirObjs(Collection<DirObj> dirObjs) {
			if (dirObjs.size() == 1) {
				return dirObjs.iterator().next();
			} else if (dirObjs.size() > 1) {
				List<DirObj> l = ImmutableList.copyOf(dirObjs);
				return mergeDirsFold(l.get(0), l.subList(1, dirObjs.size()));
			} else {
				throw new RuntimeException("Impossible");
			}
		}

		private static DirObj mergeDirsFold(DirObj dirObj, List<DirObj> dirObjs) {
			if (dirObjs.size() == 0) {
				return dirObj;
			} else {
				DirObj accumulatedSoFar = mergeDirectoryHierarchiesInternal(dirObj, dirObjs.get(0));
				return mergeDirsFold(accumulatedSoFar, dirObjs.subList(1, dirObjs.size()));
			}
		}

		private static int totalFiles(Collection<DirPair> allDirsAccumulated) {
			return getFilePaths(getFiles(allDirsAccumulated)).size();
		}

		private static Set<String> getFilePaths(Collection<FileObj> filesAlreadyAdded) {
                                        System.err.println("Coagulate.RecursiveLimitByTotal2.getFilePaths() 1 ");
			Set<String> s = new HashSet<String>();
			for (FileObj f : filesAlreadyAdded) {
				String fileAbsolutePath = f.getFileAbsolutePath();
                                        System.err.println("Coagulate.RecursiveLimitByTotal2.getFilePaths() 2 " + fileAbsolutePath);
				if (fileAbsolutePath == null) {
					// TODO: fix this
					System.err.println("Coagulate.RecursiveLimitByTotal2.getFilePaths() fileAbsolutePath = " + f.json());
				} else {
					s.add(fileAbsolutePath);
				}
			}
                                        System.err.println("Coagulate.RecursiveLimitByTotal2.getFilePaths() 5");
			return ImmutableSet.copyOf(s);
		}

		private static Set<FileObj> getFiles(Collection<DirPair> allDirsAccumulated) {
			Set<FileObj> s = new HashSet<FileObj>();
			for (DirPair p : allDirsAccumulated) {
				DirObj dirObj = p.getDirObj();
				Collection<FileObj> flat = getFiles(dirObj);
				s.addAll(flat);
			}
			return ImmutableSet.copyOf(s);
		}

		private static Collection<FileObj> getFiles(DirObj iDirObj) {
			Collection<FileObj> flat = new HashSet<FileObj>();
			Collection<FileObj> values = iDirObj.getFiles().values();
			flat.addAll(values);
			for (DirObj aDirObj : iDirObj.getDirs().values()) {
				flat.addAll(getFiles(aDirObj));
			}
			return flat;
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

		private static JsonValue createSubdirObjs(String dirPath) {
			return createSubdirObjs(Paths.get(dirPath));
		}

		// Retain this
		private static JsonValue createSubdirObjs(Path iDirectoryPath) {
			
			ImmutableMap.Builder<String, FileObj> filesInDir = ImmutableMap.builder();
			try {
				for (Path p : FluentIterable
						.from(getSubPaths(iDirectoryPath, Predicates.IS_DIRECTORY))
						.filter(Predicates.IS_DISPLAYABLE_DIR).toSet()) {
					String absolutePath = p.toAbsolutePath().toString();
					filesInDir.put(absolutePath, new FileObj(Mappings.PATH_TO_JSON_ITEM.apply(p)));
				}
			} catch (IOException e) {
				e.printStackTrace();
			}
			ImmutableMap<String, FileObj> build1 = filesInDir.build();
			
			JsonObjectBuilder subdirObjsObj = Json.createObjectBuilder();
			
			for (Entry<String, FileObj> entry : build1.entrySet()) {
				subdirObjsObj.add(entry.getKey(), entry.getValue().json());
			}
			
			return subdirObjsObj.build();
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
			ImmutableMap.Builder<String, T> putAll = ImmutableMap.<String, T> builder().putAll(
					leafNodes);
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

			// Absolute paths
			private final Set<String> _filesAlreadyObtained;
			private final int depth;
			private final int _limit;;
			
			PathToDirPair (Set<String> filesAlreadyObtained, int iDepth, int iLimit) {
				_filesAlreadyObtained = ImmutableSet.copyOf(filesAlreadyObtained);
				depth = iDepth;
				_limit = iLimit;
			}

			@Override
			public DirPair apply(String input) {
System.out.println("PathToDirPair::apply() - " + input);
				DirObj dirObj = new PathToDirObj(_filesAlreadyObtained, depth, _limit).apply(input);
				return new DirPair(input, dirObj);
			}
		}

		private static class PathToDirObj implements Function<String, DirObj> {
			
			private final Set<String> _filesAbsolutePathsAlreadyObtained;
			private final int depth;
			private final int _limit;
			PathToDirObj (Set<String> filesAlreadyObtained, int iDepth, int iLimit) {
				_filesAbsolutePathsAlreadyObtained = ImmutableSet.copyOf(filesAlreadyObtained);
				depth = iDepth;
				_limit = iLimit;
			}
			
			@Override
			public DirObj apply(String dirPath) {
				JsonObject j;
				try {
					j = dipIntoDirRecursive(Paths.get(dirPath), 1, _filesAbsolutePathsAlreadyObtained, 0,
							_limit, 0, true, depth);
					
				} catch (CannotDipIntoDirException e) {
					throw new RuntimeException(e);
				}
				DirObj dirObj = new DirObj(j, dirPath);
				return dirObj;
			}
			
			private static JsonObject dipIntoDirRecursive(Path iDirectoryPath, int filesPerLevel,
					Set<String> fileAbsolutePathsToIgnore, int maxDepth, int iLimit, int dipNumber,
					boolean isTopLevel, int depth) throws CannotDipIntoDirException {
System.out.println("dipIntoDirRecursive() - " + iDirectoryPath);
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
						fileAbsolutePathsToIgnore, iLimit, filesToIgnoreAtLevel).entrySet();
				for (Entry<String, JsonObject> e : entrySet) {
					dirHierarchyJson.add(e.getKey(), e.getValue());
				}
				
				// Subdirectories as leaf nodes (for moving directories around)

				// For ALL subdirectories, recurse

				if (depth >= 0) {
				try {
					JsonObjectBuilder dirsJson = Json.createObjectBuilder();
					for (Path p : getSubPaths(iDirectoryPath, Predicates.IS_DIRECTORY)) {
						JsonObject contentsRecursive = dipIntoDirRecursive(p, filesPerLevel,
								fileAbsolutePathsToIgnore, --maxDepth, iLimit, ++dipNumber, false, depth - 1);
						if (depth > 0) {
							dirsJson.add(p.toAbsolutePath().toString(), contentsRecursive);
						} else {
							dirsJson.add(p.toAbsolutePath().toString(), Json.createObjectBuilder().build());
						}
					}
					JsonObject build = dirsJson.build();
					dirHierarchyJson.add("dirs", build);
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
System.out.println("getFilesInsideDir()  1 - " + iDirectoryPath);
				ImmutableMap.Builder<String, JsonObject> filesInDir = ImmutableMap.builder();
				// Get one leaf node
				try {
					int addedCount = 0;
					Predicates.Contains predicate = new Predicates.Contains(filesToIgnore);
					for (Path p : FluentIterable.from(getSubPaths(iDirectoryPath, Predicates.IS_FILE))
							.filter(not(predicate)).filter(Predicates.IS_DISPLAYABLE).toSet()) {
						String absolutePath = p.toAbsolutePath().toString();
System.out.println("getFilesInsideDir()  2 - " + absolutePath);
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
System.out.println("DirObj::getFiles() - " + path);
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
				return "dirs".equalsIgnoreCase(input) || "subDirObjs".equalsIgnoreCase(input);
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
				this.fileJson = Preconditions.checkNotNull(fileJson);
				// Check if this throws a null pointer
				fileJson.getString("fileSystem");
			}

			public JsonObject json() {
				return fileJson;
			}
			
			public String getFileAbsolutePath() {
				Preconditions.checkNotNull(fileJson);
				return fileJson.getString("fileSystem");
			}
		}
		private static class SubDirObj {
			private final JsonObject fileJson;

			SubDirObj(JsonObject fileJson) {
				this.fileJson = fileJson;
			}

			public JsonObject json() {
				return fileJson;
			}
		}

		// TODO: remove this and just use the supertype?
		@Deprecated
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

	@Deprecated
	private static class Mappings {
		
		private static final Function<Path, JsonObject> PATH_TO_JSON_ITEM = new Function<Path, JsonObject>() {
			@Override
			public JsonObject apply(Path iPath) {

				if (iPath.toFile().isDirectory()) {
					long created;
					try {
						created = Files.readAttributes(iPath, BasicFileAttributes.class)
								.creationTime().toMillis();
					} catch (IOException e) {
						System.err.println("PATH_TO_JSON_ITEM.apply() - " + e.getMessage());
						created = 0;
					}
					JsonObject json = Json
							.createObjectBuilder()
							.add("location",
									iPath.getParent().toFile().getAbsolutePath().toString())
							.add("fileSystem", iPath.toAbsolutePath().toString())
							.add("httpUrl", httpLinkFor(iPath.toAbsolutePath().toString()))
							.add("thumbnailUrl",
									"http://www.pd4pic.com/images/windows-vista-folder-directory-open-explorer.png")
							.add("created", created).build();
					return json;
				} else {
					long created;
					try {
						created = Files.readAttributes(iPath, BasicFileAttributes.class)
								.creationTime().toMillis();
					} catch (IOException e) {
						System.err.println("PATH_TO_JSON_ITEM.apply() - " + e.getMessage());
						created = 0;
					}
					return Json
							.createObjectBuilder()
							.add("location",
									iPath.getParent().toFile().getAbsolutePath().toString())
							.add("fileSystem", iPath.toAbsolutePath().toString())
							.add("httpUrl", httpLinkFor(iPath.toAbsolutePath().toString()))
							.add("thumbnailUrl", httpLinkFor(thumbnailFor(iPath)))
							.add("created", created).build();
				}
			}
		};

		private static String httpLinkFor(String iAbsolutePath) {
			String prefix = "http://netgear.rohidekar.com:4" + fsPort;
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

		@Deprecated // We don't need a separate predicate
		private static final Predicate<Path> IS_DISPLAYABLE_DIR = new Predicate<Path>() {
			@Override
			public boolean apply(Path iPath) {
				if (iPath.toFile().isDirectory()) {
					return true;
				} else {
					return false;
				}
				
			}
		};

		private static final Predicate<Path> IS_DISPLAYABLE = new Predicate<Path>() {
			@Override
			public boolean apply(Path iPath) {
				if (iPath.toFile().isDirectory()) {
					// I think changing this causes problems
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
			System.out.println("moveFileToSubfolder() - beginn: " + filePath);
			Path sourceFilePath = Paths.get(filePath);
			System.out.println("moveFileToSubfolder() - sourceFilePathh = " + sourceFilePath);
			File f = sourceFilePath.toFile();
			System.out.println("moveFileToSubfolder() - f = " + f);
			boolean sourceExists = f.exists();
			System.out.println("moveFileToSubfolder() - sourceExists = " + sourceExists);
			try {
				if (!sourceExists) {
					System.out.println("moveFileToSubfolder() - file doesn't exist: " + filePath);
					throw new RuntimeException("No such source file: " + sourceFilePath.toAbsolutePath().toString());
				}
				System.out.println("moveFileToSubfolder() - getting targetDir path " + sourceFilePath);
				Path targetDir = Paths.get(sourceFilePath.getParent().toString() + "/" + iSubfolderSimpleName);
				if (!Files.exists(targetDir)) {
					System.out.println("moveFileToSubfolder() - Target directory doesn't exist. Creating dir " + targetDir.toString());
					Files.createDirectory(targetDir);
				} else if (!Files.isDirectory(targetDir)) {
					System.out.println("moveFileToSubfolder() - Target is an existing file: " + filePath);
					throw new RuntimeException("Target is an existing file");
				}
				Operations.doMove(sourceFilePath, getUnconflictedDestinationFilePath(iSubfolderSimpleName, sourceFilePath));
			} catch (Exception e) {
				e.printStackTrace();
				throw e;
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
				System.out.println("doMove() - begin");
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
			System.out.println("Coagulate.Operations.determineDestinationPathAvoidingExisting() = ");
			int lastIndexOf = destinationFilePath.lastIndexOf('.');
			String destinationFilePathWithoutExtension ;
			if (lastIndexOf == -1) {
				destinationFilePathWithoutExtension = destinationFilePath;
			} else {
				destinationFilePathWithoutExtension = destinationFilePath
						.substring(0, lastIndexOf);	
			}
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

	@Deprecated
	private static final int fsPort = 4452;

	public static void main(String[] args) throws URISyntaxException, IOException, KeyManagementException, UnrecoverableKeyException, NoSuchAlgorithmException, KeyStoreException, CertificateException, InterruptedException {
		System.out.println("start");
boolean b = Files.exists(Paths.get("/Unsorted/new/images/tumblr_m62cxfJDjg1qgytsho1_1280.jpg"));

		System.out.println("b = " + b);
		System.out.println("Note this doesn't work with JVM 1.8 build 45 due to some issue with TLS");
		
		String port = null;
		_parseOptions: {

		  Options options = new Options()
			  .addOption("h", "help", false, "show help.");

		  Option option = Option.builder("f").longOpt("file").desc("use FILE to write incoming data to").hasArg()
			  .argName("FILE").build();
		  options.addOption(option);

		  // This doesn't work with java 7
		  // "hasarg" is needed when the option takes a value
		  options.addOption(Option.builder("p").longOpt("port").hasArg().required().build());

		  try {
			CommandLine cmd = new DefaultParser().parse(options, args);
			port = cmd.getOptionValue("p", "4451");

			if (cmd.hasOption("h")) {
		
			  // This prints out some help
			  HelpFormatter formater = new HelpFormatter();

			  formater.printHelp("yurl", options);
			  System.exit(0);
			}
		  } catch (ParseException e) {
			e.printStackTrace();
			System.exit(-1);
		  }
		}
    
//		try {
//			//NioFileServerWithStreamingVideoAndPartialContent.startServer(fsPort);
//			JdkHttpServerFactory.createHttpServer(new URI(
//					"http://localhost:" + fsPort + "/"), new ResourceConfig(
//					StreamingFileServer.class));
//		} catch (Exception e) {
//			//e.printStackTrace();
//                        System.out.println("Port already listened on 2.");
//			System.exit(-1);
//		}
		try {
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
