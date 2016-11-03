import static com.google.common.base.Predicates.not;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
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
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import javax.annotation.Nullable;
import javax.json.Json;
import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;
import javax.json.JsonReader;
import javax.json.JsonValue;
import javax.json.stream.JsonParsingException;
import javax.net.ssl.SSLContext;
import javax.ws.rs.GET;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Response;

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
				for (String aDirectoryPath : iDirectoryPaths) {
					if (dirPathsFullyRead.contains(aDirectoryPath)) {
						continue;
					}
					Set<FileObj> filesAlreadyAdded = getFiles(allDirsAccumulated);
					DirPair newFiles = new PathToDirPair(getFilePaths(filesAlreadyAdded), iDepth, iLimit)
							.apply(aDirectoryPath);
					allDirsAccumulated.add(newFiles);
					if (getFiles(newFiles.getDirObj()).size() == 0) {
						dirPathsFullyRead.add(aDirectoryPath);
						if (dirPathsFullyRead.size() == iDirectoryPaths.length) {
							noMoreFilesToRead = true;
							break;
						}
					}
					int totalFiles = totalFiles(allDirsAccumulated);
					if (totalFiles > iLimit) {
						break;
					}
				}
				if (noMoreFilesToRead) {
					break;
				}
			}
			
			Multimap<String, DirObj> unmerged = toMultiMap(allDirsAccumulated);
			Map<String, DirObj> merged = mergeHierarhcies(unmerged);
			
			JsonObjectBuilder jsonObject = Json.createObjectBuilder();
			for (String dirPath : merged.keySet()) {
				DirObj dirObj = merged.get(dirPath);
				JSONObject json = new JSONObject(dirObj.json().toString());
				JsonObject json2 = new SubDirObj(RecursiveLimitByTotal2.jsonFromString(RecursiveLimitByTotal2.createSubdirObjs(dirPath).toString())).json();
				json.put("subDirObjs", new JSONObject(json2.toString()));
				// correct
//				System.out
//						.println("Coagulate.RecursiveLimitByTotal2.createFilesJsonRecursiveNew() " + json2);
				String string = json.toString();
				// incorrect
//				System.out
//						.println("Coagulate.RecursiveLimitByTotal2.createFilesJsonRecursiveNew() " + string);
				JsonObject jsonFromString = jsonFromString(string);
				// incorrect
//				System.out
//						.println("Coagulate.RecursiveLimitByTotal2.createFilesJsonRecursiveNew() subdirobjs " + jsonFromString);
				jsonObject.add(dirPath, jsonFromString);
			}
			JsonObject build = jsonObject.build();
//			System.out.println("Coagulate.RecursiveLimitByTotal2.createFilesJsonRecursiveNew() - " + build);
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
			Set<String> s = new HashSet<String>();
			for (FileObj f : filesAlreadyAdded) {
				String fileAbsolutePath = f.getFileAbsolutePath();
				if (fileAbsolutePath == null) {
					// TODO: fix this
					System.err.println("Coagulate.RecursiveLimitByTotal2.getFilePaths() fileAbsolutePath = " + f.json());
				} else {
					s.add(fileAbsolutePath);
				}
			}
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
//				System.out.println("Coagulate.RecursiveLimitByTotal2.PathToDirPair.apply() " + input);
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
				JsonObjectBuilder dirHierarchyJson = Json.createObjectBuilder();
				Set<String> filesToIgnoreAtLevel = new HashSet<String>();
				// Sanity check
				if (!iDirectoryPath.toFile().isDirectory()) {
					return dirHierarchyJson.build();
				}
				
				// Immediate files
				int filesPerLevel2 = isTopLevel ? filesPerLevel + iLimit/2 // /5 
						: filesPerLevel; 
