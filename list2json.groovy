import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.HashMap;
import java.util.Map;

import javax.json.Json;
import javax.json.JsonObject;

import org.apache.commons.codec.EncoderException;
import org.json.JSONObject;

import com.google.common.base.Function;
import com.google.common.collect.HashMultimap;
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
			Multimap<Path, Path> children, Map<Path, Path> childDir2ParentDir) {

		if (!topLevel.toFile().isDirectory()) {
			throw new RuntimeException("Not a directory: "
					+ topLevel.toAbsolutePath().toString());
		}

		JSONObject value = new JSONObject();
		for (Path child : children.get(topLevel)) {
			try {
				if (!child.toFile().getCanonicalFile().isDirectory()) {
					value.put(child.toAbsolutePath().toString(), toFileJson(child));
				}
			} catch (Exception e) {
				throw new RuntimeException(e);
			}
		}

		JSONObject dirs = new JSONObject();
		for (Path child : children.get(topLevel)) {
			if (child.toFile().isDirectory()) {
				dirs.put(child.toAbsolutePath().toString(),
						toDirJson(child, children, childDir2ParentDir));
			}
		}
		if (dirs.keySet().size() > 0) {
			value.put("dirs", dirs);
		}

		JSONObject o = new JSONObject();
		if (value.keySet().size() > 0) {
			o.put(topLevel.toAbsolutePath().toString(), value);
		}
		return o;
	}

	private static JSONObject toFileJson(Path child) {
		return new JSONObject(Mappings.PATH_TO_JSON_ITEM.apply(child)
				.toString());
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