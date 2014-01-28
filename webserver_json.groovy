import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLDecoder;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Response;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.comparator.SizeFileComparator;
import org.apache.commons.io.filefilter.IOFileFilter;
import org.glassfish.jersey.jdkhttp.JdkHttpServerFactory;
import org.glassfish.jersey.server.ResourceConfig;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import com.sun.net.httpserver.HttpServer;

public class Server {
	@Path("helloworld")
	public static class HelloWorldResource { // Must be public

		private static final String HIGHER_RANK = "_+1";
		private static final String LOWER_RANK = "_-1";
		private static final Collection<String> IGNORED_FILES = new LinkedHashSet<String>() {
			{
				add(".DS_Store");
				add(".picasa.ini");
			}
		};

		//
		// Read-only operations
		//
		@GET
		@Path("json")
		@Produces("application/json")
		public Response json(@QueryParam("dir") String iPath) throws JSONException,
				UnsupportedEncodingException {

			String dir = URLDecoder.decode(iPath, "UTF-8");
			Collection<File> files = FileUtils.listFiles(new File(dir), new IOFileFilter() {

				public boolean accept(File file) {
					// TODO: This isn't working
					if (IGNORED_FILES.contains(file.getName())) {
						return false;
					}
					return true;
				}

				public boolean accept(File dir2, String name) {
					return false;
				}
			}, null);
			List<File> fileList = new LinkedList<File>(files);
			Collections.sort(fileList, SizeFileComparator.SIZE_REVERSE);
			System.out.println(files.size());
			JSONObject outerJson = new JSONObject();

			// Level zero
			JSONArray jsonLevel0 = new JSONArray();
			for (Object o : fileList) {
				File f = (File) o;
				jsonLevel0.put(f.getAbsolutePath());
				System.out.println(f.getAbsolutePath());
			}
			outerJson.put("0", jsonLevel0);
			// Upper levels
			String inner = dir + "/_+1";
			File innerDir = new File(inner);
			if (innerDir.exists()) {
				getImagesAtLevel(innerDir, outerJson, 1, 1);
			}
			// Lower levels
			String innerMinus = dir + "/_-1";
			File innerMinusDir = new File(innerMinus);
			if (innerMinusDir.exists()) {
				getImagesAtLevel(innerMinusDir, outerJson, -1, -1);
			}
			System.out.println(outerJson.toString());
			return Response.ok().header("Access-Control-Allow-Origin", "*")
					.entity(outerJson.toString()).type("application/json").build();
		}

		private void getImagesAtLevel(File dir, JSONObject outerJson, int level, int increment)
				throws JSONException {

			Collection<File> files = FileUtils.listFiles(dir, new IOFileFilter() {

				public boolean accept(File file) {
					return true;
				}

				public boolean accept(File dir3, String name) {
					return false;
				}
			}, null);

			List<File> fileList = new LinkedList<File>(files);
			Collections.sort(fileList, SizeFileComparator.SIZE_REVERSE);
			JSONArray json = new JSONArray();
			for (Object o : fileList) {
				File f = (File) o;
				json.put(f.getAbsolutePath());
				System.out.println(f.getAbsolutePath());
			}
			outerJson.put(((Integer) level).toString(), json);

			// TODO: recurse
			String subdir;
			String incrementStr = "-1";
			if (increment == 1) {
				incrementStr = "+1";
			}
			String incrementStrFull = "/_" + incrementStr;
			String inner = dir.getAbsolutePath() + incrementStrFull;
			File innerDir2 = new File(inner);
			if (innerDir2.exists()) {
				getImagesAtLevel(innerDir2, outerJson, level + increment, increment);
			}
		}

		//
		// Write operations
		//

		@GET
		@Path("moveUp")
		@Produces("application/json")
		public Response moveUp(@QueryParam("path") String iPath) throws JSONException {
			System.out.println("moveUp() - " + iPath);

			String targetSubdirName = HIGHER_RANK;

			moveFileToSubfolder(iPath, targetSubdirName);
			JSONObject json = new JSONObject();
			System.out.println("moveUp() - end");
			return Response.ok().header("Access-Control-Allow-Origin", "*").entity(json.toString())
					.type("application/json").build();
		}