//				System.out
//						.println("Coagulate.RecursiveLimitByTotal2.PathToDirObj.dipIntoDirRecursive() isTopLevel = " + isTopLevel + ", so adding " + filesPerLevel2 + " files.");
				ImmutableSet<Entry<String, JsonObject>> entrySet = getFilesInsideDir(iDirectoryPath, filesPerLevel2,
						fileAbsolutePathsToIgnore, iLimit, filesToIgnoreAtLevel).entrySet();
				for (Entry<String, JsonObject> e : entrySet) {
//					System.out
//							.println("Coagulate.RecursiveLimitByTotal2.PathToDirObj.dipIntoDirRecursive() " + e.getKey());
					dirHierarchyJson.add(e.getKey(), e.getValue());
				}
				
				// Subdirectories as leaf nodes (for moving directories around)
//				JsonObject jsonFromString = RecursiveLimitByTotal2.jsonFromString(RecursiveLimitByTotal2.createSubdirObjs(iDirectoryPath).toString());
//				System.out
//						.println("Coagulate.RecursiveLimitByTotal2.PathToDirObj.dipIntoDirRecursive() jsonFromString = " + new JSONObject(jsonFromString.toString()).toString(2));
//				dirHierarchyJson.add("subDirObjs", new SubDirObj(jsonFromString).json());

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
//					System.out
//							.println("Coagulate.RecursiveLimitByTotal2.PathToDirObj.getFilesInsideDir() Added " + addedCount + " files from " + iDirectoryPath);
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
//				System.out.println("Coagulate.RecursiveLimitByTotal2.FileObj.FileObj() fileJson = " + fileJson);
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
//				System.out.println("Coagulate.RecursiveLimitByTotal2.FileObj.FileObj() fileJson = " + fileJson);
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

	private static class Mappings {
		
		private static final Function<Path, JsonObject> PATH_TO_JSON_ITEM = new Function<Path, JsonObject>() {
			@Override
			public JsonObject apply(Path iPath) {

				if (iPath.toFile().isDirectory()) {
//					System.out.println("Coagulate.Mappings.PATH_TO_JSON_ITEM() - is a directory");
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
//					System.out.println("Coagulate.Mappings.PATH_TO_JSON_ITEM() - dirJson = " + json);
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

	/** Based on Apache Commons NIO's NHttpFileServer sample */
	@Deprecated // This doesn't have random access for video/audio (partial content) 
	private static class NioFileServerWithStreamingVideo {
		static void startServer(int port) throws NoSuchAlgorithmException,
				KeyManagementException, KeyStoreException, UnrecoverableKeyException,
				CertificateException, IOException, InterruptedException {
			SSLContext sslcontext = null;
			if (port == 8443) {
				// Initialize SSL context
				URL url = NioFileServerWithStreamingVideo.class.getResource("/my.keystore");
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
//				System.out.println("Coagulate.NioFileServer.HttpFileHandler.processRequest() - begin");
				// Buffer request content in memory for simplicity
				return new BasicAsyncRequestConsumer();
			}

			@Override
			public void handle(final HttpRequest request, final HttpAsyncExchange httpexchange,
					final HttpContext context) throws HttpException, IOException {
//				System.out.println("Coagulate.NioFileServer.HttpFileHandler.handle() - begin");
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
//				System.out.println("NHttpFileServer.HttpFileHandler.handleInternal() - serving "
//						+ file.getAbsolutePath());
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

	private static final int port = 4451;
	@SuppressWarnings("unused")
	private static final int fsPort = 4452;

	public static void main(String[] args) throws URISyntaxException, IOException, KeyManagementException, UnrecoverableKeyException, NoSuchAlgorithmException, KeyStoreException, CertificateException, InterruptedException {

		JsonObject j = RecursiveLimitByTotal2.getDirectoryHierarchies("/e/Sridhar/Photos/2012-09-16 Dad in Bay Area/products and services", 100, 1);
		System.err.println(new JSONObject(j.toString()).toString(2));
		System.out.println("Note this doesn't work with JVM 1.8 build 45 due to some issue with TLS");
		try {
			NioFileServerWithStreamingVideo.startServer(4452);
		} catch (Exception e) {
			e.printStackTrace();
			System.exit(-1);
		}
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
