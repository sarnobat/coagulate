import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;
import java.nio.file.Paths;

import javax.ws.rs.GET;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Response;

import org.apache.commons.codec.binary.Base64;
import org.apache.commons.codec.binary.StringUtils;
import org.glassfish.jersey.jdkhttp.JdkHttpServerFactory;
import org.glassfish.jersey.server.ResourceConfig;
import org.json.JSONException;
import org.json.JSONObject;

import com.google.common.io.Files;

/**
 * 2020-11-01 this is needed because Tomcat's webdav's move is slow for large
 * files (it does a copy and delete)
 */
public class CoagulateMoveLargeFast {

    @javax.ws.rs.Path("cmsfs/v2/")
    public static class MyResource { // Must be public

        @GET
        @javax.ws.rs.Path("moveBase64")
        @Produces("application/json")
        public Response moveBase64(@QueryParam("filePath") String iFilePath1,
                @QueryParam("destinationDirPath") String iDestinationDirSimpleName) throws JSONException, IOException {

            System.err.println("moveBase64() " + iFilePath1);

            Path path = Paths.get(System.getProperty("user.home") + "/bin/coagulate_move_file_to_subfolder.sh");
            if (!path.toFile().exists()) {
                return Response.serverError().header("Access-Control-Allow-Origin", "*")
                        .entity(new JSONObject().toString(4)).type("application/json").build();
            }

            String srcFilePathDecoded = "";
            try {
                srcFilePathDecoded = StringUtils.newStringUtf8(Base64.decodeBase64((String)iFilePath1)).replaceAll(".*webdav",                        "");// Base64.getDecoder().decode(iFilePath1);
            } catch (Exception e) {
                e.printStackTrace();
                System.err.println("moveBase64() " + e.toString());
                throw e;
            }
//            System.err.println("moveBase64() " + srcFilePathDecoded);
            if (srcFilePathDecoded.endsWith("htm") || srcFilePathDecoded.endsWith(".html")) {
                throw new RuntimeException("Need to move the _files folder too");
            }

            String destDirDecoded = StringUtils.newStringUtf8(Base64.decodeBase64(iDestinationDirSimpleName))
                    .replaceAll(".*webdav", "");
	    // TODO 2020-11: do not allow move to /dev/null
            try {
                Process start = new ProcessBuilder(path.toAbsolutePath().toString(), srcFilePathDecoded, destDirDecoded)
                        .inheritIO().start();
                start.waitFor();
                int exitCode = start.exitValue();
                if (exitCode == 0) {
                    return Response.ok().header("Access-Control-Allow-Origin", "*").entity(new JSONObject().toString(4))
                            .type("application/json").build();
                } else {
                    return Response.serverError().header("Access-Control-Allow-Origin", "*")
                            .entity(new JSONObject().toString(4)).type("application/json").build();
                }
            } catch (Exception e) {
                e.printStackTrace();
                return Response.serverError().header("Access-Control-Allow-Origin", "*")
                        .entity(new JSONObject().toString(4)).type("application/json").build();
            }
        }
    }

    public static void main(String[] args) {

        String port = System.getProperty("port", "4466");

        try {
            JdkHttpServerFactory.createHttpServer(new URI("http://localhost:" + port + "/"),
                    new ResourceConfig(MyResource.class));
        } catch (Exception e) {
            System.out.println("Port already listened on.");
            System.exit(-1);
        }
    }
}

