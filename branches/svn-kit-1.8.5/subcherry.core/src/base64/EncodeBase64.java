package base64;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

import org.apache.commons.codec.binary.Base64;

/**
 * Reads a string from standard input and encodes it base64. 
 * 
 * @version $Revision$ $Author$ $Date$
 */
public class EncodeBase64 {

	public static void main(String[] args) throws IOException {
		BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
		System.out.println("Enter string to encode (input is not escaped):");

		String originalString = reader.readLine();
		String encodedBase64String = Base64.encodeBase64String(originalString.getBytes());
		System.out.println("Your encoded Base64 string :");
		System.out.println(encodedBase64String);
	}

}
