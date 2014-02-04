package Testing;

import java.io.IOException;
import Connector.Havelock;
import DataStructure.*;

public class HavelockTest {

	/**
	 * @param args
	 * @throws IOException 
	 */
	public static void main(String[] args) throws IOException {
		Havelock hl = new Havelock("temp");
		SymbolInfo info = hl.getSymbolInfo("SMG");
		print(info);
	}
	
	private static void print(Object stuff){
		System.out.println(stuff);
	}

}
