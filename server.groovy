import static com.google.common.base.Predicates.not;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileFilter;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.StringReader;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.AccessDeniedException;
import java.nio.file.DirectoryStream;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.spi.FileSystemProvider;
import java.util.AbstractMap;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
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
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Function;
import com.google.common.base.Predicate;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSet.Builder;
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
						"/media/sarnobat/Unsorted/images/",
						"/media/sarnobat/Unsorted/Videos/",
						"/media/sarnobat/d/Videos",
						"/e/Sridhar/Atletico Madrid/",
						"/e/Sridhar UK/Atletico Madrid/",
						"/e/Sridhar UK/Photos/Cats/",
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

		private static String getStatus(SftpClient sftp) {
			return "";
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
		private static final int LIMIT = 80;

		@GET
		@javax.ws.rs.Path("list")
		@Produces("application/json")
		public Response list(@QueryParam("dirs") String iDirectoryPathsString)
				throws JSONException, IOException {
			System.out.println("list() - begin");
			try {
				// To create JSONObject, do new JSONObject(aJsonObject.toString). But the other way round I haven't figured out
				JsonObject response = getDirectoryHierarchies(iDirectoryPathsString);
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

		private static JsonObject getDirectoryHierarchies(String iDirectoryPathsString) throws IOException {
			JsonObject response = Json
					.createObjectBuilder()
					.add("itemsRecursive",
							RecursiveLimitByTotal.createFilesJsonRecursive(
									iDirectoryPathsString.split("\\n"), 
									LIMIT, LEVELS_TO_RECURSE))
					.build();
			return response;
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

	private static class RecursiveLimitByTotal {

		/**
		 * Keep adding one more file from each directory (and its subdirectories) 
		 * until we reach the limit. The intention is to not spend too much time
		 * walking the file system and it taking such a long time for a response
		 * to the user. At the same time, we want to spread the returned files all over the hierarchy, not be hogged by one big directory.
		 */
		static JsonObject createFilesJsonRecursive(String[] iDirectoryPathStrings, int iLimit, int maxDepth)
				throws IOException {
			return fold(createDirecctoryHierarchies(iDirectoryPathStrings, iLimit, 1, maxDepth));
		}

		private static Set<JsonObject> createDirecctoryHierarchies(String[] iDirectoryPathStrings,
				int iLimit, int filesPerLevel, int maxDepth) {
//			System.out.println("Coagulate.RecursiveLimitByTotal.createShards() - begin");
			Set<JsonObject> directoryHierarchies = new HashSet<JsonObject>();
			Set<String> filesAlreadyObtained = new HashSet<String>();
			int total = 0;
			int swoopNumber = 1;
			boolean debug = false;
			while(total < iLimit){
				List<String> dirPaths = ImmutableList.copyOf(iDirectoryPathStrings);
//				System.out.println("Coagulate.RecursiveLimitByTotal.createDirecctoryHierarchies() - swoop number " + swoopNumber + ", total = " + total);
				swoopNumber++;
				Set<JsonObject> oneSwoopThroughDirs = swoopThroughDirs(dirPaths.get(0), dirPaths.subList(1, dirPaths.size()),iLimit, filesPerLevel, filesAlreadyObtained, maxDepth, total);
				
				Set<String> files = getFiles(oneSwoopThroughDirs);
				printFiles(files);
				if (files.size() == 0) {
					// We didn't hit the limit, but the number of files in the specified dirs doesn't exceed the limit, i.e. there are no more files left that can be gotten.
					break;
				}
				System.out.println("Coagulate.RecursiveLimitByTotal.createDirecctoryHierarchies() - files already obtained before = " + filesAlreadyObtained.size());
				filesAlreadyObtained.addAll(files);
				System.out.println("Coagulate.RecursiveLimitByTotal.createDirecctoryHierarchies() - files already obtained after = " + filesAlreadyObtained.size());
				directoryHierarchies.addAll(oneSwoopThroughDirs);
				total = filesAlreadyObtained.size();//countAllFiles(directoryHierarchies);
				
				if (debug) {
					int countAllFiles = countAllFiles(directoryHierarchies);
					if (filesAlreadyObtained.size() != countAllFiles) {
						throw new RuntimeException(countAllFiles + " vs " + filesAlreadyObtained.size());
					}
				}
			}
			if (debug) {
				int countAllFiles = countAllFiles(directoryHierarchies);
				if (filesAlreadyObtained.size() != countAllFiles) {
					throw new RuntimeException(countAllFiles + " vs " + filesAlreadyObtained.size());
				} else {
					System.out
							.println("Coagulate.RecursiveLimitByTotal.createDirecctoryHierarchies() - " + countAllFiles + ", " + directoryHierarchies.size());
				}
			} else {
				System.out.println("Coagulate.RecursiveLimitByTotal.createDirecctoryHierarchies() - debug not enabled");
			}
			System.out.println("Coagulate.RecursiveLimitByTotal.createDirecctoryHierarchies() - " + filesAlreadyObtained);
			System.out.println("Coagulate.RecursiveLimitByTotal.createDirecctoryHierarchies() - " + countAllFiles(directoryHierarchies));
//			System.out.println("Coagulate.RecursiveLimitByTotal.createShards() - end - " + directoryHierarchies);
			return directoryHierarchies;
		}

		private static void printSwoop(Set<JsonObject> oneSwoopThroughDirs) {
			for (JsonObject mergedDip : oneSwoopThroughDirs) {
				printMergedDips(mergedDip);
			}
		}

		private static void printMergedDips(JsonObject mergedDip) {
			for (String file : getFilesInShard(mergedDip)) {
				System.out.println("Coagulate.RecursiveLimitByTotal.printMergedDips() - " + file);
			}
		}

		@Deprecated // this is wrong. Find out why.
		private static int countAllFiles(Set<JsonObject> directoryHierarchies) {
			int total = 0;
			for (JsonObject aHierarchy : directoryHierarchies) {
				total += countFilesInHierarchy2(aHierarchy);
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

		private static int countFilesInShard(JsonObject aShard) {
			return countFilesInHierarchy(getOnlyValue(aShard));
		}
		@VisibleForTesting static int countFilesInHierarchy(JsonObject aHierarchy) {
			validateIsDirectoryNode(aHierarchy);
			int count = FluentIterable.from(aHierarchy.keySet()).filter(not(DIRS)).toSet().size();
			if (aHierarchy.containsKey("dirs")) {
				JsonObject dirs = aHierarchy.getJsonObject("dirs");
//				System.out.println("Coagulate.RecursiveLimitByTotal.countFilesInHierarchy() - dirs = " + dirs.toString());
//				System.out.println("Coagulate.RecursiveLimitByTotal.countFilesInHierarchy() - dirs keys: " + dirs.keySet());
				for (String keyInDirs : dirs.keySet()) {
					JsonObject dirJsonInDirs = dirs.getJsonObject(keyInDirs);
					System.out.println("Coagulate.RecursiveLimitByTotal.countFilesInHierarchy() - getting count for dir: " + dirJsonInDirs);
					count += countFilesInHierarchy(dirJsonInDirs);
				}
			}
//			System.out.println("Coagulate.RecursiveLimitByTotal.countFilesInHierarchy() - found " + count + " more files in " + aHierarchy.toString());
			return count;
		}

		private static final Predicate<String> DIRS = new Predicate<String>() {
			@Override
			public boolean apply(String input) {
				return "dirs".equalsIgnoreCase(input);
			}
			
		};

		private static void printFiles(Set<String> alreadyObtained) {
			for (Iterator iterator = alreadyObtained.iterator(); iterator.hasNext();) {
				String string = (String) iterator.next();
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
			JsonObject shard = shard2.getJsonObject((String)shard2.keySet().toArray()[0]);
			return getFilesInShard(shard);
		}
		
		private static Set<String> getFilesInShard(JsonObject shard) {
//			System.out.println("Coagulate.RecursiveLimitByTotal.getFilesInShard() - " + shard);
			Set<String> keysInShard = new HashSet();
			keysInShard.addAll(shard.keySet());
			if (shard.containsKey("dirs")) {
				keysInShard.remove("dirs");
				JsonObject jsonObject = shard.getJsonObject("dirs");
				for(String dirKey : jsonObject.keySet()) {
					JsonObject dirJson = jsonObject.getJsonObject(dirKey);
					Set<String> filesInShard = getFilesInShard(dirJson);
					keysInShard.addAll(filesInShard);
				}
			} else {
				throw new RuntimeException("You must call this method on a directory node");
			}
			return keysInShard;
		}

		private static int totalFiles(Set<JsonObject> shards) {
			int total = 0;
			for(JsonObject shard : shards) {
				total += countFilesInShard(shard);
			}
			return total;
		}

//		private static int countFilesInShard(JsonObject shard) {
////			System.out.println("Coagulate.RecursiveLimitByTotal.countFilesInShard() - begin " + shard);
//			int count = shard.keySet().size();
//			if (shard.containsKey("dirs")) {
//				count -= 1;				
//				count += countFilesInShard(shard.getJsonObject("dirs"));
//			}
////			System.out.println("Coagulate.RecursiveLimitByTotal.countFilesInShard() - end " + count);
//			return count;
//		}

		private static Set<JsonObject> swoopThroughDirs(String dirPath,
				List<String> dirPathsRemaining, int iLimit, int filesPerLevel,
				Set<String> filesAlreadyAdded, int maxDepth, int iTotal) {
//			System.out.println("Coagulate.RecursiveLimitByTotal.swoopThroughDirs() - " + dirPath);
			//
			// Base case (just 1 dir to swoop through)
			//
//			System.out.println("Coagulate.RecursiveLimitByTotal.createShardsRecursive() - begin: " + dirPath);
			Builder<JsonObject> shardsForDir = ImmutableSet.builder();
			// just get one file from every subdir
			JsonObject dirHierarchyJson = dipIntoDir(Paths.get(dirPath), filesPerLevel,
					filesAlreadyAdded, maxDepth, iTotal, iLimit);
//			System.out.println("Coagulate.RecursiveLimitByTotal.createShardsRecursive() - shard " + dirHierarchyJson);
			
			JsonObjectBuilder dirHierarchyJson2 = Json.createObjectBuilder();
			dirHierarchyJson2.add(dirPath, dirHierarchyJson);
			shardsForDir.add(dirHierarchyJson2.build());
			
			//
			// Recursive case
			//
			if (dirPathsRemaining.size() == 0) {
			} else {
				List<String> tail;
				if (dirPathsRemaining.size() == 1) {
					System.out
							.println("Coagulate.RecursiveLimitByTotal.createShardsRecursive() - remaining 1");
					tail = ImmutableList.of(dirPathsRemaining.get(0));
				} else {
					System.out
							.println("Coagulate.RecursiveLimitByTotal.createShardsRecursive() - remaining "
									+ dirPathsRemaining.size());
					tail = dirPathsRemaining.subList(1, dirPathsRemaining.size());
				}
				for (JsonObject shard : swoopThroughDirs(dirPathsRemaining.get(0), tail,
						iLimit, filesPerLevel, filesAlreadyAdded, maxDepth, iTotal
								+ countFilesInShard(dirHierarchyJson))) {
					JsonObjectBuilder ret = Json.createObjectBuilder();
					ret.add(dirPath, shard);
					JsonObject shard2 = ret.build();
					if (shard2.containsKey("dirs")) {
						throw new RuntimeException(shard2.toString());
					}
					shardsForDir.add(shard2);
				}
			}
//			System.out.println("Coagulate.RecursiveLimitByTotal.createShardsRecursive() - end: "
//					+ dirPath);
			ImmutableSet<JsonObject> build = shardsForDir.build();
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

		private static JsonObject dipIntoDir(Path iDirectoryPath, int filesPerLevel, Set<String> filesToIgnore, int maxDepth, int iTotalInShardSoFar, int iLimit) {
//			System.out.println("Coagulate.RecursiveLimitByTotal.dipIntoDir() - " + iDirectoryPath.toAbsolutePath().toString());
			JsonObjectBuilder dirHierarchyJson = Json.createObjectBuilder();
//			int totalInShardSoFar = iTotalInShardSoFar;
			// Sanity check
			if (!iDirectoryPath.toFile().isDirectory()) {
				throw new RuntimeException("cannot create a shard from a regular file");
			}
			// Get one leaf node
			try {
				DirectoryStream<Path> filesInDir2 = Files.newDirectoryStream(iDirectoryPath, isFile);
				Set<Path> filesInDir = FluentIterable.from(filesInDir2).transform(new Function<Path, Path>(){
					@Override
					public Path apply(Path input) {
						return input;
					}}).toSet();
				filesInDir2.close();
				int addedCount = 0;
				for (Path p : FluentIterable.from(filesInDir).filter(
						not(new Predicates.Contains(filesToIgnore)))) {
//					System.out.println("Coagulate.RecursiveLimitByTotal.getContentsRecursive() - "
//							+ p.toAbsolutePath().toString());
					dirHierarchyJson.add(p.toAbsolutePath().toString(),
							Mappings.PATH_TO_JSON_ITEM.apply(p));
					++addedCount;
//					++totalInShardSoFar;
					filesToIgnore.add(p.toAbsolutePath().toString());
					System.out.println("Coagulate.RecursiveLimitByTotal.dipIntoDir() - files added: " + filesToIgnore.size());
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
			// For ALL subdirectories, recurse
			try {
				JsonObjectBuilder dirsJson = Json.createObjectBuilder();
				DirectoryStream<Path> subdirectories = Files.newDirectoryStream(iDirectoryPath, isDirectory);
				for (Path p : subdirectories) {
//					System.out.print("d");
					JsonObject contentsRecursive = dipIntoDir(p, filesPerLevel, filesToIgnore, --maxDepth, -1, iLimit);
					if (filesToIgnore.size() > iLimit) {
						break;
					}
//					totalInShardSoFar += countFilesInShard(contentsRecursive);
					dirsJson.add(p.toAbsolutePath().toString(),contentsRecursive);
				}
				Files.newDirectoryStream(iDirectoryPath, isDirectory).close();
				dirHierarchyJson.add("dirs", dirsJson.build());
			} catch (IOException e) {
				e.printStackTrace();
			}
//			System.out.println("Coagulate.RecursiveLimitByTotal.getContentsRecursive() - end " + iDirectoryPath.toAbsolutePath());
			return dirHierarchyJson.build();
		}

		private static final Function<Path,String> PATH_TO_STRING = new Function<Path,String>(){
			@Override
			public String apply(Path input) {
				return input.toAbsolutePath().toString();
			}
		};
		
		// precondition : the directory structure of all members of the input are the same
		private static JsonObject fold(Set<JsonObject> directoryHierarchies) {
			validate(directoryHierarchies);
			if (directoryHierarchies.size() == 0) {
				return Json.createObjectBuilder().build();
			}
			if (directoryHierarchies.size() == 1) {
				return directoryHierarchies.iterator().next();
			}
			List<JsonObject> l = ImmutableList.copyOf(directoryHierarchies);
			JsonObject head = l.get(0);
			if (head.containsKey("dirs")) {
				throw new RuntimeException(head.toString());
			}
			List<JsonObject> tail = l.subList(1, l.size());
			return mergeRecursive2(head, tail);
		}

		private static void validate(Set<JsonObject> directoryHierarchies) {
			for (Iterator iterator = directoryHierarchies.iterator(); iterator.hasNext();) {
				JsonObject jsonObject = (JsonObject) iterator.next();
				// System.out.println("Coagulate.RecursiveLimitByTotal.validate() - "
				// + jsonObject);
			}
		}

		private static JsonObject mergeRecursive2(JsonObject accumulated, List<JsonObject> dirs) {
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
			System.out.println("Coagulate.RecursiveLimitByTotal.mergeRecursive() - accumulated:\t" + accumulated.toString());
			if (dirs.size() == 0) {
				return accumulated;
			}
			JsonObject head = dirs.get(0);
			List<JsonObject> tail = dirs.subList(1, dirs.size());
			return mergeRecursive(mergeDirectoryHierarchies(accumulated, head), tail);
		}

		private static JsonObject mergeSetsOfDirectoryHierarchies(JsonObject dirs1, JsonObject dirs2) {
			JsonObjectBuilder ret = Json.createObjectBuilder();
			for (String key1 : dirs1.keySet()) {
				JsonObject jsonObject = dirs1.getJsonObject(key1);
				JsonObject jsonObject2 = dirs2.getJsonObject(key1);
//				int i = countFilesInHierarchy(jsonObject);
//				int j = countFilesInHierarchy(jsonObject2);
				JsonObject jsonValue = mergeDirectoryHierarchies(jsonObject, jsonObject2);
//				int k = countFilesInHierarchy(jsonValue);
//				if (i + j != k) {
//					throw new RuntimeException("data lost");
//				}
				ret.add(key1, jsonValue);
			}
			JsonObject build = ret.build();
			return build;
		}

		private static JsonObject mergeDirectoryHierarchies2(JsonObject shard1, JsonObject shard2) {
			return mergeDirectoryHierarchies(getOnlyValue(shard1), getOnlyValue(shard2));
		}
		private static JsonObject getOnlyValue(JsonObject shard1) {
			return shard1.getJsonObject((String)shard1.keySet().toArray()[0]);
		}

		private static JsonObject mergeDirectoryHierarchies(JsonObject dir1, JsonObject dir2) {
			if (dir2 == null) {
				System.out.println("Coagulate.RecursiveLimitByTotal.mergeDirectoryHierarchies() - base case");
				return dir1;
			}
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
//			System.out.println("Coagulate.RecursiveLimitByTotal.mergeDirectoryHierarchies() - out\t" + build);
			return build;
		}

		private static void validateIsDirectoryNode(JsonObject dir) {
			
			if (!dir.isEmpty()) {
				if (!dir.containsKey("dirs")) {
					throw new RuntimeException("Not a directory node: " + dir);
				}
			}
			
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
	private static class RecursiveLimitByDepth {
		static JsonObject createFilesJsonRecursive(String[] iDirectoryPathStrings, int iLevelsToRecurse)
				throws IOException {
			JsonObjectBuilder rItemsJson = Json.createObjectBuilder();
			for (String aDirectoryPathString : FluentIterable.from(ImmutableSet.copyOf(iDirectoryPathStrings)).filter(Predicates.SHOULD_GET_CONTENTS)) {
				rItemsJson.add(aDirectoryPathString,
						getContentsAsJsonRecursive(Paths.get(aDirectoryPathString).toFile(), iLevelsToRecurse));
			}
			return rItemsJson.build();
		}
		
//		@Deprecated // We're hardcoding the number of levels to recurse.
//		private static JsonObject createItemDetailsJsonRecursive(String iDirectoryPathString)
//				throws IOException {
//			return getContentsAsJsonRecursive(new File(iDirectoryPathString), 2);
//		}
		
		@Deprecated // TODO: bad. Do not use output parameters. Return it instead.
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

		private File[] getDirectories(File loc) {
			return loc.listFiles((FileFilter) FileFilterUtils
					.directoryFileFilter());
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

		private static JsonObject getContentsAsJsonRecursive(File iDirectory, int iLevelToRecurse)
				throws IOException {
			JsonObjectBuilder rFilesInLocationJson = Json.createObjectBuilder();
			rFilesInLocationJson.add("dirs", getDirsJson(iDirectory));
			for (JsonObject fileEntryJson : getFilesJson(iDirectory)) {
				rFilesInLocationJson.add(fileEntryJson.getString("fileSystem"),
						fileEntryJson);
			}
			return rFilesInLocationJson.build();
		}

		private static Set<JsonObject> getFilesJson(File iDirectory)
				throws IOException {
			DirectoryStream<Path> subdirectoryStream = Utils.getDirectoryStream(iDirectory);
			Set<JsonObject> filesJson = FluentIterable
					.from(subdirectoryStream).filter(Predicates.IS_DISPLAYABLE).transform(Mappings.PATH_TO_JSON_ITEM)
					.toSet();
			subdirectoryStream.close();
			return filesJson;
		}

		private static JsonObject getDirsJson(File iDirectory)
				throws IOException {
			System.out.println();
			System.out.println("getContentsAsJsonRecursive() - "
					+ iDirectory.toString());
			JsonObjectBuilder builder = Json.createObjectBuilder();
			for (Map.Entry<String, JsonObject> pair : getDirContents(iDirectory)) {
				builder.add(pair.getKey(), pair.getValue());
			}
			return builder.build();
		}

		private static Set<Map.Entry<String, JsonObject>> getDirContents(
				File iDirectory) throws IOException {
			DirectoryStream<Path> directoryStreamRecursive = getDirectoryStreamRecursive(iDirectory);
			Set<Map.Entry<String, JsonObject>> directoryContents = FluentIterable
					.from(directoryStreamRecursive).filter(Predicates.IS_DIRECTORY)
					.transform(Mappings.DIR_PATH_TO_JSON_DIR).toSet();
			directoryStreamRecursive.close();
			return directoryContents;
		}
		
		private static DirectoryStream<Path> getDirectoryStreamRecursive(File aDirectory)
				throws IOException {
			String absolutePath = aDirectory.getAbsolutePath();
			Path aDirectoryPath = Paths.get(absolutePath);
			return getSubdirectoryStreamRecursive(aDirectoryPath);
		}
		
		private static DirectoryStream<Path> getSubdirectoryStreamRecursive(Path iDirectoryPath)
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
	}
	

	private static class Mappings {

		static final Function<String, Map.Entry<String, JsonObject>> DIR_TO_JSON = new Function<String, Map.Entry<String, JsonObject>>() {
			@Override
			@Nullable
			public Map.Entry<String, JsonObject> apply(@Nullable String iDirectoryPathString) {
				try {
					return new AbstractMap.SimpleEntry<String, JsonObject>(
							iDirectoryPathString,
							createSubdirDetailsJson2(iDirectoryPathString));
				} catch (Exception e) {
					e.printStackTrace();
					throw new RuntimeException(e);
				}
			}
		};
		
		@Deprecated // Use a function
		private static JsonObject createSubdirDetailsJson2(String iDirectoryPathString) throws IOException {
			return Mappings.getSubdirsAsJson2(new File(iDirectoryPathString));
		}

		private static JsonObject getSubdirsAsJson2(File iDirectory)
				throws IOException {
			DirectoryStream<Path> subdirectoryStream = getSubdirectoryStream2(iDirectory);
			Set<Path> files = FluentIterable.from(subdirectoryStream).filter(Predicates.IS_DIRECTORY).toSet();
			subdirectoryStream.close();
			return dirToJson(files);
		}
		

		private static DirectoryStream<Path> getSubdirectoryStream2(File aDirectory)
				throws IOException {
			String absolutePath = aDirectory.getAbsolutePath();
			Path aDirectoryPath = Paths.get(absolutePath);
			return getDirectoryStream2(aDirectoryPath);
		}
		
		private static DirectoryStream<Path> getDirectoryStream2(Path iDirectoryPath)
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
		
		private static JsonObject dirToJson(Set<Path> files) {
			JsonObjectBuilder rFilesInLocationJson = Json.createObjectBuilder();
			for (Path file : files) {
				System.out.println("dirToJson() - " + file.toString());
				rFilesInLocationJson.add(
						file.toAbsolutePath().toString(),
						createFileItemJson(file.getParent().toFile(), file.getFileName()
								.toString(), file.toAbsolutePath().toString()));
			}
			return rFilesInLocationJson.build();
		}
		
		@Deprecated // This only needs 1 parameter
		private static JsonObject createFileItemJson(File iDirectory, String filename,
				String fileAbsolutePath) {
			JsonObjectBuilder rFileEntryJson = Json.createObjectBuilder();
			rFileEntryJson.add("location", iDirectory.getAbsolutePath());
			rFileEntryJson.add("fileSystem", fileAbsolutePath);
			rFileEntryJson.add("httpUrl", Mappings.httpLinkFor(fileAbsolutePath));
			rFileEntryJson.add("thumbnailUrl",
					Mappings.httpLinkFor(iDirectory.getAbsolutePath()
							+ "/_thumbnails/" + filename + ".jpg"));
			System.out.println("thumbnail 2 : " + Mappings.httpLinkFor(iDirectory.getAbsolutePath()
					+ "/_thumbnails/" + filename + ".jpg"));
			return rFileEntryJson.build();
		}
		
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

		private static final Function<Path, Map.Entry<String, JsonObject>> DIR_PATH_TO_JSON_DIR = new Function<Path, Map.Entry<String, JsonObject>>() {
			@Override
			@Nullable
			public AbstractMap.SimpleEntry<String, JsonObject> apply(
					@Nullable Path dir) {
				if (!dir.toFile().isDirectory()) {
					throw new RuntimeException("not a dir: "
							+ dir.toAbsolutePath());
				}
//				System.out.print("d");
				JsonObject dirJson;
				try {
					dirJson = Utils.getContentsAsJson(dir.toFile());
				} catch (IOException e) {
					e.printStackTrace();
					throw new RuntimeException(e);
				}
				return new AbstractMap.SimpleEntry<String, JsonObject>(dir
						.toAbsolutePath().toString(), dirJson);
			}
		};
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
				if (filename.contains("DS_Store")) {
					return false;
				}
				if (filename.endsWith(".html") || filename.endsWith(".htm")) {
					return false;
				}
				return true;
			}
		};
		
		private static final Predicate<Path> IS_DIRECTORY = new Predicate<Path>() {
			@Override
			public boolean apply(Path iPath) {
				return iPath.toFile().isDirectory();
			}
		};

		static Predicate<String> SHOULD_GET_CONTENTS = new Predicate<String>() {

			@Override
			public boolean apply(@Nullable String iDirectoryPathString) {
				return shouldGetContents(iDirectoryPathString);
			}
		};
		@Deprecated // Use a function
		private static boolean shouldGetContents(String iDirectoryPathString) {
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
	}
	
	private static class Utils {
		
		static JsonObject getContentsAsJson(File iDirectory)
				throws IOException {
			JsonObjectBuilder rFilesInLocationJson = Json.createObjectBuilder();
			
			DirectoryStream<Path> directoryStream;
			Set<JsonObject> filesInLocation ;
			try {
				directoryStream = Utils.getDirectoryStream(iDirectory);
				filesInLocation = FluentIterable
						.from(directoryStream).filter(Predicates.IS_DISPLAYABLE)
						.transform(Mappings.PATH_TO_JSON_ITEM).toSet();
				directoryStream.close();// TODO: can't we close this sooner?
			}
			catch (AccessDeniedException e) {
				System.out.println("Coagulate.Utils.getContentsAsJson() - " + e);
				filesInLocation = ImmutableSet.of(); 
			}
			finally {
			} 
			for (JsonObject fileEntryJson : filesInLocation) {
				rFilesInLocationJson.add(fileEntryJson.getString("fileSystem"),
						fileEntryJson);
				if (fileEntryJson.toString().length() < 10) {
					System.out.println("Path not added correctly 1");
					throw new RuntimeException("Path not added correctly");
				}
			}
			return rFilesInLocationJson.build();
		}
		
		/**
		 * need to close the stream after use
		 */
		static DirectoryStream<Path> getDirectoryStream(File aDirectory)
				throws IOException {
			String absolutePath = aDirectory.getAbsolutePath();
			Path aDirectoryPath = Paths.get(absolutePath);
			return Utils.getDirectoryStream2(aDirectoryPath);
		}

		private static DirectoryStream<Path> getDirectoryStream2(Path iDirectoryPath)
				throws IOException {
			return Files
					.newDirectoryStream(iDirectoryPath,
							new DirectoryStream.Filter<Path>() {
								public boolean accept(Path entry)
										throws IOException {
									return !Files.isDirectory(entry);
								}
							});
		}
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

	private static JsonObject jsonFromString(String string) {
		JsonReader jsonReader = Json.createReader(new StringReader(string));
		JsonObject object = jsonReader.readObject();
		jsonReader.close();
		return object;
	}

	public static void main(String[] args) throws URISyntaxException, IOException {
//		JsonObject createFilesJsonRecursive = RecursiveLimitByTotal.createFilesJsonRecursive(new String[]{"/e/Drive J/pictures/Other (new)/pictures/misc_sync_master/wwe/Bella"}, 200, 3);
//		System.out.println("Coagulate.main() - list test " + createFilesJsonRecursive);
		if (true) {
//			System.exit(-1);
		}
	//	System.out.println("Coagulate.main() - " + MyResource.getDirectoryHierarchies("/Unsorted/images/"));
		
		JsonObject jsonFromString = jsonFromString("{\"/media/sarnobat/e/Drive J/pictures/Other (new)/pictures/misc_sync_master/wwe/Bella\":{\"/media/sarnobat/e/Drive J/pictures/Other (new)/pictures/misc_sync_master/wwe/Bella/1414371107.jpg\":{\"location\":\"/media/sarnobat/e/Drive J/pictures/Other (new)/pictures/misc_sync_master/wwe/Bella\",\"fileSystem\":\"/media/sarnobat/e/Drive J/pictures/Other (new)/pictures/misc_sync_master/wwe/Bella/1414371107.jpg\",\"httpUrl\":\"http://netgear.rohidekar.com:4451/cmsfs/static2//media/sarnobat/e/Drive J/pictures/Other (new)/pictures/misc_sync_master/wwe/Bella/1414371107.jpg\",\"thumbnailUrl\":\"http://netgear.rohidekar.com:4451/cmsfs/static2//media/sarnobat/e/Drive J/pictures/Other (new)/pictures/misc_sync_master/wwe/Bella/_thumbnails/1414371107.jpg.jpg\"},\"dirs\":{\"/media/sarnobat/e/Drive J/pictures/Other (new)/pictures/misc_sync_master/wwe/Bella/animated\":{\"/media/sarnobat/e/Drive J/pictures/Other (new)/pictures/misc_sync_master/wwe/Bella/animated/31X0cQ7.gif\":{\"location\":\"/media/sarnobat/e/Drive J/pictures/Other (new)/pictures/misc_sync_master/wwe/Bella/animated\",\"fileSystem\":\"/media/sarnobat/e/Drive J/pictures/Other (new)/pictures/misc_sync_master/wwe/Bella/animated/31X0cQ7.gif\",\"httpUrl\":\"http://netgear.rohidekar.com:4451/cmsfs/static2//media/sarnobat/e/Drive J/pictures/Other (new)/pictures/misc_sync_master/wwe/Bella/animated/31X0cQ7.gif\",\"thumbnailUrl\":\"http://netgear.rohidekar.com:4451/cmsfs/static2//media/sarnobat/e/Drive J/pictures/Other (new)/pictures/misc_sync_master/wwe/Bella/animated/_thumbnails/31X0cQ7.gif.jpg\"},\"dirs\":{}},\"/media/sarnobat/e/Drive J/pictures/Other (new)/pictures/misc_sync_master/wwe/Bella/brst\":{\"/media/sarnobat/e/Drive J/pictures/Other (new)/pictures/misc_sync_master/wwe/Bella/brst/0UOfWdA.gif\":{\"location\":\"/media/sarnobat/e/Drive J/pictures/Other (new)/pictures/misc_sync_master/wwe/Bella/brst\",\"fileSystem\":\"/media/sarnobat/e/Drive J/pictures/Other (new)/pictures/misc_sync_master/wwe/Bella/brst/0UOfWdA.gif\",\"httpUrl\":\"http://netgear.rohidekar.com:4451/cmsfs/static2//media/sarnobat/e/Drive J/pictures/Other (new)/pictures/misc_sync_master/wwe/Bella/brst/0UOfWdA.gif\",\"thumbnailUrl\":\"http://netgear.rohidekar.com:4451/cmsfs/static2//media/sarnobat/e/Drive J/pictures/Other (new)/pictures/misc_sync_master/wwe/Bella/brst/_thumbnails/0UOfWdA.gif.jpg\"},\"dirs\":{\"/media/sarnobat/e/Drive J/pictures/Other (new)/pictures/misc_sync_master/wwe/Bella/brst/_-1\":{\"/media/sarnobat/e/Drive J/pictures/Other (new)/pictures/misc_sync_master/wwe/Bella/brst/_-1/1414371569.jpg\":{\"location\":\"/media/sarnobat/e/Drive J/pictures/Other (new)/pictures/misc_sync_master/wwe/Bella/brst/_-1\",\"fileSystem\":\"/media/sarnobat/e/Drive J/pictures/Other (new)/pictures/misc_sync_master/wwe/Bella/brst/_-1/1414371569.jpg\",\"httpUrl\":\"http://netgear.rohidekar.com:4451/cmsfs/static2//media/sarnobat/e/Drive J/pictures/Other (new)/pictures/misc_sync_master/wwe/Bella/brst/_-1/1414371569.jpg\",\"thumbnailUrl\":\"http://netgear.rohidekar.com:4451/cmsfs/static2//media/sarnobat/e/Drive J/pictures/Other (new)/pictures/misc_sync_master/wwe/Bella/brst/_-1/_thumbnails/1414371569.jpg.jpg\"},\"dirs\":{}}}},\"/media/sarnobat/e/Drive J/pictures/Other (new)/pictures/misc_sync_master/wwe/Bella/btt\":{\"/media/sarnobat/e/Drive J/pictures/Other (new)/pictures/misc_sync_master/wwe/Bella/btt/0211.jpg\":{\"location\":\"/media/sarnobat/e/Drive J/pictures/Other (new)/pictures/misc_sync_master/wwe/Bella/btt\",\"fileSystem\":\"/media/sarnobat/e/Drive J/pictures/Other (new)/pictures/misc_sync_master/wwe/Bella/btt/0211.jpg\",\"httpUrl\":\"http://netgear.rohidekar.com:4451/cmsfs/static2//media/sarnobat/e/Drive J/pictures/Other (new)/pictures/misc_sync_master/wwe/Bella/btt/0211.jpg\",\"thumbnailUrl\":\"http://netgear.rohidekar.com:4451/cmsfs/static2//media/sarnobat/e/Drive J/pictures/Other (new)/pictures/misc_sync_master/wwe/Bella/btt/_thumbnails/0211.jpg.jpg\"},\"dirs\":{}},\"/media/sarnobat/e/Drive J/pictures/Other (new)/pictures/misc_sync_master/wwe/Bella/legs\":{\"/media/sarnobat/e/Drive J/pictures/Other (new)/pictures/misc_sync_master/wwe/Bella/legs/1412125008.png\":{\"location\":\"/media/sarnobat/e/Drive J/pictures/Other (new)/pictures/misc_sync_master/wwe/Bella/legs\",\"fileSystem\":\"/media/sarnobat/e/Drive J/pictures/Other (new)/pictures/misc_sync_master/wwe/Bella/legs/1412125008.png\",\"httpUrl\":\"http://netgear.rohidekar.com:4451/cmsfs/static2//media/sarnobat/e/Drive J/pictures/Other (new)/pictures/misc_sync_master/wwe/Bella/legs/1412125008.png\",\"thumbnailUrl\":\"http://netgear.rohidekar.com:4451/cmsfs/static2//media/sarnobat/e/Drive J/pictures/Other (new)/pictures/misc_sync_master/wwe/Bella/legs/_thumbnails/1412125008.png.jpg\"},\"dirs\":{}},\"/media/sarnobat/e/Drive J/pictures/Other (new)/pictures/misc_sync_master/wwe/Bella/navel\":{\"/media/sarnobat/e/Drive J/pictures/Other (new)/pictures/misc_sync_master/wwe/Bella/navel/212661619_478319_brie_bella_divas_champion_answer_5_xlarge.jpeg\":{\"location\":\"/media/sarnobat/e/Drive J/pictures/Other (new)/pictures/misc_sync_master/wwe/Bella/navel\",\"fileSystem\":\"/media/sarnobat/e/Drive J/pictures/Other (new)/pictures/misc_sync_master/wwe/Bella/navel/212661619_478319_brie_bella_divas_champion_answer_5_xlarge.jpeg\",\"httpUrl\":\"http://netgear.rohidekar.com:4451/cmsfs/static2//media/sarnobat/e/Drive J/pictures/Other (new)/pictures/misc_sync_master/wwe/Bella/navel/212661619_478319_brie_bella_divas_champion_answer_5_xlarge.jpeg\",\"thumbnailUrl\":\"http://netgear.rohidekar.com:4451/cmsfs/static2//media/sarnobat/e/Drive J/pictures/Other (new)/pictures/misc_sync_master/wwe/Bella/navel/_thumbnails/212661619_478319_brie_bella_divas_champion_answer_5_xlarge.jpeg.jpg\"},\"dirs\":{}},\"/media/sarnobat/e/Drive J/pictures/Other (new)/pictures/misc_sync_master/wwe/Bella/vg\":{\"/media/sarnobat/e/Drive J/pictures/Other (new)/pictures/misc_sync_master/wwe/Bella/vg/Bella-Twins-Maxim.jpg\":{\"location\":\"/media/sarnobat/e/Drive J/pictures/Other (new)/pictures/misc_sync_master/wwe/Bella/vg\",\"fileSystem\":\"/media/sarnobat/e/Drive J/pictures/Other (new)/pictures/misc_sync_master/wwe/Bella/vg/Bella-Twins-Maxim.jpg\",\"httpUrl\":\"http://netgear.rohidekar.com:4451/cmsfs/static2//media/sarnobat/e/Drive J/pictures/Other (new)/pictures/misc_sync_master/wwe/Bella/vg/Bella-Twins-Maxim.jpg\",\"thumbnailUrl\":\"http://netgear.rohidekar.com:4451/cmsfs/static2//media/sarnobat/e/Drive J/pictures/Other (new)/pictures/misc_sync_master/wwe/Bella/vg/_thumbnails/Bella-Twins-Maxim.jpg.jpg\"},\"dirs\":{}}}}}");
		int countFilesInHierarchy = RecursiveLimitByTotal.countFilesInHierarchy(jsonFromString.getJsonObject("/media/sarnobat/e/Drive J/pictures/Other (new)/pictures/misc_sync_master/wwe/Bella"));
		System.out.println("Coagulate.main() - " + countFilesInHierarchy);
//		System.out.println("Coagulate.main() - count test: " + RecursiveLimitByTotal.countAllFiles(ImmutableSet.of(jsonFromString("{\"/e/Drive J/pictures/Other (new)/pictures/misc_sync_master/wwe/Bella/1414371107.jpg\":{\"location\":\"/e/Drive J/pictures/Other (new)/pictures/misc_sync_master/wwe/Bella\",\"fileSystem\":\"/e/Drive J/pictures/Other (new)/pictures/misc_sync_master/wwe/Bella/1414371107.jpg\",\"httpUrl\":\"http://netgear.rohidekar.com:4451/cmsfs/static2//e/Drive J/pictures/Other (new)/pictures/misc_sync_master/wwe/Bella/1414371107.jpg\",\"thumbnailUrl\":\"http://netgear.rohidekar.com:4451/cmsfs/static2//e/Drive J/pictures/Other (new)/pictures/misc_sync_master/wwe/Bella/_thumbnails/1414371107.jpg.jpg\"},\"/e/Drive J/pictures/Other (new)/pictures/misc_sync_master/wwe/Bella/489.jpg\":{\"location\":\"/e/Drive J/pictures/Other (new)/pictures/misc_sync_master/wwe/Bella\",\"fileSystem\":\"/e/Drive J/pictures/Other (new)/pictures/misc_sync_master/wwe/Bella/489.jpg\",\"httpUrl\":\"http://netgear.rohidekar.com:4451/cmsfs/static2//e/Drive J/pictures/Other (new)/pictures/misc_sync_master/wwe/Bella/489.jpg\",\"thumbnailUrl\":\"http://netgear.rohidekar.com:4451/cmsfs/static2//e/Drive J/pictures/Other (new)/pictures/misc_sync_master/wwe/Bella/_thumbnails/489.jpg.jpg\"},\"/e/Drive J/pictures/Other (new)/pictures/misc_sync_master/wwe/Bella/tn00144.jpg\":{\"location\":\"/e/Drive J/pictures/Other (new)/pictures/misc_sync_master/wwe/Bella\",\"fileSystem\":\"/e/Drive J/pictures/Other (new)/pictures/misc_sync_master/wwe/Bella/tn00144.jpg\",\"httpUrl\":\"http://netgear.rohidekar.com:4451/cmsfs/static2//e/Drive J/pictures/Other (new)/pictures/misc_sync_master/wwe/Bella/tn00144.jpg\",\"thumbnailUrl\":\"http://netgear.rohidekar.com:4451/cmsfs/static2//e/Drive J/pictures/Other (new)/pictures/misc_sync_master/wwe/Bella/_thumbnails/tn00144.jpg.jpg\"},\"/e/Drive J/pictures/Other (new)/pictures/misc_sync_master/wwe/Bella/Brie-underwear.jpg\":{\"location\":\"/e/Drive J/pictures/Other (new)/pictures/misc_sync_master/wwe/Bella\",\"fileSystem\":\"/e/Drive J/pictures/Other (new)/pictures/misc_sync_master/wwe/Bella/Brie-underwear.jpg\",\"httpUrl\":\"http://netgear.rohidekar.com:4451/cmsfs/static2//e/Drive J/pictures/Other (new)/pictures/misc_sync_master/wwe/Bella/Brie-underwear.jpg\",\"thumbnailUrl\":\"http://netgear.rohidekar.com:4451/cmsfs/static2//e/Drive J/pictures/Other (new)/pictures/misc_sync_master/wwe/Bella/_thumbnails/Brie-underwear.jpg.jpg\"},\"/e/Drive J/pictures/Other (new)/pictures/misc_sync_master/wwe/Bella/18_BELLA_01272014_0019.jpg\":{\"location\":\"/e/Drive J/pictures/Other (new)/pictures/misc_sync_master/wwe/Bella\",\"fileSystem\":\"/e/Drive J/pictures/Other (new)/pictures/misc_sync_master/wwe/Bella/18_BELLA_01272014_0019.jpg\",\"httpUrl\":\"http://netgear.rohidekar.com:4451/cmsfs/static2//e/Drive J/pictures/Other (new)/pictures/misc_sync_master/wwe/Bella/18_BELLA_01272014_0019.jpg\",\"thumbnailUrl\":\"http://netgear.rohidekar.com:4451/cmsfs/static2//e/Drive J/pictures/Other (new)/pictures/misc_sync_master/wwe/Bella/_thumbnails/18_BELLA_01272014_0019.jpg.jpg\"},\"/e/Drive J/pictures/Other (new)/pictures/misc_sync_master/wwe/Bella/gWBwDn8.jpg\":{\"location\":\"/e/Drive J/pictures/Other (new)/pictures/misc_sync_master/wwe/Bella\",\"fileSystem\":\"/e/Drive J/pictures/Other (new)/pictures/misc_sync_master/wwe/Bella/gWBwDn8.jpg\",\"httpUrl\":\"http://netgear.rohidekar.com:4451/cmsfs/static2//e/Drive J/pictures/Other (new)/pictures/misc_sync_master/wwe/Bella/gWBwDn8.jpg\",\"thumbnailUrl\":\"http://netgear.rohidekar.com:4451/cmsfs/static2//e/Drive J/pictures/Other (new)/pictures/misc_sync_master/wwe/Bella/_thumbnails/gWBwDn8.jpg.jpg\"},\"/e/Drive J/pictures/Other (new)/pictures/misc_sync_master/wwe/Bella/nikki-21-612x350.jpg\":{\"location\":\"/e/Drive J/pictures/Other (new)/pictures/misc_sync_master/wwe/Bella\",\"fileSystem\":\"/e/Drive J/pictures/Other (new)/pictures/misc_sync_master/wwe/Bella/nikki-21-612x350.jpg\",\"httpUrl\":\"http://netgear.rohidekar.com:4451/cmsfs/static2//e/Drive J/pictures/Other (new)/pictures/misc_sync_master/wwe/Bella/nikki-21-612x350.jpg\",\"thumbnailUrl\":\"http://netgear.rohidekar.com:4451/cmsfs/static2//e/Drive J/pictures/Other (new)/pictures/misc_sync_master/wwe/Bella/_thumbnails/nikki-21-612x350.jpg.jpg\"},\"/e/Drive J/pictures/Other (new)/pictures/misc_sync_master/wwe/Bella/tumblr_n805iwkA6G1tfhqc0o3_250.png\":{\"location\":\"/e/Drive J/pictures/Other (new)/pictures/misc_sync_master/wwe/Bella\",\"fileSystem\":\"/e/Drive J/pictures/Other (new)/pictures/misc_sync_master/wwe/Bella/tumblr_n805iwkA6G1tfhqc0o3_250.png\",\"httpUrl\":\"http://netgear.rohidekar.com:4451/cmsfs/static2//e/Drive J/pictures/Other (new)/pictures/misc_sync_master/wwe/Bella/tumblr_n805iwkA6G1tfhqc0o3_250.png\",\"thumbnailUrl\":\"http://netgear.rohidekar.com:4451/cmsfs/static2//e/Drive J/pictures/Other (new)/pictures/misc_sync_master/wwe/Bella/_thumbnails/tumblr_n805iwkA6G1tfhqc0o3_250.png.jpg\"},\"/e/Drive J/pictures/Other (new)/pictures/misc_sync_master/wwe/Bella/pALrw.jpg\":{\"location\":\"/e/Drive J/pictures/Other (new)/pictures/misc_sync_master/wwe/Bella\",\"fileSystem\":\"/e/Drive J/pictures/Other (new)/pictures/misc_sync_master/wwe/Bella/pALrw.jpg\",\"httpUrl\":\"http://netgear.rohidekar.com:4451/cmsfs/static2//e/Drive J/pictures/Other (new)/pictures/misc_sync_master/wwe/Bella/pALrw.jpg\",\"thumbnailUrl\":\"http://netgear.rohidekar.com:4451/cmsfs/static2//e/Drive J/pictures/Other (new)/pictures/misc_sync_master/wwe/Bella/_thumbnails/pALrw.jpg.jpg\"},\"/e/Drive J/pictures/Other (new)/pictures/misc_sync_master/wwe/Bella/Bella-Twins wwe divas.jpg\":{\"location\":\"/e/Drive J/pictures/Other (new)/pictures/misc_sync_master/wwe/Bella\",\"fileSystem\":\"/e/Drive J/pictures/Other (new)/pictures/misc_sync_master/wwe/Bella/Bella-Twins wwe divas.jpg\",\"httpUrl\":\"http://netgear.rohidekar.com:4451/cmsfs/static2//e/Drive J/pictures/Other (new)/pictures/misc_sync_master/wwe/Bella/Bella-Twins wwe divas.jpg\",\"thumbnailUrl\":\"http://netgear.rohidekar.com:4451/cmsfs/static2//e/Drive J/pictures/Other (new)/pictures/misc_sync_master/wwe/Bella/_thumbnails/Bella-Twins wwe divas.jpg.jpg\"},\"/e/Drive J/pictures/Other (new)/pictures/misc_sync_master/wwe/Bella/nikki1.jpg\":{\"location\":\"/e/Drive J/pictures/Other (new)/pictures/misc_sync_master/wwe/Bella\",\"fileSystem\":\"/e/Drive J/pictures/Other (new)/pictures/misc_sync_master/wwe/Bella/nikki1.jpg\",\"httpUrl\":\"http://netgear.rohidekar.com:4451/cmsfs/static2//e/Drive J/pictures/Other (new)/pictures/misc_sync_master/wwe/Bella/nikki1.jpg\",\"thumbnailUrl\":\"http://netgear.rohidekar.com:4451/cmsfs/static2//e/Drive J/pictures/Other (new)/pictures/misc_sync_master/wwe/Bella/_thumbnails/nikki1.jpg.jpg\"},\"/e/Drive J/pictures/Other (new)/pictures/misc_sync_master/wwe/Bella/Nikki-Bella-Green.jpg\":{\"location\":\"/e/Drive J/pictures/Other (new)/pictures/misc_sync_master/wwe/Bella\",\"fileSystem\":\"/e/Drive J/pictures/Other (new)/pictures/misc_sync_master/wwe/Bella/Nikki-Bella-Green.jpg\",\"httpUrl\":\"http://netgear.rohidekar.com:4451/cmsfs/static2//e/Drive J/pictures/Other (new)/pictures/misc_sync_master/wwe/Bella/Nikki-Bella-Green.jpg\",\"thumbnailUrl\":\"http://netgear.rohidekar.com:4451/cmsfs/static2//e/Drive J/pictures/Other (new)/pictures/misc_sync_master/wwe/Bella/_thumbnails/Nikki-Bella-Green.jpg.jpg\"},\"/e/Drive J/pictures/Other (new)/pictures/misc_sync_master/wwe/Bella/Nikki-curves-002.gif\":{\"location\":\"/e/Drive J/pictures/Other (new)/pictures/misc_sync_master/wwe/Bella\",\"fileSystem\":\"/e/Drive J/pictures/Other (new)/pictures/misc_sync_master/wwe/Bella/Nikki-curves-002.gif\",\"httpUrl\":\"http://netgear.rohidekar.com:4451/cmsfs/static2//e/Drive J/pictures/Other (new)/pictures/misc_sync_master/wwe/Bella/Nikki-curves-002.gif\",\"thumbnailUrl\":\"http://netgear.rohidekar.com:4451/cmsfs/static2//e/Drive J/pictures/Other (new)/pictures/misc_sync_master/wwe/Bella/_thumbnails/Nikki-curves-002.gif.jpg\"},\"dirs\":{\"/e/Drive J/pictures/Other (new)/pictures/misc_sync_master/wwe/Bella/animated\":{\"dirs\":{}},\"/e/Drive J/pictures/Other (new)/pictures/misc_sync_master/wwe/Bella/brst\":{\"/e/Drive J/pictures/Other (new)/pictures/misc_sync_master/wwe/Bella/brst/ADocnYZ.gif\":{\"location\":\"/e/Drive J/pictures/Other (new)/pictures/misc_sync_master/wwe/Bella/brst\",\"fileSystem\":\"/e/Drive J/pictures/Other (new)/pictures/misc_sync_master/wwe/Bella/brst/ADocnYZ.gif\",\"httpUrl\":\"http://netgear.rohidekar.com:4451/cmsfs/static2//e/Drive J/pictures/Other (new)/pictures/misc_sync_master/wwe/Bella/brst/ADocnYZ.gif\",\"thumbnailUrl\":\"http://netgear.rohidekar.com:4451/cmsfs/static2//e/Drive J/pictures/Other (new)/pictures/misc_sync_master/wwe/Bella/brst/_thumbnails/ADocnYZ.gif.jpg\"},\"dirs\":{\"/e/Drive J/pictures/Other (new)/pictures/misc_sync_master/wwe/Bella/brst/duplicates\":{\"dirs\":{}},\"/e/Drive J/pictures/Other (new)/pictures/misc_sync_master/wwe/Bella/brst/_+1\":{\"dirs\":{}},\"/e/Drive J/pictures/Other (new)/pictures/misc_sync_master/wwe/Bella/brst/_-1\":{\"dirs\":{\"/e/Drive J/pictures/Other (new)/pictures/misc_sync_master/wwe/Bella/brst/_-1/_+1\":{\"dirs\":{}}}}}},\"/e/Drive J/pictures/Other (new)/pictures/misc_sync_master/wwe/Bella/btt\":{\"dirs\":{\"/e/Drive J/pictures/Other (new)/pictures/misc_sync_master/wwe/Bella/btt/duplicates\":{\"dirs\":{}},\"/e/Drive J/pictures/Other (new)/pictures/misc_sync_master/wwe/Bella/btt/_+1\":{\"dirs\":{\"/e/Drive J/pictures/Other (new)/pictures/misc_sync_master/wwe/Bella/btt/_+1/duplicates\":{\"dirs\":{}}}},\"/e/Drive J/pictures/Other (new)/pictures/misc_sync_master/wwe/Bella/btt/_-1\":{\"dirs\":{\"/e/Drive J/pictures/Other (new)/pictures/misc_sync_master/wwe/Bella/btt/_-1/duplicates\":{\"dirs\":{}}}}}},\"/e/Drive J/pictures/Other (new)/pictures/misc_sync_master/wwe/Bella/DivaBoard.com - Brie Bella - White Lightning - Powered by XMB 1.9.6 Nexus (Alpha)_files\":{\"dirs\":{\"/e/Drive J/pictures/Other (new)/pictures/misc_sync_master/wwe/Bella/DivaBoard.com - Brie Bella - White Lightning - Powered by XMB 1.9.6 Nexus (Alpha)_files/btt\":{\"dirs\":{}},\"/e/Drive J/pictures/Other (new)/pictures/misc_sync_master/wwe/Bella/DivaBoard.com - Brie Bella - White Lightning - Powered by XMB 1.9.6 Nexus (Alpha)_files/legs\":{\"dirs\":{}},\"/e/Drive J/pictures/Other (new)/pictures/misc_sync_master/wwe/Bella/DivaBoard.com - Brie Bella - White Lightning - Powered by XMB 1.9.6 Nexus (Alpha)_files/not good\":{\"dirs\":{}}}},\"/e/Drive J/pictures/Other (new)/pictures/misc_sync_master/wwe/Bella/DivaBoard.com - The Bella Twins  Bathing Suit Beauties[Diva Focus] - Powered by XMB 1.9.6 Nexus (Alpha)_files\":{\"dirs\":{\"/e/Drive J/pictures/Other (new)/pictures/misc_sync_master/wwe/Bella/DivaBoard.com - The Bella Twins  Bathing Suit Beauties[Diva Focus] - Powered by XMB 1.9.6 Nexus (Alpha)_files/legs\":{\"dirs\":{}},\"/e/Drive J/pictures/Other (new)/pictures/misc_sync_master/wwe/Bella/DivaBoard.com - The Bella Twins  Bathing Suit Beauties[Diva Focus] - Powered by XMB 1.9.6 Nexus (Alpha)_files/not good\":{\"dirs\":{}}}},\"/e/Drive J/pictures/Other (new)/pictures/misc_sync_master/wwe/Bella/DivaBoard.com - The Bellas   Diva Focus - Powered by XMB 1.9.6 Nexus (Alpha)_files\":{\"dirs\":{\"/e/Drive J/pictures/Other (new)/pictures/misc_sync_master/wwe/Bella/DivaBoard.com - The Bellas   Diva Focus - Powered by XMB 1.9.6 Nexus (Alpha)_files/brst\":{\"dirs\":{}},\"/e/Drive J/pictures/Other (new)/pictures/misc_sync_master/wwe/Bella/DivaBoard.com - The Bellas   Diva Focus - Powered by XMB 1.9.6 Nexus (Alpha)_files/hips\":{\"dirs\":{}},\"/e/Drive J/pictures/Other (new)/pictures/misc_sync_master/wwe/Bella/DivaBoard.com - The Bellas   Diva Focus - Powered by XMB 1.9.6 Nexus (Alpha)_files/navel\":{\"dirs\":{}},\"/e/Drive J/pictures/Other (new)/pictures/misc_sync_master/wwe/Bella/DivaBoard.com - The Bellas   Diva Focus - Powered by XMB 1.9.6 Nexus (Alpha)_files/not good\":{\"dirs\":{}}}},\"/e/Drive J/pictures/Other (new)/pictures/misc_sync_master/wwe/Bella/Google Annual Report_files\":{\"dirs\":{\"/e/Drive J/pictures/Other (new)/pictures/misc_sync_master/wwe/Bella/Google Annual Report_files/not good\":{\"dirs\":{}}}},\"/e/Drive J/pictures/Other (new)/pictures/misc_sync_master/wwe/Bella/legs\":{\"dirs\":{}},\"/e/Drive J/pictures/Other (new)/pictures/misc_sync_master/wwe/Bella/navel\":{\"dirs\":{}},\"/e/Drive J/pictures/Other (new)/pictures/misc_sync_master/wwe/Bella/vg\":{\"dirs\":{}},\"dirs\":{}}}"))));
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
