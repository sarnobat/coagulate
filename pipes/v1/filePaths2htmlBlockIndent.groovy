import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

import org.apache.commons.lang3.StringUtils;

public class filePaths2htmlBlockIndent {

	public static void main(String[] args) {
		String close = "";
		BufferedReader br = null;
		try {
			br = new BufferedReader(new InputStreamReader(System.in));
			String line;
			while ((line = br.readLine()) != null) {
				// log message
				System.err.println("[DEBUG] current line is: " + line);
				if (!line.contains(".") && line.startsWith("/")) {
					// is a directory
					int count = StringUtils.countMatches(line, "/");
					String open = StringUtils.repeat("<blockquote>", count);
					close = StringUtils.repeat("</blockquote>", count);
					// program output
					System.out.println(close + open + line);
				} else {
					System.out.println(line);
				}
			}
		} catch (IOException e) {
			e.printStackTrace();
		} finally {
			if (br != null) {
				try {
					br.close();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}
	}
}

