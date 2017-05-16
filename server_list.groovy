import static com.google.common.base.Predicates.not;

import java.io.IOException;
import java.io.StringReader;
import java.net.URI;
import java.net.URISyntaxException;
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
import javax.ws.rs.GET;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Response;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
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
import com.pastdev.jsch.IOUtils;

/**
 * SSHD uses slf4j. So add the api + binding jars, and point to a properties file
 */
//@Grab(group='com.pastdev', module='jsch-nio', version='0.1.5')
public class CoagulateList {
	@javax.ws.rs.Path("cmsfs")
	public static class MyResource { // Must be public
	
		public MyResource() {
			System.err.println("Coagulate.MyResource.MyResource()");
		}

		//
		// mutators
		//

		@GET
		@javax.ws.rs.Path("list")
		@Produces("application/json")
		public Response list(@QueryParam("dirs") String iDirectoryPathsString, @QueryParam("limit") String iLimit, @QueryParam("depth") Integer iDepth)
				throws JSONException, IOException {
			System.err.println("list() - begin: " + iDirectoryPathsString + ", depth = " + iDepth);
			try {
				// To create JSONObject, do new JSONObject(aJsonObject.toString). But the other way round I haven't figured out
				boolean doDynamicList = true;
				if (iDirectoryPathsString.trim().contains("\n")) {
					doDynamicList = true;
					System.err.println("CoagulateList.MyResource.list() - no cache file found");
				} else {
					String cacheFile = iDirectoryPathsString.trim() + "/_coagulate.txt";
					if (Files.exists(Paths.get(cacheFile))) {
						System.err.println("CoagulateList.MyResource.list() - cache file found");
						doDynamicList = false;
					}
				}
					
				String output;
				if (doDynamicList) {
					JsonObject response = RecursiveLimitByTotal2.getDirectoryHierarchies(
									iDirectoryPathsString, Integer.parseInt(iLimit), iDepth);
					System.err.println("list() - end");
					output = response.toString();
				} else {
					System.err.println("list() - reading cache");
					String cacheFile = iDirectoryPathsString.trim() + "/_coagulate.txt";
					output = IOUtils.readFile(Paths.get(cacheFile).toFile());
					System.err.println("list() - read cache successfully");
				}
				return Response.ok().header("Access-Control-Allow-Origin", "*")
						.entity(output).type("application/json")
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
				for (String aDirectoryPath1 : iDirectoryPaths) {
System.err.println("createFilesJsonRecursiveNew() - " + aDirectoryPath1);
					String aDirectoryPath = aDirectoryPath1.trim();
					if (dirPathsFullyRead.contains(aDirectoryPath)) {
						continue;
					}
					Set<FileObj> filesAlreadyAdded = getFiles(allDirsAccumulated);
					System.err.println("createFilesJsonRecursiveNew() - 3");
					DirPair newFiles = new PathToDirPair(getFilePaths(filesAlreadyAdded), iDepth, iLimit)
							.apply(aDirectoryPath);
					System.err.println("createFilesJsonRecursiveNew() - 4");
					allDirsAccumulated.add(newFiles);
					if (getFiles(newFiles.getDirObj()).size() == 0) {
						dirPathsFullyRead.add(aDirectoryPath);
						if (dirPathsFullyRead.size() == iDirectoryPaths.length) {
							noMoreFilesToRead = true;
							break;
						}
					}
					System.err.println("createFilesJsonRecursiveNew() - 5");
					int totalFiles = totalFiles(allDirsAccumulated);
					if (totalFiles > iLimit) {
						break;
					}
				}
				if (noMoreFilesToRead) {
					break;
				}
			}
			System.err.println("createFilesJsonRecursiveNew() - 10");	
			Multimap<String, DirObj> unmerged = toMultiMap(allDirsAccumulated);
			Map<String, DirObj> merged = mergeHierarhcies(unmerged);
			
			JsonObjectBuilder jsonObject = Json.createObjectBuilder();
			for (String dirPath : merged.keySet()) {
				System.err.println("createFilesJsonRecursiveNew() - 11 " + dirPath);
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
				System.err.println("PathToDirPair::apply() - " + input);
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
				System.err.println("dipIntoDirRecursive() - 1 " + iDirectoryPath);
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
					System.err.println("dipIntoDirRecursive() - 2 " + iDirectoryPath);
					dirHierarchyJson.add(e.getKey(), e.getValue());
				}
				
				// Subdirectories as leaf nodes (for moving directories around)

				// For ALL subdirectories, recurse

				if (depth >= 0) {
				try {
					JsonObjectBuilder dirsJson = Json.createObjectBuilder();
					for (Path p : getSubPaths(iDirectoryPath, Predicates.IS_DIRECTORY)) {
						System.err.println("dipIntoDirRecursive() - 3 " + iDirectoryPath);
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
				System.err.println("getFilesInsideDir()  1 - " + iDirectoryPath);
				ImmutableMap.Builder<String, JsonObject> filesInDir = ImmutableMap.builder();
				// Get one leaf node
				try {
					int addedCount = 0;
					Predicates.Contains predicate = new Predicates.Contains(filesToIgnore);
					for (Path p : FluentIterable.from(getSubPaths(iDirectoryPath, Predicates.IS_FILE))
							.filter(not(predicate)).filter(Predicates.IS_DISPLAYABLE).toSet()) {
						String absolutePath = p.toAbsolutePath().toString();
						System.err.println("getFilesInsideDir()  2 - " + absolutePath);
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
					System.err.println("DirObj::getFiles() - " + path);
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
	
	private static final int fsPort = 4452;

	public static void main(String[] args) throws URISyntaxException, IOException, KeyManagementException, UnrecoverableKeyException, NoSuchAlgorithmException, KeyStoreException, CertificateException, InterruptedException {

		System.err.println("Note this doesn't work with JVM 1.8 build 45 due to some issue with TLS");
		
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
			port = cmd.getOptionValue("p", "4420");

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
    
		try {
			JdkHttpServerFactory.createHttpServer(new URI(
					"http://localhost:" + port + "/"), new ResourceConfig(
					MyResource.class));
		} catch (Exception e) {
			//e.printStackTrace();
			System.err.println("Port already listened on.");
			System.exit(-1);
		}
	}
}
