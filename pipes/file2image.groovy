import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.apache.commons.io.FilenameUtils;

public class FileToImage {

	public static void main(String[] args) {
		boolean error = false;
		BufferedReader br = null;
		try {
			br = new BufferedReader(new InputStreamReader(System.in));
			String line;
			while ((line = br.readLine()) != null) {
				//line = "/Unsorted/Videos/3130 cut out tube dress TQ.mp4 .mkv";
				String out;
				if (line.contains("_thumbnails")) {
					out = line;
				} else if (line.contains(".mkv") || line.contains(".mp4") || line.contains(".flv") || line.contains(".mpg") || line.contains(".avi")) {
					Path p = Paths.get(line);
					Path parent = p.getParent();
					out = parent.toString() + "/_thumbnails/" + FilenameUtils.getName(line) + ".jpg";
				} else {
					out = line;
				}
				// log message
				System.err.println("[DEBUG] current line is: " + line);
				// program output
				System.out.println(out);
			}
		} catch (IOException e) {
			e.printStackTrace();
			error = true;
		} finally {
			if (br != null) {
				try {
					br.close();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
			if (error) {
				System.exit(-1);
			}
		}
	}
}
