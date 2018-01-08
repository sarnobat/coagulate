import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.*;

import org.apache.commons.lang3.StringUtils;

public class filePaths2htmlBlockIndent {

	public static void main(String[] args) {
		int lowestLevel = 0;
		String close = "";
		BufferedReader br = null;
		Stack stack = new Stack();
		try {
			br = new BufferedReader(new InputStreamReader(System.in));
			String line;
			String initial;
			while ((line = br.readLine()) != null) {
				// log message
				//System.err.println("[DEBUG] current line is: " + line);
				
				if (initial == null) {
					initial = line;
				}
				
				// If it's a directory...
				if (line.startsWith("/") && !line.endsWith("mwk")) {
					System.err.println("[DEBUG] Will be indented: " + line);
					// is a directory
					int count = StringUtils.countMatches(line, "/");
					if (lowestLevel == 0) {
						lowestLevel = count - 1;
					}
					int level = count - lowestLevel;
					String open = StringUtils.repeat("<blockquote>", level);
					close = stack.isEmpty() ? "" : stack.pop();//StringUtils.repeat("</blockquote>", level);
					String tobepushed = StringUtils.repeat("</blockquote>", level);
					stack.push(tobepushed);
					// program output
					System.out.println(close +"\n"+ open + "\n<h2>" + line.replace(initial,"") + "</h2>\n<br>\n");
				} else {
					System.out.println(line + "");
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

