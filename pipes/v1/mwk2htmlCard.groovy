import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

public class Mwk2HtmlCard {

	public static void main(String[] args) {
		BufferedReader br = null;
		try {
			br = new BufferedReader(new InputStreamReader(System.in));
			String line;
			while ((line = br.readLine()) != null) {
				// log message
				System.err.println("[DEBUG] current line is: " + line);
				String output;
				if (line.matches(".*mwk")) {
char dollar = ((char)36);
int id = Math.random() * 100000;
String html = "<div id="+id+"></div><div><script>var div = " +((char)36)+ "('#"+id+"'); var div2 = div;var jqxhr = "+dollar+ ".get('http://netgear.rohidekar.com:44452"							+ line							+ "', function(data) {	var i = 1;	var lines = data.split('\\n');	var out;	var titled = false;	for (var i = 0; i < lines.length; i++) {	var line = lines[i].replace(/^=+ /,'').replace(/ =+/,'');	if (!titled) {			if (/^[^=]/.test(line)) {				out = '<h3>' +line + '</h3>';				titled = true;				continue;			}		}		out += line + '<br>';	}	var style = 'padding:20px; margin:25px; border-radius: 15px; box-shadow: inset 0 0 9px #222222, 10px 10px 14px #999999; background-color: #FDFD96;clear : left;display: inline-block;vertical-align:top;width : 150px; height : 150px;overflow-y: auto;';	console.debug('1'); div.html('<div style=\"'+style + '\">' + out + '</div>');console.debug(div.html());}).done(function(e){ div2.prepend('');console.debug('done: ' + e)});</script></div>";
					output = html;
				} else {
					// program output
					output = line;
				}
				System.out.println(output);
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