		@GET
		@Path("moveDown")
		@Produces("application/json")
		public Response moveDown(@QueryParam("path") String iPath) throws JSONException {
			System.out.println("moveUp() - " + iPath);
			String targetSubdirName = LOWER_RANK;
			File fileToMove = new File(iPath);
			String higherRank = "_+";
			if (fileToMove.getParentFile().getName().startsWith(higherRank)) {
				moveToParentDir(fileToMove);
			} else {
				moveFileToSubfolder(iPath, targetSubdirName);
			}

			JSONObject json = new JSONObject();
			System.out.println("moveUp() - end");
			return Response.ok().header("Access-Control-Allow-Origin", "*").entity(json.toString())
					.type("application/json").build();
		}

		private static void moveToParentDir(File fileToMove) {
			try {
				FileUtils.moveFileToDirectory(fileToMove, fileToMove.getParentFile()
						.getParentFile(), false);
			} catch (IOException e) {
				e.printStackTrace();
			}
		}

		@GET
		@Path("duplicate")
		@Produces("application/json")
		public Response removeDuplicate(@QueryParam("path") String iPath) throws JSONException {
			String targetSubdirName = "duplicates";
			moveFileToSubfolder(iPath, targetSubdirName);
			JSONObject json = new JSONObject();
			System.out.println("removeDuplicate() - end");
			return Response.ok().header("Access-Control-Allow-Origin", "*").entity(json.toString())
					.type("application/json").build();
		}
		
		@GET
		@Path("wrongCategory")
		@Produces("application/json")
		public Response wrongCategory(@QueryParam("path") String iPath) throws JSONException {
			moveToParentDir(new File(iPath));
			JSONObject json = new JSONObject();
			System.out.println("removeDuplicate() - end");
			return Response.ok().header("Access-Control-Allow-Origin", "*").entity(json.toString())
					.type("application/json").build();
		}

		/**
		 * 
		 * @param iPath
		 * @param targetSubdirName
		 *            - just the name, not the path
		 */
		private static void moveFileToSubfolder(String iPath, String targetSubdirName) {
			_1: {
				String theContainingDirPath = FilenameUtils.getFullPath(iPath);
				System.out.println();
				String theTargetDirPath = theContainingDirPath + "/" + targetSubdirName;
				File theTargetDir = new File(theTargetDirPath);
				boolean createTargetDir = true;
				File fileToMove = new File(iPath);
				String destinationFilePath;
				String fileShortName = FilenameUtils.getName(iPath);
				System.out.println("moveFileToSubfolder() - checking if exists");
				if (theTargetDir.exists()) {
					System.out
							.println("moveFileToSubfolder() - dir already exists, need to make sure existing file is not overwritten");
					createTargetDir = false;
					destinationFilePath = theTargetDirPath + "/" + fileShortName;
					String extension = FilenameUtils.getExtension(destinationFilePath);
					String destinationFilePathWithoutExtension = destinationFilePath.substring(0,
							destinationFilePath.lastIndexOf('.'));
					File destinationFile = new File(destinationFilePath);
					while (destinationFile.exists()) {
						destinationFilePathWithoutExtension += "1";
						destinationFilePath = destinationFilePathWithoutExtension + "." + extension;
						destinationFile = new File(destinationFilePath);
					}
					fileShortName = FilenameUtils.getName(destinationFilePath);
					// Double check, for now. We don't want to lose any images
					if (destinationFile.exists()) {
						throw new RuntimeException("Developer error");
					}
				}

				try {
					System.out.println("moveFileToSubfolder() - about to move");
					FileUtils.moveFileToDirectory(fileToMove, theTargetDir, createTargetDir);
					System.out.println("moveFileToSubfolder() - success");
					check: {
						File newFile = new File(theTargetDirPath + "/" + fileShortName);
						// check that it exists
						if (!newFile.exists()) {
							throw new RuntimeException("Developer error");
						}
						System.out.println("moveFileToSubfolder() - " + newFile.getAbsolutePath());
					}
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}
	}

	public static void main(String[] args) throws URISyntaxException {
		HttpServer server = JdkHttpServerFactory.createHttpServer(
				new URI("http://localhost:9099/"), new ResourceConfig(HelloWorldResource.class));
	}
}