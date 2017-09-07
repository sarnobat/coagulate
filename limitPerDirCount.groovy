import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.io.FilenameUtils;

public class LimitFindOutput {
	private static final int LIMIT = 1;
	public static void main(String[] args) {
		boolean error = false;
		BufferedReader br = null;
		Map<Path,Integer> dirFileCounts = new HashMap<Path,Integer>();
		try {
			br = new BufferedReader(new InputStreamReader(System.in));
			String line;
			while ((line = br.readLine()) != null) {
				try {
					 Path p2 = Paths.get(line);
					if (p2.toFile().isDirectory()) {
						// Just print it
						System.out.println(line);
					} else {
				  		Path p2parent = p2.getParent();
						if (dirFileCounts.containsKey(p2parent)) {
							int filesEmittedFromDirCount = dirFileCounts.get(p2parent);
							if (filesEmittedFromDirCount >= LIMIT) {
								//System.err.println("[DEBUG] limit exceeded for " + p2parent.toString() + "\t" + p2.toString());
								continue;
							} else {
								dirFileCounts.put(p2parent, filesEmittedFromDirCount + 1);
								System.out.println(p2.toString());
							}
						} else {
							dirFileCounts.put(p2parent, 1);
							System.out.println(p2.toString());
						}
					}
				} catch (Exception e) {
					System.err.println("[ERROR] " + e.getMessage());
				}
				System.err.print(".");
			}
			System.err.println("[DEBUG] End of loop");
		} catch (IOException e) {
			e.printStackTrace();
			error = true;
		} finally {
			System.err.println("[DEBUG] finally");
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
