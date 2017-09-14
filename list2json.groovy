import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import javax.json.Json;
import javax.json.JsonObject;

import org.apache.commons.codec.EncoderException;
import org.json.JSONException;
import org.json.JSONObject;

import com.google.common.base.Function;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Multimap;

public class List2Json {

	public static void main(String[] args) throws IOException {
		Multimap<Path, Path> children = HashMultimap.create();
		Map<Path, Path> childDir2ParentDir = new HashMap<Path, Path>();

		Path root = null;
		int rootDepth = Integer.MAX_VALUE;
		BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
		String line;
		while ((line = br.readLine()) != null) {
//System.err.print(".");
//System.err.println("[DEBUG] List2Json.main() " + line);
			Path p = Paths.get(line);
			Path parent = p.getParent();
			if (parent != null) {
				if (rootDepth > parent.getNameCount()) {
					root = parent;
				}

				children.put(parent, p);
				childDir2ParentDir.put(parent, parent.getParent());
			}
		}
		if (root == null) {
			throw new RuntimeException("Couldn't determine root");
		}
		JSONObject json = toDirJson(root, children, childDir2ParentDir);
		JSONObject o = new JSONObject();
		o.put("itemsRecursive", json);
		System.out.println(o.toString(2));

	}

	private static JSONObject toDirJson(Path topLevel,
			Multimap<Path, Path> children, Map<Path, Path> childDir2ParentDir) throws JSONException, IOException {
System.err.println("[DEBUG] List2Json.toDirJson() " + topLevel.toString());
		if (!topLevel.toFile().isDirectory()) {
			throw new RuntimeException("Not a directory: "
					+ topLevel.toAbsolutePath().toString());
		}

		JSONObject filesOnlyMap = new JSONObject();
		Map<String, JSONObject> filesOnly = new HashMap<String, JSONObject>();
		Collection<Path> filesAndDirs = ImmutableSet.copyOf(children.get(topLevel));
		for (Path child : filesAndDirs) {
			System.err.print(".");
			try {
				if (!child.toFile().getCanonicalFile().isDirectory()) {
					filesOnly.put(child.toAbsolutePath().toString(), toFileJson(child));
					
				}
			} catch (Exception e) {
				throw new RuntimeException(e);
			}
		}
		try {
			Thread.sleep(5000L);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}

		for (String key : filesOnly.keySet()) {
			filesOnlyMap.put(key, filesOnly.get(key));
		}

		Map<String, JSONObject> dirsOnlyMap = new HashMap<String, JSONObject>();
		JSONObject dirs = new JSONObject();
		for (final Path child : ImmutableSet.copyOf(filesAndDirs)) {
			System.err.print(",");
			try {
				// symlink
				if (child.toFile() != child.toFile().getCanonicalFile()) {
					System.err.println("[DEBUG] List2Json.toDirJson() 2.5 symlink: " + child + " -> " + child.toFile().getCanonicalPath());
				}
				
				// symlink to dir
				if (child.toFile().getCanonicalFile().isDirectory()) {
					if (child.toFile() != child.toFile().getCanonicalFile()) {
						System.err.println("[DEBUG] List2Json.toDirJson() 2.5 symlink DIRECTORY: " + child + " -> " + child.toFile().getCanonicalPath());
					}
					dirsOnlyMap.put(child.toAbsolutePath().toString(),
							// TODO: This takes too long, so don't recurse
							//toDirJson(child, children, childDir2ParentDir)
							new JSONObject()
							);
				}
				if (child.toString().endsWith("other")) {
					System.err.println("List2Json.toDirJson() " + child + " canonical : " + child.toFile().getCanonicalFile());
					System.err.println("List2Json.toDirJson() " + child + " canonical is directory: " + child.toFile().getCanonicalFile().isDirectory());
				}
			} catch (Exception e) {
				throw new RuntimeException(e);
			}
		}
		try {
			Thread.sleep(5000L);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
		System.err.println("List2Json.toDirJson() 3.5: dir = " + topLevel);
		System.err.println("List2Json.toDirJson() 3.5: m3 size = " + dirsOnlyMap.size());
		// TODO: We need to wait for all threads to finish
		for (String key : dirsOnlyMap.keySet()) {
			System.err.println("List2Json.toDirJson() 4");
			dirs.put(key, dirsOnlyMap.get(key));
		}
		if (dirs.keySet().size() > 0) {
			filesOnlyMap.put("dirs", dirs);
		}

		JSONObject o = new JSONObject();
		if (filesOnlyMap.keySet().size() > 0) {
			System.err.println("List2Json.toDirJson() 5");
			o.put(topLevel.toAbsolutePath().toString(), filesOnlyMap);
		}
		// Too big
		//System.err.println("List2Json.toDirJson() 6" + o.toString());
		return o;
	}

	private static JSONObject toFileJson(Path child) {
		JSONObject jsonObject = new JSONObject(Mappings.PATH_TO_JSON_ITEM.apply(child)
				.toString());
		//System.out.println("List2Json.toFileJson() " + jsonObject);
		return jsonObject;
	}

	private static class Mappings {

		private static final Function<Path, JsonObject> PATH_TO_JSON_ITEM = new Function<Path, JsonObject>() {
			public JsonObject apply(Path iPath) {

				if (iPath.toFile().isDirectory()) {
					long created;
					try {
						created = Files
								.readAttributes(iPath,
										BasicFileAttributes.class)
								.creationTime().toMillis();
					} catch (IOException e) {
						System.err.println("PATH_TO_JSON_ITEM.apply() - "
								+ e.getMessage());
						created = 0;
					}
					JsonObject json = Json
							.createObjectBuilder()
							.add("location",
									iPath.getParent().toFile()
											.getAbsolutePath().toString())
							.add("fileSystem",
									iPath.toAbsolutePath().toString())
							.add("httpUrl",
									httpLinkFor(iPath.toAbsolutePath()
											.toString()))
							.add("thumbnailUrl",
									"http://www.pd4pic.com/images/windows-vista-folder-directory-open-explorer.png")
							.add("created", created).build();
					return json;
				} else {
					long created;
					try {
						created = Files
								.readAttributes(iPath,
										BasicFileAttributes.class)
								.creationTime().toMillis();
					} catch (IOException e) {
						System.err.println("PATH_TO_JSON_ITEM.apply() - "
								+ e.getMessage());
						created = 0;
					}

					return Json
							.createObjectBuilder()
							.add("location",
									iPath.getParent().toFile()
											.getAbsolutePath().toString())
							.add("fileSystem",
									iPath.toAbsolutePath().toString())
							.add("httpUrl",
									httpLinkFor(iPath.toAbsolutePath()
											.toString()))
							.add("thumbnailUrl",
									httpLinkFor(thumbnailFor(iPath)))
							.add("created", created).build();
				}
			}
		};

		private static final int fsPort = 4452;

		private static String httpLinkFor(String iAbsolutePath) {
			String prefix = "http://netgear.rohidekar.com:4" + fsPort;
			try {
				Path iPath = Paths.get(iAbsolutePath);
				// Looks like URLCodec has solved all the problems I was having
				String encode;
				try {
					encode = new org.apache.commons.codec.net.URLCodec("UTF8")
							.encode(iPath.getFileName().toString());
				} catch (EncoderException e) {
					encode = e.getMessage();
				}
				String abs = iPath.getParent().toAbsolutePath().toString();
				String s = abs + "/" + encode;

				return prefix + s;
			} catch (java.nio.file.InvalidPathException e) {
				e.printStackTrace();
				return prefix + "/InvalidPathException";
			}
		}

		private static String thumbnailFor(Path iPath) {
			Path f = iPath.getFileName().getFileName();
			String p = iPath.getParent().toFile().getAbsolutePath();
			return p + "/_thumbnails/" + f + ".jpg";
		}
	}

}
