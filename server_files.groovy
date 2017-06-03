import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.channels.WritableByteChannel;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;
import java.util.Date;
import java.util.Hashtable;
import java.util.StringTokenizer;

import javax.ws.rs.GET;
import javax.ws.rs.HeaderParam;
import javax.ws.rs.PathParam;
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

/**
 * SSHD uses slf4j. So add the api + binding jars, and point to a properties file
 */
//@Grab(group='com.pastdev', module='jsch-nio', version='0.1.5')
public class CoagulateFileServer {
	@javax.ws.rs.Path("cmsfs")
	public static class MyResource { // Must be public
	
		public MyResource() {
			System.out.println("Coagulate.MyResource.MyResource()");
		}

		//
		// mutators
		//

	}
	
	// Note - two slashes will fail
	@javax.ws.rs.Path("{filePath : .+}")
	public static class StreamingFileServer { // Must be public
	    @GET
	    public Response streamFile(
	    		@PathParam("filePath") String filePath1,
	    		@HeaderParam("Range") String range) throws Exception {
System.out.println("Request: " + filePath1);
	        File audio;
	        String filePath = filePath1;
	        if (!filePath1.startsWith("/")) {
	        	filePath  = "/"+filePath1;
	        }
			Path p;
			try {
				p = Paths.get(filePath);
			} catch (Exception e) {
				e.printStackTrace();
				throw e;
			}
			audio = p.toFile();
	        return PartialContentServer.buildStream(audio, range, getMimeType(audio));
	    }	
	    
		private static String getMimeType(File file) {
			String mimeType;
			Path path = Paths.get(file.getAbsolutePath());
			String extension = FilenameUtils.getExtension(path.getFileName().toString())
					.toLowerCase();
			mimeType = theMimeTypes.get(extension);
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
							"asc		text/plain " +
							"gif		image/gif " +
							"jpg		image/jpeg " +
							"jpeg		image/jpeg " +
							"png		image/png " +
							"mp3		audio/mpeg " +
							"m3u		audio/mpeg-url " +
							"mp4		video/mp4 " +
							"webm       video/webm " +
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

	private static class PartialContentServer {
		static Response buildStream(final File asset, final String range, String contentType) throws Exception {
			
			if (Paths.get("/home/sarnobat/trash/Coreografía.txt").toFile().exists()) {
			} else {
				// I don't know why this doesn't work, but actual files with
				// european chars coming from Chrome do work so we'll avoid printing
				// this.
				System.out.println("Coagulate.PartialContentServer.buildStream() test file doesn't exist");
			}
			if (!asset.exists()) {
				if (!asset.getAbsolutePath().contains("thumbnail")) {
					System.out.println("Coagulate.PartialContentServer.buildStream() file doesn't exist: " + asset);
				}
				return Response.status(404).build();
			}

	        if (range == null) {
	            StreamingOutput streamer = new StreamingOutput() {
	                @Override
	                public void write(OutputStream output) throws IOException, WebApplicationException {
						FileChannel inputChannel;
						try {
							inputChannel = new FileInputStream(asset).getChannel();
						} catch (Exception e) {
							e.printStackTrace();
							throw e;
						}
						WritableByteChannel outputChannel = Channels .newChannel(output);
						try {
							inputChannel.transferTo(0, inputChannel.size(), outputChannel);
		                } catch (Exception e) {
							e.printStackTrace();
					    } finally {
	                        // closing the channels
	                        inputChannel.close();
	                        if (outputChannel.isOpen()) {
	                        	outputChannel.close();
	                        }
	                    }
	                }
	            };
	            Response r =  Response.ok(streamer).status(200).header(HttpHeaders.CONTENT_LENGTH, asset.length()).header(HttpHeaders.CONTENT_TYPE, contentType).build();
	            return r;
	        }

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
		        	//System.out.println("Broken pipe (we don't need to log this)");
		        } finally {
		            raf.close();
		        }
		    }

		    public int getLenth() {
		        return length;
		    }
		}

	}

	private static String fsPort ;

	public static void main(String[] args) throws URISyntaxException, IOException, KeyManagementException, UnrecoverableKeyException, NoSuchAlgorithmException, KeyStoreException, CertificateException, InterruptedException {

		System.out.println("Note this doesn't work with JVM 1.8 build 45 due to some issue with TLS");
		
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
			fsPort = cmd.getOptionValue("p", "4452");

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
			//NioFileServerWithStreamingVideoAndPartialContent.startServer(fsPort);
			JdkHttpServerFactory.createHttpServer(new URI(
					"http://localhost:" + fsPort + "/"), new ResourceConfig(
					StreamingFileServer.class));
		} catch (Exception e) {
			//e.printStackTrace();
                        System.out.println("Port already listened on 2.");
			System.exit(-1);
		}
	}
}
